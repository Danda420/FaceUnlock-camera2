package ax.nd.faceunlock.camera.callables;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ax.nd.faceunlock.camera.Camera2Utils;
import ax.nd.faceunlock.camera.CameraRepository;
import ax.nd.faceunlock.camera.listeners.ByteBufferCallbackListener;
import ax.nd.faceunlock.camera.listeners.CameraListener;

import static android.graphics.ImageFormat.YUV_420_888;
import static ax.nd.faceunlock.FaceUnlockService.DEBUG;

public class StartPreviewCallable extends CameraCallable {
    private static final String TAG = "StartPreviewCallable";
    private final Handler mCamera2Handler;

    public StartPreviewCallable(CameraListener cameraListener, Handler camera2Handler) {
        super(cameraListener);
        mCamera2Handler = camera2Handler;
    }

    @Override
    public void run() {
        CameraRepository.CameraData data = getCameraData();

        if (data.mCameraDevice == null) {
            Log.e(TAG, "CameraDevice is null — call openCamera first");
            if (getCameraListener() != null) {
                getCameraListener().onError(new IllegalStateException("Camera not open"));
            }
            return;
        }

        Camera2Utils.closePreviousSession(data);

        ImageReader reader = ImageReader.newInstance(data.mWidth, data.mHeight, YUV_420_888, 3);
        data.mImageReader = reader;

        reader.setOnImageAvailableListener(imageReader -> {
            try (Image image = imageReader.acquireLatestImage()) {
                if (image == null) return;
                ByteBufferCallbackListener cb = data.mPreviewCallback;
                if (cb == null) return;
                byte[] nv21 = Camera2Utils.imageToNV21(image, data.mWidth, data.mHeight);
                cb.onEventCallback(0, nv21);
            } catch (IllegalStateException e) {
                // ignore
            } catch (Exception e) {
                Log.w(TAG, "Frame processing error", e);
            }
        }, mCamera2Handler);

        Surface readerSurface = reader.getSurface();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean configured = new AtomicBoolean(false);

        try {
            data.mCameraDevice.createCaptureSession(
                    Collections.singletonList(readerSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            data.mCaptureSession = session;
                            try {
                                CaptureRequest.Builder builder = data.mCameraDevice
                                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                builder.addTarget(readerSurface);
                                builder.set(CaptureRequest.CONTROL_MODE,
                                        CaptureRequest.CONTROL_MODE_AUTO);
                                builder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                builder.set(CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON);

                                try {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, 1.3f);
                                        if (DEBUG) Log.i(TAG, "Applied Camera2 CONTROL_ZOOM_RATIO: 1.3x");
                                    } else {
                                        CameraManager manager = (CameraManager) data.mContext.getSystemService(Context.CAMERA_SERVICE);
                                        CameraCharacteristics chars = manager.getCameraCharacteristics(data.mCameraIdStr);
                                        Rect activeArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                                        Float maxZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                                        if (maxZoom == null) maxZoom = 1.0f;

                                        if (activeArraySize != null) {
                                            float targetZoomRatio = 1.3f;
                                            if (targetZoomRatio > maxZoom) targetZoomRatio = maxZoom;

                                            int cropW = (int) (activeArraySize.width() / targetZoomRatio);
                                            int cropH = (int) (activeArraySize.height() / targetZoomRatio);
                                            int cx = activeArraySize.centerX();
                                            int cy = activeArraySize.centerY();

                                            Rect cropRect = new Rect(
                                                    cx - cropW / 2, cy - cropH / 2,
                                                    cx + cropW / 2, cy + cropH / 2
                                            );

                                            builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect);
                                            if (DEBUG) Log.i(TAG, "Applied Camera2 Hardware Crop-Zoom: " + targetZoomRatio + "x");
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Failed to apply Camera2 zoom", e);
                                }

                                data.mPreviewRequestBuilder = builder;
                                session.setRepeatingRequest(builder.build(), null, mCamera2Handler);
                                configured.set(true);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to start repeating request", e);
                            }
                            latch.countDown();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Capture session configuration failed");
                            latch.countDown();
                        }
                    },
                    mCamera2Handler);

            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timed out waiting for capture session");
            }

            if (configured.get()) {
                if (getCameraListener() != null) {
                    getCameraListener().onComplete(null);
                }
            } else {
                throw new RuntimeException("Capture session configuration failed");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to start preview", e);
            if (getCameraListener() != null) getCameraListener().onError(e);
        }
    }
}