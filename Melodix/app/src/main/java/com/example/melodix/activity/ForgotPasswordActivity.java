package com.example.melodix.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.melodix.R;
import com.example.melodix.listener.ThemeManager;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Objects;

public class ForgotPasswordActivity extends AppCompatActivity {
    private TextInputLayout emailInputLayout;
    private TextInputEditText emailEditText;
    private Button resetPasswordButton;
    private TextView backToLoginText;
    private ProgressBar progressBar;
    private ConstraintLayout mainLayout;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int themeMode = ThemeManager.getThemeMode(this);
        ThemeManager.setTheme(this, themeMode);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        mainLayout = findViewById(R.id.mainLayout);
        emailInputLayout = findViewById(R.id.emailInputLayout);
        emailEditText = findViewById(R.id.emailEditText);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);
        backToLoginText = findViewById(R.id.backToLoginText);
        progressBar = findViewById(R.id.progressBar);
        ImageButton backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());

        resetPasswordButton.setOnClickListener(view -> validateAndResetPassword());

        backToLoginText.setOnClickListener(view -> {
            Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }
    private void validateAndResetPassword() {
        emailInputLayout.setError(null);

        String email = Objects.requireNonNull(emailEditText.getText()).toString().trim();
        boolean isValid = true;

        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError("Email is required");
            isValid = false;
        } else if (!isValidEmail(email)) {
            emailInputLayout.setError("Please enter a valid email address");
            isValid = false;
        }

        if (isValid) {
            showLoading();
            sendPasswordResetEmail(email);
        }
    }

    private boolean isValidEmail(CharSequence target) {
        return (!TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target).matches());
    }

    private void sendPasswordResetEmail(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    hideLoading();
                    if (task.isSuccessful()) {
                        showSuccessAndRedirect();
                    } else {
                        showError("Failed to send reset email. " +
                                (task.getException() != null ? task.getException().getLocalizedMessage() : ""));
                    }
                });
    }

    private void showSuccessAndRedirect() {
        Snackbar.make(mainLayout, "Password reset email sent successfully!", Snackbar.LENGTH_LONG).show();

        mainLayout.postDelayed(() -> {
            Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }, 2000);
    }

    private void showError(String message) {
        Snackbar.make(mainLayout, message, Snackbar.LENGTH_LONG).show();
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        resetPasswordButton.setEnabled(false);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
        resetPasswordButton.setEnabled(true);
    }
}
