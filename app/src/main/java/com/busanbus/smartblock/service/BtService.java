package com.busanbus.smartblock.service;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.busanbus.smartblock.R;
import com.busanbus.smartblock.UserRef;
import com.busanbus.smartblock.model.UserData;
import com.busanbus.smartblock.model.UserDriveData;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/*
1. always alive
2. bt ble, beacon check
3. speed check

 */
public class BtService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener, BeaconConsumer, LocationListener {

    private static final String TAG = BtService.class.getSimpleName();

    boolean allow_overlay = false;
    boolean allow_location = false;
    boolean allow_gps = false;
    private TimerTask mTask;
    private Timer mTimer;
    double mySpeed;
    private LocationManager mLocationMgr;
    private LocationListener mLocationListener;
    private boolean mLocationRegistered = false;
    private int mCount = 0;
    ArrayList<Double> mSpeedList = new ArrayList<>();
    private boolean mBlock = false;
    private boolean mLocationRunning = false;
    Notification mNoti = null;
    private final double STAND_STILL = 1.0;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand : " + flags + ", " + startId);



        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if( !pm.isScreenOn() ) {
            Log.d(TAG, "screen off : stopSelf");
            stopSelf();
        } else {

            if(mNoti ==  null ) {
                mNoti = new NotificationCompat.Builder(this)
                        .setContentTitle("SmartBlock")
                        .setTicker("")
                        .setContentText("단말기와 연결됨")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setLargeIcon(Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888))
                        .setContentIntent(null)
                        .setOngoing(true)
                        .build();
                startForeground(1, mNoti);


            }

        }





        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate");

        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(this);

        /*
        Location
         */
        mLocationMgr = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        registerLocationUpdates();


        /*
        1. 10초 주기로 permission 체크
         */
        regularPermissionCheck();





        /*
        1. over 설정 체크
        2. false 면 permission activity 실행
         */
        boolean allow = checkPermission();


        /*
        1. speed check pass
        2. beacon signal catch
         */




    }



    private void regularPermissionCheck() {
        Log.d(TAG, "regularPermissionCheck");
        mTask = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "regularPermissionCheck timeout");
                boolean allow = checkPermission();
                if(allow == false) {
                    stopBlockService();
                } else {
                    /*
                    1. block service 가 종료되어 있는 경우 block service 실행
                    2. block service 가 그려져야 하는 조건 함께 체크
                    3. 지금은 조건 충족으로 판단
                     */

                    Log.d(TAG, "regularPermissionCheck block : " + mBlock + ", my speed : " + mySpeed + ", location run : " + mLocationRunning);

                    if( mLocationRunning == false && mySpeed < STAND_STILL ) {
                        mBlock = false;
                    }

                    if( mBlock ) {

                        startBlockService();

                    } else {
                        stopBlockService();
                    }



                    mSpeedList.clear();

                    mLocationRunning = false;

                }

                Thread th = Thread.currentThread();
                Log.d(TAG, "regularPermissionCheck thread info : " + th.toString());



                registerLocationUpdates();
            }
        };

        mTimer = new Timer();
        mTimer.schedule(mTask, 10000, 10000);
    }

    private void stopBlockService() {
        Log.d(TAG, "stopBlockService");
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("stop_block_service", false).apply();
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("stop_block_service", true).apply();

        UserRef.userDriveData.setState_drive("2000");
        UserRef.userDriveData.setTime_start(getCurTime());
        UserRef.userDriveRef.setValue(UserRef.userDriveData);

    }

    private void startBlockService() {
        Log.d(TAG, "startBlockService");
        Intent svc = new Intent(this, BlockService.class);
        startService(svc);

        UserRef.userDriveData.setState_drive("2100");
        UserRef.userDriveData.setTime_start(getCurTime());
        UserRef.userDriveRef.setValue(UserRef.userDriveData);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy");

        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).unregisterOnSharedPreferenceChangeListener(this);



        if(mTimer != null)
            mTimer.cancel();

        if(mLocationMgr != null)
            mLocationMgr.removeUpdates(this);

        stopForeground(true);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {


        Log.d(TAG, "onSharedPreferenceChanged : " + key );

        if(key.equals("all_stop")) {
            Boolean v = sharedPreferences.getBoolean(key, false);

            if(v) {

                Log.d(TAG, "call stopSelf");
                stopSelf();
            }
        }


    }

    @Override
    public void onBeaconServiceConnect() {


    }

    private boolean checkDrawOverlayPermission() {

        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "checkDrawOverlayPermission : not allow");

            return false;

        }

        Log.d(TAG, "checkDrawOverlayPermission : allow");


        return true;
    }

    private boolean checkLocationPermission() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "checkLocationPermission : not allow");


            return false;
        }

        Log.d(TAG, "checkLocationPermission : allow");


        return true;

    }

    private boolean checkGps() {

        if (mLocationMgr.isProviderEnabled(LocationManager.GPS_PROVIDER) == false) {

            Log.d(TAG, "checkGps : not allow");


            return false;
        }

        Log.d(TAG, "checkGps : allow");


        return true;
    }

    private boolean checkPermission() {

        allow_overlay = checkDrawOverlayPermission();
        allow_location = checkLocationPermission();
        allow_gps = checkGps();

        Log.d(TAG, "checkPermission : overlay : " + allow_overlay + ", location : " + allow_location + ", gps : " + allow_gps);

        if( allow_overlay == false || allow_location == false || allow_gps == false ) {

            return false;
        }

        return true;
    }

    @Override
    public void onLocationChanged(Location location) {

        Log.d(TAG, "onLocationChanged");

        if (location != null) {
            mCount++;
            Log.d(TAG, ": " + location.getProvider());
            mySpeed = location.getSpeed();

            Thread th = Thread.currentThread();
            Log.d(TAG, "onLocationChanged thread info : " + th.toString());

            mLocationRunning = true;

            mSpeedList.add(mySpeed);


            if(mSpeedList.size() > 0) {

                int count = 0;

                for(double i : mSpeedList) {

                    Log.d(TAG, "onLocationChanged speed : " + i);

                    if(i > STAND_STILL ) {
                        count++;
                    }
                }

                if(count > 0) {
                    mBlock = true;
                } else {
                    mBlock = false;
                }

            }

//            Toast.makeText(this, location.getProvider() + "(" +mCount + ")"+ " : "+ "\nCurrent Speed : " + (mySpeed*3.6), Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

        Log.d(TAG, "onStatusChanged");

    }

    @Override
    public void onProviderEnabled(String provider) {

        Log.d(TAG, "onProviderEnabled");

    }

    @Override
    public void onProviderDisabled(String provider) {

        Log.d(TAG, "onProviderDisabled");

    }

    private void registerLocationUpdates() {

        if( mLocationRegistered == false ) {
            if(checkLocationPermission()) {

                mLocationMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this);
                mLocationMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);

                mLocationRegistered = true;
            }

        }

        Log.d(TAG, "registerLocationUpdates : registered : " + mLocationRegistered);

    }

    private String getCurTime(){
        SimpleDateFormat mFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long mNow = System.currentTimeMillis();
        Date mDate = new Date(mNow);
        return mFormat.format(mDate);
    }
}
