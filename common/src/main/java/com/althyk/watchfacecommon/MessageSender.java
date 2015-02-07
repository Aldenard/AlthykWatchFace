package com.althyk.watchfacecommon;

import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

public class MessageSender {
    private static final String TAG = "MessageSender";

    public static final void sendMessage (final GoogleApiClient client,
                                          final String path,
                                          final DataMap dataMap) {

        if (client == null || path == null) {
            return;
        }

        Wearable.NodeApi.getConnectedNodes(client).setResultCallback(
                new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(final NodeApi.GetConnectedNodesResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to get connected nodes");
                            return;
                        }

                        byte[] rawData = dataMap == null ? null : dataMap.toByteArray();
                        for (Node node : result.getNodes()) {
                            Wearable.MessageApi.sendMessage(client, node.getId(), path, rawData);
                        }

                    }
                });

        return;
    }
}
