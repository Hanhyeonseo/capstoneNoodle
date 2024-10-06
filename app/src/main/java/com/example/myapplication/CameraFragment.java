package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.ml.BestFloat32;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFragment extends Fragment {

    public interface OnImageCapturedListener {
        void onImageCaptured(String imageUri, String classificationResult);
    }

    private PreviewView previewView;
    private ImageView overlayView;

    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;

    private OnImageCapturedListener imageCapturedListener;

    private static final int INPUT_SIZE = 640;

    private static final float CONFIDENCE_THRESHOLD = 0.25f;
    private static final float NMS_THRESHOLD = 0.45f;

    // YOLOv8 모델에서 사용하는 라벨 수 확인 (출력의 클래스 수와 일치하도록 수정)
    private final String[] ramenLabels = {
            "jinHot", "jinMild", "wang", "king", "kimchi", "ojingeo", "sesame", "kaguri", "shin", "buldak",
            "buldakCarbo", "buldak4Cheese", "kingSoup", "kingSoup2", "jinMildSoup", "sesameSoup1",
            "sesameSoup2", "sesameSoup3", "buldakSoup", "buldakSoup2", "jinHotSoup", "shinSoup",
            "kimchiSoup", "kimchiSoup2", "carboSoup", "carboSoup2", "kaguriSoup", "ojingeoSoup",
            "cheeseSoup", "cheeseSoup2", "wangSoup", "wangSoup2"
    };
// 또는 모델에 맞는 라벨이 몇 개인지 확인하고 이에 맞게 배열을 확장/축소해야 합니다.


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnImageCapturedListener) {
            imageCapturedListener = (OnImageCapturedListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnImageCapturedListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);
        previewView = view.findViewById(R.id.previewView);
        overlayView = new ImageView(getContext());
        ((ViewGroup) previewView.getParent()).addView(overlayView);
        cameraExecutor = Executors.newSingleThreadExecutor();

        startCamera();

        return view;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraFragment", "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    public void captureImage() {
        previewView.post(() -> {
            Bitmap bitmap = previewView.getBitmap();
            if (bitmap != null) {
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
                classifyImage(resizedBitmap);

                if (imageCapturedListener != null) {
                    String imageUri = saveImageToExternalStorage(bitmap);
                    if (imageUri != null) {
                        imageCapturedListener.onImageCaptured(imageUri, "Image captured");
                    }
                }
            }
        });
    }

    // YOLOv8 모델로 이미지를 분류하는 메서드
    public void classifyImage(Bitmap bitmap) {
        try {
            // TensorFlow Lite 모델 로드
            BestFloat32 model = BestFloat32.newInstance(requireContext());

            // 입력 이미지를 TensorImage로 로드
            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(bitmap);

            // TensorImage로 모델 처리
            BestFloat32.Outputs outputs = model.process(tensorImage); // TensorImage로 입력 전달

            // 출력 데이터를 가져옵니다.
            List<Category> output = outputs.getOutputAsCategoryList(); // 모델 출력 형식에 맞추어 처리

            // 출력 데이터를 해석하고 로그로 확인
            for (Category category : output) {
                Log.d("CameraFragment", String.format("Detected %s with confidence %.2f%%",
                        category.getLabel(), category.getScore() * 100));
            }

            // 모델 리소스 해제
            model.close();
        } catch (IOException e) {
            Log.e("CameraFragment", "Error running model inference", e);
        }
    }



    // YOLOv8 출력 후처리 (바운딩 박스와 클래스 확률 처리)
    private List<Detection> processYOLOv8Output(float[] outputArray) {
        List<Detection> detections = new ArrayList<>();

        // YOLOv8의 각 출력값을 바운딩 박스와 클래스 확률로 변환
        for (int i = 0; i < outputArray.length / 36; i++) {
            int index = i * 36;  // 각 출력의 시작 인덱스

            float confidence = outputArray[index + 4];  // 클래스 확률
            if (confidence > CONFIDENCE_THRESHOLD) {
                // 바운딩 박스 좌표 (x_center, y_center, width, height)
                float xCenter = outputArray[index];
                float yCenter = outputArray[index + 1];
                float width = outputArray[index + 2];
                float height = outputArray[index + 3];

                // 클래스 ID 추출
                int classId = -1;
                float maxScore = -1f;
                for (int c = 5; c < 36; c++) {
                    if (outputArray[index + c] > maxScore) {
                        maxScore = outputArray[index + c];
                        classId = c - 5;  // 클래스 ID는 5번째 인덱스부터 시작
                    }
                }

                // 바운딩 박스와 클래스 정보 저장
                Detection detection = new Detection(xCenter, yCenter, width, height, classId, confidence);
                detections.add(detection);
            }
        }

        // NMS(Non-Maximum Suppression) 적용
        return applyNonMaximumSuppression(detections);
    }

    // NMS(Non-Maximum Suppression) 적용 메서드
    private List<Detection> applyNonMaximumSuppression(List<Detection> detections) {
        List<Detection> result = new ArrayList<>();
        Collections.sort(detections, (a, b) -> Float.compare(b.confidence, a.confidence));

        for (Detection detection : detections) {
            boolean keep = true;
            for (Detection res : result) {
                if (iou(detection, res) > NMS_THRESHOLD) {
                    keep = false;
                    break;
                }
            }
            if (keep) {
                result.add(detection);
            }
        }
        return result;
    }

    // IOU(Intersection over Union) 계산
    private float iou(Detection box1, Detection box2) {
        float intersectionArea = Math.max(0, Math.min(box1.x + box1.width, box2.x + box2.width) - Math.max(box1.x, box2.x)) *
                Math.max(0, Math.min(box1.y + box1.height, box2.y + box2.height) - Math.max(box1.y, box2.y));
        float unionArea = box1.width * box1.height + box2.width * box2.height - intersectionArea;
        return intersectionArea / unionArea;
    }

    private String saveImageToExternalStorage(Bitmap bitmap) {
        File storageDir = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MyApp");
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            Log.e("CameraFragment", "Failed to create directory");
            return null;
        }

        String fileName = "captured_image.jpg";
        File imageFile = new File(storageDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            return Uri.fromFile(imageFile).toString();
        } catch (IOException e) {
            Log.e("CameraFragment", "Error saving image to external storage", e);
            return null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    // Detection 클래스 (바운딩 박스와 클래스 정보 저장)
    static class Detection {
        float x, y, width, height;
        int classId;
        float confidence;

        public Detection(float x, float y, float width, float height, int classId, float confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.classId = classId;
            this.confidence = confidence;
        }
    }
}