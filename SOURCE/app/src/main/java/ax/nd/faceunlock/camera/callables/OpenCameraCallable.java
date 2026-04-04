package ax.nd.faceunlock.camera.callables;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.util.Log;
import android.util.Size;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import ax.nd.faceunlock.camera.Camera2Utils;
import ax.nd.faceunlock.camera.CameraRepository;
import ax.nd.faceunlock.camera.listeners.CameraListener;
import ax.nd.faceunlock.camera.listeners.ErrorCallbackListener;

public class OpenCameraCallable extends CameraCallable {
    private static final String TAG = "OpenCameraCallable";

    private final Context mContext;
    private final int mCameraId;
    private final ErrorCallbackListener mErrorCallbackListener;
    private final Handler mCamera2Handler;

    public OpenCameraCallable(Context context, int cameraId,
                              ErrorCallbackListener errorCallbackListener,
                              CameraListener cameraListener,
                              Handler camera2Handler) {
        super(cameraListener);
        mContext = context;
        mCameraId = cameraId;
        mErrorCallbackListener = errorCallbackListener;
        mCamera2Handler = camera2Handler;
    }

    @SuppressLint({"MissingPermission", "SoonBlockedPrivateApi"})
    @Override
    public void run() {
        CameraRepository.CameraData data = getCameraData();

        if (data.mCameraDevice != null) {
            try { data.mCameraDevice.close(); } catch (Exception ignored) {}
            data.mCameraDevice = null;
        }
        Camera2Utils.closePreviousSession(data);

        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

        try {
            String[] ids = manager.getCameraIdList();
            if (mCameraId >= ids.length) {
                throw new IllegalArgumentException(
                        "Camera index " + mCameraId + " out of range (found " + ids.length + ")");
            }
            data.mCameraIdStr = ids[mCameraId];

            CameraCharacteristics chars = manager.getCameraCharacteristics(data.mCameraIdStr);

            Integer sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
            data.mSensorOrientation = (sensorOrientation != null) ? sensorOrientation : 90;

            StreamConfigurationMap map =
                    chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                Size best = Camera2Utils.selectBestSize(sizes);
                if (best != null) {
                    data.mWidth  = best.getWidth();
                    data.mHeight = best.getHeight();
                    Log.i(TAG, "Selected preview size: " + data.mWidth + "x" + data.mHeight);
                }
            }

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicBoolean opened = new AtomicBoolean(false);
            final AtomicReference<Exception> openError = new AtomicReference<>(null);

            final String cameraIdStr = data.mCameraIdStr;
            Thread openThread = new Thread(() -> {
                try {
                    Class<?> binderClass = Class.forName("android.os.Binder");
                    java.lang.reflect.Method m = binderClass.getDeclaredMethod("setWarnOnBlocking", boolean.class);
                    m.setAccessible(true);
                    m.invoke(null, false);
                } catch (Throwable ignored) {}

                try {
                    manager.openCamera(cameraIdStr, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice camera) {
                            data.mCameraDevice = camera;
                            opened.set(true);
                            latch.countDown();
                        }
                        @Override
                        public void onDisconnected(CameraDevice camera) {
                            Log.w(TAG, "Camera disconnected during open");
                            camera.close();
                            latch.countDown();
                        }
                        @Override
                        public void onError(CameraDevice camera, int error) {
                            Log.e(TAG, "Camera open error code: " + error);
                            camera.close();
                            latch.countDown();
                        }
                    }, mCamera2Handler);
                } catch (Exception e) {
                    openError.set(e);
                    latch.countDown();
                } finally {
                    try {
                        Class<?> binderClass = Class.forName("android.os.Binder");
                        java.lang.reflect.Method m = binderClass.getDeclaredMethod("setWarnOnBlocking", boolean.class);
                        m.setAccessible(true);
                        m.invoke(null, true);
                    } catch (Throwable ignored) {}
                }
            }, "camera-open-thread");
            openThread.setDaemon(true);
            openThread.start();

            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Timed out waiting for camera to open");
            }

            if (openError.get() != null) {
                throw openError.get();
            }

            if (opened.get()) {
                if (getCameraListener() != null) {
                    getCameraListener().onComplete(data.mCameraDevice);
                }
            } else {
                throw new RuntimeException("Camera failed to open (disconnected or error)");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to open camera", e);
            if (getCameraListener() != null) getCameraListener().onError(e);
            if (mErrorCallbackListener != null) {
                mErrorCallbackListener.onEventCallback(1, "Camera Open Failed");
            }
        }
    }
}