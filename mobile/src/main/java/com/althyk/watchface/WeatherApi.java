package com.althyk.watchface;

import android.content.Context;
import android.util.Log;

import com.google.gson.reflect.TypeToken;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.util.List;

public final class WeatherApi {
    private final static String TAG = "WeatherApi";

    public static class Weather {
        int time;
        int area;
        int weather;
    }

    public interface WeatherCallback {
        void onResult(List<Weather> weatherList);
    }

    public static final void getWeatherList(final Context context, final WeatherCallback callback) {

        String url = context.getResources().getString(R.string.weather_api_endpoint);

        Ion.with(context)
                .load(url)
                .as(new TypeToken<WeatherResult>(){})
                .setCallback(new FutureCallback<WeatherResult>() {
                    @Override
                    public void onCompleted(Exception e, WeatherResult result) {
                        callback.onResult(result.data);
                    }
                });

    }

    private static class WeatherResult {
        List<Weather> data;
    }

}
