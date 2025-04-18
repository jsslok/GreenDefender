package my.edu.utar.greendefender;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class ProfileActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;

    private TextView tvUsername, tvEmail, tvLocation;
    private EditText etUsername, etPostcode;
    private Button editUsernameBtn, saveBtn, changePasswordBtn, logoutBtn, editImageBtn, confirmPostcodeBtn;
    private ImageView profileImageView;

    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private SharedPreferences preferences;

    private static final Pattern MALAYSIA_POSTCODE_PATTERN = Pattern.compile("^(\\d{5})$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        preferences = getSharedPreferences("ProfilePrefs", MODE_PRIVATE);

        // UI component references
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
        confirmPostcodeBtn = findViewById(R.id.confirm_postcode_btn);
        profileImageView = findViewById(R.id.profile_image_placeholder);

        // Load saved profile image
        String savedImageUri = preferences.getString("profile_image", null);
        if (savedImageUri != null) {
            Glide.with(this).load(Uri.parse(savedImageUri)).into(profileImageView);
        }

        if (currentUser != null) {
            tvUsername.setText(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "User");
            tvEmail.setText(currentUser.getEmail());
        }

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
                tvUsername.setVisibility(View.VISIBLE);
                etUsername.setVisibility(View.GONE);
                saveBtn.setVisibility(View.GONE);
            }
        });

        confirmPostcodeBtn.setOnClickListener(v -> {
            String postcode = etPostcode.getText().toString().trim();
            if (validateMalaysianPostcode(postcode)) {
                getLocationFromPostcode(postcode);
            } else {
                Toast.makeText(this, "Enter a valid Malaysian postcode (e.g. 43000)", Toast.LENGTH_SHORT).show();
            }
        });

        changePasswordBtn.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, ChangePasswordActivity.class));
        });

        logoutBtn.setOnClickListener(v -> signOut());

        editImageBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_IMAGE_PICK);
        });

        // Request location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    private void getLocationFromPostcode(String postcode) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addressList = geocoder.getFromLocationName(postcode + ", Malaysia", 1);
            if (addressList != null && !addressList.isEmpty()) {
                Address address = addressList.get(0);
                String fullLocation = address.getAddressLine(0);
                tvLocation.setText(fullLocation);
            } else {
                Toast.makeText(this, "Location not found for the given postcode", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error retrieving location", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean validateMalaysianPostcode(String postcode) {
        return MALAYSIA_POSTCODE_PATTERN.matcher(postcode).matches();
    }

    private void signOut() {
        mAuth.signOut();
        startActivity(new Intent(ProfileActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri selectedImageUri = data.getData();
            profileImageView.setImageURI(selectedImageUri);
            preferences.edit().putString("profile_image", selectedImageUri.toString()).apply();
        }
    }
}
