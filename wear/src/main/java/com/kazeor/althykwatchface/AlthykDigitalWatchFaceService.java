package com.kazeor.althykwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class AlthykDigitalWatchFaceService extends CanvasWatchFaceService {

    /**
     * Update rate in milliseconds for interactive mode
     * We update once a second to advance the second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

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

        Time mTime;
        Time mETime;

        /* device feature */
        boolean mLowBitAmbient;
        boolean mBurnInProtection;

        /* graphic objects */
        Paint mBackgroundPaint;
        Paint mDebugPaint;
        Paint mLTPaint = new Paint();
        Paint mETPaint = new Paint();
        float mYOffset;

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
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler
                                    .sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
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
                    .build());

            /* create graphic style */
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setARGB(255, 0, 0, 0);

            mDebugPaint = new Paint();
            mDebugPaint.setARGB(255, 255, 255, 255);

            mLTPaint = new Paint();
            mLTPaint.setARGB(255, 255, 255, 255);
            mLTPaint.setTypeface(BOLD_TYPEFACE);
            mLTPaint.setAntiAlias(true);

            mETPaint = new Paint();
            mETPaint.setARGB(255, 255, 255, 255);
            mETPaint.setTypeface(NORMAL_TYPEFACE);
            mETPaint.setAntiAlias(true);

            /* allocate an object to hold the time*/
            mTime = new Time();
            mETime = new Time();
            mETime.clear("UTC");
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            unregisterReceiver();
            super.onDestroy();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = AlthykDigitalWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mLTPaint.setTextSize(textSize);
            mETPaint.setTextSize(textSize);
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
                mLTPaint.setAntiAlias(antiAlias);
                mETPaint.setAntiAlias(antiAlias);
            }

            invalidate();
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            /* update the time */
            mTime.setToNow();
            double etMillis = mTime.toMillis(true) * L_E_TIME_RATE;
            //double etMillis = System.currentTimeMillis() * L_E_TIME_RATE;
            long etMinute = (long)Math.floor(etMillis / 60000) % 60; // 1000 * 60
            long etHour = (long)Math.floor(etMillis / 3600000) % 24; // 1000 * 60 * 60
            String ltString = String.format("%02d:%02d", mTime.hour, mTime.minute);
            String etString = String.format("%02d:%02d", etHour, etMinute);

            //Log.d("Althyk", ltString + " > " + etString);

            int width = bounds.width();
            int height = bounds.height();

            // draw background
            canvas.drawRect(0, 0, width, height, mBackgroundPaint);

            // draw LT
            float ltX = (width - mLTPaint.measureText(ltString)) / 2f;
            canvas.drawText(ltString, ltX, height / 2, mLTPaint);

            // draw ET
            Rect result = new Rect();
            mETPaint.getTextBounds(etString, 0, etString.length(), result);
            canvas.drawText(
                    etString,
                    (width - result.width()) / 2f,
                    height / 2 + result.height(),
                    mETPaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it chagned while we weren't visible
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            updateTimer();
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

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
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
