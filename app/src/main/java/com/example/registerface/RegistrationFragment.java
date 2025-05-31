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

import com.example.registerface.databinding.FragmentRegistrationBinding;
import com.example.registerface.db.DatabaseHelper;
import com.example.registerface.face.CameraHelper;
import com.example.registerface.face.FaceDetectorHelper;
import com.example.registerface.models.User;
import com.example.registerface.utils.FaceImageUtils;
import com.google.mlkit.vision.face.Face;

import java.util.List;

public class RegistrationFragment extends Fragment implements FaceDetectorHelper.FaceDetectorListener {
    private static final String TAG = "RegistrationFragment";
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private FragmentRegistrationBinding binding;
    private DatabaseHelper dbHelper;
    private EditText userIdInput;
    private EditText nameInput;
    private EditText emailInput;
    private Button registerButton;
    private Button scanFaceButton;
    private PreviewView previewView;
    private String capturedFaceData;
    private CameraHelper cameraHelper;
    private boolean isScanning = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentRegistrationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new DatabaseHelper(getContext());

        userIdInput = binding.userIdInput;
        nameInput = binding.nameInput;
        emailInput = binding.emailInput;
        registerButton = binding.registerButton;
        scanFaceButton = binding.scanFaceButton;
        previewView = binding.previewView;

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

        registerButton.setOnClickListener(v -> {
            String userId = userIdInput.getText().toString();
            String name = nameInput.getText().toString();
            String email = emailInput.getText().toString();

            if (userId.isEmpty() || name.isEmpty() || email.isEmpty() || capturedFaceData == null) {
                Toast.makeText(getContext(), "Please fill all fields and scan your face", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidUserId(userId)) {
                Toast.makeText(getContext(), "User ID must contain only numbers", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidEmail(email)) {
                Toast.makeText(getContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Registering user with face data: " + capturedFaceData);
            User user = new User(userId, capturedFaceData, name, email);
            if (dbHelper.addUser(user)) {
                Log.d(TAG, "User registered successfully");
                Toast.makeText(getContext(), "Registration successful!", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(RegistrationFragment.this)
                        .navigate(R.id.action_registration_to_login);
            } else {
                Log.e(TAG, "Failed to register user");
                Toast.makeText(getContext(), "Registration failed. User ID might already exist.", Toast.LENGTH_SHORT)
                        .show();
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
            Log.d(TAG, "Face captured during registration: " + capturedFaceData);

            // Сохраняем фото лица
            Bitmap faceBitmap = previewView.getBitmap();
            String userId = userIdInput.getText().toString().trim();
            if (faceBitmap != null && !userId.isEmpty()) {
                FaceImageUtils.saveFaceImage(getContext(), faceBitmap, "face_reg_" + userId + ".jpg");
            }

            // Проверяем качество захвата лица
            if (face.getSmilingProbability() != null &&
                    face.getLeftEyeOpenProbability() != null &&
                    face.getRightEyeOpenProbability() != null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Scanning...", Toast.LENGTH_SHORT).show();

                    Toast.makeText(getContext(), "Face captured successfully!", Toast.LENGTH_SHORT).show();
                    stopFaceScanning();

                });
            } else {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Please look directly at the camera and try again", Toast.LENGTH_LONG)
                            .show();
                });
            }
        }
    }

    @Override
    public void onFaceDetectionError(Exception e) {
        Log.e(TAG, "Face detection error during registration", e);
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

    private boolean isValidEmail(String email) {
        String emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";
        return email != null && email.matches(emailPattern);
    }

    private boolean isValidUserId(String userId) {
        return userId != null && userId.matches("\\d+");
    }
}