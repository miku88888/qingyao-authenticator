package app.qingyao.auth;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned binary backup: magic, salt, IV, authenticated ciphertext. */
public final class BackupCrypto {
    private static final byte[] MAGIC = {'Q','I','N','G','Y','A','O',1};
    private static final int ITERATIONS = 210000;
    private BackupCrypto() {}
    private static byte[] key(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    public static byte[] encrypt(byte[] plain, char[] password) throws Exception {
        byte[] salt = new byte[16], iv = new byte[12];
        SecureRandom random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(iv);
        byte[] k = key(password, salt);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(MAGIC);
            byte[] encrypted = cipher.doFinal(plain);
            return ByteBuffer.allocate(36 + encrypted.length).put(MAGIC).put(salt).put(iv).put(encrypted).array();
        } finally { Arrays.fill(k, (byte)0); }
    }
    public static byte[] decrypt(byte[] data, char[] password) throws Exception {
        if (data.length < 52 || data.length > 2 * 1024 * 1024 || !Arrays.equals(MAGIC, Arrays.copyOf(data, 8)))
            throw new IllegalArgumentException("请选择清钥导出的 .qingyao 加密备份");
        ByteBuffer b = ByteBuffer.wrap(data); b.position(8);
        byte[] salt = new byte[16], iv = new byte[12], ciphertext = new byte[data.length - 36];
        b.get(salt).get(iv).get(ciphertext);
        byte[] k = key(password, salt);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(MAGIC);
            return cipher.doFinal(ciphertext);
        } finally { Arrays.fill(k, (byte)0); }
    }
}

