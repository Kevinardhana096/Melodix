package com.example.melodix.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.melodix.R;
import com.example.melodix.listener.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {
    private static final String TAG = "SignUpActivity";
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String USERS_COLLECTION = "users";
    private TextInputLayout emailInputLayout, passwordInputLayout, confirmPasswordInputLayout;
    private TextInputEditText emailEditText, passwordEditText, confirmPasswordEditText;
    private MaterialButton signUpButton;
    private ProgressBar signupProgressBar;
    private TextView haveAccountText;
    private View mainLayout;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: Starting SignUpActivity");
        int themeMode = ThemeManager.getThemeMode(this);
        ThemeManager.setTheme(this, themeMode);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        initializeFirebase();
        initializeViews();
        setupClickListeners();
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        Log.d(TAG, "Firebase components initialized");
    }

    private void initializeViews() {
        mainLayout = findViewById(R.id.main);
        emailInputLayout = findViewById(R.id.emailInputLayout);
        passwordInputLayout = findViewById(R.id.passwordInputLayout);
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);

        signUpButton = findViewById(R.id.signupButton);
        signupProgressBar = findViewById(R.id.signupProgressBar);
        haveAccountText = findViewById(R.id.haveAccountText);

        Log.d(TAG, "Views initialized");
    }

    private void setupClickListeners() {
        signUpButton.setOnClickListener(v -> {
            Log.d(TAG, "Sign up button clicked");
            validateAndRegisterUser();
        });

        haveAccountText.setOnClickListener(v -> {
            Log.d(TAG, "Navigate to login clicked");
            navigateToLogin();
        });
    }

    private void validateAndRegisterUser() {
        Log.d(TAG, "Starting user validation");

        clearErrors();

        String email = getTextFromEditText(emailEditText);
        String password = getTextFromEditText(passwordEditText);
        String confirmPassword = getTextFromEditText(confirmPasswordEditText);

        if (validateInputs(email, password, confirmPassword)) {
            Log.d(TAG, "Validation passed, starting registration");
            setLoadingState(true);
            registerUser(email, password);
        } else {
            Log.d(TAG, "Validation failed");
        }
    }

    private String getTextFromEditText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void clearErrors() {
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);
        confirmPasswordInputLayout.setError(null);
    }

    private boolean validateInputs(String email, String password, String confirmPassword) {
        boolean isValid = true;

        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError("Email is required");
            isValid = false;
        } else if (!isValidEmail(email)) {
            emailInputLayout.setError("Please enter a valid email address");
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInputLayout.setError("Password is required");
            isValid = false;
        } else if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordInputLayout.setError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            isValid = false;
        } else if (!isStrongPassword(password)) {
            passwordInputLayout.setError("Password must contain uppercase, lowercase, and number");
            isValid = false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordInputLayout.setError("Please confirm your password");
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            confirmPasswordInputLayout.setError("Passwords do not match");
            isValid = false;
        }
        return isValid;
    }

    private boolean isValidEmail(CharSequence target) {
        return !TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    private boolean isStrongPassword(String password) {
        return password.matches(".*[A-Z].*") &&
                password.matches(".*[a-z].*") &&
                password.matches(".*\\d.*");
    }

    private void registerUser(String email, String password) {
        Log.d(TAG, "Starting Firebase authentication");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase authentication successful");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserToFirestore(user.getUid(), email);
                        } else {
                            Log.e(TAG, "User is null after successful registration");
                            setLoadingState(false);
                            showError("Registration failed: User data is null");
                        }
                    } else {
                        Log.e(TAG, "Firebase authentication failed", task.getException());
                        setLoadingState(false);
                        handleRegistrationError(task.getException());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Registration failed with exception", e);
                    setLoadingState(false);
                    handleRegistrationError(e);
                });
    }

    private void handleRegistrationError(Exception exception) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            emailInputLayout.setError("This email is already registered");
        } else if (exception instanceof FirebaseAuthWeakPasswordException) {
            passwordInputLayout.setError("Password is too weak");
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            emailInputLayout.setError("Invalid email format");
        } else {
            String errorMessage = exception != null ? exception.getLocalizedMessage() : "Unknown error";
            showError("Registration failed: " + errorMessage);
        }
    }

    private void saveUserToFirestore(String userId, String email) {
        Log.d(TAG, "Saving user data to Firestore");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("email", email);
        userMap.put("userId", userId);
        userMap.put("username", email.substring(0, email.indexOf('@')));
        userMap.put("displayName", "");
        userMap.put("profileImageUrl", "");
        userMap.put("createdAt", System.currentTimeMillis());
        userMap.put("lastUpdated", System.currentTimeMillis());
        userMap.put("accountType", "free");
        userMap.put("preferences", new HashMap<String, Object>() {{
            put("theme", ThemeManager.getThemeMode(SignUpActivity.this));
            put("notifications", true);
        }});

        Map<String, Object> initialDownloadsDoc = new HashMap<>();
        initialDownloadsDoc.put("initialized", true);
        initialDownloadsDoc.put("timestamp", System.currentTimeMillis());

        Map<String, Object> initialPlaylistsDoc = new HashMap<>();
        initialPlaylistsDoc.put("initialized", true);
        initialPlaylistsDoc.put("timestamp", System.currentTimeMillis());

        db.runBatch(batch -> {
            batch.set(db.collection(USERS_COLLECTION).document(userId), userMap);

            batch.set(
                    db.collection(USERS_COLLECTION).document(userId)
                            .collection("downloads").document("info"),
                    initialDownloadsDoc
            );

            batch.set(
                    db.collection(USERS_COLLECTION).document(userId)
                            .collection("playlists").document("info"),
                    initialPlaylistsDoc
            );
        }).addOnSuccessListener(aVoid -> {
            Log.d(TAG, "User data and collections initialized successfully");
            completeRegistration();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to initialize user data structure", e);
            setLoadingState(false);

            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                user.delete().addOnCompleteListener(deleteTask -> {
                    Log.d(TAG, "Cleaned up Firebase Auth user after Firestore failure");
                });
            }
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
            showError("Failed to save user data: " + errorMessage);
        });
    }

    private void completeRegistration() {
        Log.d(TAG, "Registration completed successfully");

        setLoadingState(false);
        showSuccess("Account created successfully!");
        mAuth.signOut();
        mainLayout.postDelayed(() -> navigateToLogin(), 1500);
    }

    private void navigateToLogin() {
        Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        if (mainLayout != null) {
            Snackbar.make(mainLayout, message, Snackbar.LENGTH_LONG)
                    .setAction("Dismiss", v -> {})
                    .show();
        }
        Log.e(TAG, "Error shown to user: " + message);
    }

    private void showSuccess(String message) {
        if (mainLayout != null) {
            Snackbar.make(mainLayout, message, Snackbar.LENGTH_SHORT)
                    .setBackgroundTint(getColor(android.R.color.holo_green_dark))
                    .show();
        }
        Log.i(TAG, "Success message shown: " + message);
    }

    private void setLoadingState(boolean isLoading) {
        Log.d(TAG, "Setting loading state: " + isLoading);

        if (signupProgressBar != null) {
            signupProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }

        if (signUpButton != null) {
            signUpButton.setEnabled(!isLoading);
            signUpButton.setText(isLoading ? "Creating Account..." : "Sign Up");
        }

        if (emailEditText != null) emailEditText.setEnabled(!isLoading);
        if (passwordEditText != null) passwordEditText.setEnabled(!isLoading);
        if (confirmPasswordEditText != null) confirmPasswordEditText.setEnabled(!isLoading);
        if (haveAccountText != null) haveAccountText.setEnabled(!isLoading);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called");
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (signupProgressBar != null && signupProgressBar.getVisibility() == View.VISIBLE) {
            setLoadingState(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (signupProgressBar != null && signupProgressBar.getVisibility() == View.VISIBLE) {
            showError("Please wait for registration to complete");
            return;
        }
        super.onBackPressed();
    }
}