package my.edu.utar.greendefender;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private ImageView imageView;
    private Button cameraBtn, galleryBtn;
    private TextView resultTextView;

    private ImageButton profile;

    // Permissions
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int GALLERY_REQUEST_CODE = 101;
    private static final int CAMERA_PERMISSION_CODE = 102;
    private static final int STORAGE_PERMISSION_CODE = 103;

    // Model Configuration
    private static final int IMAGE_SIZE = 96;
    private static final String MODEL_FILE = "roseDetectionFYP1.tflite";
    private static final String[] CLASSES = {
            "Rose Slug", "Rose Mosaic", "Powdery Mildew", "Downy Mildew", "Black Spot"
    };
    private static final float CONFIDENCE_THRESHOLD = 0.7f;

    private Interpreter tfliteInterpreter;
    private Bitmap currentImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupClickListeners();
        loadModel();
    }

    private void initializeViews() {
        imageView = findViewById(R.id.imageView);
        cameraBtn = findViewById(R.id.button);
        galleryBtn = findViewById(R.id.button2);
        resultTextView = findViewById(R.id.result);
        profile = findViewById(R.id.profileButton);

        imageView.setImageResource(R.drawable.leaf);
        resultTextView.setText("Upload an image of a rose leaf with disease");
    }

    private void setupClickListeners() {
        cameraBtn.setOnClickListener(v -> checkCameraPermission());
        galleryBtn.setOnClickListener(v -> checkStoragePermission());
        profile.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
        });
    }


    private void loadModel() {
        try {
            tfliteInterpreter = new Interpreter(loadModelFile());
        } catch (IOException e) {
            Toast.makeText(this, "Model loading failed", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // ====================Permission Handling ====================
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            showCameraPermissionDialog();
        }
    }

    private void showCameraPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Camera Permission Required")
                .setMessage("This app needs camera access to take pictures of rose leaves for disease detection")
                .setPositiveButton("Grant Permission", (dialog, which) ->
                        ActivityCompat.requestPermissions(
                                MainActivity.this,
                                new String[]{Manifest.permission.CAMERA},
                                CAMERA_PERMISSION_CODE
                        )
                )
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    private void checkStoragePermission() {
        String permission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            openGallery();
        } else {
            showStoragePermissionDialog(permission);
        }
    }

    private void showStoragePermissionDialog(String permission) {
        new AlertDialog.Builder(this)
                .setTitle("Storage Permission Required")
                .setMessage("This app needs access to your photos to select images of rose leaves for disease detection")
                .setPositiveButton("Grant Permission", (dialog, which) ->
                        ActivityCompat.requestPermissions(
                                MainActivity.this,
                                new String[]{permission},
                                STORAGE_PERMISSION_CODE
                        )
                )
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == CAMERA_PERMISSION_CODE) {
                openCamera();
            } else if (requestCode == STORAGE_PERMISSION_CODE) {
                openGallery();
            }
        } else {
            handlePermissionDenial(requestCode);
        }
    }

    private void handlePermissionDenial(int requestCode) {
        if (requestCode == CAMERA_PERMISSION_CODE) {
            showPermissionDeniedDialog(
                    "Camera Permission Denied",
                    "You cannot take pictures without granting camera permission. " +
                            "Please grant permission when asked again.",
                    Manifest.permission.CAMERA,
                    CAMERA_PERMISSION_CODE
            );
        } else {
            String permission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                    ? Manifest.permission.READ_MEDIA_IMAGES
                    : Manifest.permission.READ_EXTERNAL_STORAGE;

            showPermissionDeniedDialog(
                    "Storage Permission Denied",
                    "You cannot select images without granting storage permission. " +
                            "Please grant permission when asked again.",
                    permission,
                    STORAGE_PERMISSION_CODE
            );
        }
    }

    private void showPermissionDeniedDialog(String title, String message,
                                            final String permission, final int requestCode) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Try Again", (dialog, which) -> {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        if (requestCode == CAMERA_PERMISSION_CODE) {
                            showCameraPermissionDialog();
                        } else {
                            String storagePermission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                                    ? Manifest.permission.READ_MEDIA_IMAGES
                                    : Manifest.permission.READ_EXTERNAL_STORAGE;
                            showStoragePermissionDialog(storagePermission);
                        }
                    } else {
                        showGoToSettingsDialog();
                    }
                })
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    private void showGoToSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("You have permanently denied permission. " +
                        "Please enable it in app settings to continue.")
                .setPositiveButton("Open Settings", (dialog, which) -> openAppSettings())
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    // ==================== Camera/Gallery Handling ====================
    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            try {
                currentImage = getImageFromResult(requestCode, data);
                if (currentImage != null) {
                    processImage(currentImage);
                }
            } catch (Exception e) {
                handleImageError();
            }
        }
    }

    private Bitmap getImageFromResult(int requestCode, Intent data) throws Exception {
        if (requestCode == CAMERA_REQUEST_CODE && data != null) {
            Bundle extras = data.getExtras();
            Bitmap image = (Bitmap) extras.get("data");
            return ThumbnailUtils.extractThumbnail(
                    image,
                    Math.min(image.getWidth(), image.getHeight()),
                    Math.min(image.getWidth(), image.getHeight()));
        } else if (requestCode == GALLERY_REQUEST_CODE && data != null) {
            Uri imageUri = data.getData();
            return MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
        }
        return null;
    }

    // ==================== Image Processing ====================
    private void processImage(Bitmap image) {
        imageView.setImageBitmap(image);

        if (!isLikelyLeaf(image)) {
            resultTextView.setText("Please upload a clear rose leaf image");
            Toast.makeText(this,
                    "This doesn't appear to be a rose leaf",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Bitmap processedImage = preprocessImage(image);
        classifyImage(processedImage);
    }

    private boolean isLikelyLeaf(Bitmap image) {
        Bitmap grayscale = toGrayscale(image);
        float greenPercentage = calculateGreenPercentage(grayscale);
        return greenPercentage > 0.3f;
    }

    private Bitmap toGrayscale(Bitmap original) {
        Bitmap grayscale = Bitmap.createBitmap(
                original.getWidth(),
                original.getHeight(),
                Bitmap.Config.ARGB_8888);

        for (int x = 0; x < original.getWidth(); x++) {
            for (int y = 0; y < original.getHeight(); y++) {
                int pixel = original.getPixel(x, y);
                int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                grayscale.setPixel(x, y, Color.rgb(gray, gray, gray));
            }
        }
        return grayscale;
    }

    private float calculateGreenPercentage(Bitmap grayscale) {
        int greenPixels = 0;
        int totalPixels = grayscale.getWidth() * grayscale.getHeight();

        for (int x = 0; x < grayscale.getWidth(); x++) {
            for (int y = 0; y < grayscale.getHeight(); y++) {
                int pixel = grayscale.getPixel(x, y);
                int green = Color.green(pixel);
                if (green > 50 && green < 200) {
                    greenPixels++;
                }
            }
        }
        return (float) greenPixels / totalPixels;
    }

    private Bitmap preprocessImage(Bitmap image) {
        return Bitmap.createScaledBitmap(image, IMAGE_SIZE, IMAGE_SIZE, false);
    }

    // ==================== Classification ====================
    private void classifyImage(Bitmap image) {
        try {
            TensorBuffer inputBuffer = prepareInputBuffer(image);
            TensorBuffer outputBuffer = prepareOutputBuffer();

            tfliteInterpreter.run(inputBuffer.getBuffer(), outputBuffer.getBuffer());

            float[] confidences = outputBuffer.getFloatArray();
            displayResults(confidences);

        } catch (Exception e) {
            handleClassificationError();
        }
    }

    private TensorBuffer prepareInputBuffer(Bitmap image) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

        for (int pixel : pixels) {
            byteBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);
            byteBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);
            byteBuffer.putFloat((pixel & 0xFF) / 255.0f);
        }

        TensorBuffer inputBuffer = TensorBuffer.createFixedSize(
                new int[]{1, IMAGE_SIZE, IMAGE_SIZE, 3}, DataType.FLOAT32);
        inputBuffer.loadBuffer(byteBuffer);
        return inputBuffer;
    }

    private TensorBuffer prepareOutputBuffer() {
        return TensorBuffer.createFixedSize(
                new int[]{1, CLASSES.length}, DataType.FLOAT32);
    }

    private void displayResults(float[] confidences) {
        int maxPos = 0;
        float maxConfidence = confidences[0];

        for (int i = 1; i < confidences.length; i++) {
            if (confidences[i] > maxConfidence) {
                maxConfidence = confidences[i];
                maxPos = i;
            }
        }

        if (maxConfidence < CONFIDENCE_THRESHOLD) {
            resultTextView.setText("Not a recognized rose leaf");
            Toast.makeText(this,
                    "The image doesn't match known rose leaf conditions",
                    Toast.LENGTH_LONG).show();
        } else {
            String result = String.format("%s",
                    CLASSES[maxPos]);
            resultTextView.setText(result);
        }
    }

    // ==================== Error Handling ====================
    private void handleImageError() {
        resultTextView.setText("Error processing image");
        Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
    }

    private void handleClassificationError() {
        resultTextView.setText("Classification failed");
        Toast.makeText(this, "Error analyzing image", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
        }
    }
}