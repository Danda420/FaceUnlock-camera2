package ax.nd.faceunlock.camera;

import android.media.Image;
import android.util.Size;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

public final class Camera2Utils {

    private Camera2Utils() {}
    public static byte[] imageToNV21(Image image, int width, int height) {
        byte[] nv21 = new byte[width * height * 3 / 2];

        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];

        ByteBuffer yBuf = yPlane.getBuffer();
        ByteBuffer uBuf = uPlane.getBuffer();
        ByteBuffer vBuf = vPlane.getBuffer();

        int yRowStride    = yPlane.getRowStride();
        int uvRowStride   = vPlane.getRowStride();
        int uvPixelStride = vPlane.getPixelStride();

        for (int row = 0; row < height; row++) {
            int srcPos = row * yRowStride;
            if (srcPos + width <= yBuf.limit()) {
                yBuf.position(srcPos);
                yBuf.get(nv21, row * width, width);
            }
        }

        int uvOffset = width * height;
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int vIdx = row * uvRowStride + col * uvPixelStride;
                int uIdx = row * uPlane.getRowStride() + col * uPlane.getPixelStride();
                int dstIdx = uvOffset + row * width + col * 2;
                if (vIdx < vBuf.limit() && uIdx < uBuf.limit() && dstIdx + 1 < nv21.length) {
                    nv21[dstIdx]     = vBuf.get(vIdx);  // V
                    nv21[dstIdx + 1] = uBuf.get(uIdx);  // U
                }
            }
        }

        return nv21;
    }

    public static Size selectBestSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return null;

        for (Size s : sizes) {
            if (s.getWidth() == 640 && s.getHeight() == 480) return s;
        }

        Size best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (Size s : sizes) {
            int diff = Math.abs(s.getWidth() * s.getHeight() - 640 * 480);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = s;
            }
        }
        return best;
    }

    public static Size selectBestSquareSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return null;

        Size[] sorted = Arrays.copyOf(sizes, sizes.length);
        Arrays.sort(sorted, Comparator.comparingInt(s -> s.getWidth() * s.getHeight()));

        for (Size s : sorted) {
            if (s.getWidth() == s.getHeight() && s.getWidth() >= 480) return s;
        }

        for (Size s : sorted) {
            if (s.getWidth() == 640 && s.getHeight() == 480) return s;
        }
        return sorted[0];
    }

    public static void closePreviousSession(CameraRepository.CameraData data) {
        if (data.mCaptureSession != null) {
            try { data.mCaptureSession.close(); } catch (Exception ignored) {}
            data.mCaptureSession = null;
        }
        if (data.mImageReader != null) {
            try { data.mImageReader.close(); } catch (Exception ignored) {}
            data.mImageReader = null;
        }
        data.mPreviewRequestBuilder = null;
    }
}
