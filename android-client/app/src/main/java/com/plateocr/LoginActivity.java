package com.plateocr;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    TextInputEditText idText;
    TextInputEditText pwText;
    Button loginBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        idText = findViewById(R.id.login_id);
        pwText = findViewById(R.id.login_pw);
        loginBtn = findViewById(R.id.login_btn);

        loginBtn.setOnClickListener(v -> {
            String id = idText.getText().toString();
            String pw = pwText.getText().toString();
            if(id.isEmpty() || pw.isEmpty()) {
                Toast.makeText(this, "아이디와 패스워드를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            loginBtn.setEnabled(false);
            HttpHelper.login(id, pw, new HttpHelper.HttpListener() {
                @Override
                public void onSuccess(String data) {
                    String ss[] = data.split(",");
                    String guardId = ss[0];
                    String guardName = ss[1];
                    String apartmentId = ss[2];
                    String apartmentName = ss[3];
                    Intent intent = new Intent(LoginActivity.this, CameraActivity.class);
                    intent.putExtra("guardId", guardId);
                    intent.putExtra("guardName", guardName);
                    intent.putExtra("apartmentId", apartmentId);
                    intent.putExtra("apartmentName", apartmentName);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onFailure(String e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "로그인 실패.", Toast.LENGTH_SHORT).show();

                        }
                    });
                }


                @Override
                public void onComplete() {
                    runOnUiThread(() -> {
                        loginBtn.setEnabled(true);
                    });
                }
            }).start();
        });
    }
}