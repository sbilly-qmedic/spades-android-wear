package com.qmedic.weartest;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

public class MainActivity extends Activity implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "QMEDIC_WEAR";
    private static final String SERVICE_CALLED_WEAR = "QMEDIC_DATA_MESSAGE";
    private static final String HEADER_LINE = "HEADER_TIMESTAMP,X,Y,Z\n";
    private static final String TEMP_FILE_DATE_FORMAT = "yyyy-MM-dd_HH_mm";
    private static final int BUFFER_SIZE = 4096;
    private static final int EXPIRATION_IN_MS = 10 * 1000; // 60 * 60 * 1000; // 1 hour (1000ms * 60s * 60min)

    private GoogleApiClient mGoogleApiClient;
    private SensorManager mSensorMgr;
    private Sensor mAccel;
    private TextView mTextView;
    private long mUptimeMillis;

    private StringBuilder buffer = new StringBuilder();
    private String fileToQueueForTransfer = null;
    private OutputStreamWriter currentWriter = null;
    private Date tempFileExpirationDateTime = null;

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

        if (mGoogleApiClient == null) return;
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mAccel != null) {
            mSensorMgr.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        long elapsedRealtime = SystemClock.elapsedRealtime();
        long currentTimeMillis = System.currentTimeMillis();
        mUptimeMillis = currentTimeMillis - elapsedRealtime;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        long elapsedRealtime = SystemClock.elapsedRealtime();
        long currentTimeMillis = System.currentTimeMillis();
        mUptimeMillis = currentTimeMillis - elapsedRealtime;

        tryUploadAndDeleteOldFiles();
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
        String text = null;
        Date date = getDateFromEvent(event);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                DecimalFormat df = new DecimalFormat("###.##");
                text = String.format(
                    "%s, %s, %s, %s\n",
                    sdf.format(date),
                    df.format(x),
                    df.format(y),
                    df.format(z));
                break;

            case Sensor.TYPE_HEART_RATE:
                // TODO: do some heart rate stuff
                break;

            default:
                // ignore
                break;
        }

        if (mTextView != null) {
            mTextView.setText(text);
        }

        if (date != null && text != null) {
            writeMessage(date, text);
        }
    }

    /**
     * Gets the datetime from the event timestamp. (Credit: http://stackoverflow.com/a/9333605)
     * @param sensorEvent - The sensor event containing the timestamp
     * @return {Date} - The datetime of the event
     */
    private Date getDateFromEvent(SensorEvent sensorEvent) {
        long timeInMillis = (sensorEvent.timestamp / 1000000) + mUptimeMillis;
        return new Date(timeInMillis);
    }

    /**
     * Send the message to be written and potentially queued to device
     * @param date - The datetime of the message
     * @param msg - The message to be sent
     */
    private void writeMessage(final Date date, final String msg) {
        // check if we should send file contents to device
        if (shouldSendFile(date)) {
            flushBufferToFile(fileToQueueForTransfer);
            setExpiration(date);
            closeCurrentWriter();

            FileUploadTask task = new FileUploadTask(mGoogleApiClient, getFilesDir(), fileToQueueForTransfer);
            task.execute();
        }

        StringBuilder buffer = getBuffer();
        buffer.append(msg);
        if (buffer.length() >= BUFFER_SIZE) {
            try {
                OutputStreamWriter writer = getCurrentWriter(date);
                if (writer != null) {
                    writer.write(buffer.toString());
                    buffer.setLength(0);
                }
            } catch (IOException ex) {
                Log.w(TAG, ex.getMessage());
            }
        }
    }

    public StringBuilder getBuffer() {
        if (buffer == null) {
            buffer = new StringBuilder();
        }
        return buffer;
    }

    private void flushBufferToFile(String filename) {
        StringBuilder buffer = getBuffer();
        if (buffer.length() == 0) return;

        if (fileToQueueForTransfer == null) return;
        OutputStreamWriter writer = openNewWriter(fileToQueueForTransfer);
        if (writer == null) return;

        try {
            writer.write(buffer.toString());
            buffer.setLength(0);
        } catch (IOException ex) {
            Log.w(TAG, "Failed to empty out buffer contents to file " + fileToQueueForTransfer + ": " + ex.getMessage());
        }
    }

    /**
     * Get the temp filename for the file being written
     * @param date - The date to associate the filename with
     * @return - The temp filename to write to
     */
    private String getTempFileName(final Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat(TEMP_FILE_DATE_FORMAT);
        Date d = shouldSendFile(date) ? date : tempFileExpirationDateTime;
        return "temp-" + sdf.format(d) + ".csv";
    }

    /**
     * Set the expiration date (used with determining when to write to a new file)
     * @param date - The date used in relation to setting the expiration date
     */
    private void setExpiration(final Date date) {
        tempFileExpirationDateTime = date;
        tempFileExpirationDateTime.setTime(tempFileExpirationDateTime.getTime() + EXPIRATION_IN_MS);
    }

    /**
     * Check whether if we should queue the file to be sent to the device
     * @param date - The date to check against
     * @return True, if the date is past the expiration. False, if otherwise.
     */
    private boolean shouldSendFile(final Date date) {
        if (tempFileExpirationDateTime == null) setExpiration(date);
        return date.compareTo(tempFileExpirationDateTime) > 0;
    }

    /**
     * Gets the output stream writer
     * @param date - The date used for getting the stream writer (Or creating a new one)
     * @return - The stream writer, or null if a problem occurred
     */
    private OutputStreamWriter getCurrentWriter(final Date date) {
        String tempFileName = getTempFileName(date);
        if (!tempFileName.equals(fileToQueueForTransfer) || currentWriter == null) {
            fileToQueueForTransfer = tempFileName;
            closeCurrentWriter();
        }

        if (currentWriter == null) {
            try {
                OutputStreamWriter writer = openNewWriter(tempFileName);
                if (writer == null) return null;

                writer.write(HEADER_LINE);
                currentWriter = writer;
            } catch (IOException ex) {
                Log.e(TAG, ex.getMessage());
            }
        }

        return currentWriter;
    }

    private OutputStreamWriter openNewWriter(String filename) {
        try {
            FileOutputStream outStream = openFileOutput(filename, MODE_APPEND);
            return new OutputStreamWriter(outStream);
        } catch (IOException ex) {
            Log.e(TAG, ex.getMessage());
        }
        return null;
    }

    /**
     * Closes the current stream writer
     */
    private void closeCurrentWriter() {
        if (currentWriter == null) return;

        try {
            currentWriter.close();
        } catch (IOException ex) {
            Log.i(TAG, "Closing current writer. Got: " + ex.getMessage());
        } finally {
            currentWriter = null;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onConnected(Bundle bundle) {
        // try to upload (and delete) all files, except for the current temp file
        tryUploadAndDeleteOldFiles();
    }

    private void tryUploadAndDeleteOldFiles() {
        String[] files = fileList();
        Date d = new Date();
        String tempFilename = getTempFileName(d);
        File fileDir = getFilesDir();

        for (int i = 0; i < files.length; i++) {
            if (tempFilename == files[i]) {
                continue;
            }

            new FileUploadTask(mGoogleApiClient, fileDir, files[i]).execute();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Empty out the buffer to the current file.

        // if a file was in the midst of being sent was interrupted,
        // ensure that file isn't deleted.
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // can't do anything with a failed connection...maybe do nothing?
    }
}

/*
    Things to do
        - Can you try recording a video or send over some images/screenshots of the wear+smartphone apps in action?
        - Make the necessary checks for managing the connection between the watch and the device
        - Figure out how to get watch/app code to run, even if the screen goes out
*/