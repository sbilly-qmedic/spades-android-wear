package com.qmedic.weartest;

/**
 * Created by jun_e on 4/29/2016.
 */

import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class WearListCallListenerService extends WearableListenerService {
    public static String SERVICE_CALLED_WEAR = "WearService";
    public static String SERVICE_ACTION = "PassData";

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
                // read your values from map:
                if (map.containsKey(SERVICE_CALLED_WEAR)) {
                    String msg = map.getString(SERVICE_CALLED_WEAR);
                    if (msg == null) continue;

                    //Asset asset = map.getAsset(SERVICE_CALLED_WEAR);
                    //byte[] assetData = asset.getData();
                    //if (assetData == null) continue;

                    //String msg = new String(assetData);

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
