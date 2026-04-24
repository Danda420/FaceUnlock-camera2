package ax.nd.faceunlock.vendor;

import static ax.nd.faceunlock.FaceUnlockService.DEBUG;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import ax.nd.faceunlock.backend.CustomUnlockEncryptor;
import ax.nd.faceunlock.backend.FaceUnlockVendorImpl;
import ax.nd.faceunlock.util.Util;
import java.io.File;

public class FacePPImpl {
    private static final String TAG = "FacePPImpl";
    private static final String MODEL_PATH = "/system/etc/face/model_file";
    private static final String PANORAMA_PATH = "/system/etc/face/panorama_mgb";
    private static final String DATA_PATH = "/data/system/facedata_system";

    private Context mContext;
    private boolean mIsInit = false;
    private int mFaceCount = 0;
    private Handler mHandler;

    public FacePPImpl(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void init() {
        synchronized (this) {
            if (mIsInit) return;
            if (DEBUG) Log.i(TAG, "FacePPImpl: Boot Latch Triggered");

            File dir = new File(DATA_PATH);
            if (!dir.exists()) dir.mkdirs();

            FaceUnlockVendorImpl.getInstance().initHandle(dir.getAbsolutePath(), new CustomUnlockEncryptor());
            long res = FaceUnlockVendorImpl.getInstance().initAllWithPath(PANORAMA_PATH, "", MODEL_PATH);

            if (res == 0) {
                if (DEBUG) Log.i(TAG, "FacePPImpl: Initialized successfully");
                restoreFeature();
                mIsInit = true;
                mHandler.postDelayed(() -> {
                    if (DEBUG) Log.i(TAG, "FacePPImpl: Boot Latch Released");
                }, 30000);
            }
        }
    }

    public void restoreFeature() {
        FaceUnlockVendorImpl.getInstance().prepare();
        int restoredCount = FaceUnlockVendorImpl.getInstance().restoreFeature();
        if (!isFeatureFilePresent()) {
            Log.w(TAG, "restoreFeature: recieved vendor code:" + restoredCount + " no face is restored.");
            mFaceCount = 0;
        } else {
            mFaceCount = restoredCount;
        }

        FaceUnlockVendorImpl.getInstance().reset();
    }

    private boolean isFeatureFilePresent() {
        File f = new File(DATA_PATH, "feature");
        return f.exists() && f.length() > 0;
    }

    public void saveFeatureStart() {
        if (!mIsInit) init();
        FaceUnlockVendorImpl.getInstance().prepare();
    }

    public int saveFeature(byte[] img, int w, int h, int angle, boolean mirror, byte[] feature, byte[] faceData, int[] outFaceId) {
        int res = FaceUnlockVendorImpl.getInstance().saveFeature(img, w, h, angle, mirror, feature, faceData, outFaceId);
        if (res == 0) mFaceCount = 1;
        return res;
    }

    public void saveFeatureStop() { FaceUnlockVendorImpl.getInstance().reset(); }

    public void compareStart() { if (!mIsInit) init(); FaceUnlockVendorImpl.getInstance().prepare(); }

    public int compare(byte[] img, int w, int h, int angle, boolean mirror, boolean live, int[] scores) {
        return FaceUnlockVendorImpl.getInstance().compare(img, w, h, angle, mirror, live, scores);
    }

    public void compareStop() { FaceUnlockVendorImpl.getInstance().reset(); }

    public void setDetectArea(int left, int top, int right, int bottom) {
        FaceUnlockVendorImpl.getInstance().setDetectArea(left, top, right, bottom);
    }

    public void deleteFeature(int id) {
        if (DEBUG) Log.i(TAG, "deleteFeature: " + id);
        FaceUnlockVendorImpl.getInstance().deleteFeature(id);
        mFaceCount = 0;
        try {
            File dir = new File(DATA_PATH);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().startsWith("restore_") || f.getName().startsWith("feature")) {
                            boolean deleted = f.delete();
                            if (DEBUG) Log.i(TAG, "Physically deleted: " + f.getName() + " Success=" + deleted);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete face template", e);
        }
        if (DEBUG) Log.i(TAG, "Force-flushing Megvii native memory to clear ghost faces...");
        FaceUnlockVendorImpl.getInstance().release();
        mIsInit = false;
        init();
    }
}