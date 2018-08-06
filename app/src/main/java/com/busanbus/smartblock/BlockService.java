package com.busanbus.smartblock;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/*
1. screen block
 */

public class BlockService extends Service implements View.OnTouchListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = BlockService.class.getSimpleName();

    private WindowManager wm;
    private View floatyView;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand : " + flags + ", " + startId);

        /*
        for test
         */
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("block_service_status", true).apply();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if( !pm.isScreenOn() ) {
            Log.d(TAG, "screen off : stopSelf");
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate");

        if( checkDrawOverlayPermission() == false ) {
            stopSelf();
            return;
        }

        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(this);
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("block_service_status", false).apply();

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        addOverlayView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy");

        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).unregisterOnSharedPreferenceChangeListener(this);
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("stop_block_service", false).apply();
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("block_service_status", false).apply();

        removeOverlayView();


    }

    private void addOverlayView() {

        Log.d(TAG, "addOverlayView");

        final WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.CENTER | Gravity.START;
        params.x = 0;
        params.y = 0;

        floatyView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.floating_view, null);
        floatyView.setOnTouchListener(this);
        wm.addView(floatyView, params);
    }

    private void removeOverlayView() {

        Log.d(TAG, "removeOverlayView");

        if(wm != null)
            wm.removeView(floatyView);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        Log.d(TAG, "onTouch");

        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("block_service_status", false).apply();


        return false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {



        Log.d(TAG, "onSharedPreferenceChanged : " + key);

        if( key.equals("stop_block_service") ) {

            Boolean v = sharedPreferences.getBoolean(key, false);

            if(v) {

                Log.d(TAG, "call stopSelf");
                stopSelf();
            }


        } else if(key.equals("all_stop")) {
            Boolean v = sharedPreferences.getBoolean(key, false);

            if(v) {

                Log.d(TAG, "call stopSelf");
                stopSelf();
            }
        }

    }

    private boolean checkDrawOverlayPermission() {

        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "checkDrawOverlayPermission : no permission");

            return false;

        }

        return true;
    }
}
