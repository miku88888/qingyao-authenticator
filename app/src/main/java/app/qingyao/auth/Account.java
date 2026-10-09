package app.qingyao.auth;

import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;

public final class Account {
    public final String id, issuer, name, secret, algorithm;
    public final int digits, period;
    public Account(String id, String issuer, String name, String secret, String algorithm, int digits, int period) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.issuer = issuer == null ? "" : issuer.trim();
        this.name = name == null ? "" : name.trim();
        this.secret = Totp.normalize(secret);
        this.algorithm = algorithm.toUpperCase(Locale.ROOT).replace("-", "");
        this.digits = digits; this.period = period;
        if (this.name.isEmpty() && this.issuer.isEmpty()) throw new IllegalArgumentException("请输入账号名称或服务名称");
        if (this.name.length() > 200 || this.issuer.length() > 100 || this.secret.length() > 1024) throw new IllegalArgumentException("账号信息过长");
        Totp.generate(this.secret, this.algorithm, digits, period, 0);
    }
    public String title() { return issuer.isEmpty() ? name : issuer; }
    public String code(long seconds) { return Totp.generate(secret, algorithm, digits, period, seconds); }
    public JSONObject json() throws Exception {
        return new JSONObject().put("id", id).put("issuer", issuer).put("name", name).put("secret", secret)
                .put("algorithm", algorithm).put("digits", digits).put("period", period);
    }
    public static Account fromJson(JSONObject j) throws Exception {
        return new Account(j.getString("id"), j.getString("issuer"), j.getString("name"), j.getString("secret"),
                j.getString("algorithm"), j.getInt("digits"), j.getInt("period"));
    }
    private static String decode(String s) throws Exception { return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8"); }
    public static Account parse(String raw) {
        try {
            if (raw == null || raw.length() > 8192) throw new IllegalArgumentException("二维码内容无效");
            URI uri = new URI(raw.trim());
            if (!"otpauth".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("这不是身份验证器二维码，请使用网站两步验证页面的二维码");
            if (!"totp".equalsIgnoreCase(uri.getHost())) throw new IllegalArgumentException("此二维码不是基于时间的验证码（TOTP）");
            String label = uri.getRawPath();
            if (label == null || label.length() < 2) throw new IllegalArgumentException("二维码缺少账号名称");
            label = decode(label.substring(1));
            Map<String,String> params = new LinkedHashMap<>();
            String query = uri.getRawQuery();
            if (query == null) throw new IllegalArgumentException("二维码缺少密钥");
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length != 2) continue;
                String k = decode(parts[0]);
                if (params.containsKey(k)) throw new IllegalArgumentException("二维码包含重复参数");
                params.put(k, decode(parts[1]));
            }
            int colon = label.indexOf(':');
            String labelIssuer = colon >= 0 ? label.substring(0, colon).trim() : "";
            String issuer = params.containsKey("issuer") ? params.get("issuer") : labelIssuer;
            String name = colon >= 0 ? label.substring(colon + 1).trim() : label;
            return new Account(null, issuer, name, params.get("secret"), params.getOrDefault("algorithm", "SHA1"),
                    Integer.parseInt(params.getOrDefault("digits", "6")), Integer.parseInt(params.getOrDefault("period", "30")));
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("二维码格式不正确，请重新扫描"); }
    }
}

