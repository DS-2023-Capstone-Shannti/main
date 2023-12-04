package com.example.shannti;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        Button goMainButton = findViewById(R.id.btn_gomain);
        Button nextButton = findViewById(R.id.btn_next);
        Button continueButton = findViewById(R.id.btn_continue);

        goMainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 메인으로 돌아가는 화면으로 이동
                Intent intent = new Intent(getApplicationContext(), Mainpage.class);
                startActivity(intent);
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 다음 운동 진행 화면으로 이동
                Intent intent = new Intent(getApplicationContext(), PoseDetection.class);
                startActivity(intent);
            }
        });

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 계속 진행하기 화면으로 이동
                Intent intent = new Intent(MenuActivity.this, PoseDetection.class);
                startActivity(intent);
            }
        });
    }
}
