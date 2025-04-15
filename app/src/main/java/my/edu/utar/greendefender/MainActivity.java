package my.edu.utar.greendefender;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

    // UI Components matching XML
    private ImageView imageView;
    private Button cameraBtn, galleryBtn;
    private TextView resultTextView;

    // Constants
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int GALLERY_REQUEST_CODE = 101;
    private static final int CAMERA_PERMISSION_CODE = 102;
    private static final int STORAGE_PERMISSION_CODE = 103;
    private static final int IMAGE_SIZE = 224; // Standard size for many ML models
    private static final String MODEL_FILE = "rosesDetectionFYP1.tflite";
    private static final String[] CLASSES = {
            "Rose Slug", "Rose Mosaic", "Powdery Mildew", "Downy Mildew", "Black Spot"
    };

    // TensorFlow Lite
    private Interpreter tfliteInterpreter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        imageView = findViewById(R.id.imageView);
        cameraBtn = findViewById(R.id.button);
        galleryBtn = findViewById(R.id.button2);
        resultTextView = findViewById(R.id.result);

        // Set default state
        imageView.setImageResource(R.drawable.leaf);
        resultTextView.setText("Select an image to diagnose");

        // Set click listeners
        cameraBtn.setOnClickListener(v -> checkCameraPermission());
        galleryBtn.setOnClickListener(v -> checkStoragePermission());

        // Load model
        try {
            tfliteInterpreter = new Interpreter(loadModelFile());
        } catch (IOException e) {
            Log.e("TFLite", "Error loading model", e);
            resultTextView.setText("Model loading failed");
            Toast.makeText(this, "Model failed to load", Toast.LENGTH_LONG).show();
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

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE
            );
        }
    }

    private void checkStoragePermission() {
        String permission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            openGallery();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{permission},
                    STORAGE_PERMISSION_CODE
            );
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            try {
                Bitmap image = getImageFromResult(requestCode, data);
                if (image != null) {
                    imageView.setImageBitmap(image);
                    Bitmap scaledImage = Bitmap.createScaledBitmap(image, IMAGE_SIZE, IMAGE_SIZE, false);
                    classifyImage(scaledImage);
                }
            } catch (Exception e) {
                Log.e("ImageProcessing", "Error processing image", e);
                resultTextView.setText("Error processing image");
                Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap getImageFromResult(int requestCode, Intent data) throws IOException {
        if (requestCode == CAMERA_REQUEST_CODE && data != null) {
            Bundle extras = data.getExtras();
            Bitmap image = (Bitmap) extras.get("data");
            return ThumbnailUtils.extractThumbnail(
                    image,
                    Math.min(image.getWidth(), image.getHeight()),
                    Math.min(image.getWidth(), image.getHeight())
            );
        } else if (requestCode == GALLERY_REQUEST_CODE && data != null) {
            Uri imageUri = data.getData();
            return MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
        }
        return null;
    }

    private void classifyImage(Bitmap image) {
        if (tfliteInterpreter == null) {
            resultTextView.setText("Model not loaded");
            return;
        }

        try {
            // Prepare input tensor
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(
                    new int[]{1, IMAGE_SIZE, IMAGE_SIZE, 3},
                    DataType.FLOAT32
            );

            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            // Convert bitmap to byte buffer
            int[] intValues = new int[IMAGE_SIZE * IMAGE_SIZE];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

            for (int pixelValue : intValues) {
                byteBuffer.putFloat(((pixelValue >> 16) & 0xFF) / 255.0f);
                byteBuffer.putFloat(((pixelValue >> 8) & 0xFF) / 255.0f);
                byteBuffer.putFloat((pixelValue & 0xFF) / 255.0f);
            }

            inputFeature0.loadBuffer(byteBuffer);

            // Prepare output tensor
            TensorBuffer outputFeature0 = TensorBuffer.createFixedSize(
                    new int[]{1, CLASSES.length},
                    DataType.FLOAT32
            );

            // Run inference
            tfliteInterpreter.run(inputFeature0.getBuffer(), outputFeature0.getBuffer());

            // Process results
            float[] confidences = outputFeature0.getFloatArray();
            int maxPos = 0;
            float maxConfidence = confidences[0];

            for (int i = 1; i < confidences.length; i++) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }

            resultTextView.setText("Result: " + CLASSES[maxPos]);

        } catch (Exception e) {
            Log.e("Classification", "Error during classification", e);
            resultTextView.setText("Classification failed");
            Toast.makeText(this, "Classification error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == CAMERA_PERMISSION_CODE) {
                openCamera();
            } else if (requestCode == STORAGE_PERMISSION_CODE) {
                openGallery();
            }
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
        }
    }
}