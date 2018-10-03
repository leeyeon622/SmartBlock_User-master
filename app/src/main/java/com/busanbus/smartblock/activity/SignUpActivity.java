package com.busanbus.smartblock.activity;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.busanbus.smartblock.R;
import com.busanbus.smartblock.model.UserData;
import com.busanbus.smartblock.model.UserDriveData;
import com.busanbus.smartblock.service.LoginService;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SignUpActivity extends AppCompatActivity {

    DatabaseReference database = FirebaseDatabase.getInstance().getReference();
    DatabaseReference database_userdata = database.child("UserData");
    DatabaseReference database_drivedata = database.child("UserDriveData");
    ArrayList<UserData> userData = new ArrayList<>();

    SharedPreferences pref;
    SharedPreferences.Editor editor;

    private static final String TAG = SignUpActivity.class.getSimpleName();

    //phone number auth
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private String mVerificationId;
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final int MY_PERMISSIONS_REQUEST_READ_PHONE_STATE = 1;
    String mMyPhNumber = null;

    Boolean authComplete = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        pref = getSharedPreferences("pref", MODE_PRIVATE);

        getUserData();
        getMyPhoneNumber();

        Button button_complete = findViewById(R.id.button_signup_complete);
        button_complete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                EditText editText_company = findViewById(R.id.editText_company);
                String company = editText_company.getText().toString();

                if(!authComplete)
                    Toast.makeText(getApplicationContext(), "본인인증을 실행해주세요", Toast.LENGTH_SHORT).show();
                else if(company.equals(""))
                    Toast.makeText(getApplicationContext(), "회사명을 입력해주세요", Toast.LENGTH_SHORT).show();
                else{
                    Toast.makeText(getApplicationContext(), "회원가입이 완료되었습니다", Toast.LENGTH_SHORT).show();
                    signup(company);
                }

            }
        });

    }

    private void readyPhoneAuth(){

        Button btn_phoneNumber_auth = findViewById(R.id.button_phone_auth);
        btn_phoneNumber_auth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d(TAG, "btn_phoneNumber_auth : onClick");

                String phone = null;

                if(mMyPhNumber == null) {
                    getMyPhoneNumber();
                    return;
                } else {
                    if(!mMyPhNumber.substring(0,1).equals("+")){
                        phone = "+82" + mMyPhNumber;
                    } else {
                        phone = mMyPhNumber;
                    }

                }

                if(checkDulpicatedPhone(phone)) {
                    Toast.makeText(SignUpActivity.this, "이미 등록된 번호입니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Log.d(TAG, "request authentication phone : " + phone);

                PhoneAuthProvider.getInstance().verifyPhoneNumber(
                        phone,        // Phone number to verify
                        60,                 // Timeout duration
                        TimeUnit.SECONDS,   // Unit of timeout
                        SignUpActivity.this,               // Activity (for callback binding)
                        mCallbacks);        // OnVerificationStateChangedCallbacks

            }
        });

        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {

                Log.d(TAG, "onVerificationCompleted:" + credential);

                signInWithPhoneAuthCredential(credential);
            }


            @Override
            public void onVerificationFailed(FirebaseException e) {

                Log.w(TAG, "onVerificationFailed", e);

                if (e instanceof FirebaseAuthInvalidCredentialsException) {

                } else if (e instanceof FirebaseTooManyRequestsException) {

                }

            }

            @Override
            public void onCodeSent(String verificationId,
                                   PhoneAuthProvider.ForceResendingToken token) {

                Log.d(TAG, "onCodeSent:" + verificationId);

                // Save verification ID and resending token so we can use them later
                mVerificationId = verificationId;
                mResendToken = token;

                showInputDialog();

            }
        };

    }

    private boolean checkDulpicatedPhone(String phone) {

        boolean ret = false;

        for(UserData user : userData) {

            Log.d(TAG, "user phone : " + user.getPhone()  + " phone : " + phone + " myphone : " + mMyPhNumber);

            if(user.getPhone().contains(mMyPhNumber)) {
                ret = true;
                break;
            }

        }

        return ret;
    }

    private void getUserData(){

        database_userdata.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                userData.clear();

                for(DataSnapshot dsp : dataSnapshot.getChildren()){
                    userData.add(dsp.getValue(UserData.class)); //add result into array list
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private String getMyPhoneNumber() {

        Log.d(TAG, "getMyPhoneNumber");

        if(mMyPhNumber != null) {
            return mMyPhNumber;
        }

        TelephonyManager telMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        try {

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {


                Log.d(TAG, "no READ_PHONE_STATE permission");
                // TODO: Consider calling

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_PHONE_STATE},
                        MY_PERMISSIONS_REQUEST_READ_PHONE_STATE);

                return null;
            }


            mMyPhNumber = telMgr.getLine1Number();

            if(mMyPhNumber.substring(0,1).equals("+")) {
                mMyPhNumber = mMyPhNumber.replace("+82", "0");
            }

            Log.d(TAG, "getMyPhoneNumber :  mMyPhNumber : " + mMyPhNumber);

        }catch(Exception e){
            Log.e(TAG, e.toString());
        }



        EditText edit = findViewById(R.id.editText_phone);
        edit.setText(mMyPhNumber);

        readyPhoneAuth();

//        Log.e("phonenum", mMyPhNumber);
        return mMyPhNumber;
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");

                            FirebaseUser user = task.getResult().getUser();

                            authComplete = true;
                            Button button = findViewById(R.id.button_phone_auth);
                            button.setText("인증완료");
                            button.setEnabled(false);

//                            authComplete.setVisibility(View.VISIBLE);
//                            authNotComplete.setVisibility(View.INVISIBLE);
//                            UserAuthChecked = true;

                        } else {
                            // Sign in failed, display a message and update the UI
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                // The verification code entered was invalid
                            }
                        }
                    }
                });
    }

    protected void showInputDialog() {

        Log.d(TAG, "showInputDialog for phone number auth");

        // get prompts.xml view
        LayoutInflater layoutInflater = LayoutInflater.from(SignUpActivity.this);
        View promptView = layoutInflater.inflate(R.layout.dialog_phoneauth, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(SignUpActivity.this);
        alertDialogBuilder.setView(promptView);

        final EditText editText = (EditText) promptView.findViewById(R.id.edittext);
        // setup a dialog window
        alertDialogBuilder.setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                        String code = editText.getText().toString();

                        Log.d(TAG, "showInputDialog : ok : code :" + code);
                        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
                        FirebaseAuth.getInstance().signInWithCredential(credential);
                    }
                })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Log.d(TAG, "showInputDialog : Cancel");
                                dialog.cancel();
                            }
                        });

        // create an alert dialog
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

    private void signup(String company){

        UserData newUser = new UserData();
        newUser.setCompany(company);
        newUser.setPhone(mMyPhNumber);
        newUser.setState_ble("0001");
        newUser.setState_frequency("1000");
        newUser.setTime_login(getCurTime());

        database_userdata.push().setValue(newUser);

        UserDriveData newDriveData = new UserDriveData();
        newDriveData.setPhone(mMyPhNumber);
        newDriveData.setTime_start("-");
        newDriveData.setState_drive("2000");
        newDriveData.setTime_logout("-");

        database_drivedata.push().setValue(newDriveData);


        editor = pref.edit();
        editor.putString("phone", mMyPhNumber);
        editor.apply();

        Toast.makeText(getApplicationContext(), "로그인 성공", Toast.LENGTH_SHORT).show();
        Intent svc = new Intent(this, LoginService.class);
        startService(svc);
        finish();

    }

    private String getCurTime(){
        SimpleDateFormat mFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        long mNow = System.currentTimeMillis();
        Date mDate = new Date(mNow);
        return mFormat.format(mDate);
    }


}
