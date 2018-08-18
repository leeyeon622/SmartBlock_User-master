package com.busanbus.smartblock.activity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.busanbus.smartblock.R;
import com.busanbus.smartblock.model.UserData;
import com.busanbus.smartblock.service.LoginService;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    DatabaseReference database = FirebaseDatabase.getInstance().getReference();
    DatabaseReference database_userdata = database.child("UserData");
    ArrayList<UserData> userData = new ArrayList<>();

    private LocationManager mLocationMgr;

    private final int PERMISSIONS_OVERLAY = 1;
    private final int PERMISSIONS_GPS = 2;
    private final static int REQUEST_ENABLE_BT = 3;

    SharedPreferences pref;
    SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate");

        mLocationMgr = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        pref = getSharedPreferences("pref", MODE_PRIVATE);

//        checkPermission();
        getUserData();
        listenerSet();

        Log.d(TAG, "onCreate end");

    }

    public void listenerSet(){

        Button btn_signup = findViewById(R.id.button_signup);
        btn_signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SignUpActivity.class);
                startActivity(intent);
                finish();
            }
        });

        Button btn_signin = findViewById(R.id.button_signin);
        btn_signin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText_phone = findViewById(R.id.editText_phone);
                String phone = editText_phone.getText().toString();

                signin(phone);
            }
        });

    }

    private void getUserData(){

        database_userdata.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                userData.clear();

                for(DataSnapshot dsp : dataSnapshot.getChildren()){
                    userData.add(dsp.getValue(UserData.class)); //add result into array list

                    for(int i = 0; i < userData.size(); i++)
                        Log.d(TAG, "phone : " + userData.get(i).getPhone());
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void signin(String phone){

        for(UserData tmp : userData){
            if(tmp.getPhone().equals(phone)) {
                Toast.makeText(getApplicationContext(), "로그인 성공", Toast.LENGTH_SHORT).show();

                editor = pref.edit();
                editor.putString("phone", phone);
                editor.apply();

                Intent svc = new Intent(this, LoginService.class);
                startService(svc);
                finish();

                return;
            }
        }

        Toast.makeText(getApplicationContext(), "등록되지 않은 아이디입니다", Toast.LENGTH_SHORT).show();
        return;

    }

    public void checkPermission(){

        Log.d(TAG, "checkPermission : " + Build.VERSION.SDK_INT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            String[] permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_PHONE_NUMBERS,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };

            List<String> per_denied = new ArrayList<String>();

            for(int i=0; i<permissions.length; i++){
                if(checkSelfPermission(permissions[i])== PackageManager.PERMISSION_DENIED) {
                    per_denied.add(permissions[i]);
                }
            }

            if(per_denied.isEmpty()){

                checkPermission2();
                return;
            }



            String[] per_need = new String[per_denied.size()];
            per_need = per_denied.toArray(per_need);

            requestPermissions(per_need,1000);

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1000:
                for(int i=0 ; i<permissions.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                        finish();
                }
                checkPermission2();
        }
    }

    public void checkPermission2(){

        Log.d(TAG, "checkPermission2");

        boolean allow = checkDrawOverlayPermission();

        if(allow) {
            allow = checkGps();

            if(allow) {
                checkBtPermisson();
            }
        }

        Log.d(TAG, "checkPermission2 : " + pref.getString("phone", "null"));

        if(!pref.getString("phone", "null").equals("null")){
            Toast.makeText(getApplicationContext(), "로그인 성공", Toast.LENGTH_SHORT).show();
            Intent svc = new Intent(this, LoginService.class);
            startService(svc);
            finish();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult : " + requestCode + ", " + resultCode);

        if (requestCode == PERMISSIONS_OVERLAY ) {
            Log.d(TAG, "PERMISSIONS_OVERLAY");

//            checkGps();

            if(checkDrawOverlayPermission()){
                checkGps();
            }
            else{
                finish();
            }

        } else if( requestCode == PERMISSIONS_GPS ) {
            Log.d(TAG, "PERMISSIONS_GPS");

//            checkBtPermisson();

            if(checkGps()){
                checkBtPermisson();
            }
            else{
                finish();
            }


        } else if(requestCode == REQUEST_ENABLE_BT) {
            Log.d(TAG, "REQUEST_ENABLE_BT");

            if(!checkBtPermisson()){
                finish();
            }

        }

    }

    private boolean checkDrawOverlayPermission() {


        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "checkDrawOverlayPermission : no permission");

            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivityForResult(intent, PERMISSIONS_OVERLAY);

            return false;
        }

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

        return true;
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

}
