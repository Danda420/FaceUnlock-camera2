package ax.nd.faceunlock.camera.callables;

import android.os.Handler;
import android.util.Log;

import ax.nd.faceunlock.camera.CameraRepository;
import ax.nd.faceunlock.camera.listeners.CameraListener;

public class SetFaceDetectionCallback extends CameraCallable {
    private static final String TAG = "SetFaceDetectCallable";

    private final boolean mEnable;
    private final Handler mCamera2Handler;

    public SetFaceDetectionCallback(CameraListener cameraListener, Handler camera2Handler) {
        super(cameraListener);
        mEnable = true;
        mCamera2Handler = camera2Handler;
    }

    public SetFaceDetectionCallback(boolean enable, CameraListener cameraListener,
                                    Handler camera2Handler) {
        super(cameraListener);
        mEnable = enable;
        mCamera2Handler = camera2Handler;
    }

    @Override
    public void run() {
        CameraRepository.CameraData data = getCameraData();
        if (data.mCaptureSession == null || data.mPreviewRequestBuilder == null) {
            if (getCameraListener() != null) getCameraListener().onComplete(null);
            return;
        }

        try {
            data.mPreviewRequestBuilder.set(
                    android.hardware.camera2.CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                    mEnable
                            ? android.hardware.camera2.CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE
                            : android.hardware.camera2.CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF);

            data.mCaptureSession.setRepeatingRequest(
                    data.mPreviewRequestBuilder.build(), null, mCamera2Handler);

            if (getCameraListener() != null) getCameraListener().onComplete(null);

        } catch (Exception e) {
            Log.w(TAG, "setFaceDetectMode failed (device may not support it)", e);
            if (getCameraListener() != null) getCameraListener().onComplete(null);
        }
    }
}
