package com.qmedic.weartest;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

import com.estimote.sdk.Region;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

public class MainActivity extends Activity implements
        DataApi.DataListener, ConnectionCallbacks, OnConnectionFailedListener {

    private static final String SERVICE_CALLED_WEAR = "WearService";
    private static final String TAG = "QMEDIC_APP";
    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";
    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);

    private TextView mTextView;
    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
            mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {

        super.onStart();

        //Start our own service
        Intent intent = new Intent(MainActivity.this, com.qmedic.weartest.WearListCallListenerService.class);
        startService(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }


    @Override
    public void onConnected(Bundle bundle) {
        // TODO: Handle case when app first connects to wear device?
    }

    @Override
    public void onConnectionSuspended(int i) {
        // TODO: Handle case when app is suspended from wear device? (Figure out what that means...)
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            final Uri uri = event.getDataItem().getUri();
            if ("/txt".equals(uri.getPath())) {
                final DataMap map = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                // read your values from map:
                if (map.containsKey(SERVICE_CALLED_WEAR)) {
                    Asset asset = map.getAsset(SERVICE_CALLED_WEAR);
                    String msg = new String(asset.getData());
                    Log.d(TAG, msg);

                    if (mTextView != null) {
                        mTextView.setText(msg);
                    }
                }
            }
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // TODO: Figure out what to do when when connection to device fails?
    }
}
