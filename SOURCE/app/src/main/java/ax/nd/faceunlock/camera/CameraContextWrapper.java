package ax.nd.faceunlock.camera;

import android.content.Context;
import android.content.ContextWrapper;

public class CameraContextWrapper extends ContextWrapper {
    public CameraContextWrapper(Context base) {
        super(base);
    }

    @Override
    public String getPackageName() {
        return "com.android.systemui";
    }

    @Override
    public String getOpPackageName() {
        return "com.android.systemui";
    }

    @Override
    public String getAttributionTag() {
        return "FaceUnlock";
    }
}