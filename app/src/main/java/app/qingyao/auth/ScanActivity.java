package app.qingyao.auth;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.util.Size;
import android.view.*;
import android.widget.*;
import com.google.zxing.PlanarYUVLuminanceSource;
import java.nio.ByteBuffer;
import java.util.*;

public final class ScanActivity extends Activity {
    private TextureView preview;private TextView info;private CameraDevice camera;private CameraCaptureSession session;private ImageReader reader;
    private HandlerThread thread;private Handler cameraHandler;private volatile boolean opening=false,finished=false,resumed=false;private long lastFrame=0;
    private Size previewSize;private String cameraId;private int sensorOrientation;
    @Override public void onCreate(Bundle state){super.onCreate(state);Ui.protect(getWindow());getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Ui.GREEN);getWindow().getDecorView().setSystemUiVisibility(0);getWindow().setNavigationBarColor(Ui.GREEN);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.GREEN);root.setPadding(Ui.dp(this,24),Ui.dp(this,16),Ui.dp(this,24),Ui.dp(this,24));setContentView(root);
        LinearLayout header=Ui.row(this);TextView title=Ui.text(this,"扫描二维码",24,Color.WHITE,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Ui.Icon close=new Ui.Icon(this,"close",Color.WHITE);close.setPadding(Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,10));close.setContentDescription("关闭扫码");close.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);close.setOnClickListener(v->finish());header.addView(close,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));root.addView(header);Ui.space(root,12);
        root.addView(Ui.text(this,"对准网站提供的身份验证器二维码",13,Color.rgb(196,218,197),false));Ui.space(root,30);
        FrameLayout frame=new FrameLayout(this);frame.setBackground(Ui.shape(Color.BLACK,24,this));frame.setClipToOutline(true);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));preview=new TextureView(this);frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));frame.addView(new Guide(this),new FrameLayout.LayoutParams(-1,-1));
        Ui.space(root,22);info=Ui.text(this,"二维码只在手机本地识别",13,Color.rgb(196,218,197),false);info.setGravity(Gravity.CENTER);root.addView(info);Ui.space(root,22);
        root.addView(Ui.button(this,"无法扫描？手动输入密钥",false,()->{setResult(RESULT_OK,new Intent().putExtra("manual",true));finish();}));
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){openCamera();}public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){transform();}public boolean onSurfaceTextureDestroyed(SurfaceTexture s){return true;}public void onSurfaceTextureUpdated(SurfaceTexture s){}});
    }
    @Override public void onResume(){super.onResume();resumed=true;finished=false;thread=new HandlerThread("LocalQrCamera");thread.start();cameraHandler=new Handler(thread.getLooper());
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},100);else if(preview.isAvailable())openCamera();}
    @Override public void onPause(){resumed=false;closeCamera();if(thread!=null){thread.quitSafely();thread=null;}super.onPause();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==100&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){if(preview.isAvailable())openCamera();}else info.setText("未获得相机权限，可以返回从图片导入或手动输入");}
    private void openCamera(){if(!resumed||opening||camera!=null||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED||!preview.isAvailable())return;
        try{CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);cameraId=null;
            for(String id:manager.getCameraIdList()){CameraCharacteristics chars=manager.getCameraCharacteristics(id);if(cameraId==null)cameraId=id;if(Objects.equals(chars.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_BACK)){cameraId=id;break;}}
            if(cameraId==null){info.setText("设备没有可用相机，请使用图片导入或手动输入");return;}
            CameraCharacteristics chars=manager.getCameraCharacteristics(cameraId);sensorOrientation=chars.get(CameraCharacteristics.SENSOR_ORIENTATION);StreamConfigurationMap map=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            previewSize=choose(map.getOutputSizes(SurfaceTexture.class));Size analysis=choose(map.getOutputSizes(ImageFormat.YUV_420_888));
            reader=ImageReader.newInstance(analysis.getWidth(),analysis.getHeight(),ImageFormat.YUV_420_888,2);reader.setOnImageAvailableListener(this::analyse,cameraHandler);opening=true;
            manager.openCamera(cameraId,new CameraDevice.StateCallback(){public void onOpened(CameraDevice c){opening=false;if(!resumed){c.close();return;}camera=c;capture();}public void onDisconnected(CameraDevice c){c.close();camera=null;opening=false;}public void onError(CameraDevice c,int error){c.close();camera=null;opening=false;runOnUiThread(()->info.setText("相机无法打开，请返回使用图片导入"));}},cameraHandler);
        }catch(Exception e){opening=false;closeCamera();info.setText("相机暂不可用，请返回使用图片导入或手动输入");}
    }
    private Size choose(Size[] options){List<Size> all=new ArrayList<>(Arrays.asList(options));all.sort(Comparator.comparingLong(s->(long)s.getWidth()*s.getHeight()));Size selected=all.get(0);for(Size s:all)if(s.getWidth()<=1280&&s.getHeight()<=960)selected=s;return selected;}
    private void capture(){try{if(!resumed||reader==null||camera==null)return;SurfaceTexture texture=preview.getSurfaceTexture();if(texture==null)return;texture.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());runOnUiThread(this::transform);
        Surface surface=new Surface(texture);CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(surface);request.addTarget(reader.getSurface());request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        camera.createCaptureSession(Arrays.asList(surface,reader.getSurface()),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){if(!resumed||camera==null){s.close();return;}session=s;try{s.setRepeatingRequest(request.build(),null,cameraHandler);}catch(Exception e){runOnUiThread(()->info.setText("相机预览失败，请返回从图片导入"));}}public void onConfigureFailed(CameraCaptureSession s){runOnUiThread(()->info.setText("相机预览失败，请返回从图片导入"));}},cameraHandler);
        }catch(Exception e){runOnUiThread(()->info.setText("相机暂不可用，请返回从图片导入"));}}
    private void transform(){if(previewSize==null||preview.getWidth()==0)return;
        int rotation=getWindowManager().getDefaultDisplay().getRotation()*90;int relative=(sensorOrientation-rotation+360)%360;
        float w=preview.getWidth(),h=preview.getHeight();boolean swap=relative==90||relative==270;float bw=swap?previewSize.getHeight():previewSize.getWidth(),bh=swap?previewSize.getWidth():previewSize.getHeight();
        float scale=Math.max(w/bw,h/bh);Matrix matrix=new Matrix();matrix.setScale(bw*scale/w,bh*scale/h,w/2,h/2);matrix.postRotate(-rotation,w/2,h/2);preview.setTransform(matrix);
    }
    private void analyse(ImageReader source){Image image=null;try{image=source.acquireLatestImage();if(image==null||finished||!resumed)return;long now=SystemClock.elapsedRealtime();if(now-lastFrame<180)return;lastFrame=now;
        int w=image.getWidth(),h=image.getHeight();Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();int stride=plane.getRowStride(),pixel=plane.getPixelStride();byte[] luminance=new byte[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)luminance[y*w+x]=buffer.get(y*stride+x*pixel);
        String raw=QrDecoder.decode(new PlanarYUVLuminanceSource(luminance,w,h,0,0,w,h,false));
        try{Account.parse(raw);}catch(IllegalArgumentException e){runOnUiThread(()->info.setText(e.getMessage()));return;}
        finished=true;runOnUiThread(()->{if(resumed){setResult(RESULT_OK,new Intent().putExtra("otp",raw));finish();}});
    }catch(Exception ignored){}finally{if(image!=null)image.close();}}
    private void closeCamera(){if(session!=null){session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(reader!=null){reader.close();reader=null;}opening=false;}
    static final class Guide extends View {
        Paint p=new Paint(3);Guide(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){float side=Math.min(getWidth(),getHeight())*.73f,left=(getWidth()-side)/2,top=(getHeight()-side)/2;p.setColor(0x44000000);p.setStyle(Paint.Style.FILL);
            c.drawRect(0,0,getWidth(),top,p);c.drawRect(0,top,left,top+side,p);c.drawRect(left+side,top,getWidth(),top+side,p);c.drawRect(0,top+side,getWidth(),getHeight(),p);
            p.setColor(Color.rgb(203,231,188));p.setStrokeWidth(Ui.dp(getContext(),3));p.setStrokeCap(Paint.Cap.ROUND);
            for(float x:new float[]{left,left+side})for(float y:new float[]{top,top+side}){float dx=x==left?1:-1,dy=y==top?1:-1;c.drawLine(x,y,x+Ui.dp(getContext(),24)*dx,y,p);c.drawLine(x,y,x,y+Ui.dp(getContext(),24)*dy,p);}
        }
    }
}
