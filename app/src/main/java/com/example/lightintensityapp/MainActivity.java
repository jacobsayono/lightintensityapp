package com.example.lightintensityapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.YuvImage;
import java.io.ByteArrayOutputStream;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.hardware.camera2.CaptureRequest.Builder;

import android.view.MotionEvent;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private static final int MAX_DATA_POINTS = 1000;

    private ToggleButton toggleButton;
    private TextureView textureView;

    private View boundingBox;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private TextView lightIntensityTextView;

    private LineChart lightIntensityChart;
    private LineDataSet lightIntensityDataSet;
    private ArrayList<Entry> lightIntensityEntries;
    private long startTime;

    private ImageReader imageReader;
    private CaptureRequest.Builder captureRequestBuilder;



    private static final int BOX_SIZE = 50; // The size of the bounding box
    private int boxLeft = 0;
    private int boxTop = 0;
    private Paint boxPaint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lightIntensityChart = findViewById(R.id.lightIntensityChart);
        lightIntensityEntries = new ArrayList<>();
        lightIntensityDataSet = new LineDataSet(lightIntensityEntries, "Light Intensity");
        lightIntensityDataSet.setDrawCircles(false);
        lightIntensityDataSet.setDrawValues(false); // Disable drawing values on data points
        lightIntensityChart.setData(new LineData(lightIntensityDataSet));

        // Set the range of the left axis of the chart to 0 to 5000
        lightIntensityChart.getAxisLeft().setAxisMinimum(0);
        lightIntensityChart.getAxisLeft().setAxisMaximum(5000);

        // Enable labels on the left axis of the chart and set the label count to 6
        lightIntensityChart.getAxisLeft().setDrawLabels(true);
        lightIntensityChart.getAxisLeft().setLabelCount(6);

        // Disable the right axis of the chart
        lightIntensityChart.getAxisRight().setEnabled(false);

        startTime = System.currentTimeMillis();

        // Set up the x-axis with a ValueFormatter that formats the values as time in milliseconds without trailing zeros
        lightIntensityChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                long millis = (long) value;
                return String.valueOf(millis).substring(0, String.valueOf(millis).length() - 2);
            }
        });

        // Set the position of the X-axis to the bottom of the plot
        lightIntensityChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);

        textureView = findViewById(R.id.textureView);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            setupCamera();
            boxPaint = new Paint();
            boxPaint.setColor(Color.RED);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(5.0f);
        }

        toggleButton = findViewById(R.id.toggleButton);
        toggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                try {
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });

        lightIntensityTextView = findViewById(R.id.lightIntensityTextView);

        if (lightIntensityTextView != null) {
            // Handle touch events to move the bounding box
            textureView.setOnTouchListener((v, event) -> {
                // Get the touch coordinates
                float touchX = event.getX();
                float touchY = event.getY();

                // Call the method to update the bounding box position
                updateBoundingBoxPosition(touchX, touchY);

                // Invalidate the texture view to redraw the bounding box
                textureView.invalidate();

                // Return false to indicate that the touch event is not consumed
                return false;
            });
        }


        // Set up the surface texture listener after the camera setup
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                boxLeft = width / 2 - BOX_SIZE / 2;
                boxTop = height / 2 - BOX_SIZE / 2;
                setupCamera(); // Move the camera setup here
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                textureView.setSurfaceTextureListener(null);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                Canvas canvas = textureView.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                    // Draw bounding box on the updated surface
                    canvas.drawRect(boxLeft, boxTop, boxLeft + BOX_SIZE, boxTop + BOX_SIZE, boxPaint);
                    textureView.unlockCanvasAndPost(canvas);
                }
            }
        });

        boundingBox = findViewById(R.id.boundingBox);
    }

    private void updateBoundingBoxPosition(float x, float y) {
        int boundingBoxWidth = BOX_SIZE;
        int boundingBoxHeight = BOX_SIZE;
        int textureViewWidth = textureView.getWidth();
        int textureViewHeight = textureView.getHeight();

        float boundingBoxX = Math.max(0, Math.min(x - boundingBoxWidth / 2, textureViewWidth - boundingBoxWidth));
        float boundingBoxY = Math.max(0, Math.min(y - boundingBoxHeight / 2, textureViewHeight - boundingBoxHeight));

        boundingBox.setX(boundingBoxX);
        boundingBox.setY(boundingBoxY);

        // Update the boxLeft and boxTop variables based on the new position and size
        boxLeft = (int) boundingBoxX;
        boxTop = (int) boundingBoxY;
    }



    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera Background");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera();
        }
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void setupCamera() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

            // Check if the camera has a flash unit that can be used as a torch.
            Boolean isTorchAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (isTorchAvailable != null && isTorchAvailable) {
                setupImageReader();
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        createCaptureSession();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        camera.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        camera.close();
                    }
                }, backgroundHandler);
            } else {
                Toast.makeText(this, "Torch not available on this device", Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (CameraAccessException | SecurityException e) {
            e.printStackTrace();
        }
    }

    private void createCaptureSession() {
        try {
            cameraDevice.createCaptureSession(
                    Arrays.asList(new Surface(textureView.getSurfaceTexture()), imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            startCapture();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startCapture() {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(new Surface(textureView.getSurfaceTexture()));
            captureRequestBuilder.addTarget(imageReader.getSurface());

            // Disable auto exposure
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);

            // Set the flash mode based on the state of the toggle button
            boolean isFlashlightOn = toggleButton.isChecked();
            if (isFlashlightOn) {
                captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            } else {
                captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }

            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int captureCount = 0;
    private static final int MAX_CAPTURE_COUNT = 100;

    private void captureSingleImage() {
        if (captureCount >= MAX_CAPTURE_COUNT) {
            try {
                captureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        // Capture the next image after processing the current one
                        captureSingleImage();
                    }
                }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }


    private void setupImageReader() {
        // Create an ImageReader with the desired format and size
        int imageFormat = ImageFormat.YUV_420_888;
        int imageWidth = textureView.getWidth();
        int imageHeight = textureView.getHeight();
        int maxImages = 2; // Allow for double buffering
        imageReader = ImageReader.newInstance(imageWidth, imageHeight, imageFormat, maxImages);

        // Set the OnImageAvailableListener to receive image capture results
        imageReader.setOnImageAvailableListener(reader -> {
            // Get the latest captured image
            Image image = reader.acquireLatestImage();
            if (image != null) {
                // Process the captured image
                int lightIntensity = calculateLightIntensity(image);

                // Close the image to release resources
                image.close();

                // Update the UI with the light intensity value
                runOnUiThread(() -> {
                    lightIntensityTextView.setText("Light Intensity: " + lightIntensity);

                    // Add the new light intensity data point to the chart
                    long elapsedTimeInMilliseconds = System.currentTimeMillis() - startTime;
                    lightIntensityEntries.add(new Entry(elapsedTimeInMilliseconds, lightIntensity));

                    // Set the minimum and maximum values of the y-axis explicitly
                    lightIntensityChart.getAxisLeft().setAxisMinimum(0f);
                    lightIntensityChart.getAxisLeft().setAxisMaximum(5000f);

                    // Remove data points that are off the left edge of the chart
                    while (lightIntensityEntries.get(0).getX() < lightIntensityEntries.get(lightIntensityEntries.size() - 1).getX() - MAX_DATA_POINTS) {
                        lightIntensityEntries.remove(0);
                    }

                    // Set the color of the line to black
                    lightIntensityDataSet.setColor(Color.BLACK);

                    // Notify the chart that the data has changed
                    lightIntensityDataSet.notifyDataSetChanged();
                    lightIntensityChart.getData().notifyDataChanged();
                    lightIntensityChart.notifyDataSetChanged();
                    lightIntensityChart.invalidate();
                });
            }
        }, null);
    }

    private int calculateLightIntensity(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] data = new byte[buffer.capacity()];
        buffer.get(data);
        int rowStride = planes[0].getRowStride();
        int pixelStride = planes[0].getPixelStride();

        int sum = 0;
        int count = 0;

        // Calculate the pixel position of the bounding box
        int boxLeftPixel = boxTop * image.getWidth() / textureView.getHeight();
        int boxTopPixel = (textureView.getWidth() - boxLeft - BOX_SIZE) * image.getHeight() / textureView.getWidth();
        int boxRightPixel = boxLeftPixel + BOX_SIZE * image.getWidth() / textureView.getHeight();
        int boxBottomPixel = boxTopPixel + BOX_SIZE * image.getHeight() / textureView.getWidth();


        // Iterate through the Y plane within the bounding box and calculate the sum of luminance values
        for (int row = boxTopPixel; row < boxBottomPixel; row++) {
            int rowOffset = row * rowStride;
            for (int col = boxLeftPixel; col < boxRightPixel; col++) {
                int offset = rowOffset + col * pixelStride;
                int luminance = buffer.get(offset) & 0xFF;
                sum += luminance;
                count++;
            }
        }

        // Calculate the average luminance
        int averageLuminance = count != 0 ? sum / count : 0;

        // Scale the average luminance to the desired range
        int lightIntensity = (int) (averageLuminance / 255.0 * 5000.0);
        return lightIntensity;
    }


    private byte[] imageToByteArray(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        byte[] data = new byte[buffer.capacity()];
        buffer.get(data);
        return data;
    }

    private void stopCapture() {
        try {
            if (captureSession != null) {
                captureSession.stopRepeating();
                captureSession.abortCaptures();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }




    @Override
    protected void onDestroy() {
        stopCapture();
        super.onDestroy();
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);
        }
        if (cameraManager != null) {
            cameraManager = null;
        }
    }
}
