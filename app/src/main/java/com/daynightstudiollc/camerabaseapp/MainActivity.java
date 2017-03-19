package com.daynightstudiollc.camerabaseapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    ImageReader imageReader;
    ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //TODO: algo
        }
    };

    static final int CAMERA_PERMISSION = 1;
    TextureView textureView;
    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
            } else {
                openCamera();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    Handler bgHandler;
    HandlerThread bgThread;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBgThread();
        textureView = (TextureView)findViewById(R.id.tv);
        textureView.setSurfaceTextureListener(surfaceTextureListener);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            throw new RuntimeException(getString(R.string.no_permission));
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        startBgThread();

        super.onResume();
    }

    @Override
    protected void onPause() {
        stopBgThread();

        super.onPause();
    }

    public void startBgThread() {
        bgThread = new HandlerThread("CameraBackground");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
    }

    public void stopBgThread() {
        bgThread.quitSafely();
        try {
            bgThread.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*################
        CAMERA STUFF
     ################*/

    CameraManager cameraManager;
    CameraCharacteristics characteristics;
    CameraDevice cameraDevice;
    CameraCaptureSession captureSession;
    CaptureRequest captureRequest;

    CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };

    CameraCaptureSession.StateCallback captureSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            captureSession = session;

            try {
                CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                //builder.addTarget(imageReader.getSurface());
                builder.addTarget(new Surface(textureView.getSurfaceTexture()));
                captureRequest = builder.build();

                captureSession.setRepeatingRequest(captureRequest, captureCallback, bgHandler);
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };

    CameraDevice.StateCallback openCameraCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            Size smallest = getSmallestSize();
            imageReader = ImageReader.newInstance(smallest.getWidth(), smallest.getHeight(), ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(imageAvailableListener, null);

            try {
                cameraDevice.createCaptureSession(Arrays.asList(new Surface[]{imageReader.getSurface(), new Surface(textureView.getSurfaceTexture())}), captureSessionCallback, null);
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

        }

        //@IntDef(value = {CameraDevice.StateCallback.ERROR_CAMERA_IN_USE, CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE, CameraDevice.StateCallback.ERROR_CAMERA_DISABLED, CameraDevice.StateCallback.ERROR_CAMERA_DEVICE, CameraDevice.StateCallback.ERROR_CAMERA_SERVICE})
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    };

    public void openCamera() {
        cameraManager = (CameraManager)getSystemService(CAMERA_SERVICE);

        try {
            for (String id : cameraManager.getCameraIdList()) {
                characteristics = cameraManager.getCameraCharacteristics(id);
                if(characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;

                if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                    throw new RuntimeException(getString(R.string.no_permission));

                cameraManager.openCamera(id, openCameraCallback, null);
                break;
            }
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        if(captureSession != null)
            captureSession.close();

        if(cameraDevice != null)
            cameraDevice.close();

        if(imageReader != null)
            imageReader.close();
    }

    public Size getSmallestSize() {
        StreamConfigurationMap configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Size smallest = null;
        int minVal = -1;
        for(Size size: configMap.getOutputSizes(ImageFormat.YUV_420_888)) {
            int tmp = size.getWidth() * size.getHeight();
            if(minVal == -1 || tmp < minVal) {
                minVal = tmp;
                smallest = size;
            }
        }

        return smallest;
    }
}
