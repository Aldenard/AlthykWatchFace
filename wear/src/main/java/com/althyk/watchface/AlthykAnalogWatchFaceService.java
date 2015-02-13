package com.althyk.watchface;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.althyk.watchfacecommon.DataMapUtil;
import com.althyk.watchfacecommon.DataSyncUtil;
import com.althyk.watchfacecommon.ETime;
import com.althyk.watchfacecommon.MessageSender;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class AlthykAnalogWatchFaceService  extends CanvasWatchFaceService {
    private static final String TAG = "AlthykA";

    /**
     * Update rate in milliseconds for interactive mode
     * We update once a second to advance the second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long ANIMATION_DURATION = 500;
    private static final long ANIMATION_UPDATE_RATE = TimeUnit.SECONDS.toMillis(1) / 30; // 30fps
    private static final long REQUEST_FETCH_RATE_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long WEATHER_UPDATE_RATE_MS = 70 * 60 * 1000 / 3; // = 8 et hour

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

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        static final int MSG_UPDATE_TIME = 0;
        static final int MSG_UPDATE_ANIMATION = 1;
        static final int MSG_REQUEST_FETCH = 2;

        static final int WEATHER_ICON_SIZE = 32;

        Time mTime;

        /* weather data */
        int mWeatherArea = 4;
        boolean mGotFullData = false;
        String mLastFetchedStartId;
        HashMap<String, Bitmap> mWeatherHashMap = new HashMap<>();

        /* device feature */
        boolean mLowBitAmbient;
        boolean mBurnInProtection;

        /* graphic objects */
        Paint mBackgroundPaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mTickPaint;
        Paint mETTickPaint;
        Paint mAccentETTickPaint;
        Paint mCirclePaint;
        Paint mCircleDimPaint;
        Paint mCircleDimInvPaint;
        Paint mTextPaint = new Paint();

        Paint mWeatherPaint;

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
                    case MSG_REQUEST_FETCH:
                        MessageSender.sendMessage(mGoogleApiClient,
                                DataSyncUtil.PATH_REQUEST_FETCH, null);
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = REQUEST_FETCH_RATE_MS - (timeMs % REQUEST_FETCH_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_REQUEST_FETCH, delayMs);
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

        /* google api client */
        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(AlthykAnalogWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(AlthykAnalogWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setPeekOpacityMode(WatchFaceStyle.PEEK_OPACITY_MODE_TRANSLUCENT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setStatusBarGravity(Gravity.CENTER)
                    .setHotwordIndicatorGravity(Gravity.CENTER)
                    .setViewProtection(WatchFaceStyle.PROTECT_STATUS_BAR |
                            WatchFaceStyle.PROTECT_HOTWORD_INDICATOR)
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

            mTickPaint = new Paint();
            mTickPaint.setARGB(255, 128, 128, 128);
            mTickPaint.setStrokeWidth(2.f);
            mTickPaint.setAntiAlias(true);

            mETTickPaint = new Paint();
            mETTickPaint.setARGB(255, 0, 0, 0);
            mETTickPaint.setStrokeWidth(3.f);
            mETTickPaint.setStrokeCap(Paint.Cap.ROUND);
            mETTickPaint.setAntiAlias(true);

            mAccentETTickPaint = new Paint();
            mAccentETTickPaint.setARGB(255, 0, 0, 0);
            mAccentETTickPaint.setStrokeWidth(6.f);
            mAccentETTickPaint.setStrokeCap(Paint.Cap.ROUND);
            mAccentETTickPaint.setAntiAlias(true);

            mCirclePaint = new Paint();
            mCirclePaint.setStrokeWidth(10.f);
            mCirclePaint.setAntiAlias(true);
            mCirclePaint.setStyle(Paint.Style.STROKE);
            mCirclePaint.setShader(new SweepGradient(0, 0, mColors, mPositions));

            int[] colors = {
                    Color.argb(0, 0, 0, 0),
                    Color.argb(0, 0, 0, 0),
                    Color.argb(255, 0, 0, 0)
            };
            float[] positions = {0.f, 0.7f, 1.f};
            float dimWidth = WEATHER_ICON_SIZE;
            mCircleDimPaint = new Paint();
            mCircleDimPaint.setStrokeWidth(dimWidth);
            mCircleDimPaint.setAntiAlias(true);
            mCircleDimPaint.setStyle(Paint.Style.STROKE);
            mCircleDimPaint.setShader(new SweepGradient(0, 0, colors, positions));

            mCircleDimInvPaint = new Paint();
            mCircleDimInvPaint.setStrokeWidth(dimWidth);
            mCircleDimInvPaint.setAntiAlias(true);
            mCircleDimInvPaint.setStyle(Paint.Style.STROKE);
            mCircleDimInvPaint.setARGB(255, 0, 0, 0);

            mTextPaint = new Paint();
            mTextPaint.setARGB(255, 255, 255, 255);
            mTextPaint.setTypeface(NORMAL_TYPEFACE);
            mTextPaint.setAntiAlias(true);
            mTextPaint.setTextSize(18f);

            mWeatherPaint = new Paint();
            mWeatherPaint.setFilterBitmap(true);

            /* allocate an object to hold the time*/
            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_ANIMATION);
            mUpdateTimeHandler.removeMessages(MSG_REQUEST_FETCH);
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

            updateFetchRequest();
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
                mTickPaint.setAntiAlias(antiAlias);
                mETTickPaint.setAntiAlias(antiAlias);
                mAccentETTickPaint.setAntiAlias(antiAlias);
                mCirclePaint.setAntiAlias(antiAlias);
                mCircleDimPaint.setAntiAlias(antiAlias);
                mCircleDimInvPaint.setAntiAlias(antiAlias);
                mWeatherPaint.setFilterBitmap(antiAlias);
            }

            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
            updateAnimation();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            /* update the time */
            mTime.setToNow();
            long millis = System.currentTimeMillis();
            ETime etime = new ETime().setLtMillis(millis);

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
            int hour = mTime.hour;
            float minRot = (minutes / 30f  + second / 1800f) * (float) Math.PI;
            float hrRot = ((hour + (minutes / 60f)) / 6f) * (float) Math.PI;

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
            int etHour = etime.hour;
            long prevETTickMS = millis - (millis % ET_HOUR_IN_LT_MS);
            double startRad = (prevETTickMS % LT_HOUR_IN_LT_MS) * LT_MS_IN_RAD;
            float startDeg = (float) (startRad * 180 / Math.PI);
            if (shouldTimerBeRunning()) {
                if (mWeatherArea == 0) {
                    canvas.save(Canvas.MATRIX_SAVE_FLAG);
                    canvas.translate(centerX, centerY);
                    canvas.rotate(startDeg - 90f - etHour * 15); // 15 = 360 / 24
                    canvas.drawArc(-centerX + 15, -centerY + 15, centerX - 15, centerY - 15,
                            0f, 360f, false, mCirclePaint);
                    canvas.restore();
                } else {
                    ETime weatherStart = etime.generateStartET();
                    String[] timeIds = {
                            weatherStart.getTimeId(),
                            new ETime().setEtMillis(weatherStart.time + ETime.HOUR_IN_MILLIS * 8).getTimeId(),
                            new ETime().setEtMillis(weatherStart.time + ETime.HOUR_IN_MILLIS * 8 * 2).getTimeId(),
                            new ETime().setEtMillis(weatherStart.time + ETime.HOUR_IN_MILLIS * 8 * 3).getTimeId(),
                    };
                    int timeIndex = 0;
                    float etCenterTickRadius = centerX - 15;
                    for (int etTickIndex = 0; etTickIndex < 20; etTickIndex++) {
                        String timeId = timeIds[timeIndex];
                        Bitmap bitmap = mWeatherHashMap.get(timeId);
                        if (bitmap != null) {
                            float tickRot = (float) (startRad + etTickIndex * ET_HOUR_IN_RAD);
                            float wPosX = (float) Math.sin(tickRot + ET_HOUR_IN_RAD / 2f) * etCenterTickRadius;
                            float wPosY = (float) -Math.cos(tickRot + ET_HOUR_IN_RAD / 2f) * etCenterTickRadius;
                            float posX = wPosX - bitmap.getWidth() / 2f;
                            float posY = wPosY - bitmap.getHeight() / 2f;
                            canvas.drawBitmap(bitmap, posX + centerX, posY + centerY, mWeatherPaint);
                        }

                        int nextTickHour = (etHour + etTickIndex + 1) % 24;
                        if (nextTickHour % 8 == 0) {
                            timeIndex ++;
                        }
                    }
                }
            }

            // Draw the ET ticks.
            float etTextRadius = centerX - 40;
            float etInnerTickRadius = centerX - 20;
            float etOuterTickRadius = centerX - 10;
            if (mWeatherArea == 0) {
                mETTickPaint.setARGB(255, 0, 0, 0);
                mAccentETTickPaint.setARGB(255, 0, 0, 0);
            } else {
                etInnerTickRadius = centerX - 15;
                etOuterTickRadius = centerX - 14;
                mETTickPaint.setARGB(255, 128, 128, 128);
                mAccentETTickPaint.setARGB(255, 200, 200, 200);
            }
            for (int etTickIndex = 0; etTickIndex < 20; etTickIndex++) {
                float tickRot = (float) (startRad + etTickIndex * ET_HOUR_IN_RAD);
                float innerX = (float) Math.sin(tickRot) * etInnerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * etInnerTickRadius;
                float outerX = (float) Math.sin(tickRot) * etOuterTickRadius;
                float outerY = (float) -Math.cos(tickRot) * etOuterTickRadius;

                int tickHour = etHour + etTickIndex;
                canvas.drawLine(centerX + innerX, centerY + innerY,
                        centerX + outerX, centerY + outerY,
                        tickHour % 8 != 0 ? mETTickPaint : mAccentETTickPaint);

                if (etTickIndex == 0 || tickHour % 8 == 0) {
                    String hourText = (tickHour % 24) + "";
                    Rect rect = new Rect();
                    mTextPaint.getTextBounds(hourText, 0, hourText.length(), rect);
                    float textX = (float) Math.sin(tickRot) * etTextRadius - rect.width() / 2f;
                    float textY = (float) -Math.cos(tickRot) * etTextRadius + rect.height() / 2f;
                    canvas.drawText(hourText, centerX + textX, centerY + textY, mTextPaint);
                }
            }

            // dimming
            if (shouldTimerBeRunning()) {
                canvas.save(Canvas.MATRIX_SAVE_FLAG);
                canvas.translate(centerX, centerY);
                canvas.rotate(startDeg - 90f);
                float sweepDegree = 360f * mAnimationValue;
                canvas.drawArc(-centerX + 15, -centerY + 15, centerX - 15, centerY - 15,
                        0, sweepDegree, false, mCircleDimPaint);
                canvas.drawArc(-centerX + 15, -centerY + 15, centerX - 15, centerY - 15,
                        sweepDegree, 360f - sweepDegree, false, mCircleDimInvPaint);
                canvas.restore();
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
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone in case it changed while we weren't visible
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
            updateFetchRequest();
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

        private void updateWeather(DataMap dataMap) {
            if (dataMap == null) {
                DataMapUtil.fetchDataMap(mGoogleApiClient, DataSyncUtil.PATH_DATA_WEATHER,
                        new DataMapUtil.FetchDataMapCallback() {
                            @Override
                            public void onDataMapFetched(DataMap dataMap) {
                                updateWeather(dataMap);
                            }
                        });
                return;
            };

            ArrayList<DataMap> weatherList =
                    dataMap.getDataMapArrayList(DataSyncUtil.KEY_WEATHER_LIST);
            if (weatherList == null) {
                return;
            }

            if (weatherList.size() != 0) {
                mWeatherHashMap.clear();
            }

            for (DataMap weatherDataMap : weatherList) {
                int weatherArea = weatherDataMap.getInt(DataSyncUtil.KEY_WEATHER_AREA);
                if (weatherArea == mWeatherArea) {
                    int year = weatherDataMap.getInt(DataSyncUtil.KEY_WEATHER_YEAR);
                    int month = weatherDataMap.getInt(DataSyncUtil.KEY_WEATHER_MONTH);
                    int day = weatherDataMap.getInt(DataSyncUtil.KEY_WEATHER_DAY);
                    int hour = weatherDataMap.getInt(DataSyncUtil.KEY_WEATHER_HOUR);
                    String key = ETime.getTimeId(year, month, day, hour);

                    int weatherId = weatherDataMap.getInt(DataSyncUtil.KEY_WEATHER_ID);
                    String resourceName = String.format("weather_icon_%02d", weatherId);
                    int iconId = getResources().getIdentifier(resourceName, "drawable", "com.althyk.watchface");
                    Bitmap bitmap = BitmapFactory.decodeResource(getResources(), iconId);
                    bitmap = Bitmap.createScaledBitmap(bitmap, WEATHER_ICON_SIZE, WEATHER_ICON_SIZE,
                            true);

                    mWeatherHashMap.put(key, bitmap);
                }
            }

            if (weatherList.size() == 24 * 5) {
                mGotFullData = true;
                mLastFetchedStartId = new ETime().setToNow().generateStartET().getTimeId();
                // stop fetch request
                mUpdateTimeHandler.removeMessages(MSG_REQUEST_FETCH);
            } else {
                mGotFullData = false;
            }

            invalidate();
        }

        private void updateArea(DataMap dataMap) {
            int areaId = dataMap.getInt(DataSyncUtil.KEY_WEATHER_AREA);
            if (areaId > -1) {
                mWeatherArea = areaId;
                updateWeather(null);
            }
        }

        private void updateFetchRequest () {
            mUpdateTimeHandler.removeMessages(MSG_REQUEST_FETCH);
            if (mGotFullData &&
                mLastFetchedStartId.equals(new ETime().setToNow().generateStartET().getTimeId())) {
                // next weather update
                long timeMs = System.currentTimeMillis();
                long delayMs = WEATHER_UPDATE_RATE_MS - (timeMs % WEATHER_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_REQUEST_FETCH, delayMs);
            } else {
                // next 1min
                long timeMs = System.currentTimeMillis();
                long delayMs = REQUEST_FETCH_RATE_MS - (timeMs % REQUEST_FETCH_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_REQUEST_FETCH, delayMs);
            }
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

        private void updateConfigDataItemAndUiOnStartup() {
            // Weather Data
            DataMapUtil.fetchDataMap(mGoogleApiClient, DataSyncUtil.PATH_DATA_WEATHER,
                    new DataMapUtil.FetchDataMapCallback() {
                        @Override
                        public void onDataMapFetched(DataMap config) {
                            // If the DataItem hasn't been created yet or some keys are missing,
                            // use the default values.
                            if (!config.containsKey(DataSyncUtil.KEY_WEATHER_LIST)) {
                                ArrayList<DataMap> dataMapList = new ArrayList<>();
                                config.putDataMapArrayList(DataSyncUtil.KEY_WEATHER_LIST, dataMapList);
                            }
                            DataMapUtil.putDataItem(mGoogleApiClient, DataSyncUtil.PATH_DATA_WEATHER, config);
                            updateWeather(config);
                        }
                    });

            // Area
            DataMapUtil.fetchDataMap(mGoogleApiClient, DataSyncUtil.PATH_DATA_AREA,
                    new DataMapUtil.FetchDataMapCallback() {
                        @Override
                        public void onDataMapFetched(DataMap config) {
                            // If the DataItem hasn't been created yet or some keys are missing,
                            // use the default values.
                            if (!config.containsKey(DataSyncUtil.KEY_WEATHER_AREA)) {
                                config.putInt(DataSyncUtil.KEY_WEATHER_AREA, 0);
                            }
                            DataMapUtil.putDataItem(mGoogleApiClient, DataSyncUtil.PATH_DATA_AREA, config);
                            updateArea(config);
                        }
                    });
        }

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            try {
                for (DataEvent dataEvent : dataEvents) {
                    if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                        continue;
                    }

                    DataItem dataItem = dataEvent.getDataItem();
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                    DataMap dataMap = dataMapItem.getDataMap();
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Config DataItem updated:" + dataMap);
                    }

                    switch (dataItem.getUri().getPath()) {
                        case DataSyncUtil.PATH_DATA_WEATHER:
                            updateWeather(dataMap);
                            break;
                        case DataSyncUtil.PATH_DATA_AREA:
                            updateArea(dataMap);
                            break;
                    }
                }
            } finally {
                dataEvents.close();
            }
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateConfigDataItemAndUiOnStartup();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        }

    }

}
