package com.example.registerface;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.registerface.databinding.FragmentLoginBinding;
import com.example.registerface.db.DatabaseHelper;
import com.example.registerface.face.CameraHelper;
import com.example.registerface.face.FaceDetectorHelper;
import com.example.registerface.models.User;
import com.example.registerface.utils.FaceImageUtils;
import com.google.mlkit.vision.face.Face;

import java.util.List;

public class LoginFragment extends Fragment implements FaceDetectorHelper.FaceDetectorListener {
    private static final String TAG = "LoginFragment";
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private FragmentLoginBinding binding;
    private DatabaseHelper dbHelper;
    private EditText userIdInput;
    private Button loginButton;
    private Button scanFaceButton;
    private Button registerButton;
    private PreviewView previewView;
    private String capturedFaceData;
    private CameraHelper cameraHelper;
    private boolean isScanning = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new DatabaseHelper(getContext());

        userIdInput = binding.userIdInput;
        loginButton = binding.loginButton;
        scanFaceButton = binding.scanFaceButton;
        registerButton = binding.registerButton;
        previewView = binding.previewView;

        registerButton.setOnClickListener(v -> {
            NavHostFragment.findNavController(LoginFragment.this)
                    .navigate(R.id.action_login_to_registration);
        });

        scanFaceButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                if (!isScanning) {
                    startFaceScanning();
                } else {
                    stopFaceScanning();
                }
            } else {
                requestCameraPermission();
            }
        });

        loginButton.setOnClickListener(v -> {
            String userId = userIdInput.getText().toString();

            if (userId.isEmpty() || capturedFaceData == null) {
                Toast.makeText(getContext(), "Please enter user ID and scan your face", Toast.LENGTH_SHORT).show();
                return;
            }

            User user = dbHelper.getUser(userId);
            if (user != null) {
                Log.d(TAG, "Stored face data: " + user.getFaceData());
                Log.d(TAG, "Captured face data: " + capturedFaceData);

                FaceDetectorHelper.FaceComparisonResult result = FaceDetectorHelper.compareFaces(user.getFaceData(),
                        capturedFaceData);
                String similarityMessage = String.format("Face similarity: %.1f%%", result.similarityPercentage);
                Log.d(TAG, "Face similarity percentage: " + result.similarityPercentage);

                if (result.matches) {
                    Toast.makeText(getContext(), "Login successful! " + similarityMessage, Toast.LENGTH_LONG).show();
                    // Pass user data to profile fragment
                    Bundle bundle = new Bundle();
                    bundle.putString("userId", user.getUserId());
                    bundle.putString("name", user.getName());
                    bundle.putString("email", user.getEmail());
                    bundle.putFloat("faceSimilarity", result.similarityPercentage);
                    Log.d(TAG, "Passing face similarity to profile: " + result.similarityPercentage);

                    NavHostFragment.findNavController(LoginFragment.this)
                            .navigate(R.id.action_login_to_profile, bundle);
                } else {
                    Toast.makeText(getContext(), "Face does not match! " + similarityMessage, Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getContext(), "User not found!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[] { Manifest.permission.CAMERA },
                REQUEST_CAMERA_PERMISSION);
    }

    private void startFaceScanning() {
        isScanning = true;
        scanFaceButton.setText("Stop Scanning");
        previewView.setVisibility(View.VISIBLE);

        cameraHelper = new CameraHelper(requireContext(), previewView, this);
        cameraHelper.startCamera(getViewLifecycleOwner());
    }

    private void stopFaceScanning() {
        isScanning = false;
        scanFaceButton.setText("Scan Face");
        previewView.setVisibility(View.GONE);

        if (cameraHelper != null) {
            cameraHelper.shutdown();
        }
    }

    @Override
    public void onFaceDetected(List<Face> faces) {
        if (faces.size() > 0) {
            Face face = faces.get(0);
            capturedFaceData = FaceDetectorHelper.getFaceData(face);
            Log.d(TAG, "Face captured: " + capturedFaceData);

            // Сохраняем фото лица
            Bitmap faceBitmap = previewView.getBitmap();
            String userId = userIdInput.getText().toString().trim();
            if (faceBitmap != null && !userId.isEmpty()) {
                FaceImageUtils.saveFaceImage(getContext(), faceBitmap, "face_login_" + userId + ".jpg");
            }

            requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), "Face captured successfully!", Toast.LENGTH_SHORT).show();
                stopFaceScanning();
            });
        }
    }

    @Override
    public void onFaceDetectionError(Exception e) {
        Log.e(TAG, "Face detection error", e);
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(getContext(), "Face detection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopFaceScanning();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraHelper != null) {
            cameraHelper.shutdown();
        }
        binding = null;
    }
}