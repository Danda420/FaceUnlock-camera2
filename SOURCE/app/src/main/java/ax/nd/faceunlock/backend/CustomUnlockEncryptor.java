package ax.nd.faceunlock.backend;

import static ax.nd.faceunlock.FaceUnlockService.DEBUG;

import android.util.Log;
import com.megvii.facepp.sdk.UnlockEncryptor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CustomUnlockEncryptor implements UnlockEncryptor {
    private static final String TAG = CustomUnlockEncryptor.class.getSimpleName();
    private static final String SEED_FILE = "/data/system/facedata_system/aes.key";
    private static final int PROFILE_KEY_IV_SIZE = 12;
    private SecretKey secretKey;

    public CustomUnlockEncryptor() {
        loadOrGenerateSeed();
    }

    private void loadOrGenerateSeed() {
        File file = new File(SEED_FILE);
        try {
            if (file.exists() && file.length() > 0) {
                byte[] keyBytes = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(keyBytes);
                }
                secretKey = new SecretKeySpec(keyBytes, "AES");
                if (DEBUG) Log.i(TAG, "Loaded existing AES key from file");
            } else {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256, new SecureRandom());
                secretKey = keyGen.generateKey();

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(secretKey.getEncoded());
                }
                if (DEBUG) Log.i(TAG, "Generated and saved new AES key to file");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load or generate seed", e);
        }
    }

    @Override
    public byte[] encrypt(byte[] bArr) {
        if (bArr == null || secretKey == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] doFinal = cipher.doFinal(bArr);
            byte[] iv = cipher.getIV();

            if (iv.length == PROFILE_KEY_IV_SIZE) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bos.write(iv);
                bos.write(doFinal);
                return bos.toByteArray();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in encrypt: " + e.getMessage());
        }
        return new byte[0];
    }

    @Override
    public byte[] decrypt(byte[] bArr) {
        if (bArr == null || secretKey == null || bArr.length <= PROFILE_KEY_IV_SIZE) return null;
        try {
            byte[] iv = Arrays.copyOfRange(bArr, 0, PROFILE_KEY_IV_SIZE);
            byte[] encrypted = Arrays.copyOfRange(bArr, PROFILE_KEY_IV_SIZE, bArr.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            Log.e(TAG, "Exception in decrypt: " + e.getMessage());
        }
        return new byte[0];
    }
}