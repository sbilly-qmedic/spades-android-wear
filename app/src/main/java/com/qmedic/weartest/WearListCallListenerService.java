package com.qmedic.weartest;

/**
 * Created by jun_e on 4/29/2016.
 */

import android.content.Intent;
import android.net.Uri;

import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class WearListCallListenerService extends WearableListenerService {
    public static String SERVICE_CALLED_WEAR = "QMEDIC_DATA_MESSAGE";
    public static String SERVICE_ACTION = "QMEDIC_DATA_ENTRY";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        String event = messageEvent.getPath();
        Log.d("Listclicked", event);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO Auto-generated method stub
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        for (DataEvent event : dataEvents) {
            final Uri uri = event.getDataItem().getUri();
            if ("/txt".equals(uri.getPath())) {
                final DataMap map = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                // collect data from watch, and pass to main activity
                // TODO: Maybe just do work here in the service?
                if (map.containsKey(SERVICE_CALLED_WEAR)) {
                    String msg = map.getString(SERVICE_CALLED_WEAR);
                    if (msg == null) continue;

                    Intent intent = new Intent();
                    intent.setAction(SERVICE_ACTION);
                    intent.putExtra("DATAPASSED", msg);

                    sendBroadcast(intent);

                    Log.d("QMEDIC", msg);
                }
            }
        }
    }
}
