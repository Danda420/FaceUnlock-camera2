package ax.nd.faceunlock.camera;

import android.content.Context;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Handler;

import ax.nd.faceunlock.camera.listeners.ByteBufferCallbackListener;

public class CameraRepository {
    private static CameraRepository sInstance;
    private final CameraData mCameraData = new CameraData();

    public static synchronized CameraRepository getInstance() {
        if (sInstance == null) {
            sInstance = new CameraRepository();
        }
        return sInstance;
    }

    public CameraData getCameraData() {
        return mCameraData;
    }

    public static class CameraData {
        public Context mContext;
        public CameraDevice mCameraDevice;
        public String mCameraIdStr;
        public CameraCaptureSession mCaptureSession;
        public CaptureRequest.Builder mPreviewRequestBuilder;
        public ImageReader mImageReader;
        public int mWidth = 640;
        public int mHeight = 480;
        public int mSensorOrientation = 90;
        public volatile ByteBufferCallbackListener mPreviewCallback;
        public Handler mCamera2Handler;
    }
}
