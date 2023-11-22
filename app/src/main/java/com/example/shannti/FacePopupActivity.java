package com.example.shannti;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
public class FacePopupActivity extends AppCompatActivity {

    Button okbtn;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.face_popup);

        okbtn = findViewById(R.id.okbtn);

        okbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // NO 버튼을 눌렀을 때 수행할 동작을 여기에 추가
                // 예를 들어, 팝업을 닫기만 하고 추가 동작이 필요하지 않을 수 있습니다.
                finish();
            }
        });
    }
}