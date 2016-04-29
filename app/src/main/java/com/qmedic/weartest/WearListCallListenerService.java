package com.qmedic.weartest;

/**
 * Created by jun_e on 4/29/2016.
 */

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;




public class WearListCallListenerService extends WearableListenerService {
    public static String SERVICE_CALLED_WEAR = "WearListClicked";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        String event = messageEvent.getPath();
        byte[] b = messageEvent.getData();
        String msg = new String(b);
        Log.d("Listclicked", event);
    }
}
