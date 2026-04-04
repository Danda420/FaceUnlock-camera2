package ax.nd.faceunlock.camera.callables;

import ax.nd.faceunlock.camera.listeners.CameraListener;

public class SetDisplayOrientationCallback extends CameraCallable {

    @SuppressWarnings("unused")
    private final int mOrientation;

    public SetDisplayOrientationCallback(int orientation, CameraListener cameraListener) {
        super(cameraListener);
        mOrientation = orientation;
    }

    @Override
    public void run() {
        if (getCameraListener() != null) {
            getCameraListener().onComplete(null);
        }
    }
}
