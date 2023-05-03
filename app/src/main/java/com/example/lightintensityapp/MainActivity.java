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

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

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
            cameraDevice.createCaptureSession(Collections.singletonList(new Surface(textureView.getSurfaceTexture())),
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
            CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(new Surface(textureView.getSurfaceTexture()));

            // Get the state of the toggle button
            boolean isFlashlightOn = toggleButton.isChecked();

            // Set the flash mode based on the state of the toggle button
            if (isFlashlightOn) {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            } else {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }

            captureSession.setRepeatingRequest(requestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    int sensorSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY);
                    int lightIntensity = 5000 - sensorSensitivity;
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
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
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
