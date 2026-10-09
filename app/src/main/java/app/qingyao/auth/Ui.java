package app.qingyao.auth;

import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class Ui {
    static void protect(Window window) {
        if ((window.getContext().getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    static final int BG = Color.rgb(245,247,244), INK = Color.rgb(28,48,39), GREEN = Color.rgb(20,63,50),
            MUTED = Color.rgb(112,128,119), LINE = Color.rgb(224,231,223), PALE = Color.rgb(231,239,227);
    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(int color, float radius, Context c) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius)); return d;
    }
    static GradientDrawable outline(int color, float radius, Context c) {
        GradientDrawable d = shape(color, radius, c); d.setStroke(dp(c,1), LINE); return d;
    }
    static TextView text(Context c, String s, float size, int color, boolean bold) {
        TextView v = new TextView(c); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("kern"); v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }
    static LinearLayout column(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    static void space(LinearLayout parent, int h) { View v = new View(parent.getContext()); parent.addView(v, new LinearLayout.LayoutParams(1, dp(parent.getContext(),h))); }
    static TextView button(Context c, String s, boolean primary, Runnable action) {
        TextView v = text(c, s, 15, primary ? Color.WHITE : GREEN, true);
        v.setGravity(Gravity.CENTER); v.setMinHeight(dp(c,54)); v.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));
        v.setBackground(primary ? shape(GREEN,18,c) : outline(Color.WHITE,18,c));
        v.setOnClickListener(w -> action.run()); return v;
    }
    static Dialog sheet(Context c, LinearLayout content) {
        Dialog d = new Dialog(c); ScrollView scroll = new ScrollView(c); scroll.setFillViewport(true);
        scroll.setBackground(shape(BG,28,c)); content.setPadding(dp(c,24),dp(c,26),dp(c,24),dp(c,30));
        scroll.addView(content); d.setContentView(scroll);
        Window w = d.getWindow(); if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent); protect(w);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); w.setGravity(Gravity.BOTTOM);
        }
        d.show(); if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if(c instanceof MainActivity)((MainActivity)c).track(d);
        return d;
    }
    static final class Icon extends View {
        private final String kind; private final Paint p = new Paint(3); private final int color;
        Icon(Context c, String kind, int color) { super(c); this.kind=kind; this.color=color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas original) {
            super.onDraw(original); Canvas c = original; c.save(); c.translate(getPaddingLeft(),getPaddingTop());
            c.scale((getWidth()-getPaddingLeft()-getPaddingRight())/24f,(getHeight()-getPaddingTop()-getPaddingBottom())/24f);
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            if (kind.equals("shield")) {
                Path path=new Path(); path.moveTo(12,2);path.lineTo(21,6);path.lineTo(21,12);path.cubicTo(21,17,16,21,12,23);path.cubicTo(8,21,3,17,3,12);path.lineTo(3,6);path.close();c.drawPath(path,p);
                c.drawCircle(12,10,2.5f,p); c.drawLine(12,12.5f,12,17,p);
            } else if (kind.equals("scan")) {
                for (int x : new int[]{3,21}) for(int y:new int[]{3,21}) { int dx=x==3?1:-1,dy=y==3?1:-1;c.drawLine(x,y,x+5*dx,y,p);c.drawLine(x,y,x,y+5*dy,p); }
                c.drawLine(3,12,21,12,p); c.drawRect(8,7,16,17,p);
            } else if (kind.equals("copy")) { c.drawRoundRect(8,8,20,21,2,2,p);Path t=new Path();t.moveTo(15,4);t.lineTo(5,4);t.lineTo(5,16);c.drawPath(t,p); }
            else if(kind.equals("plus")) {c.drawLine(12,4,12,20,p);c.drawLine(4,12,20,12,p);}
            else if(kind.equals("close")) {c.drawLine(6,6,18,18,p);c.drawLine(6,18,18,6,p);}
            else if(kind.equals("more")) {p.setStyle(Paint.Style.FILL);c.drawCircle(5,12,1.5f,p);c.drawCircle(12,12,1.5f,p);c.drawCircle(19,12,1.5f,p);}
            else if(kind.equals("settings")) {c.drawCircle(12,12,7,p);c.drawCircle(12,12,2.5f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p);}}
            c.restore();
        }
    }
    static final class Ring extends View {
        int remaining=-1, period=30; final Paint p=new Paint(3);
        Ring(Context c) {super(c);setContentDescription("验证码刷新倒计时");}
        void update(int r,int per) {if(remaining==r&&period==per)return;remaining=r;period=per;invalidate();}
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);info.setContentDescription(remaining+" 秒后刷新");
        }
        @Override protected void onDraw(Canvas c) {
            float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-dp(getContext(),3);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(getContext(),3));p.setColor(LINE);c.drawCircle(x,y,r,p);
            p.setColor(remaining<=5?Color.rgb(180,111,43):GREEN);p.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(new RectF(x-r,y-r,x+r,y+r),-90,360f*remaining/period,false,p);
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(dp(getContext(),11));
            c.drawText(String.valueOf(remaining),x,y-(p.ascent()+p.descent())/2,p);
        }
    }
}
