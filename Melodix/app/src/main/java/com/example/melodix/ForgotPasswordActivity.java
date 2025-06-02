package com.example.melodix;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class ForgotPasswordActivity extends AppCompatActivity {
    private TextInputEditText emailEditText;
    private Button resetPasswordButton;
    private TextView backToLoginText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize views
        emailEditText = findViewById(R.id.emailEditText);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);
        backToLoginText = findViewById(R.id.backToLoginText);

        // Set click listener for reset password button
        resetPasswordButton.setOnClickListener(view -> {
            String email = emailEditText.getText().toString();
            if (!email.isEmpty()) {
                // Here you would typically implement your password reset logic
                // For now, we'll just navigate to the Set New Password screen
                Intent intent = new Intent(ForgotPasswordActivity.this, SetNewPasswordActivity.class);
                startActivity(intent);
            }
        });

        // Set click listener for back to login
        backToLoginText.setOnClickListener(view -> finish()); // This will return to the previous activity (Login)
    }
}
