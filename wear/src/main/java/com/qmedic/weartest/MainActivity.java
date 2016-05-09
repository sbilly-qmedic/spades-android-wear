package com.qmedic.weartest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Region;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private TextView mTextView;
    private static final String TAG = "QMEDIC_WEAR";
    private static final String SERVICE_CALLED_WEAR = "QMEDIC_DATA_MESSAGE";

    private GoogleApiClient mGoogleApiClient;
    private SensorManager mSensorMgr;
    private Sensor mAccel;

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

        mSensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(mAccel != null) {
            mSensorMgr.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                @Override
                public void onConnected(Bundle connectionHint) {
                    Log.d(TAG, "onConnected: " + connectionHint);
                }

                @Override
                public void onConnectionSuspended(int cause) {
                    Log.d(TAG, "onConnectionSuspended: " + cause);
                }
            })
            .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(ConnectionResult result) {
                Log.d(TAG, "onConnectionFailed: " + result);
                }
            })
            .addApi(Wearable.API)
            .build();

        // keep screen on (debug only)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(mAccel != null) {
            mSensorMgr.unregisterListener(this, mAccel);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mGoogleApiClient == null) return;
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mGoogleApiClient == null) return;
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                Date date = new Date(event.timestamp);
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                DecimalFormat df = new DecimalFormat("###.##");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH");
                    String text = String.format(
                    "%s, %s, %s, %s",
                    sdf.format(date),
                    df.format(x),
                    df.format(y),
                    df.format(z));

                if (mTextView != null) {
                    mTextView.setText(text);
                }

                broadcastMessage(text.getBytes(), "txt");

            case Sensor.TYPE_HEART_RATE:
                // TODO: do some heart rate stuff

            default:
                // ignore
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onConnected(Bundle bundle) {
        //TODO: maybe some management stats here?
    }

    @Override
    public void onConnectionSuspended(int i) {
        //TODO: maybe some management stats here?
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        //TODO: maybe some management stats here?
    }

    /**
     * Broadcasts the message (in bytes) to those connect to the local google client
     *
     * @param messageInBytes - The message to be broadcasted in bytes
     */
    private void broadcastMessage(byte[] messageInBytes, String extension) {
        Asset asset = Asset.createFromBytes(messageInBytes);
        if (!extension.startsWith("/")) {
            extension = "/" + extension;
        }

        PutDataMapRequest dataMap = PutDataMapRequest.create(extension);
        dataMap.getDataMap().putAsset(SERVICE_CALLED_WEAR, asset);
        PutDataRequest request = dataMap.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request);
    }
}
