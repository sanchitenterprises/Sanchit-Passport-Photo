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
        logEvent("App started");
        showHome();
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
        b.setBackground(bg(PANEL2,10)); return b;
    }
    private EditText input(String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(SOFT); e.setTextColor(WHITE);
        e.setTextSize(18); e.setSingleLine(true); e.setPadding(dp(12),dp(8),dp(12),dp(8));
        e.setBackground(bg(PANEL,0)); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54)); p.setMargins(0,dp(2),0,dp(2)); e.setLayoutParams(p); return e;
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
        s.setBackground(bg(PANEL2,0));
        return s;
    }
    private ScrollView shell(String name){
        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);

        addFixedDropdown(outer,false);

        LinearLayout head=new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0,0,0,0);
        head.setBackgroundColor(PANEL);
        Button back=btn("‹");
        back.setTextSize(28);
        back.setBackground(bg(PANEL,0));
        head.addView(back,new LinearLayout.LayoutParams(dp(54),dp(50)));
        title=tv(name,20,WHITE);
        title.setGravity(Gravity.CENTER);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(50),1));
        TextView space=tv("",1,WHITE);
        head.addView(space,new LinearLayout.LayoutParams(dp(54),dp(50)));
        outer.addView(head,new LinearLayout.LayoutParams(-1,dp(50)));

        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0,0,0,0);
        sc.addView(root);
        outer.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(outer);
        back.setOnClickListener(v->showHome());
        return sc;
    }

    private void addFixedDropdown(LinearLayout outer, boolean openByDefault){
        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0,0,0,0);
        header.setBackgroundColor(PANEL2);

        TextView menu=tv("⋮",34,WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(bg(PANEL,0));
        header.addView(menu,new LinearLayout.LayoutParams(dp(56),dp(64)));

        TextView label=tv("CALCULATOR",23,WHITE);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(null,1);
        header.addView(label,new LinearLayout.LayoutParams(0,dp(64),1));

        TextView grip=tv("≡",32,ACCENT);
        grip.setGravity(Gravity.CENTER);
        grip.setContentDescription("Drag tools");
        header.addView(grip,new LinearLayout.LayoutParams(dp(58),dp(64)));

        outer.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));

        LinearLayout tools=new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setBackgroundColor(BG);

        addMenu(tools,"CALCULATOR",()->showCalculator());
        addMenu(tools,"CASH COUNTER",()->showCashCounter());
        addMenu(tools,"AGE CALCULATOR",()->showAge());
        addMenu(tools,"RD / FD / SIP CALCULATOR",()->showSavings());
        addMenu(tools,"EMI / INTEREST CALCULATOR",()->showEmiInterest());
        addMenu(tools,"NUMBER TO WORDS",()->showNumberWords());
        addMenu(tools,"QR CODE GENERATOR",()->showQr(false));
        addMenu(tools,"GST / DISCOUNT CALCULATOR",()->showGst());
        addMenu(tools,"WI-FI QR GENERATOR",()->showQr(true));
        addMenu(tools,"REMOTE",()->showRemote());
        addMenu(tools,"UNIT CONVERTER",()->showUnitConverter());
        addMenu(tools,"INTERNET SPEED TEST",()->openSpeedTest());
        addMenu(tools,"QUICK BILL",()->showQuickBill());
        addMenu(tools,"QR / BARCODE SCANNER",()->scanCode());

        ScrollView dropScroll=new ScrollView(this);
        dropScroll.addView(tools);
        dropScroll.setVisibility(openByDefault?View.VISIBLE:View.GONE);
        LinearLayout.LayoutParams dropParams=new LinearLayout.LayoutParams(-1,0);
        dropParams.weight=openByDefault?1f:0f;
        outer.addView(dropScroll,dropParams);

        View.OnClickListener toggle=v->{
            boolean opening=dropScroll.getVisibility()!=View.VISIBLE;
            dropScroll.setVisibility(opening?View.VISIBLE:View.GONE);
            LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)dropScroll.getLayoutParams();
            p.height=0;
            p.weight=opening?1f:0f;
            dropScroll.setLayoutParams(p);
        };
        label.setOnClickListener(toggle);

        final float[] downY={0f};
        final boolean[] dragged={false};
        grip.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                downY[0]=e.getRawY();
                dragged[0]=false;
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                float dy=e.getRawY()-downY[0];
                if(Math.abs(dy)>dp(18)) dragged[0]=true;
                if(dy>dp(42) && dropScroll.getVisibility()!=View.VISIBLE){
                    dropScroll.setVisibility(View.VISIBLE);
                    LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)dropScroll.getLayoutParams();
                    p.height=0; p.weight=1f; dropScroll.setLayoutParams(p);
                    downY[0]=e.getRawY();
                    haptic();
                }else if(dy<-dp(42) && dropScroll.getVisibility()==View.VISIBLE){
                    dropScroll.setVisibility(View.GONE);
                    LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)dropScroll.getLayoutParams();
                    p.height=0; p.weight=0f; dropScroll.setLayoutParams(p);
                    downY[0]=e.getRawY();
                    haptic();
                }
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP){
                if(!dragged[0]) toggle.onClick(v);
                return true;
            }
            return true;
        });

        menu.setOnClickListener(v->showTopMenu(menu));
    }

    private void showHome(){
        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);
        addFixedDropdown(outer,true);
        setContentView(outer);
    }

    private void addMenu(LinearLayout list,String s,Runnable r){
        TextView row=tv(s,20,WHITE); row.setGravity(Gravity.CENTER); row.setTypeface(null,1); row.setBackground(bg(PANEL,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(72)); p.setMargins(0,0,0,0); list.addView(row,p);
        row.setOnClickListener(v->{logEvent("Open: "+s); haptic(); r.run();});
    }

    private void showTopMenu(View anchor){
        PopupMenu p=new PopupMenu(this,anchor);
        android.view.SubMenu lang=p.getMenu().addSubMenu("LANGUAGE");
        lang.add("ENGLISH");
        lang.add("HINDI");
        p.getMenu().add("ABOUT");
        p.setOnMenuItemClickListener(item->{
            String s=item.getTitle().toString();
            if("ENGLISH".equals(s) || "HINDI".equals(s)){
                getSharedPreferences("sts",0).edit().putString("language",s).apply();
                Toast.makeText(this,"Language: "+s,Toast.LENGTH_SHORT).show();
                return true;
            }
            if("ABOUT".equals(s)){
                new AlertDialog.Builder(this)
                        .setTitle("STS DigiKit")
                        .setMessage("Version 1.0.6\nOffline utility toolkit\nCalculator • QR • Scanner • Finance tools")
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

        TextView about=tv("Version 1.0.6\nOffline utility toolkit\nCalculator • QR • Scanner • Finance tools",17,SOFT);
        about.setGravity(Gravity.CENTER); about.setBackground(bg(PANEL,10)); root.addView(about,new LinearLayout.LayoutParams(-1,dp(120)));
    }

    private void showLogs(){
        shell("VIEW LOGS");
        TextView logs=tv(appLogs.length()==0?"No logs yet":appLogs.toString(),15,WHITE);
        logs.setGravity(Gravity.TOP|Gravity.LEFT); logs.setTextIsSelectable(true); logs.setBackground(bg(PANEL,10));
        root.addView(logs,new LinearLayout.LayoutParams(-1,-2));
        Button clear=btn("CLEAR LOGS"); root.addView(clear,new LinearLayout.LayoutParams(-1,dp(58)));
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

    private void showCalculator(){
        shell("CALCULATOR");
        final TextView display=tv("0",32,WHITE); display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); display.setBackground(bg(PANEL,12)); root.addView(display,new LinearLayout.LayoutParams(-1,dp(80)));
        String[][] keys={{"7","8","9","÷"},{"4","5","6","×"},{"1","2","3","-"},{"0",".","C","+"},{"⌫","="}};
        final String[] expr={""}; final double[] first={0}; final String[] op={""};
        for(String[] row:keys){
            LinearLayout line=new LinearLayout(this);
            for(String k:row){
                Button b=btn(k); line.addView(b,new LinearLayout.LayoutParams(0,dp(62),1));
                b.setOnClickListener(v->{
                    String x=((Button)v).getText().toString();
                    if("C".equals(x)){expr[0]=""; first[0]=0;op[0]="";display.setText("0");}
                    else if("⌫".equals(x)){if(expr[0].length()>0)expr[0]=expr[0].substring(0,expr[0].length()-1);display.setText(expr[0].isEmpty()?"0":expr[0]);}
                    else if("=".equals(x)){
                        double second;try{second=Double.parseDouble(expr[0]);}catch(Exception z){second=0;}
                        double ans=second;
                        if("+".equals(op[0]))ans=first[0]+second; else if("-".equals(op[0]))ans=first[0]-second; else if("×".equals(op[0]))ans=first[0]*second; else if("÷".equals(op[0]))ans=second==0?0:first[0]/second;
                        expr[0]=String.valueOf(ans);display.setText(trim(ans));op[0]="";
                    } else if("+−×÷".contains(x)){
                        try{first[0]=Double.parseDouble(expr[0]);}catch(Exception z){first[0]=0;} op[0]=x; expr[0]=""; display.setText(x);
                    } else {expr[0]+=x;display.setText(expr[0]);}
                });
            }
            root.addView(line,new LinearLayout.LayoutParams(-1,dp(64)));
        }
    }
    private String trim(double x){ if(x==(long)x)return String.valueOf((long)x); return new DecimalFormat("0.########").format(x); }

    private void showCashCounter(){
        shell("CASH COUNTER");
        TextView total=tv("TOTAL ₹0",28,WHITE); total.setGravity(Gravity.CENTER); total.setBackground(bg(PANEL2,12)); root.addView(total,new LinearLayout.LayoutParams(-1,dp(76)));
        int[] den={500,200,100,50,20,10,5,2,1}; java.util.ArrayList<EditText> qty=new java.util.ArrayList<>();
        for(int d:den){
            LinearLayout line=new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
            TextView n=tv("₹"+d,20,WHITE); line.addView(n,new LinearLayout.LayoutParams(0,dp(58),1));
            EditText q=input("Qty"); q.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); qty.add(q); line.addView(q,new LinearLayout.LayoutParams(dp(130),dp(54)));
            root.addView(line);
        }
        Button calc=btn("CALCULATE TOTAL"); root.addView(calc,new LinearLayout.LayoutParams(-1,dp(60)));
        calc.setOnClickListener(v->{long sum=0;for(int i=0;i<den.length;i++){long q=0;try{q=Long.parseLong(qty.get(i).getText().toString());}catch(Exception ignored){}sum+=q*den[i];} total.setText("TOTAL ₹"+String.format(java.util.Locale.US,"%,d",sum));});
    }

    private void showAge(){
        shell("AGE CALCULATOR");
        final Calendar dob=Calendar.getInstance(), asof=Calendar.getInstance();
        Button bd=btn("SELECT DATE OF BIRTH"); Button td=btn("CALCULATE UP TO TODAY"); Button go=btn("CALCULATE AGE");
        root.addView(bd,new LinearLayout.LayoutParams(-1,dp(60))); root.addView(td,new LinearLayout.LayoutParams(-1,dp(60))); root.addView(go,new LinearLayout.LayoutParams(-1,dp(60)));
        TextView out=tv("Select date of birth",21,WHITE); out.setGravity(Gravity.CENTER); out.setBackground(bg(PANEL2,12)); root.addView(out,new LinearLayout.LayoutParams(-1,dp(120)));
        bd.setOnClickListener(v->pickDate(dob, c->bd.setText(date(c))));
        td.setOnClickListener(v->pickDate(asof, c->td.setText("UP TO: "+date(c))));
        go.setOnClickListener(v->{Calendar a=(Calendar)asof.clone();Calendar b=(Calendar)dob.clone();if(a.before(b)){out.setText("Invalid date");return;}int y=a.get(Calendar.YEAR)-b.get(Calendar.YEAR);int m=a.get(Calendar.MONTH)-b.get(Calendar.MONTH);int d=a.get(Calendar.DAY_OF_MONTH)-b.get(Calendar.DAY_OF_MONTH);if(d<0){m--;Calendar prev=(Calendar)a.clone();prev.add(Calendar.MONTH,-1);d+=prev.getActualMaximum(Calendar.DAY_OF_MONTH);}if(m<0){y--;m+=12;}long days=(a.getTimeInMillis()-b.getTimeInMillis())/86400000L;out.setText(y+" Years  "+m+" Months  "+d+" Days\nTotal Days: "+days);});
    }
    interface DateCb{void done(Calendar c);}
    private void pickDate(Calendar c,DateCb cb){new DatePickerDialog(this,(v,y,m,d)->{c.set(y,m,d,12,0,0);cb.done(c);},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    private String date(Calendar c){return String.format(java.util.Locale.US,"%02d/%02d/%04d",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.YEAR));}

    private void showSavings(){
        shell("RD / FD / SIP CALCULATOR");
        Spinner mode=dropdown(new String[]{"SIP","RD","FD"});
        root.addView(mode,new LinearLayout.LayoutParams(-1,dp(58)));
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
        box.addView(p);box.addView(rate);box.addView(years);Button calc=btn("CALCULATE "+mode);box.addView(calc,new LinearLayout.LayoutParams(-1,dp(60)));TextView out=tv("",20,WHITE);out.setGravity(Gravity.CENTER);out.setBackground(bg(PANEL2,12));box.addView(out,new LinearLayout.LayoutParams(-1,dp(110)));
        calc.setOnClickListener(v->{double P=val(p),r=val(rate)/100.0,t=val(years),fv=0,invested=0;if(mode.equals("FD")){fv=P*Math.pow(1+r/4.0,4*t);invested=P;}else{double i=r/12.0;int n=(int)Math.round(t*12);invested=P*n;if(i==0)fv=invested;else fv=P*((Math.pow(1+i,n)-1)/i)*(mode.equals("SIP")?(1+i):1);}out.setText("Invested: ₹"+df.format(invested)+"\nMaturity: ₹"+df.format(fv)+"\nGain: ₹"+df.format(fv-invested));});
    }

    private void showEmiInterest(){
        shell("EMI / INTEREST CALCULATOR");
        Spinner mode=dropdown(new String[]{"EMI","SIMPLE INTEREST"});
        root.addView(mode,new LinearLayout.LayoutParams(-1,dp(58)));
        EditText loan=input("Loan / Principal Amount");EditText rate=input("Annual Interest %");EditText months=input("Tenure in Months");
        root.addView(loan);root.addView(rate);root.addView(months);
        Button calc=btn("CALCULATE");root.addView(calc,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView out=tv("",20,WHITE);out.setGravity(Gravity.CENTER);out.setBackground(bg(PANEL2,0));root.addView(out,new LinearLayout.LayoutParams(-1,dp(120)));
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
        shell("NUMBER TO WORDS");
        EditText e=input("Enter whole number");e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);root.addView(e);Button go=btn("CONVERT");root.addView(go,new LinearLayout.LayoutParams(-1,dp(60)));TextView out=tv("",22,WHITE);out.setGravity(Gravity.CENTER);out.setBackground(bg(PANEL2,12));root.addView(out,new LinearLayout.LayoutParams(-1,dp(150)));
        go.setOnClickListener(v->{try{long n=Long.parseLong(e.getText().toString());out.setText(indianWords(n));}catch(Exception x){out.setText("Enter valid number");}});
    }
    private String indianWords(long n){
        if(n==0)return "Zero";if(n<0)return "Minus "+indianWords(-n);String s="";
        long crore=n/10000000;n%=10000000;long lakh=n/100000;n%=100000;long thousand=n/1000;n%=1000;long hundred=n/100;n%=100; if(crore>0)s+=indianWords(crore)+" Crore ";if(lakh>0)s+=indianWords(lakh)+" Lakh ";if(thousand>0)s+=indianWords(thousand)+" Thousand ";if(hundred>0)s+=indianWords(hundred)+" Hundred ";if(n>0)s+=under100((int)n);return s.trim();
    }
    private String under100(int n){String[] a={"","One","Two","Three","Four","Five","Six","Seven","Eight","Nine","Ten","Eleven","Twelve","Thirteen","Fourteen","Fifteen","Sixteen","Seventeen","Eighteen","Nineteen"};String[] t={"","","Twenty","Thirty","Forty","Fifty","Sixty","Seventy","Eighty","Ninety"};if(n<20)return a[n];return t[n/10]+(n%10>0?" "+a[n%10]:"");}

    private void showQr(boolean wifi){
        shell(wifi?"WI-FI QR GENERATOR":"QR CODE GENERATOR");
        EditText a=input(wifi?"Wi-Fi Name (SSID)":"Text / URL");root.addView(a);EditText b=null;if(wifi){b=input("Wi-Fi Password");b.setInputType(android.text.InputType.TYPE_CLASS_TEXT);root.addView(b);}
        Button go=btn("GENERATE QR");root.addView(go,new LinearLayout.LayoutParams(-1,dp(60)));ImageView img=new ImageView(this);img.setAdjustViewBounds(true);root.addView(img,new LinearLayout.LayoutParams(-1,dp(340)));EditText pass=b;
        go.setOnClickListener(v->{String data=wifi?"WIFI:T:WPA;S:"+a.getText().toString()+";P:"+pass.getText().toString()+";;":a.getText().toString();Bitmap bm=qrBitmap(data,800);if(bm!=null)img.setImageBitmap(bm);});
    }
    private Bitmap qrBitmap(String data,int size){try{BitMatrix m=new MultiFormatWriter().encode(data,BarcodeFormat.QR_CODE,size,size);Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565);for(int y=0;y<size;y++)for(int x=0;x<size;x++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);return b;}catch(Exception e){Toast.makeText(this,"QR error",Toast.LENGTH_SHORT).show();return null;}}

    private void showGst(){
        shell("GST / DISCOUNT CALCULATOR");
        EditText amt=input("Amount");EditText disc=input("Discount %");EditText gst=input("GST %");root.addView(amt);root.addView(disc);root.addView(gst);Button go=btn("CALCULATE");root.addView(go,new LinearLayout.LayoutParams(-1,dp(60)));TextView out=tv("",20,WHITE);out.setGravity(Gravity.CENTER);out.setBackground(bg(PANEL2,12));root.addView(out,new LinearLayout.LayoutParams(-1,dp(130)));
        go.setOnClickListener(v->{double a=val(amt),d=a*val(disc)/100.0,after=a-d,g=after*val(gst)/100.0;out.setText("Discount: ₹"+df.format(d)+"\nGST: ₹"+df.format(g)+"\nFinal: ₹"+df.format(after+g));});
    }

    private void showRemote(){
        shell("REMOTE");
        ConsumerIrManager ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
        String msg=(ir!=null&&ir.hasIrEmitter())?"IR Blaster detected. Device-specific remote profiles अगले update में जोड़ेंगे।":"इस phone में IR Blaster उपलब्ध नहीं है।";
        TextView t=tv(msg,22,WHITE);t.setGravity(Gravity.CENTER);t.setBackground(bg(PANEL2,12));root.addView(t,new LinearLayout.LayoutParams(-1,dp(180)));
    }

    private void showUnitConverter(){
        shell("UNIT CONVERTER");
        Spinner type=dropdown(new String[]{"Kilometer → Mile","Mile → Kilometer","Kilogram → Pound","Pound → Kilogram","Celsius → Fahrenheit","Fahrenheit → Celsius"});root.addView(type,new LinearLayout.LayoutParams(-1,dp(58)));
        EditText in=input("Value");root.addView(in);Button go=btn("CONVERT");root.addView(go,new LinearLayout.LayoutParams(-1,dp(60)));TextView out=tv("",24,WHITE);out.setGravity(Gravity.CENTER);out.setBackground(bg(PANEL2,12));root.addView(out,new LinearLayout.LayoutParams(-1,dp(110)));
        go.setOnClickListener(v->{double x=val(in),y=0;switch(type.getSelectedItemPosition()){case 0:y=x*0.621371;break;case 1:y=x/0.621371;break;case 2:y=x*2.20462;break;case 3:y=x/2.20462;break;case 4:y=x*9/5+32;break;case 5:y=(x-32)*5/9;break;}out.setText(trim(y));});
    }

    private void openSpeedTest(){try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://fast.com")));}catch(Exception e){Toast.makeText(this,"Browser नहीं मिला",Toast.LENGTH_SHORT).show();}}

    private void showQuickBill(){
        shell("QUICK BILL");
        EditText name=input("Item Name");name.setInputType(android.text.InputType.TYPE_CLASS_TEXT);EditText qty=input("Quantity");EditText rate=input("Rate ₹");EditText gst=input("GST %");
        root.addView(name);root.addView(qty);root.addView(rate);root.addView(gst);Button go=btn("MAKE BILL");root.addView(go,new LinearLayout.LayoutParams(-1,dp(60)));TextView out=tv("",20,WHITE);out.setGravity(Gravity.CENTER);out.setBackground(bg(PANEL2,12));root.addView(out,new LinearLayout.LayoutParams(-1,dp(180)));
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

    @Override public void onBackPressed(){ showHome(); }
}
