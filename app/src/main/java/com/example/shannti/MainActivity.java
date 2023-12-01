package com.example.shannti;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find the ImageView by its ID
        ImageView shanntiImage = findViewById(R.id.shanntiImage);

        // Set a click listener for the ImageView
        shanntiImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Call the method to handle the screen click
                onScreenClick(v);
            }
        });
    }

    // Method to handle screen click
    public void onScreenClick(View view) {
        // 페이지 전환을 위한 Intent 생성
        Intent intent = new Intent(this, Mainpage.class);

        // 새로운 액티비티 시작
        startActivity(intent);
    }
}
