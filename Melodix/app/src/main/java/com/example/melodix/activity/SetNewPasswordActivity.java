package com.example.melodix.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.melodix.R;
import com.example.melodix.listener.ThemeManager;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Objects;

public class SetNewPasswordActivity extends AppCompatActivity {
    private TextInputLayout newPasswordInputLayout, confirmPasswordInputLayout;
    private TextInputEditText newPasswordEditText, confirmPasswordEditText;
    private Button savePasswordButton;
    private ProgressBar progressBar;
    private ConstraintLayout mainLayout;
    private FirebaseAuth mAuth;
    private String resetCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int themeMode = ThemeManager.getThemeMode(this);
        ThemeManager.setTheme(this, themeMode);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_new_password);
        mAuth = FirebaseAuth.getInstance();
        if (getIntent() != null && getIntent().getData() != null) {
            resetCode = getIntent().getData().getQueryParameter("oobCode");
        }

        mainLayout = findViewById(R.id.mainLayout);
        newPasswordInputLayout = findViewById(R.id.newPasswordInputLayout);
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout);
        newPasswordEditText = findViewById(R.id.newPasswordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        savePasswordButton = findViewById(R.id.savePasswordButton);
        progressBar = findViewById(R.id.progressBar);
        ImageButton backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());
        savePasswordButton.setOnClickListener(view -> validateAndSetNewPassword());
    }

    private void validateAndSetNewPassword() {
        newPasswordInputLayout.setError(null);
        confirmPasswordInputLayout.setError(null);

        String newPassword = Objects.requireNonNull(newPasswordEditText.getText()).toString().trim();
        String confirmPassword = Objects.requireNonNull(confirmPasswordEditText.getText()).toString().trim();
        boolean isValid = true;

        if (TextUtils.isEmpty(newPassword)) {
            newPasswordInputLayout.setError("Password is required");
            isValid = false;
        } else if (newPassword.length() < 6) {
            newPasswordInputLayout.setError("Password must be at least 6 characters");
            isValid = false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordInputLayout.setError("Please confirm your password");
            isValid = false;
        } else if (!newPassword.equals(confirmPassword)) {
            confirmPasswordInputLayout.setError("Passwords do not match");
            isValid = false;
        }

        if (isValid) {
            showLoading();

            if (resetCode != null && !resetCode.isEmpty()) {
                confirmPasswordReset(resetCode, newPassword);
            }

            else if (mAuth.getCurrentUser() != null) {
                updatePassword(newPassword);
            }

            else {
                showError("No active session found. Please try the reset link from your email again.");
                hideLoading();
            }
        }
    }

    private void confirmPasswordReset(String code, String newPassword) {
        mAuth.confirmPasswordReset(code, newPassword)
                .addOnCompleteListener(task -> {
                    hideLoading();
                    if (task.isSuccessful()) {
                        showSuccessAndRedirect();
                    } else {
                        showError("Failed to reset password: " +
                                  (task.getException() != null ? task.getException().getLocalizedMessage() : "Unknown error"));
                    }
                });
    }

    private void updatePassword(String newPassword) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.updatePassword(newPassword)
                    .addOnCompleteListener(task -> {
                        hideLoading();
                        if (task.isSuccessful()) {
                            showSuccessAndRedirect();
                        } else {
                            showError("Failed to update password: " +
                                      (task.getException() != null ? task.getException().getLocalizedMessage() : "Unknown error"));
                        }
                    });
        } else {
            hideLoading();
            showError("No user is currently signed in");
        }
    }

    private void showSuccessAndRedirect() {
        Snackbar.make(mainLayout, "Password updated successfully!", Snackbar.LENGTH_SHORT).show();

        mainLayout.postDelayed(() -> {
            Intent intent = new Intent(SetNewPasswordActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }, 2000);
    }

    private void showError(String message) {
        Snackbar.make(mainLayout, message, Snackbar.LENGTH_LONG).show();
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        savePasswordButton.setEnabled(false);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
        savePasswordButton.setEnabled(true);
    }
}
