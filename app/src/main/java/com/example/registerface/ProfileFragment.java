package com.example.registerface;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.registerface.databinding.FragmentProfileBinding;
import com.example.registerface.models.User;
import com.example.registerface.db.DatabaseHelper;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.FileOutputStream;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private SharedPreferences sharedPreferences;
    private SimpleDateFormat dateFormat;
    private DatabaseHelper dbHelper;
    private static final String TAG = "ProfileFragment";
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_REG_DATE = "registration_date";
    private static final String KEY_LAST_LOGIN = "last_login";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        sharedPreferences = requireContext().getSharedPreferences(PREF_NAME, 0);
        dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        dbHelper = new DatabaseHelper(getContext());

        // Получаем данные из Bundle
        Bundle args = getArguments();
        String userId = null;
        if (args != null) {
            String name = args.getString("name");
            String email = args.getString("email");
            float faceSimilarity = args.getFloat("faceSimilarity", 0f);
            userId = args.getString("userId");
            
            // Сохраняем данные в SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_NAME, name);
            editor.putString(KEY_EMAIL, email);
            editor.putLong(KEY_LAST_LOGIN, System.currentTimeMillis());
            editor.putFloat("face_similarity", faceSimilarity);
            editor.putString("user_id", userId);
            // Если это первая регистрация, сохраняем дату регистрации
            if (!sharedPreferences.contains(KEY_REG_DATE)) {
                editor.putLong(KEY_REG_DATE, System.currentTimeMillis());
            }
            editor.apply();

            // Загружаем и отображаем данные о лице
            if (userId != null) {
                User user = dbHelper.getUser(userId);
                if (user != null) {
                    displayFaceData(user.getFaceData(), faceSimilarity);
                }
            }
        } else {
            userId = sharedPreferences.getString("user_id", null);
        }

        // Загружаем и отображаем фото лица
        if (userId != null) {
            Bitmap regFace = loadFaceImage("face_reg_" + userId + ".jpg");
            Bitmap loginFace = loadFaceImage("face_login_" + userId + ".jpg");
            if (regFace != null) binding.ivRegisteredFace.setImageBitmap(regFace);
            if (loginFace != null) binding.ivLoginFace.setImageBitmap(loginFace);
        }

        setupViews();
        loadUserData();
        setupClickListeners();
    }

    private void setupViews() {
        binding.btnEditProfile.setVisibility(View.VISIBLE);
        binding.btnChangePassword.setVisibility(View.GONE);
        binding.btnShowUsers.setVisibility(View.VISIBLE); // Показываем кнопку просмотра пользователей
    }

    private void loadUserData() {
        String name = sharedPreferences.getString(KEY_NAME, "");
        String email = sharedPreferences.getString(KEY_EMAIL, "");
        long regDate = sharedPreferences.getLong(KEY_REG_DATE, System.currentTimeMillis());
        long lastLogin = sharedPreferences.getLong(KEY_LAST_LOGIN, System.currentTimeMillis());

        Log.d(TAG, "Loading user data - Name: " + name + ", Email: " + email);

        binding.nameText.setText("Имя: " + name);
        binding.emailText.setText("Email: " + email);
        binding.registrationDateText.setText("Дата регистрации: " + dateFormat.format(regDate));
        binding.lastLoginText.setText("Последний вход: " + dateFormat.format(lastLogin));
    }

    private void setupClickListeners() {
        binding.btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
        binding.logoutButton.setOnClickListener(v -> logout());
        binding.btnShowUsers.setOnClickListener(v -> showAllUsers());
    }

    private void showAllUsers() {
        StringBuilder usersList = new StringBuilder();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("users", null, null, null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                String userId = cursor.getString(cursor.getColumnIndexOrThrow("user_id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                String email = cursor.getString(cursor.getColumnIndexOrThrow("email"));
                
                usersList.append("ID: ").append(userId)
                        .append("\nИмя: ").append(name)
                        .append("\nEmail: ").append(email)
                        .append("\n\n");
            } while (cursor.moveToNext());
            cursor.close();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Список пользователей")
                .setMessage(usersList.length() > 0 ? usersList.toString() : "Нет зарегистрированных пользователей")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showEditProfileDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_profile, null);
        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);

        String currentName = binding.nameText.getText().toString().replace("Имя: ", "");
        String currentEmail = binding.emailText.getText().toString().replace("Email: ", "");
        
        Log.d(TAG, "Current profile - Name: " + currentName + ", Email: " + currentEmail);
        
        etName.setText(currentName);
        etEmail.setText(currentEmail);

        new AlertDialog.Builder(requireContext())
                .setTitle("Редактировать профиль")
                .setView(dialogView)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String email = etEmail.getText().toString().trim();
                    
                    if (name.isEmpty() || email.isEmpty()) {
                        Toast.makeText(getContext(), "Пожалуйста, заполните все поля", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    Log.d(TAG, "Saving profile - Name: " + name + ", Email: " + email);
                    updateProfile(name, email);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateProfile(String name, String email) {
        Log.d(TAG, "Updating profile - Name: " + name + ", Email: " + email);
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_NAME, name);
        editor.putString(KEY_EMAIL, email);
        boolean success = editor.commit();
        
        Log.d(TAG, "Profile update success: " + success);
        
        if (success) {
            Toast.makeText(getContext(), "Профиль обновлен", Toast.LENGTH_SHORT).show();
            loadUserData();
        } else {
            Toast.makeText(getContext(), "Ошибка обновления профиля", Toast.LENGTH_SHORT).show();
        }
    }

    private void logout() {
        // Очищаем данные пользователя
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        // Переходим на экран входа
        NavHostFragment.findNavController(ProfileFragment.this)
                .navigate(R.id.action_profile_to_login);
    }

    private void displayFaceData(String faceData, float similarity) {
        if (faceData != null) {
            // Отображаем процент схожести
            binding.faceSimilarityText.setText(String.format("Схожесть лица: %.1f%%", similarity * 5));

            // Парсим и отображаем данные о лице
            Map<String, String> features = parseFaceData(faceData);
            StringBuilder metricsBuilder = new StringBuilder();
            StringBuilder dataBuilder = new StringBuilder();

            // Добавляем основные метрики
            if (features.containsKey("headEulerY")) {
                metricsBuilder.append("Поворот головы по Y: ")
                        .append(features.get("headEulerY"))
                        .append("°\n");
            }
            if (features.containsKey("headEulerZ")) {
                metricsBuilder.append("Поворот головы по Z: ")
                        .append(features.get("headEulerZ"))
                        .append("°\n");
            }

            // Добавляем данные о чертах лица
            String[] landmarks = {"leftEye", "rightEye", "nose", "mouth", "leftCheek", "rightCheek"};
            for (String landmark : landmarks) {
                if (features.containsKey(landmark)) {
                    dataBuilder.append(landmark).append(": ")
                            .append(features.get(landmark))
                            .append("\n");
                }
            }

            binding.faceMetricsText.setText(metricsBuilder.toString());
            binding.faceDataText.setText(dataBuilder.toString());
        }
    }

    private Map<String, String> parseFaceData(String faceData) {
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

    // Сохранение фото лица
    public String saveFaceImage(Bitmap bitmap, String fileName) {
        try {
            File file = new File(requireContext().getFilesDir(), fileName);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Загрузка фото лица
    public Bitmap loadFaceImage(String fileName) {
        try {
            File file = new File(requireContext().getFilesDir(), fileName);
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 