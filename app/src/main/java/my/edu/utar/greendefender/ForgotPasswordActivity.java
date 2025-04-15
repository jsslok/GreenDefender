package my.edu.utar.greendefender;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail;
    private Button btnReset, btnResend;
    private TextView tvBackToLogin;
    private FirebaseAuth mAuth;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        etEmail = findViewById(R.id.forgot_email);
        btnReset = findViewById(R.id.btn_reset_password);
        btnResend = findViewById(R.id.btn_resend);
        tvBackToLogin = findViewById(R.id.back_to_login);

        // Reset password button
        btnReset.setOnClickListener(v -> {
            userEmail = etEmail.getText().toString().trim();
            if (validateEmail(userEmail)) {
                checkIfEmailExists(userEmail);
            }
        });

        // Resend email button
        btnResend.setOnClickListener(v -> {
            if (userEmail != null && validateEmail(userEmail)) {
                sendResetEmail(userEmail);
            } else {
                Toast.makeText(this, "Please enter a valid email first", Toast.LENGTH_SHORT).show();
            }
        });

        // Back to login
        tvBackToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
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

    private void checkIfEmailExists(String email) {
        mAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().getSignInMethods().size() > 0) {
                            sendResetEmail(email);
                        } else {
                            etEmail.setError("This email is not registered");
                        }
                    } else {
                        Toast.makeText(this, "Error checking email: " + task.getException(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendResetEmail(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Reset link sent to " + email,
                                Toast.LENGTH_LONG).show();
                        userEmail = email; // Store for possible resend
                    } else {
                        Toast.makeText(this,
                                "Failed to send reset email: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}