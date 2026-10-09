package app.qingyao.auth;

import android.graphics.Bitmap;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.EnumMap;
import java.util.Collections;

final class QrDecoder {
    static String decode(LuminanceSource source) throws NotFoundException {
        MultiFormatReader reader = new MultiFormatReader();
        EnumMap<DecodeHintType,Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        try { return reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText(); }
        catch (NotFoundException e) { return reader.decode(new BinaryBitmap(new HybridBinarizer(source.invert())), hints).getText(); }
        finally { reader.reset(); }
    }
    static String bitmap(Bitmap image) throws NotFoundException {
        int w=image.getWidth(),h=image.getHeight();int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);
        return decode(new RGBLuminanceSource(w,h,pixels));
    }
}
