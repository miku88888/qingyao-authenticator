package app.qingyao.auth;

import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 / RFC 4226. No networking or wall-clock adjustments. */
public final class Totp {
    private Totp() {}
    public static String normalize(String secret) {
        if (secret == null) throw new IllegalArgumentException("请输入网站提供的密钥");
        String s = secret.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!s.matches("[A-Z2-7]+={0,6}")) throw new IllegalArgumentException("密钥格式不正确：只接受 Base32 字母 A–Z 和数字 2–7");
        s = s.replaceAll("=+$", "");
        decode(s);
        return s;
    }
    public static byte[] decode(String base32) {
        String s = base32.replaceAll("\\s", "").toUpperCase(Locale.ROOT).replaceAll("=+$", "");
        if (s.isEmpty() || !s.matches("[A-Z2-7]+")) throw new IllegalArgumentException("密钥不是有效的 Base32 编码");
        int rem = s.length() % 8;
        if (rem == 1 || rem == 3 || rem == 6) throw new IllegalArgumentException("密钥长度不正确，请复制完整密钥");
        byte[] out = new byte[s.length() * 5 / 8];
        int buffer = 0, bits = 0, pos = 0;
        for (char ch : s.toCharArray()) {
            int value = ch >= 'A' && ch <= 'Z' ? ch - 'A' : ch - '2' + 26;
            buffer = (buffer << 5) | value; bits += 5;
            if (bits >= 8) { bits -= 8; out[pos++] = (byte)(buffer >> bits); }
        }
        if (out.length < 10) throw new IllegalArgumentException("密钥过短，请检查是否复制完整");
        if (bits > 0 && (buffer & ((1 << bits) - 1)) != 0) throw new IllegalArgumentException("密钥编码不完整");
        return out;
    }
    public static String generate(String secret, String algorithm, int digits, int period, long seconds) {
        if (digits != 6 && digits != 8) throw new IllegalArgumentException("仅支持 6 位或 8 位验证码");
        if (period < 1 || period > 300 || seconds < 0) throw new IllegalArgumentException("刷新周期不正确");
        if (!algorithm.equals("SHA1") && !algorithm.equals("SHA256") && !algorithm.equals("SHA512")) throw new IllegalArgumentException("不支持此算法");
        byte[] key = decode(secret);
        try {
            long count = seconds / period;
            byte[] message = new byte[8];
            for (int i = 7; i >= 0; i--) { message[i] = (byte) count; count >>>= 8; }
            Mac mac = Mac.getInstance("Hmac" + algorithm);
            mac.init(new SecretKeySpec(key, "Hmac" + algorithm));
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 15;
            int value = ((hash[offset] & 127) << 24) | ((hash[offset + 1] & 255) << 16)
                    | ((hash[offset + 2] & 255) << 8) | (hash[offset + 3] & 255);
            return String.format(Locale.ROOT, "%0" + digits + "d", value % (digits == 6 ? 1000000 : 100000000));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("无法生成验证码", e); }
        finally { Arrays.fill(key, (byte)0); }
    }
}

