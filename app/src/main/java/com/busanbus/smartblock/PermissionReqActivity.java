package com.busanbus.smartblock;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

public class PermissionReqActivity extends AppCompatActivity {

    private static final String TAG = PermissionReqActivity.class.getSimpleName();

    private LocationManager mLocationMgr;

    private final int PERMISSIONS_LOCATION = 1;
    private final int PERMISSIONS_OVERLAY = 2;
    private final int PERMISSIONS_GPS = 3;
    private final int PERMISSIONS_MIC = 4;
    private final static int REQUEST_ENABLE_BT = 5;

    boolean allow_overlay = false;
    boolean allow_location = false;
    boolean allow_gps = false;
    boolean allow_mic = false;
    boolean allow_bt = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        mLocationMgr = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        boolean allow = checkMic();

        if(allow) {
            allow = checkBtPermisson();

            if(allow) {

                allow = checkDrawOverlayPermission();

                if( allow ) {
                    allow = checkLocationPermission();

                    if(allow) {
                        checkGps();
                    }
                }
            }

        }

        finishActivity();

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        Log.d(TAG, "onNewIntent");

        boolean allow = checkDrawOverlayPermission();

        if( allow == true ) {
            allow = checkLocationPermission();

            if(allow) {
                checkGps();
            }
        }

        finishActivity();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.d(TAG, "onRequestPermissionsResult : " + requestCode);

        switch (requestCode) {
            case PERMISSIONS_LOCATION: {
                Log.d(TAG, "PERMISSIONS_LOCATION");
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

//                    Intent svc = new Intent(this, BtService.class);
//                    svc.putExtra("permission_location", true);
//                    startService(svc);

                    allow_location = true;

                    checkGps();

                    finishActivity();

                } else {

                    Toast.makeText(this, "앱 실행을 위해서는 권한을 설정해야 합니다", Toast.LENGTH_LONG).show();
                }
                return;
            }

            case PERMISSIONS_MIC: {
                Log.d(TAG, "PERMISSIONS_MIC");

                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

//                    Intent svc = new Intent(this, BtService.class);
//                    svc.putExtra("permission_mic", true);
//                    startService(svc);

                    allow_mic = true;

                    boolean allow = checkDrawOverlayPermission();

                    if(allow) {
                        allow = checkLocationPermission();
                        if(allow) {
                            checkGps();
                        }
                    }

                    finishActivity();

                } else {

                    Toast.makeText(this, "앱 실행을 위해서는 권한을 설정해야 합니다", Toast.LENGTH_LONG).show();
                }

                return;
            }

            default:
                break;


        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult : " + requestCode + ", " + resultCode);

        if (requestCode == PERMISSIONS_OVERLAY ) {
            Log.d(TAG, "PERMISSIONS_OVERLAY");

            boolean ok = checkDrawOverlayPermission();

            if(ok) {
//                Intent svc = new Intent(this, BtService.class);
//                svc.putExtra("permission_overlay", true);
//                startService(svc);

                allow_overlay = true;

                boolean allow = checkLocationPermission();
                if(allow){
                    checkGps();
                }

                finishActivity();
            }

        } else if( requestCode == PERMISSIONS_GPS ) {
            Log.d(TAG, "PERMISSIONS_GPS");

            boolean ok = checkGps();

            if(ok) {

//                Intent svc = new Intent(this, BtService.class);
//                svc.putExtra("permission_gps", true);
//                startService(svc);

                allow_gps = true;

                finishActivity();

            }
        } else if(requestCode == REQUEST_ENABLE_BT) {
            Log.d(TAG, "REQUEST_ENABLE_BT");


            boolean ok = checkBtPermisson();

            if(ok) {

                allow_bt = true;

                boolean allow = checkLocationPermission();


                finishActivity();

            }

        }


    }

    private void finishActivity() {
        Log.d(TAG, "finishActivity : " + allow_overlay + " : " + allow_location + " : " + allow_gps + " : " + allow_mic + " : " + allow_bt);

        if( allow_overlay == false )
            return;

        if( allow_location == false )
            return;

        if( allow_gps == false )
            return;

        if( allow_mic == false )
            return;

        if(allow_bt == false)
            return;

        finish();
    }


    private boolean checkBtPermisson() {

        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();

        if (btAdapter != null && !btAdapter.isEnabled()) {

            Log.d(TAG, "checkBtPermisson : not allow");

            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);

            return false;
        }

        Log.d(TAG, "checkBtPermisson : allow");
        return true;
    }


    private boolean checkDrawOverlayPermission() {


        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "checkDrawOverlayPermission : no permission");


            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivityForResult(intent, PERMISSIONS_OVERLAY);

            return false;
        }

        allow_overlay = true;

        return true;
    }

    private boolean checkLocationPermission() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "checkLocationPermission : no permission");
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                Log.d(TAG, "checkLocationPermission : ????");

                Toast.makeText(this, "앱 실행을 위해서는 권한을 설정해야 합니다",
                        Toast.LENGTH_LONG).show();

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION
                                , Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSIONS_LOCATION);

            } else {

                Log.d(TAG, "checkLocationPermission : request permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION
                                , Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSIONS_LOCATION);

            }

            return false;
        }

        allow_location = true;

        return true;

    }

    private boolean checkGps() {

        if (mLocationMgr.isProviderEnabled(LocationManager.GPS_PROVIDER) == false) {

            Log.d(TAG, "checkGps : not allow");

            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivityForResult(intent, PERMISSIONS_GPS);


            return false;
        }

        Log.d(TAG, "checkGps : allow");

        allow_gps = true;

        return true;
    }

    private boolean checkMic() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            Log.d(TAG, "checkMic : not allow");


            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_MIC);


            return false;
        }

        Log.d(TAG, "checkMic : allow");


        return true;
    }
}
