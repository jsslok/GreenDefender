package my.edu.utar.greendefender;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final String TAG = "ProfileActivity";

    // UI Components
    private TextView tvUsername, tvEmail, tvLocation;
    private EditText etUsername, etPostcode;
    private Button editUsernameBtn, saveBtn, changePasswordBtn, logoutBtn, editImageBtn, confirmPostcodeBtn;
    private ImageView backBtn, profileImageView;
    private ProgressBar progressBar;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private StorageReference storageRef;

    // Other variables
    private SharedPreferences preferences;
    private GoogleSignInClient mGoogleSignInClient;
    private boolean isEditingPostcode = false;
    private Uri selectedImageUri;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase components
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();

        // Initialize SharedPreferences
        preferences = getSharedPreferences("ProfilePrefs", MODE_PRIVATE);

        initializeViews();
        loadUserProfile();
        setupListeners();
        requestLocationPermission();
    }

    private void initializeViews() {
        backBtn = findViewById(R.id.back_btn);
        tvUsername = findViewById(R.id.tv_username);
        tvEmail = findViewById(R.id.tv_email);
        tvLocation = findViewById(R.id.tv_location);
        etUsername = findViewById(R.id.et_username);
        etPostcode = findViewById(R.id.et_postcode);


        editUsernameBtn = findViewById(R.id.edit_username_btn);
        saveBtn = findViewById(R.id.save_btn);
        changePasswordBtn = findViewById(R.id.change_password_btn);
        logoutBtn = findViewById(R.id.logout_btn);
        editImageBtn = findViewById(R.id.edit_image_btn);
        confirmPostcodeBtn = findViewById(R.id.toggle_postcode_btn);
        profileImageView = findViewById(R.id.profile_image_placeholder);

        etPostcode.setEnabled(false);
        confirmPostcodeBtn.setText("Change Postcode");
    }

    private void loadUserProfile() {

        // Load basic info from Firebase Auth
        if (currentUser != null) {
            tvEmail.setText(currentUser.getEmail());

            // Check SharedPreferences first for quick load
            String cachedUsername = preferences.getString("username", "");
            String cachedImage = preferences.getString("profile_image", "");

            if (!cachedUsername.isEmpty()) {
                tvUsername.setText(cachedUsername);
            }

            if (!cachedImage.isEmpty()) {
                Glide.with(this)
                        .load(cachedImage)
                        .placeholder(R.drawable.default_profile_picture)
                        .into(profileImageView);
            }

            // Load from Firestore
            db.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                updateUIFromDocument(document);
                            } else {
                                createNewUserDocument();
                            }
                        } else {
                            Log.w(TAG, "Error loading profile", task.getException());
                            Toast.makeText(this, "Failed to load profile data", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void updateUIFromDocument(DocumentSnapshot document) {
        String username = document.getString("username");
        String postcode = document.getString("postcode");
        String location = document.getString("location");
        String imageUrl = document.getString("profileImageUrl");

        // Update UI
        if (username != null && !username.isEmpty()) {
            tvUsername.setText(username);
            preferences.edit().putString("username", username).apply();
        } else {
            String displayName = currentUser.getDisplayName();
            tvUsername.setText(displayName != null ? displayName : "User");
        }

        if (postcode != null) {
            etPostcode.setText(postcode);
            if (location != null && !location.isEmpty()) {
                tvLocation.setText(location);
            } else if (!postcode.isEmpty()) {
                loadLocationFromPostcode(postcode);
            }
        }

        // Load profile image
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.default_profile_picture)
                    .into(profileImageView);
            preferences.edit().putString("profile_image", imageUrl).apply();
        }
    }

    private void createNewUserDocument() {
        if (currentUser == null) return;

        Map<String, Object> user = new HashMap<>();
        user.put("username", currentUser.getDisplayName() != null ?
                currentUser.getDisplayName() : "User");
        user.put("email", currentUser.getEmail());
        user.put("postcode", "");
        user.put("location", "");
        user.put("profileImageUrl", "");

        db.collection("users").document(currentUser.getUid())
                .set(user)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User document created"))
                .addOnFailureListener(e -> Log.w(TAG, "Error creating user document", e));
    }

    private void setupListeners() {
        backBtn.setOnClickListener(v -> finish());

        editUsernameBtn.setOnClickListener(v -> {
            etUsername.setVisibility(View.VISIBLE);
            etUsername.setText(tvUsername.getText().toString());
            tvUsername.setVisibility(View.GONE);
            saveBtn.setVisibility(View.VISIBLE);
        });

        saveBtn.setOnClickListener(v -> {
            String newUsername = etUsername.getText().toString().trim();
            if (!TextUtils.isEmpty(newUsername)) {
                tvUsername.setText(newUsername);
                etUsername.setVisibility(View.GONE);
                tvUsername.setVisibility(View.VISIBLE);
                saveBtn.setVisibility(View.GONE);
                saveProfileToFirestore();
            } else {
                Toast.makeText(this, "Username cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        changePasswordBtn.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));

        logoutBtn.setOnClickListener(v -> signOut());

        editImageBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_IMAGE_PICK);
        });

        confirmPostcodeBtn.setOnClickListener(v -> togglePostcodeEditing());
    }

    private void togglePostcodeEditing() {
        if (!isEditingPostcode) {
            etPostcode.setEnabled(true);
            etPostcode.requestFocus();
            confirmPostcodeBtn.setText("Confirm");
            isEditingPostcode = true;
        } else {
            String postcode = etPostcode.getText().toString().trim();
            if (validateMalaysianPostcode(postcode)) {
                etPostcode.setEnabled(false);
                confirmPostcodeBtn.setText("Change Postcode");
                isEditingPostcode = false;
                loadLocationFromPostcode(postcode);
                saveProfileToFirestore();
            } else {
                Toast.makeText(this, "Enter a valid Malaysian postcode (5 digits)", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean validateMalaysianPostcode(String postcode) {
        return postcode.matches("^\\d{5}$");
    }

    private void loadLocationFromPostcode(String postcode) {

        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(postcode + ", Malaysia", 1);

                mainHandler.post(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        String location = address.getAddressLine(0);
                        tvLocation.setText(location);
                        saveLocationToFirestore(location);
                    } else {
                        Toast.makeText(this, "Location not found for this postcode", Toast.LENGTH_SHORT).show();
                        tvLocation.setText("");
                    }
                });
            } catch (IOException e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Error getting location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Geocoder error", e);
                });
            }
        });
    }

    private void saveLocationToFirestore(String location) {
        if (currentUser == null) return;

        db.collection("users").document(currentUser.getUid())
                .update("location", location)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Location updated successfully");
                    preferences.edit().putString("location", location).apply();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error updating location", e);
                });
    }

    private void saveProfileToFirestore() {
        if (currentUser == null) return;

        // First upload image if selected
        if (selectedImageUri != null) {
            uploadImageAndSaveProfile();
        } else {
            saveProfileDataToFirestore(null);
        }
    }

    private void uploadImageAndSaveProfile() {
        String filename = "profile_" + currentUser.getUid() + "_" + System.currentTimeMillis();
        StorageReference imageRef = storageRef.child("profile_images/" + filename);

        imageRef.putFile(selectedImageUri)
                .addOnProgressListener(taskSnapshot -> {
                    double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                    Log.d(TAG, "Upload is " + progress + "% done");
                })
                .addOnSuccessListener(taskSnapshot -> {
                    // Get download URL
                    imageRef.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                String imageUrl = uri.toString();
                                saveProfileDataToFirestore(imageUrl);
                            })
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Error getting download URL", e);
                                Toast.makeText(this, "Failed to get image URL", Toast.LENGTH_SHORT).show();
                                saveProfileDataToFirestore(null);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error uploading image", e);
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                    saveProfileDataToFirestore(null);
                });
    }

    private void saveProfileDataToFirestore(String imageUrl) {
        DocumentReference userRef = db.collection("users").document(currentUser.getUid());

        Map<String, Object> updates = new HashMap<>();
        updates.put("username", tvUsername.getText().toString());
        updates.put("postcode", etPostcode.getText().toString());

        if (imageUrl != null) {
            updates.put("profileImageUrl", imageUrl);
            preferences.edit().putString("profile_image", imageUrl).apply();
        }

        userRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();

                    // Update local preferences
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("username", tvUsername.getText().toString());
                    editor.putString("postcode", etPostcode.getText().toString());
                    editor.apply();

                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error updating profile", e);
                    Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                Glide.with(this)
                        .load(selectedImageUri)
                        .placeholder(R.drawable.default_profile_picture)
                        .circleCrop()
                        .into(profileImageView);

                // Save to Firestore will happen when user clicks save
            }
        }
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    private void signOut() {
        FirebaseAuth.getInstance().signOut();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }


}