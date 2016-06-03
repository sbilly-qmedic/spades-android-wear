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
    private static final int BUFFER_SIZE = 4096;
    private static final int EXPIRATION_IN_MS = 5 * 1000; // 60 * 60 * 1000; // 1 hour (1000ms * 60s * 60min)

    private GoogleApiClient mGoogleApiClient;
    private SensorManager mSensorMgr;
    private Sensor mAccel;
    private TextView mTextView;
    private StringBuilder buffer = new StringBuilder();
    private String fileToQueueForTransfer = null;
    private OutputStreamWriter currentWriter = null;
    private Date tempFileExpirationDateTime = null;
    private long mUptimeMillis; // member variable of the activity or service

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

        long elapsedRealtime = SystemClock.elapsedRealtime();
        long currentTimeMillis = System.currentTimeMillis();
        mUptimeMillis = currentTimeMillis - elapsedRealtime;
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
        String text = null;
        Date date = null;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                date = getDateFromEvent(event);
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                DecimalFormat df = new DecimalFormat("###.##");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
                text = String.format(
                    "%s, %s, %s, %s\n",
                    sdf.format(date),
                    df.format(x),
                    df.format(y),
                    df.format(z));

            case Sensor.TYPE_HEART_RATE:
                // TODO: do some heart rate stuff

            default:
                // ignore
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
        //TODO: Time is off by a few days + hours....investigate this a bit more
        long timeInMillis = ((sensorEvent.timestamp / 1000000) + mUptimeMillis);
        return new Date(timeInMillis);
    }

    /**
     * Send the message to be written and potentially queued to device
     * @param date - The datetime of the message
     * @param msg - The messsage to be sent
     */
    private void writeMessage(final Date date, final String msg) {
        // check if we should send file contents to device
        if (shouldSendFile(date)) {
            flushBufferToFile(fileToQueueForTransfer);
            setExpiration(date);
            closeCurrentWriter();

            FileUploadTask task = new FileUploadTask(fileToQueueForTransfer);
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
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss");
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
     * Get's the output stream writer
     * @param date - The date used for getting the stream writer (Or creating a new one)
     * @return - The stream writer, or null if a problem occured
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
     * Async task used for queuing a file transfer
     */
    private class FileUploadTask extends AsyncTask<Void, Void, Void> {
        private static final int BUFFER_SIZE = 8192;
        private static final String FILE_TRANSFER_TAG = TAG + " - Runner";
        private static final String EXTENSION = "gz";

        private String filename;
        private String compressedFile;
        private StringBuilder bufferToEmpty = null;

        public FileUploadTask(String filename) {
            this.filename = filename;
            this.compressedFile = filename + "." + EXTENSION;
        }

        public void setBufferToEmpty(StringBuilder buffer) {
            bufferToEmpty = buffer;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (bufferToEmpty != null) {
                emptyOutBuffer();
            }

            if (!createCompressedFile()) {
                Log.i(FILE_TRANSFER_TAG, "Tried to compress the file but failed");
                return null;
            }

            byte[] compressedBytes = createByteArrayFromCompressedFile();
            if (compressedBytes == null) {
                Log.i(FILE_TRANSFER_TAG, "Tried to get compressed bytes but failed");
                return null;
            }

            broadcastMessage(compressedBytes);
            return null;
        }

        private void emptyOutBuffer() {
            try {
                FileOutputStream fileOut = openFileOutput(filename, MODE_APPEND);
                OutputStreamWriter writer = new OutputStreamWriter(fileOut);
                writer.write(bufferToEmpty.toString());
                writer.flush();
                writer.close();
                bufferToEmpty = null;
            } catch (IOException ex) {
                Log.w(FILE_TRANSFER_TAG, "Failed to empty out buffer contents to file " + filename + ": " + ex.getMessage());
            }
        }

        /**
         * Create a file which contains the raw contents gzipped.
         * @return True if the compression was successful. False, if otherwise.
         */
        private boolean createCompressedFile() {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;

            // Compress file contents
            try {
                FileInputStream inStream = openFileInput(filename);
                GZIPOutputStream outputStream = new GZIPOutputStream(openFileOutput(compressedFile, MODE_APPEND));
                while ((len = inStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, len);
                }

                inStream.close();
                outputStream.finish();
                outputStream.close();
            } catch (IOException ex) {
                Log.w(FILE_TRANSFER_TAG, "Failed to compress file " + filename + ": " + ex.getMessage());
                return false;
            }

            return true;
        }

        /**
         * Generate a byte array representing the compressed file contents
         * @return A byte array of the compressed contents. Null if we failed to generate the file.
         */
        private byte[] createByteArrayFromCompressedFile() {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            ByteArrayOutputStream out = null;

            try {
                FileInputStream in = openFileInput(compressedFile);
                out = new ByteArrayOutputStream();
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }

                in.close();
                out.close();
            } catch (Exception ex) {
                Log.w(TAG, "Failed to get file output stream for " + compressedFile + ": " + ex.getMessage());
                return null;
            }

            return out.toByteArray();
        }

        /**
         * Broadcasts the message (in bytes) to those connect to the local google client
         * @param messageInBytes - The message to be broadcasted in bytes
         */
        private void broadcastMessage(byte[] messageInBytes) {
            Asset asset = Asset.createFromBytes(messageInBytes);
            String extension = "/" + EXTENSION;

            PutDataMapRequest dataMap = PutDataMapRequest.create(extension);
            dataMap.getDataMap().putAsset(SERVICE_CALLED_WEAR, asset);
            PutDataRequest request = dataMap.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, request);
            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(final DataApi.DataItemResult result) {
                    if (result.getStatus().isSuccess()) {
                        Log.i(TAG, "File '" + compressedFile + "' was successfully sent to device.");

                        if (deleteFile(filename)) {
                            Log.i(TAG, "Deleted the last sent file " + filename);
                        }

                        if (deleteFile(compressedFile)) {
                            Log.i(TAG, "Deleted the gzipped last sent file " + compressedFile);
                        }
                    }
                }
            });
        }


    }
}

/*
    Things to do
        - Fix issue with event time
        - Collect heart rate data as well
        - Regarding the watch sampling rate, there's a property that you can set when you initialize the sensor itself (https://developer.android.com/guide/topics/sensors/sensors_motion.html). I don't remember if it's the same doc for Wear though.
        - ***Figure out how to get watch/app code to run, even if the screen goes out
        - Can you try recording a video or send over some images/screenshots of the wear+smartphone apps in action?
        - [WATCH SAMPLING RATE] Regarding the watch sampling rate, there's a property that you can set when you initialize the sensor itself (https://developer.android.com/guide/topics/sensors/sensors_motion.html). I don't remember if it's the same doc for Wear though.
        - Make the necessary checks for managing the connection between the watch and the device
*/