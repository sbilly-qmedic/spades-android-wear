package com.qmedic.weartest;

/**
 * Created by jun_e on 6/6/2016.
 */

import android.os.AsyncTask;
import android.util.Log;

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
import java.util.zip.GZIPOutputStream;

/**
 * Async task used for queuing a file transfer
 */
public class FileUploadTask extends AsyncTask<Void, Void, Void> {
    private static final int BUFFER_SIZE = 8192;
    private static final String FILE_TRANSFER_TAG = "FILE_UPLOAD_TASK";
    private static final String EXTENSION = "gz";
    private static final String SERVICE_CALLED_WEAR = "QMEDIC_DATA_MESSAGE";

    private GoogleApiClient mGoogleApiClient;
    private String filename;
    private String compressedFile;
    private StringBuilder bufferToEmpty = null;

    public FileUploadTask(final GoogleApiClient client, final File fileDir, final String filename) {
        mGoogleApiClient = client;

        this.filename = fileDir.getAbsolutePath() + "/" + filename;
        this.compressedFile = this.filename + ".gz";
    }

    public void setBufferToEmpty(final StringBuilder buffer) {
        bufferToEmpty = buffer;
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (!checkIfFileExists()) return null;

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

    private boolean checkIfFileExists() {
        File file = new File(filename);
        return file.exists() && !file.isDirectory();
    }

    private void emptyOutBuffer() {
        try {

            File file = new File(filename);
            FileOutputStream out = new FileOutputStream(file, true);
            OutputStreamWriter writer = new OutputStreamWriter(out);

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
            File file = new File(filename);
            FileInputStream inStream = new FileInputStream(file);

            File outFile = new File(compressedFile);
            FileOutputStream outStream = new FileOutputStream(outFile, true);

            GZIPOutputStream outputStream = new GZIPOutputStream(outStream);
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
        ByteArrayOutputStream out;

        try {
            FileInputStream in = new FileInputStream(compressedFile);
            out = new ByteArrayOutputStream();
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }

            in.close();
            out.close();
        } catch (Exception ex) {
            Log.w(FILE_TRANSFER_TAG, "Failed to get file output stream for " + compressedFile + ": " + ex.getMessage());
            return null;
        }

        return out.toByteArray();
    }

    /**
     * Broadcasts the message (in bytes) to those connect to the local google client
     * @param messageInBytes - The message to be broadcasted in bytes
     */
    private void broadcastMessage(final byte[] messageInBytes) {
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
                    Log.i(FILE_TRANSFER_TAG, "File '" + compressedFile + "' was successfully sent to device.");

                    if (deleteFile(filename)) {
                        Log.i(FILE_TRANSFER_TAG, "Deleted the last sent file " + filename);
                    }

                    if (deleteFile(compressedFile)) {
                        Log.i(FILE_TRANSFER_TAG, "Deleted the gzipped last sent file " + compressedFile);
                    }
                }
            }
        });
    }

    private boolean deleteFile(final String filename) {
        File file = new File(filename);
        if (!file.exists()) return false;

        return file.delete();
    }
}