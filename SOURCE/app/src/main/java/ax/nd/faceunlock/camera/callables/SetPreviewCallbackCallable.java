package ax.nd.faceunlock.camera.callables;

import ax.nd.faceunlock.camera.CameraRepository;
import ax.nd.faceunlock.camera.listeners.ByteBufferCallbackListener;
import ax.nd.faceunlock.camera.listeners.CameraListener;

public class SetPreviewCallbackCallable extends CameraCallable {
    private final ByteBufferCallbackListener mCallback;
    private final boolean mWithBuffer;

    public SetPreviewCallbackCallable(ByteBufferCallbackListener callback,
                                      boolean withBuffer,
                                      CameraListener cameraListener) {
        super(cameraListener);
        mCallback = callback;
        mWithBuffer = withBuffer;
    }

    @Override
    public void run() {
        getCameraData().mPreviewCallback = mCallback;

        if (getCameraListener() != null) {
            getCameraListener().onComplete(null);
        }
    }
}
