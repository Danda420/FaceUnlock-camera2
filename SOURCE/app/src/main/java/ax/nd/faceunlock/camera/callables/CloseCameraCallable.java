package ax.nd.faceunlock.camera.callables;

import android.os.Handler;
import android.util.Log;

import ax.nd.faceunlock.camera.Camera2Utils;
import ax.nd.faceunlock.camera.CameraRepository;
import ax.nd.faceunlock.camera.listeners.CameraListener;

public class CloseCameraCallable extends CameraCallable {
    private static final String TAG = "CloseCameraCallable";
    private final Handler mCamera2Handler;

    public CloseCameraCallable(CameraListener cameraListener, Handler camera2Handler) {
        super(cameraListener);
        mCamera2Handler = camera2Handler;
    }

    @Override
    public void run() {
        CameraRepository.CameraData data = getCameraData();

        data.mPreviewCallback = null;

        Camera2Utils.closePreviousSession(data);

        if (data.mCameraDevice != null) {
            try {
                data.mCameraDevice.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing CameraDevice", e);
            } finally {
                data.mCameraDevice = null;
            }
        }

        data.mCameraIdStr = null;

        final CameraListener listener = getCameraListener();
        if (listener != null) {
            CameraCallable.runOnUiThread(() -> listener.onComplete(null));
        }
    }
}