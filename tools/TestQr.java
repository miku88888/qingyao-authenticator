import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
public class TestQr {
    public static void main(String[] args)throws Exception {
        String uri="otpauth://totp/OpenAI%3Ademo%40example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=OpenAI";
        BitMatrix matrix=new MultiFormatWriter().encode(uri,BarcodeFormat.QR_CODE,800,800);
        BufferedImage image=new BufferedImage(800,800,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<800;y++)for(int x=0;x<800;x++)image.setRGB(x,y,matrix.get(x,y)?0:0xffffff);
        ImageIO.write(image,"png",new File(args[0]));
    }
}
