package app.qingyao.auth;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Vault {
    private static final String ALIAS = "qingyao.vault.v1";
    private final AtomicFile file;
    public Vault(Context context) { file = new AtomicFile(new File(context.getNoBackupFilesDir(), "accounts.enc")); }
    private SecretKey key(boolean allowCreate) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        if (!allowCreate) throw new IllegalStateException("本机加密密钥不可用");
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    public static byte[] serialize(List<Account> accounts) throws Exception {
        JSONArray array = new JSONArray(); for (Account a : accounts) array.put(a.json());
        return new JSONObject().put("version", 1).put("accounts", array).toString().getBytes(StandardCharsets.UTF_8);
    }
    public static List<Account> deserialize(byte[] data) throws Exception {
        JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
        if (root.getInt("version") != 1) throw new IllegalArgumentException("不支持此备份版本");
        JSONArray array = root.getJSONArray("accounts");
        if (array.length() > 500) throw new IllegalArgumentException("最多支持 500 个账号");
        List<Account> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) out.add(Account.fromJson(array.getJSONObject(i)));
        return out;
    }
    public List<Account> load() throws Exception {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile().getPath() + ".bak").exists()) return new ArrayList<>();
        byte[] data = file.readFully();
        if (data.length < 29 || data[0] != 1) throw new IllegalStateException("本地保险库格式损坏");
        byte[] iv = java.util.Arrays.copyOfRange(data, 1, 13);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
        cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        byte[] plain = cipher.doFinal(data, 13, data.length - 13);
        try { return deserialize(plain); } finally { java.util.Arrays.fill(plain, (byte)0); }
    }
    public void save(List<Account> accounts) throws Exception {
        byte[] plain = serialize(accounts);
        byte[] encrypted;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
            cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
            encrypted = cipher.doFinal(plain);
        } finally { java.util.Arrays.fill(plain, (byte)0); }
        byte[] data = ByteBuffer.allocate(13 + encrypted.length).put((byte)1).put(cipher.getIV()).put(encrypted).array();
        FileOutputStream stream = null;
        try { stream = file.startWrite(); stream.write(data); file.finishWrite(stream); }
        catch (Exception e) { if (stream != null) file.failWrite(stream); throw e; }
    }
}
