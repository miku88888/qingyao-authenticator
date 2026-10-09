package app.qingyao.auth;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public final class CoreTest {
    static int tests=0;
    static void eq(Object a,Object b){tests++;if(!a.equals(b))throw new AssertionError("Mismatch: "+a+" / "+b);}
    static void rejects(Runnable r){tests++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection");}
    static String base32(String text){String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";StringBuilder s=new StringBuilder();int buffer=0,bits=0;for(byte b:text.getBytes(StandardCharsets.US_ASCII)){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=5){bits-=5;s.append(alphabet.charAt((buffer>>bits)&31));}}if(bits>0)s.append(alphabet.charAt((buffer<<(5-bits))&31));return s.toString();}
    public static void main(String[] args)throws Exception {
        long[] times={59,1111111109L,1111111111L,1234567890L,2000000000L,20000000000L};
        String[][] vectors={{"94287082","07081804","14050471","89005924","69279037","65353130"},{"46119246","68084774","67062674","91819424","90698825","77737706"},{"90693936","25091201","99943326","93441116","38618901","47863826"}};
        String[] keys={"12345678901234567890","12345678901234567890123456789012","1234567890123456789012345678901234567890123456789012345678901234"};String[] algos={"SHA1","SHA256","SHA512"};
        for(int a=0;a<3;a++)for(int t=0;t<times.length;t++)eq(Totp.generate(base32(keys[a]),algos[a],8,30,times[t]),vectors[a][t]);
        eq(Totp.generate(base32(keys[0]),"SHA1",6,30,59),"287082");
        eq(Totp.normalize("jbsw y3dp ehpk 3pxp"),"JBSWY3DPEHPK3PXP");
        eq(Totp.generate(base32(keys[0]),"SHA1",6,30,30),Totp.generate(base32(keys[0]),"SHA1",6,30,59));
        rejects(()->Totp.normalize("secret!"));rejects(()->Totp.normalize("AAAA"));rejects(()->Totp.generate(base32(keys[0]),"MD5",6,30,59));rejects(()->Totp.generate(base32(keys[0]),"SHA1",7,30,59));
        Account a=Account.parse("otpauth://totp/OpenAI%3Atest%2Btag%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=OpenAI");eq(a.issuer,"OpenAI");eq(a.name,"test+tag@example.com");eq(a.period,30);eq(a.digits,6);
        Account advanced=Account.parse("otpauth://totp/Test%3Aname?secret="+base32(keys[1])+"&algorithm=SHA256&digits=8&period=60");eq(advanced.algorithm,"SHA256");eq(advanced.period,60);
        rejects(()->Account.parse("https://example.com"));rejects(()->Account.parse("otpauth://hotp/Test?secret=JBSWY3DPEHPK3PXP"));rejects(()->Account.parse("otpauth://totp/Test?secret=JBSWY3DPEHPK3PXP&secret=JBSWY3DPEHPK3PXP"));rejects(()->Account.parse("otpauth://totp/Test?secret=JBSWY3DPEHPK3PXP&period=0"));
        char[] password="testing-password-123".toCharArray();byte[] plain="backup test; this is public test data".getBytes(StandardCharsets.UTF_8);byte[] encrypted=BackupCrypto.encrypt(plain,password);eq(Arrays.equals(plain,BackupCrypto.decrypt(encrypted,password)),true);
        byte[] second=BackupCrypto.encrypt(plain,password);eq(Arrays.equals(encrypted,second),false);
        tests++;try{BackupCrypto.decrypt(encrypted,"wrong password".toCharArray());throw new AssertionError("Bad password accepted");}catch(javax.crypto.AEADBadTagException expected){}
        encrypted[encrypted.length-1]^=1;tests++;try{BackupCrypto.decrypt(encrypted,password);throw new AssertionError("Tampering accepted");}catch(javax.crypto.AEADBadTagException expected){}
        if(args.length>0){byte[] deviceBackup=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0]));byte[] decoded=BackupCrypto.decrypt(deviceBackup,"backup-test-password".toCharArray());org.json.JSONObject root=new org.json.JSONObject(new String(decoded,StandardCharsets.UTF_8));eq(root.getJSONArray("accounts").length(),2);Arrays.fill(decoded,(byte)0);System.out.println("PASS: Android-exported backup decrypts independently and contains 2 accounts");}
        System.out.println("PASS: "+tests+" core checks (18 RFC vectors, URI validation, backup integrity)");
    }
}
