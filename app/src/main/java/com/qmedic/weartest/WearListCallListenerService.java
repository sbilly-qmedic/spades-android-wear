package com.qmedic.weartest;

import android.content.Intent;
import android.net.Uri;

import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class WearListCallListenerService extends WearableListenerService {
    // intent filter must be the same as the app to broadcast these events
    private static final String INTENT_FILTER = "SPADES_INTENT_FILTER";

    // The tag used to identify which assets came from the watch
    public static final String SERVICE_CALLED_WEAR = "QMEDIC_DATA_MESSAGE";

    // The tag used to identify which service action should the app listen to
    public static final String SERVICE_ACTION = "QMEDIC_DATA_ENTRY";

    // The log tag used when recording messages
    private static final String TAG = "QMEDIC_APP_SERVICE";

    // The api client used to communicate with google play services
    private GoogleApiClient mGoogleApiClient;

    public WearListCallListenerService() {
        super();
    }

    @Override
    public void onCreate() {
        // Ensure that our client can communicate with the wearable API
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
        super.onDataChanged(dataEvents);

        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        for (DataEvent event : events) {
            final Uri uri = event.getDataItem().getUri();

            // Match only events whose URI end with '/gz'
            if ("/gz".equals(uri.getPath())) {
                final DataMap map = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();

                if (map.containsKey(SERVICE_CALLED_WEAR)) {
                    Asset asset = map.getAsset(SERVICE_CALLED_WEAR);
                    if (asset == null) continue;
                    processGZippedAsset(asset);
                }
            }
        }
    }

    /**
     * Reads the compressed file from the asset and transfers it to the app
     * @param asset - The asset to be processed
     */
    private void processGZippedAsset(Asset asset) {
        // Transform asset into an input stream
        InputStream assetInputStream = Wearable
            .DataApi
            .getFdForAsset(mGoogleApiClient, asset)
            .await()
            .getInputStream();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return;
        }

        // Transform input stream into an output stream byte array
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

        // Feed output stream into GZIPInputStream and finally decompress contents
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

        // Transfer decompressed contents to the app
        if (msg != null) {
            Intent intent = new Intent(INTENT_FILTER);
            intent.putExtra(SERVICE_ACTION, msg);

            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            Log.d(TAG, "Asset received: " + msg);
        }
    }
}
