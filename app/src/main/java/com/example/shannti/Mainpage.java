package com.example.shannti;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class Mainpage extends AppCompatActivity {

    ImageButton Pose, Object, Calendar;
    TextView textView;

    EditText TTS_textView;
    Intent intent;
    SpeechRecognizer mRecognizer;
    final int PERMISSION = 1;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mainpage);
        // 안드로이드 6.0버전 이상인지 체크해서 퍼미션 체크


        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status!=android.speech.tts.TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });

        Pose = findViewById(R.id.posedetection_background);
        Object = findViewById(R.id.objectdetection_background);


        //TTS
        Pose.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override public void onClick(View v) {
                String text = TTS_textView.getText().toString();

                tts.setPitch(1.0f);
                tts.setSpeechRate(1.0f);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        });


        if ( Build.VERSION.SDK_INT >= 23 ){
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.INTERNET , android.Manifest.permission.RECORD_AUDIO}, PERMISSION);
        }

        Calendar = findViewById(R.id.calendar_background);

        // RecognizerIntent 생성
        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName()); // 여분의 키
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR"); // 언어 설정

        // 버튼 클릭 시 객체에 Context와 listener를 할당
        Calendar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecognizer = SpeechRecognizer.createSpeechRecognizer(Mainpage.this); // 새 SpeechRecognizer 를 만드는 팩토리 메서드
                mRecognizer.setRecognitionListener(listener); // 리스너 설정
                mRecognizer.startListening(intent); // 듣기 시작
            }
        });
    }

    private RecognitionListener listener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            // 말하기 시작할 준비가되면 호출
            Toast.makeText(getApplicationContext(),"음성인식 시작",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onBeginningOfSpeech() {
            // 말하기 시작했을 때 호출
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // 입력받는 소리의 크기를 알려줌
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            // 말을 시작하고 인식이 된 단어를 buffer에 담음
        }

        @Override
        public void onEndOfSpeech() {
            // 말하기를 중지하면 호출
        }

        @Override
        public void onError(int error) {
            // 네트워크 또는 인식 오류가 발생했을 때 호출
            String message;

            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    message = "오디오 에러";
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    message = "클라이언트 에러";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "퍼미션 없음";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    message = "네트워크 에러";
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    message = "네트웍 타임아웃";
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    message = "찾을 수 없음";
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "RECOGNIZER 가 바쁨";
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    message = "서버가 이상함";
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "말하는 시간초과";
                    break;
                default:
                    message = "알 수 없는 오류임";
                    break;
            }

            Toast.makeText(getApplicationContext(), "에러 발생 : " + message,Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onResults(Bundle results) {

            HashMap<String, Integer> Act = new HashMap<>();

            Act.put("샨티야",0);
            Act.put("산티야",0);
            Act.put("샨티",0);
            Act.put("산티",0);

            Act.put("자세 교정 운동",1);
            Act.put("자세 교정",1);
            Act.put("자세",1);

            Act.put("운동 출석부",2);
            Act.put("운동",2);
            Act.put("출석부",2);

            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            for(int i = 0; i < matches.size() ; i++){
                String OrderText = matches.get(i);
                textView.setText(matches.get(i));

                if(OrderText.equals("샨티야") || OrderText.equals("산티야"))
                {
                    String text = "네 불르셨어요? 누르실 버튼을 말씀해주세요";
                    tts.setPitch(1.0f);
                    tts.setSpeechRate(1.0f);
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);

                    new Handler().postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            mRecognizer.startListening(intent);
                        }
                    }, 3000);// 0.6초 정도 딜레이를 준 후 시작

                }

                if(OrderText.equals("자세 교정 운동 버튼 눌러 줘") || OrderText.equals("자세 교정 운동"))
                {
                    String text = "자세 교정 운동 페이지로 이동합니다";
                    tts.setPitch(1.0f);
                    tts.setSpeechRate(1.0f);
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);

                    new Handler().postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Intent intent = new Intent(getApplicationContext(), PoseDetection.class);
                            startActivity(intent);
                        }
                    }, 3000);// 0.6초 정도 딜레이를 준 후 시작
                }

                if(OrderText.equals("운동 출석부 버튼 눌러 줘") || OrderText.equals("운동 출석부"))
                {
                    String text = "운동출석부 페이지로 이동합니다";
                    tts.setPitch(1.0f);
                    tts.setSpeechRate(1.0f);
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);

                    new Handler().postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Intent intent = new Intent(getApplicationContext(), Calendar.class);
                            startActivity(intent);
                        }
                    }, 3000);// 0.6초 정도 딜레이를 준 후 시작
                }
//                else
//                {
//                    String text = "잘 못들었어요 다시한번 말해주세요";
//                    tts.setPitch(1.0f);
//                    tts.setSpeechRate(1.0f);
//                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
//
//                    new Handler().postDelayed(new Runnable()
//                    {
//                        @Override
//                        public void run()
//                        {
//                            mRecognizer.startListening(intent);
//                        }
//                    }, 3000);// 0.6초 정도 딜레이를 준 후 시작
//                }
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            // 부분 인식 결과를 사용할 수 있을 때 호출
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            // 향후 이벤트를 추가하기 위해 예약
        }


    };

    @Override public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }
}