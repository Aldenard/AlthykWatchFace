package com.althyk.watchfacecommon;

import android.graphics.Color;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

public final class DataMapUtil {
    private static final String TAG = "DataMapUtil";

    public interface FetchDataMapCallback {
        void onDataMapFetched(DataMap config);
    }

    public static void fetchDataMap(final GoogleApiClient googleApiClient,
                                    final String path,
                                    final FetchDataMapCallback callback) {
        Wearable.NodeApi.getLocalNode(googleApiClient).setResultCallback(
                new ResultCallback<NodeApi.GetLocalNodeResult>() {
                    @Override
                    public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                        String localNode = getLocalNodeResult.getNode().getId();
                        Uri uri = new Uri.Builder()
                                .scheme("wear")
                                .path(path)
                                .authority(localNode)
                                .build();
                        Wearable.DataApi.getDataItem(googleApiClient, uri)
                                .setResultCallback(new DataItemResultCallback(callback));
                    }
                }
        );
    }

    public static void overwriteKeysInDataMap(final GoogleApiClient googleApiClient,
                                              final String path,
                                              final DataMap dataMapToOverwrite) {

        DataMapUtil.fetchDataMap(googleApiClient,
                path,
                new FetchDataMapCallback() {
                    @Override
                    public void onDataMapFetched(DataMap currentDataMap) {
                        DataMap overwrittenDataMap = new DataMap();
                        overwrittenDataMap.putAll(currentDataMap);
                        overwrittenDataMap.putAll(dataMapToOverwrite);
                        DataMapUtil.putDataItem(googleApiClient, path, overwrittenDataMap);
                    }
                }
        );
    }

    public static void putDataItem(GoogleApiClient googleApiClient,
                                   String path,
                                   DataMap newDataMap) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(path);
        DataMap configToPut = putDataMapRequest.getDataMap();
        configToPut.putAll(newDataMap);
        Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "putDataItem result status: " + dataItemResult.getStatus());
                        }
                    }
                });
    }

    private static class DataItemResultCallback implements ResultCallback<DataApi.DataItemResult> {

        private final FetchDataMapCallback mCallback;

        public DataItemResultCallback(FetchDataMapCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onResult(DataApi.DataItemResult dataItemResult) {
            if (dataItemResult.getStatus().isSuccess()) {
                if (dataItemResult.getDataItem() != null) {
                    DataItem dataItem = dataItemResult.getDataItem();
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                    DataMap dataMap = dataMapItem.getDataMap();
                    mCallback.onDataMapFetched(dataMap);
                } else {
                    mCallback.onDataMapFetched(new DataMap());
                }
            }
        }
    }

    private DataMapUtil() { }
}
