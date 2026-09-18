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
import java.util.Calendar;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(9,18,34);
    private final int PANEL = Color.rgb(28,42,74);
    private final int PANEL2 = Color.rgb(31,70,104);
    private final int ACCENT = Color.rgb(38,183,255);
    private final int SOFT = Color.rgb(155,174,210);
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
    private Button btn(String s){
        Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(WHITE); b.setTextSize(16);
        b.setPadding(dp(10),dp(8),dp(10),dp(8));
        b.setBackground(bg(PANEL2,8)); return b;
    }
    private EditText input(String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(SOFT); e.setTextColor(WHITE);
        e.setTextSize(18); e.setSingleLine(true); e.setPadding(dp(14),dp(8),dp(14),dp(8));
        e.setBackground(bg(PANEL,8)); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
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
        t.setBackground(bg(PANEL2,10));
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
        s.setBackground(bg(PANEL2,8));
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
        else if("SCAN".equals(key)) scanCode();
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
        outer.setBackgroundColor(BG);

        addFixedDropdown(outer,name);

        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.BOTTOM);
        root.setPadding(0,0,0,0);
        sc.addView(root,new ScrollView.LayoutParams(-1,-2));
        outer.addView(sc,new LinearLayout.LayoutParams(-1,0,1));

        setContentView(outer);
        return sc;
    }

    private void addFixedDropdown(LinearLayout outer, String currentName){
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0,0,0,0);
        header.setBackgroundColor(PANEL2);

        TextView menu=tv("⋮",34,WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(bg(PANEL,0));
        header.addView(menu,new LinearLayout.LayoutParams(dp(56),dp(64)));

        TextView label=tv(currentName,21,WHITE);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(null,1);
        header.addView(label,new LinearLayout.LayoutParams(0,dp(64),1));

        TextView spacer=tv("",1,WHITE);
        header.addView(spacer,new LinearLayout.LayoutParams(dp(56),dp(64)));

        outer.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));

        menu.setOnClickListener(v->showTopMenu(menu));
        label.setOnClickListener(v->showToolPicker(currentName));
        spacer.setOnClickListener(v->showToolPicker(currentName));
    }

    private void showToolPicker(String currentName){
        toolPickerOpen=true;

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(PANEL2);

        TextView menu=tv("⋮",34,WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(bg(PANEL,0));
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
        listScroll.setBackgroundColor(BG);
        listScroll.setVerticalScrollBarEnabled(true);
        listScroll.setScrollbarFadingEnabled(false);

        LinearLayout tools=new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setBackgroundColor(BG);

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
        row.setBackground(bg(selected?ACCENT:PANEL,0));
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
                                "Version 1.0.14\nOffline utility toolkit\nChange the dropdown item order from the three-dot menu.",
                                "संस्करण 1.0.14\nऑफलाइन यूटिलिटी टूलकिट\nThree-dot मेनू से dropdown items का क्रम ऊपर-नीचे बदल सकते हैं।"))
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

        TextView about=tv("Version 1.0.14\nOffline utility toolkit\nCalculator • QR • Scanner • Finance tools",17,SOFT);
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

    private double evaluateCalcExpression(String exp){
        String s=calcCompleteExpression(exp);
        if(s.isEmpty()) return 0;

        String[] t=s.split("\\s+");
        if(t.length==0) return 0;

        double current=Double.parseDouble(t[0]);
        double total=0;
        char addOp='+';

        for(int i=1;i+1<t.length;i+=2){
            String op=t[i];
            double n=Double.parseDouble(t[i+1]);

            if("×".equals(op)){
                current*=n;
            }else if("÷".equals(op)){
                if(n==0) throw new ArithmeticException("divide by zero");
                current/=n;
            }else if("+".equals(op) || "-".equals(op)){
                total+=(addOp=='+')?current:-current;
                current=n;
                addOp=op.charAt(0);
            }
        }
        total+=(addOp=='+')?current:-current;
        return total;
    }

    private void showCalculator(){
        currentTool="CALCULATOR";
        toolPickerOpen=false;

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);

        addFixedDropdown(outer,L("CALCULATOR","कैलकुलेटर"));

        LinearLayout calcBody=new LinearLayout(this);
        calcBody.setOrientation(LinearLayout.VERTICAL);
        calcBody.setBackgroundColor(BG);
        outer.addView(calcBody,new LinearLayout.LayoutParams(-1,0,1));

        final TextView typing=tv("",28,WHITE);
        typing.setGravity(Gravity.RIGHT|Gravity.BOTTOM);
        typing.setPadding(dp(18),dp(16),dp(18),dp(16));
        typing.setBackgroundColor(BG);
        typing.setTextIsSelectable(false);
        calcBody.addView(typing,new LinearLayout.LayoutParams(-1,0,1));

        final TextView display=tv("0",32,WHITE);
        display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        display.setTypeface(null,1);
        display.setPadding(dp(18),dp(10),dp(18),dp(10));
        display.setBackground(bg(PANEL,8));
        LinearLayout.LayoutParams displayParams=new LinearLayout.LayoutParams(-1,dp(80));
        displayParams.setMargins(0,0,0,dp(4));
        calcBody.addView(display,displayParams);

        final String[] expression={""};
        final boolean[] justEvaluated={false};

        Runnable refreshLive=()->{
            typing.setText(expression[0]);
            String complete=calcCompleteExpression(expression[0]);
            if(complete.isEmpty()){
                display.setText("0");
                return;
            }
            try{
                display.setText(trim(evaluateCalcExpression(complete)));
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
                    double ans=evaluateCalcExpression(complete);
                    typing.setText(complete+" =");
                    display.setText(trim(ans));
                    expression[0]=trim(ans);
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
                    double v=Double.parseDouble(number)/100.0;
                    expression[0]=s.substring(0,i+1)+trim(v);
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

    private void showCashCounter(){
        currentTool="CASH_COUNTER"; shell(L("CASH COUNTER","कैश काउंटर"));
        TextView total=tv("TOTAL ₹0",28,WHITE); styleResult(total); root.addView(total,resultParams(76));
        int[] den={500,200,100,50,20,10,5,2,1}; java.util.ArrayList<EditText> qty=new java.util.ArrayList<>();
        for(int d:den){
            LinearLayout line=new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
            TextView n=tv("₹"+d,20,WHITE); line.addView(n,new LinearLayout.LayoutParams(0,dp(58),1));
            EditText q=input("Qty"); q.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); qty.add(q); line.addView(q,new LinearLayout.LayoutParams(dp(130),dp(54)));
            root.addView(line);
        }
        Button calc=btn("CALCULATE TOTAL"); root.addView(calc,controlParams(60));
        calc.setOnClickListener(v->{long sum=0;for(int i=0;i<den.length;i++){long q=0;try{q=Long.parseLong(qty.get(i).getText().toString());}catch(Exception ignored){}sum+=q*den[i];} total.setText("TOTAL ₹"+String.format(java.util.Locale.US,"%,d",sum));});
    }

    private void showAge(){
        currentTool="AGE"; shell(L("AGE CALCULATOR","आयु कैलकुलेटर"));
        final Calendar dob=Calendar.getInstance(), asof=Calendar.getInstance();
        Button bd=btn("SELECT DATE OF BIRTH"); Button td=btn("CALCULATE UP TO TODAY"); Button go=btn("CALCULATE AGE");
        root.addView(bd,controlParams(60)); root.addView(td,controlParams(60)); root.addView(go,controlParams(60));
        TextView out=tv("Select date of birth",21,WHITE); styleResult(out); root.addView(out,resultParams(120));
        bd.setOnClickListener(v->pickDate(dob, c->bd.setText(date(c))));
        td.setOnClickListener(v->pickDate(asof, c->td.setText("UP TO: "+date(c))));
        go.setOnClickListener(v->{Calendar a=(Calendar)asof.clone();Calendar b=(Calendar)dob.clone();if(a.before(b)){out.setText("Invalid date");return;}int y=a.get(Calendar.YEAR)-b.get(Calendar.YEAR);int m=a.get(Calendar.MONTH)-b.get(Calendar.MONTH);int d=a.get(Calendar.DAY_OF_MONTH)-b.get(Calendar.DAY_OF_MONTH);if(d<0){m--;Calendar prev=(Calendar)a.clone();prev.add(Calendar.MONTH,-1);d+=prev.getActualMaximum(Calendar.DAY_OF_MONTH);}if(m<0){y--;m+=12;}long days=(a.getTimeInMillis()-b.getTimeInMillis())/86400000L;out.setText(y+" Years  "+m+" Months  "+d+" Days\nTotal Days: "+days);});
    }
    interface DateCb{void done(Calendar c);}
    private void pickDate(Calendar c,DateCb cb){new DatePickerDialog(this,(v,y,m,d)->{c.set(y,m,d,12,0,0);cb.done(c);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    private String date(Calendar c){return String.format(java.util.Locale.US,"%02d/%02d/%04d",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.YEAR));}

    private void showSavings(){
        currentTool="SAVINGS"; shell(L("RD / FD / SIP CALCULATOR","आरडी / एफडी / एसआईपी कैलकुलेटर"));
        Spinner mode=dropdown(new String[]{"SIP","RD","FD"});
        root.addView(mode,controlParams(58));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);root.addView(box);
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){ financeBox(box,String.valueOf(mode.getSelectedItem())); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        financeBox(box,"SIP");
    }
    private void financeBox(LinearLayout box,String mode){
        box.removeAllViews();
        EditText p=input(mode.equals("FD")?"Principal Amount":"Monthly Amount"); EditText rate=input("Annual Interest %"); EditText years=input("Years");
        box.addView(p);box.addView(rate);box.addView(years);Button calc=btn("CALCULATE "+mode);box.addView(calc,controlParams(60));TextView out=tv("",20,WHITE);styleResult(out);box.addView(out,resultParams(110));
        calc.setOnClickListener(v->{double P=val(p),r=val(rate)/100.0,t=val(years),fv=0,invested=0;if(mode.equals("FD")){fv=P*Math.pow(1+r/4.0,4*t);invested=P;}else{double i=r/12.0;int n=(int)Math.round(t*12);invested=P*n;if(i==0)fv=invested;else fv=P*((Math.pow(1+i,n)-1)/i)*(mode.equals("SIP")?(1+i):1);}out.setText("Invested: ₹"+df.format(invested)+"\nMaturity: ₹"+df.format(fv)+"\nGain: ₹"+df.format(fv-invested));});
    }

    private void showEmiInterest(){
        currentTool="EMI"; shell(L("EMI / INTEREST CALCULATOR","ईएमआई / ब्याज कैलकुलेटर"));
        Spinner mode=dropdown(new String[]{"EMI","SIMPLE INTEREST"});
        root.addView(mode,controlParams(58));
        EditText loan=input("Loan / Principal Amount");EditText rate=input("Annual Interest %");EditText months=input("Tenure in Months");
        root.addView(loan);root.addView(rate);root.addView(months);
        Button calc=btn("CALCULATE");root.addView(calc,controlParams(58));
        TextView out=tv("",20,WHITE);styleResult(out);root.addView(out,resultParams(120));
        calc.setOnClickListener(v->{
            double P=val(loan);
            if(mode.getSelectedItemPosition()==0){
                double i=val(rate)/1200.0;int n=(int)val(months);double e=i==0?(n==0?0:P/n):P*i*Math.pow(1+i,n)/(Math.pow(1+i,n)-1);double total=e*n;
                out.setText("EMI: ₹"+df.format(e)+"\nInterest: ₹"+df.format(total-P)+"\nTotal: ₹"+df.format(total));
            }else{
                double r=val(rate)/100.0,t=val(months)/12.0;double si=P*r*t;out.setText("Interest: ₹"+df.format(si)+"\nTotal: ₹"+df.format(P+si));
            }
        });
    }

    private void showNumberWords(){
        currentTool="WORDS"; shell(L("NUMBER TO WORDS","संख्या शब्दों में"));
        EditText e=input("Enter whole number");e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);root.addView(e);Button go=btn("CONVERT");root.addView(go,controlParams(60));TextView out=tv("",22,WHITE);styleResult(out);root.addView(out,resultParams(150));
        go.setOnClickListener(v->{try{long n=Long.parseLong(e.getText().toString());out.setText(indianWords(n));}catch(Exception x){out.setText("Enter valid number");}});
    }
    private String indianWords(long n){
        if(n==0)return "Zero";if(n<0)return "Minus "+indianWords(-n);String s="";
        long crore=n/10000000;n%=10000000;long lakh=n/100000;n%=100000;long thousand=n/1000;n%=1000;long hundred=n/100;n%=100; if(crore>0)s+=indianWords(crore)+" Crore ";if(lakh>0)s+=indianWords(lakh)+" Lakh ";if(thousand>0)s+=indianWords(thousand)+" Thousand ";if(hundred>0)s+=indianWords(hundred)+" Hundred ";if(n>0)s+=under100((int)n);return s.trim();
    }
    private String under100(int n){String[] a={"","One","Two","Three","Four","Five","Six","Seven","Eight","Nine","Ten","Eleven","Twelve","Thirteen","Fourteen","Fifteen","Sixteen","Seventeen","Eighteen","Nineteen"};String[] t={"","","Twenty","Thirty","Forty","Fifty","Sixty","Seventy","Eighty","Ninety"};if(n<20)return a[n];return t[n/10]+(n%10>0?" "+a[n%10]:"");}

    private void showQr(boolean wifi){
        currentTool=wifi?"WIFI_QR":"QR"; shell(wifi?L("WI-FI QR GENERATOR","वाई-फाई QR जनरेटर"):L("QR CODE GENERATOR","QR कोड जनरेटर"));
        EditText a=input(wifi?"Wi-Fi Name (SSID)":"Text / URL");root.addView(a);EditText b=null;if(wifi){b=input("Wi-Fi Password");b.setInputType(android.text.InputType.TYPE_CLASS_TEXT);root.addView(b);}
        Button go=btn("GENERATE QR");root.addView(go,controlParams(60));ImageView img=new ImageView(this);img.setAdjustViewBounds(true);root.addView(img,new LinearLayout.LayoutParams(-1,dp(340)));EditText pass=b;
        go.setOnClickListener(v->{String data=wifi?"WIFI:T:WPA;S:"+a.getText().toString()+";P:"+pass.getText().toString()+";;":a.getText().toString();Bitmap bm=qrBitmap(data,800);if(bm!=null)img.setImageBitmap(bm);});
    }
    private Bitmap qrBitmap(String data,int size){try{BitMatrix m=new MultiFormatWriter().encode(data,BarcodeFormat.QR_CODE,size,size);Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565);for(int y=0;y<size;y++)for(int x=0;x<size;x++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);return b;}catch(Exception e){Toast.makeText(this,"QR error",Toast.LENGTH_SHORT).show();return null;}}

    private void showGst(){
        currentTool="GST"; shell(L("GST / DISCOUNT CALCULATOR","GST / डिस्काउंट कैलकुलेटर"));
        EditText amt=input("Amount");EditText disc=input("Discount %");EditText gst=input("GST %");root.addView(amt);root.addView(disc);root.addView(gst);Button go=btn("CALCULATE");root.addView(go,controlParams(60));TextView out=tv("",20,WHITE);styleResult(out);root.addView(out,resultParams(130));
        go.setOnClickListener(v->{double a=val(amt),d=a*val(disc)/100.0,after=a-d,g=after*val(gst)/100.0;out.setText("Discount: ₹"+df.format(d)+"\nGST: ₹"+df.format(g)+"\nFinal: ₹"+df.format(after+g));});
    }

    private void showRemote(){
        currentTool="REMOTE"; shell(L("REMOTE","रिमोट"));
        ConsumerIrManager ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
        String msg=(ir!=null&&ir.hasIrEmitter())?"IR Blaster detected. Device-specific remote profiles अगले update में जोड़ेंगे।":"इस phone में IR Blaster उपलब्ध नहीं है।";
        TextView t=tv(msg,22,WHITE);styleResult(t);root.addView(t,resultParams(180));
    }

    private void showUnitConverter(){
        currentTool="UNIT"; shell(L("UNIT CONVERTER","यूनिट कन्वर्टर"));
        Spinner type=dropdown(new String[]{"Kilometer → Mile","Mile → Kilometer","Kilogram → Pound","Pound → Kilogram","Celsius → Fahrenheit","Fahrenheit → Celsius"});root.addView(type,controlParams(58));
        EditText in=input("Value");root.addView(in);Button go=btn("CONVERT");root.addView(go,controlParams(60));TextView out=tv("",24,WHITE);styleResult(out);root.addView(out,resultParams(110));
        go.setOnClickListener(v->{double x=val(in),y=0;switch(type.getSelectedItemPosition()){case 0:y=x*0.621371;break;case 1:y=x/0.621371;break;case 2:y=x*2.20462;break;case 3:y=x/2.20462;break;case 4:y=x*9/5+32;break;case 5:y=(x-32)*5/9;break;}out.setText(trim(y));});
    }

    private void openSpeedTest(){try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://fast.com")));}catch(Exception e){Toast.makeText(this,"Browser नहीं मिला",Toast.LENGTH_SHORT).show();}}

    private void showQuickBill(){
        currentTool="BILL"; shell(L("QUICK BILL","क्विक बिल"));
        EditText name=input("Item Name");name.setInputType(android.text.InputType.TYPE_CLASS_TEXT);EditText qty=input("Quantity");EditText rate=input("Rate ₹");EditText gst=input("GST %");
        root.addView(name);root.addView(qty);root.addView(rate);root.addView(gst);Button go=btn("MAKE BILL");root.addView(go,controlParams(60));TextView out=tv("",20,WHITE);styleResult(out);root.addView(out,resultParams(180));
        go.setOnClickListener(v->{double q=val(qty),r=val(rate),base=q*r,g=base*val(gst)/100.0;out.setText((name.getText().length()>0?name.getText().toString():"Item")+"\nQty: "+trim(q)+" × ₹"+df.format(r)+"\nSubtotal: ₹"+df.format(base)+"\nGST: ₹"+df.format(g)+"\nTOTAL: ₹"+df.format(base+g));});
    }

    private void scanCode(){
        IntentIntegrator in=new IntentIntegrator(this);
        in.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        in.setPrompt("Scan QR / Barcode"); in.setBeepEnabled(true); in.setOrientationLocked(true); in.initiateScan();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(r!=null){
            if(r.getContents()!=null)new AlertDialog.Builder(this).setTitle("Scan Result").setMessage(r.getContents()).setPositiveButton("COPY",(d,w)->{android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(android.content.ClipData.newPlainText("scan",r.getContents()));}).setNegativeButton("CLOSE",null).show();
            return;
        }
        super.onActivityResult(requestCode,resultCode,data);
    }

    @Override public void onBackPressed(){
        if(toolPickerOpen){
            toolPickerOpen=false;
            reopenCurrentTool();
        }else{
            finish();
        }
    }
}
