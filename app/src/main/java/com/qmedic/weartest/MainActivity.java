package com.qmedic.weartest;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.view.WatchViewStub;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;

import java.util.List;

public class MainActivity extends Activity {
    // intent filter must be the same as the service to capture these events
    private static final String INTENT_FILTER = "SPADES_INTENT_FILTER";

    private static final String TAG = "SPADES_APP";
    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";
    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);

    private TextView mTextView;
    private BeaconManager mBeaconManager = null;

    private BroadcastReceiver localReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = intent.getStringExtra(WearListCallListenerService.SERVICE_ACTION);
            if (mTextView != null) {
                mTextView.setText(msg);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                mTextView.setMovementMethod(new ScrollingMovementMethod());
            }
        });

        // Set up estimote
        mBeaconManager = new BeaconManager(this.getApplicationContext());
        mBeaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, List<Beacon> beacons) {
                Log.d(TAG, "Beacons: " + beacons);
            }
        });

        // configure broadcast manager for capturing service events
        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(localReceiver, new IntentFilter(INTENT_FILTER));
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mBeaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                try {
                    mBeaconManager.startRanging(ALL_ESTIMOTE_BEACONS);
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot start ranging", e);
                }
            }
        });

        //Start our own service for monitoring data transfer between device and android wear
        Intent intent = new Intent(MainActivity.this, WearListCallListenerService.class);
        startService(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();

        try {
            mBeaconManager.stopRanging(ALL_ESTIMOTE_BEACONS);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot stop but it does not matter now", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBeaconManager.disconnect();

        // clean up broadcast watches
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver);
    }
}
