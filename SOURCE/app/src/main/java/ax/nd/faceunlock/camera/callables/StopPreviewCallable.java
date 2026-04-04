package ax.nd.faceunlock.camera.callables;

import android.os.Handler;
import android.util.Log;

import ax.nd.faceunlock.camera.CameraRepository;
import ax.nd.faceunlock.camera.listeners.CameraListener;

public class StopPreviewCallable extends CameraCallable {
    private static final String TAG = "StopPreviewCallable";
    private final Handler mCamera2Handler;

    public StopPreviewCallable(CameraListener cameraListener, Handler camera2Handler) {
        super(cameraListener);
        mCamera2Handler = camera2Handler;
    }

    @Override
    public void run() {
        CameraRepository.CameraData data = getCameraData();
        try {
            if (data.mCaptureSession != null) {
                data.mCaptureSession.stopRepeating();
            }
            if (getCameraListener() != null) {
                getCameraListener().onComplete(null);
            }
        } catch (IllegalStateException e) {
            // ignore
        } catch (Exception e) {
            Log.w(TAG, "stopPreview error", e);
            if (getCameraListener() != null) {
                getCameraListener().onError(e);
            }
        }
    }
}
