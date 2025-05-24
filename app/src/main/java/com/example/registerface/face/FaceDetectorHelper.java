package com.example.registerface.face;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;
import com.google.mlkit.vision.face.FaceContour;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class FaceDetectorHelper {
    private static final String TAG = "FaceDetectorHelper";
    private final com.google.mlkit.vision.face.FaceDetector detector;
    private FaceDetectorListener listener;
    private static final float SIMILARITY_THRESHOLD = 0.15f;
    private static final float LANDMARK_DISTANCE_THRESHOLD = 0.10f;
    private static final float FEATURE_DIFF_THRESHOLD = 0.15f;
    private static final float CONTOUR_POINT_DISTANCE_THRESHOLD = 0.15f;
    private static final float HEAD_ANGLE_THRESHOLD = 30f;
    private static final int MAX_CONTOUR_POINTS = 20;

    public interface FaceDetectorListener {
        void onFaceDetected(List<Face> faces);
        void onFaceDetectionError(Exception e);
    }

    public FaceDetectorHelper(FaceDetectorListener listener) {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.35f)  // Increased minimum face size for better quality
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)  // Added contour detection
                .enableTracking()
                .build();

        detector = FaceDetection.getClient(options);
        this.listener = listener;
    }

    public Task<List<Face>> detectFaces(ImageProxy image) {
        InputImage inputImage = InputImage.fromMediaImage(
                image.getImage(),
                image.getImageInfo().getRotationDegrees()
        );

        return detector.process(inputImage)
                .addOnSuccessListener(faces -> {
                    if (listener != null) {
                        listener.onFaceDetected(faces);
                    }
                })
                .addOnFailureListener(e -> {
                    if (listener != null) {
                        listener.onFaceDetectionError(e);
                    }
                });
    }

    public static String getFaceData(Face face) {
        StringBuilder faceData = new StringBuilder();
        
        // Get face bounds
        Rect bounds = face.getBoundingBox();
        float width = bounds.width();
        float height = bounds.height();
        
        // Проверка на нулевые размеры
        if (width == 0 || height == 0) {
            Log.e(TAG, "Invalid face dimensions");
            return "";
        }

        // Получаем углы поворота головы
        float headEulerY = face.getHeadEulerAngleY();
        float headEulerZ = face.getHeadEulerAngleZ();
        
        // Проверяем, не слишком ли сильно повернута голова
        if (Math.abs(headEulerY) > HEAD_ANGLE_THRESHOLD || Math.abs(headEulerZ) > HEAD_ANGLE_THRESHOLD) {
            Log.d(TAG, String.format("Head rotation too large: Y=%.1f, Z=%.1f", headEulerY, headEulerZ));
            return "";
        }

        // Нормализуем координаты относительно размера лица
        float scale = Math.max(width, height);
        float centerX = bounds.left + width / 2;
        float centerY = bounds.top + height / 2;

        // Get face landmarks with more precision
        FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);
        FaceLandmark nose = face.getLandmark(FaceLandmark.NOSE_BASE);
        FaceLandmark mouth = face.getLandmark(FaceLandmark.MOUTH_BOTTOM);
        FaceLandmark leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK);
        FaceLandmark rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK);

        // Add all landmarks with high precision, нормализуем относительно центра лица
        if (leftEye != null) {
            float relX = (leftEye.getPosition().x - centerX) / scale;
            float relY = (leftEye.getPosition().y - centerY) / scale;
            faceData.append("leftEye:").append(String.format("%.4f", relX)).append(",")
                    .append(String.format("%.4f", relY)).append(";");
        }
        if (rightEye != null) {
            float relX = (rightEye.getPosition().x - centerX) / scale;
            float relY = (rightEye.getPosition().y - centerY) / scale;
            faceData.append("rightEye:").append(String.format("%.4f", relX)).append(",")
                    .append(String.format("%.4f", relY)).append(";");
        }
        if (nose != null) {
            float relX = (nose.getPosition().x - centerX) / scale;
            float relY = (nose.getPosition().y - centerY) / scale;
            faceData.append("nose:").append(String.format("%.4f", relX)).append(",")
                    .append(String.format("%.4f", relY)).append(";");
        }
        if (mouth != null) {
            float relX = (mouth.getPosition().x - centerX) / scale;
            float relY = (mouth.getPosition().y - centerY) / scale;
            faceData.append("mouth:").append(String.format("%.4f", relX)).append(",")
                    .append(String.format("%.4f", relY)).append(";");
        }
        if (leftCheek != null) {
            float relX = (leftCheek.getPosition().x - centerX) / scale;
            float relY = (leftCheek.getPosition().y - centerY) / scale;
            faceData.append("leftCheek:").append(String.format("%.4f", relX)).append(",")
                    .append(String.format("%.4f", relY)).append(";");
        }
        if (rightCheek != null) {
            float relX = (rightCheek.getPosition().x - centerX) / scale;
            float relY = (rightCheek.getPosition().y - centerY) / scale;
            faceData.append("rightCheek:").append(String.format("%.4f", relX)).append(",")
                    .append(String.format("%.4f", relY)).append(";");
        }

        // Get face contours and normalize them
        FaceContour contour = face.getContour(FaceContour.FACE);
        if (contour != null && contour.getPoints() != null) {
            List<PointF> faceContour = contour.getPoints();
            if (!faceContour.isEmpty()) {
                faceData.append("faceContour:");
                // Выбираем равномерно распределенные точки контура
                int step = Math.max(1, faceContour.size() / MAX_CONTOUR_POINTS);
                for (int i = 0; i < faceContour.size(); i += step) {
                    PointF point = faceContour.get(i);
                    float relX = (point.x - centerX) / scale;
                    float relY = (point.y - centerY) / scale;
                    faceData.append(String.format("%.4f", relX)).append(",")
                            .append(String.format("%.4f", relY)).append(";");
                }
            }
        }

        // Get face features with high precision
        faceData.append("smile:").append(String.format("%.4f", face.getSmilingProbability() != null ? face.getSmilingProbability() : 0f)).append(";");
        faceData.append("leftEyeOpen:").append(String.format("%.4f", face.getLeftEyeOpenProbability() != null ? face.getLeftEyeOpenProbability() : 0f)).append(";");
        faceData.append("rightEyeOpen:").append(String.format("%.4f", face.getRightEyeOpenProbability() != null ? face.getRightEyeOpenProbability() : 0f)).append(";");
        faceData.append("headEulerY:").append(String.format("%.4f", headEulerY)).append(";");
        faceData.append("headEulerZ:").append(String.format("%.4f", headEulerZ));

        Log.d(TAG, "Generated face data: " + faceData.toString());
        return faceData.toString();
    }

    public static class FaceComparisonResult {
        public final boolean matches;
        public final float similarityPercentage;

        public FaceComparisonResult(boolean matches, float similarityPercentage) {
            this.matches = matches;
            this.similarityPercentage = similarityPercentage;
        }
    }

    private static float compareLandmarksWithSimilarity(String landmark1, String landmark2) {
        try {
            String[] coords1 = landmark1.split(",");
            String[] coords2 = landmark2.split(",");
            
            if (coords1.length != 2 || coords2.length != 2) {
                Log.d(TAG, "Invalid landmark coordinates");
                return 0f;
            }

            float x1 = Float.parseFloat(coords1[0].replace(",", "."));
            float y1 = Float.parseFloat(coords1[1].replace(",", "."));
            float x2 = Float.parseFloat(coords2[0].replace(",", "."));
            float y2 = Float.parseFloat(coords2[1].replace(",", "."));

            // Вычисляем расстояние между точками
            float distance = (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
            
            // Используем экспоненциальную функцию для более плавного уменьшения схожести
            float similarity = (float) Math.exp(-distance / LANDMARK_DISTANCE_THRESHOLD);
            
            Log.d(TAG, String.format("Landmark comparison: (%.4f,%.4f) vs (%.4f,%.4f) -> distance=%.4f, similarity=%.4f",
                x1, y1, x2, y2, distance, similarity));
            return similarity;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing landmark coordinates: " + e.getMessage());
            return 0f;
        }
    }

    private static float compareFeatureValuesWithSimilarity(String value1, String value2) {
        try {
            float v1 = Float.parseFloat(value1.replace(",", "."));
            float v2 = Float.parseFloat(value2.replace(",", "."));
            
            // Для углов поворота головы используем специальную обработку
            if (value1.contains("headEuler")) {
                // Нормализуем углы в диапазон [-180, 180]
                v1 = normalizeAngle(v1);
                v2 = normalizeAngle(v2);
                float diff = Math.abs(v1 - v2);
                // Используем экспоненциальную функцию для более плавного уменьшения схожести
                float similarity = (float) Math.exp(-diff / HEAD_ANGLE_THRESHOLD);
                Log.d(TAG, String.format("Head angle comparison: %.2f vs %.2f -> diff=%.2f, similarity=%.4f",
                    v1, v2, diff, similarity));
                return similarity;
            }
            
            float diff = Math.abs(v1 - v2);
            // Используем экспоненциальную функцию для более плавного уменьшения схожести
            float similarity = (float) Math.exp(-diff / FEATURE_DIFF_THRESHOLD);
            Log.d(TAG, String.format("Feature comparison: %.4f vs %.4f -> diff=%.4f, similarity=%.4f",
                v1, v2, diff, similarity));
            return similarity;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing feature values: " + e.getMessage());
            return 0f;
        }
    }

    private static float normalizeAngle(float angle) {
        // Нормализуем угол в диапазон [-180, 180]
        angle = angle % 360;
        if (angle > 180) {
            angle -= 360;
        } else if (angle < -180) {
            angle += 360;
        }
        return angle;
    }

    private static float compareContours(String contour1, String contour2) {
        try {
            String[] points1 = contour1.split(";");
            String[] points2 = contour2.split(";");
            
            if (points1.length == 0 || points2.length == 0) {
                return 0f;
            }

            float totalSimilarity = 0f;
            int totalPoints = 0;

            // Сравниваем каждую точку контура
            for (int i = 0; i < Math.min(points1.length, points2.length); i++) {
                String[] coords1 = points1[i].split(",");
                String[] coords2 = points2[i].split(",");
                
                if (coords1.length == 2 && coords2.length == 2) {
                    float x1 = Float.parseFloat(coords1[0].replace(",", "."));
                    float y1 = Float.parseFloat(coords1[1].replace(",", "."));
                    float x2 = Float.parseFloat(coords2[0].replace(",", "."));
                    float y2 = Float.parseFloat(coords2[1].replace(",", "."));

                    float distance = (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
                    float similarity = (float) Math.exp(-distance / CONTOUR_POINT_DISTANCE_THRESHOLD);
                    
                    totalSimilarity += similarity;
                    totalPoints++;
                }
            }

            return totalPoints > 0 ? totalSimilarity / totalPoints : 0f;
        } catch (Exception e) {
            Log.e(TAG, "Error comparing contours: " + e.getMessage());
            return 0f;
        }
    }

    public static FaceComparisonResult compareFaces(String face1Data, String face2Data) {
        if (face1Data == null || face2Data == null) {
            Log.e(TAG, "Face data is null");
            return new FaceComparisonResult(false, 0f);
        }

        Map<String, String> face1Features = parseFaceData(face1Data);
        Map<String, String> face2Features = parseFaceData(face2Data);

        // Сначала проверяем углы поворота головы
        if (face1Features.containsKey("headEulerY") && face2Features.containsKey("headEulerY") &&
            face1Features.containsKey("headEulerZ") && face2Features.containsKey("headEulerZ")) {
            float y1 = Float.parseFloat(face1Features.get("headEulerY").replace(",", "."));
            float y2 = Float.parseFloat(face2Features.get("headEulerY").replace(",", "."));
            float z1 = Float.parseFloat(face1Features.get("headEulerZ").replace(",", "."));
            float z2 = Float.parseFloat(face2Features.get("headEulerZ").replace(",", "."));
            
            if (Math.abs(normalizeAngle(y1 - y2)) > HEAD_ANGLE_THRESHOLD ||
                Math.abs(normalizeAngle(z1 - z2)) > HEAD_ANGLE_THRESHOLD) {
                Log.d(TAG, "Head rotation difference too large");
                return new FaceComparisonResult(false, 0f);
            }
        }

        float totalSimilarity = 0f;
        int totalFeatures = 0;

        // Compare landmarks with higher precision
        String[] landmarks = {"leftEye", "rightEye", "nose", "mouth", "leftCheek", "rightCheek"};
        for (String landmark : landmarks) {
            if (face1Features.containsKey(landmark) && face2Features.containsKey(landmark)) {
                totalFeatures++;
                float similarity = compareLandmarksWithSimilarity(face1Features.get(landmark), face2Features.get(landmark));
                totalSimilarity += similarity;
                Log.d(TAG, String.format("Landmark %s similarity: %.2f", landmark, similarity));
            }
        }

        // Compare face contours
        if (face1Features.containsKey("faceContour") && face2Features.containsKey("faceContour")) {
            totalFeatures++;
            float similarity = compareContours(face1Features.get("faceContour"), face2Features.get("faceContour"));
            totalSimilarity += similarity;
            Log.d(TAG, String.format("Face contour similarity: %.2f", similarity));
        }

        // Compare face features with higher precision
        String[] features = {"smile", "leftEyeOpen", "rightEyeOpen", "headEulerY", "headEulerZ"};
        for (String feature : features) {
            if (face1Features.containsKey(feature) && face2Features.containsKey(feature)) {
                totalFeatures++;
                float similarity = compareFeatureValuesWithSimilarity(face1Features.get(feature), face2Features.get(feature));
                totalSimilarity += similarity;
                Log.d(TAG, String.format("Feature %s similarity: %.2f", feature, similarity));
            }
        }

        // Calculate overall similarity
        float averageSimilarity = totalFeatures > 0 ? totalSimilarity / totalFeatures : 0;
        float similarityPercentage = averageSimilarity * 100;

        Log.d(TAG, String.format("Face comparison results: total similarity=%.1f%%", similarityPercentage));

        return new FaceComparisonResult(similarityPercentage >= SIMILARITY_THRESHOLD * 100, similarityPercentage);
    }

    private static Map<String, String> parseFaceData(String faceData) {
        Map<String, String> features = new HashMap<>();
        String[] parts = faceData.split(";");
        for (String part : parts) {
            String[] keyValue = part.split(":");
            if (keyValue.length == 2) {
                features.put(keyValue[0], keyValue[1]);
            }
        }
        return features;
    }
} 