package ax.nd.faceunlock;

import static ax.nd.faceunlock.util.Util.getSystemContext;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;

import java.util.concurrent.CountDownLatch;

import ax.nd.faceunlock.camera.CameraFaceAuthController;
import ax.nd.faceunlock.camera.CameraFaceEnrollController;
import ax.nd.faceunlock.util.Util;
import ax.nd.faceunlock.vendor.FacePPImpl;

public class FaceUnlockService {
    private static String TAG = "FaceUnlockService";
    public static final Boolean DEBUG = Util.getBooleanSystemProperty("persist.sys.facehal.verbose", false);
    private static FacePPImpl sFacePP;
    private static Surface sEnrollSurface = null;

    public static void setEnrollSurface(Surface surface) {
        if (DEBUG) Log.i(TAG, "Successfully intercepted Enrollment Surface from FaceProvider!");
        sEnrollSurface = surface;
    }

    @SuppressLint("NewApi")
    public static void startService() {
        final Context context = getSystemContext();
        new Thread(() -> {
            try {
                sFacePP = new FacePPImpl(context);
                sFacePP.init();

                SurfaceTexture dummyTexture = new SurfaceTexture(10);
                Surface dummySurface = new Surface(dummyTexture);

                Util.setSystemProperty("debug.face-hal.command", "0");
                Util.setSystemProperty("debug.face-hal.result", "0");

                if (DEBUG) Log.i(TAG, "Property Polling Bridge started successfully!");

                while (true) {
                    int cmd = Util.getIntSystemProperty("debug.face-hal.command", 0);

                    if (cmd != -1 && cmd != 0 && cmd != 4) {
                        cmdHandler(cmd, dummySurface, dummyTexture, context);
                    }
                    Thread.sleep(50);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Property bridge error", t);
            }
        }).start();
    }

    private static void cmdHandler(int cmd, Surface dummySurface, SurfaceTexture dummyTexture, Context context) {
        if (DEBUG) Log.i(TAG, "Received Command: " + cmd);
        switch (cmd) {
            case 1: {
                Util.setSystemProperty("debug.face-hal.command", "0");

                final CountDownLatch latch = new CountDownLatch(1);
                final int[] result = { -1 };

                Surface targetSurface = (sEnrollSurface != null && sEnrollSurface.isValid()) ? sEnrollSurface : dummySurface;

                sFacePP.saveFeatureStart();
                CameraFaceEnrollController.getInstance(context).start(new CameraFaceEnrollController.CameraCallback() {
                    byte[] mFeature = new byte[10000];
                    byte[] mFaceData = new byte[40000];
                    int[] mOutId = new int[1];

                    @Override
                    public int handleSaveFeature(byte[] data, int width, int height, int angle) {
                        return sFacePP.saveFeature(data, width, height, angle, true, mFeature, mFaceData, mOutId);
                    }

                    @Override
                    public void handleSaveFeatureResult(int res) {
                        if (res == 0) {
                            result[0] = 1;
                            latch.countDown();
                        }
                    }

                    @Override public void onFaceDetected() {}
                    @Override public void onTimeout() { latch.countDown(); }
                    @Override public void onCameraError() { latch.countDown(); }
                    @Override public void setDetectArea(android.hardware.Camera.Size size) {
                        sFacePP.setDetectArea(0, 0, size.height, size.width);
                    }
                }, 1, targetSurface);

                int status = waitForFace(latch, 15000);

                CameraFaceEnrollController.getInstance(context).stop(null);
                sFacePP.saveFeatureStop();
                sEnrollSurface = null;

                if (status == 1 && result[0] == 1) {
                    Util.setSystemProperty("debug.face-hal.result", "1");
                }
                break;
            }
            case 2: {
                Util.setSystemProperty("debug.face-hal.command", "0");

                final CountDownLatch latch = new CountDownLatch(1);
                final int[] result = { -1 };

                sFacePP.compareStart();
                CameraFaceAuthController authController = new CameraFaceAuthController(context, new CameraFaceAuthController.ServiceCallback() {
                    @Override
                    public int handlePreviewData(byte[] data, int width, int height) {
                        int[] scores = new int[20];
                        int res = sFacePP.compare(data, width, height, 0, true, true, scores);
                        if (res == 0) {
                            result[0] = 1;
                            latch.countDown();
                        }
                        return res;
                    }

                    @Override public void setDetectArea(android.hardware.Camera.Size size) {
                        sFacePP.setDetectArea(0, 0, size.height, size.width);
                    }
                    @Override public void onTimeout(boolean b) { latch.countDown(); }
                    @Override public void onCameraError() { latch.countDown(); }
                });

                authController.start(1, dummyTexture);

                int status = waitForFace(latch, 4000);

                authController.stop();
                sFacePP.compareStop();

                if (status == 1 && result[0] == 1) {
                    Util.setSystemProperty("debug.face-hal.result", "1");
                }
                break;
            }
            case 3:
                Util.setSystemProperty("debug.face-hal.command", "0");
                sFacePP.deleteFeature(1);
                Util.setSystemProperty("debug.face-hal.result", "1");
                break;
            default:
                Log.w(TAG, "Unknown command received: " + cmd);
                Util.setSystemProperty("debug.face-hal.command", "0");
                break;
        }
    }

    private static int waitForFace(CountDownLatch latch, int maxTimeMs) {
        int timeMs = 0;
        final int sleepInterval = 10;

        while (timeMs < maxTimeMs) {
            if (latch.getCount() == 0) return 1;

            if (Util.getIntSystemProperty("debug.face-hal.command", 0) == 4) {
                if (DEBUG) Log.i(TAG, "Received CANCEL command! Force-aborting camera...");
                Util.setSystemProperty("debug.face-hal.command", "0");
                sEnrollSurface = null;
                return -1;
            }

            // TIER 2 ACTIVE POLLING: 10ms for instant unlock speeds (0 battery impact)
            try { Thread.sleep(sleepInterval); } catch (Exception e) {}
            timeMs += sleepInterval;
        }
        return 0;
    }
}