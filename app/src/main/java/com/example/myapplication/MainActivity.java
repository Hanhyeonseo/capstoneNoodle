// MainActivity.java
package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends BaseActivity implements TextToSpeech.OnInitListener, CameraFragment.OnImageCapturedListener {

    private static final int REQUEST_CAMERA_PERMISSION = 200;  // 카메라 권한 요청 코드
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;  // 오디오 권한 요청 코드
    private static final String PREFS_NAME = "TTS_PREFS";  // SharedPreferences 파일 이름
    private static final String SPEED_KEY = "SPEED_KEY";  // TTS 속도 저장 키

    private TextToSpeech tts;  // 텍스트를 음성으로 변환하는 TTS 객체
    private float speed = 1.4f;  // TTS 초기 속도
    private Vibrator vibrator;  // 진동 기능 객체
    private SharedPreferences sharedPreferences;  // TTS 속도 저장을 위한 SharedPreferences 객체
    private ActivityResultLauncher<Intent> activityResultLauncher;  // 액티비티 결과 런처
    private SpeechRecognizer speechRecognizer;  // 음성 인식 객체
    private Intent speechRecognizerIntent;  // 음성 인식 인텐트

    public MainActivity() {
        super(TransitionMode.HORIZON);  // 화면 전환 애니메이션 설정
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 화면 켜진 상태 유지
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);  // SharedPreferences 초기화

        setPermissions();  // 권한 설정

        tts = new TextToSpeech(this, this);  // TTS 초기화
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);  // 진동 서비스 초기화

        Button ttsButton = findViewById(R.id.replay);  // 듣기 버튼
        Button shoot = findViewById(R.id.shoot);  // 촬영 버튼

        // 액티비티 결과 런처 초기화
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        float speed2 = result.getData().getFloatExtra("SPEED2", 1.0f);
                        setTtsSpeed(speed2);
                        speed = speed2;
                    } else {
                        Toast.makeText(getApplicationContext(), "반환 실패", Toast.LENGTH_SHORT).show();
                    }
                });

        // 촬영 버튼 클릭 리스너 설정
        shoot.setOnClickListener(view -> {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(100);  // 100 milliseconds 진동
            }

            // 카메라 프래그먼트의 captureImage 메소드 호출
            CameraFragment cameraFragment = (CameraFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (cameraFragment != null) {
                cameraFragment.captureImage();
            }
        });

        // 듣기 버튼 클릭 리스너 설정
        ttsButton.setOnClickListener(v -> startListening());

        ImageButton ttsCtlSlow = findViewById(R.id.ttsCtlSlow);
        ImageButton ttsCtlPause = findViewById(R.id.ttsCtlPause);
        ImageButton ttsCtlFast = findViewById(R.id.ttsCtlFast);

        // TTS 속도 제어 버튼 클릭 리스너 설정
        ttsCtlSlow.setOnClickListener(v -> {
            speed = Math.max(0.8f, speed - 0.2f);  // 최소 속도
            if (speed == 0.8f) {
                tts.speak("티티에스 최저속도", TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                tts.speak("티티에스 느리게", TextToSpeech.QUEUE_FLUSH, null, null);
            }
            setTtsSpeed(speed);
        });

        ttsCtlPause.setOnClickListener(v -> {
            speed = 1.4f;
            tts.speak("티티에스 기본 속도", TextToSpeech.QUEUE_FLUSH, null, null);
            setTtsSpeed(speed);
        });

        ttsCtlFast.setOnClickListener(v -> {
            speed = Math.min(2.0f, speed + 0.2f);  // 최대 속도
            if (speed == 2.0f) {
                tts.speak("티티에스 최대속도", TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                tts.speak("티티에스 빠르게", TextToSpeech.QUEUE_FLUSH, null, null);
            }
            setTtsSpeed(speed);
        });

        loadCameraFragment();  // 카메라 프래그먼트 로드
        initSpeechRecognizer();  // 음성 인식 초기화
    }

    private void setPermissions() {
        // 필요한 권한 목록 설정
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        };

        // 특정 안드로이드 버전 이상일 경우 추가 권한 설정
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        }

        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "All permissions are required to use this feature.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            loadCameraFragment();
        }
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new SpeechRecognitionListener());
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
    }

    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            speechRecognizer.startListening(speechRecognizerIntent);
        } else {
            Toast.makeText(this, "Audio permission is required to use this feature", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    private void loadCameraFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        CameraFragment cameraFragment = new CameraFragment();
        fragmentTransaction.replace(R.id.fragment_container, cameraFragment);
        fragmentTransaction.commit();
    }

    @Override
    public void onImageCaptured(String imageUri, String classificationResult) {
        if (classificationResult != null && !classificationResult.isEmpty()) {
            Intent intent = new Intent(this, NewActivity.class);
            intent.putExtra("imageUri", imageUri);
            intent.putExtra("classificationResult", classificationResult);
            activityResultLauncher.launch(intent);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.KOREAN);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(MainActivity.this, "한국어를 지원하지 않습니다.", Toast.LENGTH_SHORT).show();
            } else {
                speed = sharedPreferences.getFloat(SPEED_KEY, 1.0f);
                tts.setSpeechRate(speed);
            }
        } else {
            Toast.makeText(MainActivity.this, "TTS 초기화에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setTtsSpeed(float setSpeed) {
        if (tts != null) {
            tts.setSpeechRate(setSpeed);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat(SPEED_KEY, setSpeed);
            editor.apply();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }

    private class SpeechRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Toast.makeText(MainActivity.this, "음성 인식을 시작합니다.", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
        }

        @Override
        public void onError(int error) {
            Toast.makeText(MainActivity.this, "음성 인식 오류: " + error, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null) {
                for (String result : matches) {
                    Toast.makeText(MainActivity.this, "인식 결과: " + result, Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }
}