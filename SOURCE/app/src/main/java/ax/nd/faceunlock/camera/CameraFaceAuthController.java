package ax.nd.faceunlock.camera;

import static ax.nd.faceunlock.FaceUnlockService.DEBUG;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.os.Process;

import ax.nd.faceunlock.camera.listeners.CameraListener;
import ax.nd.faceunlock.camera.listeners.ErrorCallbackListener;

public class CameraFaceAuthController {
    private static final String TAG = "CameraFaceAuthController";
    private Context mContext;
    private Handler mHandler;
    private HandlerThread mAuthHandlerThread;
    private volatile Handler mAuthHandler;
    private ServiceCallback mCallback;
    private volatile boolean mIsAuthenticating = false;

    private int mWidth  = 640;
    private int mHeight = 480;

    public interface ServiceCallback {
        int handlePreviewData(byte[] data, int width, int height);
        void setDetectArea(int width, int height);
        void onTimeout(boolean b);
        void onCameraError();
    }

    public CameraFaceAuthController(Context context, ServiceCallback callback) {
        mContext  = context;
        mCallback = callback;
        mHandler  = new Handler(Looper.getMainLooper());
    }

    public void start(int cameraId, SurfaceTexture dummySurface) {
        if (DEBUG) Log.d(TAG, "Starting Auth Camera...");
        if (mIsAuthenticating) stop();
        mIsAuthenticating = true;

        mAuthHandlerThread = new HandlerThread("face_auth_thread", Process.THREAD_PRIORITY_VIDEO);
        mAuthHandlerThread.start();
        mAuthHandler = new Handler(mAuthHandlerThread.getLooper());

        CameraService.openCamera(mContext, cameraId, new ErrorCallbackListener() {
            @Override
            public void onEventCallback(int i, Object value) {
                Log.e(TAG, "Auth Camera Open Error: " + i);
                if (mCallback != null) mCallback.onCameraError();
            }
        }, new CameraListener() {
            @Override
            public void onComplete(Object value) {
                CameraRepository.CameraData data = CameraRepository.getInstance().getCameraData();
                mWidth  = data.mWidth;
                mHeight = data.mHeight;

                if (DEBUG) Log.d(TAG, "Auth camera open OK (" + mWidth + "x" + mHeight + ")");
                if (mCallback != null) mCallback.setDetectArea(mWidth, mHeight);

                CameraService.startPreview(dummySurface, new CameraListener() {
                    @Override
                    public void onComplete(Object value) {
                        if (DEBUG) Log.d(TAG, "Auth preview started. Registering callback...");
                        setupFrameCallback();
                    }
                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Auth preview start failed", e);
                        if (mCallback != null) mCallback.onCameraError();
                    }
                });
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Camera open exception", e);
                if (mCallback != null) mCallback.onCameraError();
            }
        });
    }

    private void setupFrameCallback() {
        CameraService.setPreviewCallback((i, obj) -> {
            if (!mIsAuthenticating || mCallback == null) return;

            if (obj instanceof byte[]) {
                final byte[] data = (byte[]) obj;
                final Handler handler = mAuthHandler;
                if (handler != null) {
                    handler.post(() -> {
                        try {
                            if (mCallback == null || !mIsAuthenticating) return;
                            mCallback.handlePreviewData(data, mWidth, mHeight);
                        } catch (Exception e) {
                            Log.e(TAG, "Auth frame processing error", e);
                        }
                    });
                }
            }
        }, false, null);
    }

    public void stop() {
        if (DEBUG) Log.d(TAG, "Stopping Auth Camera");
        mIsAuthenticating = false;
        mCallback = null;

        CameraService.closeCamera(new CameraListener() {
            @Override
            public void onComplete(Object value) {
                mAuthHandler = null;
                if (mAuthHandlerThread != null) {
                    mAuthHandlerThread.quitSafely();
                    mAuthHandlerThread = null;
                }
            }
            @Override
            public void onError(Exception e) {
                if (mAuthHandlerThread != null) {
                    mAuthHandlerThread.quitSafely();
                    mAuthHandlerThread = null;
                }
            }
        });
    }
}