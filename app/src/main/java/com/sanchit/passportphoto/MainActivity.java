package com.sts.digikit;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.ConsumerIrManager;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.text.DecimalFormat;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Calendar;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(9,18,34);
    private final int PANEL = Color.rgb(28,42,74);
    private final int PANEL2 = Color.rgb(31,70,104);
    private final int ACCENT = Color.rgb(38,183,255);
    private final int PURPLE = Color.rgb(115,92,255);
    private final int TEAL = Color.rgb(24,196,170);
    private final int GREEN = Color.rgb(38,183,108);
    private final int ORANGE = Color.rgb(244,154,48);
    private final int RED = Color.rgb(232,83,95);
    private final int SURFACE = Color.rgb(13,27,48);
    private final int SOFT = Color.rgb(172,190,220);
    private final int WHITE = Color.WHITE;
    private LinearLayout root;
    private TextView title;
    private boolean devMode = false;
    private boolean vibrationEnabled = true;
    private String language = "ENGLISH";
    private String currentTool = "CALCULATOR";
    private boolean toolPickerOpen = false;
    private final StringBuilder appLogs = new StringBuilder();
    private final DecimalFormat df = new DecimalFormat("#,##0.00");
    private AndroidTvV2 androidTvV2;

    private static final int REQ_EMBEDDED_SCANNER_CAMERA=9012;
    private static final int REQ_GALLERY_SCAN=9013;
    private com.journeyapps.barcodescanner.DecoratedBarcodeView embeddedScanner;
    private FrameLayout scannerViewport;
    private TextView scannerStatus;
    private TextView scannerDetails;
    private boolean scannerActive=false;
    private boolean scannerResultLocked=false;
    private boolean scannerFullScreen=false;
    private String lastScannerRaw="";
    private String lastScannerDetails="";


    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        devMode=sp.getBoolean("devMode",false);
        vibrationEnabled=sp.getBoolean("vibration",true);
        language=sp.getString("language","ENGLISH");
        logEvent("App started");
        showCalculator();
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView tv(String s,int sp,int color){
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(14),dp(8),dp(14),dp(8));
        return t;
    }
    private GradientDrawable bg(int color,float radius){
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp((int)radius)); return g;
    }

    private GradientDrawable grad(int c1,int c2,float radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{c1,c2});
        g.setCornerRadius(dp((int)radius));
        return g;
    }

    private GradientDrawable fieldBg(){
        GradientDrawable g=bg(PANEL,10);
        g.setStroke(dp(1),Color.rgb(54,101,145));
        return g;
    }

    private android.graphics.drawable.Drawable touchBg(int color,float radius){
        GradientDrawable base=bg(color,radius);
        if(Build.VERSION.SDK_INT>=21){
            return new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(90,255,255,255)),
                    base,null);
        }
        return base;
    }

    private android.graphics.drawable.Drawable screenBg(){
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{BG,SURFACE,BG});
    }

    private int actionColor(String s){
        String x=s==null?"":s.toUpperCase(java.util.Locale.US);
        if(x.contains("SHARE") || x.contains("शेयर")) return GREEN;
        if(x.contains("HISTORY") || x.contains("हिस्ट्री")) return PURPLE;
        if(x.contains("CLEAR") || "C".equals(x)) return RED;
        if(x.contains("CALCULATE") || x.contains("CONVERT") || x.contains("GENERATE") || x.contains("MAKE")) return ACCENT;
        if("=".equals(x)) return TEAL;
        if("+".equals(x) || "-".equals(x) || "×".equals(x) || "÷".equals(x) || "%".equals(x)) return ORANGE;
        return PANEL2;
    }
    private Button btn(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(WHITE);
        b.setTextSize(16);
        b.setTypeface(null,1);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10),dp(8),dp(10),dp(8));
        b.setMinHeight(dp(48));
        b.setBackground(touchBg(actionColor(s),10));
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN) haptic();
            return false;
        });
        return b;
    }
    private EditText input(String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(SOFT); e.setTextColor(WHITE);
        e.setTextSize(18); e.setSingleLine(true); e.setPadding(dp(14),dp(8),dp(14),dp(8));
        e.setBackground(fieldBg()); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54)); p.setMargins(0,dp(4),0,dp(4)); e.setLayoutParams(p); return e;
    }

    private LinearLayout.LayoutParams controlParams(int heightDp){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(heightDp));
        p.setMargins(0,dp(4),0,dp(4));
        return p;
    }

    private LinearLayout.LayoutParams resultParams(int heightDp){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(heightDp));
        p.setMargins(0,dp(10),0,dp(4));
        return p;
    }

    private void styleResult(TextView t){
        t.setGravity(Gravity.CENTER);
        t.setTypeface(null,1);
        t.setPadding(dp(14),dp(12),dp(14),dp(12));
        t.setBackground(grad(PANEL2,Color.rgb(28,96,132),12));
    }

    private Spinner dropdown(String[] items){
        Spinner s=new Spinner(this);
        ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,items){
            @Override public View getView(int pos, View convert, android.view.ViewGroup parent){
                TextView t=(TextView)super.getView(pos,convert,parent);
                t.setTextColor(WHITE); t.setTextSize(18); t.setPadding(dp(14),0,dp(14),0);
                t.setBackgroundColor(PANEL2); return t;
            }
            @Override public View getDropDownView(int pos, View convert, android.view.ViewGroup parent){
                TextView t=(TextView)super.getDropDownView(pos,convert,parent);
                t.setTextColor(WHITE); t.setTextSize(18); t.setPadding(dp(14),dp(14),dp(14),dp(14));
                t.setBackgroundColor(PANEL); return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setBackground(touchBg(PANEL2,10));
        return s;
    }
    private String L(String en,String hi){
        return "HINDI".equals(language)?hi:en;
    }

    private void openTool(String key){
        currentTool=key;
        if("CALCULATOR".equals(key)) showCalculator();
        else if("CASH_COUNTER".equals(key)) showCashCounter();
        else if("AGE".equals(key)) showAge();
        else if("SAVINGS".equals(key)) showSavings();
        else if("EMI".equals(key)) showEmiInterest();
        else if("WORDS".equals(key)) showNumberWords();
        else if("QR".equals(key)) showQr(false);
        else if("GST".equals(key)) showGst();
        else if("WIFI_QR".equals(key)) showQr(true);
        else if("REMOTE".equals(key)) showRemote();
        else if("UNIT".equals(key)) showUnitConverter();
        else if("SPEED".equals(key)) openSpeedTest();
        else if("BILL".equals(key)) showQuickBill();
        else if("SCAN".equals(key)) showScanner();
    }

    private void reopenCurrentTool(){
        openTool(currentTool);
    }

    private String toolName(String key){
        if("CASH_COUNTER".equals(key)) return L("CASH COUNTER","कैश काउंटर");
        if("AGE".equals(key)) return L("AGE CALCULATOR","आयु कैलकुलेटर");
        if("SAVINGS".equals(key)) return L("RD / FD / SIP CALCULATOR","आरडी / एफडी / एसआईपी कैलकुलेटर");
        if("EMI".equals(key)) return L("EMI / INTEREST CALCULATOR","ईएमआई / ब्याज कैलकुलेटर");
        if("WORDS".equals(key)) return L("NUMBER TO WORDS","संख्या शब्दों में");
        if("QR".equals(key)) return L("QR CODE GENERATOR","QR कोड जनरेटर");
        if("GST".equals(key)) return L("GST / DISCOUNT CALCULATOR","GST / डिस्काउंट कैलकुलेटर");
        if("WIFI_QR".equals(key)) return L("WI-FI QR GENERATOR","वाई-फाई QR जनरेटर");
        if("REMOTE".equals(key)) return L("REMOTE","रिमोट");
        if("UNIT".equals(key)) return L("UNIT CONVERTER","यूनिट कन्वर्टर");
        if("SPEED".equals(key)) return L("INTERNET SPEED TEST","इंटरनेट स्पीड टेस्ट");
        if("BILL".equals(key)) return L("QUICK BILL","क्विक बिल");
        if("SCAN".equals(key)) return L("QR / BARCODE SCANNER","QR / बारकोड स्कैनर");
        return L("CALCULATOR","कैलकुलेटर");
    }

    private String currentToolName(){
        return toolName(currentTool);
    }

    private String[] defaultToolOrder(){
        return new String[]{"CALCULATOR","CASH_COUNTER","AGE","SAVINGS","EMI","WORDS","QR","GST","WIFI_QR","REMOTE","UNIT","SPEED","BILL","SCAN"};
    }

    private String[] getToolOrder(){
        String saved=getSharedPreferences("sts",0).getString("toolOrder","");
        String[] def=defaultToolOrder();
        if(saved==null || saved.trim().isEmpty()) return def;
        String[] arr=saved.split(",");
        if(arr.length!=def.length) return def;
        java.util.HashSet<String> valid=new java.util.HashSet<>();
        for(String x:def) valid.add(x);
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(String x:arr) if(!valid.contains(x) || !seen.add(x)) return def;
        return arr;
    }

    private void saveToolOrder(String[] order){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<order.length;i++){
            if(i>0)b.append(",");
            b.append(order[i]);
        }
        getSharedPreferences("sts",0).edit().putString("toolOrder",b.toString()).apply();
    }

    private void showOrderEditor(){
        final String[] order=getToolOrder();
        final int[] selected={0};

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12),dp(10),dp(12),dp(10));
        box.setBackgroundColor(BG);

        TextView help=tv(L("Select an item, then move it UP or DOWN.","आइटम चुनें, फिर उसे ऊपर या नीचे करें।"),16,SOFT);
        box.addView(help,controlParams(58));

        ListView list=new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setDividerHeight(1);

        java.util.ArrayList<String> names=new java.util.ArrayList<>();
        for(String key:order) names.add(toolName(key));
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_single_choice,names){
            @Override public View getView(int pos,View convert,android.view.ViewGroup parent){
                TextView t=(TextView)super.getView(pos,convert,parent);
                t.setTextColor(WHITE);
                t.setTextSize(17);
                t.setBackgroundColor(PANEL);
                t.setPadding(dp(12),dp(10),dp(12),dp(10));
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setItemChecked(0,true);
        list.setOnItemClickListener((p,v,pos,id)->selected[0]=pos);
        box.addView(list,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout controls=new LinearLayout(this);
        Button up=btn("▲  "+L("UP","ऊपर"));
        Button down=btn("▼  "+L("DOWN","नीचे"));
        controls.addView(up,new LinearLayout.LayoutParams(0,dp(58),1));
        controls.addView(down,new LinearLayout.LayoutParams(0,dp(58),1));
        box.addView(controls,controlParams(58));

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(L("DROPDOWN LIST ORDER","ड्रॉपडाउन लिस्ट क्रम"))
                .setView(box)
                .setPositiveButton(L("DONE","पूरा"),null)
                .setNeutralButton(L("RESET","रीसेट"),null)
                .create();

        Runnable refresh=()->{
            names.clear();
            for(String key:order) names.add(toolName(key));
            adapter.notifyDataSetChanged();
            list.setItemChecked(selected[0],true);
            list.smoothScrollToPosition(selected[0]);
        };

        up.setOnClickListener(v->{
            int i=selected[0];
            if(i<=0)return;
            String t=order[i-1]; order[i-1]=order[i]; order[i]=t;
            selected[0]=i-1;
            saveToolOrder(order);
            refresh.run();
            haptic();
        });

        down.setOnClickListener(v->{
            int i=selected[0];
            if(i>=order.length-1)return;
            String t=order[i+1]; order[i+1]=order[i]; order[i]=t;
            selected[0]=i+1;
            saveToolOrder(order);
            refresh.run();
            haptic();
        });

        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                String[] def=defaultToolOrder();
                for(int i=0;i<order.length;i++)order[i]=def[i];
                selected[0]=0;
                saveToolOrder(order);
                refresh.run();
            });
        });
        dialog.show();
    }

    private ScrollView shell(String name){
        toolPickerOpen=false;
        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(screenBg());

        addFixedDropdown(outer,name);

        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackground(screenBg());
        sc.setClipToPadding(false);

        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(dp(8),dp(8),dp(8),dp(8));
        root.setBackground(screenBg());

        sc.addView(root,new ScrollView.LayoutParams(-1,-1));
        outer.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        setContentView(outer);
        return sc;
    }

    private void addFixedDropdown(LinearLayout outer, String currentName){
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0,0,0,0);
        header.setBackground(grad(Color.rgb(26,56,96),Color.rgb(31,94,135),0));

        TextView menu=tv("⋮",34,WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(touchBg(Color.rgb(27,42,78),0));
        header.addView(menu,new LinearLayout.LayoutParams(dp(56),dp(64)));

        TextView label=tv(currentName,21,WHITE);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(null,1);
        header.addView(label,new LinearLayout.LayoutParams(0,dp(64),1));

        TextView spacer=tv("",1,WHITE);
        header.addView(spacer,new LinearLayout.LayoutParams(dp(56),dp(64)));

        outer.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));

        menu.setOnClickListener(v->{haptic();showTopMenu(menu);});
        label.setOnClickListener(v->{haptic();showToolPicker(currentName);});
        spacer.setOnClickListener(v->{haptic();showToolPicker(currentName);});
    }

    private void showToolPicker(String currentName){
        toolPickerOpen=true;

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(screenBg());

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(grad(Color.rgb(26,56,96),Color.rgb(31,94,135),0));

        TextView menu=tv("⋮",34,WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(touchBg(Color.rgb(27,42,78),0));
        header.addView(menu,new LinearLayout.LayoutParams(dp(56),dp(64)));

        TextView label=tv(currentName,21,WHITE);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(null,1);
        header.addView(label,new LinearLayout.LayoutParams(0,dp(64),1));

        TextView spacer=tv("",1,WHITE);
        header.addView(spacer,new LinearLayout.LayoutParams(dp(56),dp(64)));

        outer.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));

        ScrollView listScroll=new ScrollView(this);
        listScroll.setFillViewport(true);
        listScroll.setBackground(screenBg());
        listScroll.setVerticalScrollBarEnabled(true);
        listScroll.setScrollbarFadingEnabled(false);

        LinearLayout tools=new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setBackground(screenBg());

        for(String key:getToolOrder()){
            final String k=key;
            addMenu(tools,toolName(k),()->{toolPickerOpen=false;openTool(k);},k.equals(currentTool));
        }

        listScroll.addView(tools,new ScrollView.LayoutParams(-1,-2));
        outer.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1));

        menu.setOnClickListener(v->showTopMenu(menu));
        label.setOnClickListener(v->{toolPickerOpen=false;reopenCurrentTool();});
        spacer.setOnClickListener(v->{toolPickerOpen=false;reopenCurrentTool();});

        setContentView(outer);
    }

    private void showHome(){
        showCalculator();
    }

    private void addMenu(LinearLayout list,String s,Runnable r){
        addMenu(list,s,r,false);
    }

    private void addMenu(LinearLayout list,String s,Runnable r,boolean selected){
        TextView row=tv(s,20,selected?BG:WHITE);
        row.setGravity(Gravity.CENTER);
        row.setTypeface(null,1);
        row.setBackground(selected?grad(TEAL,ACCENT,0):touchBg(PANEL,0));
        if(selected) row.setContentDescription(s+" selected");
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(72));
        p.setMargins(0,0,0,dp(2));
        list.addView(row,p);
        row.setOnClickListener(v->{logEvent("Open: "+s); haptic(); r.run();});
    }

    private void showTopMenu(View anchor){
        PopupMenu p=new PopupMenu(this,anchor);
        p.getMenu().add(L("LANGUAGE","भाषा"));
        p.getMenu().add(L("DROPDOWN LIST ORDER","ड्रॉपडाउन लिस्ट क्रम"));
        p.getMenu().add(L("ABOUT","ऐप के बारे में"));
        p.setOnMenuItemClickListener(item->{
            String s=item.getTitle().toString();

            if(s.equals(L("LANGUAGE","भाषा"))){
                final String[] choices={"ENGLISH","HINDI"};
                int checked="HINDI".equals(language)?1:0;
                new AlertDialog.Builder(this)
                        .setTitle(L("Select Language","भाषा चुनें"))
                        .setSingleChoiceItems(choices,checked,(d,which)->{
                            language=choices[which];
                            getSharedPreferences("sts",0).edit().putString("language",language).apply();
                            d.dismiss();
                            toolPickerOpen=false;
                            reopenCurrentTool();
                        })
                        .setNegativeButton(L("CANCEL","रद्द करें"),null)
                        .show();
                return true;
            }

            if(s.equals(L("DROPDOWN LIST ORDER","ड्रॉपडाउन लिस्ट क्रम"))){
                showOrderEditor();
                return true;
            }

            if(s.equals(L("ABOUT","ऐप के बारे में"))){
                new AlertDialog.Builder(this)
                        .setTitle("STS DigiKit")
                        .setMessage(L(
                                "Version 1.0.39\nOffline utility toolkit\nChange the dropdown item order from the three-dot menu.",
                                "संस्करण 1.0.39\nऑफलाइन यूटिलिटी टूलकिट\nThree-dot मेनू से dropdown items का क्रम ऊपर-नीचे बदल सकते हैं।"))
                        .setPositiveButton("OK",null)
                        .show();
                return true;
            }
            return false;
        });
        p.show();
    }

    private void showSettings(){
        shell("SETTINGS");
        TextView h=tv("STS DigiKit Settings",24,WHITE); h.setGravity(Gravity.CENTER); h.setTypeface(null,1); root.addView(h,new LinearLayout.LayoutParams(-1,dp(70)));

        Switch vib=new Switch(this); vib.setText("Vibration Feedback"); vib.setTextColor(WHITE); vib.setTextSize(18); vib.setChecked(vibrationEnabled);
        vib.setPadding(dp(12),dp(8),dp(12),dp(8)); root.addView(vib,new LinearLayout.LayoutParams(-1,dp(62)));
        vib.setOnCheckedChangeListener((b,on)->{
            vibrationEnabled=on;
            getSharedPreferences("sts",0).edit().putBoolean("vibration",on).apply();
            logEvent("Vibration: "+(on?"ON":"OFF"));
        });

        Switch dev=new Switch(this); dev.setText("Developer Mode"); dev.setTextColor(WHITE); dev.setTextSize(18); dev.setChecked(devMode);
        dev.setPadding(dp(12),dp(8),dp(12),dp(8)); root.addView(dev,new LinearLayout.LayoutParams(-1,dp(62)));
        dev.setOnCheckedChangeListener((b,on)->{
            devMode=on;
            getSharedPreferences("sts",0).edit().putBoolean("devMode",on).apply();
            logEvent("Dev Mode: "+(on?"ON":"OFF"));
        });

        TextView about=tv("Version 1.0.39\nOffline utility toolkit\nCalculator • QR • Scanner • Finance tools",17,SOFT);
        about.setGravity(Gravity.CENTER); about.setBackground(bg(PANEL,10)); root.addView(about,resultParams(120));
    }

    private void showLogs(){
        shell("VIEW LOGS");
        TextView logs=tv(appLogs.length()==0?"No logs yet":appLogs.toString(),15,WHITE);
        logs.setGravity(Gravity.TOP|Gravity.LEFT); logs.setTextIsSelectable(true); logs.setBackground(bg(PANEL,10));
        root.addView(logs,new LinearLayout.LayoutParams(-1,-2));
        Button clear=btn("CLEAR LOGS"); root.addView(clear,controlParams(58));
        clear.setOnClickListener(v->{appLogs.setLength(0);logs.setText("No logs yet");});
    }

    private void logEvent(String s){
        appLogs.append(new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.US).format(new java.util.Date()))
                .append("  ").append(s).append("\n");
    }

    private void haptic(){
        if(vibrationEnabled) try{
            if(Build.VERSION.SDK_INT>=26) ((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE))
                    .vibrate(android.os.VibrationEffect.createOneShot(25,80));
            else ((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(25);
        }catch(Exception ignored){}
    }

    private double val(EditText e){
        try{return Double.parseDouble(e.getText().toString().trim());}catch(Exception x){return 0;}
    }
    private void resultBox(String s){
        TextView r=tv(s,20,WHITE); r.setGravity(Gravity.CENTER); r.setBackground(bg(PANEL2,10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(12),0,dp(6)); root.addView(r,p);
    }

    private boolean isCalcOperatorChar(char ch){
        return ch=='+' || ch=='-' || ch=='×' || ch=='÷';
    }

    private String calcCompleteExpression(String exp){
        String s=exp==null?"":exp.trim();
        while(!s.isEmpty() && isCalcOperatorChar(s.charAt(s.length()-1))){
            s=s.substring(0,s.length()-1).trim();
        }
        return s;
    }

    private BigDecimal evaluateCalcExpression(String exp){
        String s=calcCompleteExpression(exp);
        if(s.isEmpty()) return BigDecimal.ZERO;

        String[] t=s.split("\\s+");
        if(t.length==0) return BigDecimal.ZERO;

        BigDecimal current=new BigDecimal(t[0]);
        BigDecimal total=BigDecimal.ZERO;
        char addOp='+';

        for(int i=1;i+1<t.length;i+=2){
            String op=t[i];
            BigDecimal n=new BigDecimal(t[i+1]);

            if("×".equals(op)){
                current=current.multiply(n);
            }else if("÷".equals(op)){
                if(n.compareTo(BigDecimal.ZERO)==0) throw new ArithmeticException("divide by zero");
                int scale=Math.max(50,Math.max(current.scale(),n.scale())+50);
                current=current.divide(n,scale,RoundingMode.HALF_UP);
            }else if("+".equals(op) || "-".equals(op)){
                total=(addOp=='+')?total.add(current):total.subtract(current);
                current=n;
                addOp=op.charAt(0);
            }
        }

        total=(addOp=='+')?total.add(current):total.subtract(current);
        return total;
    }

    private String calcFormat(BigDecimal x){
        if(x==null) return "0";
        BigDecimal y=x.stripTrailingZeros();
        if(y.compareTo(BigDecimal.ZERO)==0) return "0";
        return y.toPlainString();
    }

    private void showCalculator(){
        currentTool="CALCULATOR";
        toolPickerOpen=false;

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(screenBg());

        addFixedDropdown(outer,L("CALCULATOR","कैलकुलेटर"));

        LinearLayout calcBody=new LinearLayout(this);
        calcBody.setOrientation(LinearLayout.VERTICAL);
        calcBody.setBackground(screenBg());
        outer.addView(calcBody,new LinearLayout.LayoutParams(-1,0,1));

        final TextView typing=tv("",28,WHITE);
        typing.setGravity(Gravity.RIGHT|Gravity.BOTTOM);
        typing.setPadding(dp(18),dp(16),dp(18),dp(16));
        typing.setBackground(screenBg());
        typing.setTextIsSelectable(false);
        if(Build.VERSION.SDK_INT>=26){
            typing.setAutoSizeTextTypeUniformWithConfiguration(14,28,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        }
        calcBody.addView(typing,new LinearLayout.LayoutParams(-1,0,1));

        final TextView display=tv("0",32,WHITE);
        display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        display.setTypeface(null,1);
        display.setPadding(dp(18),dp(10),dp(18),dp(10));
        display.setBackground(bg(PANEL,8));
        if(Build.VERSION.SDK_INT>=26){
            display.setAutoSizeTextTypeUniformWithConfiguration(12,32,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        }
        LinearLayout.LayoutParams displayParams=new LinearLayout.LayoutParams(-1,dp(80));
        displayParams.setMargins(0,0,0,dp(4));
        calcBody.addView(display,displayParams);

        final String[] expression={""};
        final boolean[] justEvaluated={false};

        LinearLayout calcActions=new LinearLayout(this);
        calcActions.setOrientation(LinearLayout.HORIZONTAL);
        Button calcHistory=btn(L("HISTORY","हिस्ट्री"));
        Button calcShare=btn(L("SHARE","शेयर"));
        calcActions.addView(calcHistory,new LinearLayout.LayoutParams(0,dp(48),1));
        calcActions.addView(calcShare,new LinearLayout.LayoutParams(0,dp(48),1));
        calcBody.addView(calcActions,new LinearLayout.LayoutParams(-1,dp(50)));

        calcHistory.setOnClickListener(v->{
            String value=typing.getText().toString().trim();
            String result=display.getText().toString().trim();
            String full=(value.isEmpty()?result:value+"\n= "+result);
            if(meaningfulResult(result)) savePanelHistory("calculator",L("CALCULATOR","कैलकुलेटर"),full);
            showPanelHistory("calculator",L("CALCULATOR","कैलकुलेटर"));
        });
        calcShare.setOnClickListener(v->{
            String value=typing.getText().toString().trim();
            String result=display.getText().toString().trim();
            String full=(value.isEmpty()?result:value+"\n= "+result);
            if(meaningfulResult(result)) savePanelHistory("calculator",L("CALCULATOR","कैलकुलेटर"),full);
            sharePanelText(L("CALCULATOR","कैलकुलेटर"),full);
        });

        Runnable refreshLive=()->{
            typing.setText(expression[0]);
            String complete=calcCompleteExpression(expression[0]);
            if(complete.isEmpty()){
                display.setText("0");
                return;
            }
            try{
                display.setText(calcFormat(evaluateCalcExpression(complete)));
            }catch(Exception ex){
                display.setText("Error");
            }
        };

        java.util.function.Consumer<String> press=(String x)->{
            if("C".equals(x)){
                expression[0]="";
                justEvaluated[0]=false;
                refreshLive.run();
                return;
            }

            if("⌫".equals(x)){
                if(justEvaluated[0]){
                    expression[0]="";
                    justEvaluated[0]=false;
                }else{
                    String s=expression[0];
                    if(!s.isEmpty()){
                        s=s.substring(0,s.length()-1).trim();
                        expression[0]=s;
                    }
                }
                refreshLive.run();
                return;
            }

            if("=".equals(x)){
                String complete=calcCompleteExpression(expression[0]);
                if(complete.isEmpty()) return;
                try{
                    BigDecimal ans=evaluateCalcExpression(complete);
                    String formatted=calcFormat(ans);
                    typing.setText(complete+" =");
                    display.setText(formatted);
                    savePanelHistory("calculator",L("CALCULATOR","कैलकुलेटर"),complete+" = "+formatted);
                    expression[0]=formatted;
                    justEvaluated[0]=true;
                }catch(Exception ex){
                    display.setText("Error");
                }
                return;
            }

            if("%".equals(x)){
                if(justEvaluated[0]) justEvaluated[0]=false;
                String s=expression[0].trim();
                int i=s.length()-1;
                while(i>=0 && (Character.isDigit(s.charAt(i)) || s.charAt(i)=='.')) i--;
                String number=s.substring(i+1);
                if(number.isEmpty()) return;
                try{
                    BigDecimal v=new BigDecimal(number).divide(new BigDecimal("100"),50,RoundingMode.HALF_UP);
                    expression[0]=s.substring(0,i+1)+calcFormat(v);
                    refreshLive.run();
                }catch(Exception ignored){}
                return;
            }

            boolean operator="+".equals(x) || "-".equals(x) || "×".equals(x) || "÷".equals(x);
            if(operator){
                if(justEvaluated[0]) justEvaluated[0]=false;

                String s=expression[0].trim();
                if(s.isEmpty()) return;

                if(isCalcOperatorChar(s.charAt(s.length()-1))){
                    s=s.substring(0,s.length()-1).trim();
                }
                expression[0]=s+" "+x+" ";
                refreshLive.run();
                return;
            }

            if(justEvaluated[0]){
                expression[0]="";
                justEvaluated[0]=false;
            }

            String s=expression[0];
            if(".".equals(x)){
                int lastSpace=s.lastIndexOf(' ');
                String last=lastSpace>=0?s.substring(lastSpace+1):s;
                if(last.contains(".")) return;
                if(last.isEmpty()) x="0.";
            }

            expression[0]+=x;
            refreshLive.run();
        };

        String[][] rows={
                {"C","⌫","÷"},
                {"7","8","9","%","×"},
                {"4","5","6",".","-"},
                {"1","2","3","+"},
                {"0","00","000","="}
        };

        for(int r=0;r<rows.length;r++){
            LinearLayout line=new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);

            for(int i=0;i<rows[r].length;i++){
                String k=rows[r][i];
                Button b=btn(k);
                b.setTextSize(("C".equals(k)||"⌫".equals(k))?20:22);

                float weight=1f;
                if(r==0 && ("C".equals(k)||"⌫".equals(k))) weight=2f;
                if((r==3 && "+".equals(k)) || (r==4 && "=".equals(k))) weight=2f;

                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(62),weight);
                bp.setMargins(dp(1),dp(1),dp(1),dp(1));
                line.addView(b,bp);

                b.setOnClickListener(v->{
                    haptic();
                    press.accept(((Button)v).getText().toString());
                });
            }
            calcBody.addView(line,new LinearLayout.LayoutParams(-1,dp(64)));
        }

        setContentView(outer);
    }

    private String trim(double x){ if(x==(long)x)return String.valueOf((long)x); return new DecimalFormat("0.########").format(x); }

    private String formatCash(BigInteger value){
        try{
            java.text.NumberFormat nf=java.text.NumberFormat.getIntegerInstance(new java.util.Locale("en","IN"));
            return nf.format(value);
        }catch(Exception e){
            return value.toString();
        }
    }

    private String buildCashSummary(String partyName,int[] den, EditText[] qty, BigInteger total){
        StringBuilder b=new StringBuilder();
        b.append("STS DigiKit - Cash Counter\n");
        if(partyName!=null && !partyName.trim().isEmpty()){
            b.append("Party / Customer / Company: ").append(partyName.trim()).append("\n");
        }
        b.append("TOTAL: ₹").append(formatCash(total)).append("\n\n");
        for(int i=0;i<den.length;i++){
            String q=qty[i].getText().toString().trim();
            if(q.isEmpty()) continue;
            try{
                BigInteger n=new BigInteger(q);
                if(n.signum()==0) continue;
                BigInteger amount=n.multiply(BigInteger.valueOf(den[i]));
                b.append("₹").append(den[i])
                        .append(" × ").append(n)
                        .append(" = ₹").append(formatCash(amount))
                        .append("\n");
            }catch(Exception ignored){}
        }
        b.append("\n").append(new java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a",java.util.Locale.getDefault()).format(new java.util.Date()));
        return b.toString();
    }

    private void saveCashHistory(String summary, BigInteger total){
        if(total==null || total.signum()==0 || summary==null || summary.trim().isEmpty()) return;
        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        String old=sp.getString("cashHistory","");
        String payload=android.util.Base64.encodeToString(summary.getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);

        if(old!=null && !old.isEmpty()){
            String[] rows=old.split("\\u001e",-1);
            if(rows.length>0){
                String[] p=rows[0].split("\\|",2);
                if(p.length==2 && p[1].equals(payload)) return;
            }
        }

        String rec=System.currentTimeMillis()+"|"+payload;
        String merged=old==null||old.isEmpty()?rec:rec+"\u001e"+old;
        String[] rows=merged.split("\\u001e",-1);
        StringBuilder keep=new StringBuilder();
        for(int i=0;i<rows.length && i<20;i++){
            if(i>0) keep.append("\u001e");
            keep.append(rows[i]);
        }
        sp.edit().putString("cashHistory",keep.toString()).apply();
    }

    private void showCashHistory(){
        String raw=getSharedPreferences("sts",0).getString("cashHistory","");
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(BG);
        box.setPadding(dp(10),dp(8),dp(10),dp(8));

        ScrollView sc=new ScrollView(this);
        TextView content=tv("",16,WHITE);
        content.setGravity(Gravity.LEFT|Gravity.TOP);
        content.setTextIsSelectable(true);
        content.setBackground(bg(PANEL,8));

        if(raw==null || raw.isEmpty()){
            content.setText(L("No cash history yet.","अभी कोई कैश हिस्ट्री नहीं है।"));
        }else{
            StringBuilder out=new StringBuilder();
            String[] rows=raw.split("\\u001e",-1);
            for(int i=0;i<rows.length;i++){
                String[] p=rows[i].split("\\|",2);
                if(p.length!=2) continue;
                try{
                    String summary=new String(android.util.Base64.decode(p[1],android.util.Base64.NO_WRAP),java.nio.charset.StandardCharsets.UTF_8);
                    if(out.length()>0) out.append("\n\n--------------------\n\n");
                    out.append(summary);
                }catch(Exception ignored){}
            }
            content.setText(out.length()==0?L("No cash history yet.","अभी कोई कैश हिस्ट्री नहीं है।"):out.toString());
        }

        sc.addView(content,new ScrollView.LayoutParams(-1,-2));
        box.addView(sc,new LinearLayout.LayoutParams(-1,dp(430)));

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(L("CASH HISTORY","कैश हिस्ट्री"))
                .setView(box)
                .setPositiveButton(L("CLOSE","बंद करें"),null)
                .setNegativeButton(L("CLEAR HISTORY","हिस्ट्री साफ करें"),null)
                .create();

        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
                getSharedPreferences("sts",0).edit().remove("cashHistory").apply();
                content.setText(L("No cash history yet.","अभी कोई कैश हिस्ट्री नहीं है।"));
            });
        });
        dialog.show();
    }

    private void shareCashSummary(String summary){
        try{
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT,"STS DigiKit Cash Counter");
            send.putExtra(Intent.EXTRA_TEXT,summary);
            startActivity(Intent.createChooser(send,L("Share Cash Summary","कैश सारांश शेयर करें")));
        }catch(Exception e){
            Toast.makeText(this,L("No sharing app found","शेयर करने वाला ऐप नहीं मिला"),Toast.LENGTH_SHORT).show();
        }
    }

    private void showCashCounter(){
        currentTool="CASH_COUNTER";
        toolPickerOpen=false;

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(screenBg());

        addFixedDropdown(outer,L("CASH COUNTER","कैश काउंटर"));

        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackground(screenBg());
        outer.addView(body,new LinearLayout.LayoutParams(-1,0,1));

        EditText partyName=new EditText(this);
        partyName.setHint(L("Party / Customer / Company Name","Party / Customer / Company Name"));
        partyName.setHintTextColor(SOFT);
        partyName.setTextColor(WHITE);
        partyName.setTextSize(18);
        partyName.setSingleLine(true);
        partyName.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        partyName.setPadding(dp(14),dp(8),dp(14),dp(8));
        partyName.setBackground(fieldBg());
        LinearLayout.LayoutParams partyParams=new LinearLayout.LayoutParams(-1,dp(56));
        partyParams.setMargins(dp(6),dp(6),dp(6),dp(4));
        body.addView(partyName,partyParams);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button history=btn(L("HISTORY","हिस्ट्री"));
        Button share=btn(L("SHARE","शेयर"));
        actions.addView(history,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(share,new LinearLayout.LayoutParams(0,dp(48),1));
        body.addView(actions,new LinearLayout.LayoutParams(-1,dp(50)));

        TextView total=tv("TOTAL ₹0",27,WHITE);
        styleResult(total);
        body.addView(total,new LinearLayout.LayoutParams(-1,dp(72)));

        LinearLayout heads=new LinearLayout(this);
        heads.setGravity(Gravity.CENTER_VERTICAL);
        TextView hDen=tv(L("NOTE / COIN","नोट / सिक्का"),14,SOFT);
        TextView hQty=tv("QTY",14,SOFT);
        TextView hAmt=tv(L("AMOUNT","राशि"),14,SOFT);
        hDen.setGravity(Gravity.CENTER);
        hQty.setGravity(Gravity.CENTER);
        hAmt.setGravity(Gravity.CENTER);
        heads.addView(hDen,new LinearLayout.LayoutParams(0,dp(38),1.05f));
        heads.addView(hQty,new LinearLayout.LayoutParams(0,dp(38),0.85f));
        heads.addView(hAmt,new LinearLayout.LayoutParams(0,dp(38),1.25f));
        body.addView(heads,new LinearLayout.LayoutParams(-1,dp(38)));

        int[] den={500,200,100,50,20,10,5,2,1};
        EditText[] qty=new EditText[den.length];
        TextView[] amount=new TextView[den.length];

        LinearLayout rowsBox=new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(rowsBox,new LinearLayout.LayoutParams(-1,0,1));

        final BigInteger[] grandTotal={BigInteger.ZERO};

        Runnable recalc=()->{
            BigInteger totalValue=BigInteger.ZERO;
            for(int i=0;i<den.length;i++){
                BigInteger q=BigInteger.ZERO;
                String raw=qty[i].getText().toString().trim();
                try{
                    if(!raw.isEmpty()) q=new BigInteger(raw);
                }catch(Exception ignored){}
                if(q.signum()<0) q=BigInteger.ZERO;
                BigInteger row=q.multiply(BigInteger.valueOf(den[i]));
                amount[i].setText("₹"+formatCash(row));
                totalValue=totalValue.add(row);
            }
            grandTotal[0]=totalValue;
            total.setText("TOTAL ₹"+formatCash(totalValue));
        };

        for(int i=0;i<den.length;i++){
            final int pos=i;
            LinearLayout line=new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setBackground(i%2==0?bg(Color.rgb(11,24,43),8):bg(Color.rgb(15,32,55),8));

            TextView note=tv("₹"+den[i],20,WHITE);
            note.setGravity(Gravity.CENTER);
            line.addView(note,new LinearLayout.LayoutParams(0,-1,1.05f));

            EditText q=new EditText(this);
            q.setHint("0");
            q.setHintTextColor(SOFT);
            q.setTextColor(WHITE);
            q.setTextSize(19);
            q.setGravity(Gravity.CENTER);
            q.setSingleLine(true);
            q.setSelectAllOnFocus(true);
            q.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            q.setPadding(dp(6),dp(4),dp(6),dp(4));
            q.setBackground(fieldBg());
            qty[i]=q;
            LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,dp(48),0.85f);
            qp.setMargins(dp(3),dp(3),dp(3),dp(3));
            line.addView(q,qp);

            TextView a=tv("₹0",18,WHITE);
            a.setGravity(Gravity.CENTER);
            a.setTypeface(null,1);
            a.setBackground(grad(Color.rgb(26,72,105),Color.rgb(29,107,116),8));
            amount[i]=a;
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1.25f);
            ap.setMargins(dp(3),dp(3),dp(3),dp(3));
            line.addView(a,ap);

            q.addTextChangedListener(new android.text.TextWatcher(){
                @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
                @Override public void onTextChanged(CharSequence s,int st,int before,int count){ recalc.run(); }
                @Override public void afterTextChanged(android.text.Editable e){}
            });

            rowsBox.addView(line,new LinearLayout.LayoutParams(-1,0,1));
        }

        history.setOnClickListener(v->{
            recalc.run();
            if(grandTotal[0].signum()>0){
                saveCashHistory(buildCashSummary(partyName.getText().toString(),den,qty,grandTotal[0]),grandTotal[0]);
            }
            showCashHistory();
        });

        share.setOnClickListener(v->{
            recalc.run();
            String summary=buildCashSummary(partyName.getText().toString(),den,qty,grandTotal[0]);
            if(grandTotal[0].signum()>0) saveCashHistory(summary,grandTotal[0]);
            shareCashSummary(summary);
        });

        recalc.run();
        setContentView(outer);
    }

    private boolean meaningfulResult(String s){
        if(s==null) return false;
        String x=s.trim();
        if(x.isEmpty() || "0".equals(x)) return false;
        String l=x.toLowerCase(java.util.Locale.US);
        return !(l.startsWith("select ") || l.startsWith("enter ") || l.equals("error"));
    }

    private String panelHistoryKey(String key){
        return "panel_history_"+key;
    }

    private void savePanelHistory(String key,String title,String content){
        if(!meaningfulResult(content)) return;
        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        String pref=panelHistoryKey(key);
        String old=sp.getString(pref,"");
        String stamp=new java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a",java.util.Locale.getDefault()).format(new java.util.Date());
        String raw=stamp+"\n"+title+"\n"+content.trim();
        String payload=android.util.Base64.encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);

        if(old!=null && !old.isEmpty()){
            String[] top=old.split("\\u001e",-1);
            if(top.length>0 && top[0].equals(payload)) return;
        }

        String merged=(old==null||old.isEmpty())?payload:payload+"\u001e"+old;
        String[] rows=merged.split("\\u001e",-1);
        StringBuilder keep=new StringBuilder();
        for(int i=0;i<rows.length && i<30;i++){
            if(i>0) keep.append("\u001e");
            keep.append(rows[i]);
        }
        sp.edit().putString(pref,keep.toString()).apply();
    }

    private void showPanelHistory(String key,String title){
        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        String raw=sp.getString(panelHistoryKey(key),"");

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(8),dp(8),dp(8));
        box.setBackgroundColor(BG);

        ScrollView sc=new ScrollView(this);
        TextView content=tv("",16,WHITE);
        content.setGravity(Gravity.LEFT|Gravity.TOP);
        content.setTextIsSelectable(true);
        content.setBackground(bg(PANEL,10));

        if(raw==null || raw.isEmpty()){
            content.setText(L("No history yet.","अभी कोई हिस्ट्री नहीं है।"));
        }else{
            StringBuilder out=new StringBuilder();
            String[] rows=raw.split("\\u001e",-1);
            for(String row:rows){
                try{
                    String item=new String(android.util.Base64.decode(row,android.util.Base64.NO_WRAP),java.nio.charset.StandardCharsets.UTF_8);
                    if(out.length()>0) out.append("\n\n--------------------\n\n");
                    out.append(item);
                }catch(Exception ignored){}
            }
            content.setText(out.toString());
        }

        sc.addView(content,new ScrollView.LayoutParams(-1,-2));
        box.addView(sc,new LinearLayout.LayoutParams(-1,dp(430)));

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(title+" - "+L("HISTORY","हिस्ट्री"))
                .setView(box)
                .setPositiveButton(L("CLOSE","बंद करें"),null)
                .setNegativeButton(L("CLEAR HISTORY","हिस्ट्री साफ करें"),null)
                .create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
            sp.edit().remove(panelHistoryKey(key)).apply();
            content.setText(L("No history yet.","अभी कोई हिस्ट्री नहीं है।"));
        }));
        dialog.show();
    }

    private void sharePanelText(String title,String content){
        if(!meaningfulResult(content)){
            Toast.makeText(this,L("Nothing to share yet","अभी शेयर करने के लिए कोई परिणाम नहीं है"),Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,title);
        send.putExtra(Intent.EXTRA_TEXT,title+"\n\n"+content.trim());
        try{
            startActivity(Intent.createChooser(send,L("Share Result","परिणाम शेयर करें")));
        }catch(Exception e){
            Toast.makeText(this,L("No sharing app found","शेयर करने वाला ऐप नहीं मिला"),Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout addHistoryShareBar(LinearLayout parent,String key,String title,TextView result){
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button history=btn(L("HISTORY","हिस्ट्री"));
        Button share=btn(L("SHARE","शेयर"));
        bar.addView(history,new LinearLayout.LayoutParams(0,dp(50),1));
        bar.addView(share,new LinearLayout.LayoutParams(0,dp(50),1));
        parent.addView(bar,controlParams(52));

        history.setOnClickListener(v->{
            String value=result==null?"":result.getText().toString();
            if(meaningfulResult(value)) savePanelHistory(key,title,value);
            showPanelHistory(key,title);
        });
        share.setOnClickListener(v->{
            String value=result==null?"":result.getText().toString();
            if(meaningfulResult(value)) savePanelHistory(key,title,value);
            sharePanelText(title,value);
        });
        return bar;
    }

    private Calendar parseManualAgeDate(String raw) throws Exception{
        String s=raw==null?"":raw.trim();
        java.text.SimpleDateFormat sdf=new java.text.SimpleDateFormat("dd/MM/yy",java.util.Locale.US);
        sdf.setLenient(false);
        java.util.Date parsed=sdf.parse(s);
        Calendar cal=Calendar.getInstance();
        cal.setTime(parsed);
        cal.set(Calendar.HOUR_OF_DAY,0);
        cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0);
        cal.set(Calendar.MILLISECOND,0);
        return cal;
    }

    private void showAge(){
        currentTool="AGE";
        shell(L("AGE CALCULATOR","आयु कैलकुलेटर"));

        EditText dateInput=new EditText(this);
        dateInput.setHint("DD/MM/YY");
        dateInput.setHintTextColor(SOFT);
        dateInput.setTextColor(WHITE);
        dateInput.setTextSize(20);
        dateInput.setGravity(Gravity.CENTER);
        dateInput.setSingleLine(true);
        dateInput.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        dateInput.setPadding(dp(14),dp(8),dp(14),dp(8));
        dateInput.setBackground(fieldBg());
        root.addView(dateInput,controlParams(58));

        Button go=btn(L("SHOW DETAILS","DETAILS दिखाएं"));
        root.addView(go,controlParams(60));

        TextView out=tv(L("Enter date in DD/MM/YY","DD/MM/YY में date लिखें"),21,WHITE);
        styleResult(out);
        out.setGravity(Gravity.CENTER);
        root.addView(out,new LinearLayout.LayoutParams(-1,0,1));

        addHistoryShareBar(root,"age",L("AGE CALCULATOR","आयु कैलकुलेटर"),out);

        go.setOnClickListener(v->{
            try{
                Calendar b=parseManualAgeDate(dateInput.getText().toString());
                Calendar a=Calendar.getInstance();
                a.set(Calendar.HOUR_OF_DAY,0);
                a.set(Calendar.MINUTE,0);
                a.set(Calendar.SECOND,0);
                a.set(Calendar.MILLISECOND,0);

                if(b.after(a)){
                    out.setText(L("Invalid date","अमान्य date"));
                    return;
                }

                int y=a.get(Calendar.YEAR)-b.get(Calendar.YEAR);
                int m=a.get(Calendar.MONTH)-b.get(Calendar.MONTH);
                int d=a.get(Calendar.DAY_OF_MONTH)-b.get(Calendar.DAY_OF_MONTH);

                if(d<0){
                    m--;
                    Calendar prev=(Calendar)a.clone();
                    prev.add(Calendar.MONTH,-1);
                    d+=prev.getActualMaximum(Calendar.DAY_OF_MONTH);
                }
                if(m<0){
                    y--;
                    m+=12;
                }

                long days=(a.getTimeInMillis()-b.getTimeInMillis())/86400000L;
                String entered=new java.text.SimpleDateFormat("dd/MM/yy",java.util.Locale.US).format(b.getTime());

                String res=L("Date: ","Date: ")+entered
                        +"\n"+y+" "+L("Years","वर्ष")
                        +"  "+m+" "+L("Months","महीने")
                        +"  "+d+" "+L("Days","दिन")
                        +"\n"+L("Total Days: ","कुल दिन: ")+days;

                out.setText(res);
                savePanelHistory("age",L("AGE CALCULATOR","आयु कैलकुलेटर"),res);
            }catch(Exception e){
                out.setText(L("Enter valid date in DD/MM/YY","DD/MM/YY में सही date लिखें"));
            }
        });
    }

    interface DateCb{void done(Calendar c);}
    private void pickDate(Calendar c,DateCb cb){new DatePickerDialog(this,(v,y,m,d)->{c.set(y,m,d,12,0,0);cb.done(c);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    private String date(Calendar c){return String.format(java.util.Locale.US,"%02d/%02d/%04d",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.YEAR));}

    private void showSavings(){
        currentTool="SAVINGS";
        shell(L("RD / FD / SIP CALCULATOR","आरडी / एफडी / एसआईपी कैलकुलेटर"));

        Spinner mode=dropdown(new String[]{"SIP","RD","FD"});
        root.addView(mode,controlParams(58));

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.TOP);
        root.addView(box,new LinearLayout.LayoutParams(-1,0,1));

        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                financeBox(box,String.valueOf(mode.getSelectedItem()));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        financeBox(box,"SIP");
    }

    private void financeBox(LinearLayout box,String mode){
        box.removeAllViews();

        EditText p=input(mode.equals("FD")?L("Principal Amount","मूल राशि"):L("Monthly Amount","मासिक राशि"));
        EditText rate=input(L("Annual Interest %","वार्षिक ब्याज %"));
        EditText years=input(L("Years","वर्ष"));
        box.addView(p);
        box.addView(rate);
        box.addView(years);

        Button calc=btn(L("CALCULATE ","गणना ")+mode);
        box.addView(calc,controlParams(60));

        TextView out=tv(L("Enter values and calculate","मान भरें और गणना करें"),20,WHITE);
        styleResult(out);
        box.addView(out,new LinearLayout.LayoutParams(-1,0,1));
        addHistoryShareBar(box,"savings",L("RD / FD / SIP CALCULATOR","आरडी / एफडी / एसआईपी कैलकुलेटर"),out);

        calc.setOnClickListener(v->{
            double P=val(p),r=val(rate)/100.0,t=val(years),fv=0,invested=0;
            if(mode.equals("FD")){
                fv=P*Math.pow(1+r/4.0,4*t);
                invested=P;
            }else{
                double i=r/12.0;
                int n=(int)Math.round(t*12);
                invested=P*n;
                if(i==0) fv=invested;
                else fv=P*((Math.pow(1+i,n)-1)/i)*(mode.equals("SIP")?(1+i):1);
            }
            String res=mode+"\n"+L("Invested: ₹","निवेश: ₹")+df.format(invested)
                    +"\n"+L("Maturity: ₹","परिपक्वता: ₹")+df.format(fv)
                    +"\n"+L("Gain: ₹","लाभ: ₹")+df.format(fv-invested);
            out.setText(res);
            savePanelHistory("savings",L("RD / FD / SIP CALCULATOR","आरडी / एफडी / एसआईपी कैलकुलेटर"),res);
        });
    }

    private void showEmiInterest(){
        currentTool="EMI";
        shell(L("EMI / INTEREST CALCULATOR","ईएमआई / ब्याज कैलकुलेटर"));

        Spinner mode=dropdown(new String[]{"EMI","SIMPLE INTEREST"});
        root.addView(mode,controlParams(58));

        EditText loan=input(L("Loan / Principal Amount","लोन / मूल राशि"));
        EditText rate=input(L("Annual Interest %","वार्षिक ब्याज %"));
        EditText months=input(L("Tenure in Months","अवधि (महीने)"));
        root.addView(loan);
        root.addView(rate);
        root.addView(months);

        Button calc=btn(L("CALCULATE","गणना करें"));
        root.addView(calc,controlParams(58));

        TextView out=tv(L("Enter values and calculate","मान भरें और गणना करें"),20,WHITE);
        styleResult(out);
        root.addView(out,new LinearLayout.LayoutParams(-1,0,1));
        addHistoryShareBar(root,"emi",L("EMI / INTEREST CALCULATOR","ईएमआई / ब्याज कैलकुलेटर"),out);

        calc.setOnClickListener(v->{
            double P=val(loan);
            String res;
            if(mode.getSelectedItemPosition()==0){
                double i=val(rate)/1200.0;
                int n=(int)val(months);
                double e=i==0?(n==0?0:P/n):P*i*Math.pow(1+i,n)/(Math.pow(1+i,n)-1);
                double total=e*n;
                res="EMI: ₹"+df.format(e)
                        +"\n"+L("Interest: ₹","ब्याज: ₹")+df.format(total-P)
                        +"\n"+L("Total: ₹","कुल: ₹")+df.format(total);
            }else{
                double r=val(rate)/100.0,t=val(months)/12.0;
                double si=P*r*t;
                res=L("Interest: ₹","ब्याज: ₹")+df.format(si)
                        +"\n"+L("Total: ₹","कुल: ₹")+df.format(P+si);
            }
            out.setText(res);
            savePanelHistory("emi",L("EMI / INTEREST CALCULATOR","ईएमआई / ब्याज कैलकुलेटर"),res);
        });
    }

    private void showNumberWords(){
        currentTool="WORDS";
        shell(L("NUMBER TO WORDS","संख्या शब्दों में"));

        EditText e=input(L("Enter whole number","पूर्ण संख्या दर्ज करें"));
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(e);

        Button go=btn(L("CONVERT","बदलें"));
        root.addView(go,controlParams(60));

        TextView out=tv(L("Converted words will appear here","शब्द यहाँ दिखाई देंगे"),22,WHITE);
        styleResult(out);
        root.addView(out,new LinearLayout.LayoutParams(-1,0,1));
        addHistoryShareBar(root,"words",L("NUMBER TO WORDS","संख्या शब्दों में"),out);

        go.setOnClickListener(v->{
            try{
                long n=Long.parseLong(e.getText().toString());
                String res=indianWords(n);
                out.setText(res);
                savePanelHistory("words",L("NUMBER TO WORDS","संख्या शब्दों में"),e.getText().toString()+" = "+res);
            }catch(Exception x){
                out.setText(L("Enter valid number","सही संख्या दर्ज करें"));
            }
        });
    }

    private String indianWords(long n){
        if(n==0)return "Zero";if(n<0)return "Minus "+indianWords(-n);String s="";
        long crore=n/10000000;n%=10000000;long lakh=n/100000;n%=100000;long thousand=n/1000;n%=1000;long hundred=n/100;n%=100; if(crore>0)s+=indianWords(crore)+" Crore ";if(lakh>0)s+=indianWords(lakh)+" Lakh ";if(thousand>0)s+=indianWords(thousand)+" Thousand ";if(hundred>0)s+=indianWords(hundred)+" Hundred ";if(n>0)s+=under100((int)n);return s.trim();
    }
    private String under100(int n){String[] a={"","One","Two","Three","Four","Five","Six","Seven","Eight","Nine","Ten","Eleven","Twelve","Thirteen","Fourteen","Fifteen","Sixteen","Seventeen","Eighteen","Nineteen"};String[] t={"","","Twenty","Thirty","Forty","Fifty","Sixty","Seventy","Eighty","Ninety"};if(n<20)return a[n];return t[n/10]+(n%10>0?" "+a[n%10]:"");}

    private void showQr(boolean wifi){
        currentTool=wifi?"WIFI_QR":"QR";
        String title=wifi?L("WI-FI QR GENERATOR","वाई-फाई QR जनरेटर"):L("QR CODE GENERATOR","QR कोड जनरेटर");
        shell(title);

        EditText a=input(wifi?L("Wi-Fi Name (SSID)","वाई-फाई नाम (SSID)"):L("Text / URL","टेक्स्ट / URL"));
        root.addView(a);

        EditText b=null;
        if(wifi){
            b=input(L("Wi-Fi Password","वाई-फाई पासवर्ड"));
            b.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            root.addView(b);
        }

        Button go=btn(L("GENERATE QR","QR बनाएं"));
        root.addView(go,controlParams(60));

        TextView info=tv(L("Generate a QR code to enable History / Share","History / Share के लिए QR बनाएं"),16,SOFT);
        info.setGravity(Gravity.CENTER);
        root.addView(info,controlParams(50));

        ImageView img=new ImageView(this);
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        img.setBackground(bg(Color.WHITE,12));
        root.addView(img,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout actions=new LinearLayout(this);
        Button history=btn(L("HISTORY","हिस्ट्री"));
        Button share=btn(L("SHARE","शेयर"));
        actions.addView(history,new LinearLayout.LayoutParams(0,dp(50),1));
        actions.addView(share,new LinearLayout.LayoutParams(0,dp(50),1));
        root.addView(actions,controlParams(52));

        EditText pass=b;
        final String[] shareValue={""};
        final String[] historyValue={""};

        go.setOnClickListener(v->{
            String primary=a.getText().toString().trim();
            if(primary.isEmpty()){
                Toast.makeText(this,L("Enter data first","पहले जानकारी दर्ज करें"),Toast.LENGTH_SHORT).show();
                return;
            }
            String data;
            if(wifi){
                String pwd=pass==null?"":pass.getText().toString();
                data="WIFI:T:WPA;S:"+primary+";P:"+pwd+";;";
                shareValue[0]=data;
                historyValue[0]="SSID: "+primary;
                info.setText(L("Wi-Fi QR generated for: ","वाई-फाई QR बना: ")+primary);
            }else{
                data=primary;
                shareValue[0]=primary;
                historyValue[0]=primary;
                info.setText(L("QR generated","QR बन गया"));
            }
            Bitmap bm=qrBitmap(data,800);
            if(bm!=null){
                img.setImageBitmap(bm);
                savePanelHistory(wifi?"wifi_qr":"qr",title,historyValue[0]);
            }
        });

        history.setOnClickListener(v->{
            if(meaningfulResult(historyValue[0])) savePanelHistory(wifi?"wifi_qr":"qr",title,historyValue[0]);
            showPanelHistory(wifi?"wifi_qr":"qr",title);
        });

        share.setOnClickListener(v->sharePanelText(title,shareValue[0]));
    }

    private Bitmap qrBitmap(String data,int size){try{BitMatrix m=new MultiFormatWriter().encode(data,BarcodeFormat.QR_CODE,size,size);Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565);for(int y=0;y<size;y++)for(int x=0;x<size;x++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);return b;}catch(Exception e){Toast.makeText(this,"QR error",Toast.LENGTH_SHORT).show();return null;}}

    private void showGst(){
        currentTool="GST";
        shell(L("GST / DISCOUNT CALCULATOR","GST / डिस्काउंट कैलकुलेटर"));

        EditText amt=input(L("Amount","राशि"));
        EditText disc=input(L("Discount %","छूट %"));
        EditText gst=input("GST %");
        root.addView(amt);
        root.addView(disc);
        root.addView(gst);

        Button go=btn(L("CALCULATE","गणना करें"));
        root.addView(go,controlParams(60));

        TextView out=tv(L("Enter values and calculate","मान भरें और गणना करें"),20,WHITE);
        styleResult(out);
        root.addView(out,new LinearLayout.LayoutParams(-1,0,1));
        addHistoryShareBar(root,"gst",L("GST / DISCOUNT CALCULATOR","GST / डिस्काउंट कैलकुलेटर"),out);

        go.setOnClickListener(v->{
            double a=val(amt),d=a*val(disc)/100.0,after=a-d,g=after*val(gst)/100.0;
            String res=L("Discount: ₹","छूट: ₹")+df.format(d)
                    +"\nGST: ₹"+df.format(g)
                    +"\n"+L("Final: ₹","अंतिम: ₹")+df.format(after+g);
            out.setText(res);
            savePanelHistory("gst",L("GST / DISCOUNT CALCULATOR","GST / डिस्काउंट कैलकुलेटर"),res);
        });
    }

    private static class TvDevice{
        String name;
        String ip;
        String type;
        TvDevice(String n,String i,String t){name=n;ip=i;type=t;}
        @Override public String toString(){return name+"  ("+type+" • "+ip+")";}
    }

    private String xmlTag(String xml,String tag){
        if(xml==null) return "";
        String low=xml.toLowerCase(java.util.Locale.US);
        String open="<"+tag.toLowerCase(java.util.Locale.US)+">";
        String close="</"+tag.toLowerCase(java.util.Locale.US)+">";
        int a=low.indexOf(open);
        if(a<0) return "";
        int b=low.indexOf(close,a+open.length());
        if(b<0) return "";
        return xml.substring(a+open.length(),b).replace("&amp;","&").trim();
    }

    private String fetchLanText(String url,int timeout){
        java.net.HttpURLConnection con=null;
        try{
            con=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();
            con.setConnectTimeout(timeout);
            con.setReadTimeout(timeout);
            con.setUseCaches(false);
            java.io.InputStream in=con.getInputStream();
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] b=new byte[4096];
            int n,total=0;
            while((n=in.read(b))>0 && total<200000){
                out.write(b,0,n);
                total+=n;
            }
            in.close();
            return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        }catch(Exception e){
            return "";
        }finally{
            if(con!=null) con.disconnect();
        }
    }

    private String headerValue(String response,String name){
        String[] lines=response.split("\\r?\\n");
        for(String line:lines){
            int p=line.indexOf(':');
            if(p>0 && line.substring(0,p).trim().equalsIgnoreCase(name)){
                return line.substring(p+1).trim();
            }
        }
        return "";
    }

    private java.util.ArrayList<TvDevice> discoverTvs(){
        java.util.ArrayList<TvDevice> out=new java.util.ArrayList<>();
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        android.net.wifi.WifiManager.MulticastLock lock=null;
        java.net.DatagramSocket socket=null;
        try{
            android.net.wifi.WifiManager wm=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            if(wm!=null){
                lock=wm.createMulticastLock("sts-tv-discovery");
                lock.setReferenceCounted(false);
                lock.acquire();
            }

            socket=new java.net.DatagramSocket();
            socket.setSoTimeout(350);
            String[] targets={"ssdp:all","roku:ecp","urn:schemas-upnp-org:device:MediaRenderer:1"};
            for(String st:targets){
                String msg="M-SEARCH * HTTP/1.1\r\n"
                        +"HOST: 239.255.255.250:1900\r\n"
                        +"MAN: \"ssdp:discover\"\r\n"
                        +"MX: 2\r\n"
                        +"ST: "+st+"\r\n\r\n";
                byte[] data=msg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                java.net.DatagramPacket packet=new java.net.DatagramPacket(
                        data,data.length,java.net.InetAddress.getByName("239.255.255.250"),1900);
                socket.send(packet);
            }

            long until=System.currentTimeMillis()+2800;
            while(System.currentTimeMillis()<until){
                try{
                    byte[] buf=new byte[8192];
                    java.net.DatagramPacket packet=new java.net.DatagramPacket(buf,buf.length);
                    socket.receive(packet);
                    String response=new String(packet.getData(),0,packet.getLength(),java.nio.charset.StandardCharsets.UTF_8);
                    String lower=response.toLowerCase(java.util.Locale.US);
                    String ip=packet.getAddress().getHostAddress();
                    if(ip==null || seen.contains(ip)) continue;

                    String location=headerValue(response,"LOCATION");
                    String description=location.isEmpty()?"":fetchLanText(location,1200);
                    String all=(response+"\n"+description).toLowerCase(java.util.Locale.US);

                    boolean tvLike=all.contains("roku") || all.contains("samsung") || all.contains("webos")
                            || all.contains("lg electronics") || all.contains("mediarenderer")
                            || all.contains("smarttv") || all.contains("television") || all.contains("dial");
                    if(!tvLike) continue;

                    String type="UPNP";
                    if(all.contains("roku")) type="ROKU";
                    else if(all.contains("samsung")) type="SAMSUNG";
                    else if(all.contains("webos") || all.contains("lg electronics")) type="LG";
                    else if(all.contains("android tv") || all.contains("google tv")
                            || portOpen(ip,6466,450) || portOpen(ip,6467,450)) type="ANDROID_TV";

                    String friendly=xmlTag(description,"friendlyName");
                    String manufacturer=xmlTag(description,"manufacturer");
                    String model=xmlTag(description,"modelName");
                    String name=friendly;
                    if(name.isEmpty()) name=(manufacturer+" "+model).trim();
                    if(name.isEmpty()) name=type+" TV";

                    out.add(new TvDevice(name,ip,type));
                    seen.add(ip);
                }catch(java.net.SocketTimeoutException ignored){}
            }
        }catch(Exception ignored){
        }finally{
            if(socket!=null) socket.close();
            if(lock!=null && lock.isHeld()) lock.release();
        }
        return out;
    }

    private boolean portOpen(String ip,int port,int timeout){
        java.net.Socket s=null;
        try{
            s=new java.net.Socket();
            s.connect(new java.net.InetSocketAddress(ip,port),timeout);
            return true;
        }catch(Exception e){
            return false;
        }finally{
            try{if(s!=null)s.close();}catch(Exception ignored){}
        }
    }

    private boolean checkTvDevice(TvDevice d){
        if(d==null) return false;
        if("ROKU".equals(d.type)){
            String x=fetchLanText("http://"+d.ip+":8060/query/device-info",1800);
            return !x.isEmpty();
        }
        if("SAMSUNG".equals(d.type)) return portOpen(d.ip,8001,1000) || portOpen(d.ip,8002,1000);
        if("ANDROID_TV".equals(d.type)) return portOpen(d.ip,6466,1200) || portOpen(d.ip,6467,1200);
        if("LG".equals(d.type)) return portOpen(d.ip,3000,1000) || portOpen(d.ip,3001,1000);
        return portOpen(d.ip,80,900) || portOpen(d.ip,8000,900);
    }

    private String rokuKey(String key){
        if("POWER".equals(key)) return "Power";
        if("HOME".equals(key)) return "Home";
        if("UP".equals(key)) return "Up";
        if("DOWN".equals(key)) return "Down";
        if("LEFT".equals(key)) return "Left";
        if("RIGHT".equals(key)) return "Right";
        if("OK".equals(key)) return "Select";
        if("BACK".equals(key)) return "Back";
        if("VOL_UP".equals(key)) return "VolumeUp";
        if("VOL_DOWN".equals(key)) return "VolumeDown";
        if("MUTE".equals(key)) return "VolumeMute";
        if("CH_UP".equals(key)) return "ChannelUp";
        if("CH_DOWN".equals(key)) return "ChannelDown";
        if("PLAY".equals(key)) return "Play";
        return key;
    }

    private boolean sendRokuKey(String ip,String key){
        java.net.HttpURLConnection con=null;
        try{
            String k=java.net.URLEncoder.encode(rokuKey(key),"UTF-8");
            java.net.URL url=new java.net.URL("http://"+ip+":8060/keypress/"+k);
            con=(java.net.HttpURLConnection)url.openConnection();
            con.setConnectTimeout(1800);
            con.setReadTimeout(1800);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.getOutputStream().close();
            int code=con.getResponseCode();
            return code>=200 && code<400;
        }catch(Exception e){
            return false;
        }finally{
            if(con!=null) con.disconnect();
        }
    }

    private String samsungKey(String key){
        if("POWER".equals(key)) return "KEY_POWER";
        if("HOME".equals(key)) return "KEY_HOME";
        if("UP".equals(key)) return "KEY_UP";
        if("DOWN".equals(key)) return "KEY_DOWN";
        if("LEFT".equals(key)) return "KEY_LEFT";
        if("RIGHT".equals(key)) return "KEY_RIGHT";
        if("OK".equals(key)) return "KEY_ENTER";
        if("BACK".equals(key)) return "KEY_RETURN";
        if("VOL_UP".equals(key)) return "KEY_VOLUP";
        if("VOL_DOWN".equals(key)) return "KEY_VOLDOWN";
        if("MUTE".equals(key)) return "KEY_MUTE";
        if("CH_UP".equals(key)) return "KEY_CHUP";
        if("CH_DOWN".equals(key)) return "KEY_CHDOWN";
        if("PLAY".equals(key)) return "KEY_PLAY";
        return key;
    }

    private java.net.Socket samsungSocket(String ip,int port,boolean secure) throws Exception{
        if(!secure){
            java.net.Socket s=new java.net.Socket();
            s.connect(new java.net.InetSocketAddress(ip,port),2200);
            return s;
        }
        javax.net.ssl.TrustManager[] trust={new javax.net.ssl.X509TrustManager(){
            public java.security.cert.X509Certificate[] getAcceptedIssuers(){return new java.security.cert.X509Certificate[0];}
            public void checkClientTrusted(java.security.cert.X509Certificate[] c,String a){}
            public void checkServerTrusted(java.security.cert.X509Certificate[] c,String a){}
        }};
        javax.net.ssl.SSLContext sc=javax.net.ssl.SSLContext.getInstance("TLS");
        sc.init(null,trust,new java.security.SecureRandom());
        javax.net.ssl.SSLSocket s=(javax.net.ssl.SSLSocket)sc.getSocketFactory().createSocket();
        s.connect(new java.net.InetSocketAddress(ip,port),2500);
        s.startHandshake();
        return s;
    }

    private void writeWsText(java.io.OutputStream out,String text) throws Exception{
        byte[] data=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.security.SecureRandom rnd=new java.security.SecureRandom();
        byte[] mask=new byte[4];
        rnd.nextBytes(mask);

        out.write(0x81);
        int len=data.length;
        if(len<=125){
            out.write(0x80|len);
        }else if(len<=65535){
            out.write(0x80|126);
            out.write((len>>8)&255);
            out.write(len&255);
        }else{
            out.write(0x80|127);
            for(int i=7;i>=0;i--) out.write((len>>(8*i))&255);
        }
        out.write(mask);
        for(int i=0;i<data.length;i++) out.write(data[i]^mask[i%4]);
        out.flush();
    }

    private boolean sendSamsungWs(String ip,int port,boolean secure,String key){
        java.net.Socket socket=null;
        try{
            socket=samsungSocket(ip,port,secure);
            socket.setSoTimeout(3000);

            byte[] nonce=new byte[16];
            new java.security.SecureRandom().nextBytes(nonce);
            String wsKey=android.util.Base64.encodeToString(nonce,android.util.Base64.NO_WRAP);
            String name=android.util.Base64.encodeToString("STS DigiKit".getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);
            name=java.net.URLEncoder.encode(name,"UTF-8");
            String path="/api/v2/channels/samsung.remote.control?name="+name;

            java.io.OutputStream out=socket.getOutputStream();
            String req="GET "+path+" HTTP/1.1\r\n"
                    +"Host: "+ip+":"+port+"\r\n"
                    +"Upgrade: websocket\r\n"
                    +"Connection: Upgrade\r\n"
                    +"Sec-WebSocket-Key: "+wsKey+"\r\n"
                    +"Sec-WebSocket-Version: 13\r\n"
                    +"Origin: http://localhost\r\n\r\n";
            out.write(req.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();

            java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));
            String first=br.readLine();
            if(first==null || !first.contains("101")) return false;
            String line;
            while((line=br.readLine())!=null && !line.isEmpty()){}

            String payload="{\"method\":\"ms.remote.control\",\"params\":{\"Cmd\":\"Click\",\"DataOfCmd\":\""
                    +samsungKey(key)+"\",\"Option\":\"false\",\"TypeOfRemote\":\"SendRemoteKey\"}}";
            writeWsText(out,payload);
            try{Thread.sleep(100);}catch(Exception ignored){}
            return true;
        }catch(Exception e){
            return false;
        }finally{
            try{if(socket!=null)socket.close();}catch(Exception ignored){}
        }
    }

    private boolean sendSamsungKey(String ip,String key){
        if(sendSamsungWs(ip,8001,false,key)) return true;
        return sendSamsungWs(ip,8002,true,key);
    }

    private int androidTvKeyCode(String key){
        if("POWER".equals(key)) return 26;
        if("HOME".equals(key)) return 3;
        if("BACK".equals(key)) return 4;
        if("UP".equals(key)) return 19;
        if("DOWN".equals(key)) return 20;
        if("LEFT".equals(key)) return 21;
        if("RIGHT".equals(key)) return 22;
        if("OK".equals(key)) return 23;
        if("VOL_UP".equals(key)) return 24;
        if("VOL_DOWN".equals(key)) return 25;
        if("MUTE".equals(key)) return 164;
        if("CH_UP".equals(key)) return 166;
        if("CH_DOWN".equals(key)) return 167;
        if("PLAY".equals(key)) return 85;
        return 0;
    }

    private boolean sendTvKey(TvDevice d,String key){
        if(d==null) return false;
        if("ROKU".equals(d.type)) return sendRokuKey(d.ip,key);
        if("SAMSUNG".equals(d.type)) return sendSamsungKey(d.ip,key);
        if("ANDROID_TV".equals(d.type)){
            int code=androidTvKeyCode(key);
            return code!=0 && androidTvV2!=null && androidTvV2.sendKey(code);
        }
        return false;
    }

    private void saveConnectedTv(TvDevice d){
        getSharedPreferences("sts",0).edit()
                .putString("tv_name",d.name)
                .putString("tv_ip",d.ip)
                .putString("tv_type",d.type)
                .apply();
    }

    private void connectAndroidTv(TvDevice d,TextView status,Runnable connected){
        if(androidTvV2==null) androidTvV2=new AndroidTvV2(this);
        status.setText(L("Connecting to Android TV...","Android TV से कनेक्ट कर रहे हैं..."));

        new Thread(()->{
            try{
                if(androidTvV2.connect(d.ip)){
                    runOnUiThread(()->{
                        saveConnectedTv(d);
                        status.setText(L("Connected: ","कनेक्टेड: ")+d.name);
                        connected.run();
                    });
                    return;
                }
            }catch(Exception ignored){}

            try{
                androidTvV2.startPairing(d.ip);
                runOnUiThread(()->{
                    final EditText pin=new EditText(this);
                    pin.setHint("A1B2C3");
                    pin.setSingleLine(true);
                    pin.setTextSize(22);
                    pin.setGravity(Gravity.CENTER);
                    pin.setAllCaps(true);
                    pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
                    pin.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                            |android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                    pin.setPadding(dp(16),dp(12),dp(16),dp(12));
                    pin.setBackground(fieldBg());

                    AlertDialog dialog=new AlertDialog.Builder(this)
                            .setTitle(L("PAIR ANDROID TV","ANDROID TV PAIR करें"))
                            .setMessage(L(
                                    "A 6-character code should now be visible on the TV. Enter that code below.",
                                    "TV स्क्रीन पर अब 6-character code दिखना चाहिए। वही code नीचे दर्ज करें।"))
                            .setView(pin)
                            .setPositiveButton(L("PAIR","PAIR करें"),null)
                            .setNegativeButton(L("CANCEL","रद्द करें"),null)
                            .create();

                    dialog.setOnShowListener(x->{
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                            String code=pin.getText().toString().trim().toUpperCase(java.util.Locale.US);
                            if(code.length()!=6 || !code.matches("[0-9A-F]{6}")){
                                pin.setError(L("Enter the 6-character TV code","TV का 6-character code दर्ज करें"));
                                return;
                            }
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                            status.setText(L("Pairing with Android TV...","Android TV pair हो रहा है..."));

                            new Thread(()->{
                                try{
                                    boolean ok=androidTvV2.finishPairing(code);
                                    runOnUiThread(()->{
                                        dialog.dismiss();
                                        if(ok){
                                            saveConnectedTv(d);
                                            status.setText(L("Paired & connected: ","Pair और कनेक्टेड: ")+d.name);
                                            connected.run();
                                        }else{
                                            status.setText(L("Pairing completed but connection failed. Tap Connect again.",
                                                    "Pairing हुआ, लेकिन connection नहीं हुआ। फिर Connect दबाएं।"));
                                        }
                                    });
                                }catch(Exception ex){
                                    runOnUiThread(()->{
                                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                        status.setText(L("Pairing failed: ","Pairing failed: ")+
                                                (ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()));
                                    });
                                }
                            }).start();
                        });
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
                            try{androidTvV2.closePairing();}catch(Exception ignored){}
                            dialog.dismiss();
                            status.setText(L("Pairing cancelled","Pairing रद्द किया गया"));
                        });
                    });
                    dialog.show();
                });
            }catch(Exception ex){
                runOnUiThread(()->{
                    String detail=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
                    String msg=L(
                            "Android TV pairing could not start. Both devices must be on the same Wi-Fi and Android TV Remote Service must be enabled.\n\nTechnical detail: ",
                            "Android TV pairing शुरू नहीं हुआ। दोनों device एक ही Wi-Fi पर हों और Android TV Remote Service चालू हो।\n\nTechnical detail: ")+detail;
                    status.setText(msg);
                    new AlertDialog.Builder(this)
                            .setTitle(L("ANDROID TV CONNECTION","ANDROID TV CONNECTION"))
                            .setMessage(msg)
                            .setPositiveButton("OK",null)
                            .show();
                });
            }
        }).start();
    }

    private void showRemote(){
        currentTool="REMOTE";
        if(androidTvV2==null) androidTvV2=new AndroidTvV2(this);
        shell(L("SMART TV REMOTE","स्मार्ट TV रिमोट"));

        TextView status=tv(L("Same Wi-Fi TV remote","एक ही Wi-Fi पर TV रिमोट"),17,SOFT);
        status.setGravity(Gravity.CENTER);
        styleResult(status);
        root.addView(status,controlParams(92));

        Button scan=btn(L("AUTO FIND TV ON WI-FI","Wi-Fi पर TV खोजें"));
        root.addView(scan,controlParams(58));

        Spinner devices=dropdown(new String[]{L("No TV selected","कोई TV चुना नहीं")});
        root.addView(devices,controlParams(58));

        Button connect=btn(L("CONNECT / SAVE TV","TV कनेक्ट / सेव करें"));
        root.addView(connect,controlParams(58));

        LinearLayout remoteBox=new LinearLayout(this);
        remoteBox.setOrientation(LinearLayout.VERTICAL);
        remoteBox.setPadding(dp(4),dp(4),dp(4),dp(4));
        remoteBox.setBackground(grad(Color.rgb(13,31,52),Color.rgb(20,50,75),14));
        root.addView(remoteBox,new LinearLayout.LayoutParams(-1,0,1));

        java.util.ArrayList<TvDevice> found=new java.util.ArrayList<>();
        final TvDevice[] active={null};

        java.util.function.BiConsumer<String,String> addRemoteButton=(label,key)->{};

        java.util.function.Consumer<String[]> addRow=(String[] specs)->{
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for(String spec:specs){
                String[] parts=spec.split("\\|",2);
                String label=parts[0];
                String key=parts.length>1?parts[1]:"";
                Button b=btn(label);
                row.addView(b,new LinearLayout.LayoutParams(0,dp(58),1));
                b.setOnClickListener(v->{
                    TvDevice d=active[0];
                    if(d==null){
                        Toast.makeText(this,L("Connect a TV first","पहले TV कनेक्ट करें"),Toast.LENGTH_SHORT).show();
                        return;
                    }
                    status.setText(L("Sending: ","भेज रहे हैं: ")+label);
                    new Thread(()->{
                        boolean ok=sendTvKey(d,key);
                        runOnUiThread(()->{
                            if(ok) status.setText(L("Connected: ","कनेक्टेड: ")+d.name);
                            else status.setText(L("Command failed. Reconnect TV or allow remote access on TV.","कमांड नहीं गया। TV दोबारा कनेक्ट करें या TV पर remote access allow करें।"));
                        });
                    }).start();
                });
            }
            remoteBox.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        };

        addRow.accept(new String[]{"⏻|POWER","⌂|HOME","↩|BACK"});
        addRow.accept(new String[]{"VOL −|VOL_DOWN","▲|UP","VOL +|VOL_UP"});
        addRow.accept(new String[]{"◀|LEFT","OK|OK","▶|RIGHT"});
        addRow.accept(new String[]{"CH −|CH_DOWN","▼|DOWN","CH +|CH_UP"});
        addRow.accept(new String[]{"MUTE|MUTE","PLAY|PLAY"});

        Runnable refreshSpinner=()->{
            java.util.ArrayList<String> names=new java.util.ArrayList<>();
            if(found.isEmpty()) names.add(L("No TV found","कोई TV नहीं मिला"));
            else for(TvDevice d:found) names.add(d.toString());

            ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,names){
                @Override public View getView(int pos,View convert,android.view.ViewGroup parent){
                    TextView t=(TextView)super.getView(pos,convert,parent);
                    t.setTextColor(WHITE);t.setTextSize(16);t.setPadding(dp(12),0,dp(12),0);t.setBackgroundColor(PANEL2);
                    return t;
                }
                @Override public View getDropDownView(int pos,View convert,android.view.ViewGroup parent){
                    TextView t=(TextView)super.getDropDownView(pos,convert,parent);
                    t.setTextColor(WHITE);t.setTextSize(16);t.setPadding(dp(12),dp(12),dp(12),dp(12));t.setBackgroundColor(PANEL);
                    return t;
                }
            };
            devices.setAdapter(adapter);
        };

        scan.setOnClickListener(v->{
            scan.setEnabled(false);
            status.setText(L("Searching TVs on this Wi-Fi...","इस Wi-Fi पर TV खोज रहे हैं..."));
            new Thread(()->{
                java.util.ArrayList<TvDevice> list=discoverTvs();
                runOnUiThread(()->{
                    found.clear();
                    found.addAll(list);
                    refreshSpinner.run();
                    scan.setEnabled(true);
                    if(found.isEmpty()){
                        status.setText(L("No compatible TV discovered. Phone and TV must be on the same Wi-Fi.","TV नहीं मिला। Phone और TV एक ही Wi-Fi पर होना चाहिए।"));
                    }else{
                        status.setText(found.size()+" "+L("TV device(s) found. Select one and tap Connect.",
                                "TV मिले। एक चुनें और Connect दबाएं।"));
                    }
                });
            }).start();
        });

        connect.setOnClickListener(v->{
            if(found.isEmpty()){
                scan.performClick();
                return;
            }

            int pos=devices.getSelectedItemPosition();
            if(pos<0 || pos>=found.size()) pos=0;
            TvDevice d=found.get(pos);

            if("UPNP".equals(d.type) && (portOpen(d.ip,6466,600) || portOpen(d.ip,6467,600))){
                d.type="ANDROID_TV";
                refreshSpinner.run();
            }

            if("ANDROID_TV".equals(d.type)){
                final TvDevice selected=d;
                connectAndroidTv(selected,status,()->active[0]=selected);
                return;
            }

            if("ROKU".equals(d.type) || "SAMSUNG".equals(d.type)){
                active[0]=d;
                saveConnectedTv(d);
                status.setText(L("Connected: ","कनेक्टेड: ")+d.name);
                return;
            }

            status.setText(d.name+" "+L(
                    "was detected, but this TV uses a different Wi-Fi remote protocol.",
                    "detect हुआ है, लेकिन यह TV अलग Wi-Fi remote protocol इस्तेमाल करता है।"));
        });

        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        String savedIp=sp.getString("tv_ip","");
        String savedType=sp.getString("tv_type","");
        String savedName=sp.getString("tv_name","Saved TV");

        if(!savedIp.isEmpty()){
            TvDevice saved=new TvDevice(savedName,savedIp,savedType);
            found.add(saved);
            refreshSpinner.run();
            status.setText(L("Reconnecting saved TV...","सेव TV दोबारा कनेक्ट कर रहे हैं..."));

            if("ANDROID_TV".equals(savedType)){
                new Thread(()->{
                    boolean ok=false;
                    try{ok=androidTvV2.connect(saved.ip);}catch(Exception ignored){}
                    final boolean connectedOk=ok;
                    runOnUiThread(()->{
                        if(connectedOk){
                            active[0]=saved;
                            status.setText(L("Auto connected: ","ऑटो कनेक्ट: ")+saved.name);
                        }else{
                            status.setText(L("Saved Android TV needs pairing/reconnect. Tap CONNECT / SAVE TV.",
                                    "सेव Android TV को pairing/reconnect चाहिए। CONNECT / SAVE TV दबाएं।"));
                        }
                    });
                }).start();
            }else{
                new Thread(()->{
                    boolean ok=checkTvDevice(saved);
                    runOnUiThread(()->{
                        if(ok){
                            active[0]=saved;
                            status.setText(L("Auto connected: ","ऑटो कनेक्ट: ")+saved.name);
                        }else{
                            status.setText(L("Saved TV not reachable. Tap Auto Find TV.",
                                    "सेव TV नहीं मिला। Auto Find TV दबाएं।"));
                        }
                    });
                }).start();
            }
        }else{
            scan.performClick();
        }
    }

    private void showUnitConverter(){
        currentTool="UNIT";
        shell(L("UNIT CONVERTER","यूनिट कन्वर्टर"));

        Spinner type=dropdown(new String[]{
                "Kilometer → Mile","Mile → Kilometer","Kilogram → Pound",
                "Pound → Kilogram","Celsius → Fahrenheit","Fahrenheit → Celsius"
        });
        root.addView(type,controlParams(58));

        EditText in=input(L("Value","मान"));
        root.addView(in);

        Button go=btn(L("CONVERT","बदलें"));
        root.addView(go,controlParams(60));

        TextView out=tv(L("Converted value will appear here","परिवर्तित मान यहाँ दिखेगा"),24,WHITE);
        styleResult(out);
        root.addView(out,new LinearLayout.LayoutParams(-1,0,1));
        addHistoryShareBar(root,"unit",L("UNIT CONVERTER","यूनिट कन्वर्टर"),out);

        go.setOnClickListener(v->{
            double x=val(in),y=0;
            switch(type.getSelectedItemPosition()){
                case 0:y=x*0.621371;break;
                case 1:y=x/0.621371;break;
                case 2:y=x*2.20462;break;
                case 3:y=x/2.20462;break;
                case 4:y=x*9/5+32;break;
                case 5:y=(x-32)*5/9;break;
            }
            String res=String.valueOf(type.getSelectedItem())+"\n"+trim(x)+" → "+trim(y);
            out.setText(res);
            savePanelHistory("unit",L("UNIT CONVERTER","यूनिट कन्वर्टर"),res);
        });
    }

    private class SpeedometerView extends View{
        private double speed=0;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

        SpeedometerView(Context context){ super(context); }

        void setSpeed(double value){
            speed=Math.max(0,value);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            float cx=w/2f;
            float cy=h*0.62f;
            float r=Math.min(w*0.40f,h*0.48f);
            RectF arc=new RectF(cx-r,cy-r,cx+r,cy+r);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(16));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(43,76,105));
            canvas.drawArc(arc,135,270,false,p);

            p.setStrokeWidth(dp(12));
            p.setColor(TEAL);
            canvas.drawArc(arc,135,90,false,p);
            p.setColor(ACCENT);
            canvas.drawArc(arc,225,90,false,p);
            p.setColor(ORANGE);
            canvas.drawArc(arc,315,90,false,p);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(dp(12));
            p.setColor(SOFT);

            for(int i=0;i<=10;i++){
                double a=Math.toRadians(135+(270.0*i/10.0));
                float x1=(float)(cx+Math.cos(a)*(r-dp(27)));
                float y1=(float)(cy+Math.sin(a)*(r-dp(27)));
                String label=String.valueOf(i*50);
                canvas.drawText(label,x1,y1+dp(4),p);
            }

            double shown=Math.min(speed,500.0);
            double ang=Math.toRadians(135+(shown/500.0)*270.0);
            float nx=(float)(cx+Math.cos(ang)*(r-dp(46)));
            float ny=(float)(cy+Math.sin(ang)*(r-dp(46)));

            p.setColor(WHITE);
            p.setStrokeWidth(dp(5));
            p.setStyle(Paint.Style.STROKE);
            canvas.drawLine(cx,cy,nx,ny,p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(ACCENT);
            canvas.drawCircle(cx,cy,dp(10),p);

            p.setColor(WHITE);
            p.setTextSize(dp(30));
            p.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(new DecimalFormat("0.0").format(speed),cx,cy-dp(48),p);

            p.setTextSize(dp(13));
            p.setColor(SOFT);
            canvas.drawText("Mbps",cx,cy-dp(24),p);
        }
    }

    private double speedPing() throws Exception{
        long sum=0;
        int ok=0;
        for(int i=0;i<3;i++){
            java.net.HttpURLConnection con=null;
            try{
                long st=System.nanoTime();
                java.net.URL url=new java.net.URL("https://speed.cloudflare.com/cdn-cgi/trace?x="+System.nanoTime());
                con=(java.net.HttpURLConnection)url.openConnection();
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.setUseCaches(false);
                con.setRequestProperty("Cache-Control","no-cache");
                java.io.InputStream in=con.getInputStream();
                byte[] b=new byte[512];
                while(in.read(b)>0){}
                in.close();
                long ms=(System.nanoTime()-st)/1000000L;
                sum+=ms;
                ok++;
            }finally{
                if(con!=null) con.disconnect();
            }
        }
        return ok==0?0:(double)sum/ok;
    }

    private double speedDownload(java.util.function.Consumer<Double> live) throws Exception{
        int[] sizes={10000000,5000000,2000000,1000000};
        Exception last=null;

        for(int bytesWanted:sizes){
            java.net.HttpURLConnection con=null;
            long bytes=0;
            long st=System.nanoTime();
            try{
                java.net.URL url=new java.net.URL("https://speed.cloudflare.com/__down?bytes="+bytesWanted+"&cache="+System.nanoTime());
                con=(java.net.HttpURLConnection)url.openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(18000);
                con.setUseCaches(false);
                con.setInstanceFollowRedirects(true);
                con.setRequestProperty("Cache-Control","no-cache");
                con.setRequestProperty("Pragma","no-cache");
                con.setRequestProperty("Accept","*/*");
                con.setRequestProperty("Accept-Encoding","identity");
                con.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android) STS-DigiKit/1.0.20");
                con.setRequestProperty("Referer","https://speed.cloudflare.com/");
                con.setRequestProperty("Origin","https://speed.cloudflare.com");

                int code=con.getResponseCode();
                if(code<200 || code>=400) throw new java.io.IOException("HTTP "+code);

                java.io.InputStream in=new java.io.BufferedInputStream(con.getInputStream(),65536);
                byte[] buf=new byte[65536];
                int n;
                long lastUpdate=st;

                while((n=in.read(buf))!=-1){
                    bytes+=n;
                    long now=System.nanoTime();
                    if(now-lastUpdate>220000000L){
                        double sec=(now-st)/1_000_000_000.0;
                        double mbps=sec<=0?0:(bytes*8.0/1_000_000.0)/sec;
                        live.accept(mbps);
                        lastUpdate=now;
                    }
                }
                in.close();

                double sec=(System.nanoTime()-st)/1_000_000_000.0;
                double mbps=sec<=0?0:(bytes*8.0/1_000_000.0)/sec;
                if(bytes>0 && mbps>0) return mbps;
                throw new java.io.IOException("No download data");
            }catch(Exception e){
                last=e;
            }finally{
                if(con!=null) con.disconnect();
            }
        }

        if(last!=null) throw last;
        throw new java.io.IOException("Download test failed");
    }

    private double speedUpload(java.util.function.Consumer<Double> live) throws Exception{
        int[] sizes={3000000,1500000,750000};
        Exception last=null;

        for(int totalBytes:sizes){
            java.net.HttpURLConnection con=null;
            long sent=0;
            long st=System.nanoTime();
            try{
                java.net.URL url=new java.net.URL("https://speed.cloudflare.com/__up");
                con=(java.net.HttpURLConnection)url.openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(18000);
                con.setDoOutput(true);
                con.setRequestMethod("POST");
                con.setUseCaches(false);
                con.setInstanceFollowRedirects(true);
                con.setRequestProperty("Content-Type","application/octet-stream");
                con.setRequestProperty("Accept","*/*");
                con.setRequestProperty("Cache-Control","no-cache");
                con.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android) STS-DigiKit/1.0.20");
                con.setRequestProperty("Referer","https://speed.cloudflare.com/");
                con.setRequestProperty("Origin","https://speed.cloudflare.com");
                con.setFixedLengthStreamingMode(totalBytes);

                java.io.OutputStream out=new java.io.BufferedOutputStream(con.getOutputStream(),65536);
                byte[] buf=new byte[32768];
                new java.security.SecureRandom().nextBytes(buf);
                long lastUpdate=st;

                while(sent<totalBytes){
                    int n=(int)Math.min(buf.length,totalBytes-sent);
                    out.write(buf,0,n);
                    sent+=n;
                    long now=System.nanoTime();
                    if(now-lastUpdate>220000000L){
                        double sec=(now-st)/1_000_000_000.0;
                        double mbps=sec<=0?0:(sent*8.0/1_000_000.0)/sec;
                        live.accept(mbps);
                        lastUpdate=now;
                    }
                }
                out.flush();
                out.close();

                int code=con.getResponseCode();
                java.io.InputStream in=(code>=400)?con.getErrorStream():con.getInputStream();
                if(in!=null){
                    byte[] drain=new byte[1024];
                    while(in.read(drain)>0){}
                    in.close();
                }
                if(code<200 || code>=400) throw new java.io.IOException("HTTP "+code);

                double sec=(System.nanoTime()-st)/1_000_000_000.0;
                double mbps=sec<=0?0:(sent*8.0/1_000_000.0)/sec;
                if(sent>0 && mbps>0) return mbps;
                throw new java.io.IOException("No upload data");
            }catch(Exception e){
                last=e;
            }finally{
                if(con!=null) con.disconnect();
            }
        }

        if(last!=null) throw last;
        throw new java.io.IOException("Upload test failed");
    }

    private void openSpeedTest(){
        currentTool="SPEED";
        shell(L("INTERNET SPEED TEST","इंटरनेट स्पीड टेस्ट"));

        SpeedometerView meter=new SpeedometerView(this);
        meter.setBackground(grad(Color.rgb(12,31,51),Color.rgb(18,47,72),16));
        root.addView(meter,new LinearLayout.LayoutParams(-1,0,1));

        TextView status=tv(L("Ready to test your connection","कनेक्शन टेस्ट के लिए तैयार"),16,SOFT);
        status.setGravity(Gravity.CENTER);
        root.addView(status,controlParams(40));

        LinearLayout metrics=new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);

        TextView ping=tv("PING\n-- ms",16,WHITE);
        TextView down=tv("DOWNLOAD\n-- Mbps",16,WHITE);
        TextView up=tv("UPLOAD\n-- Mbps",16,WHITE);
        for(TextView t:new TextView[]{ping,down,up}){
            t.setGravity(Gravity.CENTER);
            t.setTypeface(null,1);
            t.setBackground(bg(PANEL,10));
        }
        metrics.addView(ping,new LinearLayout.LayoutParams(0,dp(72),1));
        metrics.addView(down,new LinearLayout.LayoutParams(0,dp(72),1));
        metrics.addView(up,new LinearLayout.LayoutParams(0,dp(72),1));
        root.addView(metrics,controlParams(74));

        TextView result=new TextView(this);
        result.setText(L("Run a test to create a result","टेस्ट चलाकर परिणाम बनाएं"));

        LinearLayout speedBar=addHistoryShareBar(root,"speed",L("INTERNET SPEED TEST","इंटरनेट स्पीड टेस्ट"),result);
        if(speedBar.getChildCount()>0) speedBar.getChildAt(0).setBackground(touchBg(PURPLE,10));
        if(speedBar.getChildCount()>1) speedBar.getChildAt(1).setBackground(touchBg(ORANGE,10));

        Button start=btn(L("START SPEED TEST","स्पीड टेस्ट शुरू करें"));
        start.setTextSize(21);
        start.setBackground(touchBg(GREEN,14));
        root.addView(start,controlParams(88));

        start.setOnClickListener(v->{
            start.setEnabled(false);
            start.setText(L("TESTING...","टेस्ट चल रहा है..."));
            status.setText(L("Measuring ping...","पिंग माप रहे हैं..."));
            ping.setText("PING\n-- ms");
            down.setText("DOWNLOAD\n-- Mbps");
            up.setText("UPLOAD\n-- Mbps");
            meter.setSpeed(0);
            result.setText(L("Testing connection...","कनेक्शन टेस्ट हो रहा है..."));

            new Thread(()->{
                double pms=0,dm=0,um=0;
                String pingError=null,downloadError=null,uploadError=null;

                try{
                    pms=speedPing();
                }catch(Exception e){
                    pingError=e.getClass().getSimpleName();
                }

                final double fpNow=pms;
                final String pe=pingError;
                runOnUiThread(()->{
                    if(pe==null) ping.setText("PING\n"+new DecimalFormat("0").format(fpNow)+" ms");
                    else ping.setText("PING\n-- ms");
                    status.setText(L("Testing download speed...","डाउनलोड स्पीड टेस्ट हो रही है..."));
                });

                try{
                    dm=speedDownload(value->runOnUiThread(()->{
                        meter.setSpeed(value);
                        down.setText("DOWNLOAD\n"+new DecimalFormat("0.0").format(value)+" Mbps");
                    }));
                }catch(Exception e){
                    downloadError=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                }

                final double fdNow=dm;
                final String de=downloadError;
                runOnUiThread(()->{
                    if(de==null){
                        meter.setSpeed(fdNow);
                        down.setText("DOWNLOAD\n"+new DecimalFormat("0.0").format(fdNow)+" Mbps");
                    }else{
                        down.setText("DOWNLOAD\nFAILED");
                    }
                    status.setText(L("Testing upload speed...","अपलोड स्पीड टेस्ट हो रही है..."));
                });

                try{
                    um=speedUpload(value->runOnUiThread(()->{
                        meter.setSpeed(value);
                        up.setText("UPLOAD\n"+new DecimalFormat("0.0").format(value)+" Mbps");
                    }));
                }catch(Exception e){
                    uploadError=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
                }

                final double fp=pms,fd=dm,fu=um;
                final String fpe=pingError,fde=downloadError,fue=uploadError;
                runOnUiThread(()->{
                    start.setEnabled(true);
                    start.setText(L("TEST AGAIN","फिर से टेस्ट करें"));

                    boolean any=(fpe==null)||(fde==null)||(fue==null);
                    boolean all=(fpe==null)&&(fde==null)&&(fue==null);

                    if(fpe==null) ping.setText("PING\n"+new DecimalFormat("0").format(fp)+" ms");
                    if(fde==null) down.setText("DOWNLOAD\n"+new DecimalFormat("0.0").format(fd)+" Mbps");
                    if(fue==null) up.setText("UPLOAD\n"+new DecimalFormat("0.0").format(fu)+" Mbps");

                    if(all){
                        meter.setSpeed(fd);
                        status.setText(L("Test completed","टेस्ट पूरा हुआ"));
                    }else if(any){
                        status.setText(L("Test partially completed. Retry for missing values.","टेस्ट आंशिक रूप से पूरा हुआ। जो value नहीं आई उसके लिए फिर टेस्ट करें।"));
                    }else{
                        status.setText(L("Speed test failed. Check internet and retry.","स्पीड टेस्ट नहीं हो सका। इंटरनेट जांचकर फिर कोशिश करें।"));
                    }

                    String summary="Ping: "+(fpe==null?new DecimalFormat("0").format(fp)+" ms":"Failed")
                            +"\nDownload: "+(fde==null?new DecimalFormat("0.0").format(fd)+" Mbps":"Failed")
                            +"\nUpload: "+(fue==null?new DecimalFormat("0.0").format(fu)+" Mbps":"Failed");

                    result.setText(summary);
                    if(any) savePanelHistory("speed",L("INTERNET SPEED TEST","इंटरनेट स्पीड टेस्ट"),summary);
                });
            }).start();
        });
    }

    private static class QuickBillItem{
        String name;
        double qty;
        double rate;
        double gst;
        QuickBillItem(String name,double qty,double rate,double gst){
            this.name=name;
            this.qty=qty;
            this.rate=rate;
            this.gst=gst;
        }
    }

    private String qbCenter(String s,int width){
        String x=s==null?"":s;
        if(x.length()>=width) return x;
        int left=(width-x.length())/2;
        StringBuilder b=new StringBuilder();
        for(int i=0;i<left;i++) b.append(' ');
        b.append(x);
        return b.toString();
    }

    private String qbPadRight(String s,int width){
        String x=s==null?"":s;
        if(x.length()>width) x=x.substring(0,width);
        StringBuilder b=new StringBuilder(x);
        while(b.length()<width) b.append(' ');
        return b.toString();
    }

    private String qbPadLeft(String s,int width){
        String x=s==null?"":s;
        if(x.length()>width) x=x.substring(x.length()-width);
        StringBuilder b=new StringBuilder();
        while(b.length()+x.length()<width) b.append(' ');
        b.append(x);
        return b.toString();
    }

    private QuickBillItem pendingQuickBillItem(EditText item,EditText qty,EditText rate,EditText gst){
        String itemName=item.getText().toString().trim();
        String qRaw=qty.getText().toString().trim();
        String rRaw=rate.getText().toString().trim();
        String gRaw=gst.getText().toString().trim();

        if(itemName.isEmpty() && qRaw.isEmpty() && rRaw.isEmpty() && gRaw.isEmpty()) return null;

        double q=val(qty);
        double r=val(rate);
        double g=val(gst);
        if(itemName.isEmpty() || q<=0 || r<0) return null;

        return new QuickBillItem(itemName,q,r,Math.max(0,g));
    }

    private String buildQuickBillPreview(String shopCompanyName,EditText customer,
                                         java.util.List<QuickBillItem> addedItems,
                                         EditText item,EditText qty,EditText rate,EditText gst,
                                         String billNo,String billDate){
        String customerName=customer.getText().toString().trim();
        String shopName=shopCompanyName==null?"":shopCompanyName.trim();

        java.util.ArrayList<QuickBillItem> all=new java.util.ArrayList<>();
        if(addedItems!=null) all.addAll(addedItems);

        QuickBillItem pending=pendingQuickBillItem(item,qty,rate,gst);
        if(pending!=null) all.add(pending);

        if(customerName.isEmpty() && all.isEmpty()){
            return L("Bill preview will appear here","बिल यहाँ दिखाई देगा");
        }

        final int W=34;
        final String line="----------------------------------";

        double subTotal=0;
        double gstTotal=0;

        StringBuilder res=new StringBuilder();
        res.append(qbCenter(shopName.isEmpty()?"STS DIGIKIT":shopName,W)).append("\n");
        res.append(qbCenter(L("INVOICE","बिल"),W)).append("\n");
        res.append(line).append("\n");
        res.append(L("Date: ","दिनांक: ")).append(billDate).append("\n");
        res.append(L("Bill No: ","बिल नं: ")).append(billNo).append("\n");
        if(!customerName.isEmpty()){
            res.append(L("Customer: ","ग्राहक: ")).append(customerName).append("\n");
        }
        res.append(line).append("\n");

        res.append(qbPadRight(L("Item","आइटम"),18))
                .append(qbPadLeft(L("Qty","मात्रा"),6))
                .append(qbPadLeft(L("Amount","राशि"),10)).append("\n");
        res.append(line).append("\n");

        for(QuickBillItem row:all){
            double base=row.qty*row.rate;
            double rowGst=base*row.gst/100.0;
            subTotal+=base;
            gstTotal+=rowGst;

            res.append(qbPadRight(row.name,18))
                    .append(qbPadLeft(trim(row.qty),6))
                    .append(qbPadLeft(df.format(base),10))
                    .append("\n");

            if(row.gst>0){
                String gstLine=L("GST ","GST ")+trim(row.gst)+"%";
                res.append(qbPadRight("  "+gstLine,24))
                        .append(qbPadLeft(df.format(rowGst),10))
                        .append("\n");
            }
        }

        double total=subTotal+gstTotal;

        res.append(line).append("\n");
        res.append(qbPadRight(L("Sub Total","उप-योग"),23))
                .append(qbPadLeft(df.format(subTotal),11)).append("\n");

        if(gstTotal>0){
            res.append(qbPadRight(L("GST","जीएसटी"),23))
                    .append(qbPadLeft(df.format(gstTotal),11)).append("\n");
        }

        res.append(line).append("\n");
        res.append(qbPadRight(L("TOTAL","कुल"),22))
                .append(qbPadLeft("Rs "+df.format(total),12)).append("\n");
        res.append(line).append("\n");
        res.append(qbPadRight(L("Cash","नकद"),22))
                .append(qbPadLeft("Rs "+df.format(total),12)).append("\n");
        res.append(qbPadRight(L("Cash Given","प्राप्त नकद"),22))
                .append(qbPadLeft("Rs "+df.format(total),12)).append("\n");
        return res.toString();
    }

    private void printQuickBill58mm(String content){
        if(!meaningfulResult(content)){
            Toast.makeText(this,L("Nothing to print yet","अभी print करने के लिए bill नहीं है"),Toast.LENGTH_SHORT).show();
            return;
        }

        try{
            android.print.PrintManager pm=(android.print.PrintManager)getSystemService(Context.PRINT_SERVICE);
            if(pm==null){
                Toast.makeText(this,L("Print service is not available","Print service उपलब्ध नहीं है"),Toast.LENGTH_SHORT).show();
                return;
            }

            final String receipt=content.trim();
            final String[] receiptLines=receipt.split("\\n",-1);
            final int pageWidthPt=164;
            final int lineHeightPt=11;
            final int pageHeightPt=Math.max(240,36+(receiptLines.length*lineHeightPt));
            final int mediaHeightMils=Math.max(3333,Math.round(pageHeightPt*1000f/72f));

            android.print.PrintDocumentAdapter adapter=new android.print.PrintDocumentAdapter(){
                @Override public void onLayout(android.print.PrintAttributes oldAttributes,
                                               android.print.PrintAttributes newAttributes,
                                               android.os.CancellationSignal cancellationSignal,
                                               LayoutResultCallback callback,
                                               Bundle extras){
                    if(cancellationSignal.isCanceled()){
                        callback.onLayoutCancelled();
                        return;
                    }
                    android.print.PrintDocumentInfo info=new android.print.PrintDocumentInfo.Builder("STS-DigiKit-Bill.pdf")
                            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build();
                    callback.onLayoutFinished(info,true);
                }

                @Override public void onWrite(android.print.PageRange[] pages,
                                              android.os.ParcelFileDescriptor destination,
                                              android.os.CancellationSignal cancellationSignal,
                                              WriteResultCallback callback){
                    android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();
                    try{
                        android.graphics.pdf.PdfDocument.PageInfo pageInfo=
                                new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidthPt,pageHeightPt,1).create();
                        android.graphics.pdf.PdfDocument.Page page=pdf.startPage(pageInfo);
                        android.graphics.Canvas canvas=page.getCanvas();

                        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                        paint.setColor(Color.BLACK);
                        paint.setTextSize("HINDI".equals(language)?7.2f:7.6f);
                        paint.setTypeface("HINDI".equals(language)
                                ?android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL)
                                :android.graphics.Typeface.MONOSPACE);

                        final float left=2.84f;
                        final float usable=pageWidthPt-5.68f;

                        float size="HINDI".equals(language)?7.5f:7.6f;
                        paint.setTextSize(size);
                        float widest=0f;
                        for(String measureLine:receiptLines){
                            widest=Math.max(widest,paint.measureText(measureLine));
                        }
                        while(widest>usable && size>5.8f){
                            size-=0.2f;
                            paint.setTextSize(size);
                            widest=0f;
                            for(String measureLine:receiptLines){
                                widest=Math.max(widest,paint.measureText(measureLine));
                            }
                        }

                        float y=14f;

                        for(String lineText:receiptLines){
                            if(cancellationSignal.isCanceled()){
                                pdf.finishPage(page);
                                callback.onWriteCancelled();
                                pdf.close();
                                return;
                            }

                            String remaining=lineText;
                            if(remaining.length()==0){
                                y+=lineHeightPt;
                                continue;
                            }

                            while(remaining.length()>0){
                                int fit=paint.breakText(remaining,true,usable,null);
                                if(fit<=0) fit=Math.min(1,remaining.length());
                                String part=remaining.substring(0,fit);
                                canvas.drawText(part,left,y,paint);
                                y+=lineHeightPt;
                                remaining=remaining.substring(fit);
                            }
                        }

                        pdf.finishPage(page);

                        java.io.FileOutputStream out=new java.io.FileOutputStream(destination.getFileDescriptor());
                        pdf.writeTo(out);
                        out.close();
                        callback.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});
                    }catch(Exception e){
                        callback.onWriteFailed(e.getMessage());
                    }finally{
                        try{pdf.close();}catch(Exception ignored){}
                    }
                }
            };

            android.print.PrintAttributes attrs=new android.print.PrintAttributes.Builder()
                    .setMediaSize(new android.print.PrintAttributes.MediaSize(
                            "STS_58MM","Receipt",2283,mediaHeightMils))
                    .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                    .setColorMode(android.print.PrintAttributes.COLOR_MODE_MONOCHROME)
                    .build();

            pm.print(L("STS DigiKit Bill","STS DigiKit बिल"),adapter,attrs);
        }catch(Exception e){
            Toast.makeText(this,L("Could not open print screen","Print screen नहीं खुल सकी"),Toast.LENGTH_SHORT).show();
        }
    }

    private void showQuickBill(){
        currentTool="BILL";
        shell(L("QUICK BILL","क्विक बिल"));

        final String billNo="QB"+new java.text.SimpleDateFormat("ddHHmmss",java.util.Locale.US).format(new java.util.Date());
        final String billDate=new java.text.SimpleDateFormat("dd/MM/yyyy, hh:mm a",java.util.Locale.getDefault()).format(new java.util.Date());
        final java.util.ArrayList<QuickBillItem> billItems=new java.util.ArrayList<>();

        android.content.SharedPreferences qbPrefs=getSharedPreferences("sts",0);
        final String[] shopCompanyName={qbPrefs.getString("quick_bill_default_shop_name","")};

        EditText customer=input(L("Customer Name","ग्राहक का नाम"));
        customer.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        customer.setPadding(dp(14),dp(8),dp(62),dp(8));

        FrameLayout customerFrame=new FrameLayout(this);
        customerFrame.addView(customer,new FrameLayout.LayoutParams(-1,dp(54)));

        Button customerMenu=btn("⋮");
        customerMenu.setTextSize(26);
        customerMenu.setMinWidth(dp(52));
        FrameLayout.LayoutParams menuParams=new FrameLayout.LayoutParams(dp(52),dp(46),Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        menuParams.setMargins(0,0,dp(4),0);
        customerFrame.addView(customerMenu,menuParams);

        LinearLayout.LayoutParams customerFrameParams=new LinearLayout.LayoutParams(-1,dp(54));
        customerFrameParams.setMargins(0,dp(4),0,dp(4));
        root.addView(customerFrame,customerFrameParams);

        EditText name=input(L("Item Name","आइटम नाम"));
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        EditText qty=input(L("Quantity","मात्रा"));
        EditText rate=input(L("Rate ₹","दर ₹"));
        EditText gst=input(L("GST %","GST %"));

        LinearLayout itemRow=new LinearLayout(this);
        itemRow.setOrientation(LinearLayout.HORIZONTAL);
        itemRow.setGravity(Gravity.CENTER_VERTICAL);

        Button addItem=btn("+");
        addItem.setTextSize(28);
        addItem.setMinWidth(dp(56));

        LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(0,dp(54),1);
        nameParams.setMargins(0,dp(4),dp(4),dp(4));
        itemRow.addView(name,nameParams);

        LinearLayout.LayoutParams plusParams=new LinearLayout.LayoutParams(dp(58),dp(54));
        plusParams.setMargins(0,dp(4),0,dp(4));
        itemRow.addView(addItem,plusParams);
        root.addView(itemRow,new LinearLayout.LayoutParams(-1,dp(62)));

        LinearLayout valuesRow=new LinearLayout(this);
        valuesRow.setOrientation(LinearLayout.HORIZONTAL);
        valuesRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams qtyParams=new LinearLayout.LayoutParams(0,dp(54),1);
        qtyParams.setMargins(0,dp(4),dp(2),dp(4));
        LinearLayout.LayoutParams rateParams=new LinearLayout.LayoutParams(0,dp(54),1);
        rateParams.setMargins(dp(2),dp(4),dp(2),dp(4));
        LinearLayout.LayoutParams gstParams=new LinearLayout.LayoutParams(0,dp(54),1);
        gstParams.setMargins(dp(2),dp(4),0,dp(4));

        valuesRow.addView(qty,qtyParams);
        valuesRow.addView(rate,rateParams);
        valuesRow.addView(gst,gstParams);
        root.addView(valuesRow,new LinearLayout.LayoutParams(-1,dp(62)));

        TextView out=tv(L("Bill preview will appear here","बिल यहाँ दिखाई देगा"),14,WHITE);
        out.setTypeface("HINDI".equals(language)
                ?android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL)
                :android.graphics.Typeface.MONOSPACE);
        out.setGravity(Gravity.TOP|Gravity.LEFT);
        out.setTextIsSelectable(true);
        out.setPadding(dp(12),dp(12),dp(12),dp(12));
        out.setBackground(grad(PANEL2,Color.rgb(28,96,132),12));
        root.addView(out,new LinearLayout.LayoutParams(-1,0,1));

        addHistoryShareBar(root,"bill",L("QUICK BILL","क्विक बिल"),out);

        Button print=btn(L("PRINT","प्रिंट"));
        root.addView(print,controlParams(60));

        Runnable updateBill=()->out.setText(
                buildQuickBillPreview(shopCompanyName[0],customer,billItems,name,qty,rate,gst,billNo,billDate));

        customerMenu.setOnClickListener(v->{
            android.widget.PopupMenu menu=new android.widget.PopupMenu(this,customerMenu);
            menu.getMenu().add(L("Set Shop / Company Name","दुकान / कंपनी नाम सेट करें"));
            menu.setOnMenuItemClickListener(itemMenu->{
                EditText defaultName=new EditText(this);
                defaultName.setSingleLine(true);
                defaultName.setTextColor(Color.BLACK);
                defaultName.setTextSize(18);
                defaultName.setPadding(dp(12),dp(8),dp(12),dp(8));
                String saved=qbPrefs.getString("quick_bill_default_shop_name","");
                defaultName.setText(saved==null?"":saved);
                defaultName.setSelection(defaultName.getText().length());

                new AlertDialog.Builder(this)
                        .setTitle(L("Shop / Company Name","दुकान / कंपनी नाम"))
                        .setView(defaultName)
                        .setNegativeButton(L("CANCEL","रद्द करें"),null)
                        .setPositiveButton(L("SAVE","सेव करें"),(d,w)->{
                            String value=defaultName.getText().toString().trim();
                            qbPrefs.edit().putString("quick_bill_default_shop_name",value).apply();
                            shopCompanyName[0]=value;
                            updateBill.run();
                        })
                        .show();
                return true;
            });
            menu.show();
        });

        android.text.TextWatcher watcher=new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){ updateBill.run(); }
            @Override public void afterTextChanged(android.text.Editable e){}
        };

        customer.addTextChangedListener(watcher);
        name.addTextChangedListener(watcher);
        qty.addTextChangedListener(watcher);
        rate.addTextChangedListener(watcher);
        gst.addTextChangedListener(watcher);

        addItem.setOnClickListener(v->{
            QuickBillItem pending=pendingQuickBillItem(name,qty,rate,gst);
            if(pending==null){
                Toast.makeText(this,
                        L("Enter Item Name, Quantity and Rate","आइटम नाम, मात्रा और दर भरें"),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            billItems.add(pending);
            name.setText("");
            qty.setText("");
            rate.setText("");
            gst.setText("");
            updateBill.run();
        });

        print.setOnClickListener(v->{
            String bill=buildQuickBillPreview(shopCompanyName[0],customer,billItems,name,qty,rate,gst,billNo,billDate);
            out.setText(bill);
            if(meaningfulResult(bill)){
                savePanelHistory("bill",L("QUICK BILL","क्विक बिल"),bill);
                printQuickBill58mm(bill);
            }
        });

        updateBill.run();
    }

    private String scanParam(android.net.Uri uri,String key){
        try{
            String v=uri.getQueryParameter(key);
            return v==null?"":v.trim();
        }catch(Exception e){
            return "";
        }
    }

    private String parseWifiPart(String raw,String key){
        try{
            String body=raw.substring(5);
            java.util.regex.Matcher m=java.util.regex.Pattern
                    .compile("(?:^|;)"+java.util.regex.Pattern.quote(key)+":((?:\\\\.|[^;])*)")
                    .matcher(body);
            if(m.find()) return m.group(1).replace("\\;",";").replace("\\:"," : ").replace("\\\\","\\");
        }catch(Exception ignored){}
        return "";
    }

    private String scanDetails(String raw,com.google.zxing.BarcodeFormat format){
        String value=raw==null?"":raw.trim();
        StringBuilder b=new StringBuilder();
        b.append(L("FORMAT: ","फॉर्मेट: ")).append(format==null?"UNKNOWN":format.toString()).append("\n");

        try{
            android.net.Uri uri=android.net.Uri.parse(value);
            String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(java.util.Locale.US);

            if("upi".equals(scheme)){
                String pa=scanParam(uri,"pa");
                String pn=scanParam(uri,"pn");
                String am=scanParam(uri,"am");
                String cu=scanParam(uri,"cu");
                String tn=scanParam(uri,"tn");
                String tr=scanParam(uri,"tr");
                String mc=scanParam(uri,"mc");

                b.append(L("TYPE: UPI PAYMENT QR","प्रकार: UPI PAYMENT QR")).append("\n");
                if(!pn.isEmpty()) b.append(L("Name: ","नाम: ")).append(pn).append("\n");
                if(!pa.isEmpty()) b.append("UPI ID: ").append(pa).append("\n");
                if(!am.isEmpty()) b.append(L("Amount: ₹","राशि: ₹")).append(am).append("\n");
                if(!cu.isEmpty()) b.append(L("Currency: ","मुद्रा: ")).append(cu).append("\n");
                if(!tn.isEmpty()) b.append(L("Note: ","नोट: ")).append(tn).append("\n");
                if(!tr.isEmpty()) b.append(L("Reference: ","रेफरेंस: ")).append(tr).append("\n");
                if(!mc.isEmpty()) b.append(L("Merchant Category: ","मर्चेंट कैटेगरी: ")).append(mc).append("\n");
            }else if(value.startsWith("WIFI:")){
                b.append(L("TYPE: WI-FI QR","प्रकार: WI-FI QR")).append("\n");
                String ssid=parseWifiPart(value,"S");
                String type=parseWifiPart(value,"T");
                String pass=parseWifiPart(value,"P");
                if(!ssid.isEmpty()) b.append("SSID: ").append(ssid).append("\n");
                if(!type.isEmpty()) b.append(L("Security: ","सिक्योरिटी: ")).append(type).append("\n");
                if(!pass.isEmpty()) b.append(L("Password: ","पासवर्ड: ")).append(pass).append("\n");
            }else if("http".equals(scheme) || "https".equals(scheme)){
                b.append(L("TYPE: WEBSITE / URL","प्रकार: WEBSITE / URL")).append("\n");
                b.append("URL: ").append(value).append("\n");
            }else if("tel".equals(scheme)){
                b.append(L("TYPE: PHONE NUMBER","प्रकार: फोन नंबर")).append("\n");
                b.append(L("Number: ","नंबर: ")).append(uri.getSchemeSpecificPart()).append("\n");
            }else if("mailto".equals(scheme)){
                b.append(L("TYPE: EMAIL","प्रकार: ईमेल")).append("\n");
                b.append("Email: ").append(uri.getSchemeSpecificPart()).append("\n");
            }else if(format!=null && format!=com.google.zxing.BarcodeFormat.QR_CODE){
                b.append(L("TYPE: BARCODE","प्रकार: BARCODE")).append("\n");
                b.append(L("Value: ","वैल्यू: ")).append(value).append("\n");
            }else{
                b.append(L("TYPE: QR DATA","प्रकार: QR DATA")).append("\n");
                b.append(L("Value: ","वैल्यू: ")).append(value).append("\n");
            }
        }catch(Exception e){
            b.append(L("Value: ","वैल्यू: ")).append(value).append("\n");
        }

        b.append("\n").append(L("RAW DATA:","RAW DATA:")).append("\n").append(value);
        return b.toString();
    }

    private String scannerWebLink(String value){
        if(value==null) return "";
        String s=value.trim();
        if(s.matches("(?i)^https?://\\S+$")) return s;
        if(s.matches("(?i)^www\\.\\S+$")) return "https://"+s;
        try{
            java.util.regex.Matcher m=java.util.regex.Pattern
                    .compile("(?i)https?://[^\\s]+")
                    .matcher(s);
            if(m.find()) return m.group();
        }catch(Exception ignored){}
        return "";
    }

    private void renderScannerDetails(String details,String raw){
        if(scannerViewport==null) return;

        scannerViewport.removeAllViews();

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        TextView result=tv("",19,WHITE);
        result.setGravity(Gravity.CENTER);
        result.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        result.setPadding(dp(18),dp(18),dp(18),dp(18));
        result.setBackground(grad(PANEL,Color.rgb(23,52,88),0));

        String link=scannerWebLink(raw);
        if(link.isEmpty()){
            result.setText(details);
            result.setTextIsSelectable(true);
        }else{
            String label=details+"\n\n"+L("LINK: ","लिंक: ")+link;
            android.text.SpannableString span=new android.text.SpannableString(label);
            int pos=label.lastIndexOf(link);
            if(pos>=0){
                span.setSpan(new android.text.style.ClickableSpan(){
                    @Override public void onClick(View widget){
                        try{
                            Intent open=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(link));
                            startActivity(open);
                        }catch(Exception e){
                            Toast.makeText(MainActivity.this,L("Browser could not open this link","Browser इस link को नहीं खोल सका"),Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void updateDrawState(android.text.TextPaint ds){
                        super.updateDrawState(ds);
                        ds.setColor(Color.rgb(40,145,255));
                        ds.setUnderlineText(true);
                    }
                },pos,pos+link.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            result.setText(span);
            result.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            result.setHighlightColor(Color.TRANSPARENT);
        }

        scannerDetails=result;
        scroll.addView(result,new ScrollView.LayoutParams(-1,-1));
        scannerViewport.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
    }

    private void handleScannerResult(String value,com.google.zxing.BarcodeFormat format){
        if(value==null || value.trim().isEmpty()) return;

        scannerResultLocked=true;
        scannerActive=false;
        try{if(embeddedScanner!=null)embeddedScanner.pause();}catch(Throwable ignored){}

        String details=scanDetails(value,format);
        lastScannerRaw=value;
        lastScannerDetails=details;

        savePanelHistory("scanner",L("QR / BARCODE SCANNER","QR / बारकोड स्कैनर"),details);
        haptic();
        renderScannerDetails(details,value);
    }

    private android.graphics.Bitmap loadGalleryBitmap(android.net.Uri uri) throws Exception{
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;

        java.io.InputStream first=getContentResolver().openInputStream(uri);
        if(first==null) throw new java.io.IOException("Image could not be opened");
        android.graphics.BitmapFactory.decodeStream(first,null,bounds);
        first.close();

        int sample=1;
        int max=Math.max(bounds.outWidth,bounds.outHeight);
        while(max/sample>2200) sample*=2;

        android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();
        opts.inSampleSize=Math.max(1,sample);
        opts.inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888;

        java.io.InputStream in=getContentResolver().openInputStream(uri);
        if(in==null) throw new java.io.IOException("Image could not be opened");
        android.graphics.Bitmap bm=android.graphics.BitmapFactory.decodeStream(in,null,opts);
        in.close();

        if(bm==null) throw new java.io.IOException("Unsupported image");
        return bm;
    }

    private com.google.zxing.Result decodeBitmapOnce(android.graphics.Bitmap bm) throws Exception{
        int w=bm.getWidth(),h=bm.getHeight();
        int[] pixels=new int[w*h];
        bm.getPixels(pixels,0,w,0,0,w,h);

        com.google.zxing.RGBLuminanceSource source=
                new com.google.zxing.RGBLuminanceSource(w,h,pixels);

        java.util.EnumMap<com.google.zxing.DecodeHintType,Object> hints=
                new java.util.EnumMap<>(com.google.zxing.DecodeHintType.class);
        hints.put(com.google.zxing.DecodeHintType.TRY_HARDER,Boolean.TRUE);
        hints.put(com.google.zxing.DecodeHintType.CHARACTER_SET,"UTF-8");
        hints.put(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS,
                java.util.EnumSet.allOf(com.google.zxing.BarcodeFormat.class));

        com.google.zxing.MultiFormatReader reader=new com.google.zxing.MultiFormatReader();
        reader.setHints(hints);

        try{
            return reader.decodeWithState(new com.google.zxing.BinaryBitmap(
                    new com.google.zxing.common.HybridBinarizer(source)));
        }catch(Exception first){
            reader.reset();
            com.google.zxing.LuminanceSource inv=source.invert();
            return reader.decode(new com.google.zxing.BinaryBitmap(
                    new com.google.zxing.common.HybridBinarizer(inv)),hints);
        }
    }

    private com.google.zxing.Result decodeGalleryImage(android.graphics.Bitmap original) throws Exception{
        Exception last=null;
        android.graphics.Bitmap current=original;

        for(int i=0;i<4;i++){
            try{
                return decodeBitmapOnce(current);
            }catch(Exception e){
                last=e;
            }

            if(i<3){
                android.graphics.Matrix m=new android.graphics.Matrix();
                m.postRotate(90);
                android.graphics.Bitmap rotated=android.graphics.Bitmap.createBitmap(
                        current,0,0,current.getWidth(),current.getHeight(),m,true);
                if(current!=original && current!=rotated) current.recycle();
                current=rotated;
            }
        }

        if(current!=original){
            try{current.recycle();}catch(Exception ignored){}
        }
        if(last!=null) throw last;
        throw com.google.zxing.NotFoundException.getNotFoundInstance();
    }

    private void pickScannerImageFromGallery(){
        try{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");
            startActivityForResult(pick,REQ_GALLERY_SCAN);
        }catch(Exception e){
            if(scannerDetails!=null){
                scannerDetails.setText(L("Gallery could not be opened.","Gallery नहीं खुल सकी।"));
            }
        }
    }

    private void configureEmbeddedScanner(){
        if(embeddedScanner==null) return;

        java.util.EnumMap<com.google.zxing.DecodeHintType,Object> scanHints=
                new java.util.EnumMap<>(com.google.zxing.DecodeHintType.class);
        scanHints.put(com.google.zxing.DecodeHintType.TRY_HARDER,Boolean.TRUE);
        scanHints.put(com.google.zxing.DecodeHintType.CHARACTER_SET,"UTF-8");

        java.util.Collection<com.google.zxing.BarcodeFormat> allFormats=
                java.util.EnumSet.allOf(com.google.zxing.BarcodeFormat.class);

        embeddedScanner.getBarcodeView().setDecoderFactory(
                new com.journeyapps.barcodescanner.DefaultDecoderFactory(
                        allFormats,scanHints,"UTF-8",2));

        embeddedScanner.decodeContinuous(new com.journeyapps.barcodescanner.BarcodeCallback(){
            @Override public void barcodeResult(com.journeyapps.barcodescanner.BarcodeResult result){
                if(result==null || result.getText()==null || scannerResultLocked) return;
                handleScannerResult(result.getText(),result.getBarcodeFormat());
            }
            @Override public void possibleResultPoints(java.util.List<com.google.zxing.ResultPoint> resultPoints){}
        });
    }

    private void showScannerCameraInViewport(){
        if(scannerViewport==null) return;

        scannerViewport.removeAllViews();
        embeddedScanner=new com.journeyapps.barcodescanner.DecoratedBarcodeView(this);
        embeddedScanner.setBackgroundColor(Color.BLACK);
        embeddedScanner.setStatusText("");
        configureEmbeddedScanner();
        scannerViewport.addView(embeddedScanner,new FrameLayout.LayoutParams(-1,-1));

        scannerResultLocked=false;
        scannerActive=true;

        try{
            embeddedScanner.resume();
        }catch(Throwable e){
            scannerActive=false;
            renderScannerDetails(
                    L("Camera could not start. Close other camera apps and try again.",
                      "Camera शुरू नहीं हुआ। दूसरे camera apps बंद करके फिर कोशिश करें।"),
                    "");
        }
    }

    private void showScanner(){
        currentTool="SCAN";
        toolPickerOpen=false;
        scannerActive=false;
        scannerResultLocked=false;
        scannerFullScreen=false;
        try{if(embeddedScanner!=null)embeddedScanner.pause();}catch(Throwable ignored){}
        embeddedScanner=null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(screenBg());
        addFixedDropdown(outer,L("QR / BARCODE SCANNER","QR / बारकोड स्कैनर"));

        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackground(screenBg());
        body.setPadding(0,0,0,0);
        outer.addView(body,new LinearLayout.LayoutParams(-1,0,1));

        scannerViewport=new FrameLayout(this);
        scannerViewport.setBackground(screenBg());
        body.addView(scannerViewport,new LinearLayout.LayoutParams(-1,0,1));

        Button start=btn(L("START SCANNER","स्कैनर शुरू करें"));
        Button gallery=btn(L("GALLERY PICKUP","गैलरी से चुनें"));
        body.addView(start,controlParams(60));
        body.addView(gallery,controlParams(60));

        LinearLayout secondary=new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        Button history=btn(L("HISTORY","हिस्ट्री"));
        Button share=btn(L("SHARE RESULT","RESULT शेयर"));
        secondary.addView(history,new LinearLayout.LayoutParams(0,dp(54),1));
        secondary.addView(share,new LinearLayout.LayoutParams(0,dp(54),1));
        body.addView(secondary,new LinearLayout.LayoutParams(-1,dp(56)));

        setContentView(outer);

        if(lastScannerDetails==null || lastScannerDetails.isEmpty()){
            renderScannerDetails(
                    L("Result will appear here automatically after QR / Barcode detection.",
                      "QR / Barcode detect होते ही result यहाँ अपने-आप दिखाई देगा।"),
                    "");
        }else{
            renderScannerDetails(lastScannerDetails,lastScannerRaw);
        }

        start.setOnClickListener(v->startEmbeddedScanner());
        gallery.setOnClickListener(v->pickScannerImageFromGallery());

        history.setOnClickListener(v->
                showPanelHistory("scanner",L("QR / BARCODE SCANNER","QR / बारकोड स्कैनर")));

        share.setOnClickListener(v->{
            if(lastScannerDetails==null || lastScannerDetails.trim().isEmpty()){
                Toast.makeText(this,L("Scan something first","पहले QR / Barcode scan करें"),Toast.LENGTH_SHORT).show();
            }else{
                sharePanelText(L("SCAN RESULT","स्कैन परिणाम"),lastScannerDetails);
            }
        });
    }

    private void startEmbeddedScanner(){
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)){
            renderScannerDetails(L("Camera is not available on this device.","इस डिवाइस में कैमरा उपलब्ध नहीं है।"),"");
            return;
        }

        if(Build.VERSION.SDK_INT>=23 &&
                checkSelfPermission(android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            renderScannerDetails(
                    L("Allow Camera permission to start scanning.",
                      "Scanning शुरू करने के लिए Camera permission Allow करें।"),
                    "");
            try{
                requestPermissions(new String[]{android.Manifest.permission.CAMERA},REQ_EMBEDDED_SCANNER_CAMERA);
            }catch(Throwable e){
                renderScannerDetails(L("Camera permission request failed.","Camera permission request नहीं हो सकी।"),"");
            }
            return;
        }

        showScannerCameraInViewport();
    }

    private void stopEmbeddedScanner(){
        scannerActive=false;
        try{if(embeddedScanner!=null)embeddedScanner.pause();}catch(Throwable ignored){}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_EMBEDDED_SCANNER_CAMERA){
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                if("SCAN".equals(currentTool)) startEmbeddedScanner();
            }else{
                scannerActive=false;
                if(scannerDetails!=null){
                    scannerDetails.setText(L(
                            "Camera permission denied. You can still use GALLERY PICKUP.",
                            "Camera permission नहीं मिली। फिर भी GALLERY PICKUP से photo scan कर सकते हैं।"));
                }
            }
        }
    }

    @Override protected void onPause(){
        super.onPause();
        if(embeddedScanner!=null){
            try{embeddedScanner.pause();}catch(Throwable ignored){}
        }
    }

    @Override protected void onResume(){
        super.onResume();
        if("SCAN".equals(currentTool) && scannerActive && embeddedScanner!=null){
            if(Build.VERSION.SDK_INT<23 ||
                    checkSelfPermission(android.Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){
                try{embeddedScanner.resume();}catch(Throwable ignored){}
            }
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode==REQ_GALLERY_SCAN){
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null){
                final android.net.Uri uri=data.getData();

                if(scannerDetails!=null){
                    scannerDetails.setText(L("Reading QR / Barcode from selected image...",
                            "चुनी हुई photo से QR / Barcode पढ़ रहे हैं..."));
                    scannerDetails.setTextColor(SOFT);
                }

                new Thread(()->{
                    android.graphics.Bitmap bm=null;
                    try{
                        bm=loadGalleryBitmap(uri);
                        com.google.zxing.Result result=decodeGalleryImage(bm);
                        final String value=result.getText();
                        final com.google.zxing.BarcodeFormat format=result.getBarcodeFormat();

                        runOnUiThread(()->handleScannerResult(value,format));
                    }catch(Exception e){
                        runOnUiThread(()->{
                            if(scannerDetails!=null){
                                scannerDetails.setText(L(
                                        "No readable QR / Barcode found in this image. Try a clearer or closer image.",
                                        "इस photo में readable QR / Barcode नहीं मिला। साफ या नजदीक वाली photo चुनें।"));
                                scannerDetails.setTextColor(WHITE);
                            }
                        });
                    }finally{
                        if(bm!=null){
                            try{bm.recycle();}catch(Exception ignored){}
                        }
                    }
                }).start();
            }
            return;
        }
        super.onActivityResult(requestCode,resultCode,data);
    }

    @Override public void onBackPressed(){
        if("SCAN".equals(currentTool) && scannerActive){
            try{if(embeddedScanner!=null)embeddedScanner.pause();}catch(Throwable ignored){}
            embeddedScanner=null;
            scannerActive=false;
            scannerResultLocked=false;
            if(lastScannerDetails!=null && !lastScannerDetails.isEmpty()){
                renderScannerDetails(lastScannerDetails,lastScannerRaw);
            }else{
                renderScannerDetails(
                        L("Result will appear here automatically after QR / Barcode detection.",
                          "QR / Barcode detect होते ही result यहाँ अपने-आप दिखाई देगा।"),
                        "");
            }
            return;
        }
        if("SCAN".equals(currentTool) && embeddedScanner!=null){
            try{embeddedScanner.pause();}catch(Throwable ignored){}
            embeddedScanner=null;
            scannerActive=false;
        }
        if(toolPickerOpen){
            toolPickerOpen=false;
            reopenCurrentTool();
        }else{
            finish();
        }
    }
}
