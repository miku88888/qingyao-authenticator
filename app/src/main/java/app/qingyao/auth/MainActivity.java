package app.qingyao.auth;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int SCAN=10, PHOTO=11, EXPORT=12, IMPORT=13, UNLOCK=14;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Account> accounts=new ArrayList<>();
    private final List<CodeRow> codeRows=new ArrayList<>();
    private Vault vault; private LinearLayout root, list; private TextView count;
    private boolean needsUnlock=true, authenticating=false, cancelledUnlock=false, loaded=false, busy=false;
    private Runnable pendingAction; private byte[] pendingExport;
    private final List<Dialog> dialogs=new ArrayList<>();
    void track(Dialog d){dialogs.removeIf(x->!x.isShowing());dialogs.add(d);}
    private final Runnable ticker=new Runnable() { public void run(){if(loaded&&!needsUnlock){updateCodes();handler.postDelayed(this,1010-System.currentTimeMillis()%1000);}} };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); Ui.protect(getWindow());
        vault=new Vault(this); showLocked();
    }
    private boolean hasDeviceLock(){return ((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isDeviceSecure();}
    private boolean lockEnabled(){return getPreferences(0).getBoolean("lock",true)&&hasDeviceLock();}
    @Override public void onResume(){super.onResume();if(needsUnlock&&!authenticating&&!cancelledUnlock)unlock();else if(loaded){handler.removeCallbacks(ticker);ticker.run();}}
    @Override public void onPause(){handler.removeCallbacks(ticker);super.onPause();}
    @Override public void onStop(){super.onStop();for(Dialog d:dialogs)if(d.isShowing())d.dismiss();dialogs.clear();needsUnlock=true;loaded=false;accounts.clear();codeRows.clear();showLocked();}
    @Override public void onDestroy(){handler.removeCallbacks(ticker);worker.shutdown();if(pendingExport!=null)Arrays.fill(pendingExport,(byte)0);super.onDestroy();}
    private void unlock(){
        cancelledUnlock=false;
        if(lockEnabled()) {
            Intent i=((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).createConfirmDeviceCredentialIntent("解锁清钥","请验证手机锁屏密码，以查看验证码");
            if(i!=null){authenticating=true;startActivityForResult(i,UNLOCK);return;}
        }
        openVault();
    }
    private void openVault(){
        try {accounts.clear();accounts.addAll(vault.load());loaded=true;needsUnlock=false;showHome();handler.removeCallbacks(ticker);ticker.run();
            Runnable action=pendingAction;pendingAction=null;if(action!=null)action.run();
        } catch(Exception e){loaded=false;needsUnlock=true;cancelledUnlock=true;showErrorVault();}
    }
    private void whenUnlocked(Runnable action){if(loaded&&!needsUnlock)action.run();else {pendingAction=action;if(!authenticating)unlock();}}
    private int dp(float v){return Ui.dp(this,v);}
    private void newRoot(){root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setPadding(dp(24),dp(16),dp(24),dp(12));setContentView(root);}
    private void showLocked(){newRoot();LinearLayout c=Ui.column(this);c.setGravity(Gravity.CENTER);root.addView(c,new LinearLayout.LayoutParams(-1,0,1));
        c.addView(new Ui.Icon(this,"shield",Ui.GREEN),new LinearLayout.LayoutParams(dp(56),dp(56)));Ui.space(c,24);
        c.addView(Ui.text(this,"清钥",30,Ui.INK,true));Ui.space(c,10);c.addView(Ui.text(this,"你的验证码，安心保管",14,Ui.MUTED,false));Ui.space(c,32);
        c.addView(Ui.button(this,"解锁查看",true,this::unlock),new LinearLayout.LayoutParams(-1,dp(56)));footer();}
    private void showErrorVault(){newRoot();Ui.space(root,50);root.addView(Ui.text(this,"无法打开本地保险库",24,Ui.INK,true));Ui.space(root,16);
        root.addView(Ui.text(this,"加密密钥不可用或文件已损坏。你的原始数据仍被保留，请勿卸载应用。",15,Ui.MUTED,false));Ui.space(root,26);
        root.addView(Ui.button(this,"重新尝试",true,this::openVault));}
    private void footer(){TextView t=Ui.text(this,"无广告  ·  不联网  ·  本地加密",11,Ui.MUTED,false);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(12),0,dp(10));root.addView(t);}
    private void showHome(){
        newRoot();codeRows.clear();LinearLayout header=Ui.row(this);
        LinearLayout brand=Ui.column(this);TextView tag=Ui.text(this,"Q I N G Y A O",9,Ui.MUTED,true);brand.addView(tag);Ui.space(brand,6);
        brand.addView(Ui.text(this,"清钥",29,Ui.INK,true));header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=Ui.text(this,"●  离线就绪",11,Ui.GREEN,true);status.setPadding(dp(12),dp(8),dp(12),dp(8));status.setBackground(Ui.shape(Ui.PALE,30,this));header.addView(status);
        Ui.Icon settings=new Ui.Icon(this,"settings",Ui.GREEN);settings.setPadding(dp(12),dp(12),dp(12),dp(12));settings.setContentDescription("设置与备份");settings.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        settings.setOnClickListener(v->settings());LinearLayout.LayoutParams settingsLp=new LinearLayout.LayoutParams(dp(48),dp(48));settingsLp.leftMargin=dp(8);header.addView(settings,settingsLp);root.addView(header);Ui.space(root,22);
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=Ui.column(this);scroll.addView(content);
        LinearLayout hero=Ui.row(this);hero.setPadding(dp(22),dp(24),dp(18),dp(24));hero.setBackground(Ui.shape(Ui.GREEN,26,this));
        LinearLayout words=Ui.column(this);words.addView(Ui.text(this,"让每次登录\n都多一份安心",22,Color.WHITE,true));Ui.space(words,12);
        words.addView(Ui.text(this,"验证码只在这台设备上生成",11,Color.rgb(192,214,192),false));hero.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout shield=new FrameLayout(this);shield.setBackground(Ui.shape(Color.rgb(38,79,61),80,this));Ui.Icon shieldIcon=new Ui.Icon(this,"shield",Color.rgb(203,231,188));
        FrameLayout.LayoutParams shieldLp=new FrameLayout.LayoutParams(dp(43),dp(48),Gravity.CENTER);shield.addView(shieldIcon,shieldLp);hero.addView(shield,new LinearLayout.LayoutParams(dp(75),dp(75)));content.addView(hero);Ui.space(content,28);
        LinearLayout label=Ui.row(this);label.addView(Ui.text(this,"我的账号",18,Ui.INK,true));count=Ui.text(this,String.format(Locale.ROOT,"  %02d",accounts.size()),13,Ui.MUTED,false);label.addView(count);
        View gap=new View(this);label.addView(gap,new LinearLayout.LayoutParams(0,1,1));TextView manage=Ui.text(this,"添加  ＋",13,Ui.GREEN,true);manage.setPadding(dp(12),dp(10),0,dp(10));manage.setOnClickListener(v->addOptions());label.addView(manage);content.addView(label);Ui.space(content,12);
        list=Ui.column(this);content.addView(list);
        if(accounts.isEmpty())emptyState();else for(Account account:accounts)addCard(account);
        Ui.space(content,12);
        if(!accounts.isEmpty()){Ui.space(root,12);root.addView(Ui.button(this,"＋  添加新账号",true,this::addOptions),new LinearLayout.LayoutParams(-1,dp(56)));}
        footer();updateCodes();
    }
    private void emptyState(){
        LinearLayout card=Ui.column(this);card.setGravity(Gravity.CENTER);card.setPadding(dp(22),dp(26),dp(22),dp(24));card.setBackground(Ui.outline(Color.WHITE,26,this));
        FrameLayout circle=new FrameLayout(this);circle.setBackground(Ui.shape(Ui.PALE,24,this));circle.addView(new Ui.Icon(this,"scan",Ui.GREEN),new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER));card.addView(circle,new LinearLayout.LayoutParams(dp(64),dp(64)));Ui.space(card,18);
        card.addView(Ui.text(this,"从第一个账号开始",19,Ui.INK,true));Ui.space(card,10);
        TextView intro=Ui.text(this,"扫描网站提供的验证器二维码，\n即可生成动态验证码。",13,Ui.MUTED,false);intro.setGravity(Gravity.CENTER);intro.setLineSpacing(dp(4),1);card.addView(intro);Ui.space(card,24);
        card.addView(Ui.button(this,"扫码添加账号",true,this::scan),new LinearLayout.LayoutParams(-1,dp(54)));Ui.space(card,10);
        LinearLayout alternatives=Ui.row(this);alternatives.addView(Ui.button(this,"从图片导入",false,this::photo),new LinearLayout.LayoutParams(0,dp(50),1));View sp=new View(this);alternatives.addView(sp,new LinearLayout.LayoutParams(dp(10),1));alternatives.addView(Ui.button(this,"手动输入",false,()->accountForm(null,false)),new LinearLayout.LayoutParams(0,dp(50),1));card.addView(alternatives);list.addView(card);
        Ui.space(list,18);TextView tip=Ui.text(this,"小提示：在网站的「安全 / 两步验证」中，\n选择「身份验证器应用」即可找到二维码。",12,Ui.MUTED,false);tip.setLineSpacing(dp(5),1);list.addView(tip);
    }
    private static final class CodeRow {Account account;TextView code;Ui.Ring ring;}
    private void addCard(Account a){
        LinearLayout card=Ui.column(this);card.setPadding(dp(20),dp(18),dp(20),dp(18));card.setBackground(Ui.outline(Color.WHITE,23,this));
        LinearLayout top=Ui.row(this);TextView logo=Ui.text(this,a.title().substring(0,1).toUpperCase(Locale.ROOT),20,Ui.GREEN,true);logo.setGravity(Gravity.CENTER);logo.setBackground(Ui.shape(Ui.PALE,14,this));top.addView(logo,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout names=Ui.column(this);TextView title=Ui.text(this,a.title(),16,Ui.INK,true);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);names.addView(title);Ui.space(names,5);
        TextView sub=Ui.text(this,a.name.isEmpty()?"动态验证码":a.name,11,Ui.MUTED,false);sub.setSingleLine(true);sub.setEllipsize(TextUtils.TruncateAt.END);names.addView(sub);LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,-2,1);nlp.leftMargin=dp(12);top.addView(names,nlp);
        Ui.Icon menu=new Ui.Icon(this,"more",Ui.MUTED);menu.setPadding(dp(8),dp(8),dp(8),dp(8));menu.setContentDescription("管理 "+a.title());menu.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);menu.setOnClickListener(v->manage(a));top.addView(menu,new LinearLayout.LayoutParams(dp(40),dp(40)));card.addView(top);Ui.space(card,20);
        LinearLayout bottom=Ui.row(this);TextView code=Ui.text(this,"",a.digits==8?30:34,Ui.GREEN,true);code.setTypeface(Typeface.create("monospace",0));code.setLetterSpacing(.04f);bottom.addView(code,new LinearLayout.LayoutParams(0,-2,1));code.setOnClickListener(v->copy(a));
        Ui.Icon copy=new Ui.Icon(this,"copy",Ui.GREEN);copy.setPadding(dp(11),dp(11),dp(11),dp(11));copy.setBackground(Ui.shape(Ui.BG,14,this));copy.setContentDescription("复制 "+a.title()+" 验证码");copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);copy.setOnClickListener(v->copy(a));bottom.addView(copy,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Ui.Ring ring=new Ui.Ring(this);LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(dp(36),dp(36));rlp.leftMargin=dp(12);bottom.addView(ring,rlp);card.addView(bottom);
        CodeRow row=new CodeRow();row.account=a;row.code=code;row.ring=ring;codeRows.add(row);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);list.addView(card,lp);
    }
    private void updateCodes(){if(!loaded||needsUnlock)return;long now=System.currentTimeMillis()/1000;
        for(CodeRow r:codeRows){String code=r.account.code(now);int split=code.length()/2;String formatted=code.substring(0,split)+" "+code.substring(split);if(!TextUtils.equals(r.code.getText(),formatted)){r.code.setText(formatted);r.code.setContentDescription(r.account.title()+" 验证码 "+code);}r.ring.update(r.account.period-(int)(now%r.account.period),r.account.period);}}
    private void copy(Account a){if(needsUnlock||!loaded)return;String value=a.code(System.currentTimeMillis()/1000);ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        ClipData clip=ClipData.newPlainText("清钥验证码",value);if(Build.VERSION.SDK_INT>=33){PersistableBundle sensitive=new PersistableBundle();sensitive.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(sensitive);}clipboard.setPrimaryClip(clip);toast("已复制验证码");
        handler.postDelayed(()->{if(clipboard.hasPrimaryClip()){ClipData current=clipboard.getPrimaryClip();if(current!=null&&current.getItemCount()>0&&TextUtils.equals("清钥验证码",current.getDescription().getLabel())&&TextUtils.equals(value,current.getItemAt(0).coerceToText(this)))clipboard.clearPrimaryClip();}},20000);
    }
    private void addOptions(){LinearLayout c=Ui.column(this);c.addView(Ui.text(this,"添加账号",23,Ui.INK,true));Ui.space(c,8);c.addView(Ui.text(this,"选一种方便的方式",13,Ui.MUTED,false));Ui.space(c,24);
        Dialog[] d=new Dialog[1];c.addView(Ui.button(this,"扫描二维码",true,()->{d[0].dismiss();scan();}));Ui.space(c,12);c.addView(Ui.button(this,"从二维码图片导入",false,()->{d[0].dismiss();photo();}));Ui.space(c,12);c.addView(Ui.button(this,"手动输入密钥",false,()->{d[0].dismiss();accountForm(null,false);}));d[0]=Ui.sheet(this,c);}
    private void scan(){startActivityForResult(new Intent(this,ScanActivity.class),SCAN);}
    private void photo(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PHOTO);}
    private EditText field(LinearLayout c,String label,String placeholder,String value,boolean secret){
        c.addView(Ui.text(this,label,12,Ui.MUTED,true));Ui.space(c,7);EditText e=new EditText(this);e.setTextSize(15);e.setTextColor(Ui.INK);e.setHintTextColor(Ui.MUTED);e.setHint(placeholder);e.setSingleLine(true);e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setBackground(Ui.outline(Color.WHITE,14,this));
        e.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);if(secret)e.setTransformationMethod(PasswordTransformationMethod.getInstance());e.setText(value==null?"":value);c.addView(e,new LinearLayout.LayoutParams(-1,dp(52)));Ui.space(c,16);return e;
    }
    private Spinner selector(LinearLayout c,String label,String[] choices,int selection){c.addView(Ui.text(this,label,12,Ui.MUTED,true));Ui.space(c,4);Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));s.setSelection(selection);c.addView(s,new LinearLayout.LayoutParams(-1,dp(42)));Ui.space(c,12);return s;}
    private void accountForm(Account original,boolean editing){
        LinearLayout c=Ui.column(this);c.addView(Ui.text(this,editing?"编辑账号":original==null?"手动添加账号":"确认添加账号",23,Ui.INK,true));Ui.space(c,8);c.addView(Ui.text(this,editing?"修改名称不会改变验证码":"密钥来自网站的两步验证设置",12,Ui.MUTED,false));Ui.space(c,24);
        EditText issuer=field(c,"服务名称","例如 OpenAI、GitHub",original==null?"":original.issuer,false);
        EditText name=field(c,"账号名称","邮箱或方便辨认的名称",original==null?"":original.name,false);
        EditText secret=editing?null:field(c,"设置密钥","粘贴网站提供的密钥",original==null?"":original.secret,true);
        Spinner algo=null,digits=null;EditText period=null;
        if(!editing){TextView more=Ui.text(this,"高级选项  ﹀",12,Ui.GREEN,true);more.setPadding(0,dp(6),0,dp(14));c.addView(more);LinearLayout advanced=Ui.column(this);advanced.setVisibility(original!=null&&(original.digits!=6||original.period!=30||!original.algorithm.equals("SHA1"))?View.VISIBLE:View.GONE);c.addView(advanced);more.setOnClickListener(v->advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
            algo=selector(advanced,"算法",new String[]{"SHA1","SHA256","SHA512"},original==null?0:original.algorithm.equals("SHA256")?1:original.algorithm.equals("SHA512")?2:0);
            digits=selector(advanced,"验证码位数",new String[]{"6 位","8 位"},original!=null&&original.digits==8?1:0);
            period=field(advanced,"刷新周期（秒）","30",String.valueOf(original==null?30:original.period),false);period.setInputType(InputType.TYPE_CLASS_NUMBER);}
        TextView error=Ui.text(this,"",12,Color.rgb(161,64,48),false);c.addView(error);Ui.space(c,8);Dialog[] dialog=new Dialog[1];final Spinner algorithmField=algo,digitsField=digits;final EditText periodField=period;
        c.addView(Ui.button(this,editing?"保存修改":"添加到清钥",true,()->{
            try{if(needsUnlock||!loaded)throw new IllegalArgumentException("请先解锁应用");Account a=new Account(editing?original.id:null,issuer.getText().toString(),name.getText().toString(),editing?original.secret:secret.getText().toString(),editing?original.algorithm:algorithmField.getSelectedItem().toString(),editing?original.digits:digitsField.getSelectedItemPosition()==0?6:8,editing?original.period:Integer.parseInt(periodField.getText().toString()));
                if(!editing){if(accounts.size()>=500)throw new IllegalArgumentException("最多支持 500 个账号");for(Account existing:accounts)if(existing.secret.equals(a.secret)&&existing.algorithm.equals(a.algorithm)&&existing.digits==a.digits&&existing.period==a.period)throw new IllegalArgumentException("这个密钥已经添加过了");}
                List<Account> updated=new ArrayList<>(accounts);if(editing){for(int n=0;n<updated.size();n++)if(updated.get(n).id.equals(original.id))updated.set(n,a);}else updated.add(a);
                vault.save(updated);accounts.clear();accounts.addAll(updated);dialog[0].dismiss();hideKeyboard();showHome();toast(editing?"已保存":"账号已添加");
            }catch(NumberFormatException e){error.setText("请输入有效的刷新周期（1–300 秒）");}catch(IllegalArgumentException e){error.setText(e.getMessage());}catch(Exception e){error.setText("保存失败，请重试；原有账号未更改");}
        }));dialog[0]=Ui.sheet(this,c);
    }
    private void manage(Account a){LinearLayout c=Ui.column(this);c.addView(Ui.text(this,a.title(),23,Ui.INK,true));Ui.space(c,8);c.addView(Ui.text(this,a.name,13,Ui.MUTED,false));Ui.space(c,24);Dialog[] d=new Dialog[1];
        c.addView(Ui.button(this,"编辑账号名称",true,()->{d[0].dismiss();accountForm(a,true);}));Ui.space(c,12);c.addView(Ui.button(this,"删除账号",false,()->{d[0].dismiss();new AlertDialog.Builder(this).setTitle("删除这个账号？").setMessage("删除后无法从本机找回。请先确认网站已关闭两步验证，或你还有其他登录方式。").setNegativeButton("保留",null).setPositiveButton("删除",(v,w)->{if(needsUnlock||!loaded)return;List<Account> next=new ArrayList<>(accounts);next.removeIf(x->x.id.equals(a.id));try{vault.save(next);accounts.clear();accounts.addAll(next);showHome();toast("已删除账号");}catch(Exception e){toast("删除失败，请重试");}}).show().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}));d[0]=Ui.sheet(this,c);}
    private void settings(){
        LinearLayout c=Ui.column(this);c.addView(Ui.text(this,"设置与备份",23,Ui.INK,true));Ui.space(c,22);
        Switch lock=new Switch(this);lock.setText("打开应用时验证锁屏密码");lock.setTextSize(14);lock.setChecked(lockEnabled());lock.setEnabled(hasDeviceLock());c.addView(lock);
        Ui.space(c,8);c.addView(Ui.text(this,hasDeviceLock()?"使用手机已有的锁屏密码保护查看入口":"请先在手机设置中启用锁屏密码",12,Ui.MUTED,false));lock.setOnCheckedChangeListener((v,enabled)->getPreferences(0).edit().putBoolean("lock",enabled).apply());Ui.space(c,26);
        Dialog[] d=new Dialog[1];c.addView(Ui.button(this,"导出加密备份",true,()->{d[0].dismiss();backupPassword(null);}));Ui.space(c,12);c.addView(Ui.button(this,"从加密备份恢复",false,()->{d[0].dismiss();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);}));Ui.space(c,14);
        TextView warning=Ui.text(this,"换手机前，请导出备份并记住备份密码。\n卸载应用会删除本机账号。恢复时会合并账号。",12,Ui.MUTED,false);warning.setLineSpacing(dp(5),1);c.addView(warning);Ui.space(c,24);
        c.addView(Ui.text(this,"验证码不被网站接受？",14,Ui.INK,true));Ui.space(c,8);TextView help=Ui.text(this,"在手机设置中打开「自动设置日期和时间」，等下一组验证码刷新后再试。请同时保存网站提供的恢复代码。",12,Ui.MUTED,false);help.setLineSpacing(dp(4),1);c.addView(help);Ui.space(c,24);
        c.addView(Ui.text(this,"清钥 1.0.0\n无广告 · 无统计追踪 · 无网络权限\n二维码识别使用 ZXing（Apache 2.0）",11,Ui.MUTED,false));d[0]=Ui.sheet(this,c);
    }
    private void backupPassword(Uri importUri){
        boolean restore=importUri!=null;if(!restore&&accounts.isEmpty()){toast("先添加一个账号再备份");return;}
        LinearLayout c=Ui.column(this);c.addView(Ui.text(this,restore?"恢复加密备份":"设置备份密码",23,Ui.INK,true));Ui.space(c,8);c.addView(Ui.text(this,restore?"输入导出时设置的密码":"密码至少 10 个字符，丢失后无法找回",12,Ui.MUTED,false));Ui.space(c,24);
        EditText password=field(c,"备份密码","输入密码","",true);EditText confirm=restore?null:field(c,"再次输入","再次输入相同密码","",true);TextView error=Ui.text(this,"",12,Color.rgb(161,64,48),false);c.addView(error);Ui.space(c,8);Dialog[] d=new Dialog[1];
        TextView action=Ui.button(this,restore?"解密并恢复":"加密并选择保存位置",true,()->{
            if(busy)return;char[] chars=password.getText().toString().toCharArray();if(!restore&&(chars.length<10||!password.getText().toString().equals(confirm.getText().toString()))){Arrays.fill(chars,'\0');error.setText("密码需至少 10 个字符，且两次输入相同");return;}
            byte[] snapshot;try{snapshot=restore?null:Vault.serialize(accounts);}catch(Exception e){Arrays.fill(chars,'\0');error.setText("无法读取账号");return;}
            busy=true;error.setText("正在处理，请稍候…");worker.execute(()->{
                try{if(restore){byte[] data=readLimited(importUri,2*1024*1024);byte[] plain=BackupCrypto.decrypt(data,chars);List<Account> restored;try{restored=Vault.deserialize(plain);}finally{Arrays.fill(plain,(byte)0);}handler.post(()->{busy=false;d[0].dismiss();whenUnlocked(()->merge(restored));});}
                    else{byte[] encrypted=BackupCrypto.encrypt(snapshot,chars);handler.post(()->{busy=false;d[0].dismiss();whenUnlocked(()->{pendingExport=encrypted;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");i.putExtra(Intent.EXTRA_TITLE,"清钥备份-"+new java.text.SimpleDateFormat("yyyyMMdd",Locale.ROOT).format(new Date())+".qingyao");startActivityForResult(i,EXPORT);});});}
                }catch(Exception e){handler.post(()->{busy=false;error.setText(restore?"密码不正确、文件损坏或备份格式不支持":"加密失败，请重试");});}
                finally{Arrays.fill(chars,'\0');if(snapshot!=null)Arrays.fill(snapshot,(byte)0);}
            });
        });c.addView(action);d[0]=Ui.sheet(this,c);d[0].setOnDismissListener(v->{password.setText("");if(confirm!=null)confirm.setText("");});
    }
    private void merge(List<Account> restored){List<Account> next=new ArrayList<>(accounts);int added=0;
        for(Account a:restored){boolean duplicate=false;for(Account b:next)if(a.secret.equals(b.secret)&&a.algorithm.equals(b.algorithm)&&a.period==b.period&&a.digits==b.digits){duplicate=true;break;}if(!duplicate){next.add(new Account(null,a.issuer,a.name,a.secret,a.algorithm,a.digits,a.period));added++;}}
        if(next.size()>500){toast("合并后超过 500 个账号，未导入");return;}try{vault.save(next);accounts.clear();accounts.addAll(next);showHome();toast(added==0?"备份中的账号已在本机，无需重复导入":"已恢复 "+added+" 个账号");}catch(Exception e){toast("恢复失败，原有账号未更改");}}
    private byte[] readLimited(Uri uri,int max)throws Exception{try(InputStream stream=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(stream==null)throw new IOException();byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("文件过大");out.write(buffer,0,n);}return out.toByteArray();}}
    private void decodePhoto(Uri uri){if(busy)return;busy=true;toast("正在识别二维码…");worker.execute(()->{
        Bitmap image=null;
        try{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream s=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(s,null,options);}if(options.outWidth<1||options.outHeight<1)throw new IOException();
            options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2000)options.inSampleSize*=2;options.inJustDecodeBounds=false;try(InputStream s=getContentResolver().openInputStream(uri)){image=BitmapFactory.decodeStream(s,null,options);}if(image==null)throw new IOException();String raw=QrDecoder.bitmap(image);Account account=Account.parse(raw);handler.post(()->{busy=false;whenUnlocked(()->accountForm(account,false));});
        }catch(IllegalArgumentException e){handler.post(()->{busy=false;toast(e.getMessage());});}catch(Exception e){handler.post(()->{busy=false;toast("图片中没有识别到验证器二维码，请选择更清晰的原图");});}finally{if(image!=null)image.recycle();}
    });}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==UNLOCK){authenticating=false;if(result==RESULT_OK){cancelledUnlock=false;openVault();}else{cancelledUnlock=true;showLocked();}return;}
        if(result!=RESULT_OK){if(request==EXPORT&&pendingExport!=null){Arrays.fill(pendingExport,(byte)0);pendingExport=null;}return;}
        if(request==SCAN&&data!=null){if(data.getBooleanExtra("manual",false))pendingAction=()->accountForm(null,false);else{String raw=data.getStringExtra("otp");pendingAction=()->{try{accountForm(Account.parse(raw),false);}catch(IllegalArgumentException e){toast(e.getMessage());}};}}
        if(request==PHOTO&&data!=null&&data.getData()!=null){Uri uri=data.getData();pendingAction=()->decodePhoto(uri);}
        if(request==IMPORT&&data!=null&&data.getData()!=null){Uri uri=data.getData();pendingAction=()->backupPassword(uri);}
        if(request==EXPORT&&data!=null&&data.getData()!=null&&pendingExport!=null){Uri uri=data.getData();byte[] encrypted=pendingExport;pendingExport=null;pendingAction=()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(encrypted);toast("加密备份已保存");}catch(Exception e){toast("保存失败，请重新导出备份");}finally{Arrays.fill(encrypted,(byte)0);}};}
        if(loaded&&!needsUnlock){Runnable action=pendingAction;pendingAction=null;if(action!=null)action.run();}
    }
    private void hideKeyboard(){View v=getCurrentFocus();if(v!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
