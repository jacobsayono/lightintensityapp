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
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private TextView lightIntensityTextView;

    private LineChart lightIntensityChart;
    private LineDataSet lightIntensityDataSet;
    private ArrayList<Entry> lightIntensityEntries;
    private long startTime;

    private ImageReader imageReader;

    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;

    // Declare the previous frame's buffer variable as a class member variable
    private ByteBuffer previousFrameBuffer = null;

    private int[][] blinkCounters;  // This should be declared at the top of your class



    /*
    This threshold is used to determine whether the change in brightness of a pixel is significant enough to be considered a "blinking" pixel. If the difference between the current brightness and the previous brightness of a pixel exceeds this threshold, then the pixel is considered to be "blinking".

    Increasing this value would mean that only larger changes in brightness would be considered "blinking". This could help reduce the influence of minor brightness fluctuations, such as those caused by noise in the camera sensor or small variations in lighting. However, if this value is too high, it could miss genuine brightness changes caused by your rotating mechanism.

    Decreasing this value would mean that smaller changes in brightness would be considered "blinking". This could make the algorithm more sensitive to brightness changes caused by your rotating mechanism. However, if this value is too low, it could cause the algorithm to detect a lot of false positives due to minor brightness fluctuations.
     */
    private int blinkingPixelThreshold = 50; // Adjust this threshold as needed (0-255)



    /*
    This threshold is used to determine whether the rate of brightness change of a pixel is fast enough to be considered a "blinking" pixel. If the rate of change of brightness (calculated as the difference in brightness divided by the time difference) exceeds this threshold, then the pixel is considered to be "blinking".

    Increasing this value would mean that only faster changes in brightness would be considered "blinking". This could help reduce the influence of slow changes in brightness, such as those caused by changes in ambient lighting. However, if this value is too high, it could miss genuine brightness changes caused by your rotating mechanism that happen at a slower rate.

    Decreasing this value would mean that slower changes in brightness would be considered "blinking". This could make the algorithm more sensitive to brightness changes caused by your rotating mechanism, even if they happen at a slower rate. However, if this value is too low, it could cause the algorithm to detect a lot of false positives due to slow changes in brightness.
     */
    // Set a time-based threshold for ignoring pixels based on brightness change rate
    private float brightnessChangeRateThreshold = 1f; // Adjust this threshold as needed, for example 0.2 = 20% change per millisecond



    private int minBlinkCountThreshold = 3; // adjust this value based on your mechanism



    /*
    This threshold determines the maximum amount of time that a brightness change can take to still be considered a "blinking" pixel. If the time difference between the current frame and the frame in which the brightness change started is less than this threshold, then the pixel is considered to be "blinking".

    Increasing this value would mean that longer-lasting changes in brightness would still be considered "blinking". This could make the algorithm more tolerant of brightness changes that happen over a longer time period. However, if this value is too high, it could cause the algorithm to detect a lot of false positives due to longer-lasting brightness changes that are not caused by your rotating mechanism.

    Decreasing this value would mean that only quick, short-lasting changes in brightness would be considered "blinking". This could help reduce the influence of brightness changes that happen over a longer time period. However, if this value is too low, it could miss genuine brightness changes caused by your rotating mechanism that happen over a longer time period.
     */
//    private long maxChangeDuration = 5000; // Change this as needed, in milliseconds

    private long[][] previousFrameTime = null; // This needs to be initialized similarly to previousFrameBuffer

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
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                setupCamera();
                textureView.setSurfaceTextureListener(this);
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
            }
        });

        toggleButton = findViewById(R.id.toggleButton);
        toggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            startCapture();
        });


        lightIntensityTextView = findViewById(R.id.lightIntensityTextView);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            setupCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCamera();
            }
        }
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
                }, null);
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

            captureSingleImage();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureSingleImage() {
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


    private void setupImageReader() {
        // Create an ImageReader with the desired format and size
        int imageFormat = ImageFormat.YUV_420_888;
        int imageWidth = textureView.getWidth() / 4;  // Reduce image resolution by a factor of 2
        int imageHeight = textureView.getHeight() / 4;
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
        int rowStride = planes[0].getRowStride();
        int pixelStride = planes[0].getPixelStride();
        int width = image.getWidth();
        int height = image.getHeight();

        int sum = 0;
        int count = 0;

        // If buffer sizes have changed, re-allocate
        if (previousFrameBuffer == null || previousFrameBuffer.capacity() != buffer.capacity()) {
            int bufferSize = buffer.capacity();
            previousFrameBuffer = ByteBuffer.allocate(bufferSize);
            previousFrameTime = new long[height][width];
            blinkCounters = new int[height][width];  // Initialize blinkCounters here

        }

        for (int row = 0; row < height; row++) {
            int rowOffset = row * rowStride;
            for (int col = 0; col < width; col++) {
                int offset = rowOffset + col * pixelStride;
                int luminance = buffer.get(offset) & 0xFF;

                if (previousFrameBuffer != null) {
                    int previousLuminance = previousFrameBuffer.get(offset) & 0xFF;
                    long currentTime = System.currentTimeMillis();
                    long previousTime = previousFrameTime[row][col];
                    long elapsedTimeInMilliseconds = currentTime - previousTime;

                    // If elapsed time is zero, skip this pixel to avoid division by zero
                    if (elapsedTimeInMilliseconds == 0) {
                        continue;
                    }

                    int luminanceDifference = Math.abs(luminance - previousLuminance);
                    float brightnessChangeRate = 1000.0f * luminanceDifference / (float) elapsedTimeInMilliseconds;

                    // Modify the condition for incrementing the sum and count variables
                    if (luminanceDifference > blinkingPixelThreshold && brightnessChangeRate > brightnessChangeRateThreshold) {
                        blinkCounters[row][col]++;
                        if (blinkCounters[row][col] >= minBlinkCountThreshold) {
                            sum += luminance;
                            count++;
                        }
                    } else {
                        blinkCounters[row][col] = 0;
                    }

                    previousFrameTime[row][col] = currentTime;
                }
            }
        }

        previousFrameBuffer.clear();
        previousFrameBuffer.put(buffer);

        int averageLuminance = count > 0 ? sum / count : 0;
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


    @Override
    protected void onDestroy() {
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
