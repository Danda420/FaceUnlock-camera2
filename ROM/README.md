# ROM Integration Guide

This directory contains the necessary vendor configurations, HAL, shared libraries, and initialization scripts to bake this implementation directly into an Android ROM.

## Guide: Adding Files to Your Build

To integrate these files into your ROM, you need to copy the contents of the `system` and `vendor` folders into your corresponding build targets.

1. **Copy Binaries and Libraries:** Ensure that the `lib64` files (like `libMegviiUnlock.so`, `libmegface.so`, etc.) and the model files are placed in their correct output directories within `/system/` and `/vendor/`.

2. **Init Scripts:** The script `android.hardware.biometrics.face-service.rc` must be included in your build. This script handles the necessary `post-fs-data` creation of the `/data/vendor_de/0/facedata` directory and registers the `vendor.face-hal-service` service.

3. **Properties:** Add all the necessary props inside `property.prop` file into any build.prop you want.

4. **SELinux Contexts:**
   You must assign the correct SELinux file context for the vendor AIDL executable to prevent access denials. Use `hal_allocator_default` for file contexts:
   ```plaintext
   /vendor/bin/hw/android\.hardware\.biometrics\.face-service u:object_r:hal_allocator_default_exec:s0
   ```
   If you're using this in oplus ROMs (OxygenOS,ColorOS,RealmeUI) add this to any .cil selinux file (preferrably vendor_sepolicy.cil):
   ```plaintext
   (allow hal_allocator_default system_server (binder (call)))
   (allow hal_allocator_default face_vendor_data_file (dir (search write getattr open create add_name remove_name)))
   (allow hal_allocator_default face_vendor_data_file (file (getattr open read write create unlink)))
   (allow system_server hal_allocator_default (unix_stream_socket (connectto)))

   ```
   You might need to address additional denials. And you can do it by:
   ```bash
   adb shell dmesg | grep "permissive=0"
   ```
   check for denials with hal_allocator_default in scontext and address it by using this template:
   ```plaintext
   (allow scontext tcontext (tclass (whats denied)))
   ;; for ex:
   (allow hal_allocator_default vendor_data_file (file (read open getattr)))
   ```
