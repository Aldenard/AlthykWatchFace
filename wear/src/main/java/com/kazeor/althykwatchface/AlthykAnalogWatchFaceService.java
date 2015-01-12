package com.kazeor.althykwatchface;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SweepGradient;
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

public class AlthykAnalogWatchFaceService  extends CanvasWatchFaceService {
    private static final String TAG = "AlthykA";

    /**
     * Update rate in milliseconds for interactive mode
     * We update once a second to advance the second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    private static final double L_E_TIME_RATE = 3600.0 / 175;
    private static final double LT_MS_IN_RAD = 2 * Math.PI / (1000 * 60 * 60);
    private static final long ET_HOUR_IN_LT_MS = 1000 * 60 * 70 / 24; // 24 [hour in ET] = 70 [min]
    private static final long LT_HOUR_IN_LT_MS = 1000 * 60 * 60;
    private static final double ET_HOUR_IN_RAD = LT_MS_IN_RAD * ET_HOUR_IN_LT_MS;

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;

        Time mTime;

        /* device feature */
        boolean mLowBitAmbient;
        boolean mBurnInProtection;

        /* graphic objects */
        Paint mBackgroundPaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mTickPaint;
        Paint mETTickPaint;
        Paint mAccentETTickPaint;
        Paint mCirclePaint;
        Paint mTextPaint = new Paint();

        /** Handler to update the time once a second in interactive mode. */
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
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
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

            setWatchFaceStyle(new WatchFaceStyle.Builder(AlthykAnalogWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            /* create graphic style */
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setARGB(255, 0, 0, 0);

            mHourPaint = new Paint();
            mHourPaint.setARGB(255, 200, 200, 200);
            mHourPaint.setStrokeWidth(5.f);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);

            mMinutePaint = new Paint();
            mMinutePaint.setARGB(255, 200, 200, 200);
            mMinutePaint.setStrokeWidth(3.f);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);

            mSecondPaint = new Paint();
            mSecondPaint.setARGB(255, 255, 0, 0);
            mSecondPaint.setStrokeWidth(2.f);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);

            mTickPaint = new Paint();
            mTickPaint.setARGB(255, 128, 128, 128);
            mTickPaint.setStrokeWidth(2.f);
            mTickPaint.setAntiAlias(true);

            mETTickPaint = new Paint();
            mETTickPaint.setARGB(255, 0, 0, 0);
            mETTickPaint.setStrokeWidth(3.f);
            mETTickPaint.setAntiAlias(true);

            mAccentETTickPaint = new Paint();
            mAccentETTickPaint.setARGB(255, 0, 0, 0);
            mAccentETTickPaint.setStrokeWidth(6.f);
            mAccentETTickPaint.setAntiAlias(true);

            mCirclePaint = new Paint();
            mCirclePaint.setARGB(250, 255, 255, 255);
            mCirclePaint.setStrokeWidth(10.f);
            mCirclePaint.setAntiAlias(true);
            mCirclePaint.setStyle(Paint.Style.STROKE);
            mCirclePaint.setShader(new SweepGradient(0, 0,
                    Color.argb(255, 0, 255, 0), Color.argb(0, 0, 255, 0)));

            mTextPaint = new Paint();
            mTextPaint.setARGB(255, 255, 255, 255);
            mTextPaint.setTypeface(NORMAL_TYPEFACE);
            mTextPaint.setAntiAlias(true);
            mTextPaint.setTextSize(18f);

            /* allocate an object to hold the time*/
            mTime = new Time();
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
            //Resources resources = AlthykAnalogWatchFaceService.this.getResources();
            //boolean isRound = insets.isRound();
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
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mSecondPaint.setAntiAlias(antiAlias);
                mTickPaint.setAntiAlias(antiAlias);
                mETTickPaint.setAntiAlias(antiAlias);
                mAccentETTickPaint.setAntiAlias(antiAlias);
                mCirclePaint.setAntiAlias(antiAlias);
            }

            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            /* update the time */
            mTime.setToNow();
            long millis = System.currentTimeMillis();

            int width = bounds.width();
            int height = bounds.height();
            float centerX = bounds.exactCenterX();
            float centerY = bounds.exactCenterY();

            // draw background
            canvas.drawRect(0, 0, width, height, mBackgroundPaint);

            // Draw the ticks.
            float innerTickRadius = centerX - 100;
            float outerTickRadius = centerX - 90;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(centerX + innerX, centerY + innerY,
                        centerX + outerX, centerY + outerY, mTickPaint);
            }

            int second = mTime.second;
            int minutes = mTime.minute;
            float minRot = (minutes / 30f  + second / 1800f) * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float minLength = centerX - 40;
            float hrLength = centerX - 115;

            // draw min
            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mMinutePaint);

            // draw hour
            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHourPaint);

            // Draw the circle
            double etMillis = millis * L_E_TIME_RATE;
            long etHour = (long) Math.floor(etMillis / 3600000) % 24; // 1000 * 60 * 60
            long prevETTickMS = millis - (millis % ET_HOUR_IN_LT_MS);
            double startRad = (prevETTickMS % LT_HOUR_IN_LT_MS) * LT_MS_IN_RAD;
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.translate(centerX, centerY);
            canvas.rotate((float) (startRad * 180 / Math.PI) - 90f);
            canvas.drawArc(-centerX + 15, -centerY + 15, centerX - 15, centerY - 15,
                    0, 360f, false, mCirclePaint);
            canvas.restore();

            // Draw the ET ticks.
            float etTextRadius = centerX - 35;
            float etInnerTickRadius = centerX - 20;
            float etOuterTickRadius = centerX - 10;
            for (int etTickIndex = 0; etTickIndex < 20; etTickIndex++) {
                float tickRot = (float) (startRad + etTickIndex * ET_HOUR_IN_RAD);
                float innerX = (float) Math.sin(tickRot) * etInnerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * etInnerTickRadius;
                float outerX = (float) Math.sin(tickRot) * etOuterTickRadius;
                float outerY = (float) -Math.cos(tickRot) * etOuterTickRadius;

                long hour = etHour + etTickIndex + 1;
                if (etTickIndex != 0 && hour % 8 != 0) {
                    canvas.drawLine(centerX + innerX, centerY + innerY,
                            centerX + outerX, centerY + outerY, mETTickPaint);
                } else {
                    canvas.drawLine(centerX + innerX, centerY + innerY,
                            centerX + outerX, centerY + outerY, mAccentETTickPaint);

                    String hourText = (hour % 24) + "";
                    Rect rect = new Rect();
                    mTextPaint.getTextBounds(hourText, 0, hourText.length(), rect);
                    float textX = (float) Math.sin(tickRot) * etTextRadius - rect.width() / 2f;
                    float textY = (float) -Math.cos(tickRot) * etTextRadius + rect.height() / 2f;
                    canvas.drawText(hourText, centerX + textX, centerY + textY, mTextPaint);
                }
            }
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

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            AlthykAnalogWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            AlthykAnalogWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
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