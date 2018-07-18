package com.busanbus.smartblock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AutoRunReceiver extends BroadcastReceiver {

    private static final String TAG = AutoRunReceiver.class.getSimpleName();


    @Override
    public void onReceive(Context context, Intent intent) {

        Log.i(TAG, "onReceive : " + intent.getAction());

        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            Log.i(TAG, "android.intent.action.BOOT_COMPLETED");

        }

    }
}
