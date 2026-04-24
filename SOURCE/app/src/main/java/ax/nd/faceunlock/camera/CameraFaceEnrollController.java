package ax.nd.faceunlock.camera;

import static ax.nd.faceunlock.FaceUnlockService.DEBUG;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import ax.nd.faceunlock.camera.listeners.CameraListener;
import ax.nd.faceunlock.camera.listeners.ErrorCallbackListener;

public class CameraFaceEnrollController {
    private static final String TAG = "CameraFaceEnrollController";
    private static CameraFaceEnrollController sInstance;

    private Context mContext;
    private Handler mHandler;
    private HandlerThread mEnrollHandlerThread;
    private volatile Handler mEnrollHandler;
    private volatile CameraCallback mCallback;
    private volatile boolean mIsEnrolling = false;
    private int mSrcWidth  = 0;
    private int mSrcHeight = 0;
    private int mTargetWidth  = 640;
    private int mTargetHeight = 480;
    private boolean mUseDownscale = false;
    private byte[] mProcessedBuffer;

    public interface CameraCallback {
        int handleSaveFeature(byte[] data, int width, int height, int angle);
        void handleSaveFeatureResult(int res);
        void onFaceDetected();
        void onTimeout();
        void onCameraError();
        void setDetectArea(int width, int height);
    }

    public static CameraFaceEnrollController getInstance(Context context) {
        if (sInstance == null) sInstance = new CameraFaceEnrollController(context);
        return sInstance;
    }

    public static CameraFaceEnrollController getInstance() {
        return sInstance;
    }

    private CameraFaceEnrollController(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void start(CameraCallback callback, int cameraId, Surface previewSurface) {
        if (DEBUG) Log.d(TAG, "start() cameraId=" + cameraId);
        if (mIsEnrolling) stop(null);
        mIsEnrolling = true;
        mCallback = callback;
        mSrcWidth  = 0;
        mSrcHeight = 0;

        mEnrollHandlerThread = new HandlerThread("face_enroll_thread", Process.THREAD_PRIORITY_VIDEO);
        mEnrollHandlerThread.start();
        mEnrollHandler = new Handler(mEnrollHandlerThread.getLooper());

        CameraService.openCamera(mContext, cameraId, new ErrorCallbackListener() {
            @Override
            public void onEventCallback(int i, Object value) {
                Log.e(TAG, "Camera open error: " + i);
                if (mCallback != null) mCallback.onCameraError();
            }
        }, new CameraListener() {
            @Override
            public void onComplete(Object value) {
                CameraRepository.CameraData data = CameraRepository.getInstance().getCameraData();
                mSrcWidth  = data.mWidth;
                mSrcHeight = data.mHeight;
                computeTargetSize();
                if (mCallback != null) mCallback.setDetectArea(mSrcWidth, mSrcHeight);
                startConfiguredPreview(previewSurface);
            }
            @Override
            public void onError(Exception e) {
                if (mCallback != null) mCallback.onCameraError();
            }
        });
    }

    private void startConfiguredPreview(Surface surface) {
        CameraService.configureAndStartPreview(surface, new CameraListener() {
            @Override
            public void onComplete(Object value) {
                CameraRepository.CameraData data = CameraRepository.getInstance().getCameraData();
                if (data.mWidth != mSrcWidth || data.mHeight != mSrcHeight) {
                    mSrcWidth  = data.mWidth;
                    mSrcHeight = data.mHeight;
                    computeTargetSize();
                }

                if (mCallback != null) mCallback.setDetectArea(mTargetWidth, mTargetHeight);
                if (DEBUG) Log.d(TAG, "Enrollment preview started "
                        + mSrcWidth + "x" + mSrcHeight
                        + " → target " + mTargetWidth + "x" + mTargetHeight);
                attachPreviewCallback();
            }
            @Override
            public void onError(Exception e) {
                if (mCallback != null) mCallback.onCameraError();
            }
        });
    }

    private void attachPreviewCallback() {
        CameraService.setPreviewCallback((i, obj) -> {
            final CameraCallback callback = mCallback;
            if (!mIsEnrolling || callback == null) return;

            if (obj instanceof byte[]) {
                final byte[] srcData = (byte[]) obj;

                if (mSrcWidth == 0) detectSourceResolutionFromBuffer(srcData.length);
                if (mSrcWidth == 0) return;

                if (mEnrollHandler != null) {
                    mEnrollHandler.post(() -> {
                        try {
                            byte[] dest = mProcessedBuffer;
                            if (callback == null || mSrcWidth == 0 || dest == null) return;

                            if (mUseDownscale) {
                                downscaleNV21(srcData, mSrcWidth, mSrcHeight, dest, mTargetWidth, mTargetHeight);
                            } else {
                                cropNV21(srcData, mSrcWidth, mSrcHeight, dest, mTargetWidth, mTargetHeight);
                            }

                            int res = callback.handleSaveFeature(dest, mTargetWidth, mTargetHeight, 90);
                            callback.handleSaveFeatureResult(res);

                        } catch (Exception e) {
                            Log.e(TAG, "Enroll processing error", e);
                        }
                    });
                }
            }
        }, false, null);
    }

    public void stop(CameraCallback callback) {
        if (DEBUG) Log.d(TAG, "Stopping Enroll Camera");
        mIsEnrolling = false;
        mCallback = null;
        mProcessedBuffer = null;

        mEnrollHandler = null;

        CameraService.closeCamera(new CameraListener() {
            @Override
            public void onComplete(Object value) {
                if (mEnrollHandlerThread != null) {
                    mEnrollHandlerThread.quitSafely();
                    mEnrollHandlerThread = null;
                }
            }

            @Override
            public void onError(Exception e) {
                if (mEnrollHandlerThread != null) {
                    mEnrollHandlerThread.quitSafely();
                    mEnrollHandlerThread = null;
                }
            }
        });
    }

    private void computeTargetSize() {
        if (mSrcWidth <= 0 || mSrcHeight <= 0) return;

        boolean isSquare = (mSrcWidth == mSrcHeight);

        if (isSquare && mSrcWidth >= 800) {
            mUseDownscale = true;
            mTargetWidth  = mSrcWidth  / 2;
            mTargetHeight = mSrcHeight / 2;
            if (DEBUG) Log.i(TAG, "High-Res Square → downscale to " + mTargetWidth + "x" + mTargetHeight);
        } else if (isSquare) {
            mUseDownscale = false;
            mTargetWidth  = 640;
            mTargetHeight = 480;
            if (DEBUG) Log.i(TAG, "Standard Square → crop to " + mTargetWidth + "x" + mTargetHeight);
        } else {
            mUseDownscale = false;
            mTargetWidth  = 640;
            mTargetHeight = 480;
        }
        mProcessedBuffer = new byte[mTargetWidth * mTargetHeight * 3 / 2];
    }

    private void detectSourceResolutionFromBuffer(int dataLength) {
        int pixels = (int) (dataLength / 1.5);
        int sqrt = (int) Math.sqrt(pixels);
        if (sqrt * sqrt == pixels) {
            mSrcWidth = mSrcHeight = sqrt;
        } else if (pixels == 307200)  { mSrcWidth = 640;  mSrcHeight = 480;  }
        else if (pixels == 921600)    { mSrcWidth = 1280; mSrcHeight = 720;  }
        else if (pixels == 2073600)   { mSrcWidth = 1920; mSrcHeight = 1080; }
        else {
            Log.e(TAG, "Unknown buffer size: " + dataLength);
            return;
        }
        computeTargetSize();
    }

    private void downscaleNV21(byte[] src, int srcW, int srcH,
                               byte[] dst, int dstW, int dstH) {
        for (int y = 0; y < dstH; y++)
            for (int x = 0; x < dstW; x++)
                dst[y * dstW + x] = src[(y * 2) * srcW + (x * 2)];

        int uvSrc = srcW * srcH, uvDst = dstW * dstH;
        for (int y = 0; y < dstH / 2; y++) {
            for (int x = 0; x < dstW; x += 2) {
                int si = uvSrc + (y * 2) * srcW + x * 2;
                int di = uvDst + y * dstW + x;
                dst[di]     = src[si];
                dst[di + 1] = src[si + 1];
            }
        }
    }

    private void cropNV21(byte[] src, int srcW, int srcH,
                          byte[] dst, int dstW, int dstH) {
        if (src.length < srcW * srcH * 3 / 2) return;
        int xOff = (srcW - dstW) / 2, yOff = (srcH - dstH) / 2;
        if (xOff % 2 != 0) xOff--;
        if (yOff % 2 != 0) yOff--;

        for (int i = 0; i < dstH; i++)
            System.arraycopy(src, (yOff + i) * srcW + xOff, dst, i * dstW, dstW);

        int uvSrc = srcW * srcH, uvDst = dstW * dstH;
        for (int i = 0; i < dstH / 2; i++)
            System.arraycopy(src, uvSrc + (yOff / 2 + i) * srcW + xOff,
                    dst, uvDst + i * dstW, dstW);
    }
}