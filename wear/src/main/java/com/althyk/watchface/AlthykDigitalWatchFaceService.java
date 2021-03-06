package com.althyk.watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class AlthykDigitalWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "AlthykD";

    /**
     * Update rate in milliseconds for interactive mode
     * We update once a second to advance the second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 8750; // 3 [minutes in ET] = 8750 [msec]
    private static final long INTERACTIVE_UPDATE_RATE_CHILD_MS = 2917; // 2917 + 2917 + 2916 = 8750
    private static final long INTERACTIVE_UPDATE_RATE_MS_IN_AMBIENT = 8750 * 20; // 1 [hour in ET] = 8750 * 20 [msec]
    private static final long ANIMATION_DURATION = 500;
    private static final long ANIMATION_UPDATE_RATE = TimeUnit.SECONDS.toMillis(1) / 30; // 30fps

    private static final double L_E_TIME_RATE = 3600.0 / 175;

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;
        static final int MSG_UPDATE_TIME_AMBIENT = 1;
        static final int MSG_UPDATE_ANIMATION = 2;

        Time mTime;

        /* device feature */
        boolean mLowBitAmbient;
        boolean mBurnInProtection;

        /* graphic objects */
        Paint mBackgroundPaint;
        Paint mLinePaint;
        Paint mLTPaint = new Paint();
        Paint mETPaint = new Paint();
        float mLTTextWidth, mLTTextHeight;
        float mETTextWidth, mETTextHeight;
        float mTextPositionRatio = 0.5f;

        int[] mColors = {
                Color.parseColor("#5062a6"),
                Color.parseColor("#3386bd"),
                Color.parseColor("#43c2e8"),
                Color.parseColor("#ffe98c"),
                Color.parseColor("#e09f57"),
                Color.parseColor("#b36679"),
                Color.parseColor("#5062a6"),
        };
        float[] mPositions = {0f, 5 / 24f, 7 / 24f, 0.5f, 17 / 24f, 19 / 24f, 1f};

        /* animation */
        long mAnimationStart;
        float mAnimationValue = 1f;

        /**
         * The system notifies the watch face once a minute when the time changes.
         * This handler updates INTERACTIVE_UPDATE_RATE_MS in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_CHILD_MS
                                    - ((timeMs % INTERACTIVE_UPDATE_RATE_MS) %
                                    INTERACTIVE_UPDATE_RATE_CHILD_MS);
                            mUpdateTimeHandler
                                    .sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                    case MSG_UPDATE_TIME_AMBIENT:
                        invalidate();
                        if (!shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS_IN_AMBIENT
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS_IN_AMBIENT);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME_AMBIENT, delayMs);
                        }
                        break;
                    case MSG_UPDATE_ANIMATION:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long diffMs = System.currentTimeMillis() - mAnimationStart;
                            if (diffMs < ANIMATION_DURATION) {
                                long delayMs = ANIMATION_UPDATE_RATE -
                                        diffMs % ANIMATION_UPDATE_RATE;
                                float t = (float) diffMs / ANIMATION_DURATION;
                                mAnimationValue = t * (2f -  t);
                                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_ANIMATION, delayMs);
                            } else {
                                mAnimationValue = 1f;
                            }
                        } else {
                            mAnimationValue = 1f;
                        }
                        break;
                }

            }
        };

        /* receiver to update the time zone */
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(AlthykDigitalWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setStatusBarGravity(Gravity.TOP | Gravity.LEFT)
                    .setHotwordIndicatorGravity(Gravity.CENTER | Gravity.TOP)
                    .setViewProtection(WatchFaceStyle.PROTECT_STATUS_BAR |
                            WatchFaceStyle.PROTECT_HOTWORD_INDICATOR)
                    .build());

            /* create graphic style */
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setARGB(255, 0, 0, 0);

            mLinePaint = new Paint();
            mLinePaint.setAntiAlias(true);
            mLinePaint.setARGB(255, 255, 255, 255);
            mLinePaint.setStrokeWidth(2f);

            mLTPaint = new Paint();
            mLTPaint.setARGB(255, 255, 255, 255);
            mLTPaint.setTypeface(NORMAL_TYPEFACE);
            mLTPaint.setAntiAlias(true);
            mLTPaint.setShadowLayer(5f, 0f, 0f, Color.argb(128, 0, 0, 0));

            mETPaint = new Paint();
            mETPaint.setARGB(255, 255, 255, 255);
            mETPaint.setTypeface(NORMAL_TYPEFACE);
            mETPaint.setAntiAlias(true);
            mETPaint.setShadowLayer(5f, 0f, 0f, Color.argb(128, 0, 0, 0));

            updateFontMetrics();

            /* allocate an object to hold the time*/
            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME_AMBIENT);
            unregisterReceiver();
            super.onDestroy();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = AlthykDigitalWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();

            // text size
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            mLTPaint.setTextSize(textSize);
            mETPaint.setTextSize(textSize);

            // text position
            mTextPositionRatio = isRound ? 0.4f : 0.5f;

            updateFontMetrics();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            /* get device features (burn-in, low-bit ambient) */
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();

            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            /* the wearable switched between modes */
            super.onAmbientModeChanged(inAmbientMode);

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mLinePaint.setAntiAlias(antiAlias);
                mLTPaint.setAntiAlias(antiAlias);
                mETPaint.setAntiAlias(antiAlias);
                updateFontMetrics();
            }

            if (inAmbientMode) {
                mLTPaint.clearShadowLayer();
                mETPaint.clearShadowLayer();
            } else {
                mLTPaint.setShadowLayer(5f, 0f, 0f, Color.argb(128, 0, 0, 0));
                mETPaint.setShadowLayer(5f, 0f, 0f, Color.argb(128, 0, 0, 0));
            }

            invalidate();
            updateTimer();
            updateAnimation();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            /* update the time */
            mTime.setToNow();
            double etMillis = System.currentTimeMillis() * L_E_TIME_RATE;

            int width = bounds.width();
            int height = bounds.height();
            int cardHeight = getPeekCardPosition().height();
            boolean shouldTimerBeRunning = shouldTimerBeRunning();

            float qY = shouldTimerBeRunning ? (height - cardHeight) / 4f : height / 4f;
            float centerX = bounds.exactCenterX();
            float centerY = (height - cardHeight) / 2f;

            float dist = cardHeight > 0 ? (height - cardHeight) / 4f : centerY * mTextPositionRatio;
            dist = shouldTimerBeRunning ? dist : centerY * mTextPositionRatio;

            // calc LT time
            int ltHour = mTime.hour;
            int ltMin = mTime.minute;
            String ltString = String.format("%02d:%02d", ltHour, ltMin);

            // draw LT background
            if (shouldTimerBeRunning) {
                canvas.save();
                canvas.clipRect(0, 0, width, centerY);
                canvas.drawColor(calcColor(ltHour, ltMin));
                canvas.restore();
            } else {
                canvas.drawRect(0, 0, width, height, mBackgroundPaint);
            }

            // draw LT
            float ltX = centerX - mLTTextWidth / 2f;
            float ltY = centerY - dist + mLTTextHeight / 2f;
            canvas.drawText(ltString, ltX, ltY, mLTPaint);

            if (shouldTimerBeRunning) {
                int etHour = (int) Math.floor(etMillis / 3600000) % 24; // 1000 * 60 * 60
                int etMin = (int) Math.floor(etMillis / 60000) % 60; // 1000 * 60
                String etString = String.format("%02d:%02d", etHour, etMin);

                // draw ET background
                canvas.save();
                canvas.clipRect(0, 0, width, height);
                canvas.clipRect(0, centerY, width, height);
                canvas.drawColor(calcColor(etHour, etMin));
                canvas.restore();

                // draw ET
                float etX = centerX - mETTextWidth / 2f;
                float etY = centerY + dist + mETTextHeight /2f;
                canvas.drawText(etString, etX, etY, mETPaint);

            } else {
                int etHour = (int) Math.floor(etMillis / 3600000) % 24; // 1000 * 60 * 60
                String etString = String.format("%02d:--", etHour);

                // draw ET
                float etX = centerX - mETTextWidth / 2f;
                float etY = centerY + dist + mETTextHeight /2f;
                canvas.drawText(etString, etX, etY, mETPaint);
            }

            // center line
            canvas.drawLine(0, centerY, width * mAnimationValue, centerY, mLinePaint);
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            invalidate();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            updateTimer();
        }

        private int calcColor(int hour, int minute) {
            float value = (hour + minute / 60f) / 24f;
            int red = 255, green = 255, blue = 255;
            for (int i = 0; i < mPositions.length - 1; i++) {
                if (mPositions[i] <= value && value < mPositions[i + 1]) {
                    float d = mPositions[i + 1] - mPositions[i];
                    float a = (value - mPositions[i]) / d;
                    float b = (mPositions[i + 1] - value) / d;
                    red = Math.round(Color.red(mColors[i]) * b + Color.red(mColors[i + 1]) * a);
                    green = Math.round(Color.green(mColors[i]) * b + Color.green(mColors[i + 1]) * a);
                    blue = Math.round(Color.blue(mColors[i]) * b + Color.blue(mColors[i + 1]) * a);
                    break;
                }
            }
            return Color.rgb(red, green, blue);
        }

        private void updateFontMetrics() {
            Rect result = new Rect();
            mLTPaint.getTextBounds("00:00", 0, 5, result);
            mLTTextHeight = result.height();
            mLTTextWidth = mLTPaint.measureText("00:00");

            mETPaint.getTextBounds("00:00", 0, 5, result);
            mETTextHeight = result.height();
            mETTextWidth = mETPaint.measureText("00:00");
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            AlthykDigitalWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            AlthykDigitalWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateAnimation() {
            mAnimationValue = 1f;
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_ANIMATION);
            if (shouldTimerBeRunning()) {
                mAnimationStart = System.currentTimeMillis();
                mAnimationValue = 0f;
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_ANIMATION);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME_AMBIENT);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            } else {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME_AMBIENT);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

    }

}
