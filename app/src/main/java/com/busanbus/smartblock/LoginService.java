package com.busanbus.smartblock;

import android.Manifest;
import android.app.AlertDialog;
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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.api.GoogleApiClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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
    ArrayList<Double> mfrequencyList = new ArrayList<>();

    boolean allow_bt = false;
    boolean allow_location = false;
    boolean allow_mic = false;


    private static int CHANNEL_MODE = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private static int ENCODING = AudioFormat.ENCODING_PCM_16BIT;


    public final static int MIN_FREQUENCY = 8500; // 49.0 HZ of G1 - lowest
    // note
    // for crazy Russian choir.
    public final static int MAX_FREQUENCY = 22050; // 1567.98 HZ of G6 - highest
    // demanded note in the
    // classical repertoire

    //bt ble
    private final String DEV_NAME = "FUFI";
    private final String DEV_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    Boolean btScanning = false;
    BluetoothDevice discoveredDev = null;
    BluetoothGatt bluetoothGatt = null;
    String devData = null;

    private Handler mHandler = new Handler();
    private static final long SCAN_PERIOD = 3000;


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


        startScanning();


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
                Log.d(TAG, "checkFrequency : run : current thread : " + Thread.currentThread());

                if(mfrequencyList.size() > 0) {
                    Log.d(TAG, "checkFrequency : run : frequency size : " + mfrequencyList.size());
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", false).apply();
                    Intent svc = new Intent(LoginService.this, BtService.class);
                    startService(svc);

                } else {
                    Log.d(TAG, "checkFrequency : run : ");

                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("all_stop", true).apply();
                }

                mfrequencyList.clear();


//                startRecord();

                if(discoveredDev == null){

                    startScanning();
                } else if(bluetoothGatt == null) {
                    connectToDevice();
                }

            }
        };

        mTimer = new Timer();
        mTimer.schedule(mTask, 10000, 10000);
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

        if(allow_mic == false || allow_bt == false || allow_location == false){

            Intent intent = new Intent(this, PermissionReqActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

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

            Log.d(TAG,"AnalyzeFrequencies ");

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

            Log.d(TAG,"DoFFT : length : " + length);

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

            Log.d(TAG,"AnalyzeFFT : audioDataLength : " + audioDataLength);


            double best_frequency = 0;
            double bestAmplitude = 0;

            final int min_frequency_fft = (int) Math.round(1.0 * MIN_FREQUENCY
                    * audioDataLength / RATE);
            final int max_frequency_fft = (int) Math.round(1.0 * MAX_FREQUENCY
                    * audioDataLength / RATE);

            Log.d(TAG,"AnalyzeFFT : min_frequency_fft : " + min_frequency_fft + " : max_frequency_fft : " + max_frequency_fft);

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

            Log.d(TAG,"AnalyzeFFT : best_frequency : " + best_frequency);
            Log.d(TAG,"AnalyzeFFT : bestAmplitude : " + bestAmplitude);

            if( best_frequency > 19000 ) {
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

            if(DEV_NAME.equals(bluetoothDevice.getName())) {

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
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d(TAG, "btleGattCallback : onConnectionStateChange : STATE_CONNECTED");

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

            AsyncTask.execute(startScan);

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
            AsyncTask.execute(stopScan);
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
            bluetoothGatt = discoveredDev.connectGatt(this, false, btleGattCallback);
        }

        Log.d(TAG, "connectToDevice end");
    }

    public void disconnectDevice() {

        Log.d(TAG, "disconnectDevice start");
        if(bluetoothGatt != null) {
            bluetoothGatt.disconnect();
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

}
