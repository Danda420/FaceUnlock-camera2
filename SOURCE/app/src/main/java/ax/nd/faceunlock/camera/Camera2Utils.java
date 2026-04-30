package ax.nd.faceunlock.camera;

import android.media.Image;
import android.util.Size;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

public final class Camera2Utils {

    private Camera2Utils() {}
    public static byte[] imageToNV21(Image image, int width, int height) {
        try {
            Image.Plane[] planes = image.getPlanes();
            byte[] nv21 = new byte[width * height * 3 / 2];

            ByteBuffer yBuf = planes[0].getBuffer();
            int yRowStride = planes[0].getRowStride();
            int yPos = 0;

            byte[] yRow = new byte[yRowStride];
            for (int row = 0; row < height; row++) {
                yBuf.position(row * yRowStride);
                yBuf.get(yRow, 0, Math.min(yBuf.remaining(), width));
                System.arraycopy(yRow, 0, nv21, yPos, width);
                yPos += width;
            }

            ByteBuffer uBuf = planes[1].getBuffer();
            ByteBuffer vBuf = planes[2].getBuffer();
            int uvRowStride = planes[2].getRowStride();
            int uvPixelStride = planes[2].getPixelStride();
            int uPixelStride = planes[1].getPixelStride();

            int uvOffset = width * height;
            byte[] uRow = new byte[planes[1].getRowStride()];
            byte[] vRow = new byte[uvRowStride];

            for (int row = 0; row < height / 2; row++) {
                vBuf.position(row * uvRowStride);
                uBuf.position(row * planes[1].getRowStride());

                vBuf.get(vRow, 0, Math.min(vBuf.remaining(), uvRowStride));
                uBuf.get(uRow, 0, Math.min(uBuf.remaining(), planes[1].getRowStride()));

                for (int col = 0; col < width / 2; col++) {
                    int dstIdx = uvOffset + row * width + col * 2;
                    nv21[dstIdx] = vRow[col * uvPixelStride];     // V
                    nv21[dstIdx + 1] = uRow[col * uPixelStride];  // U
                }
            }

            return nv21;

        } catch (IllegalStateException e) {
            return null;
        }
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