package ax.nd.faceunlock.camera.callables;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.util.Log;

import ax.nd.faceunlock.camera.CameraRepository;
import ax.nd.faceunlock.camera.listeners.CameraListener;
import ax.nd.faceunlock.camera.listeners.FocusResultListener;

public class AutoFocusCallable extends CameraCallable {
    private static final String TAG = "AutoFocusCallable";

    private final boolean mAutoFocus;
    private final FocusResultListener mFocusListener;
    private final Handler mCamera2Handler;

    public AutoFocusCallable(boolean autoFocus, FocusResultListener focusResultListener,
                             CameraListener cameraListener, Handler camera2Handler) {
        super(cameraListener);
        mAutoFocus = autoFocus;
        mFocusListener = focusResultListener;
        mCamera2Handler = camera2Handler;
    }

    @Override
    public void run() {
        CameraRepository.CameraData data = getCameraData();
        if (data.mCaptureSession == null || data.mPreviewRequestBuilder == null) {
            Log.w(TAG, "No active capture session; skipping AF " + (mAutoFocus ? "trigger" : "cancel"));
            notifyComplete();
            return;
        }

        try {
            if (mAutoFocus) {
                data.mPreviewRequestBuilder.set(
                        CaptureRequest.CONTROL_AF_TRIGGER,
                        CaptureRequest.CONTROL_AF_TRIGGER_START);
            } else {
                data.mPreviewRequestBuilder.set(
                        CaptureRequest.CONTROL_AF_TRIGGER,
                        CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            }
            data.mCaptureSession.capture(
                    data.mPreviewRequestBuilder.build(), null, mCamera2Handler);

            data.mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            data.mCaptureSession.setRepeatingRequest(
                    data.mPreviewRequestBuilder.build(), null, mCamera2Handler);

            if (mFocusListener != null) {
                mFocusListener.onEventCallback(FocusResultListener.FOCUS_COMPLETE, true);
            }
            notifyComplete();

        } catch (CameraAccessException e) {
            Log.e(TAG, "AF trigger failed", e);
            CameraCallable.runOnUiThread(() -> {
                if (getCameraListener() != null) getCameraListener().onError(e);
            });
        }
    }

    private void notifyComplete() {
        CameraCallable.runOnUiThread(() -> {
            if (getCameraListener() != null) getCameraListener().onComplete(null);
        });
    }
}
