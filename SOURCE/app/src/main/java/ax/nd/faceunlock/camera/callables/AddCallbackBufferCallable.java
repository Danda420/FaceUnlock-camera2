package ax.nd.faceunlock.camera.callables;

import ax.nd.faceunlock.camera.listeners.CameraListener;
public class AddCallbackBufferCallable extends CameraCallable {

    @SuppressWarnings("unused")
    private final byte[] mBuffer;

    public AddCallbackBufferCallable(byte[] buffer, CameraListener cameraListener) {
        super(cameraListener);
        mBuffer = buffer;
    }

    @Override
    public void run() {
        if (getCameraListener() != null) {
            getCameraListener().onComplete(null);
        }
    }
}
