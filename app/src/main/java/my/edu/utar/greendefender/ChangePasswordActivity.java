package my.edu.utar.greendefender;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    private EditText currentPassword, newPassword, confirmPassword;
    private Button btnChangePassword;
    private TextView backButton;
    private FirebaseAuth mAuth;
    private FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();

        // Initialize views
        currentPassword = findViewById(R.id.current_password);
        newPassword = findViewById(R.id.new_password);
        confirmPassword = findViewById(R.id.confirm_password);
        btnChangePassword = findViewById(R.id.btn_change_password);
        backButton = findViewById(R.id.back_button);

        btnChangePassword.setOnClickListener(v -> changePassword());

        backButton.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
        });
    }

    private void changePassword() {
        String currentPass = currentPassword.getText().toString().trim();
        String newPass = newPassword.getText().toString().trim();
        String confirmPass = confirmPassword.getText().toString().trim();

        // Validate inputs
        if (TextUtils.isEmpty(currentPass)) {
            currentPassword.setError("Current password is required");
            return;
        }

        if (TextUtils.isEmpty(newPass)) {
            newPassword.setError("New password is required");
            return;
        }

        if (newPass.length() < 6) {
            newPassword.setError("Password must be at least 6 characters");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            confirmPassword.setError("Passwords don't match");
            return;
        }

        // Reauthenticate user before changing password
        AuthCredential credential = EmailAuthProvider
                .getCredential(user.getEmail(), currentPass);

        user.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Update password
                        user.updatePassword(newPass)
                                .addOnCompleteListener(updateTask -> {
                                    if (updateTask.isSuccessful()) {
                                        Toast.makeText(ChangePasswordActivity.this,
                                                "Password changed successfully",
                                                Toast.LENGTH_SHORT).show();
                                        finish();
                                    } else {
                                        Toast.makeText(ChangePasswordActivity.this,
                                                "Error: " + updateTask.getException().getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        Toast.makeText(ChangePasswordActivity.this,
                                "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}