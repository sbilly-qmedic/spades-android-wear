package com.qmedic.weartest;

import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class WearListCallListenerService extends WearableListenerService {
    public static String SERVICE_CALLED_WEAR = "QMEDIC_DATA_MESSAGE";
    public static String SERVICE_ACTION = "QMEDIC_DATA_ENTRY";
    private static final String INTENT_FILTER = "SPADES_INTENT_FILTER";
    private static final String TAG = "QMEDIC_APP_SERVICE";

    private GoogleApiClient mGoogleApiClient;

    public WearListCallListenerService() {
        super();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        String event = messageEvent.getPath();
        Log.d(TAG, "Got message: " + event);
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);
    }

    @Override
    public void onConnectedNodes(List<Node> connectedNodes) {
        super.onConnectedNodes(connectedNodes);
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        super.onCapabilityChanged(capabilityInfo);
    }

    @Override
    public void onChannelOpened(Channel channel) {
        super.onChannelOpened(channel);
    }

    @Override
    public void onChannelClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        super.onChannelClosed(channel, closeReason, appSpecificErrorCode);
    }

    @Override
    public void onInputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        super.onInputClosed(channel, closeReason, appSpecificErrorCode);
    }

    @Override
    public void onOutputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        super.onOutputClosed(channel, closeReason, appSpecificErrorCode);
    }



    @Override
    public void onCreate() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .build();
        }

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mGoogleApiClient == null) return;
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        // Need to freeze the dataEvents so they will exist later on the UI thread
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        for (DataEvent event : events) {
            final Uri uri = event.getDataItem().getUri();
            if ("/gz".equals(uri.getPath())) {
                final DataMap map = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();

                // collect data from watch, and pass to main activity
                // TODO: Maybe just do work here in the service?
                if (map.containsKey(SERVICE_CALLED_WEAR)) {
                    Asset asset = map.getAsset(SERVICE_CALLED_WEAR);
                    if (asset == null) continue;

                    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                            mGoogleApiClient, asset).await().getInputStream();

                    if (assetInputStream == null) {
                        Log.w(TAG, "Requested an unknown Asset.");
                        return;
                    }

                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                    int nRead;
                    byte[] data = new byte[1024];
                    try {
                        while ((nRead = assetInputStream.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }
                        buffer.flush();
                    } catch (IOException ex) {
                        Log.e(TAG, "Problem occured while trying to read input stream");
                    }

                    String msg = null;
                    StringWriter writer = new StringWriter();
                    try {
                        GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(buffer.toByteArray()));
                        InputStreamReader r = new InputStreamReader(in);
                        char[] charBuffer = new char[1024];

                        while ((nRead = r.read(charBuffer, 0, charBuffer.length)) > 0) {
                            writer.write(charBuffer, 0, nRead);
                        }

                        msg = writer.toString();
                    } catch (IOException ex) {
                        Log.w(TAG, "Failed to decompress gzip contents: " + ex.getMessage());
                    }



                    if (msg != null) {
                        Intent intent = new Intent(INTENT_FILTER);
                        intent.putExtra(SERVICE_ACTION, msg);

                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

                        Log.d(TAG, "Asset received: " + msg);
                    }
                }
            }
        }
        //super.onDataChanged(dataEvents);
    }
}
