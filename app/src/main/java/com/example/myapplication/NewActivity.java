// NewActivity.java
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
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class NewActivity extends BaseActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "NewActivity";
    private static final String PREFS_NAME = "TTS_PREFS";
    private static final String SPEED_KEY = "SPEED_KEY";
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private TextToSpeech tts;
    private float speed2 = 1.4f;  // TTS 초기 속도
    private Vibrator vibrator;
    private SharedPreferences sharedPreferences;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;
    private String lastRecognitionResult = null;

    public NewActivity() {
        super(TransitionMode.HORIZON);  // 화면 전환 애니메이션 설정
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new);

        TextView classificationResultView = findViewById(R.id.classificationResultView);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        tts = new TextToSpeech(this, this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new SpeechRecognitionListener());
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        Button ttsButton2 = findViewById(R.id.replay2);  // 듣기2 버튼
        Button shoot2 = findViewById(R.id.shoot2);  // 촬영2 버튼

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("classificationResult")) {
            String classificationResult = intent.getStringExtra("classificationResult");
            if (classificationResult != null) {
                classificationResultView.setText(classificationResult);
            } else {
                Log.e("NewActivity", "Received null classification result");
            }
        } else {
            Log.e("NewActivity", "Intent or classification result missing");
        }

        shoot2.setOnClickListener(view -> {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(100);  // 100 milliseconds 진동
            }
            finish();  // 액티비티 종료
        });

        ttsButton2.setOnClickListener(v -> {
            if (lastRecognitionResult != null) {
                if (lastRecognitionResult.equalsIgnoreCase("레시피")) {
                    tts.speak("조리 방법을 안내합니다", TextToSpeech.QUEUE_FLUSH, null, "RECIPE_RESULT");
                } else if (lastRecognitionResult.equalsIgnoreCase("가격")) {
                    tts.speak("이 상품의 가격은 얼마입니다", TextToSpeech.QUEUE_FLUSH, null, "PRICE_RESULT");
                }
            } else {
                tts.speak("다시 듣기", TextToSpeech.QUEUE_FLUSH, null, "REPLAY");
            }
        });

        ImageButton ttsCtlSlow2 = findViewById(R.id.ttsCtlSlow2);
        ImageButton ttsCtlPause2 = findViewById(R.id.ttsCtlPause2);
        ImageButton ttsCtlFast2 = findViewById(R.id.ttsCtlFast2);

        ttsCtlSlow2.setOnClickListener(v -> {
            speed2 = Math.max(0.8f, speed2 - 0.2f);  // 최소 속도 0.8f
            tts.speak("티티에스 느리게", TextToSpeech.QUEUE_FLUSH, null, null);
            setTtsSpeed(speed2);
        });

        ttsCtlPause2.setOnClickListener(v -> {
            speed2 = 1.4f;
            tts.speak("티티에스 기본 속도", TextToSpeech.QUEUE_FLUSH, null, null);
            setTtsSpeed(speed2);
        });

        ttsCtlFast2.setOnClickListener(v -> {
            speed2 = Math.min(2.0f, speed2 + 0.2f);  // 최대 속도 2.0f
            tts.speak("티티에스 빠르게", TextToSpeech.QUEUE_FLUSH, null, null);
            setTtsSpeed(speed2);
        });

        checkAudioPermission();
    }

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Audio permission granted");
            } else {
                Toast.makeText(this, "Audio permission is required to use this feature", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startListening() {
        if (!isListening) {
            tts.speak("음성 인식을 시작합니다", TextToSpeech.QUEUE_FLUSH, null, "START_LISTENING");
        }
    }

    private void stopListening() {
        if (isListening) {
            speechRecognizer.stopListening();
            tts.speak("음성 인식을 종료합니다", TextToSpeech.QUEUE_FLUSH, null, "STOP_LISTENING");
            isListening = false;
        }
    }

    private class SpeechRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Toast.makeText(NewActivity.this, "음성 인식을 시작합니다.", Toast.LENGTH_SHORT).show();
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
            Log.e(TAG, "Speech recognition error: " + error);
            if (isListening) {
                speechRecognizer.startListening(speechRecognizerIntent);
            }
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null) {
                for (String result : matches) {
                    if (result.equalsIgnoreCase("레시피")) {
                        lastRecognitionResult = "레시피";
                        tts.speak("조리 방법을 안내합니다", TextToSpeech.QUEUE_FLUSH, null, "RECIPE_RESULT");
                        return;
                    } else if (result.equalsIgnoreCase("가격")) {
                        lastRecognitionResult = "가격";
                        tts.speak("이 상품의 가격은 얼마입니다", TextToSpeech.QUEUE_FLUSH, null, "PRICE_RESULT");
                        return;
                    }
                }
                lastRecognitionResult = null;
                stopListening();
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.KOREAN);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(NewActivity.this, "한국어를 지원하지 않습니다.", Toast.LENGTH_SHORT).show();
            } else {
                speed2 = sharedPreferences.getFloat(SPEED_KEY, 1.0f);
                tts.setSpeechRate(speed2);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        if ("START_LISTENING".equals(utteranceId)) {
                            runOnUiThread(() -> {
                                speechRecognizer.startListening(speechRecognizerIntent);
                                Log.d(TAG, "Speech recognizer started listening");
                            });
                            isListening = true;
                        } else if ("RECIPE_RESULT".equals(utteranceId) || "PRICE_RESULT".equals(utteranceId)) {
                            runOnUiThread(NewActivity.this::stopListening);
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                    }
                });
            }
        } else {
            Toast.makeText(NewActivity.this, "TTS 초기화에 실패했습니다.", Toast.LENGTH_SHORT).show();
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
}