//package my.edu.utar.greendefender;
//
//import android.os.Bundle;
//import android.text.TextUtils;
//import android.view.View;
//import android.widget.EditText;
//import android.widget.TextView;
//import android.widget.Toast;
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseUser;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//
//public class ProfileActivity extends AppCompatActivity {
//
//    private EditText etUsername, etLocation;
//    private TextView tvUsername, tvEmail, tvLocation;
//    private boolean isEditing = false;
///
//    private FirebaseAuth mAuth;
//    private DatabaseReference mDatabase;
//    private String userId;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_profile);
//
//        // Initialize Firebase
//        mAuth = FirebaseAuth.getInstance();
//        mDatabase = FirebaseDatabase.getInstance().getReference();
//        FirebaseUser currentUser = mAuth.getCurrentUser();
//
//        if (currentUser == null) {
//            finish();
//            return;
//        }
//        userId = currentUser.getUid();
//
//        // Initialize views
//        etUsername = findViewById(R.id.et_username);
//        etLocation = findViewById(R.id.et_location);
//        tvUsername = findViewById(R.id.tv_username);
//        tvEmail = findViewById(R.id.tv_email);
//        tvLocation = findViewById(R.id.tv_location);
//
//        // Set default values
//        tvEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "user@example.com");
//        tvUsername.setText("PlantLover");
//        tvLocation.setText("Kuala Lumpur");
//
//        // Load user data from Firebase
//        loadUserData();
//
//        // Set edit/save functionality
//        setupEditButtons();
//    }
//
//    private void loadUserData() {
//        mDatabase.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (snapshot.exists()) {
//                    User user = snapshot.getValue(User.class);
//                    if (user != null) {
//                        if (!TextUtils.isEmpty(user.username)) {
//                            tvUsername.setText(user.username);
//                        }
//                        if (!TextUtils.isEmpty(user.location)) {
//                            tvLocation.setText(user.location);
//                        }
//                    }
//                } else {
//                    // No data exists, save default values
//                    saveUserData();
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Toast.makeText(ProfileActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
//            }
//        });
//    }
//
//    private void setupEditButtons() {
//        findViewById(R.id.edit_username_btn).setOnClickListener(v -> toggleEditMode(etUsername, tvUsername));
//        findViewById(R.id.edit_location_btn).setOnClickListener(v -> toggleEditMode(etLocation, tvLocation));
//        findViewById(R.id.save_btn).setOnClickListener(v -> saveAndExitEditMode());
//        findViewById(R.id.back_btn).setOnClickListener(v -> finish());
//    }
//
//    private void toggleEditMode(EditText et, TextView tv) {
//        if (!isEditing) {
//            isEditing = true;
//            findViewById(R.id.save_btn).setVisibility(View.VISIBLE);
//            et.setText(tv.getText().toString());
//            et.setVisibility(View.VISIBLE);
//            tv.setVisibility(View.GONE);
//        }
//    }
//
//    private void saveAndExitEditMode() {
//        // Validate inputs
//        if (etUsername.getVisibility() == View.VISIBLE &&
//                TextUtils.isEmpty(etUsername.getText().toString())) {
//            etUsername.setError("Username cannot be empty");
//            return;
//        }
//
//        if (etLocation.getVisibility() == View.VISIBLE &&
//                TextUtils.isEmpty(etLocation.getText().toString())) {
//            etLocation.setError("Location cannot be empty");
//            return;
//        }
//
//        // Update TextViews with new values
//        if (etUsername.getVisibility() == View.VISIBLE) {
//            tvUsername.setText(etUsername.getText().toString());
//        }
//        if (etLocation.getVisibility() == View.VISIBLE) {
//            tvLocation.setText(etLocation.getText().toString());
//        }
//
//        // Save to Firebase
//        saveUserData();
//
//        // Exit edit mode
//        isEditing = false;
//        findViewById(R.id.save_btn).setVisibility(View.GONE);
//        etUsername.setVisibility(View.GONE);
//        etLocation.setVisibility(View.GONE);
//        tvUsername.setVisibility(View.VISIBLE);
//        tvLocation.setVisibility(View.VISIBLE);
//    }
//
//    private void saveUserData() {
//        User user = new User(
//                tvUsername.getText().toString(),
//                tvEmail.getText().toString(),
//                tvLocation.getText().toString()
//        );
//
//        mDatabase.child("users").child(userId).setValue(user)
//                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show())
//                .addOnFailureListener(e -> Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show());
//    }
//
//    public static class User {
//        public String username, email, location;
//
//        public User() {
//            // Default constructor required for Firebase
//        }
//
//        public User(String username, String email, String location) {
//            this.username = username;
//            this.email = email;
//            this.location = location;
//        }
//    }
//}