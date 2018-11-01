package com.busanbus.smartblock.service;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import com.busanbus.smartblock.R;
import com.busanbus.smartblock.UserRef;
import com.busanbus.smartblock.activity.MainActivity;
import com.busanbus.smartblock.model.UserData;
import com.busanbus.smartblock.model.UserDriveData;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

/*
1. always alive
2. login check
3. permission check
 */

public class LoginService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener, BeaconConsumer {

    private static final String TAG = LoginService.class.getSimpleName();

    ScreenOnReceiver mScreenOnReceiver;


    private final static int fftChunkSize = 0x400;//0x1000;
    private final static int RATE = 44100;
    public final int BUFFER_SIZE = 1024;
    private short[] mAudioData;
    private AudioRecord mRecorder;
    boolean started = false;
    RecordAudio mRecordAudio = null;

    private TimerTask mTask;
    private Timer mTimer;
    private TimerTask mTask2;
    private Timer mTimer2;
    ArrayList<Double> mfrequencyList = new ArrayList<>();
    ArrayList<FreqInfo> mFreqInfos = new ArrayList<>();

    boolean allow_bt = false;
    boolean allow_location = false;
    boolean allow_mic = false;

    boolean isLogin = false;
    boolean allowUserDataUpdate = false;
    boolean allowUserDrivingUpdate = false;

    private static int CHANNEL_MODE = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private static int ENCODING = AudioFormat.ENCODING_PCM_16BIT;


    boolean final_apart = false;
    boolean final_batt = false;
    boolean final_normal = false;


    public final static int MIN_FREQUENCY = 8500; // 49.0 HZ of G1 - lowest
    // note
    // for crazy Russian choir.
    public final static int MAX_FREQUENCY = 22050; // 1567.98 HZ of G6 - highest
    // demanded note in the
    // classical repertoire

    //bt ble
    private final String DEV_NAME = "T_ISLAND";
    private final String DEV_NAME1 = "M_ISLAND";
    private final String DEV_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    Boolean btScanning = false;
    BluetoothDevice discoveredDev = null;
    BluetoothGatt bluetoothGatt = null;
    String devData = null;
    private boolean bleConnected = false;
    ArrayList<Boolean> mBeaconConnectList = new ArrayList<>();
    private String mMinor = "-";

    private Handler mHandler = new Handler();
    private static final long SCAN_PERIOD = 3000;
    int bleCheckCount = 0;



    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";


    /*beacon*/
    private BeaconManager beaconManager;

    String IBEACON_LAYOUT = "m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24";
    String ALTBEACON_LAYOUT = BeaconParser.ALTBEACON_LAYOUT;
    String EDDYSTONE_UID_LAYOUT = BeaconParser.EDDYSTONE_UID_LAYOUT;
    String EDDYSTONE_URL_LAYOUT = BeaconParser.EDDYSTONE_URL_LAYOUT;
    String EDDYSTONE_TLM_LAYOUT = BeaconParser.EDDYSTONE_TLM_LAYOUT;

    String target_uuid = "74278bda-b644-4520-8f0c-720eaf059935";

    private boolean beaconConnected = false;

    private View debugView;
    private WindowManager wm;
    private TextView debugInfo;
    private TextView stopReason;

    private Thread trd;
    private RecorderRunnable record;

    class FreqInfo {
        private double bestFreq;
        private int bestCount;

        FreqInfo(double freq, int count) {
            bestFreq = freq;
            bestCount = count;
        }

        public double getBestFreq() {
            return bestFreq;
        }

        public int getBestCount() {
            return bestCount;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand : " + flags + ", " + startId);

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1,new Notification());
        UserRef ref = new UserRef();
        getUserData();
        Log.d(TAG, "onCreate : " + " : current thread : " + Thread.currentThread());
        mAudioData = new short[BUFFER_SIZE];
        //bt ble
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        //beacon
        beaconManager = BeaconManager.getInstanceForApplication(this);

        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(IBEACON_LAYOUT));
//        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(ALTBEACON_LAYOUT));
        //beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(EDDYSTONE_URL_LAYOUT));
        //beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(EDDYSTONE_TLM_LAYOUT));

        beaconManager.setForegroundScanPeriod(1000l);
        beaconManager.bind(this);
//        mScreenOnReceiver = new ScreenOnReceiver();

//        IntentFilter filter = new IntentFilter();
//        filter.addAction("android.intent.action.SCREEN_ON");
//        filter.addAction("android.intent.action.SCREEN_OFF");
//        registerReceiver(mScreenOnReceiver, filter);

        checkPermisson();
        /*
        check frequency value periodically
         */
        checkFrequency();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        startRecord();

//        Intent svc = new Intent(LoginService.this, BlockService.class);
//        startService(svc);

        //addDebugView();

    }

    private void addDebugView() {

        Log.d(TAG, "addDebugView");

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        final WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.CENTER | Gravity.START;
        params.x = 0;
        params.y = 0;

        debugView = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.debug_view, null);
        debugInfo = debugView.findViewById(R.id.debug_info);
        stopReason = debugView.findViewById(R.id.stop_reason);

        wm.addView(debugView, params);

    }

    public void getUserData(){
        SharedPreferences pref = getSharedPreferences("pref", MODE_PRIVATE);
        final String phone = pref.getString("phone", "null");

        DatabaseReference database = FirebaseDatabase.getInstance().getReference();
        DatabaseReference database_userdata = database.child("UserData");

        database_userdata.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for(DataSnapshot dsp : dataSnapshot.getChildren()){

                    UserData tmp = dsp.getValue(UserData.class);
                    if(tmp.getPhone().equals(phone)){
                        UserRef.userData = tmp;
                        UserRef.userDataRef = dsp.getRef();

                        Log.e("LoginService", UserRef.userData.getPhone());
                        allowUserDataUpdate = true;
                        break;
                    }

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        DatabaseReference database_drivedata = database.child("UserDriveData");

        database_drivedata.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for(DataSnapshot dsp : dataSnapshot.getChildren()){

                    UserDriveData tmp = dsp.getValue(UserDriveData.class);
                    if(tmp.getPhone().equals(phone)){
                        UserRef.userDriveData = tmp;
                        UserRef.userDriveRef = dsp.getRef();

                        allowUserDrivingUpdate = true;
                        UserRef.allowUserDrivingUpdate = true;

                        break;
                    }

                }

            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        beaconManager.unbind(this);
        stopRecord();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        if(mScreenOnReceiver != null) {
            unregisterReceiver(mScreenOnReceiver);
        }
        stopForeground(true);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged : " + key );
        if("speed_info".equals(key)) {
            String info = sharedPreferences.getString(key, "error");
            //debugInfo.setText(info);
        }
        if("stop_reason".equals(key)) {
            String info = sharedPreferences.getString(key, "error");
            //stopReason.setText(info);
        }
//        Intent svc = new Intent(this, BtService.class);
//        svc.putExtra("key", key);
//        startService(svc);
    }

    public void startRecord() {
        Log.d(TAG, "startRecord : started : " + started );
        if(checkMicPermission()) {
            if(started == false) {
                started = true;
                mRecordAudio = new RecordAudio();
                mRecordAudio.execute();
            }
        }
    }

    public void stopRecord() {
        Log.d(TAG, "stopRecord " );
        started = false;
        if(mRecordAudio != null) {
            mRecordAudio.cancel(true);
            mRecordAudio = null;
        }
    }

    public void checkFrequency() {
        Log.d(TAG, "checkFrequency");
        mTask = new TimerTask() {
            @Override
            public void run() {
                checkPermisson();
                //Log.d(TAG, "mTask : run : thead " + Thread.currentThread());
                for(int i = 0; i < mBeaconConnectList.size(); i++) {
                    beaconConnected = mBeaconConnectList.get(i);
                    if(beaconConnected == true) break;
                }
                if(beaconConnected) {
                    Log.d(TAG, "mTask : run : beaconConnected : " + beaconConnected);
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", false).apply();
                    String reason = "none";
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("stop_reason", reason).apply();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent svc = new Intent(LoginService.this, BtService.class);
                        startForegroundService(svc);
                    } else {
                        Intent svc = new Intent(LoginService.this, BtService.class);
                        startService(svc);
                    }
                    if(allowUserDataUpdate) {
                        if(!isLogin){
                            UserRef.userData.setTime_login(getCurTime());
                            UserRef.userDataRef.setValue(UserRef.userData);

                        }
                        if(allowUserDrivingUpdate) {
                            UserRef.userDriveData.setTime_logout("-");
                            UserRef.userDriveRef.setValue(UserRef.userDriveData);
                        }
                        if(mMinor.equals("256")){
                            UserRef.userData.setState_ble("0000");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }
                        else if(mMinor.equals("257")){
                            UserRef.userData.setState_ble("0001");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }
                        else if(mMinor.equals("272")){
                            UserRef.userData.setState_ble("0010");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }
                        else if(mMinor.equals("273")){
                            UserRef.userData.setState_ble("0011");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }
                    }
                    isLogin = true;
                } else {
                    Log.d(TAG, "mTask : run not beaconConnected : " + final_apart + " : " + final_batt + " : " + final_normal);
                    if(allowUserDataUpdate) {
                        UserRef.userData.setState_ble("-");
                        UserRef.userDataRef.setValue(UserRef.userData);
                    }
                    if((final_apart == false) && (final_batt == false) && (final_normal == false)) {
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", true).apply();
                        String reason = "beacon is not connected && frequency is out of scope";
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("stop_reason", reason).apply();

                        if(allowUserDataUpdate) {
                            UserRef.userData.setState_frequency("-");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }
                        if(allowUserDrivingUpdate) {
                            UserRef.userDriveData.setTime_logout(getCurTime());
                            UserRef.userDriveRef.setValue(UserRef.userDriveData);
                        }
                    }
                    isLogin = false;
                }
                mBeaconConnectList.clear();
            }
        };

        mTask2 = new TimerTask() {
            @Override
            public void run() {
                //Log.d(TAG, "mTask2 : run : thread : " + Thread.currentThread());

                final_apart = false;
                final_batt = false;
                final_normal = false;

                if(mFreqInfos.size() > 0) {
                    Log.d(TAG, "mTask2 : run : mFreqInfos size : " + mFreqInfos.size() );

                    double best_freq = 0.0;
                    int best_count = 0;
                    double maxCount_freq = 0.0;
                    int max_count = 0;

                    for(int i=0;i < mFreqInfos.size(); i++) {
                        double freq_i = 0.0;
                        int count_i = 0;
                        FreqInfo info_i = mFreqInfos.get(i);
                        double val0 = info_i.getBestFreq();
                        Log.d(TAG, "mTask2 : run : mFreqInfos frequency : " + info_i.getBestFreq() + " : mFreqInfos count : " + info_i.getBestCount());
                        for(int j=i+1; j < mFreqInfos.size(); j++) {
                            FreqInfo info_j = mFreqInfos.get(j);
                            double val1 = info_j.getBestFreq();
                            if(val0 == val1) {
                                count_i++;
                            }
                        }
                        if(count_i > best_count) {
                            best_count = count_i;
                            best_freq = val0;
                        }
                        if(info_i.getBestCount() > max_count) {
                            max_count = info_i.getBestCount();
                            maxCount_freq = info_i.getBestFreq();
                        }
                    }
                    Log.d(TAG, "mTask2 : run : best frequency : " + best_freq + " : best count : " + best_count);
                    Log.d(TAG, "mTask2 : run : max frequency : " + maxCount_freq + " : max count : " + max_count);

                    double final_freq = 0.0;
                    if(best_freq > 0.0) {
                        final_freq = best_freq;
                    } else {
                        final_freq = maxCount_freq;
                    }

                    if(final_freq > 13000.0 && final_freq < 15300.0){
                        final_apart = true;
                    } else if(final_freq >= 15300.0 && final_freq < 18300.0){
                        final_batt = true;
                    } else if(final_freq >= 18300.0 && final_freq < 22000.0){
                        final_normal = true;
                    } else {
                        final_normal = true;
                    }

                    if(allowUserDataUpdate) {
                        if(!isLogin){
                            UserRef.userData.setTime_login(getCurTime());
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }
                        if(final_normal){
                            UserRef.userData.setState_frequency("19500");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        } else if(final_apart){
                            UserRef.userData.setState_frequency("16500");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        } else if(final_batt){
                            UserRef.userData.setState_frequency("13500");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }
                    }
                    isLogin = true;
                    Log.d(TAG, "mTask2 : " + final_apart + " : " + final_batt + " : " + final_normal);
                    if(final_apart || final_batt || final_normal) {
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", false).apply();
                        String reason = "none";
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("stop_reason", reason).apply();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent svc = new Intent(LoginService.this, BtService.class);
                            startForegroundService(svc);
                        } else {
                            Intent svc = new Intent(LoginService.this, BtService.class);
                            startService(svc);
                        }
                    }
                } else {
                     Log.d(TAG, "mTask2 : no frequency beaconConnected : " + beaconConnected);
                    if(allowUserDataUpdate) {
                        UserRef.userData.setState_frequency("-");
                        UserRef.userDataRef.setValue(UserRef.userData);
                    }
                    if(beaconConnected == false) {
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", true).apply();
                        String reason = "there is no frequency && beacon is not connected";
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("stop_reason", reason).apply();
                        if(allowUserDataUpdate) {
                            UserRef.userData.setState_ble("-");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }
                        if(allowUserDrivingUpdate) {
                            UserRef.userDriveData.setTime_logout(getCurTime());
                            UserRef.userDriveRef.setValue(UserRef.userDriveData);
                        }
                    }
                    isLogin = false;
                }
                mFreqInfos.clear();
            }
        };

        mTimer = new Timer();
        mTimer.schedule(mTask, 3000, 10000);

        mTimer2 = new Timer();
        mTimer2.schedule(mTask2, 30000, 30000);
    }

    private boolean checkMicPermission() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "checkMicPermission : not allow");
            Intent intent = new Intent(LoginService.this, MainActivity.class);
            startActivity(intent);

            return false;
        }

        Log.d(TAG, "checkMicPermission : allow");


        return true;

    }

    private boolean checkBtPermisson() {

        if (btAdapter != null && !btAdapter.isEnabled()) {

            Log.d(TAG, "checkBtPermisson : not allow");

            Intent intent = new Intent(LoginService.this, MainActivity.class);
            startActivity(intent);

            return false;
        }

        Log.d(TAG, "checkBtPermisson : allow");
        return true;
    }

    private boolean checkLocationPermission() {

        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "checkLocationPermission : not allow");
            Intent intent = new Intent(LoginService.this, MainActivity.class);
            startActivity(intent);

            return false;
        }

        Log.d(TAG, "checkLocationPermission : allow");
        return true;
    }

    private boolean checkPermisson() {

        allow_mic = checkMicPermission();
        allow_bt = checkBtPermisson();
        allow_location = checkLocationPermission();

        if(!allow_mic || !allow_bt || !allow_location){

            Log.d(TAG, "checkPermisson : not allow");

            return false;
        }

        Log.d(TAG, "checkPermisson : allow");

        return true;

    }

    @Override
    public void onBeaconServiceConnect() {

        Log.d(TAG, "onBeaconServiceConnect");

        beaconManager.removeAllRangeNotifiers();
        beaconManager.addRangeNotifier(new RangeNotifier() {


            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                //Log.d(TAG, "didRangeBeaconsInRegion : beacon size : " + beacons.size());
                if (beacons.size() > 0) {
                    Iterator it = beacons.iterator();
                    while(it.hasNext()) {
                        Beacon b = (Beacon)it.next();
                        //Log.d(TAG, "to string : " + b.toString());
                        String uuid = b.getId1().toString();
                        //Log.d(TAG,  "uuid : " + uuid);
                        if(target_uuid.equals(uuid)) {
                            //beaconConnected = true;
                            mBeaconConnectList.add(true);
                            //Log.d(TAG,  " beacon connected ");
                            mMinor = b.getId3().toString();
                            //Log.d(TAG,  "miner : " + mMinor);
                        } else {
                            //beaconConnected = false;
                            mBeaconConnectList.add(false);
                        }
                    }
                } else {
                    //beaconConnected = false;
                    mBeaconConnectList.add(false);
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region(target_uuid, null, null, null));
        } catch (RemoteException e) {
            Log.d(TAG, e.toString());
        }

  /*      beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {

                Log.d(TAG, "didEnterRegion");


            }

            @Override
            public void didExitRegion(Region region) {

                Log.d(TAG, "didExitRegion");

            }

            @Override
            public void didDetermineStateForRegion(int i, Region region) {
                Log.d(TAG, "didDetermineStateForRegion");

            }
        });

        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                for (Beacon beacon : beacons){
                    Log.d(TAG, "didRangeBeaconsInRegion : distance: " +
                            beacon.getDistance() + "id: " +
                            beacon.getId1() + "/" +
                            beacon.getId2() + "/" +
                            beacon.getId3());
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region(target_uuid, null, null, null));
        } catch (RemoteException e) {
            Log.d(TAG, e.toString());
        }

        try {
            beaconManager.startMonitoringBeaconsInRegion(new Region(target_uuid, null, null, null));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
*/
    }


    class ScreenOnReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "ScreenOnReceiver : onReceive");
            if (action.equals(Intent.ACTION_SCREEN_ON)) {


                //startRecord();


                Log.d(TAG, "ACTION_SCREEN_ON");
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", false).apply();



            }
            else if (action.equals(Intent.ACTION_SCREEN_OFF)) {

                //stopRecord();

                Log.d(TAG, "ACTION_SCREEN_OFF");
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", true).apply();
            }
        }
    }


    // The Record and analysis class
    private class RecordAudio extends AsyncTask<Void, Double, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            LinkedBlockingQueue<Integer> samplesReadQueue = new LinkedBlockingQueue<Integer>();
            record = new RecorderRunnable(samplesReadQueue, RATE, fftChunkSize);
            trd = new Thread(record);
            trd.start();
            short[] audioBuffer;
            while(started && !Thread.interrupted()) {
                try {
                    audioBuffer = record.getLatest(fftChunkSize);
                } catch (InterruptedException e) {
                    Log.e(TAG, "InterruptedException for getting audio data.");
                    e.printStackTrace();
                    break;
                }
                AnalyzeFrequencies(audioBuffer);
            }
            Log.d(TAG, "RecordAudio : doInBackground : RecorderRunnable stopping recording.");
            return null;
        }

        @Override
        protected void onProgressUpdate(Double... values) {
            super.onProgressUpdate(values);
            Log.d(TAG, "RecordAudio : onProgressUpdate");
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.d(TAG, "RecordAudio : onPreExecute");
        }

        public int AnalyzeFrequencies(short[] audio_data) {
//            Log.d(TAG,"AnalyzeFrequencies ");
            if (audio_data.length * 2 < 0) {
                Log.e(TAG, "awkward fail: " + (audio_data.length * 2));
            }
            double[] frequencyData = new double[audio_data.length * 2];
            for (int i = 0; i < audio_data.length; i++) {
                frequencyData[i * 2] = audio_data[i];
                frequencyData[i * 2 + 1] = 0;
            }
            //audio signal to 주파수 데이터
            DoFFT(frequencyData, audio_data.length);
            return AnalyzeFFT(audio_data.length, frequencyData);
        }

        public void DoFFT(double[] data, int length) {

//            Log.d(TAG,"DoFFT : length : " + length);

            long n = length * 2, m;
            int i, j = 1;
            double temp;

            long mmax = 2;
            long istep;

            for (i = 1; i < n; i += 2) {
                if (j > i) {
                    temp = data[j - 1];
                    data[j - 1] = data[i - 1];
                    data[i - 1] = temp;
                    temp = data[j];
                    data[j] = data[i];
                    data[i] = temp;
                }
                m = length;
                while (m >= 2 && j > m) {
                    j -= m;
                    m >>= 1;
                }
                j += m;
            }

            mmax = 2;
            double theta, wtemp, wpr, wpi, wr, wi;
            double tempr, tempi;
            while (n > mmax) {
                istep = mmax * 2;
                theta = -2 * (Math.PI / mmax);
                wtemp = Math.sin(0.5 * theta);
                wpr = -2.0 * wtemp * wtemp;
                wpi = Math.sin(theta);
                wr = 1.0;
                wi = 0.0;
                for (m = 1; m < mmax; m += 2) {
                    for (i = (int) m; i <= n; i += istep) {
                        j = (int) (i + mmax);
                        tempr = wr * data[j - 1] - wi * data[j];
                        tempi = wr * data[j] + wi * data[j - 1];

                        data[j - 1] = data[i - 1] - tempr;
                        data[j] = data[i] - tempi;
                        data[i - 1] += tempr;
                        data[i] += tempi;
                    }
                    wtemp = wr;
                    wr += wr * wpr - wi * wpi;
                    wi += wi * wpr + wtemp * wpi;
                }
                mmax = istep;
            }
        }

        public int AnalyzeFFT(int audioDataLength, double[] frequencyData) {
//            Log.d(TAG,"AnalyzeFFT : audioDataLength : " + audioDataLength);
            double best_frequency = 0;
            double bestAmplitude = 0;

            final int min_frequency_fft = (int) Math.round(1.0 * MIN_FREQUENCY
                    * audioDataLength / RATE);
            final int max_frequency_fft = (int) Math.round(1.0 * MAX_FREQUENCY
                    * audioDataLength / RATE);
//            Log.d(TAG,"AnalyzeFFT : min_frequency_fft : " + min_frequency_fft + " : max_frequency_fft : " + max_frequency_fft);

            //가장 높은 주파수 찾기
            for (int i = min_frequency_fft; i <= max_frequency_fft; i++) {

                final double currentFrequency = i * 1.0 * RATE / audioDataLength;
                final double current_amplitude = Math.pow(frequencyData[i * 2], 2)
                        + Math.pow(frequencyData[i * 2 + 1], 2);
                final double normalizedAmplitude = Math.pow(current_amplitude, 0.5)
                        / currentFrequency;
                // find peaks
                // NOTE: this finds all the relevant peaks because their
                // amplitude usually keeps rising with the frequency.
                if (normalizedAmplitude > bestAmplitude) {
                    // it's important to note the best_amplitude also for noise
                    // level measurement.
                    best_frequency = currentFrequency;
                    bestAmplitude = normalizedAmplitude;

                }
            }
            //Log.d(TAG,"AnalyzeFFT : best_frequency : " + best_frequency);
//            Log.d(TAG,"AnalyzeFFT : bestAmplitude : " + bestAmplitude);
            if( best_frequency > 13000 ) {
                mfrequencyList.add(best_frequency);
            }

            if(mfrequencyList.size() > 100) {
                int bestCount = 0;
                double bestFrequency = 0.0;
                for(int i=0; i< mfrequencyList.size(); i++) {
                    int count = 0;
                    double freq = 0.0;
                    int noiseCount = 0;
                    double val0 = mfrequencyList.get(i);
                    for(int j=i+1; j<mfrequencyList.size(); j++) {
                        double val1 = mfrequencyList.get(j);
                        if(val0 == val1) {
                            noiseCount = 0;
                            count++;
                        } else {
                            noiseCount++;
                        }
                        if(noiseCount == 2) break;
                    }
                    if(count > bestCount) {
                        bestCount = count;
                        bestFrequency = val0;
                    }
                }
                mfrequencyList.clear();
                Log.d(TAG,"AnalyzeFFT : best_frequency : " + bestFrequency + " : best_count : " + bestCount);
                if(bestCount > 3) {
                    mFreqInfos.add(new FreqInfo(bestFrequency, bestCount));
                }
            }
            return 1;
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            Log.d(TAG, "leScanCallback : Device Name : " + bluetoothDevice.getName());

            if(DEV_NAME.equals(bluetoothDevice.getName()) || DEV_NAME1.equals(bluetoothDevice.getName())) {

                discoveredDev = bluetoothDevice;

                //stop scanning
                stopScanning();

                connectToDevice();
            }

        }


    };



    // Device connect call back
    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation
            Log.d(TAG, "btleGattCallback : onCharacteristicChanged");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // this will get called when a device connects or disconnects
            Log.d(TAG, "btleGattCallback : onConnectionStateChange : state : " + status + " : newState : " + newState);
            switch (newState) {
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.d(TAG, "btleGattCallback : onConnectionStateChange : STATE_DISCONNECTED");
                    bleConnected = false;
                    disconnectDevice();
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d(TAG, "btleGattCallback : onConnectionStateChange : STATE_CONNECTED");

                    bleConnected = true;

                    // discover services and characteristics for this device
                    if(bluetoothGatt != null)
                        bluetoothGatt.discoverServices();
                    else
                        Log.d(TAG, "btleGattCallback : onConnectionStateChange : bluetoothGatt == null");

                    break;
                default:
                    Log.d(TAG, "btleGattCallback : onConnectionStateChange : unknown");
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            // this will get called after the client initiates a 			BluetoothGatt.discoverServices() call
            Log.d(TAG, "btleGattCallback : onServicesDiscovered : status : " + status);

            displayGattServices(bluetoothGatt.getServices());
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.d(TAG, "btleGattCallback : onCharacteristicRead : status : " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }
    };

    public void startScanning() {
        Log.d(TAG, "startScanning");


        if(btScanning == false){

            btScanning = true;

//            AsyncTask.execute(startScan);
            btAdapter.startLeScan(leScanCallback);

            mHandler.postDelayed(scanTimeout, SCAN_PERIOD);

        }


    }

    Runnable startScan = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "startScan : startLeScan");
            btAdapter.startLeScan(leScanCallback);
        }
    };

    Runnable scanTimeout = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "scanTimeout : timeout");
            stopScanning();
        }
    };

    public void stopScanning() {
        Log.d(TAG, "stopScanning");

        if(btScanning == true) {

            btScanning = false;
//            AsyncTask.execute(stopScan);
            btAdapter.stopLeScan(leScanCallback);
        }



    }

    Runnable stopScan = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "startScanning : stopLeScan");
            btAdapter.stopLeScan(leScanCallback);
        }
    };

    public void connectToDevice() {
        Log.d(TAG, "connectToDevice start");

        if(discoveredDev != null) {

            disconnectDevice();

            Log.d(TAG, "connectToDevice name : " + discoveredDev.getName());
            bluetoothGatt = discoveredDev.connectGatt(this, false, btleGattCallback);

        }

        Log.d(TAG, "connectToDevice end");
    }

    public void disconnectDevice() {

        Log.d(TAG, "disconnectDevice start");
        if(bluetoothGatt != null) {

            Log.d(TAG, "disconnectDevice start : " + bluetoothGatt.getDevice().getName());
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        Log.d(TAG, "disconnectDevice end");
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {

        Log.d(TAG, "broadcastUpdate : action : " + action);

        if(ACTION_DATA_AVAILABLE.equals(action)) {


            final byte[] data = characteristic.getValue();

            devData = new String(data);

            Log.d(TAG, "broadcastUpdate : devData : " + devData);

            if(devData.equals("AT+MINO0x0000")){
                UserRef.userData.setState_ble("0000");
                UserRef.userDataRef.setValue(UserRef.userData);
            }
            else if(devData.equals("AT+MINO0x0001")){
                UserRef.userData.setState_ble("0001");
                UserRef.userDataRef.setValue(UserRef.userData);
            }
            else if(devData.equals("AT+MINO0x0010")){
                UserRef.userData.setState_ble("0010");
                UserRef.userDataRef.setValue(UserRef.userData);
            }
            else if(devData.equals("AT+MINO0x0011")){
                UserRef.userData.setState_ble("0011");
                UserRef.userDataRef.setValue(UserRef.userData);
            }

        }

    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {

        Log.d(TAG, "displayGattServices ");


        if (gattServices == null){

            Log.d(TAG, "displayGattServices : gattServices == null");
            return;
        }


        BluetoothGattService targetService = null;

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {

            final String uuid = gattService.getUuid().toString();


            if(DEV_UUID.equals(uuid)) {
                targetService = gattService;

                Log.d(TAG, "displayGattServices : target dev uuid : " + uuid );

                break;

            }

        }

        List<BluetoothGattCharacteristic> gattCharacteristics = targetService.getCharacteristics();

        // Loops through available Characteristics.
        for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {

            bluetoothGatt.readCharacteristic(gattCharacteristic);
            bluetoothGatt.setCharacteristicNotification(gattCharacteristic, true);


            final byte[] data = gattCharacteristic.getValue();

            devData = new String(data);


            Log.d(TAG, "displayGattServices : devData : " + devData );


        }

    }

    private String getCurTime(){
        SimpleDateFormat mFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long mNow = System.currentTimeMillis();
        Date mDate = new Date(mNow);
        return mFormat.format(mDate);
    }



}