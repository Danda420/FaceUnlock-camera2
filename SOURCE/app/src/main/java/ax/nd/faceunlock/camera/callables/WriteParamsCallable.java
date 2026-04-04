package ax.nd.faceunlock.camera.callables;

import android.os.Handler;
import android.util.Log;

import ax.nd.faceunlock.camera.CameraRepository;
import ax.nd.faceunlock.camera.listeners.CameraListener;

public class WriteParamsCallable extends CameraCallable {
    private static final String TAG = "WriteParamsCallable";
    private final Handler mCamera2Handler;

    public WriteParamsCallable(CameraListener cameraListener, Handler camera2Handler) {
        super(cameraListener);
        mCamera2Handler = camera2Handler;
    }

    @Override
    public void run() {
        CameraRepository.CameraData data = getCameraData();
        if (data.mCaptureSession == null || data.mPreviewRequestBuilder == null) {
            if (getCameraListener() != null) {
                CameraCallable.runOnUiThread(() -> {
                    if (getCameraListener() != null) getCameraListener().onComplete(null);
                });
            }
            return;
        }

        try {
            data.mCaptureSession.setRepeatingRequest(
                    data.mPreviewRequestBuilder.build(), null, mCamera2Handler);
            if (getCameraListener() != null) {
                CameraCallable.runOnUiThread(() -> {
                    if (getCameraListener() != null) getCameraListener().onComplete(null);
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to resubmit capture request", e);
            if (getCameraListener() != null) {
                final Exception ex = e;
                CameraCallable.runOnUiThread(() -> {
                    if (getCameraListener() != null) getCameraListener().onError(ex);
                });
            }
        }
    }
}
