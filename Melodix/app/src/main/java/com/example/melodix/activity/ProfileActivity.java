package com.example.melodix.activity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.melodix.R;
import com.example.melodix.listener.UserPreferencesManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;


import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {
    private static final String TAG = "ProfileActivity";
    private FirebaseAuth mAuth;
    private ImageView imgBack;
    private ImageView profileImage;
    private TextView tvUploadProfilePicture;
    private TextInputEditText usernameEdit;
    private TextInputEditText emailEdit;
    private TextInputEditText currentPasswordEdit;
    private TextInputEditText newPasswordEdit;
    private MaterialButton btnSaveProfile;
    private MaterialButton btnLogout;
    private ProgressBar progressBarUpload;
    private Uri selectedImageUri;
    private boolean isUploadingImage = false;
    private String currentProfileImageUrl = "";
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();

        initializeViews();
        initializeActivityLaunchers();
        setupClickListeners();
        loadUserData();
    }

    private void initializeViews() {
        Log.d(TAG, "=== Initializing Views ===");

        imgBack = findViewById(R.id.img_back);
        profileImage = findViewById(R.id.profile_image_large);
        tvUploadProfilePicture = findViewById(R.id.tv_upload_profile_picture);
        usernameEdit = findViewById(R.id.username_edit);
        emailEdit = findViewById(R.id.email_edit);
        currentPasswordEdit = findViewById(R.id.current_password_edit);
        newPasswordEdit = findViewById(R.id.new_password_edit);
        btnSaveProfile = findViewById(R.id.btn_save_profile);
        btnLogout = findViewById(R.id.btn_logout);

        // Debug: Check if views are found
        Log.d(TAG, "imgBack: " + (imgBack != null ? "✅ Found" : "❌ NULL"));
        Log.d(TAG, "profileImage: " + (profileImage != null ? "✅ Found" : "❌ NULL"));
        Log.d(TAG, "tvUploadProfilePicture: " + (tvUploadProfilePicture != null ? "✅ Found" : "❌ NULL"));

        progressBarUpload = findViewById(R.id.progress_bar_upload);
        Log.d(TAG, "progressBarUpload: " + (progressBarUpload != null ? "✅ Found" : "❌ NULL"));

        if (progressBarUpload != null) {
            progressBarUpload.setVisibility(View.GONE);
        }

        Log.d(TAG, "Views initialization completed");
    }

    private void initializeActivityLaunchers() {
        Log.d(TAG, "=== Initializing activity launchers ===");

        // Image picker launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "Image picker result: " + result.getResultCode());
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            Glide.with(this)
                                    .load(selectedImageUri)
                                    .placeholder(R.drawable.ic_launcher_foreground)
                                    .into(profileImage);
                            processSelectedImage(selectedImageUri);
                        }
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "Camera result: " + result.getResultCode());
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // Camera returns bitmap in extras
                        Bundle extras = result.getData().getExtras();
                        if (extras != null) {
                            Bitmap imageBitmap = (Bitmap) extras.get("data");
                            if (imageBitmap != null) {
                                profileImage.setImageBitmap(imageBitmap);
                                processCapturedImage(imageBitmap);
                            }
                        }
                    } else {
                        Log.w(TAG, "Camera capture cancelled or failed");
                    }
                }
        );
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        showImageSourceDialog();
                    } else {
                        Toast.makeText(this, "Permission required to access photos", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        Log.d(TAG, "Activity launchers initialized successfully");
    }
    private void processCapturedImage(Bitmap bitmap) {
        if (bitmap == null || isUploadingImage) {
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        isUploadingImage = true;
        showUploadProgress(true);

        // Convert bitmap to Base64 in background thread
        new Thread(() -> {
            try {
                String base64Image = convertBitmapToBase64(bitmap);
                if (base64Image != null) {
                    runOnUiThread(() -> {
                        updateUserProfilePhotoInFirestore(base64Image);
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileActivity.this, "Failed to process captured image", Toast.LENGTH_SHORT).show();
                        showUploadProgress(false);
                        isUploadingImage = false;
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing captured image", e);
                runOnUiThread(() -> {
                    Toast.makeText(ProfileActivity.this, "Failed to process captured image", Toast.LENGTH_SHORT).show();
                    showUploadProgress(false);
                    isUploadingImage = false;
                });
            }
        }).start();
    }

    private String convertBitmapToBase64(Bitmap bitmap) {
        try {
            // Resize to reduce size
            bitmap = resizeBitmap(bitmap, 400); // Max 400x400

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteArrayOutputStream); // 60% quality
            byte[] byteArray = byteArrayOutputStream.toByteArray();

            // Check size (max 300KB for safety)
            if (byteArray.length > 300000) {
                byteArrayOutputStream.reset();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 40, byteArrayOutputStream);
                byteArray = byteArrayOutputStream.toByteArray();
            }

            return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error converting bitmap to base64", e);
            return null;
        }
    }

    private void setupClickListeners() {
        imgBack.setOnClickListener(v -> finish());
        btnSaveProfile.setOnClickListener(v -> saveProfileChanges());
        btnLogout.setOnClickListener(v -> logoutUser());

        // Profile picture upload click listener
        tvUploadProfilePicture.setOnClickListener(v -> {
            Log.d(TAG, "tvUploadProfilePicture clicked!");
            checkPermissionAndSelectImage();
        });

        profileImage.setOnClickListener(v -> {
            Log.d(TAG, "profileImage clicked!");
            checkPermissionAndSelectImage();
        });
    }

    private void checkPermissionAndSelectImage() {
        Log.d(TAG, "=== checkPermissionAndSelectImage called ===");

        String permission;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        Log.d(TAG, "Checking permission: " + permission);

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission not granted, requesting: " + permission);
            permissionLauncher.launch(permission);
        } else {
            Log.d(TAG, "Permission already granted");
            showImageSourceDialog();
        }
    }

    private void showImageSourceDialog() {
        Log.d(TAG, "=== showImageSourceDialog called ===");

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Select Profile Picture")
                .setItems(new CharSequence[]{"Choose from Gallery", "Take Photo"},
                        (dialog, which) -> {
                            Log.d(TAG, "Dialog item selected: " + which);
                            switch (which) {
                                case 0:
                                    Log.d(TAG, "Gallery selected");
                                    selectImageFromGallery();
                                    break;
                                case 1:
                                    Log.d(TAG, "Camera selected");
                                    takePhotoWithCamera();
                                    break;
                            }
                        })
                .show();

        Log.d(TAG, "Dialog shown successfully");
    }

    private void selectImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void takePhotoWithCamera() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(intent);
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void processSelectedImage(Uri imageUri) {
        if (imageUri == null || isUploadingImage) {
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        isUploadingImage = true;
        showUploadProgress(true);

        // Convert to Base64 in background thread
        new Thread(() -> {
            try {
                String base64Image = convertImageToBase64(imageUri);
                if (base64Image != null) {
                    runOnUiThread(() -> {
                        updateUserProfilePhotoInFirestore(base64Image);
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileActivity.this, "Failed to process image", Toast.LENGTH_SHORT).show();
                        showUploadProgress(false);
                        isUploadingImage = false;
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
                runOnUiThread(() -> {
                    Toast.makeText(ProfileActivity.this, "Failed to process image", Toast.LENGTH_SHORT).show();
                    showUploadProgress(false);
                    isUploadingImage = false;
                });
            }
        }).start();
    }
    private String convertImageToBase64(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            // Resize to reduce size (Firestore limit 1MB per document)
            bitmap = resizeBitmap(bitmap, 400); // Max 400x400

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteArrayOutputStream); // 60% quality
            byte[] byteArray = byteArrayOutputStream.toByteArray();

            // Check size (max 300KB for safety)
            if (byteArray.length > 300000) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 40, byteArrayOutputStream);
                byteArray = byteArrayOutputStream.toByteArray();
            }

            return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error converting image to base64", e);
            return null;
        }
    }
    private Bitmap resizeBitmap(Bitmap bitmap, int maxSize) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= maxSize && height <= maxSize) {
            return bitmap;
        }

        float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    private void updateUserProfilePhotoInFirestore(String downloadUrl) {
        // Update using UserPreferencesManager
        UserPreferencesManager.updateProfileImageAsync(this, downloadUrl, new UserPreferencesManager.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean success) {
                currentProfileImageUrl = downloadUrl;

                // Also update Firebase Auth profile
                updateFirebaseAuthPhoto(downloadUrl);

                Log.d(TAG, "Profile image URL updated successfully");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to update profile image: " + error);
                Toast.makeText(ProfileActivity.this, "Failed to save profile picture", Toast.LENGTH_SHORT).show();
                showUploadProgress(false);
                isUploadingImage = false;
            }
        });
    }

    private void updateFirebaseAuthPhoto(String base64Image) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Just complete the upload process
            showUploadProgress(false);
            isUploadingImage = false;

            Log.d(TAG, "Profile image updated successfully (Base64 stored in Firestore)");
            Toast.makeText(ProfileActivity.this, "Profile picture updated successfully", Toast.LENGTH_SHORT).show();
        }
    }

    private void showUploadProgress(boolean show) {
        if (progressBarUpload != null) {
            progressBarUpload.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        if (tvUploadProfilePicture != null) {
            tvUploadProfilePicture.setEnabled(!show);
            tvUploadProfilePicture.setText(show ? "Uploading..." : "Upload Profile Picture");
        }
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            usernameEdit.setText(user.getDisplayName());
            emailEdit.setText(user.getEmail());

            UserPreferencesManager.getProfileImageUrlAsync(this, new UserPreferencesManager.DataCallback<String>() {
                @Override
                public void onSuccess(String imageUrl) {
                    if (!TextUtils.isEmpty(imageUrl)) {
                        currentProfileImageUrl = imageUrl;

                        if (imageUrl.startsWith("data:image")) {
                            // Base64 image
                            loadBase64Image(imageUrl);
                        } else {
                            // Regular URL (fallback)
                            Glide.with(ProfileActivity.this)
                                    .load(imageUrl)
                                    .placeholder(R.drawable.ic_launcher_foreground)
                                    .error(R.drawable.ic_launcher_foreground)
                                    .into(profileImage);
                        }
                    } else {
                        profileImage.setImageResource(R.drawable.ic_launcher_foreground);
                    }
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error loading profile image: " + error);
                    profileImage.setImageResource(R.drawable.ic_launcher_foreground);
                }
            });
        }
    }
    private void loadBase64Image(String base64String) {
        try {
            String base64Image = base64String.substring(base64String.indexOf(",") + 1);
            byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            profileImage.setImageBitmap(decodedByte);
        } catch (Exception e) {
            Log.e(TAG, "Error loading base64 image", e);
            profileImage.setImageResource(R.drawable.ic_launcher_foreground);
        }
    }

    private void saveProfileChanges() {
        if (isUploadingImage) {
            Toast.makeText(this, "Please wait for image upload to complete", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String newUsername = usernameEdit.getText().toString().trim();
        String newEmail = emailEdit.getText().toString().trim();
        String currentPassword = currentPasswordEdit.getText().toString();
        String newPassword = newPasswordEdit.getText().toString();

        if (TextUtils.isEmpty(newUsername)) {
            usernameEdit.setError("Username cannot be empty");
            return;
        }

        if (TextUtils.isEmpty(newEmail)) {
            emailEdit.setError("Email cannot be empty");
            return;
        }

        if (TextUtils.isEmpty(currentPassword)) {
            currentPasswordEdit.setError("Current password is required to make changes");
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updateUserProfile(user, newUsername, newEmail, newPassword);
                    } else {
                        Toast.makeText(ProfileActivity.this,
                                "Current password is incorrect", Toast.LENGTH_SHORT).show();
                        currentPasswordEdit.setError("Incorrect password");
                    }
                });
    }

    private void updateUserProfile(FirebaseUser user, String newUsername, String newEmail, String newPassword) {
        // Update Firebase Auth
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(newUsername)
                .build();

        user.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Username updated successfully in Firebase Auth");

                        // Update Firestore using UserPreferencesManager
                        updateUserProfileInFirestore(user.getUid(), newUsername, newEmail);
                    } else {
                        Log.e(TAG, "Error updating username in Firebase Auth", task.getException());
                    }
                });

        if (!user.getEmail().equals(newEmail)) {
            user.updateEmail(newEmail)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Email updated successfully");
                        } else {
                            Log.e(TAG, "Error updating email", task.getException());
                            Toast.makeText(ProfileActivity.this,
                                    "Error updating email: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        if (!TextUtils.isEmpty(newPassword)) {
            user.updatePassword(newPassword)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Password updated successfully");
                            newPasswordEdit.setText("");
                        } else {
                            Log.e(TAG, "Error updating password", task.getException());
                            Toast.makeText(ProfileActivity.this,
                                    "Error updating password: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }
        currentPasswordEdit.setText("");
        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
    }

    private void updateUserProfileInFirestore(String userId, String newUsername, String newEmail) {
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("displayName", newUsername);
        profileData.put("email", newEmail);
        profileData.put("profileImageUrl", currentProfileImageUrl);

        UserPreferencesManager.saveUserProfileAsync(this, profileData, new UserPreferencesManager.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean success) {
                Log.d(TAG, "User profile updated successfully in Firestore");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to update user profile in Firestore: " + error);
            }
        });
    }

    private void logoutUser() {
        try {
            mAuth.signOut();
            UserPreferencesManager.clearUserPreferences(this);
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error logging out: " + e.getMessage());
            Toast.makeText(this, "Error logging out", Toast.LENGTH_SHORT).show();
        }
    }
}