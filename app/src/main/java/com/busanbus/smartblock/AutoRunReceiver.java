package com.busanbus.smartblock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.busanbus.smartblock.activity.MainActivity;


public class AutoRunReceiver extends BroadcastReceiver {

    private static final String TAG = AutoRunReceiver.class.getSimpleName();


    @Override
    public void onReceive(Context context, Intent intent) {

        Log.i(TAG, "onReceive : " + intent.getAction());

        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            Log.i(TAG, "android.intent.action.BOOT_COMPLETED");

            Intent i = new Intent(context, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);

        }

    }
}
