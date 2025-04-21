package my.edu.utar.greendefender;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPasswordActivity";
    private EditText etEmail;
    private Button btnAction;
    private TextView tvBackToLogin;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.forgot_email);
        btnAction = findViewById(R.id.btn_action);
        tvBackToLogin = findViewById(R.id.back_to_login);

        btnAction.setOnClickListener(v -> attemptPasswordReset());

        tvBackToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void attemptPasswordReset() {
        String email = etEmail.getText().toString().trim();

        if (!validateEmail(email)) {
            return;
        }

        // Try sending reset email directly first
        sendResetEmail(email);
    }

    private boolean validateEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email");
            return false;
        }
        return true;
    }

    private void sendResetEmail(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Reset email sent successfully to: " + email);
                        btnAction.setText("Resend Link");
                        Toast.makeText(this,
                                "Password reset link sent to " + email,
                                Toast.LENGTH_LONG).show();
                    } else {
                        handleResetError(task.getException(), email);
                    }
                });
    }

    private void handleResetError(Exception exception, String email) {
        String errorMsg = "Failed to send reset email";

        if (exception != null) {
            errorMsg = exception.getMessage();
            Log.e(TAG, "Password reset error: " + errorMsg);

            // Check for specific Firebase error codes
            if (errorMsg.contains("no user record")) {
                etEmail.setError("This email is not registered");
                return;
            } else if (errorMsg.contains("invalid email")) {
                etEmail.setError("Invalid email format");
                return;
            }
        }

        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
    }
}