package com.qmedic.weartest;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;

import java.text.DecimalFormat;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = "WEAR TEST";

    private static final String ESTIMOTE_PROXIMITY_UUID = "B9407F30-F5F8-466E-AFF9-25556B57FE6D";
    private static final Region ALL_ESTIMOTE_BEACONS = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);

    private TextView mTextView;
    private SensorManager mSensorMgr;
    private Sensor mAccel;

    private BeaconManager mBeaconManager = null;

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

        // Set up accelerometer sensor
        mSensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(mAccel != null) {
            mSensorMgr.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Set up estimote
        mBeaconManager = new BeaconManager(this.getApplicationContext());
        mBeaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, List<Beacon> beacons) {
                Log.d(TAG, "Beacons: "+beacons);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mAccel!=null) {
            mSensorMgr.unregisterListener(this, mAccel);
        }
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
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            String now = String.valueOf(event.timestamp);
            DecimalFormat df = new DecimalFormat("###.##");
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            String text = df.format(x)+", "+df.format(y)+", "+df.format(z);
            Log.i(TAG, now+": "+text);
            mTextView.setText(text);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


}
