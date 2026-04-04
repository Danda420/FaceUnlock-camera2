package ax.nd.faceunlock.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import ax.nd.faceunlock.camera.callables.*;
import ax.nd.faceunlock.camera.listeners.*;

public class CameraService {
    private static final String TAG = "CameraService";
    private static final int DEFAULT_MSG_TYPE = 1;
    private final HandlerThread mThread;
    private final Handler mServiceHandler;
    private final HandlerThread mCamera2Thread;
    private final Handler mCamera2Handler;

    private CameraService() {
        mThread = new HandlerThread("CameraServiceThread", Process.THREAD_PRIORITY_VIDEO);
        mThread.start();
        mServiceHandler = new Handler(mThread.getLooper(), message -> {
            try {
                if (message.obj instanceof Runnable) {
                    ((Runnable) message.obj).run();
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error in CameraService thread", t);
            }
            return true;
        });

        mCamera2Thread = new HandlerThread("Camera2CallbackThread", Process.THREAD_PRIORITY_URGENT_DISPLAY);
        mCamera2Thread.start();
        mCamera2Handler = new Handler(mCamera2Thread.getLooper());
    }

    private static CameraService getInstance() {
        return LazyLoader.INSTANCE;
    }

    public static void openCamera(Context context, int cameraId,
                                  ErrorCallbackListener errorCallbackListener,
                                  CameraListener cameraListener) {
        CameraService svc = getInstance();
        CameraRepository.CameraData data = CameraRepository.getInstance().getCameraData();

        data.mContext = context;
        data.mCamera2Handler = svc.mCamera2Handler;

        svc.addCallable(new OpenCameraCallable(
                context, cameraId, errorCallbackListener, cameraListener, svc.mCamera2Handler));
    }

    public static void closeCamera(CameraListener cameraListener) {
        CameraService svc = getInstance();
        svc.mServiceHandler.removeMessages(DEFAULT_MSG_TYPE);
        svc.addCallable(new CloseCameraCallable(cameraListener, svc.mCamera2Handler));
    }

    public static void configureAndStartPreview(Surface surface, CameraListener cameraListener) {
        CameraService svc = getInstance();
        svc.addCallable(new ConfigureAndStartPreviewCallable(
                surface, cameraListener, svc.mCamera2Handler));
    }

    public static void startPreview(SurfaceTexture surfaceTexture, CameraListener cameraListener) {
        CameraService svc = getInstance();
        svc.addCallable(new StartPreviewCallable(cameraListener, svc.mCamera2Handler));
    }

    public static void startPreview(SurfaceHolder surfaceHolder, CameraListener cameraListener) {
        CameraService svc = getInstance();
        svc.addCallable(new StartPreviewCallable(cameraListener, svc.mCamera2Handler));
    }

    public static void stopPreview(CameraListener cameraListener) {
        getInstance().addCallable(new StopPreviewCallable(cameraListener, getInstance().mCamera2Handler));
    }

    public static void setPreviewCallback(ByteBufferCallbackListener callback,
                                          boolean withBuffer,
                                          CameraListener cameraListener) {
        getInstance().addCallable(new SetPreviewCallbackCallable(callback, withBuffer, cameraListener));
    }

    public static void addCallbackBuffer(byte[] buffer, CameraListener cameraListener) {
        getInstance().addCallable(new AddCallbackBufferCallable(buffer, cameraListener));
    }

    public static void setFaceDetectionCallback(CameraListener cameraListener) {
        CameraService svc = getInstance();
        svc.addCallable(new SetFaceDetectionCallback(cameraListener, svc.mCamera2Handler));
    }

    public static void setDisplayOrientationCallback(int orientation, CameraListener cameraListener) {
        getInstance().addCallable(new SetDisplayOrientationCallback(orientation, cameraListener));
    }

    public static void clearQueue() {
        getInstance().mServiceHandler.removeMessages(DEFAULT_MSG_TYPE);
    }

    public static void autoFocus(boolean enable, FocusResultListener focusListener, CameraListener cameraListener) {
        CameraService svc = getInstance();
        svc.addCallable(new AutoFocusCallable(enable, focusListener, cameraListener, svc.mCamera2Handler));
    }

    public static void readParameters(ReadParametersListener listener, CameraListener cameraListener) {
        getInstance().addCallable(new ReadParamsCallable(listener, cameraListener));
    }

    public static void writeParameters(CameraListener cameraListener) {
        getInstance().addCallable(new WriteParamsCallable(cameraListener, getInstance().mCamera2Handler));
    }

    private void addCallable(Runnable callable) {
        mServiceHandler.sendMessage(mServiceHandler.obtainMessage(DEFAULT_MSG_TYPE, callable));
    }

    private static final class LazyLoader {
        private static final CameraService INSTANCE = new CameraService();
    }
}