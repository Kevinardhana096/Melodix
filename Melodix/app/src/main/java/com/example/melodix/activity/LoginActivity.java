package com.example.melodix.activity;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private TextInputLayout emailInputLayout, passwordInputLayout;
    private TextInputEditText emailEditText, passwordEditText;
    private MaterialButton loginButton;
    private ProgressBar loginProgressBar;
    private FirebaseAuth mAuth;
    private View mainLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int themeMode = ThemeManager.getThemeMode(this);
        ThemeManager.setTheme(this, themeMode);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        mainLayout = findViewById(R.id.main);
        emailInputLayout = findViewById(R.id.emailInputLayout);
        passwordInputLayout = findViewById(R.id.passwordInputLayout);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        loginProgressBar = findViewById(R.id.loginProgressBar);
        TextView forgotPasswordText = findViewById(R.id.forgotPasswordText);
        TextView noAccountText = findViewById(R.id.noAccountText);

        emailInputLayout.setHint("Email");

        loginButton.setOnClickListener(v -> validateAndSignIn());

        forgotPasswordText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        noAccountText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            updateUI(currentUser);
        }
    }

    private void validateAndSignIn() {
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);

        String email = getTextFromEditText(emailEditText);
        String password = getTextFromEditText(passwordEditText);
        boolean isValid = true;

        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError("Email is required");
            emailEditText.requestFocus();
            isValid = false;
        } else if (!isValidEmail(email)) {
            emailInputLayout.setError("Please enter a valid email address");
            emailEditText.requestFocus();
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInputLayout.setError("Password is required");
            if (isValid) passwordEditText.requestFocus();
            isValid = false;
        } else if (password.length() < 6) {
            passwordInputLayout.setError("Password must be at least 6 characters");
            if (isValid) passwordEditText.requestFocus();
            isValid = false;
        }

        if (isValid) {
            showLoading();
            signInWithEmailPassword(email, password);
        }
    }
    private String getTextFromEditText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private boolean isValidEmail(CharSequence target) {
        return (!TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target).matches());
    }
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void signInWithEmailPassword(String email, String password) {
        if (!isNetworkAvailable()) {
            showError("No internet connection. Please check your network.");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    hideLoading();
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        updateUI(user);
                    } else {
                        handleFirebaseAuthErrors(task.getException());
                    }
                });
    }
    private void handleFirebaseAuthErrors(Exception exception) {
        if (exception instanceof FirebaseAuthInvalidUserException) {
            String errorCode = ((FirebaseAuthInvalidUserException) exception).getErrorCode();
            if ("ERROR_USER_NOT_FOUND".equals(errorCode)) {
                emailInputLayout.setError("No account found with this email");
            } else if ("ERROR_USER_DISABLED".equals(errorCode)) {
                showError("This account has been disabled");
            }
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            passwordInputLayout.setError("Invalid password");
        } else {
            showError("Authentication failed. Please try again.");
        }
    }
    private void showError(String message) {
        Snackbar.make(mainLayout, message, Snackbar.LENGTH_LONG).show();
    }

    private void showLoading() {
        loginProgressBar.setVisibility(View.VISIBLE);
        loginButton.setEnabled(false);
    }

    private void hideLoading() {
        loginProgressBar.setVisibility(View.GONE);
        loginButton.setEnabled(true);
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
