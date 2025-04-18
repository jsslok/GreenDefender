package my.edu.utar.greendefender;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class ProfileActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final Pattern MALAYSIA_POSTCODE_PATTERN = Pattern.compile("^\\d{5}$");

    private TextView tvUsername, tvEmail, tvLocation;
    private EditText etUsername, etPostcode;
    private Button backBtn, editUsernameBtn, saveBtn, changePasswordBtn, logoutBtn, editImageBtn, confirmPostcodeBtn;
    private ImageView profileImageView;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private SharedPreferences preferences;

    private GoogleSignInClient mGoogleSignInClient;

    private boolean isEditingPostcode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initializeFirebase();
        initializeViews();
        loadUserProfile();
        setupListeners();
        requestLocationPermission();
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        preferences = getSharedPreferences("ProfilePrefs", MODE_PRIVATE);
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
        // Profile image
        String imageUri = preferences.getString("profile_image", null);
        if (imageUri != null) {
            Glide.with(this).load(Uri.parse(imageUri)).into(profileImageView);
        }

        // Firebase user
        if (currentUser != null) {
            tvUsername.setText(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "User");
            tvEmail.setText(currentUser.getEmail());
        }

        // Postcode
        String savedPostcode = preferences.getString("postcode", "");
        etPostcode.setText(savedPostcode);
        if (!savedPostcode.isEmpty()) {
            getLocationFromPostcode(savedPostcode);
        }
    }

    private void setupListeners() {
        backBtn.setOnClickListener(v-> onBackPressed());
        editUsernameBtn.setOnClickListener(v -> enableUsernameEdit());
        saveBtn.setOnClickListener(v -> saveUsername());

        changePasswordBtn.setOnClickListener(v -> startActivity(new Intent(this, ChangePasswordActivity.class)));
        logoutBtn.setOnClickListener(v -> signOut());

        editImageBtn.setOnClickListener(v -> pickImageFromGallery());
        confirmPostcodeBtn.setOnClickListener(v -> togglePostcodeEditing());

    }

    private void enableUsernameEdit() {
        etUsername.setVisibility(View.VISIBLE);
        etUsername.setText(tvUsername.getText().toString());
        tvUsername.setVisibility(View.GONE);
        saveBtn.setVisibility(View.VISIBLE);
    }

    private void saveUsername() {
        String newUsername = etUsername.getText().toString().trim();
        if (!TextUtils.isEmpty(newUsername)) {
            tvUsername.setText(newUsername);
            etUsername.setVisibility(View.GONE);
            tvUsername.setVisibility(View.VISIBLE);
            saveBtn.setVisibility(View.GONE);
        }
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
                preferences.edit().putString("postcode", postcode).apply();
                getLocationFromPostcode(postcode);
                etPostcode.setEnabled(false);
                confirmPostcodeBtn.setText("Change Postcode");
                isEditingPostcode = false;
            } else {
                Toast.makeText(this, "Enter a valid Malaysian postcode (e.g. 43000)", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean validateMalaysianPostcode(String postcode) {
        return MALAYSIA_POSTCODE_PATTERN.matcher(postcode).matches();
    }

    private void getLocationFromPostcode(String postcode) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addressList = geocoder.getFromLocationName(postcode + ", Malaysia", 1);
            if (addressList != null && !addressList.isEmpty()) {
                tvLocation.setText(addressList.get(0).getAddressLine(0));
            } else {
                Toast.makeText(this, "Location not found for the given postcode", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error retrieving location", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
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
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // clear backstack
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri selectedImageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 300, 300, true);
                profileImageView.setImageBitmap(resizedBitmap);
                preferences.edit().putString("profile_image", selectedImageUri.toString()).apply();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
