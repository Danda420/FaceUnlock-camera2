**A custom Face Unlock implementation and integration toolkit for Android, bridging low-level hardware interfaces with a custom system service via a custom HAL.**

Previous implementations were strictly limited to specific OEM ROMs because they relied on extensive smali hooking, requiring developers to decompile `services.jar` and completely remake complex methods inside files like `FaceService.smali`, `FaceProvider.smali`, and `TestHal.smali`. 
This revised project overhauls that legacy approach by abstracting the heavy lifting into an independent custom system service and a native Custom HAL daemon. By decoupling the architecture, framework patching is reduced to basic service-start hook and a hook for showing face enroll preview (which is completely optional), making the installation vastly simpler and transforming the project into a universal solution capable of bringing Face Unlock to a wide variety of OEM ROMs.

---

## How It Works:
The core architecture operates by acting as a translation layer between Android's native biometrics framework and a custom background service written in Java. It uses a Custom HAL that registers itself as the official Face HAL (`android.hardware.biometrics.face.IFace/default`). 

This Custom HAL acts as a controller that delegates the actual biometric processing (via Megvii/FacePP) to the `ax.nd.faceunlock` service. 

---
### The System Properties Bridge
To communicate between the native Custom HAL daemon and the custom system service, the system utilizes Android System Properties as a seamless bridge:
1. **Command Execution:** When the Android OS requests a biometric operation (like unlocking the phone or adding a face), the AIDL `ISession` implementation sets `debug.face-hal.command` to a specific state:
   - `1` : Start Enrollment
   - `2` : Start Authentication
   - `3` : Remove Enrollments
   - `4` : Abort / Cancel Operation
2. **Polling for Results:** The HAL backend simultaneously sets `debug.face-hal.result` to `0` and begins polling it. 
3. **Java Processing:** The service listens for these property changes, opens the camera, runs the FacePP algorithms, and eventually writes the outcome back to `debug.face-hal.result` (`1` for success, `-1` for failure).
4. **Binder Callbacks:** Once the HAL reads the result, it fires the appropriate Binder callbacks (`onAuthenticationSucceeded`, `onEnrollmentProgress`, etc.) back to the Android OS.

---
## Project Structure
* **`SOURCE/`**: Contains the main Android service (`ax.nd.faceunlock`) source code, including the camera controllers, listeners, and the Megvii FacePP vendor implementation.
* **`ROM/`**: Contains the pre-compiled binaries, shared libraries (`.so`), XML manifests, and initialization scripts required to bundle the face unlock HAL directly into an Android ROM build.
* **`AUTOPATCH/`**: Contains the simplified automated scripts and utilities to patch the Android framework (`services.jar`) to seamlessly hook the custom FaceUnlock service into the system's biometrics stack.

---
## Guide
First off, import all of the necessary files and props, you can check [this readme](ROM/README.md) for guide.
For `services.jar` patches, you can either do it with auto patcher using [this guide](AUTOPATCH/README.md) or just do it manually like this if you don't have linux or WSL installed:
### 1. FaceService
Open `services.jar` and locate the `FaceService` class. Find the `onStart` method. method structure will vary so no need to panic.
```smali
.method public onStart()V
    .locals 2

    .line 893
    invoke-virtual {p0}, Lcom/android/server/biometrics/sensors/face/FaceService;->getWrapper()Lcom/android/server/biometrics/sensors/face/IFaceServiceWrapper;

    move-result-object v0

    invoke-interface {v0}, Lcom/android/server/biometrics/sensors/face/IFaceServiceWrapper;->getExtImpl()Lcom/android/server/biometrics/sensors/face/IFaceServiceExt;

    move-result-object v0

    invoke-interface {v0}, Lcom/android/server/biometrics/sensors/face/IFaceServiceExt;->init()V

    .line 895
    const-string v0, "face"

    iget-object v1, p0, Lcom/android/server/biometrics/sensors/face/FaceService;->mServiceWrapper:Lcom/android/server/biometrics/sensors/face/FaceService$FaceServiceWrapper;

    invoke-virtual {p0, v0, v1}, Lcom/android/server/biometrics/sensors/face/FaceService;->publishBinderService(Ljava/lang/String;Landroid/os/IBinder;)V

    .line 896
    invoke-static {}, Lax/nd/faceunlock/FaceUnlockService;->startService()V # <---- add this line here at the faarr bottom to start the custom system service.

    return-void
.end method
```
### 2. Optional FaceProvider patch
This next method is optional because faceunlock enrollment and autentication will still work properly without this patch even though when scanning it'll probably show nothing (blank front camera). so you can skip this if you can't find the method or register value.

Locate `FaceProvider` class in `services.jar` and find `scheduleEnroll` method And patch it like this:
```smali
.method public scheduleEnroll(ILandroid/os/IBinder;[BILandroid/hardware/face/IFaceServiceReceiver;Ljava/lang/String;[ILandroid/view/Surface;ZLandroid/hardware/face/FaceEnrollOptions;)J
    .locals 15
    .param p1, "sensorId"    # I
    .param p2, "token"    # Landroid/os/IBinder;
    .param p3, "hardwareAuthToken"    # [B
    .param p4, "userId"    # I
    .param p5, "receiver"    # Landroid/hardware/face/IFaceServiceReceiver;
    .param p6, "opPackageName"    # Ljava/lang/String;
    .param p7, "disabledFeatures"    # [I
    .param p8, "previewSurface"    # Landroid/view/Surface;
    .param p9, "debugConsent"    # Z
    .param p10, "options"    # Landroid/hardware/face/FaceEnrollOptions;

    invoke-static/range {p8 .. p8}, Lax/nd/faceunlock/FaceUnlockService;->setEnrollSurface(Landroid/view/Surface;)V # <---- add this at the far top of the method after the method declaration and stuff. use register with surface view for the arg, in this example, its p8 because of ".param p8 "previewSurface"    # Landroid/view/Surface;".

    .line 532
    iget-object v0, p0, Lcom/android/server/biometrics/sensors/face/aidl/FaceProvider;->mRequestCounter:Ljava/util/concurrent/atomic/AtomicLong;

    invoke-virtual {v0}, Ljava/util/concurrent/atomic/AtomicLong;->incrementAndGet()J

    move-result-wide v9
    # rest of the method
```

---
## Credits and Contributions
- [**UniversalAuth**](https://github.com/null-dev/UniversalAuth)
- [**ryanistr**](https://github.com/ryanistr/OPlusFace)
- **Motorola**