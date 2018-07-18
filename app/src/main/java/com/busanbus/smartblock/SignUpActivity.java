package com.busanbus.smartblock;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.busanbus.smartblock.Model.Sequences;
import com.busanbus.smartblock.Model.User;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.Profile;
import com.facebook.ProfileTracker;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
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
import com.kakao.auth.ISessionCallback;
import com.kakao.auth.Session;
import com.kakao.network.ErrorResult;
import com.kakao.usermgmt.UserManagement;
import com.kakao.usermgmt.callback.MeResponseCallback;
import com.kakao.usermgmt.response.model.UserProfile;
import com.kakao.util.exception.KakaoException;
import com.kakao.util.helper.log.Logger;

import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static android.content.ContentValues.TAG;

public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = SignUpActivity.class.getSimpleName();

    DatabaseReference database = FirebaseDatabase.getInstance().getReference();
    DatabaseReference database_sequences = database.child("Sequences");
    DatabaseReference database_user = database.child("User");

    ArrayList<User> users = new ArrayList<User>();
    Sequences seq = new Sequences();

    Boolean IDChecked = false;
    Boolean UserAuthChecked = false;

    EditText editText_ID;
    EditText editText_PW;

    long kakaouuid = 0;
    String facebookid = null;

    SessionCallback callback;

    LinearLayout authComplete;
    LinearLayout authNotComplete;

    AccessToken accessToken;

    private CallbackManager callbackManager;
    ProfileTracker profileTracker;


    //phone number auth
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private String mVerificationId;
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final int MY_PERMISSIONS_REQUEST_READ_PHONE_STATE = 1;
    String mMyPhNumber = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        Log.d(TAG, "onCreate : " + getKeyHash(this));

        getMyPhoneNumber();

        authComplete = findViewById(R.id.AuthComplete);
        authNotComplete = findViewById(R.id.AuthNotComplete);

        authComplete.setVisibility(View.INVISIBLE);

        callback = new SessionCallback();
        Session.getCurrentSession().addCallback(callback);

        callbackManager = CallbackManager.Factory.create();

        // facebook
        LoginButton loginButton = findViewById(R.id.login_button);
        loginButton.setReadPermissions(Arrays.asList("public_profile", "email"));
        loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {

                Log.d(TAG, "loginButton : onSuccess");

                GraphRequest graphRequest = GraphRequest.newMeRequest(loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        Log.d(TAG, "loginButton : onCompleted");

                        Log.d(TAG, object.toString());
                    }
                });

                Bundle parameters = new Bundle();
                parameters.putString("fields", "id,name,email,gender,birthday");
                graphRequest.setParameters(parameters);
                graphRequest.executeAsync();
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "loginButton : onCancel");
            }

            @Override
            public void onError(FacebookException error) {
                Log.d(TAG, "loginButton : onError");
                Log.d(TAG, error.toString());
            }
        });

        // facebook
        LoginManager.getInstance().registerCallback(callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        // App code
                        accessToken = loginResult.getAccessToken();
                        Log.d(TAG, "LoginManager : onSuccess");
                    }

                    @Override
                    public void onCancel() {
                        // App code
                        Log.d(TAG, "LoginManager : onCancel");
                    }

                    @Override
                    public void onError(FacebookException exception) {
                        // App code
                        Log.d(TAG, "LoginManager : onError");
                        Log.d(TAG, exception.toString());
                    }
                });

        profileTracker = new ProfileTracker() {
            @Override
            protected void onCurrentProfileChanged(Profile oldProfile, Profile currentProfile) {
                facebookid = currentProfile.getId();

                Log.d(TAG, "ProfileTracker : onCurrentProfileChanged : facebookid : " + facebookid);

                authComplete.setVisibility(View.VISIBLE);
                authNotComplete.setVisibility(View.INVISIBLE);
                UserAuthChecked = true;
            }
        };
        profileTracker.startTracking();

        editText_ID = findViewById(R.id.editText_id);
        editText_PW = findViewById(R.id.editText_pw);

        database_user.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                Log.d(TAG, "database_user : onDataChange");

                users.clear();

                for(DataSnapshot dsp : dataSnapshot.getChildren()){
                    users.add(dsp.getValue(User.class)); //add result into array list

                    Log.d(TAG, dsp.toString());
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d(TAG, "database_user : onCancelled");
            }
        });

        database_sequences.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                Log.d(TAG, "database_sequences : onDataChange");

                seq = dataSnapshot.getValue(Sequences.class);

                Log.d(TAG, seq.toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d(TAG, "database_sequences : onCancelled");
            }
        });

        Button btn_IDCheck = findViewById(R.id.button_idcheck);
        btn_IDCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IDChecked = userIDCheck(editText_ID.getText().toString());
            }
        });

        TextView textView_submit = findViewById(R.id.text_submit);
        textView_submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(checkBeforeSubmit()){
                    String userID = editText_ID.getText().toString();
                    String userPW = editText_PW.getText().toString();
                    pushUserToDatabase(userID, userPW, mMyPhNumber, Long.toString(kakaouuid), facebookid);
                }
            }
        });

        // phone number authentication
        Button btn_phoneNumber_auth = findViewById(R.id.button_phone_auth);
        btn_phoneNumber_auth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d(TAG, "btn_phoneNumber_auth : onClick");

                if(mMyPhNumber == null) {
                    getMyPhoneNumber();
                    return;
                }

                PhoneAuthProvider.getInstance().verifyPhoneNumber(
                        mMyPhNumber,        // Phone number to verify
                        60,                 // Timeout duration
                        TimeUnit.SECONDS,   // Unit of timeout
                        SignUpActivity.this,               // Activity (for callback binding)
                        mCallbacks);        // OnVerificationStateChangedCallbacks

            }
        });

        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {
                // This callback will be invoked in two situations:
                // 1 - Instant verification. In some cases the phone number can be instantly
                //     verified without needing to send or enter a verification code.
                // 2 - Auto-retrieval. On some devices Google Play services can automatically
                //     detect the incoming verification SMS and perform verification without
                //     user action.
                Log.d(TAG, "onVerificationCompleted:" + credential);
                // [START_EXCLUDE silent]
//                mVerificationInProgress = false;
                // [END_EXCLUDE]

                // [START_EXCLUDE silent]
                // Update the UI and attempt sign in with the phone credential
//                updateUI(STATE_VERIFY_SUCCESS, credential);
                // [END_EXCLUDE]
                signInWithPhoneAuthCredential(credential);
            }


            @Override
            public void onVerificationFailed(FirebaseException e) {
                // This callback is invoked in an invalid request for verification is made,
                // for instance if the the phone number format is not valid.
                Log.w(TAG, "onVerificationFailed", e);
                // [START_EXCLUDE silent]
//                mVerificationInProgress = false;
                // [END_EXCLUDE]

                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    // Invalid request
                    // [START_EXCLUDE]
//                    mPhoneNumberField.setError("Invalid phone number.");
                    // [END_EXCLUDE]
                } else if (e instanceof FirebaseTooManyRequestsException) {
                    // The SMS quota for the project has been exceeded
                    // [START_EXCLUDE]
//                    Snackbar.make(findViewById(android.R.id.content), "Quota exceeded.",
//                            Snackbar.LENGTH_SHORT).show();
                    // [END_EXCLUDE]
                }

                // Show a message and update the UI
                // [START_EXCLUDE]
//                updateUI(STATE_VERIFY_FAILED);
                // [END_EXCLUDE]
            }

            @Override
            public void onCodeSent(String verificationId,
                                   PhoneAuthProvider.ForceResendingToken token) {
                // The SMS verification code has been sent to the provided phone number, we
                // now need to ask the user to enter the code and then construct a credential
                // by combining the code with a verification ID.
                Log.d(TAG, "onCodeSent:" + verificationId);

                // Save verification ID and resending token so we can use them later
                mVerificationId = verificationId;
                mResendToken = token;

                showInputDialog();

                // [START_EXCLUDE]
                // Update UI
//                updateUI(STATE_CODE_SENT);
//            }
////                updateUI(STATE_CODE_SENT);
//        };
                // [END_EXCLUDE]
            }
        };

    }

    private class SessionCallback implements ISessionCallback {

        @Override
        public void onSessionOpened() {
            Log.d(TAG, "onSessionOpened");
            requestMe();
        }

        @Override
        public void onSessionOpenFailed(KakaoException exception) {
            Log.d(TAG, "onSessionOpenFailed");
            if(exception != null) {
                Logger.e(exception);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume ");

        //requestMe();

    }

    //카카오톡 프로필 불러오기
    public void requestMe() {
        //유저의 정보를 받아오는 함수

        Log.d(TAG, "requestMe ");

        UserManagement.requestMe(new MeResponseCallback() {
            @Override
            public void onFailure(ErrorResult errorResult) {
                Log.e(TAG, "error message=" + errorResult);
//                super.onFailure(errorResult);
            }

            @Override
            public void onSessionClosed(ErrorResult errorResult) {

                Log.d(TAG, "onSessionClosed1 =" + errorResult);
            }

            @Override
            public void onNotSignedUp() {
                //카카오톡 회원이 아닐시
                Log.d(TAG, "onNotSignedUp ");

            }

            @Override
            public void onSuccess(UserProfile result) {

                kakaouuid = result.getId();
                Log.d(TAG, "onSuccess : ");
                Log.e(TAG, result.toString());
                Log.e(TAG, result.getId() + "");

                authComplete.setVisibility(View.VISIBLE);
                authNotComplete.setVisibility(View.INVISIBLE);
                UserAuthChecked = true;
            }
        });
    }

    public static String getKeyHash(final Context context)  {
        PackageManager localPackageManager = context.getPackageManager();
        PackageInfo packageInfo = null;

        try {
            packageInfo = localPackageManager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }



        if (packageInfo == null)
            return null;

        for (Signature signature : packageInfo.signatures) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                return Base64.encodeToString(md.digest(), Base64.NO_WRAP);
            } catch (NoSuchAlgorithmException e) {
                Log.w(TAG, "Unable to get MessageDigest. signature=" + signature, e);
            }
        }
        return null;
    }

    public boolean checkBeforeSubmit(){
        if(!IDChecked){
            Toast.makeText(getApplicationContext(),"아이디 중복확인을 해주세요", Toast.LENGTH_SHORT).show();
            return false;
        }
        if(editText_PW.getText().toString().equals("")){
            Toast.makeText(getApplicationContext(),"비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show();
            return false;
        }
        if(!UserAuthChecked){
            Toast.makeText(getApplicationContext(),"본인인증을 완료해주세요", Toast.LENGTH_SHORT).show();
            return false;
        }

        if(mMyPhNumber == null){
            getMyPhoneNumber();
            return false;
        }
        return true;
    }

    public boolean userIDCheck(String userID_input){
        for(User user : users){
            if(user.getId().equals(userID_input)){
                Toast.makeText(getApplicationContext(),"이미 사용중인 아이디입니다", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        editText_ID.setEnabled(false);
        Toast.makeText(getApplicationContext(),"사용 가능한 아이디입니다", Toast.LENGTH_SHORT).show();
        return true;
    }

    public void pushUserToDatabase(String id, String pw, String phone, String kakaoID, String facebookID){

        Log.d(TAG, "onDataChange");

        int userNo;
        int state = 0;

        userNo = seq.getUserSeq() + 1;
        seq.setUserSeq(userNo);

        database_sequences.setValue(seq);

        User user_tmp = new User();
        user_tmp.setUserNo(userNo);
        user_tmp.setId(id);
        user_tmp.setPw(pw);
        user_tmp.setPhone(phone);
        user_tmp.setKakaoID(kakaoID);
        user_tmp.setFacebookID(facebookID);
        user_tmp.setState(state);

        DatabaseReference newUserRef = database_user.push();

        newUserRef.setValue(user_tmp);

        Toast.makeText(getApplicationContext(),"회원가입이 완료되었습니다", Toast.LENGTH_SHORT).show();

        finish();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult");

        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy");

        if(profileTracker != null ) {
            profileTracker.stopTracking();
        }


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
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_PHONE_STATE},
                        MY_PERMISSIONS_REQUEST_READ_PHONE_STATE);

                return null;
            }


            mMyPhNumber = telMgr.getLine1Number();

            Log.d(TAG, "getMyPhoneNumber :  mMyPhNumber : " + mMyPhNumber);

        }catch(Exception e){
            Log.e(TAG, e.toString());
        }
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

                            UserAuthChecked = true;
                            // ...
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
        View promptView = layoutInflater.inflate(R.layout.input_dialog, null);
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
                        signInWithPhoneAuthCredential(credential);
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
}
