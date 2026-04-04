package ax.nd.faceunlock.camera.callables;

import static ax.nd.faceunlock.FaceUnlockService.DEBUG;
import static android.graphics.ImageFormat.YUV_420_888;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ax.nd.faceunlock.camera.Camera2Utils;
import ax.nd.faceunlock.camera.CameraRepository;
import ax.nd.faceunlock.camera.listeners.ByteBufferCallbackListener;
import ax.nd.faceunlock.camera.listeners.CameraListener;

public class ConfigureAndStartPreviewCallable extends CameraCallable {
    private static final String TAG = "ConfigStartCallable";
    private final Surface mSurface;
    private final Handler mCamera2Handler;

    public ConfigureAndStartPreviewCallable(Surface surface, CameraListener cameraListener,
                                            Handler camera2Handler) {
        super(cameraListener);
        mSurface = surface;
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

        Context ctx = data.mContext;
        if (ctx != null && data.mCameraIdStr != null) {
            try {
                CameraManager manager =
                        (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
                CameraCharacteristics chars =
                        manager.getCameraCharacteristics(data.mCameraIdStr);
                StreamConfigurationMap map =
                        chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map != null) {
                    Size[] sizes = map.getOutputSizes(YUV_420_888);
                    Size best = Camera2Utils.selectBestSquareSize(sizes);
                    if (best != null) {
                        data.mWidth  = best.getWidth();
                        data.mHeight = best.getHeight();
                        if (DEBUG) Log.d(TAG, "Enrollment size: " + data.mWidth + "x" + data.mHeight);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not re-query sizes; using defaults", e);
            }
        }

        ImageReader reader = ImageReader.newInstance(data.mWidth, data.mHeight, YUV_420_888, 3);
        data.mImageReader = reader;

        reader.setOnImageAvailableListener(imageReader -> {
            try (Image image = imageReader.acquireLatestImage()) {
                if (image == null) return;
                ByteBufferCallbackListener cb = data.mPreviewCallback;
                if (cb == null) return;
                byte[] nv21 = Camera2Utils.imageToNV21(image, data.mWidth, data.mHeight);
                cb.onEventCallback(0, nv21);
            } catch (Exception e) {
                Log.w(TAG, "Frame processing error", e);
            }
        }, mCamera2Handler);

        List<Surface> targets = new ArrayList<>();
        targets.add(reader.getSurface());
        if (mSurface != null) {
            targets.add(mSurface);
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean configured = new AtomicBoolean(false);

        try {
            data.mCameraDevice.createCaptureSession(targets,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            data.mCaptureSession = session;
                            try {
                                CaptureRequest.Builder builder = data.mCameraDevice
                                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                builder.addTarget(reader.getSurface());
                                if (mSurface != null) builder.addTarget(mSurface);
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
                                if (DEBUG) Log.d(TAG, "Enrollment capture session started");
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to start repeating request", e);
                            }
                            latch.countDown();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Enrollment capture session configuration failed");
                            latch.countDown();
                        }
                    },
                    mCamera2Handler);

            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timed out waiting for enrollment capture session");
            }

            if (configured.get()) {
                if (getCameraListener() != null) {
                    CameraCallable.runOnUiThread(() -> getCameraListener().onComplete(null));
                }
            } else {
                throw new RuntimeException("Enrollment capture session configuration failed");
            }

        } catch (Exception e) {
            Log.e(TAG, "Critical failure in ConfigureAndStartPreview", e);
            if (getCameraListener() != null) {
                final Exception err = e;
                CameraCallable.runOnUiThread(() -> getCameraListener().onError(err));
            }
        }
    }
}