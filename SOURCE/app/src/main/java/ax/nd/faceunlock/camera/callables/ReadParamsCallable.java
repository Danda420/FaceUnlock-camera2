package ax.nd.faceunlock.camera.callables;

import android.util.Size;

import ax.nd.faceunlock.camera.CameraRepository;
import ax.nd.faceunlock.camera.listeners.CameraListener;
import ax.nd.faceunlock.camera.listeners.ReadParametersListener;

public class ReadParamsCallable extends CameraCallable {
    private final ReadParametersListener mReadListener;

    public ReadParamsCallable(ReadParametersListener listener, CameraListener cameraListener) {
        super(cameraListener);
        mReadListener = listener;
    }

    @Override
    public void run() {
        CameraRepository.CameraData data = getCameraData();
        Size size = new Size(data.mWidth, data.mHeight);

        if (mReadListener != null) {
            mReadListener.onEventCallback(0, size);
        }
        if (getCameraListener() != null) {
            getCameraListener().onComplete(null);
        }
    }
}
