package com.qmedic.weartest;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends Activity implements SensorEventListener {
    // The log tag used when recording messages
    private static final String TAG = "QMEDIC_WEAR";

    // The header line for each CSV report
    private static final String ACCEL_HEADER_LINE = "HEADER_TIMESTAMP,ACCELERATION_X,ACCELERATION_Y,ACCELERATION_Z\n";

    // The temp file name format used when sending files from the watch to the device
    private static final String TEMP_FILE_DATE_FORMAT = "yyyy-MM-dd_HH_mm";

    // The size of the buffer used in writing contents to disk memory
    private static final int BUFFER_SIZE = 4096;

    // The lifetime of the file before deciding to write to a new file
    private static final int FILE_LIFETIME_IN_HR = 1;


    // The api client used to communicate with Google Play services
    private GoogleApiClient mGoogleApiClient;

    // The sensor manager used to listen to events of interest
    private SensorManager mSensorMgr;

    // The sensor that is meant to correspond with the accelerometer
    private Sensor mAccel;

    // A text label used to show data on the screen
    private TextView mTextView;

    // The offset used to correct event timestamps
    private long timestampOffsetMS;


    // The buffer used to temporarily hold data before writing the contents to file
    private StringBuilder buffer = new StringBuilder();

    // The file that is next in line to be transferred from the watch to the device
    private String fileToQueueForTransfer = null;

    // The output stream writer that the buffer empties it's contents into
    private OutputStreamWriter currentWriter = null;

    // The time at which we should generate a new file
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

        // register the accelerometer to the sensor manager
        mSensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mAccel != null) {
            mSensorMgr.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // instantiate the google client used for communication with Google Play services
        mGoogleApiClient = new GoogleApiClient
                .Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);

                        // we've successfully connected to the device
                        // let's try and upload any 'old' files we have here.
                        tryUploadAndDeleteOldFiles();
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        // somehow our connection was interrupted...can't upload anything
                        // but can still continue doing our operations
                        Log.d(TAG, "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        // we failed to connect to the google API....can't upload anything
                        // but can still continue doing our operations
                        Log.d(TAG, "onConnectionFailed: " + result);
                    }
                })
                // connect to wearable API to enable data transfer between watch and device
                .addApi(Wearable.API)
                .build();

        // keep screen on (debug only)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // connect to Google Play services, this let's us send data between watch and device
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }

        // calculate the offset used in getting the normalized timestamp
        long elapsedRealtime = SystemClock.elapsedRealtime();
        long currentTimeMillis = System.currentTimeMillis();
        timestampOffsetMS = currentTimeMillis - elapsedRealtime;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mAccel != null) {
            mSensorMgr.unregisterListener(this, mAccel);
        }

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ensure that the sensor manager is still listener to the accelerometer
        if (mAccel != null) {
            mSensorMgr.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // always check that we're connected to google play services
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        // timestamp offset offset may change on resume, so recalculate it
        long elapsedRealtime = SystemClock.elapsedRealtime();
        long currentTimeMillis = System.currentTimeMillis();
        timestampOffsetMS = currentTimeMillis - elapsedRealtime;
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Date date = getDateFromEvent(event);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");

        // parse event data
        String text = null;
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
                // TODO Later: do some heart rate stuff
                break;

            default:
                // ignore
                break;
        }

        // show on screen
        if (mTextView != null) {
            mTextView.setText(text);
        }

        // write message to buffer (or to file)
        if (date != null && text != null) {
            writeMessage(date, text);
        }
    }

    /**
     * Send the message to be written and potentially queued to device
     *
     * @param date - The datetime of the message
     * @param msg  - The message to be sent
     */
    private void writeMessage(final Date date, final String msg) {
        // check if we should send file contents to device
        if (shouldSendFile(date)) {
            setExpiration(date);
            closeCurrentWriter();

            FileUploadTask task = new FileUploadTask(mGoogleApiClient, getFilesDir(), fileToQueueForTransfer);
            task.setBufferToEmpty(getBuffer());
            resetBuffer();
            task.execute();
        }

        // write out to the buffer
        StringBuilder buffer = getBuffer();
        buffer.append(msg);

        // check if the buffer is full
        if (buffer.length() >= BUFFER_SIZE) {
            try {
                // dump buffer contents and clear for next pass
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

    /**
     * Get the current buffer instance
     *
     * @return An instance of the buffer
     */
    private StringBuilder getBuffer() {
        if (buffer == null) {
            buffer = new StringBuilder();
        }
        return buffer;
    }

    /**
     * Clears out the current buffer
     */
    private void resetBuffer()
    {
        buffer = null;
    }

    /**
     * Get the temp filename for the file being written
     * @param date - The date to associate the filename with
     * @return - The temp filename to write to
     */
    private String getTempFileName(final Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat(TEMP_FILE_DATE_FORMAT);
        Date d = shouldSendFile(date) ? date : tempFileExpirationDateTime;
        d = clampDate(d);
        return "temp-" + sdf.format(d) + ".csv";
    }

    /**
     * Set the expiration date (used with determining when to write to a new file)
     * @param date - The date used in relation to setting the expiration date
     */
    private void setExpiration(final Date date) {
        Date clampedDate = clampDate(date);
        Calendar c = Calendar.getInstance();
        c.setTime(clampedDate);

        // set to next expiration time
        c.add(Calendar.HOUR_OF_DAY, FILE_LIFETIME_IN_HR);

        tempFileExpirationDateTime = c.getTime();
    }

    /**
     * Clamp the date to the top of the hour
     * @param date - The date to adjust
     * @return - The Date, shifted to the top of the hour
     */
    private Date clampDate(final Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);

        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MINUTE, 0);

        return c.getTime();
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

                writer.write(ACCEL_HEADER_LINE);
                currentWriter = writer;
            } catch (IOException ex) {
                Log.e(TAG, ex.getMessage());
            }
        }

        return currentWriter;
    }

    /**
     * Open an instance of an output stream to write into
     * @param filename - The name of the file to open
     * @return - An Output stream used for writing into
     */
    private OutputStreamWriter openNewWriter(final String filename) {
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

    /**
     * Try to upload (and delete) all older files currently stored within the app
     */
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

    /**
     * Gets the datetime from the event timestamp. (Credit: http://stackoverflow.com/a/9333605)
     * @param sensorEvent - The sensor event containing the timestamp
     * @return {Date} - The datetime of the event
     */
    private Date getDateFromEvent(final SensorEvent sensorEvent) {
        long timeInMillis = (sensorEvent.timestamp / 1000000) + timestampOffsetMS;
        return new Date(timeInMillis);
    }
}