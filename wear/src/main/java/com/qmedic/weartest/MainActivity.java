package com.qmedic.weartest;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MainActivity extends Activity implements SensorEventListener,GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private TextView mTextView;
    private static final String TAG = "WEAR TEST";
    private SensorManager mSensorMgr;
    private Sensor mAccel;

    private FileOutputStream outStream = null;
    private String lastFileName = null;

    private static final SimpleDateFormat AMBIENT_DATE_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);

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

        // Set up accelerometer sensor
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
                        // Now you can use the Data Layer API
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
                        // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        // keep screen on (debug only)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
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

            writeToFile(text);
            clearFiles();
        }
    }

    private String getTempFileName(final Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss");
        return "temp-" + sdf.format(date) + ".csv";
    }

    private void writeToFile(String text) {
        openOutFileStream();
        if (outStream == null) return;

        try {

           // outStream.write((text + "\n").getBytes());
            Log.i(TAG, text);
        } catch (Exception e) {
            Log.e("no-print-line", e.getMessage());
        }
    }

    private void clearFiles() {
        String[] files = fileList();
        for (String file : files) {
            deleteFile(file);
        }
    }

    private void openOutFileStream() {
        //TODO: Pass in a large string to be appended to every second for now (expand interval later on)
        Date date = new Date();
        String tempFileName = getTempFileName(date);

        try {
            outStream = openFileOutput(tempFileName, MODE_APPEND);
            outStream.close();
            outStream = null;

            queueFileTransfer(tempFileName);
        } catch (Exception ex) {
            Log.e("no-open-file", ex.getMessage());
        }
    }

    private void queueFileTransfer(String filename) {
        String gzipFilename = filename + ".gz";

        try {
            // read file to be gzipped
            //File file = getFileStreamPath(filename);
            //FileInputStream in = new FileInputStream(file);

            // prepare file to be gzipped
            GZIPOutputStream gzipOut = new GZIPOutputStream(openFileOutput(gzipFilename, MODE_PRIVATE));
            gzipOut.write(("I'm a test at " + new Date().toString()).getBytes());

            //// gzip contents
            byte[] buffer; // = new byte[4096];
            //int len = 0;
            //while ((len = in.read(buffer)) > 0) {
            //    gzipOut.write(buffer);
            //}

            // close files
            //in.close();
            gzipOut.finish();
            gzipOut.close();

            // get gzipped contents to transfer
            FileInputStream inFile = openFileInput(gzipFilename);
            int fileSize = (int)inFile.getChannel().size();
            buffer = new byte[fileSize];
            inFile.read(buffer);
            inFile.close();

            // perform transfer
            Asset asset = Asset.createFromBytes(buffer);
            PutDataMapRequest dataMap = PutDataMapRequest.create("/gz");
            dataMap.getDataMap().putAsset("qmedic", asset);
            PutDataRequest request = dataMap.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, request);

            // on device...follow the 'mobile side'
            // http://stackoverflow.com/a/28356039

        } catch (Exception ex) {
            Log.e(TAG, "Failed to transfer file " + filename + " to android device.");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
