package com.busanbus.smartblock;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.busanbus.smartblock.Model.User;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;



public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    DatabaseReference database = FirebaseDatabase.getInstance().getReference();
    DatabaseReference database_user = database.child("User");

    ArrayList<User> users = new ArrayList<User>();

    EditText userID;
    EditText userPW;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        Button button_signin = findViewById(R.id.button_signin);
        Button button_signup = findViewById(R.id.button_signup);

        userID = findViewById(R.id.editText_id);
        userPW = findViewById(R.id.editText_pw);

        database_user.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                users.clear();

                Log.d(TAG, "onDataChange");

                for(DataSnapshot dsp : dataSnapshot.getChildren()){
                    users.add(dsp.getValue(User.class)); //add result into array list
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d(TAG, "onCancelled");
            }
        });

        button_signin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                siginin();
            }
        });

        button_signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SignUpActivity.class));
            }
        });

        Intent svc = new Intent(this, LoginService.class);
        startService(svc);


        database_user.orderByChild("phone").equalTo("+821088188595").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "orderByChild(phone) : onDataChange");

                ArrayList<User> adsList = new ArrayList<User>();
                for (DataSnapshot adSnapshot: dataSnapshot.getChildren()) {
                    adsList.add(adSnapshot.getValue(User.class));
                }

                Log.d(TAG, "orderByChild(phone) : onDataChange : size : " + adsList.size());

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d(TAG, "orderByChild(phone) : onCancelled");
            }
        });




    }

    public void siginin(){

        Log.d(TAG, "siginin");

        for(User user_tmp : users){
            if(userID.getText().toString().equals(user_tmp.getId())){
                if(userPW.getText().toString().equals(user_tmp.getPw())){
                    Toast.makeText(getApplicationContext(),"로그인에 성공하였습니다", Toast.LENGTH_SHORT).show();
                    return;
                }
                else{
                    Toast.makeText(getApplicationContext(),"잘못된 비밀번호입니다", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        Toast.makeText(getApplicationContext(),"존재하지 않는 아이디입니다", Toast.LENGTH_SHORT).show();

    }

}
