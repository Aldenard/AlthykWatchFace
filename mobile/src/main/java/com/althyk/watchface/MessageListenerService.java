package com.althyk.watchface;

import android.os.Bundle;
import android.util.Log;

import com.althyk.watchfacecommon.DataSyncUtil;
import com.althyk.watchfacecommon.ETime;
import com.althyk.watchfacecommon.MessageSender;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MessageListenerService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "MessageListenerService";

    private GoogleApiClient mGoogleApiClient;

    @Override // WearableListenerService
    public void onMessageReceived(MessageEvent messageEvent) {

        // check PATH
        if (!messageEvent.getPath().equals(DataSyncUtil.PATH_REQUEST_FETCH)) {
            return;
        }

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
        }

        if (!mGoogleApiClient.isConnected()) {
            ConnectionResult connectionResult =
                    mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);
            if (!connectionResult.isSuccess()) {
                Log.e(TAG, "Failed to connect to GoogleApiClient.");
                return;
            }
        }

        fetchWeather();
    }

    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnected: " + connectionHint);
        }
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


    private void fetchWeather() {
        ETime etime = new ETime().setToNow().generateStartET();
        final ArrayList<ETime> etimeList = new ArrayList<>();
        for (int i = -1; i < 4; i++) {
            etimeList.add(new ETime().setEtMillis(etime.time - ETime.HOUR_IN_MILLIS * 8 * i));
        }

        WeatherApi.getWeatherList(this, new WeatherApi.WeatherCallback() {
            @Override
            public void onResult(List<WeatherApi.Weather> weatherList) {
                ArrayList<DataMap> dataMapList = new ArrayList<>();

                for (WeatherApi.Weather weather : weatherList) {
                    DataMap dataMap = new DataMap();

                    // weather information
                    dataMap.putInt(DataSyncUtil.KEY_WEATHER_ID, weather.weather);
                    dataMap.putInt(DataSyncUtil.KEY_WEATHER_AREA, weather.area);

                    // time information
                    ETime time = etimeList.get(weather.time + 1); // time range is [-1, 3]
                    dataMap.putInt(DataSyncUtil.KEY_WEATHER_YEAR, time.year);
                    dataMap.putInt(DataSyncUtil.KEY_WEATHER_MONTH, time.month);
                    dataMap.putInt(DataSyncUtil.KEY_WEATHER_DAY, time.day);
                    dataMap.putInt(DataSyncUtil.KEY_WEATHER_HOUR, time.hour);

                    dataMapList.add(dataMap);
                }

                DataMap dataMap = new DataMap();
                dataMap.putDataMapArrayList(DataSyncUtil.KEY_WEATHER_LIST, dataMapList);
                MessageSender.sendMessage(mGoogleApiClient, DataSyncUtil.PATH_DATA_WEATHER, dataMap);
            }
        });
    }

}
