package com.busanbus.smartblock.service;

import android.Manifest;
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
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.busanbus.smartblock.UserRef;
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

public class LoginService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = LoginService.class.getSimpleName();

    ScreenOnReceiver mScreenOnReceiver;


    private final static int fftChunkSize = 1024;
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

    boolean allow_bt = false;
    boolean allow_location = false;
    boolean allow_mic = false;

    boolean isLogin = false;


    private static int CHANNEL_MODE = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private static int ENCODING = AudioFormat.ENCODING_PCM_16BIT;


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

        getUserData();

        Log.d(TAG, "onCreate : " + " : current thread : " + Thread.currentThread());

        mAudioData = new short[BUFFER_SIZE];

        //bt ble
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();

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


//        startScanning();

//        startRecord();

//        Intent svc = new Intent(LoginService.this, BlockService.class);
//        startService(svc);


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

        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

        if(mScreenOnReceiver != null) {
            unregisterReceiver(mScreenOnReceiver);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {


        Log.d(TAG, "onSharedPreferenceChanged : " + key );


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

                bleCheckCount++;

                Log.d(TAG, "mTask : run : bleCheckCount : " + bleCheckCount);



                if(bleConnected) {
                    Log.d(TAG, "mTask : run : bleConnected : " + bleConnected);
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", false).apply();
                    Intent svc = new Intent(LoginService.this, BtService.class);
                    startService(svc);

                    if(!isLogin){
                        UserRef.userData.setTime_login(getCurTime());
                        UserRef.userDataRef.setValue(UserRef.userData);
                    }

                    isLogin = true;

                } else {
                    Log.d(TAG, "mTask : run : not bleConnected");

                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", true).apply();

                    isLogin = false;

                    UserRef.userData.setState_frequency("-");
                    UserRef.userData.setState_ble("-");
                    UserRef.userDataRef.setValue(UserRef.userData);

                    UserRef.userDriveData.setTime_logout(getCurTime());
                    UserRef.userDriveRef.setValue(UserRef.userDriveData);

                }

//                mfrequencyList.clear();


//                startRecord();

                if(discoveredDev == null || (bleCheckCount%3 == 0 && bleConnected == false) ){

                    startScanning();
                } else if(bluetoothGatt == null) {
                    connectToDevice();
                }

            }
        };

        mTask2 = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "mTask2 : run : current thread : " + Thread.currentThread());

                if(mfrequencyList.size() > 0) {
                    Log.d(TAG, "mTask2 : run : frequency size : " + mfrequencyList.size() );

                    boolean state_apart = false;
                    boolean state_batt = false;
                    boolean state_normal = false;

                    boolean final_apart = false;
                    boolean final_batt = false;
                    boolean final_normal = false;

//                    ArrayList<Double> arr_apart = new ArrayList<>();
//                    ArrayList<Double> arr_batt = new ArrayList<>();
//                    ArrayList<Double> arr_normal = new ArrayList<>();
//
//                    for(int i=0; i < mfrequencyList.size(); i++){
////                        Log.d(TAG, "" + mfrequencyList.get(i));
//
//                        if(mfrequencyList.get(i)>12500&&mfrequencyList.get(i)<14500){
//                            if(arr_apart.contains(mfrequencyList.get(i)))
//                                state_apart = true;
//                            else
//                                arr_apart.add(mfrequencyList.get(i));
//                        }
//                        if(mfrequencyList.get(i)>15300&&mfrequencyList.get(i)<18000){
//                            if(arr_batt.contains(mfrequencyList.get(i)))
//                                state_batt = true;
//                            else
//                                arr_batt.add(mfrequencyList.get(i));
//                        }
//                        if(mfrequencyList.get(i)>18300&&mfrequencyList.get(i)<21000){
//                            if(arr_normal.contains(mfrequencyList.get(i)))
//                                state_normal = true;
//                            else
//                                arr_normal.add(mfrequencyList.get(i));
//                        }
//
//
//                    }


                    for(int i=0; i < mfrequencyList.size(); i++){
                        double freq = mfrequencyList.get(i);

                        if(freq == 0.0)
                            continue;


                        Log.d(TAG, "mTask2 : i : " + i + ", freq : " + freq);

                        mfrequencyList.set(i, 0.0);

                        if(freq>12500.0 && freq<15300.0){

                            state_apart = true;
//                            Log.d(TAG, "state_apart");

                        } else if(freq>15300.0 && freq<18300.0){

                             state_batt = true;
//                             Log.d(TAG, "state_batt");

                        } else if(freq>18300.0 && freq<21000.0){

                             state_normal = true;
//                            Log.d(TAG, "state_normal");

                        }

                        for(int j = i+1;j < mfrequencyList.size();j++) {

                            double freq2 = mfrequencyList.get(j);
//                            Log.d(TAG, "j : " + j + ", freq2 : " + freq2);

                            if(freq2 == 0.0)
                                continue;

                            if(freq == freq2) {

                                mfrequencyList.set(j, 0.0);

                                if(state_apart) {

                                    final_apart = true;
                                    Log.d(TAG, "mTask2 : final_apart");

                                }

                                if(state_batt) {

                                    final_batt = true;
                                    Log.d(TAG, "mTask2 : final_batt");

                                }

                                if(state_normal) {

                                    final_normal = true;
                                    Log.d(TAG, "mTask2 : final_normal");

                                }

                            }

                        }

                    }



                    if(isLogin){
                        if(state_normal){
                            UserRef.userData.setState_frequency("19500");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }

                        if(state_apart){
                            UserRef.userData.setState_frequency("16500");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }

                        if(state_batt){
                            UserRef.userData.setState_frequency("13500");
                            UserRef.userDataRef.setValue(UserRef.userData);
                        }

                    }

                    Log.d(TAG, "mTask2 : " + final_apart + " : " + final_batt + " : " + final_normal );

                    if(final_apart || final_batt || final_normal) {
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", false).apply();
                        Intent svc = new Intent(LoginService.this, BtService.class);
                        startService(svc);
                    } else {

                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", true).apply();

                    }

                } else {
                    Log.d(TAG, "mTask2 : run : no frequency");

                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", true).apply();
                }

                mfrequencyList.clear();


                startRecord();

//                if(discoveredDev == null){
//
//                    startScanning();
//                } else if(bluetoothGatt == null) {
//                    connectToDevice();
//                }

            }
        };

        mTimer = new Timer();
        mTimer.schedule(mTask, 3000, 10000);

        mTimer2 = new Timer();
        mTimer2.schedule(mTask2, 3000, 10000);
    }

    private boolean checkMicPermission() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "checkMicPermission : not allow");

            return false;
        }

        Log.d(TAG, "checkMicPermission : allow");


        return true;

    }

    private boolean checkBtPermisson() {

        if (btAdapter != null && !btAdapter.isEnabled()) {

            Log.d(TAG, "checkBtPermisson : not allow");


            return false;
        }

        Log.d(TAG, "checkBtPermisson : allow");
        return true;
    }

    private boolean checkLocationPermission() {

        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "checkLocationPermission : not allow");


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


    class ScreenOnReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "ScreenOnReceiver : onReceive");
            if (action.equals(Intent.ACTION_SCREEN_ON)) {


                startRecord();


                Log.d(TAG, "ACTION_SCREEN_ON");
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", false).apply();



            }
            else if (action.equals(Intent.ACTION_SCREEN_OFF)) {

                stopRecord();

                Log.d(TAG, "ACTION_SCREEN_OFF");
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", true).apply();
            }
        }
    }


    // The Record and analysis class
    private class RecordAudio extends AsyncTask<Void, Double, Void> {
        @Override
        protected Void doInBackground(Void... params) {

            int bufferSize = BUFFER_SIZE;
            int minBufferSize = AudioRecord.getMinBufferSize(RATE,
                    CHANNEL_MODE, ENCODING);

            Log.d(TAG, "RecordAudio : doInBackground : minBufferSize : " + minBufferSize + " : current thread : " + Thread.currentThread());

            if (minBufferSize > bufferSize) {
                bufferSize = minBufferSize;
            }

            mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, CHANNEL_MODE,
                    ENCODING, bufferSize);

            if (mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {

                Log.d(TAG, "RecordAudio : doInBackground : not STATE_INITIALIZED");
                return null;
            }

            mRecorder.startRecording();
            Log.d(TAG, "RecordAudio : doInBackground : startRecording");

            while(started) {

                int bufferReadResult = mRecorder.read(mAudioData, 0, BUFFER_SIZE);

                AnalyzeFrequencies(mAudioData);

            }

            Log.d(TAG, "RecordAudio : doInBackground : RecorderRunnable stopping recording.");
            mRecorder.stop();
            mRecorder.release();

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

//            Log.d(TAG,"AnalyzeFFT : best_frequency : " + best_frequency);
//            Log.d(TAG,"AnalyzeFFT : bestAmplitude : " + bestAmplitude);

            if( best_frequency > 13500 ) {
                mfrequencyList.add(best_frequency);


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