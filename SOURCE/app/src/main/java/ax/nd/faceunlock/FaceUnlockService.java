package ax.nd.faceunlock;

import static ax.nd.faceunlock.util.Util.getSystemContext;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import ax.nd.faceunlock.camera.CameraFaceAuthController;
import ax.nd.faceunlock.camera.CameraFaceEnrollController;
import ax.nd.faceunlock.util.Util;
import ax.nd.faceunlock.vendor.FacePPImpl;

public class FaceUnlockService {
    private static final String TAG = "FaceUnlockService";
    public static final boolean DEBUG = Util.getBooleanSystemProperty("persist.sys.facehal.verbose", false);
    private static final String SOCKET_PATH  = "/dev/socket/vendor_face_hal";
    private static final int RETRY_DELAY_MS = 3000;
    private final Context mContext;
    private final FacePPImpl mFacePP;
    private final SurfaceTexture mDummyTexture;
    private final Surface mDummySurface;
    private static volatile Surface sEnrollSurface = null;
    // mCancelled: written by the reader thread, read by the worker thread.
    // volatile guarantees visibility; both reader and worker run on separate threads.
    private volatile boolean mCancelled = false;
    private volatile PrintWriter mWriter;
    private final BlockingQueue<String> mCommandQueue = new LinkedBlockingQueue<>();

    public FaceUnlockService(Context context) {
        mContext      = context;
        mFacePP       = new FacePPImpl(context);
        mDummyTexture = new SurfaceTexture(10);
        mDummySurface = new Surface(mDummyTexture);
    }

    public static void setEnrollSurface(Surface surface) {
        if (DEBUG) Log.i(TAG, "Successfully intercepted Enrollment Surface from FaceProvider!");
        sEnrollSurface = surface;
    }

    public static void startService() {
        final Context context = getSystemContext();
        if (context == null) {
            Log.e(TAG, "Cannot start FaceUnlockService: Context is null!");
            return;
        }

        new Thread(() -> {
            try {
                if (DEBUG) Log.i(TAG, "Initializing Socket-based FaceUnlockService...");

                Context spoofedContext = context;
                try {
                    spoofedContext = context.createPackageContext("com.android.systemui", 0);
                    if (DEBUG) Log.i(TAG, "Successfully spoofed SystemUI context");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to spoof SystemUI context", e);
                }

                FaceUnlockService service = new FaceUnlockService(spoofedContext);
                service.run();
            } catch (Throwable t) {
                Log.e(TAG, "Fatal error in FaceUnlockService thread", t);
            }
        }, "FaceUnlockService-Main").start();
    }

    public void run() {
        mFacePP.init();
        if (DEBUG) Log.i(TAG, "FaceUnlockService ready, connecting to HAL socket…");

        while (true) {
            try {
                connServe();
            } catch (Exception e) {
                Log.e(TAG, "Socket session ended, retrying in " + RETRY_DELAY_MS + " ms", e);
            }
            try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
        }
    }

    private void connServe() throws Exception {
        LocalSocket socket = new LocalSocket();
        socket.connect(new LocalSocketAddress(SOCKET_PATH,
                LocalSocketAddress.Namespace.FILESYSTEM));
        if (DEBUG) Log.i(TAG, "Connected to HAL socket at " + SOCKET_PATH);

        mWriter = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(socket.getInputStream()));

        mCancelled = false;
        mCommandQueue.clear();

        Thread worker = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String cmd = mCommandQueue.take();

                    if (mCancelled) {
                        if (DEBUG) Log.i(TAG, "Ignoring queued command (" + cmd + ") due to previous CANCEL");
                        mCommandQueue.clear();
                        mCancelled = false;
                        continue;
                    }

                    switch (cmd) {
                        case "AUTH":
                            mCancelled = false;
                            mWriter.println(runAuth() ? "1" : "-1");
                            break;
                        case "ENROLL":
                            mCancelled = false;
                            mWriter.println(runEnroll() ? "1" : "-1");
                            break;
                        case "REMOVE":
                            mFacePP.deleteFeature(1);
                            mWriter.println("1");
                            break;
                        default:
                            Log.w(TAG, "Worker: unknown command: " + cmd);
                            break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "faceunlock-worker");
        worker.start();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String cmd = line.trim();
                if (cmd.isEmpty()) continue;
                if ("CANCEL".equals(cmd)) {
                    if (DEBUG) Log.i(TAG, "CANCEL received — aborting current operation");
                    mCancelled = true;
                } else {
                    if (DEBUG) Log.i(TAG, "Command queued: " + cmd);
                    mCommandQueue.put(cmd);
                }
            }
        } finally {
            worker.interrupt();
            socket.close();
            if (DEBUG) Log.i(TAG, "Socket session closed");
        }
    }

    private boolean runAuth() {
        if (DEBUG) Log.i(TAG, "Starting authentication");
        final CountDownLatch latch  = new CountDownLatch(1);
        final int[]          result = {-1};

        mFacePP.compareStart();

        CameraFaceAuthController controller = new CameraFaceAuthController(mContext,
                new CameraFaceAuthController.ServiceCallback() {
                    @Override
                    public int handlePreviewData(byte[] data, int w, int h) {
                        if (mCancelled) { latch.countDown(); return -1; }
                        int[] scores = new int[20];
                        int res = mFacePP.compare(data, w, h, 0, true, true, scores);
                        if (res == 0) { result[0] = 1; latch.countDown(); }
                        return res;
                    }
                    @Override
                    public void setDetectArea(int width, int height) {
                        mFacePP.setDetectArea(0, 0, height, width);
                    }
                    @Override public void onTimeout(boolean b) { latch.countDown(); }
                    @Override public void onCameraError()       { latch.countDown(); }
                });

        controller.start(1, mDummyTexture);
        waitForResult(latch, 4000);
        controller.stop();
        mFacePP.compareStop();

        if (DEBUG) Log.i(TAG, "Authentication result: " + result[0]);
        return result[0] == 1;
    }

    private boolean runEnroll() {
        if (DEBUG) Log.i(TAG, "Starting enrollment");
        final CountDownLatch latch  = new CountDownLatch(1);
        final int[]          result = {-1};

        Surface targetSurface = (sEnrollSurface != null && sEnrollSurface.isValid()) ? sEnrollSurface : mDummySurface;

        mFacePP.saveFeatureStart();

        CameraFaceEnrollController.getInstance(mContext).start(
                new CameraFaceEnrollController.CameraCallback() {
                    final byte[] mFeature  = new byte[10000];
                    final byte[] mFaceData = new byte[40000];
                    final int[]  mOutId    = new int[1];

                    @Override
                    public int handleSaveFeature(byte[] data, int w, int h, int angle) {
                        if (mCancelled) { latch.countDown(); return -1; }
                        return mFacePP.saveFeature(data, w, h, angle, true,
                                mFeature, mFaceData, mOutId);
                    }
                    @Override
                    public void handleSaveFeatureResult(int res) {
                        if (res == 0) { result[0] = 1; latch.countDown(); }
                    }
                    @Override public void onFaceDetected() {}
                    @Override public void onTimeout()      { latch.countDown(); }
                    @Override public void onCameraError()  { latch.countDown(); }
                    @Override
                    public void setDetectArea(int width, int height) {
                        mFacePP.setDetectArea(0, 0, height, width);
                    }
                },
                1, targetSurface);

        waitForResult(latch, 15000);
        CameraFaceEnrollController.getInstance(mContext).stop(null);
        mFacePP.saveFeatureStop();

        sEnrollSurface = null;

        if (DEBUG) Log.i(TAG, "Enrollment result: " + result[0]);
        return result[0] == 1;
    }

    private void waitForResult(CountDownLatch latch, int maxMs) {
        final long deadline = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < deadline) {
            if (mCancelled) return;
            try {
                if (latch.await(50, TimeUnit.MILLISECONDS)) return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}