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
    // Eye-comfort theme: low-glare charcoal surfaces with muted accents.
    private final int BG = Color.rgb(18,24,31);
    private final int PANEL = Color.rgb(31,40,50);
    private final int PANEL2 = Color.rgb(40,52,63);
    private final int ACCENT = Color.rgb(83,145,174);
    private final int PURPLE = Color.rgb(125,116,164);
    private final int TEAL = Color.rgb(72,145,134);
    private final int GREEN = Color.rgb(82,145,106);
    private final int ORANGE = Color.rgb(174,128,78);
    private final int RED = Color.rgb(166,88,94);
    private final int PINK = Color.rgb(158,105,134);
    private final int INDIGO = Color.rgb(101,112,158);
    private final int SURFACE = Color.rgb(23,31,39);
    private final int SOFT = Color.rgb(166,177,187);
    private final int WHITE = Color.rgb(230,234,238);
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
    private ScrollView activeInputScroll;
    private String pendingThermalPrintText="";
    private String pendingThermalPrintMode="GENERIC";
    private boolean pendingThermalSetup=false;
    private FrameLayout globalContentRoot;
    private int safeInsetLeft=0,safeInsetTop=0,safeInsetRight=0,safeInsetBottom=0;

    private static final int REQ_EMBEDDED_SCANNER_CAMERA=9012;
    private static final int REQ_GALLERY_SCAN=9013;
    private static final int REQ_THERMAL_BLUETOOTH=9014;
    private static final int REQ_SAVE_QR_IMAGE=9015;
    private static final int REQ_PICK_RESIZER_IMAGE=9016;
    private static final int REQ_SAVE_RESIZER=9017;
    private static final int REQ_PICK_PDFS=9018;
    private static final int REQ_SAVE_JOINED_PDF=9019;
    private static final int REQ_SAVE_PDF_IMAGES_DIR=9020;
    private static final int REQ_SAVE_VIEWER_PDF=9021;
    private static final int REQ_SAVE_VIEWER_IMAGES_DIR=9022;

    private Bitmap lastGeneratedQrBitmap;
    private String pendingQrSaveFormat="PNG";

    private Uri resizerSourceUri;
    private Bitmap resizerSourceBitmap;
    private Bitmap resizerOutputBitmap;
    private String pendingResizerFormat="JPG";
    private int resizerTargetKb=0;

    private final java.util.ArrayList<Uri> pdfToolUris=new java.util.ArrayList<>();
    private byte[] lastJoinedPdfBytes;
    private String pendingPdfExportFormat="PDF";
    private float lastPdfBuildScale=1.0f;
    private String lastPdfPageMode="ORIGINAL";

    private Uri viewerPdfUri;
    private android.os.ParcelFileDescriptor viewerPdfPfd;
    private android.graphics.pdf.PdfRenderer viewerPdfRenderer;
    private android.graphics.pdf.PdfRenderer.Page viewerPdfPage;
    private int viewerPageIndex=0;
    private ContinuousPdfPageView viewerImage;
    private TextView viewerPageLabel;
    private Bitmap viewerBitmap;
    private final Matrix viewerMatrix=new Matrix();
    private float viewerZoom=1f;
    private float viewerBaseScale=1f;
    private boolean viewerContinuousMode=false;
    private FrameLayout viewerViewport;
    private ListView viewerContinuousList;
    private TextView viewerModeButton;
    private android.util.LruCache<Integer,Bitmap> viewerPageCache;
    private java.util.concurrent.ExecutorService viewerRenderExecutor;
    private final java.util.Set<Integer> viewerPagesLoading=
            java.util.Collections.synchronizedSet(new java.util.HashSet<Integer>());
    private final Object viewerRendererLock=new Object();
    private int viewerRenderGeneration=0;
    private String pendingViewerExportFormat="PDF";
    private com.journeyapps.barcodescanner.DecoratedBarcodeView embeddedScanner;
    private FrameLayout scannerViewport;
    private TextView scannerStatus;
    private TextView scannerDetails;
    private boolean scannerActive=false;
    private boolean scannerResultLocked=false;
    private boolean scannerFullScreen=false;
    private String lastScannerRaw="";
    private String lastScannerDetails="";
    private android.view.ScaleGestureDetector scannerScaleDetector;
    private float scannerZoomLevel=0f;


    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        configureSystemBars();
        installGlobalSafeArea();
        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        devMode=sp.getBoolean("devMode",false);
        vibrationEnabled=sp.getBoolean("vibration",true);
        language=sp.getString("language","ENGLISH");
        logEvent("App started");
        if(!handleIncomingPdfIntent(getIntent())){
            showCalculator();
        }
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(!handleIncomingPdfIntent(intent)){
            reopenCurrentTool();
        }
    }


    private void configureSystemBars(){
        Window w=getWindow();
        if(w==null) return;

        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        View decor=w.getDecorView();
        if(decor!=null){
            int flags=decor.getSystemUiVisibility();
            flags&=~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if(Build.VERSION.SDK_INT>=26){
                flags&=~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            flags&=~View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            flags&=~View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            flags&=~View.SYSTEM_UI_FLAG_FULLSCREEN;
            flags&=~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            decor.setSystemUiVisibility(flags);
            decor.setBackgroundColor(BG);
        }

        if(Build.VERSION.SDK_INT>=30){
            try{
                android.view.WindowInsetsController controller=w.getInsetsController();
                if(controller!=null){
                    controller.setSystemBarsAppearance(
                            0,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            }catch(Throwable ignored){}
        }

        // Before Android 15, let the framework fit the decor to system bars.
        // Android 15+ is still covered by the explicit inset guard below.
        if(Build.VERSION.SDK_INT>=30 && Build.VERSION.SDK_INT<35){
            try{w.setDecorFitsSystemWindows(true);}catch(Throwable ignored){}
        }
    }

    private void refreshSafeAreaNow(){
        configureSystemBars();

        if(globalContentRoot==null){
            installGlobalSafeArea();
        }
        if(globalContentRoot==null) return;

        globalContentRoot.setBackgroundColor(BG);

        if(Build.VERSION.SDK_INT>=23){
            try{
                WindowInsets current=globalContentRoot.getRootWindowInsets();
                if(current!=null){
                    captureSafeInsets(current);
                    applyGlobalSafeAreaPadding();
                }
            }catch(Throwable ignored){}
        }

        if(Build.VERSION.SDK_INT>=20){
            globalContentRoot.requestApplyInsets();
        }

        globalContentRoot.postOnAnimation(()->{
            configureSystemBars();
            applyGlobalSafeAreaPadding();
            if(Build.VERSION.SDK_INT>=20) globalContentRoot.requestApplyInsets();
        });
    }

    @Override public void setContentView(View view){
        super.setContentView(view);
        refreshSafeAreaNow();
    }

    @Override public void setContentView(View view,ViewGroup.LayoutParams params){
        super.setContentView(view,params);
        refreshSafeAreaNow();
    }

    private void installGlobalSafeArea(){
        View content=findViewById(android.R.id.content);
        if(!(content instanceof FrameLayout)) return;
        globalContentRoot=(FrameLayout)content;
        globalContentRoot.setClipToPadding(false);

        globalContentRoot.setBackgroundColor(BG);
        globalContentRoot.setOnApplyWindowInsetsListener((v,insets)->{
            captureSafeInsets(insets);
            applyGlobalSafeAreaPadding();
            return insets;
        });

        globalContentRoot.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->
                applyGlobalSafeAreaPadding());

        if(Build.VERSION.SDK_INT>=20){
            globalContentRoot.requestApplyInsets();
        }
    }

    private void captureSafeInsets(WindowInsets insets){
        if(insets==null) return;

        int left=insets.getSystemWindowInsetLeft();
        int top=insets.getSystemWindowInsetTop();
        int right=insets.getSystemWindowInsetRight();
        int bottom=insets.getSystemWindowInsetBottom();

        if(Build.VERSION.SDK_INT>=28){
            try{
                android.view.DisplayCutout cutout=insets.getDisplayCutout();
                if(cutout!=null){
                    left=Math.max(left,cutout.getSafeInsetLeft());
                    top=Math.max(top,cutout.getSafeInsetTop());
                    right=Math.max(right,cutout.getSafeInsetRight());
                    bottom=Math.max(bottom,cutout.getSafeInsetBottom());
                }
            }catch(Throwable ignored){}
        }

        safeInsetLeft=Math.max(0,left);
        safeInsetTop=Math.max(0,top);
        safeInsetRight=Math.max(0,right);
        safeInsetBottom=Math.max(0,bottom);
    }

    private void applyGlobalSafeAreaPadding(){
        if(globalContentRoot==null) return;
        View decor=getWindow().getDecorView();
        if(decor==null || decor.getWidth()<=0 || decor.getHeight()<=0
                || globalContentRoot.getWidth()<=0 || globalContentRoot.getHeight()<=0) return;

        int[] loc=new int[2];
        globalContentRoot.getLocationInWindow(loc);

        int contentLeft=loc[0];
        int contentTop=loc[1];
        int contentRight=contentLeft+globalContentRoot.getWidth();
        int contentBottom=contentTop+globalContentRoot.getHeight();

        int safeLeftEdge=safeInsetLeft;
        int safeTopEdge=safeInsetTop;
        int safeRightEdge=decor.getWidth()-safeInsetRight;
        int safeBottomEdge=decor.getHeight()-safeInsetBottom;

        // Only compensate for the part that actually overlaps a system area.
        // If an OEM already inset the Activity, these values naturally become 0.
        int padLeft=Math.max(0,safeLeftEdge-contentLeft);
        int padTop=Math.max(0,safeTopEdge-contentTop);
        int padRight=Math.max(0,contentRight-safeRightEdge);
        int padBottom=Math.max(0,contentBottom-safeBottomEdge);

        if(globalContentRoot.getPaddingLeft()!=padLeft
                || globalContentRoot.getPaddingTop()!=padTop
                || globalContentRoot.getPaddingRight()!=padRight
                || globalContentRoot.getPaddingBottom()!=padBottom){
            globalContentRoot.setPadding(padLeft,padTop,padRight,padBottom);
        }
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
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp((int)radius));
        return g;
    }

    private GradientDrawable grad(int c1,int c2,float radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{c1,c2});
        g.setCornerRadius(dp((int)radius));
        return g;
    }

    private int mixColor(int a,int b,float amount){
        float p=Math.max(0f,Math.min(1f,amount));
        int r=(int)(Color.red(a)*(1f-p)+Color.red(b)*p);
        int g=(int)(Color.green(a)*(1f-p)+Color.green(b)*p);
        int bl=(int)(Color.blue(a)*(1f-p)+Color.blue(b)*p);
        return Color.rgb(r,g,bl);
    }

    private int toolAccent(){
        if("CASH_COUNTER".equals(currentTool)) return GREEN;
        if("NOTEPAD".equals(currentTool)) return INDIGO;
        if("AGE".equals(currentTool)) return PURPLE;
        if("SAVINGS".equals(currentTool)) return TEAL;
        if("EMI".equals(currentTool)) return ORANGE;
        if("WORDS".equals(currentTool)) return PINK;
        if("GST".equals(currentTool)) return GREEN;
        if("REMOTE".equals(currentTool)) return RED;
        if("UNIT".equals(currentTool)) return TEAL;
        if("SPEED".equals(currentTool)) return ACCENT;
        if("BILL".equals(currentTool)) return ORANGE;
        if("SCAN".equals(currentTool)) return PURPLE;
        if("WIFI_QR".equals(currentTool)) return TEAL;
        if("QR".equals(currentTool)) return PINK;
        if("PHOTO_RESIZER".equals(currentTool)) return TEAL;
        if("PDF_TOOLS".equals(currentTool) || "PDF_VIEWER".equals(currentTool)) return INDIGO;
        return ACCENT;
    }

    private GradientDrawable fieldBg(){
        int accent=toolAccent();
        GradientDrawable g=grad(
                mixColor(PANEL,accent,0.05f),
                mixColor(PANEL2,accent,0.09f),
                15);
        g.setStroke(dp(1),mixColor(PANEL2,accent,0.34f));
        return g;
    }

    private GradientDrawable focusedFieldBg(){
        int accent=toolAccent();
        GradientDrawable g=grad(
                mixColor(PANEL,accent,0.09f),
                mixColor(PANEL2,accent,0.15f),
                15);
        g.setStroke(dp(2),mixColor(PANEL2,accent,0.52f));
        return g;
    }

    private GradientDrawable contentCardBg(){
        int accent=toolAccent();
        GradientDrawable g=grad(
                mixColor(SURFACE,accent,0.035f),
                mixColor(BG,accent,0.055f),
                20);
        g.setStroke(dp(1),Color.argb(70,Color.red(accent),Color.green(accent),Color.blue(accent)));
        return g;
    }

    private GradientDrawable actionBarBg(){
        int accent=toolAccent();
        GradientDrawable g=grad(
                mixColor(PANEL,accent,0.07f),
                mixColor(SURFACE,accent,0.10f),
                16);
        g.setStroke(dp(1),mixColor(PANEL2,accent,0.30f));
        return g;
    }

    private android.graphics.drawable.Drawable touchBg(int color,float radius){
        GradientDrawable base=grad(mixColor(color,PANEL,0.16f),mixColor(color,BG,0.27f),radius);
        base.setStroke(dp(1),mixColor(color,PANEL2,0.42f));
        if(Build.VERSION.SDK_INT>=21){
            return new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(42,255,255,255)),
                    base,null);
        }
        return base;
    }

    private android.graphics.drawable.Drawable screenBg(){
        int accent=toolAccent();
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        BG,
                        mixColor(SURFACE,accent,0.035f),
                        mixColor(BG,accent,0.025f),
                        BG
                });
    }

    private int actionColor(String s){
        String x=s==null?"":s.toUpperCase(java.util.Locale.US);
        if(x.contains("SHARE") || x.contains("शेयर")) return GREEN;
        if(x.contains("HISTORY") || x.contains("हिस्ट्री")) return PURPLE;
        if(x.contains("CLEAR") || x.contains("DELETE") || x.contains("REMOVE") || "C".equals(x)) return RED;
        if(x.contains("PRINT") || x.contains("INPUT") || x.contains("SOURCE")) return ORANGE;
        if(x.contains("DATE") || x.contains("तारीख")) return PURPLE;
        if(x.contains("SAVE") || x.contains("CONNECT") || x.contains("PAIR")) return TEAL;
        if(x.contains("START") || x.contains("SCAN") || x.contains("FIND")
                || x.contains("CALCULATE") || x.contains("CONVERT")
                || x.contains("GENERATE") || x.contains("SHOW")
                || x.contains("MAKE") || x.contains("DETAIL")) return toolAccent();
        if("=".equals(x) || "OK".equals(x)) return TEAL;
        if("+".equals(x) || "-".equals(x) || "×".equals(x) || "÷".equals(x) || "%".equals(x)) return ORANGE;
        return mixColor(PANEL2,toolAccent(),0.22f);
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
        b.setElevation(dp(1));
        b.setBackground(touchBg(actionColor(s),14));
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN) haptic();
            return false;
        });
        return b;
    }

    private EditText input(String hint){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(mixColor(SOFT,Color.WHITE,0.06f));
        e.setTextColor(WHITE);
        e.setTextSize(18);
        e.setSingleLine(true);
        e.setPadding(dp(16),dp(8),dp(16),dp(8));
        e.setBackground(fieldBg());
        e.setElevation(dp(1));
        attachInputBehavior(e);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));
        p.setMargins(0,dp(5),0,dp(5));
        e.setLayoutParams(p);
        return e;
    }

    private boolean isInside(View child,ViewGroup parent){
        View v=child;
        while(v!=null){
            if(v==parent) return true;
            android.view.ViewParent p=v.getParent();
            if(!(p instanceof View)) break;
            v=(View)p;
        }
        return false;
    }

    private void ensureInputVisible(EditText e){
        final ScrollView sc=activeInputScroll;
        if(sc==null || e==null || !isInside(e,sc)) return;
        sc.postDelayed(()->{
            if(activeInputScroll!=sc || !isInside(e,sc)) return;
            int[] fieldPos=new int[2];
            int[] scrollPos=new int[2];
            e.getLocationOnScreen(fieldPos);
            sc.getLocationOnScreen(scrollPos);

            int safeTop=scrollPos[1]+dp(10);
            int safeBottom=scrollPos[1]+sc.getHeight()-sc.getPaddingBottom()-dp(18);
            int fieldTop=fieldPos[1];
            int fieldBottom=fieldTop+e.getHeight();

            if(fieldBottom>safeBottom){
                sc.smoothScrollBy(0,fieldBottom-safeBottom+dp(12));
            }else if(fieldTop<safeTop){
                sc.smoothScrollBy(0,fieldTop-safeTop-dp(8));
            }
        },140);
    }

    private void attachInputBehavior(EditText e){
        e.setOnFocusChangeListener((v,focused)->{
            e.setBackground(focused?focusedFieldBg():fieldBg());
            e.setElevation(dp(focused?2:1));
            if(focused) ensureInputVisible(e);
        });
    }

    private void registerInputScroll(ScrollView sc){
        activeInputScroll=sc;
        sc.setFillViewport(true);
        sc.setClipToPadding(false);

        final int left=sc.getPaddingLeft();
        final int top=sc.getPaddingTop();
        final int right=sc.getPaddingRight();
        final int bottom=sc.getPaddingBottom();
        final int[] closedObstruction={-1};
        final View decor=getWindow().getDecorView();

        decor.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            if(activeInputScroll!=sc || sc.getWindowToken()==null) return;

            int keyboard=0;
            if(Build.VERSION.SDK_INT>=30){
                android.view.WindowInsets wi=decor.getRootWindowInsets();
                if(wi!=null){
                    keyboard=wi.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                }
            }else{
                Rect visible=new Rect();
                decor.getWindowVisibleDisplayFrame(visible);
                int obstruction=Math.max(0,decor.getHeight()-visible.bottom);
                if(closedObstruction[0]<0) closedObstruction[0]=obstruction;
                if(obstruction<closedObstruction[0]+dp(40)){
                    closedObstruction[0]=Math.min(closedObstruction[0],obstruction);
                }
                keyboard=Math.max(0,obstruction-Math.max(0,closedObstruction[0]));
            }

            if(keyboard<dp(120)) keyboard=0;
            int targetBottom=bottom+(keyboard>0?keyboard+dp(18):0);
            if(sc.getPaddingBottom()!=targetBottom){
                sc.setPadding(left,top,right,targetBottom);
            }

            if(keyboard>0){
                View focused=getCurrentFocus();
                if(focused instanceof EditText && isInside(focused,sc)){
                    ensureInputVisible((EditText)focused);
                }
            }
        });
    }

    private LinearLayout.LayoutParams controlParams(int heightDp){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(heightDp));
        p.setMargins(0,dp(5),0,dp(5));
        return p;
    }

    private LinearLayout.LayoutParams resultParams(int heightDp){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(heightDp));
        p.setMargins(0,dp(10),0,dp(6));
        return p;
    }

    private void styleResult(TextView t){
        t.setGravity(Gravity.CENTER);
        t.setTypeface(null,1);
        t.setPadding(dp(18),dp(16),dp(18),dp(16));
        t.setElevation(dp(2));
        GradientDrawable g=grad(
                mixColor(PANEL2,toolAccent(),0.10f),
                mixColor(SURFACE,toolAccent(),0.13f),16);
        g.setStroke(dp(1),mixColor(PANEL2,toolAccent(),0.36f));
        t.setBackground(g);
    }

    private void styleProfessionalSpinner(Spinner s){
        int accent=toolAccent();
        GradientDrawable popup=grad(
                mixColor(BG,accent,0.025f),
                mixColor(PANEL,accent,0.055f),
                16);
        popup.setStroke(dp(1),mixColor(PANEL2,accent,0.30f));
        s.setPopupBackgroundDrawable(popup);
        s.setDropDownVerticalOffset(dp(6));
        s.setDropDownWidth(Math.max(dp(260),getResources().getDisplayMetrics().widthPixels-dp(40)));
        s.setElevation(dp(2));
        s.setPadding(dp(2),0,dp(2),0);
    }

    private Spinner dropdown(String[] items){
        Spinner s=new Spinner(this);
        s.setPadding(dp(2),0,dp(2),0);

        ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,items){
            @Override public View getView(int pos, View convert, android.view.ViewGroup parent){
                TextView t=(TextView)super.getView(pos,convert,parent);
                t.setTextColor(WHITE);
                t.setTextSize(17);
                t.setTypeface(null,1);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setPadding(dp(16),0,dp(16),0);

                GradientDrawable g=grad(
                        mixColor(PANEL2,toolAccent(),0.10f),
                        mixColor(PANEL,toolAccent(),0.06f),15);
                g.setStroke(dp(1),mixColor(PANEL2,toolAccent(),0.36f));
                t.setBackground(g);
                return t;
            }

            @Override public View getDropDownView(int pos, View convert, android.view.ViewGroup parent){
                TextView t=(TextView)super.getDropDownView(pos,convert,parent);
                t.setTextColor(WHITE);
                t.setTextSize(17);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(dp(18),dp(13),dp(18),dp(13));

                boolean selected=pos==s.getSelectedItemPosition();
                GradientDrawable g=grad(
                        selected?mixColor(PANEL2,toolAccent(),0.17f):mixColor(PANEL,toolAccent(),0.045f),
                        selected?mixColor(SURFACE,toolAccent(),0.14f):mixColor(PANEL2,toolAccent(),0.06f),
                        12);
                g.setStroke(dp(1),
                        selected?mixColor(PANEL2,toolAccent(),0.52f)
                                :Color.argb(55,Color.red(toolAccent()),Color.green(toolAccent()),Color.blue(toolAccent())));
                t.setBackground(g);
                t.setTypeface(null,selected?1:0);
                return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setElevation(dp(3));
        s.setPopupBackgroundDrawable(contentCardBg());
        s.setBackground(touchBg(mixColor(PANEL2,toolAccent(),0.10f),15));
        return s;
    }

    private String L(String en,String hi){
        return "HINDI".equals(language)?hi:en;
    }

    private void openTool(String key){
        currentTool=key;
        if("CALCULATOR".equals(key)) showCalculator();
        else if("NOTEPAD".equals(key)) showNotepad();
        else if("CASH_COUNTER".equals(key)) showCashCounter();
        else if("AGE".equals(key)) showAge();
        else if("SAVINGS".equals(key)) showSavings();
        else if("EMI".equals(key) || "GST".equals(key)) showEmiInterest();
        else if("WORDS".equals(key)) showNumberWords();
        else if("QR".equals(key) || "WIFI_QR".equals(key)) showQr();
        else if("PHOTO_RESIZER".equals(key)) showPhotoSignatureResizer();
        else if("PDF_TOOLS".equals(key)) showPdfJoinResize();
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
        if("NOTEPAD".equals(key)) return L("NOTEPAD","नोटपैड");
        if("CASH_COUNTER".equals(key)) return L("CASH COUNTER","कैश काउंटर");
        if("AGE".equals(key)) return L("AGE CALCULATOR","आयु कैलकुलेटर");
        if("SAVINGS".equals(key)) return L("RD / FD / SIP CALCULATOR","आरडी / एफडी / एसआईपी कैलकुलेटर");
        if("EMI".equals(key) || "GST".equals(key)) return L("EMI / INTEREST + GST / DISCOUNT","ईएमआई / ब्याज + GST / डिस्काउंट");
        if("WORDS".equals(key)) return L("NUMBER TO WORDS","संख्या शब्दों में");
        if("QR".equals(key) || "WIFI_QR".equals(key)) return L("QR GENERATOR","QR जनरेटर");
        if("PHOTO_RESIZER".equals(key)) return L("PHOTO / SIGNATURE RESIZER","फोटो / सिग्नेचर रिसाइज़र");
        if("PDF_TOOLS".equals(key)) return L("PDF JOIN / RESIZE","PDF जोड़ें / रिसाइज़");
        if("PDF_VIEWER".equals(key)) return L("PDF VIEWER","PDF व्यूअर");
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
        return new String[]{"CALCULATOR","NOTEPAD","CASH_COUNTER","AGE","SAVINGS","EMI","WORDS","QR","PHOTO_RESIZER","PDF_TOOLS","REMOTE","UNIT","SPEED","BILL","SCAN"};
    }

    private String[] getToolOrder(){
        String saved=getSharedPreferences("sts",0).getString("toolOrder","");
        String[] def=defaultToolOrder();
        if(saved==null || saved.trim().isEmpty()) return def;

        java.util.HashSet<String> valid=new java.util.HashSet<>();
        for(String x:def) valid.add(x);

        java.util.ArrayList<String> migrated=new java.util.ArrayList<>();
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        boolean changed=false;

        String[] arr=saved.split(",");
        for(String raw:arr){
            String x=raw==null?"":raw.trim();
            if(x.isEmpty()) continue;

            // Old separate entries are now merged into their combined modules.
            if("WIFI_QR".equals(x)){
                x="QR";
                changed=true;
            }else if("GST".equals(x)){
                x="EMI";
                changed=true;
            }

            if(valid.contains(x) && seen.add(x)){
                migrated.add(x);
            }else{
                changed=true;
            }
        }

        // Keep the user's order and append only tools that were missing in an older version.
        for(String x:def){
            if(seen.add(x)){
                migrated.add(x);
                changed=true;
            }
        }

        String[] result=migrated.toArray(new String[0]);
        if(changed || result.length!=arr.length) saveToolOrder(result);
        return result;
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

        View accentLine=new View(this);
        accentLine.setBackgroundColor(toolAccent());
        outer.addView(accentLine,new LinearLayout.LayoutParams(-1,dp(2)));

        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackground(screenBg());
        sc.setClipToPadding(false);
        sc.setPadding(dp(8),dp(8),dp(8),dp(8));

        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(dp(12),dp(12),dp(12),dp(12));
        root.setBackground(contentCardBg());
        root.setElevation(dp(1));

        sc.addView(root,new ScrollView.LayoutParams(-1,-1));
        outer.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        registerInputScroll(sc);

        setContentView(outer);
        return sc;
    }

    private void addFixedDropdown(LinearLayout outer, String currentName){
        int accent=toolAccent();

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8),dp(7),dp(8),dp(7));
        header.setBackground(grad(
                mixColor(PANEL,accent,0.08f),
                mixColor(PANEL2,accent,0.11f),0));
        header.setElevation(dp(5));

        TextView menu=tv("⋮",32,WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(touchBg(mixColor(PANEL,accent,0.10f),14));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(50),dp(50));
        mp.setMargins(0,0,dp(8),0);
        header.addView(menu,mp);

        LinearLayout selector=new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setPadding(dp(14),0,dp(8),0);
        GradientDrawable selectorBg=grad(
                mixColor(PANEL2,accent,0.11f),
                mixColor(SURFACE,accent,0.09f),16);
        selectorBg.setStroke(dp(2),mixColor(PANEL2,accent,0.48f));
        selector.setBackground(selectorBg);
        selector.setElevation(dp(3));

        TextView label=tv(currentName,20,WHITE);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(null,1);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setShadowLayer(3f,0f,1f,Color.argb(110,0,0,0));
        selector.addView(label,new LinearLayout.LayoutParams(0,dp(50),1));

        TextView handle=tv("━",26,mixColor(WHITE,accent,0.20f));
        handle.setGravity(Gravity.CENTER);
        handle.setTypeface(null,1);
        selector.addView(handle,new LinearLayout.LayoutParams(dp(44),dp(50)));

        header.addView(selector,new LinearLayout.LayoutParams(0,dp(50),1));
        outer.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));

        menu.setOnClickListener(v->{haptic();showTopMenu(menu);});
        selector.setOnClickListener(v->{haptic();showToolPicker(currentName);});
        label.setOnClickListener(v->{haptic();showToolPicker(currentName);});
        handle.setOnClickListener(v->{haptic();showToolPicker(currentName);});
    }

    private void showToolPicker(String currentName){
        toolPickerOpen=true;
        int accent=toolAccent();

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(screenBg());

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8),dp(7),dp(8),dp(7));
        header.setBackground(grad(
                mixColor(PANEL,accent,0.08f),
                mixColor(PANEL2,accent,0.11f),0));
        header.setElevation(dp(5));

        TextView menu=tv("⋮",32,WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(touchBg(mixColor(PANEL,accent,0.10f),14));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(50),dp(50));
        mp.setMargins(0,0,dp(8),0);
        header.addView(menu,mp);

        LinearLayout selector=new LinearLayout(this);
        selector.setOrientation(LinearLayout.HORIZONTAL);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setPadding(dp(14),0,dp(8),0);

        GradientDrawable selectorBg=grad(
                mixColor(PANEL2,accent,0.13f),
                mixColor(SURFACE,accent,0.10f),16);
        selectorBg.setStroke(dp(2),mixColor(PANEL2,accent,0.52f));
        selector.setBackground(selectorBg);
        selector.setElevation(dp(4));

        TextView label=tv(currentName,20,WHITE);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(null,1);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        selector.addView(label,new LinearLayout.LayoutParams(0,dp(50),1));

        TextView handle=tv("━",26,mixColor(WHITE,accent,0.20f));
        handle.setGravity(Gravity.CENTER);
        handle.setTypeface(null,1);
        selector.addView(handle,new LinearLayout.LayoutParams(dp(44),dp(50)));

        header.addView(selector,new LinearLayout.LayoutParams(0,dp(50),1));
        outer.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));

        TextView caption=tv(L("SELECT TOOL","टूल चुनें"),13,SOFT);
        caption.setTypeface(null,1);
        caption.setLetterSpacing(0.08f);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setPadding(dp(20),dp(9),dp(20),dp(7));
        outer.addView(caption,new LinearLayout.LayoutParams(-1,dp(38)));

        ScrollView listScroll=new ScrollView(this);
        listScroll.setFillViewport(true);
        listScroll.setBackground(screenBg());
        listScroll.setVerticalScrollBarEnabled(true);
        listScroll.setScrollbarFadingEnabled(false);
        listScroll.setPadding(dp(7),0,dp(7),dp(8));

        LinearLayout tools=new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setPadding(dp(2),dp(2),dp(2),dp(8));
        tools.setBackground(contentCardBg());

        for(String key:getToolOrder()){
            final String k=key;
            addMenu(tools,toolName(k),()->{toolPickerOpen=false;openTool(k);},k.equals(currentTool));
        }

        listScroll.addView(tools,new ScrollView.LayoutParams(-1,-2));
        outer.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1));

        menu.setOnClickListener(v->{haptic();showTopMenu(menu);});
        selector.setOnClickListener(v->{haptic();toolPickerOpen=false;reopenCurrentTool();});
        label.setOnClickListener(v->{haptic();toolPickerOpen=false;reopenCurrentTool();});
        handle.setOnClickListener(v->{haptic();toolPickerOpen=false;reopenCurrentTool();});

        setContentView(outer);
    }

    private void addMenu(LinearLayout list,String s,Runnable r){
        addMenu(list,s,r,false);
    }

    private void addMenu(LinearLayout list,String s,Runnable r,boolean selected){
        int accent=toolAccent();

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10),0,dp(10),0);
        row.setElevation(dp(selected?5:2));

        GradientDrawable card=grad(
                selected?mixColor(PANEL2,accent,0.17f):mixColor(PANEL,accent,0.035f),
                selected?mixColor(SURFACE,accent,0.14f):mixColor(PANEL2,accent,0.05f),
                17);
        card.setStroke(dp(1),
                selected?mixColor(PANEL2,accent,0.52f)
                        :Color.argb(48,Color.red(accent),Color.green(accent),Color.blue(accent)));
        row.setBackground(card);

        TextView dot=tv(selected?"✓":"•",selected?18:24,selected?WHITE:accent);
        dot.setGravity(Gravity.CENTER);
        dot.setTypeface(null,1);
        GradientDrawable dotBg=bg(
                selected?mixColor(accent,PANEL2,0.25f):mixColor(PANEL2,accent,0.09f),
                13);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(40),dp(40));
        dpv.setMargins(0,0,dp(12),0);
        row.addView(dot,dpv);

        TextView name=tv(s,18,WHITE);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setTypeface(null,selected?1:0);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name,new LinearLayout.LayoutParams(0,-1,1));

        if(selected){
            TextView badge=tv(L("ACTIVE","चालू"),11,WHITE);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(null,1);
            badge.setBackground(bg(mixColor(accent,PANEL2,0.18f),10));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(64),dp(30));
            bp.setMargins(dp(8),0,0,0);
            row.addView(badge,bp);
            row.setContentDescription(s+" selected");
        }

        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(64));
        p.setMargins(dp(7),dp(4),dp(7),dp(4));
        list.addView(row,p);
        row.setOnClickListener(v->{logEvent("Open: "+s); haptic(); r.run();});
    }

    private void showTopMenu(View anchor){
        PopupMenu p=new PopupMenu(this,anchor);
        p.getMenu().add(L("LANGUAGE","भाषा"));
        p.getMenu().add(L("DROPDOWN LIST ORDER","ड्रॉपडाउन लिस्ट क्रम"));
        p.getMenu().add(L("THERMAL PRINTER SETUP","थर्मल प्रिंटर सेटअप"));
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

            if(s.equals(L("THERMAL PRINTER SETUP","थर्मल प्रिंटर सेटअप"))){
                pendingThermalSetup=true;
                ensureThermalBluetoothPermission();
                return true;
            }

            if(s.equals(L("ABOUT","ऐप के बारे में"))){
                new AlertDialog.Builder(this)
                        .setTitle("STS DigiKit")
                        .setMessage(L(
                                "Version 1.0.78\nOffline utility toolkit\nChange the dropdown item order from the three-dot menu.",
                                "संस्करण 1.0.78\nऑफलाइन यूटिलिटी टूलकिट\nThree-dot मेनू से dropdown items का क्रम ऊपर-नीचे बदल सकते हैं।"))
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

        TextView about=tv("Version 1.0.78\nOffline utility toolkit\nCalculator • QR • Scanner • Finance tools",17,SOFT);
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
        final int W=34;
        final String line="----------------------------------";
        StringBuilder b=new StringBuilder();

        b.append(qbCenter("STS DigiKit - Cash Counter",W)).append("\n");
        if(partyName!=null && !partyName.trim().isEmpty()){
            b.append(qbPadRight("Party: "+partyName.trim(),W)).append("\n");
        }
        b.append(line).append("\n");
        b.append(qbPadRight("TOTAL",19))
                .append(qbPadLeft("₹"+formatCash(total),15))
                .append("\n");
        b.append(line).append("\n\n");

        // Fixed grid: denomination | x | quantity | = | amount.
        // Every row has the same 34-character width.
        for(int i=0;i<den.length;i++){
            String q=qty[i].getText().toString().trim();
            if(q.isEmpty()) continue;
            try{
                BigInteger n=new BigInteger(q);
                if(n.signum()==0) continue;
                BigInteger amount=n.multiply(BigInteger.valueOf(den[i]));

                String denomination="₹"+den[i];
                String quantity=n.toString();
                String amountText="₹"+formatCash(amount);

                b.append(qbPadLeft(denomination,7))
                        .append("  x  ")
                        .append(qbPadLeft(quantity,5))
                        .append("  =  ")
                        .append(qbPadLeft(amountText,12))
                        .append("\n");
            }catch(Exception ignored){}
        }

        b.append("\n")
                .append(qbCenter(
                        new java.text.SimpleDateFormat(
                                "dd/MM/yyyy hh:mm a",
                                java.util.Locale.getDefault()).format(new java.util.Date()),
                        W));
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

    private void showCashHistoryEntry(String summary){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10),dp(8),dp(10),dp(8));
        box.setBackgroundColor(BG);

        ScrollView sc=new ScrollView(this);
        TextView content=tv(summary,16,WHITE);
        content.setGravity(Gravity.LEFT|Gravity.TOP);
        content.setTextIsSelectable(true);
        content.setPadding(dp(12),dp(12),dp(12),dp(12));
        content.setBackground(bg(PANEL,8));
        sc.addView(content,new ScrollView.LayoutParams(-1,-2));
        box.addView(sc,new LinearLayout.LayoutParams(-1,dp(430)));

        new AlertDialog.Builder(this)
                .setTitle(L("CASH HISTORY","कैश हिस्ट्री"))
                .setView(box)
                .setPositiveButton(L("CLOSE","बंद करें"),null)
                .setNegativeButton(L("PRINT COPY","प्रिंट कॉपी"),(d,w)->printCashSummary58mm(summary))
                .setNeutralButton(L("SHARE","शेयर"),(d,w)->shareCashSummary(summary))
                .show();
    }

    private void showCashHistory(){
        android.content.SharedPreferences prefs=getSharedPreferences("sts",0);
        String raw=prefs.getString("cashHistory","");

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(BG);
        box.setPadding(dp(8),dp(8),dp(8),dp(8));

        ScrollView sc=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(2),dp(2),dp(2),dp(2));
        sc.addView(list,new ScrollView.LayoutParams(-1,-2));
        box.addView(sc,new LinearLayout.LayoutParams(-1,dp(500)));

        if(raw==null || raw.isEmpty()){
            TextView empty=tv(L("No cash history yet.","अभी कोई कैश हिस्ट्री नहीं है।"),16,SOFT);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
        }else{
            String[] rows=raw.split("\\u001e",-1);
            for(String row:rows){
                String[] p=row.split("\\|",2);
                if(p.length!=2) continue;
                try{
                    long when=Long.parseLong(p[0]);
                    final String summary=new String(
                            android.util.Base64.decode(p[1],android.util.Base64.NO_WRAP),
                            java.nio.charset.StandardCharsets.UTF_8);
                    String stamp=new java.text.SimpleDateFormat(
                            "dd/MM/yyyy hh:mm a",
                            java.util.Locale.getDefault()).format(new java.util.Date(when));

                    LinearLayout card=new LinearLayout(this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(10),dp(8),dp(10),dp(8));
                    card.setBackground(contentCardBg());

                    TextView date=tv(stamp,13,SOFT);
                    card.addView(date,new LinearLayout.LayoutParams(-1,dp(32)));

                    String preview=summary.replace("\n"," ").trim();
                    if(preview.length()>145) preview=preview.substring(0,145)+"…";
                    TextView pv=tv(preview,15,WHITE);
                    pv.setGravity(Gravity.LEFT|Gravity.TOP);
                    card.addView(pv,new LinearLayout.LayoutParams(-1,dp(70)));

                    LinearLayout buttons=new LinearLayout(this);
                    buttons.setOrientation(LinearLayout.HORIZONTAL);
                    Button view=btn(L("VIEW","देखें"));
                    Button print=btn(L("PRINT COPY","प्रिंट कॉपी"));
                    Button share=btn(L("SHARE","शेयर"));
                    buttons.addView(view,new LinearLayout.LayoutParams(0,dp(50),1));
                    buttons.addView(print,new LinearLayout.LayoutParams(0,dp(50),1));
                    buttons.addView(share,new LinearLayout.LayoutParams(0,dp(50),1));
                    card.addView(buttons,new LinearLayout.LayoutParams(-1,dp(52)));

                    view.setOnClickListener(v->showCashHistoryEntry(summary));
                    print.setOnClickListener(v->printCashSummary58mm(summary));
                    share.setOnClickListener(v->shareCashSummary(summary));

                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
                    cp.setMargins(0,0,0,dp(10));
                    list.addView(card,cp);
                }catch(Exception ignored){}
            }
        }

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(L("CASH HISTORY","कैश हिस्ट्री"))
                .setView(box)
                .setPositiveButton(L("CLOSE","बंद करें"),null)
                .setNegativeButton(L("CLEAR HISTORY","हिस्ट्री साफ करें"),null)
                .create();

        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
            prefs.edit().remove("cashHistory").apply();
            list.removeAllViews();
            TextView empty=tv(L("No cash history yet.","अभी कोई कैश हिस्ट्री नहीं है।"),16,SOFT);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
        }));
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

        ScrollView cashScroll=new ScrollView(this);
        cashScroll.setFillViewport(true);
        cashScroll.setClipToPadding(false);
        cashScroll.setBackground(screenBg());
        cashScroll.setPadding(0,0,0,0);

        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackground(screenBg());
        body.setPadding(dp(4),dp(4),dp(4),dp(4));
        cashScroll.addView(body,new ScrollView.LayoutParams(-1,-1));
        outer.addView(cashScroll,new LinearLayout.LayoutParams(-1,0,1));
        registerInputScroll(cashScroll);

        EditText partyName=new EditText(this);
        partyName.setHint(L("Party / Customer / Company Name","Party / Customer / Company Name"));
        partyName.setHintTextColor(SOFT);
        partyName.setTextColor(WHITE);
        partyName.setTextSize(18);
        partyName.setSingleLine(true);
        partyName.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        partyName.setPadding(dp(14),dp(8),dp(14),dp(8));
        partyName.setBackground(fieldBg());
        partyName.setElevation(dp(1));
        attachInputBehavior(partyName);
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
        body.addView(rowsBox,new LinearLayout.LayoutParams(-1,-2));

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
            line.setBackground(i%2==0?bg(SURFACE,8):bg(mixColor(SURFACE,PANEL,0.35f),8));

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
            q.setElevation(dp(1));
            attachInputBehavior(q);
            qty[i]=q;
            LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,dp(48),0.85f);
            qp.setMargins(dp(3),dp(3),dp(3),dp(3));
            line.addView(q,qp);

            TextView a=tv("₹0",18,WHITE);
            a.setGravity(Gravity.CENTER);
            a.setTypeface(null,1);
            a.setBackground(grad(mixColor(PANEL2,TEAL,0.08f),mixColor(PANEL2,TEAL,0.16f),8));
            amount[i]=a;
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(48),1.25f);
            ap.setMargins(dp(3),dp(3),dp(3),dp(3));
            line.addView(a,ap);

            q.addTextChangedListener(new android.text.TextWatcher(){
                @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
                @Override public void onTextChanged(CharSequence s,int st,int before,int count){ recalc.run(); }
                @Override public void afterTextChanged(android.text.Editable e){}
            });

            rowsBox.addView(line,new LinearLayout.LayoutParams(-1,dp(56)));
        }

        Button cashPrint=btn(L("PRINT 58MM","58MM प्रिंट"));
        LinearLayout.LayoutParams cashPrintParams=new LinearLayout.LayoutParams(-1,dp(58));
        cashPrintParams.setMargins(0,dp(6),0,0);
        body.addView(cashPrint,cashPrintParams);

        cashPrint.setOnClickListener(v->{
            recalc.run();
            String summary=buildCashSummary(partyName.getText().toString(),den,qty,grandTotal[0]);
            if(grandTotal[0].signum()<=0){
                Toast.makeText(this,L("Enter cash quantity first","पहले cash quantity भरें"),Toast.LENGTH_SHORT).show();
                return;
            }
            saveCashHistory(summary,grandTotal[0]);
            printCashSummary58mm(summary);
        });

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

    private String panelHistoryResultOnly(String decoded){
        if(decoded==null) return "";
        String[] lines=decoded.split("\n",-1);
        if(lines.length<=2) return decoded.trim();
        StringBuilder b=new StringBuilder();
        for(int i=2;i<lines.length;i++){
            if(i>2) b.append("\n");
            b.append(lines[i]);
        }
        return b.toString().trim();
    }

    private String panelHistoryStamp(String decoded){
        if(decoded==null) return "";
        int nl=decoded.indexOf('\n');
        return nl<0?decoded.trim():decoded.substring(0,nl).trim();
    }

    private void printPanelHistoryCopy(String key,String title,String decoded){
        String result=panelHistoryResultOnly(decoded);
        if(!meaningfulResult(result)) return;

        if("bill".equals(key)){
            printQuickBill58mm(result);
        }else{
            print58mmText(
                    result,
                    "STS-DigiKit-History-Copy.pdf",
                    title+" - "+L("Print Copy","प्रिंट कॉपी"),
                    "Nothing to print",
                    "प्रिंट करने के लिए कुछ नहीं है",
                    "GENERIC");
        }
    }

    private void showPanelHistoryEntry(String key,String title,String decoded){
        String result=panelHistoryResultOnly(decoded);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10),dp(8),dp(10),dp(8));
        box.setBackgroundColor(BG);

        TextView content=tv(decoded==null?"":decoded,16,WHITE);
        content.setGravity(Gravity.LEFT|Gravity.TOP);
        content.setTextIsSelectable(true);
        content.setPadding(dp(12),dp(12),dp(12),dp(12));
        content.setBackground(bg(PANEL,10));

        ScrollView sc=new ScrollView(this);
        sc.addView(content,new ScrollView.LayoutParams(-1,-2));
        box.addView(sc,new LinearLayout.LayoutParams(-1,dp(430)));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(L("CLOSE","बंद करें"),null)
                .setNegativeButton(L("PRINT COPY","प्रिंट कॉपी"),(d,w)->
                        printPanelHistoryCopy(key,title,decoded))
                .setNeutralButton(L("SHARE","शेयर"),(d,w)->
                        sharePanelText(title,result))
                .show();
    }

    private void showPanelHistory(String key,String title){
        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        String raw=sp.getString(panelHistoryKey(key),"");

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(8),dp(8),dp(8));
        box.setBackgroundColor(BG);

        ScrollView sc=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(2),dp(2),dp(2),dp(2));
        sc.addView(list,new ScrollView.LayoutParams(-1,-2));
        box.addView(sc,new LinearLayout.LayoutParams(-1,dp(500)));

        if(raw==null || raw.isEmpty()){
            TextView empty=tv(L("No history yet.","अभी कोई हिस्ट्री नहीं है।"),16,SOFT);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
        }else{
            String[] rows=raw.split("\\u001e",-1);
            for(String row:rows){
                try{
                    final String decoded=new String(
                            android.util.Base64.decode(row,android.util.Base64.NO_WRAP),
                            java.nio.charset.StandardCharsets.UTF_8);
                    final String result=panelHistoryResultOnly(decoded);

                    LinearLayout card=new LinearLayout(this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(10),dp(8),dp(10),dp(8));
                    card.setBackground(contentCardBg());

                    TextView stamp=tv(panelHistoryStamp(decoded),13,SOFT);
                    card.addView(stamp,new LinearLayout.LayoutParams(-1,dp(30)));

                    String preview=result.replace("\n"," ").trim();
                    if(preview.length()>135) preview=preview.substring(0,135)+"…";
                    TextView pv=tv(preview,15,WHITE);
                    pv.setGravity(Gravity.LEFT|Gravity.TOP);
                    card.addView(pv,new LinearLayout.LayoutParams(-1,dp(68)));

                    LinearLayout buttons=new LinearLayout(this);
                    buttons.setOrientation(LinearLayout.HORIZONTAL);
                    Button view=btn(L("VIEW","देखें"));
                    Button print=btn(L("PRINT COPY","प्रिंट कॉपी"));
                    Button share=btn(L("SHARE","शेयर"));
                    buttons.addView(view,new LinearLayout.LayoutParams(0,dp(50),1));
                    buttons.addView(print,new LinearLayout.LayoutParams(0,dp(50),1));
                    buttons.addView(share,new LinearLayout.LayoutParams(0,dp(50),1));
                    card.addView(buttons,new LinearLayout.LayoutParams(-1,dp(52)));

                    view.setOnClickListener(v->showPanelHistoryEntry(key,title,decoded));
                    print.setOnClickListener(v->printPanelHistoryCopy(key,title,decoded));
                    share.setOnClickListener(v->sharePanelText(title,result));

                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
                    cp.setMargins(0,0,0,dp(10));
                    list.addView(card,cp);
                }catch(Exception ignored){}
            }

            if(list.getChildCount()==0){
                TextView empty=tv(L("No history yet.","अभी कोई हिस्ट्री नहीं है।"),16,SOFT);
                empty.setGravity(Gravity.CENTER);
                list.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
            }
        }

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(title+" - "+L("HISTORY","हिस्ट्री"))
                .setView(box)
                .setPositiveButton(L("CLOSE","बंद करें"),null)
                .setNegativeButton(L("CLEAR HISTORY","हिस्ट्री साफ करें"),null)
                .create();

        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
            sp.edit().remove(panelHistoryKey(key)).apply();
            list.removeAllViews();
            TextView empty=tv(L("No history yet.","अभी कोई हिस्ट्री नहीं है।"),16,SOFT);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
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
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(3),dp(3),dp(3),dp(3));
        bar.setBackground(actionBarBg());
        bar.setElevation(dp(3));

        Button history=btn(L("HISTORY","हिस्ट्री"));
        Button share=btn(L("SHARE","शेयर"));

        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-1,1);
        hp.setMargins(0,0,dp(3),0);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-1,1);
        sp.setMargins(dp(3),0,0,0);
        bar.addView(history,hp);
        bar.addView(share,sp);

        LinearLayout.LayoutParams barParams=new LinearLayout.LayoutParams(-1,dp(58));
        barParams.setMargins(0,dp(7),0,0);
        parent.addView(bar,barParams);

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

    private String normalizeManualAgeDate(String raw) throws Exception{
        String s=raw==null?"":raw.trim();
        if(s.isEmpty()) throw new java.text.ParseException("Empty date",0);

        // Fast typing: 05051996 -> 05/05/1996, 050596 -> 05/05/96.
        if(s.matches("\\d{8}")){
            return s.substring(0,2)+"/"+s.substring(2,4)+"/"+s.substring(4,8);
        }
        if(s.matches("\\d{6}")){
            return s.substring(0,2)+"/"+s.substring(2,4)+"/"+s.substring(4,6);
        }

        // Accept slash, dash, dot, spaces, or mixed separators.
        String cleaned=s.replaceAll("[.\\-\\s]+","/");
        cleaned=cleaned.replaceAll("/+","/");
        String[] p=cleaned.split("/");
        if(p.length!=3
                || !p[0].matches("\\d{1,2}")
                || !p[1].matches("\\d{1,2}")
                || !(p[2].matches("\\d{4}") || p[2].matches("\\d{2}"))){
            throw new java.text.ParseException("Use DD/MM/YYYY",0);
        }

        int day=Integer.parseInt(p[0]);
        int month=Integer.parseInt(p[1]);
        String year=p[2];
        return String.format(java.util.Locale.US,"%02d/%02d/%s",day,month,year);
    }

    private Calendar parseManualAgeDate(String raw) throws Exception{
        String s=normalizeManualAgeDate(raw);
        java.text.SimpleDateFormat sdf;
        if(s.matches("\\d{2}/\\d{2}/\\d{4}")){
            sdf=new java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.US);
        }else if(s.matches("\\d{2}/\\d{2}/\\d{2}")){
            sdf=new java.text.SimpleDateFormat("dd/MM/yy",java.util.Locale.US);
        }else{
            throw new java.text.ParseException("Use DD/MM/YYYY",0);
        }
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

    private Calendar birthdayForYear(Calendar birth,int year){
        Calendar x=Calendar.getInstance();
        x.clear();
        x.set(Calendar.YEAR,year);
        x.set(Calendar.MONTH,birth.get(Calendar.MONTH));
        int max=x.getActualMaximum(Calendar.DAY_OF_MONTH);
        x.set(Calendar.DAY_OF_MONTH,Math.min(birth.get(Calendar.DAY_OF_MONTH),max));
        x.set(Calendar.HOUR_OF_DAY,0);
        x.set(Calendar.MINUTE,0);
        x.set(Calendar.SECOND,0);
        x.set(Calendar.MILLISECOND,0);
        return x;
    }

    private int[] calendarDiffYmd(Calendar from,Calendar to){
        int y=to.get(Calendar.YEAR)-from.get(Calendar.YEAR);
        int m=to.get(Calendar.MONTH)-from.get(Calendar.MONTH);
        int d=to.get(Calendar.DAY_OF_MONTH)-from.get(Calendar.DAY_OF_MONTH);
        if(d<0){
            m--;
            Calendar prev=(Calendar)to.clone();
            prev.add(Calendar.MONTH,-1);
            d+=prev.getActualMaximum(Calendar.DAY_OF_MONTH);
        }
        if(m<0){
            y--;
            m+=12;
        }
        return new int[]{y,m,d};
    }

    private String ageWeekday(Calendar c){
        java.util.Locale dayLocale="HINDI".equals(language)
                ?new java.util.Locale("hi","IN")
                :java.util.Locale.ENGLISH;
        return new java.text.SimpleDateFormat("EEEE",dayLocale).format(c.getTime());
    }

    private String ageDate(Calendar c){
        return new java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.US).format(c.getTime());
    }

    private String milestoneLine(Calendar birth,Calendar today,int age){
        Calendar when=birthdayForYear(birth,birth.get(Calendar.YEAR)+age);
        boolean done=!when.after(today);
        return age+" "+L("Years","वर्ष")+": "+ageDate(when)+" ("+ageWeekday(when)+")"
                +"  •  "+(done?L("Completed","पूरा"):L("Upcoming","आने वाला"));
    }

    private String buildAdvancedAgeDetails(Calendar birth){
        Calendar now=Calendar.getInstance();
        Calendar today=(Calendar)now.clone();
        today.set(Calendar.HOUR_OF_DAY,0);
        today.set(Calendar.MINUTE,0);
        today.set(Calendar.SECOND,0);
        today.set(Calendar.MILLISECOND,0);

        if(birth.after(today)) return L("Invalid date","अमान्य तारीख");

        int[] ymd=calendarDiffYmd(birth,today);
        int years=ymd[0], months=ymd[1], days=ymd[2];

        long totalSeconds=Math.max(0L,(now.getTimeInMillis()-birth.getTimeInMillis())/1000L);
        long totalMinutes=totalSeconds/60L;
        long totalHours=totalSeconds/3600L;
        long totalDays=totalSeconds/86400L;
        long totalWeeks=totalDays/7L;
        long totalMonths=(long)years*12L+months;

        int liveHours=now.get(Calendar.HOUR_OF_DAY);
        int liveMinutes=now.get(Calendar.MINUTE);
        int liveSeconds=now.get(Calendar.SECOND);

        int thisYear=today.get(Calendar.YEAR);
        Calendar thisBirthday=birthdayForYear(birth,thisYear);

        Calendar next=thisBirthday.before(today)
                ?birthdayForYear(birth,thisYear+1)
                :thisBirthday;
        Calendar last=thisBirthday.after(today)
                ?birthdayForYear(birth,thisYear-1)
                :thisBirthday;

        long daysToBirthday=(next.getTimeInMillis()-today.getTimeInMillis())/86400000L;
        long daysSinceBirthday=(today.getTimeInMillis()-last.getTimeInMillis())/86400000L;

        int[] remain=calendarDiffYmd(today,next);
        int monthsLeft=remain[0]*12+remain[1];
        int daysLeftPart=remain[2];

        long countdownSeconds=Math.max(0L,(next.getTimeInMillis()-now.getTimeInMillis())/1000L);
        long cdDays=countdownSeconds/86400L;
        long cdHours=(countdownSeconds%86400L)/3600L;
        long cdMinutes=(countdownSeconds%3600L)/60L;
        long cdSeconds=countdownSeconds%60L;

        int nextAge=next.get(Calendar.YEAR)-birth.get(Calendar.YEAR);
        boolean leapDob=birth.get(Calendar.MONTH)==Calendar.FEBRUARY
                && birth.get(Calendar.DAY_OF_MONTH)==29;

        StringBuilder res=new StringBuilder();
        res.append(L("BIRTH INFORMATION","जन्म की जानकारी")).append("\n");
        res.append(L("DATE OF BIRTH","जन्म तिथि")).append(": ").append(ageDate(birth));
        res.append("\n").append(L("BIRTH DAY","जन्म का दिन")).append(": ").append(ageWeekday(birth));
        res.append("\n").append(L("LEAP-DAY DOB","लीप-डे जन्म")).append(": ")
                .append(leapDob?L("Yes","हाँ"):L("No","नहीं"));

        res.append("\n\n").append(L("LIVE AGE COUNTER","लाइव आयु काउंटर")).append("\n");
        res.append(years).append(" ").append(L("Years","वर्ष"))
                .append("  ").append(months).append(" ").append(L("Months","महीने"))
                .append("  ").append(days).append(" ").append(L("Days","दिन"))
                .append("\n")
                .append(liveHours).append(" ").append(L("Hours","घंटे"))
                .append("  ").append(liveMinutes).append(" ").append(L("Minutes","मिनट"))
                .append("  ").append(liveSeconds).append(" ").append(L("Seconds","सेकंड"));

        res.append("\n\n").append(L("TOTAL AGE","कुल आयु")).append("\n");
        res.append(L("TOTAL MONTHS","कुल महीने")).append(": ").append(totalMonths);
        res.append("\n").append(L("TOTAL WEEKS","कुल सप्ताह")).append(": ").append(totalWeeks);
        res.append("\n").append(L("TOTAL DAYS","कुल दिन")).append(": ").append(totalDays);
        res.append("\n").append(L("TOTAL HOURS","कुल घंटे")).append(": ").append(totalHours);
        res.append("\n").append(L("TOTAL MINUTES","कुल मिनट")).append(": ").append(totalMinutes);
        res.append("\n").append(L("TOTAL SECONDS","कुल सेकंड")).append(": ").append(totalSeconds);
        res.append("\n").append(L("COMPLETED BIRTHDAYS","पूरे हुए जन्मदिन")).append(": ").append(years);

        res.append("\n\n").append(L("LAST BIRTHDAY","पिछला जन्मदिन")).append("\n");
        res.append(ageDate(last)).append(" (").append(ageWeekday(last)).append(")");
        res.append("\n").append(L("DAYS AGO","दिन पहले")).append(": ").append(daysSinceBirthday);

        res.append("\n\n").append(L("NEXT BIRTHDAY","अगला जन्मदिन")).append("\n");
        res.append(ageDate(next)).append(" (").append(ageWeekday(next)).append(")");
        res.append("\n").append(L("AGE ON NEXT BIRTHDAY","अगले जन्मदिन पर आयु")).append(": ")
                .append(nextAge).append(" ").append(L("Years","वर्ष"));
        res.append("\n").append(L("CALENDAR TIME LEFT","कैलेंडर समय बाकी")).append(": ")
                .append(monthsLeft).append(" ").append(L("Months","महीने"))
                .append("  ").append(daysLeftPart).append(" ").append(L("Days","दिन"));
        res.append("\n").append(L("DAYS LEFT","बाकी दिन")).append(": ").append(daysToBirthday);
        res.append("\n").append(L("LIVE COUNTDOWN","लाइव काउंटडाउन")).append(": ")
                .append(cdDays).append("d ").append(cdHours).append("h ")
                .append(cdMinutes).append("m ").append(cdSeconds).append("s");

        res.append("\n\n").append(L("AGE MILESTONES","आयु पड़ाव")).append("\n");
        int[] milestones={18,21,25,30,40,50,60};
        for(int i=0;i<milestones.length;i++){
            res.append(milestoneLine(birth,today,milestones[i]));
            if(i<milestones.length-1) res.append("\n");
        }

        if(leapDob){
            res.append("\n\n").append(L(
                    "Leap-day note: In non-leap years, 29 February is shown as 28 February for birthday calculations.",
                    "लीप-डे नोट: गैर-लीप वर्ष में जन्मदिन की गणना के लिए 29 फरवरी को 28 फरवरी माना गया है।"));
        }
        return res.toString();
    }

    private void showAge(){
        currentTool="AGE";
        shell(L("AGE CALCULATOR","आयु कैलकुलेटर"));

        LinearLayout dateRow=new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText dateInput=new EditText(this);
        dateInput.setHint("DD/MM/YYYY");
        dateInput.setHintTextColor(SOFT);
        dateInput.setTextColor(WHITE);
        dateInput.setTextSize(20);
        dateInput.setGravity(Gravity.CENTER);
        dateInput.setSingleLine(true);
        dateInput.setInputType(android.text.InputType.TYPE_CLASS_DATETIME
                |android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
        dateInput.setPadding(dp(14),dp(8),dp(14),dp(8));
        dateInput.setBackground(fieldBg());
        dateInput.setElevation(dp(1));
        attachInputBehavior(dateInput);

        final boolean[] ageDateFormatting={false};
        dateInput.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count){}

            @Override public void afterTextChanged(android.text.Editable editable){
                if(ageDateFormatting[0]) return;
                String current=editable==null?"":editable.toString().trim();
                if(current.isEmpty()) return;

                boolean completeDigits=current.matches("\\d{8}");
                boolean completeSeparated=current.matches(
                        "\\d{1,2}[./\\-\\s]+\\d{1,2}[./\\-\\s]+\\d{4}");

                if(!completeDigits && !completeSeparated) return;

                try{
                    String normalized=normalizeManualAgeDate(current);
                    // Validate before replacing the user's text.
                    parseManualAgeDate(normalized);
                    if(!normalized.equals(current)){
                        ageDateFormatting[0]=true;
                        dateInput.setText(normalized);
                        dateInput.setSelection(normalized.length());
                        ageDateFormatting[0]=false;
                    }
                }catch(Exception ignored){
                    ageDateFormatting[0]=false;
                }
            }
        });

        LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(0,dp(58),1);
        dateParams.setMargins(0,dp(4),dp(4),dp(4));
        dateRow.addView(dateInput,dateParams);

        Button pick=btn(L("DATE","तारीख"));
        pick.setTextSize(15);
        LinearLayout.LayoutParams pickParams=new LinearLayout.LayoutParams(dp(88),dp(58));
        pickParams.setMargins(dp(4),dp(4),0,dp(4));
        dateRow.addView(pick,pickParams);
        root.addView(dateRow,new LinearLayout.LayoutParams(-1,dp(66)));

        Button go=btn(L("SHOW FULL AGE DETAILS","पूरा आयु विवरण दिखाएं"));
        root.addView(go,controlParams(60));

        TextView out=tv(L("Type 05051996 or 05/05/1996","05051996 या 05/05/1996 लिखें"),17,WHITE);
        styleResult(out);
        out.setGravity(Gravity.LEFT|Gravity.TOP);
        out.setPadding(dp(18),dp(16),dp(18),dp(16));

        ScrollView detailsScroll=new ScrollView(this);
        detailsScroll.setFillViewport(true);
        detailsScroll.addView(out,new ScrollView.LayoutParams(-1,-1));
        root.addView(detailsScroll,new LinearLayout.LayoutParams(-1,0,1));
        addHistoryShareBar(root,"age",L("AGE CALCULATOR","आयु कैलकुलेटर"),out);

        final Calendar[] activeBirth={null};
        final android.os.Handler liveHandler=new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] ticker=new Runnable[1];

        ticker[0]=()->{
            if(!"AGE".equals(currentTool) || activeBirth[0]==null) return;
            out.setText(buildAdvancedAgeDetails(activeBirth[0]));
            liveHandler.postDelayed(ticker[0],1000);
        };

        Runnable calculate=()->{
            try{
                String normalized=normalizeManualAgeDate(dateInput.getText().toString());
                Calendar birth=parseManualAgeDate(normalized);
                if(!normalized.equals(dateInput.getText().toString().trim())){
                    ageDateFormatting[0]=true;
                    dateInput.setText(normalized);
                    dateInput.setSelection(normalized.length());
                    ageDateFormatting[0]=false;
                }
                String res=buildAdvancedAgeDetails(birth);
                if(res.equals(L("Invalid date","अमान्य तारीख"))){
                    activeBirth[0]=null;
                    liveHandler.removeCallbacks(ticker[0]);
                    out.setText(res);
                    return;
                }
                activeBirth[0]=(Calendar)birth.clone();
                out.setText(res);
                savePanelHistory("age",L("AGE CALCULATOR","आयु कैलकुलेटर"),res);
                liveHandler.removeCallbacks(ticker[0]);
                liveHandler.postDelayed(ticker[0],1000);
            }catch(Exception e){
                activeBirth[0]=null;
                liveHandler.removeCallbacks(ticker[0]);
                out.setText(L(
                        "Enter a valid date, e.g. 05051996 or 05/05/1996",
                        "सही तारीख लिखें, जैसे 05051996 या 05/05/1996"));
            }
        };

        pick.setOnClickListener(v->{
            Calendar chosen=Calendar.getInstance();
            try{chosen=parseManualAgeDate(dateInput.getText().toString());}catch(Exception ignored){}
            Calendar initial=chosen;
            DatePickerDialog dialog=new DatePickerDialog(this,(view,y,m,d)->{
                Calendar b=Calendar.getInstance();
                b.set(y,m,d,0,0,0);
                b.set(Calendar.MILLISECOND,0);
                dateInput.setText(new java.text.SimpleDateFormat("dd/MM/yyyy",java.util.Locale.US).format(b.getTime()));
                calculate.run();
            },initial.get(Calendar.YEAR),initial.get(Calendar.MONTH),initial.get(Calendar.DAY_OF_MONTH));
            dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            dialog.show();
        });

        go.setOnClickListener(v->calculate.run());
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

    private void migrateMergedFinanceHistory(){
        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        if(sp.getBoolean("merged_finance_history_v1",false)) return;

        String emiRaw=sp.getString(panelHistoryKey("emi"),"");
        String gstRaw=sp.getString(panelHistoryKey("gst"),"");

        if(gstRaw!=null && !gstRaw.isEmpty()){
            String merged=(emiRaw==null || emiRaw.isEmpty())?gstRaw:gstRaw+"\u001e"+emiRaw;
            String[] rows=merged.split("\\u001e",-1);
            StringBuilder keep=new StringBuilder();
            java.util.HashSet<String> seen=new java.util.HashSet<>();
            int count=0;
            for(String row:rows){
                if(row==null || row.isEmpty() || !seen.add(row)) continue;
                if(count>0) keep.append("\u001e");
                keep.append(row);
                count++;
                if(count>=30) break;
            }
            sp.edit().putString(panelHistoryKey("emi"),keep.toString()).apply();
        }
        sp.edit().putBoolean("merged_finance_history_v1",true).apply();
    }

    private void showEmiInterest(){
        currentTool="EMI";
        shell(L("EMI / INTEREST + GST / DISCOUNT","ईएमआई / ब्याज + GST / डिस्काउंट"));
        root.setPadding(dp(4),dp(4),dp(4),dp(4));
        migrateMergedFinanceHistory();

        Spinner calculatorType=dropdown(new String[]{
                L("EMI / INTEREST","EMI / ब्याज"),
                L("GST / DISCOUNT","GST / डिस्काउंट")
        });
        root.addView(calculatorType,controlParams(58));

        LinearLayout financeBody=new LinearLayout(this);
        financeBody.setOrientation(LinearLayout.VERTICAL);
        financeBody.setBackgroundColor(Color.TRANSPARENT);
        root.addView(financeBody,new LinearLayout.LayoutParams(-1,0,1));

        Runnable render=()->{
            financeBody.removeAllViews();

            if(calculatorType.getSelectedItemPosition()==1){
                EditText amt=input(L("Amount","राशि"));
                EditText disc=input(L("Discount %","छूट %"));
                EditText gst=input("GST %");
                financeBody.addView(amt);
                financeBody.addView(disc);
                financeBody.addView(gst);

                Button go=btn(L("CALCULATE GST / DISCOUNT","GST / डिस्काउंट गणना"));
                financeBody.addView(go,controlParams(60));

                TextView out=tv(L("Enter values and calculate","मान भरें और गणना करें"),20,WHITE);
                styleResult(out);
                financeBody.addView(out,new LinearLayout.LayoutParams(-1,0,1));
                addHistoryShareBar(
                        financeBody,
                        "emi",
                        L("EMI / INTEREST + GST / DISCOUNT","ईएमआई / ब्याज + GST / डिस्काउंट"),
                        out);

                go.setOnClickListener(v->{
                    double a=val(amt);
                    double d=a*val(disc)/100.0;
                    double after=a-d;
                    double g=after*val(gst)/100.0;
                    String res=L("GST / DISCOUNT","GST / डिस्काउंट")
                            +"\n"+L("Discount: ₹","छूट: ₹")+df.format(d)
                            +"\n"+L("After Discount: ₹","छूट के बाद: ₹")+df.format(after)
                            +"\nGST: ₹"+df.format(g)
                            +"\n"+L("Final: ₹","अंतिम: ₹")+df.format(after+g);
                    out.setText(res);
                    savePanelHistory(
                            "emi",
                            L("EMI / INTEREST + GST / DISCOUNT","ईएमआई / ब्याज + GST / डिस्काउंट"),
                            res);
                });
            }else{
                Spinner mode=dropdown(new String[]{"EMI",L("SIMPLE INTEREST","साधारण ब्याज")});
                financeBody.addView(mode,controlParams(58));

                EditText loan=input(L("Loan / Principal Amount","लोन / मूल राशि"));
                EditText rate=input(L("Annual Interest %","वार्षिक ब्याज %"));
                EditText months=input(L("Tenure in Months","अवधि (महीने)"));
                financeBody.addView(loan);
                financeBody.addView(rate);
                financeBody.addView(months);

                Button calc=btn(L("CALCULATE","गणना करें"));
                financeBody.addView(calc,controlParams(58));

                TextView out=tv(L("Enter values and calculate","मान भरें और गणना करें"),20,WHITE);
                styleResult(out);
                financeBody.addView(out,new LinearLayout.LayoutParams(-1,0,1));
                addHistoryShareBar(
                        financeBody,
                        "emi",
                        L("EMI / INTEREST + GST / DISCOUNT","ईएमआई / ब्याज + GST / डिस्काउंट"),
                        out);

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
                        res=L("Simple Interest: ₹","साधारण ब्याज: ₹")+df.format(si)
                                +"\n"+L("Total: ₹","कुल: ₹")+df.format(P+si);
                    }
                    out.setText(res);
                    savePanelHistory(
                            "emi",
                            L("EMI / INTEREST + GST / DISCOUNT","ईएमआई / ब्याज + GST / डिस्काउंट"),
                            res);
                });
            }
        };

        calculatorType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                render.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        render.run();
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

    private void migrateMergedQrHistory(){
        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        if(sp.getBoolean("merged_qr_history_v1",false)) return;

        String wifiRaw=sp.getString(panelHistoryKey("wifi_qr"),"");
        String qrRaw=sp.getString(panelHistoryKey("qr"),"");

        if(wifiRaw!=null && !wifiRaw.isEmpty()){
            String merged=(qrRaw==null || qrRaw.isEmpty())?wifiRaw:wifiRaw+"\u001e"+qrRaw;
            String[] rows=merged.split("\\u001e",-1);
            StringBuilder keep=new StringBuilder();
            java.util.HashSet<String> seen=new java.util.HashSet<>();
            int count=0;
            for(String row:rows){
                if(row==null || row.isEmpty() || !seen.add(row)) continue;
                if(count>0) keep.append("\u001e");
                keep.append(row);
                count++;
                if(count>=30) break;
            }
            sp.edit().putString(panelHistoryKey("qr"),keep.toString()).apply();
        }
        sp.edit().putBoolean("merged_qr_history_v1",true).apply();
    }

    private String wifiQrEscape(String value){
        if(value==null) return "";
        return value.replace("\\","\\\\")
                .replace(";","\\;")
                .replace(",","\\,")
                .replace(":","\\:")
                .replace("\"","\\\"");
    }

    private void showQr(){
        currentTool="QR";
        shell(L("QR GENERATOR","QR जनरेटर"));
        root.setPadding(dp(4),dp(4),dp(4),dp(4));
        migrateMergedQrHistory();

        Spinner type=dropdown(new String[]{
                L("TEXT / LINK QR","TEXT / LINK QR"),
                L("WI-FI QR","WI-FI QR")
        });
        root.addView(type,controlParams(58));

        EditText text=input(L("Text / URL","टेक्स्ट / URL"));
        text.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                |android.text.InputType.TYPE_TEXT_VARIATION_URI
                |android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        text.setSingleLine(false);
        text.setMaxLines(3);
        root.addView(text);

        EditText ssid=input(L("Wi-Fi Name (SSID)","वाई-फाई नाम (SSID)"));
        ssid.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        ssid.setSingleLine(true);
        root.addView(ssid);

        EditText password=input(L("Wi-Fi Password","वाई-फाई पासवर्ड"));
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        password.setSingleLine(true);
        root.addView(password);

        Spinner security=dropdown(new String[]{"WPA / WPA2","OPEN","WEP"});
        root.addView(security,controlParams(56));

        Button go=btn(L("GENERATE QR","QR बनाएं"));
        root.addView(go,controlParams(58));

        TextView info=tv(L("Enter text/link and generate QR","Text/Link डालकर QR बनाएं"),14,SOFT);
        info.setGravity(Gravity.CENTER);
        info.setBackground(bg(mixColor(SURFACE,PINK,0.025f),10));
        root.addView(info,controlParams(42));

        FrameLayout qrPreview=new FrameLayout(this);
        qrPreview.setBackground(contentCardBg());

        TextView previewHint=tv(
                L("QR PREVIEW","QR PREVIEW"),
                16,
                SOFT);
        previewHint.setGravity(Gravity.CENTER);
        previewHint.setTypeface(null,1);
        qrPreview.addView(previewHint,new FrameLayout.LayoutParams(-1,-1));

        ImageView img=new ImageView(this);
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setPadding(dp(8),dp(8),dp(8),dp(8));
        img.setBackground(bg(Color.rgb(244,244,242),10));
        img.setVisibility(View.GONE);

        int qrCardSize=Math.min(
                dp(280),
                getResources().getDisplayMetrics().widthPixels-dp(86));
        FrameLayout.LayoutParams qrCardParams=
                new FrameLayout.LayoutParams(qrCardSize,qrCardSize,Gravity.CENTER);
        qrPreview.addView(img,qrCardParams);

        LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,0,1);
        imageParams.setMargins(0,dp(4),0,dp(4));
        root.addView(qrPreview,imageParams);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button history=btn(L("HISTORY","हिस्ट्री"));
        Button share=btn(L("SHARE","शेयर"));
        Button download=btn(L("DOWNLOAD","डाउनलोड"));
        actions.addView(history,new LinearLayout.LayoutParams(0,dp(52),1));
        actions.addView(share,new LinearLayout.LayoutParams(0,dp(52),1));
        actions.addView(download,new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(actions,new LinearLayout.LayoutParams(-1,dp(54)));

        final String[] shareValue={""};
        final String[] historyValue={""};

        Runnable updateMode=()->{
            boolean wifi=type.getSelectedItemPosition()==1;
            text.setVisibility(wifi?View.GONE:View.VISIBLE);
            ssid.setVisibility(wifi?View.VISIBLE:View.GONE);
            password.setVisibility(wifi?View.VISIBLE:View.GONE);
            security.setVisibility(wifi?View.VISIBLE:View.GONE);
            info.setText(wifi
                    ?L("Enter Wi-Fi details and generate QR","Wi-Fi जानकारी भरकर QR बनाएं")
                    :L("Enter text/link and generate QR","Text/Link डालकर QR बनाएं"));
        };

        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                updateMode.run();
                img.setImageDrawable(null);
                img.setVisibility(View.GONE);
                previewHint.setVisibility(View.VISIBLE);
                shareValue[0]="";
                historyValue[0]="";
                lastGeneratedQrBitmap=null;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        updateMode.run();

        go.setOnClickListener(v->{
            boolean wifi=type.getSelectedItemPosition()==1;
            String data;
            String hist;

            if(wifi){
                String network=ssid.getText().toString().trim();
                if(network.isEmpty()){
                    Toast.makeText(this,L("Enter Wi-Fi name first","पहले Wi-Fi नाम भरें"),Toast.LENGTH_SHORT).show();
                    return;
                }

                String sec=security.getSelectedItemPosition()==1
                        ?"nopass"
                        :(security.getSelectedItemPosition()==2?"WEP":"WPA");
                String pwd=password.getText().toString();

                data="WIFI:T:"+sec+";S:"+wifiQrEscape(network)+";";
                if(!"nopass".equals(sec)) data+="P:"+wifiQrEscape(pwd)+";";
                data+=";";

                hist=L("Wi-Fi: ","Wi-Fi: ")+network+"  ["+
                        (security.getSelectedItem()==null?"WPA / WPA2":security.getSelectedItem().toString())+"]";
                info.setText(L("Wi-Fi QR generated for: ","Wi-Fi QR बना: ")+network);
            }else{
                String value=text.getText().toString().trim();
                if(value.isEmpty()){
                    Toast.makeText(this,L("Enter text or link first","पहले text या link भरें"),Toast.LENGTH_SHORT).show();
                    return;
                }
                data=value;
                hist=value;
                info.setText(L("QR generated","QR बन गया"));
            }

            Bitmap bm=qrBitmap(data,800);
            if(bm!=null){
                if(lastGeneratedQrBitmap!=null && lastGeneratedQrBitmap!=bm){
                    try{lastGeneratedQrBitmap.recycle();}catch(Exception ignored){}
                }
                lastGeneratedQrBitmap=bm;
                img.setImageBitmap(bm);
                img.setVisibility(View.VISIBLE);
                previewHint.setVisibility(View.GONE);
                shareValue[0]=data;
                historyValue[0]=hist;
                savePanelHistory("qr",L("QR GENERATOR","QR जनरेटर"),hist);
            }
        });

        history.setOnClickListener(v->{
            if(meaningfulResult(historyValue[0])){
                savePanelHistory("qr",L("QR GENERATOR","QR जनरेटर"),historyValue[0]);
            }
            showPanelHistory("qr",L("QR GENERATOR","QR जनरेटर"));
        });

        share.setOnClickListener(v->sharePanelText(
                L("QR GENERATOR","QR जनरेटर"),
                shareValue[0]));

        download.setOnClickListener(v->{
            if(lastGeneratedQrBitmap==null || lastGeneratedQrBitmap.isRecycled()){
                Toast.makeText(this,L("Generate QR first","पहले QR बनाएं"),Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(L("DOWNLOAD QR","QR डाउनलोड"))
                    .setItems(new String[]{"JPG","JPEG","PNG","PDF"},(d,which)->{
                        pendingQrSaveFormat=new String[]{"JPG","JPEG","PNG","PDF"}[which];
                        String stamp=new java.text.SimpleDateFormat(
                                "yyyyMMdd-HHmmss",
                                java.util.Locale.US).format(new java.util.Date());
                        launchBitmapSave("STS-DigiKit-QR-"+stamp,pendingQrSaveFormat,REQ_SAVE_QR_IMAGE);
                    })
                    .show();
        });
    }

    private Bitmap qrBitmap(String data,int size){try{BitMatrix m=new MultiFormatWriter().encode(data,BarcodeFormat.QR_CODE,size,size);Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565);for(int y=0;y<size;y++)for(int x=0;x<size;x++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);return b;}catch(Exception e){Toast.makeText(this,"QR error",Toast.LENGTH_SHORT).show();return null;}}


    private boolean handleIncomingPdfIntent(Intent intent){
        if(intent==null || !Intent.ACTION_VIEW.equals(intent.getAction())) return false;
        Uri uri=intent.getData();
        if(uri==null) return false;
        String type=intent.getType();
        String name=getDisplayName(uri).toLowerCase(java.util.Locale.US);
        if((type!=null && type.toLowerCase(java.util.Locale.US).contains("pdf")) || name.endsWith(".pdf")){
            showPdfViewer(uri);
            return true;
        }
        return false;
    }

    private String getDisplayName(Uri uri){
        if(uri==null) return "";
        android.database.Cursor c=null;
        try{
            c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null);
            if(c!=null && c.moveToFirst()){
                int i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if(i>=0) return c.getString(i);
            }
        }catch(Exception ignored){
        }finally{
            if(c!=null) try{c.close();}catch(Exception ignored){}
        }
        String p=uri.getLastPathSegment();
        return p==null?"document":p;
    }

    private Bitmap loadWorkBitmap(Uri uri,int maxDimension) throws Exception{
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        java.io.InputStream in=getContentResolver().openInputStream(uri);
        if(in==null) throw new java.io.IOException("Image not available");
        android.graphics.BitmapFactory.decodeStream(in,null,bounds);
        in.close();

        int sample=1;
        int largest=Math.max(bounds.outWidth,bounds.outHeight);
        while(largest/sample>maxDimension) sample*=2;

        android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();
        opts.inSampleSize=Math.max(1,sample);
        opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        in=getContentResolver().openInputStream(uri);
        if(in==null) throw new java.io.IOException("Image not available");
        Bitmap bm=android.graphics.BitmapFactory.decodeStream(in,null,opts);
        in.close();
        if(bm==null) throw new java.io.IOException("Image decode failed");
        return bm;
    }

    private Bitmap resizeFitCrop(Bitmap src,int targetW,int targetH,boolean crop){
        targetW=Math.max(1,targetW);
        targetH=Math.max(1,targetH);
        Bitmap out=Bitmap.createBitmap(targetW,targetH,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);
        canvas.drawColor(Color.WHITE);

        float sx=targetW/(float)src.getWidth();
        float sy=targetH/(float)src.getHeight();
        float scale=crop?Math.max(sx,sy):Math.min(sx,sy);
        float dw=src.getWidth()*scale;
        float dh=src.getHeight()*scale;
        float left=(targetW-dw)/2f;
        float top=(targetH-dh)/2f;

        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src,null,new RectF(left,top,left+dw,top+dh),p);
        return out;
    }

    private int dimensionToPx(String value,String unit,int dpi){
        double v;
        try{v=Double.parseDouble(value.trim());}catch(Exception e){return 0;}
        if(v<=0) return 0;
        if("MM".equals(unit)) return Math.max(1,(int)Math.round(v*dpi/25.4));
        if("CM".equals(unit)) return Math.max(1,(int)Math.round(v*dpi/2.54));
        return Math.max(1,(int)Math.round(v));
    }

    private byte[] jpegBytesForTarget(Bitmap bitmap,int targetKb) throws Exception{
        int quality=96;
        byte[] last=null;
        while(quality>=30){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out);
            last=out.toByteArray();
            if(targetKb<=0 || last.length<=targetKb*1024) return last;
            quality-=8;
        }
        return last==null?new byte[0]:last;
    }

    private void writeBitmapExport(Bitmap bitmap,java.io.OutputStream out,String format,int targetKb) throws Exception{
        if("PDF".equals(format)){
            android.graphics.pdf.PdfDocument doc=new android.graphics.pdf.PdfDocument();
            int pw=595,ph=842;
            android.graphics.pdf.PdfDocument.PageInfo info=
                    new android.graphics.pdf.PdfDocument.PageInfo.Builder(pw,ph,1).create();
            android.graphics.pdf.PdfDocument.Page page=doc.startPage(info);
            Canvas c=page.getCanvas();
            c.drawColor(Color.WHITE);
            float scale=Math.min((pw-30f)/bitmap.getWidth(),(ph-30f)/bitmap.getHeight());
            float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
            float l=(pw-w)/2f,t=(ph-h)/2f;
            c.drawBitmap(bitmap,null,new RectF(l,t,l+w,t+h),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            doc.finishPage(page);
            doc.writeTo(out);
            doc.close();
            return;
        }

        if("JPG".equals(format) || "JPEG".equals(format)){
            byte[] bytes=jpegBytesForTarget(bitmap,targetKb);
            out.write(bytes);
            return;
        }
        bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
    }

    private String exportMime(String format){
        if("PDF".equals(format)) return "application/pdf";
        if("PNG".equals(format)) return "image/png";
        return "image/jpeg";
    }

    private String exportExtension(String format){
        if("PDF".equals(format)) return ".pdf";
        if("PNG".equals(format)) return ".png";
        if("JPEG".equals(format)) return ".jpeg";
        return ".jpg";
    }

    private void launchBitmapSave(String title,String format,int requestCode){
        try{
            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            save.addCategory(Intent.CATEGORY_OPENABLE);
            save.setType(exportMime(format));
            save.putExtra(Intent.EXTRA_TITLE,title+exportExtension(format));
            startActivityForResult(save,requestCode);
        }catch(Exception e){
            Toast.makeText(this,L("Save screen could not open","Save screen नहीं खुल सकी"),Toast.LENGTH_SHORT).show();
        }
    }

    private void showPhotoSignatureResizer(){
        currentTool="PHOTO_RESIZER";
        shell(L("PHOTO / SIGNATURE RESIZER","फोटो / सिग्नेचर रिसाइज़र"));
        root.setPadding(dp(4),dp(4),dp(4),dp(4));

        Spinner mode=dropdown(new String[]{L("PHOTO","फोटो"),L("SIGNATURE","सिग्नेचर")});
        root.addView(mode,controlParams(56));

        Button pick=btn(L("SELECT IMAGE","इमेज चुनें"));
        root.addView(pick,controlParams(58));

        TextView selected=tv(
                resizerSourceUri==null
                        ?L("No image selected","कोई इमेज नहीं चुनी गई")
                        :getDisplayName(resizerSourceUri),
                14,SOFT);
        selected.setGravity(Gravity.CENTER);
        root.addView(selected,controlParams(42));

        FrameLayout preview=new FrameLayout(this);
        preview.setBackground(contentCardBg());
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setAdjustViewBounds(true);
        preview.addView(image,new FrameLayout.LayoutParams(-1,-1));
        if(resizerOutputBitmap!=null && !resizerOutputBitmap.isRecycled()) image.setImageBitmap(resizerOutputBitmap);
        else if(resizerSourceBitmap!=null && !resizerSourceBitmap.isRecycled()) image.setImageBitmap(resizerSourceBitmap);
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout wh=new LinearLayout(this);
        wh.setOrientation(LinearLayout.HORIZONTAL);
        EditText width=input(L("Width","चौड़ाई"));
        EditText height=input(L("Height","ऊंचाई"));
        wh.addView(width,new LinearLayout.LayoutParams(0,dp(56),1));
        wh.addView(height,new LinearLayout.LayoutParams(0,dp(56),1));
        root.addView(wh,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout options=new LinearLayout(this);
        options.setOrientation(LinearLayout.HORIZONTAL);
        Spinner unit=dropdown(new String[]{"PX","MM","CM"});
        Spinner fit=dropdown(new String[]{L("FIT","फिट"),L("CROP","क्रॉप")});
        options.addView(unit,new LinearLayout.LayoutParams(0,dp(54),1));
        options.addView(fit,new LinearLayout.LayoutParams(0,dp(54),1));
        root.addView(options,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout details=new LinearLayout(this);
        details.setOrientation(LinearLayout.HORIZONTAL);
        EditText dpi=input("DPI (Default 300)");
        EditText kb=input(L("Target KB (Optional)","Target KB (वैकल्पिक)"));
        details.addView(dpi,new LinearLayout.LayoutParams(0,dp(56),1));
        details.addView(kb,new LinearLayout.LayoutParams(0,dp(56),1));
        root.addView(details,new LinearLayout.LayoutParams(-1,dp(58)));

        TextView result=tv(L("Select image, set size and resize","Image चुनें, size भरें और resize करें"),14,SOFT);
        result.setGravity(Gravity.CENTER);
        root.addView(result,controlParams(44));

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button process=btn(L("RESIZE","रिसाइज़"));
        Button download=btn(L("DOWNLOAD","डाउनलोड"));
        actions.addView(process,new LinearLayout.LayoutParams(0,dp(56),1));
        actions.addView(download,new LinearLayout.LayoutParams(0,dp(56),1));
        root.addView(actions,new LinearLayout.LayoutParams(-1,dp(58)));

        pick.setOnClickListener(v->{
            try{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i,REQ_PICK_RESIZER_IMAGE);
            }catch(Exception e){
                Toast.makeText(this,L("Gallery could not open","Gallery नहीं खुल सकी"),Toast.LENGTH_SHORT).show();
            }
        });

        process.setOnClickListener(v->{
            if(resizerSourceBitmap==null || resizerSourceBitmap.isRecycled()){
                Toast.makeText(this,L("Select image first","पहले image चुनें"),Toast.LENGTH_SHORT).show();
                return;
            }
            int dpiValue=300;
            try{if(!dpi.getText().toString().trim().isEmpty()) dpiValue=Math.max(72,Integer.parseInt(dpi.getText().toString().trim()));}catch(Exception ignored){}
            String u=String.valueOf(unit.getSelectedItem());
            int tw=dimensionToPx(width.getText().toString(),u,dpiValue);
            int th=dimensionToPx(height.getText().toString(),u,dpiValue);

            if(tw<=0 && th<=0){
                tw=resizerSourceBitmap.getWidth();
                th=resizerSourceBitmap.getHeight();
            }else if(tw<=0){
                tw=Math.max(1,(int)Math.round(th*(resizerSourceBitmap.getWidth()/(double)resizerSourceBitmap.getHeight())));
            }else if(th<=0){
                th=Math.max(1,(int)Math.round(tw*(resizerSourceBitmap.getHeight()/(double)resizerSourceBitmap.getWidth())));
            }

            if(tw>6000 || th>6000){
                Toast.makeText(this,L("Maximum output dimension is 6000 px","Maximum output 6000 px है"),Toast.LENGTH_LONG).show();
                return;
            }

            try{resizerTargetKb=Math.max(0,Integer.parseInt(kb.getText().toString().trim()));}catch(Exception ignored){resizerTargetKb=0;}
            Bitmap made=resizeFitCrop(resizerSourceBitmap,tw,th,fit.getSelectedItemPosition()==1);
            if(resizerOutputBitmap!=null && resizerOutputBitmap!=resizerSourceBitmap && !resizerOutputBitmap.isRecycled()){
                try{resizerOutputBitmap.recycle();}catch(Exception ignored){}
            }
            resizerOutputBitmap=made;
            image.setImageBitmap(made);
            result.setText(tw+" × "+th+" px  •  "+dpiValue+" DPI"
                    +(resizerTargetKb>0?("  •  "+L("Target ","Target ")+resizerTargetKb+" KB"):""));
        });

        download.setOnClickListener(v->{
            if(resizerOutputBitmap==null || resizerOutputBitmap.isRecycled()){
                Toast.makeText(this,L("Resize image first","पहले image resize करें"),Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(L("DOWNLOAD AS","इस format में डाउनलोड"))
                    .setItems(new String[]{"JPG","JPEG","PNG","PDF"},(d,which)->{
                        pendingResizerFormat=new String[]{"JPG","JPEG","PNG","PDF"}[which];
                        launchBitmapSave("STS-DigiKit-Resized",pendingResizerFormat,REQ_SAVE_RESIZER);
                    })
                    .show();
        });
    }

    private void pickPdfFiles(){
        try{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            startActivityForResult(i,REQ_PICK_PDFS);
        }catch(Exception e){
            Toast.makeText(this,L("PDF picker could not open","PDF picker नहीं खुल सका"),Toast.LENGTH_SHORT).show();
        }
    }

    private int countPdfPages(){
        int total=0;
        for(Uri uri:pdfToolUris){
            android.os.ParcelFileDescriptor pfd=null;
            android.graphics.pdf.PdfRenderer r=null;
            try{
                pfd=getContentResolver().openFileDescriptor(uri,"r");
                if(pfd==null) continue;
                r=new android.graphics.pdf.PdfRenderer(pfd);
                total+=r.getPageCount();
            }catch(Exception ignored){
            }finally{
                if(r!=null) try{r.close();}catch(Exception ignored){}
                if(pfd!=null) try{pfd.close();}catch(Exception ignored){}
            }
        }
        return total;
    }

    private byte[] buildJoinedPdfBytes(float renderScale,String pageMode) throws Exception{
        android.graphics.pdf.PdfDocument output=new android.graphics.pdf.PdfDocument();
        int outputPageNo=1;
        try{
            for(Uri uri:pdfToolUris){
                android.os.ParcelFileDescriptor pfd=null;
                android.graphics.pdf.PdfRenderer renderer=null;
                try{
                    pfd=getContentResolver().openFileDescriptor(uri,"r");
                    if(pfd==null) continue;
                    renderer=new android.graphics.pdf.PdfRenderer(pfd);
                    for(int i=0;i<renderer.getPageCount();i++){
                        android.graphics.pdf.PdfRenderer.Page rp=renderer.openPage(i);
                        try{
                            int bw=Math.max(1,(int)(rp.getWidth()*renderScale));
                            int bh=Math.max(1,(int)(rp.getHeight()*renderScale));
                            int largest=Math.max(bw,bh);
                            if(largest>1800){
                                float f=1800f/largest;
                                bw=Math.max(1,(int)(bw*f));
                                bh=Math.max(1,(int)(bh*f));
                            }
                            Bitmap bm=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);
                            Canvas bc=new Canvas(bm);
                            bc.drawColor(Color.WHITE);
                            rp.render(bm,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                            int pw,ph;
                            if("A4".equals(pageMode)){pw=595;ph=842;}
                            else if("LEGAL".equals(pageMode)){pw=612;ph=1008;}
                            else{pw=Math.max(1,rp.getWidth());ph=Math.max(1,rp.getHeight());}

                            android.graphics.pdf.PdfDocument.PageInfo pi=
                                    new android.graphics.pdf.PdfDocument.PageInfo.Builder(pw,ph,outputPageNo++).create();
                            android.graphics.pdf.PdfDocument.Page op=output.startPage(pi);
                            Canvas c=op.getCanvas();
                            c.drawColor(Color.WHITE);
                            float sc=Math.min(pw/(float)bm.getWidth(),ph/(float)bm.getHeight());
                            float dw=bm.getWidth()*sc,dh=bm.getHeight()*sc;
                            float l=(pw-dw)/2f,t=(ph-dh)/2f;
                            c.drawBitmap(bm,null,new RectF(l,t,l+dw,t+dh),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
                            output.finishPage(op);
                            bm.recycle();
                        }finally{
                            try{rp.close();}catch(Exception ignored){}
                        }
                    }
                }finally{
                    if(renderer!=null) try{renderer.close();}catch(Exception ignored){}
                    if(pfd!=null) try{pfd.close();}catch(Exception ignored){}
                }
            }
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            output.writeTo(out);
            return out.toByteArray();
        }finally{
            output.close();
        }
    }

    private void showPdfJoinResize(){
        currentTool="PDF_TOOLS";
        shell(L("PDF JOIN / RESIZE","PDF जोड़ें / रिसाइज़"));
        root.setPadding(dp(4),dp(4),dp(4),dp(4));

        Button pick=btn(L("SELECT PDF FILES","PDF FILES चुनें"));
        root.addView(pick,controlParams(58));

        TextView count=tv(
                pdfToolUris.isEmpty()
                        ?L("No PDF selected","कोई PDF नहीं चुना गया")
                        :pdfToolUris.size()+" PDF  •  "+countPdfPages()+" "+L("pages","pages"),
                14,SOFT);
        count.setGravity(Gravity.CENTER);
        root.addView(count,controlParams(42));

        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<pdfToolUris.size();i++){
            final int pos=i;
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8),dp(6),dp(8),dp(6));
            card.setBackground(contentCardBg());

            TextView name=tv((i+1)+". "+getDisplayName(pdfToolUris.get(i)),15,WHITE);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(name,new LinearLayout.LayoutParams(-1,dp(40)));

            LinearLayout buttons=new LinearLayout(this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            Button up=btn("▲");
            Button down=btn("▼");
            Button del=btn(L("DELETE","हटाएं"));
            buttons.addView(up,new LinearLayout.LayoutParams(0,dp(46),1));
            buttons.addView(down,new LinearLayout.LayoutParams(0,dp(46),1));
            buttons.addView(del,new LinearLayout.LayoutParams(0,dp(46),1));
            card.addView(buttons,new LinearLayout.LayoutParams(-1,dp(48)));

            up.setOnClickListener(v->{
                if(pos>0){
                    Uri a=pdfToolUris.get(pos-1);
                    pdfToolUris.set(pos-1,pdfToolUris.get(pos));
                    pdfToolUris.set(pos,a);
                    showPdfJoinResize();
                }
            });
            down.setOnClickListener(v->{
                if(pos<pdfToolUris.size()-1){
                    Uri a=pdfToolUris.get(pos+1);
                    pdfToolUris.set(pos+1,pdfToolUris.get(pos));
                    pdfToolUris.set(pos,a);
                    showPdfJoinResize();
                }
            });
            del.setOnClickListener(v->{
                pdfToolUris.remove(pos);
                lastJoinedPdfBytes=null;
                showPdfJoinResize();
            });

            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
            cp.setMargins(0,0,0,dp(6));
            list.addView(card,cp);
        }
        root.addView(list,new LinearLayout.LayoutParams(-1,-2));

        Spinner pageSize=dropdown(new String[]{"ORIGINAL","A4","LEGAL"});
        root.addView(pageSize,controlParams(56));

        EditText target=input(L("Target PDF KB (Optional)","Target PDF KB (वैकल्पिक)"));
        root.addView(target);

        TextView result=tv(
                lastJoinedPdfBytes==null
                        ?L("Create PDF after selecting files","Files चुनकर PDF बनाएं")
                        :L("Ready: ","तैयार: ")+df.format(lastJoinedPdfBytes.length/1024.0)+" KB",
                14,SOFT);
        result.setGravity(Gravity.CENTER);
        root.addView(result,controlParams(44));

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button create=btn(L("JOIN / RESIZE","जोड़ें / रिसाइज़"));
        Button download=btn(L("DOWNLOAD","डाउनलोड"));
        actions.addView(create,new LinearLayout.LayoutParams(0,dp(56),1));
        actions.addView(download,new LinearLayout.LayoutParams(0,dp(56),1));
        root.addView(actions,new LinearLayout.LayoutParams(-1,dp(58)));

        pick.setOnClickListener(v->pickPdfFiles());

        create.setOnClickListener(v->{
            if(pdfToolUris.isEmpty()){
                Toast.makeText(this,L("Select PDF files first","पहले PDF files चुनें"),Toast.LENGTH_SHORT).show();
                return;
            }
            int targetKb=0;
            try{targetKb=Math.max(0,Integer.parseInt(target.getText().toString().trim()));}catch(Exception ignored){}
            final int requestedKb=targetKb;
            final String mode=String.valueOf(pageSize.getSelectedItem());
            result.setText(L("Processing PDF...","PDF process हो रहा है..."));
            create.setEnabled(false);

            new Thread(()->{
                try{
                    float scale=1.35f;
                    byte[] made=null;
                    for(int attempt=0;attempt<5;attempt++){
                        made=buildJoinedPdfBytes(scale,mode);
                        if(requestedKb<=0 || made.length<=requestedKb*1024 || scale<=0.42f) break;
                        scale*=0.76f;
                    }
                    lastJoinedPdfBytes=made;
                    lastPdfBuildScale=scale;
                    lastPdfPageMode=mode;
                    final byte[] done=made;
                    runOnUiThread(()->{
                        create.setEnabled(true);
                        result.setText(L("Ready: ","तैयार: ")+df.format(done.length/1024.0)+" KB"
                                +"  •  "+countPdfPages()+" "+L("pages","pages"));
                    });
                }catch(Exception e){
                    runOnUiThread(()->{
                        create.setEnabled(true);
                        result.setText(L("PDF processing failed","PDF process नहीं हो सका"));
                    });
                }
            }).start();
        });

        download.setOnClickListener(v->{
            if(lastJoinedPdfBytes==null || lastJoinedPdfBytes.length==0){
                Toast.makeText(this,L("Create PDF first","पहले PDF बनाएं"),Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(L("DOWNLOAD AS","इस format में डाउनलोड"))
                    .setItems(new String[]{"JPG","JPEG","PNG","PDF"},(d,which)->{
                        pendingPdfExportFormat=new String[]{"JPG","JPEG","PNG","PDF"}[which];
                        if("PDF".equals(pendingPdfExportFormat)){
                            try{
                                Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                save.addCategory(Intent.CATEGORY_OPENABLE);
                                save.setType("application/pdf");
                                save.putExtra(Intent.EXTRA_TITLE,"STS-DigiKit-Joined.pdf");
                                startActivityForResult(save,REQ_SAVE_JOINED_PDF);
                            }catch(Exception e){
                                Toast.makeText(this,L("Save screen could not open","Save screen नहीं खुल सकी"),Toast.LENGTH_SHORT).show();
                            }
                        }else{
                            try{
                                Intent folder=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                                startActivityForResult(folder,REQ_SAVE_PDF_IMAGES_DIR);
                            }catch(Exception e){
                                Toast.makeText(this,L("Folder picker could not open","Folder picker नहीं खुल सका"),Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .show();
        });
    }

    private Uri treeRootDocument(Uri treeUri){
        String id=android.provider.DocumentsContract.getTreeDocumentId(treeUri);
        return android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri,id);
    }

    private void exportPdfSourcesAsImages(Uri treeUri,String format){
        new Thread(()->{
            int saved=0;
            try{
                Uri parent=treeRootDocument(treeUri);
                int pageNo=1;
                for(Uri src:pdfToolUris){
                    android.os.ParcelFileDescriptor pfd=null;
                    android.graphics.pdf.PdfRenderer renderer=null;
                    try{
                        pfd=getContentResolver().openFileDescriptor(src,"r");
                        if(pfd==null) continue;
                        renderer=new android.graphics.pdf.PdfRenderer(pfd);
                        for(int i=0;i<renderer.getPageCount();i++){
                            android.graphics.pdf.PdfRenderer.Page page=renderer.openPage(i);
                            try{
                                int w=Math.max(1,(int)(page.getWidth()*Math.max(0.75f,lastPdfBuildScale)));
                                int h=Math.max(1,(int)(page.getHeight()*Math.max(0.75f,lastPdfBuildScale)));
                                int largest=Math.max(w,h);
                                if(largest>1800){
                                    float f=1800f/largest;
                                    w=(int)(w*f);h=(int)(h*f);
                                }
                                Bitmap bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                                Canvas c=new Canvas(bm);c.drawColor(Color.WHITE);
                                page.render(bm,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                                String ext=exportExtension(format);
                                String mime=exportMime(format);
                                Uri file=android.provider.DocumentsContract.createDocument(
                                        getContentResolver(),parent,mime,
                                        String.format(java.util.Locale.US,"STS-PDF-page-%03d%s",pageNo++,ext));
                                if(file!=null){
                                    java.io.OutputStream out=getContentResolver().openOutputStream(file);
                                    if(out!=null){
                                        writeBitmapExport(bm,out,format,0);
                                        out.close();
                                        saved++;
                                    }
                                }
                                bm.recycle();
                            }finally{
                                page.close();
                            }
                        }
                    }finally{
                        if(renderer!=null) try{renderer.close();}catch(Exception ignored){}
                        if(pfd!=null) try{pfd.close();}catch(Exception ignored){}
                    }
                }
                final int count=saved;
                runOnUiThread(()->Toast.makeText(this,
                        L("Saved ","सेव हुए ")+count+" "+L("pages","pages"),
                        Toast.LENGTH_LONG).show());
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,L("Image export failed","Image export नहीं हो सका"),Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void closePdfViewerResources(){
        viewerRenderGeneration++;
        if(viewerRenderExecutor!=null){
            try{viewerRenderExecutor.shutdownNow();}catch(Exception ignored){}
            viewerRenderExecutor=null;
        }
        viewerPagesLoading.clear();
        if(viewerPageCache!=null){
            try{viewerPageCache.evictAll();}catch(Exception ignored){}
            viewerPageCache=null;
        }

        synchronized(viewerRendererLock){
            if(viewerPdfPage!=null){try{viewerPdfPage.close();}catch(Exception ignored){} viewerPdfPage=null;}
            if(viewerPdfRenderer!=null){try{viewerPdfRenderer.close();}catch(Exception ignored){} viewerPdfRenderer=null;}
            if(viewerPdfPfd!=null){try{viewerPdfPfd.close();}catch(Exception ignored){} viewerPdfPfd=null;}
        }

        if(viewerBitmap!=null && !viewerBitmap.isRecycled()){
            try{viewerBitmap.recycle();}catch(Exception ignored){}
        }
        viewerBitmap=null;
        viewerViewport=null;
        viewerContinuousList=null;
        viewerModeButton=null;
        viewerContinuousMode=false;
    }

    private void resetViewerTransform(){
        if(viewerImage==null || viewerBitmap==null || viewerBitmap.isRecycled()) return;
        viewerImage.post(()->{
            if(viewerImage==null || viewerBitmap==null || viewerBitmap.isRecycled()) return;
            int vw=viewerImage.getWidth();
            int vh=viewerImage.getHeight();
            if(vw<=0 || vh<=0) return;

            viewerBaseScale=Math.min(
                    vw/(float)viewerBitmap.getWidth(),
                    vh/(float)viewerBitmap.getHeight());
            if(viewerBaseScale<=0f) viewerBaseScale=1f;

            float dw=viewerBitmap.getWidth()*viewerBaseScale;
            float dh=viewerBitmap.getHeight()*viewerBaseScale;
            float dx=(vw-dw)/2f;
            float dy=(vh-dh)/2f;

            viewerMatrix.reset();
            viewerMatrix.setScale(viewerBaseScale,viewerBaseScale);
            viewerMatrix.postTranslate(dx,dy);
            viewerZoom=1f;
            viewerImage.setImageMatrix(viewerMatrix);
        });
    }

    private void constrainViewerMatrix(){
        if(viewerImage==null || viewerBitmap==null || viewerBitmap.isRecycled()) return;
        int vw=viewerImage.getWidth();
        int vh=viewerImage.getHeight();
        if(vw<=0 || vh<=0) return;

        RectF rect=new RectF(0,0,viewerBitmap.getWidth(),viewerBitmap.getHeight());
        viewerMatrix.mapRect(rect);

        float dx=0f,dy=0f;
        if(rect.width()<=vw){
            dx=vw/2f-rect.centerX();
        }else{
            if(rect.left>0) dx=-rect.left;
            else if(rect.right<vw) dx=vw-rect.right;
        }

        if(rect.height()<=vh){
            dy=vh/2f-rect.centerY();
        }else{
            if(rect.top>0) dy=-rect.top;
            else if(rect.bottom<vh) dy=vh-rect.bottom;
        }

        if(dx!=0f || dy!=0f) viewerMatrix.postTranslate(dx,dy);
    }

    private void viewerScaleAround(float factor,float focusX,float focusY){
        if(viewerImage==null || viewerBitmap==null || viewerBitmap.isRecycled()) return;
        float target=viewerZoom*factor;
        if(target<1f) factor=1f/viewerZoom;
        else if(target>5f) factor=5f/viewerZoom;

        if(Math.abs(factor-1f)<0.0001f) return;
        viewerMatrix.postScale(factor,factor,focusX,focusY);
        viewerZoom=Math.max(1f,Math.min(5f,viewerZoom*factor));
        constrainViewerMatrix();
        viewerImage.setImageMatrix(viewerMatrix);

        if(viewerZoom<=1.01f) resetViewerTransform();
    }

    private void showPdfViewer(Uri uri){
        closePdfViewerResources();
        currentTool="PDF_VIEWER";
        viewerPdfUri=uri;
        viewerPageIndex=0;
        viewerContinuousMode=false;
        viewerRenderGeneration++;
        try{
            viewerPdfPfd=getContentResolver().openFileDescriptor(uri,"r");
            if(viewerPdfPfd==null) throw new java.io.IOException("PDF unavailable");
            viewerPdfRenderer=new android.graphics.pdf.PdfRenderer(viewerPdfPfd);
        }catch(Exception e){
            closePdfViewerResources();
            Toast.makeText(this,L("PDF could not open","PDF नहीं खुल सका"),Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14),dp(8),dp(8),dp(8));
        header.setBackground(actionBarBg());

        LinearLayout titleBox=new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);

        TextView name=tv(getDisplayName(uri),18,WHITE);
        name.setTypeface(null,1);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setPadding(0,0,dp(8),0);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);

        viewerPageLabel=tv("",12,SOFT);
        viewerPageLabel.setGravity(Gravity.CENTER_VERTICAL);
        viewerPageLabel.setPadding(0,0,dp(8),0);
        viewerPageLabel.setSingleLine(true);
        viewerPageLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);

        titleBox.addView(name,new LinearLayout.LayoutParams(-1,dp(36)));
        titleBox.addView(viewerPageLabel,new LinearLayout.LayoutParams(-1,dp(24)));
        header.addView(titleBox,new LinearLayout.LayoutParams(0,dp(60),1));

        viewerModeButton=tv(L("SCROLL","SCROLL"),12,WHITE);
        viewerModeButton.setGravity(Gravity.CENTER);
        viewerModeButton.setTypeface(null,1);
        viewerModeButton.setPadding(dp(5),0,dp(5),0);
        viewerModeButton.setBackground(touchBg(mixColor(PANEL2,INDIGO,0.10f),12));
        LinearLayout.LayoutParams modeParams=new LinearLayout.LayoutParams(dp(72),dp(52));
        modeParams.setMargins(0,0,dp(6),0);
        header.addView(viewerModeButton,modeParams);

        TextView menu=tv("⋮",32,WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setPadding(0,0,0,0);
        menu.setBackground(touchBg(PANEL,12));
        header.addView(menu,new LinearLayout.LayoutParams(dp(52),dp(52)));
        outer.addView(header,new LinearLayout.LayoutParams(-1,dp(80)));

        viewerViewport=new FrameLayout(this);
        viewerViewport.setBackgroundColor(BG);
        viewerImage=new ContinuousPdfPageView(this);
        viewerImage.setBackgroundColor(BG);
        viewerImage.setHorizontalPageSwipe(
                true,
                ()->{
                    if(viewerPageIndex>0){
                        viewerPageIndex--;
                        renderViewerPage();
                    }
                },
                ()->{
                    if(viewerPdfRenderer!=null
                            && viewerPageIndex<viewerPdfRenderer.getPageCount()-1){
                        viewerPageIndex++;
                        renderViewerPage();
                    }
                });
        viewerViewport.addView(viewerImage,new FrameLayout.LayoutParams(-1,-1));
        outer.addView(viewerViewport,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(outer);

        viewerModeButton.setOnClickListener(v->{
            haptic();
            setViewerContinuousMode(!viewerContinuousMode);
        });
        menu.setOnClickListener(v->showPdfViewerMenu(menu));
        renderViewerPage();
    }


    private void setViewerContinuousMode(boolean continuous){
        if(viewerViewport==null || viewerPdfRenderer==null) return;
        if(viewerContinuousMode==continuous) return;

        viewerContinuousMode=continuous;
        viewerRenderGeneration++;

        if(viewerRenderExecutor!=null){
            try{viewerRenderExecutor.shutdownNow();}catch(Exception ignored){}
            viewerRenderExecutor=null;
        }
        viewerPagesLoading.clear();

        if(continuous){
            synchronized(viewerRendererLock){
                if(viewerPdfPage!=null){
                    try{viewerPdfPage.close();}catch(Exception ignored){}
                    viewerPdfPage=null;
                }
            }
            if(viewerBitmap!=null && !viewerBitmap.isRecycled()){
                try{viewerBitmap.recycle();}catch(Exception ignored){}
            }
            viewerBitmap=null;
            viewerImage.clearPdfBitmap();
            viewerImage.setVisibility(View.GONE);

            showViewerContinuousPages();

            if(viewerModeButton!=null) viewerModeButton.setText(L("PAGE","PAGE"));
            if(viewerPageLabel!=null){
                viewerPageLabel.setText(viewerPdfRenderer.getPageCount()+" "
                        +L("pages • Scroll • Pinch zoom • Double-tap",
                                "pages • Scroll • Pinch zoom • Double-tap"));
            }
        }else{
            if(viewerContinuousList!=null){
                viewerViewport.removeView(viewerContinuousList);
                viewerContinuousList=null;
            }
            if(viewerPageCache!=null){
                viewerPageCache.evictAll();
                viewerPageCache=null;
            }
            viewerImage.setVisibility(View.VISIBLE);
            if(viewerModeButton!=null) viewerModeButton.setText(L("SCROLL","SCROLL"));
            renderViewerPage();
        }
    }


    private class ContinuousPdfPageView extends ImageView {
        private final Matrix pageMatrix=new Matrix();
        private final android.view.ScaleGestureDetector pageScaleDetector;
        private final android.view.GestureDetector pageTapDetector;
        private float pageZoom=1f;
        private float pageBaseScale=1f;
        private float lastX=0f,lastY=0f;
        private float downX=0f,downY=0f;
        private boolean manualPinchActive=false;
        private float lastPinchDistance=0f;
        private float lastPinchFocusX=0f,lastPinchFocusY=0f;
        private boolean horizontalPageSwipeEnabled=false;
        private Runnable previousPageAction;
        private Runnable nextPageAction;
        private Bitmap currentPdfBitmap;

        ContinuousPdfPageView(Context context){
            super(context);
            setScaleType(ImageView.ScaleType.MATRIX);
            setBackgroundColor(Color.WHITE);
            setClickable(true);
            setLongClickable(false);

            pageScaleDetector=new android.view.ScaleGestureDetector(
                    context,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
                        @Override public boolean onScaleBegin(android.view.ScaleGestureDetector detector){
                            android.view.ViewParent p=getParent();
                            if(p!=null) p.requestDisallowInterceptTouchEvent(true);
                            return true;
                        }

                        @Override public boolean onScale(android.view.ScaleGestureDetector detector){
                            scaleAround(detector.getScaleFactor(),
                                    detector.getFocusX(),
                                    detector.getFocusY());
                            return true;
                        }

                        @Override public void onScaleEnd(android.view.ScaleGestureDetector detector){
                            constrainMatrix();
                            setImageMatrix(pageMatrix);
                            android.view.ViewParent p=getParent();
                            if(p!=null) p.requestDisallowInterceptTouchEvent(pageZoom>1.01f);
                        }
                    });
            pageScaleDetector.setQuickScaleEnabled(false);

            pageTapDetector=new android.view.GestureDetector(
                    context,new android.view.GestureDetector.SimpleOnGestureListener(){
                        @Override public boolean onDown(MotionEvent e){return true;}

                        @Override public boolean onDoubleTap(MotionEvent e){
                            android.view.ViewParent p=getParent();
                            if(p!=null) p.requestDisallowInterceptTouchEvent(true);
                            if(pageZoom<1.75f){
                                scaleAround(2f/pageZoom,e.getX(),e.getY());
                            }else{
                                resetTransform();
                            }
                            if(p!=null) p.requestDisallowInterceptTouchEvent(pageZoom>1.01f);
                            return true;
                        }
                    });

            setOnTouchListener((v,event)->{
                final int action=event.getActionMasked();

                // Keep the gesture inside this page whenever two fingers are present.
                if(event.getPointerCount()>=2
                        || action==MotionEvent.ACTION_POINTER_DOWN
                        || manualPinchActive){
                    lockAllParents(true);
                }

                // Double-tap still uses the same matrix zoom engine.
                if(event.getPointerCount()==1 && !manualPinchActive){
                    pageTapDetector.onTouchEvent(event);
                }

                switch(action){
                    case MotionEvent.ACTION_DOWN:
                        downX=event.getX();
                        downY=event.getY();
                        lastX=event.getX();
                        lastY=event.getY();
                        manualPinchActive=false;
                        lastPinchDistance=0f;
                        if(horizontalPageSwipeEnabled || pageZoom>1.01f){
                            lockAllParents(true);
                        }
                        return true;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        if(event.getPointerCount()>=2){
                            manualPinchActive=true;
                            lastPinchDistance=pinchDistance(event);
                            lastPinchFocusX=pinchFocusX(event);
                            lastPinchFocusY=pinchFocusY(event);
                            lockAllParents(true);
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if(event.getPointerCount()>=2){
                            float distance=pinchDistance(event);
                            float fx=pinchFocusX(event);
                            float fy=pinchFocusY(event);

                            if(!manualPinchActive){
                                manualPinchActive=true;
                                lastPinchDistance=distance;
                                lastPinchFocusX=fx;
                                lastPinchFocusY=fy;
                            }else if(lastPinchDistance>4f && distance>4f){
                                float factor=distance/lastPinchDistance;
                                if(factor>0.75f && factor<1.35f){
                                    scaleAround(factor,fx,fy);
                                }
                                lastPinchDistance=distance;
                                lastPinchFocusX=fx;
                                lastPinchFocusY=fy;
                            }
                            lockAllParents(true);
                            return true;
                        }

                        if(manualPinchActive){
                            lockAllParents(true);
                            return true;
                        }

                        if(pageZoom>1.01f && event.getPointerCount()==1){
                            float dx=event.getX()-lastX;
                            float dy=event.getY()-lastY;
                            pageMatrix.postTranslate(dx,dy);
                            constrainMatrix();
                            setImageMatrix(pageMatrix);
                            lastX=event.getX();
                            lastY=event.getY();
                            lockAllParents(true);
                            return true;
                        }

                        // Continuous mode needs parent scrolling at base zoom.
                        if(!horizontalPageSwipeEnabled && pageZoom<=1.01f){
                            lockAllParents(false);
                        }
                        return true;

                    case MotionEvent.ACTION_POINTER_UP:
                        if(manualPinchActive){
                            constrainMatrix();
                            setImageMatrix(pageMatrix);
                            // Stay locked until the final finger lifts so no jump occurs.
                            lockAllParents(true);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        boolean wasPinching=manualPinchActive;
                        manualPinchActive=false;
                        lastPinchDistance=0f;

                        if(!wasPinching
                                && pageZoom<=1.01f
                                && horizontalPageSwipeEnabled){
                            float dx=event.getX()-downX;
                            float dy=event.getY()-downY;
                            if(Math.abs(dx)>dp(80)
                                    && Math.abs(dx)>Math.abs(dy)*1.20f){
                                if(dx<0){
                                    if(nextPageAction!=null) nextPageAction.run();
                                }else{
                                    if(previousPageAction!=null) previousPageAction.run();
                                }
                            }
                        }

                        lockAllParents(pageZoom>1.01f);
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        manualPinchActive=false;
                        lastPinchDistance=0f;
                        lockAllParents(pageZoom>1.01f);
                        return true;
                }
                return true;
            });
        }

        private float pinchDistance(MotionEvent e){
            if(e==null || e.getPointerCount()<2) return 0f;
            float dx=e.getX(0)-e.getX(1);
            float dy=e.getY(0)-e.getY(1);
            return (float)Math.sqrt(dx*dx+dy*dy);
        }

        private float pinchFocusX(MotionEvent e){
            return e==null || e.getPointerCount()<2?0f:(e.getX(0)+e.getX(1))/2f;
        }

        private float pinchFocusY(MotionEvent e){
            return e==null || e.getPointerCount()<2?0f:(e.getY(0)+e.getY(1))/2f;
        }

        private void lockAllParents(boolean lock){
            android.view.ViewParent p=getParent();
            while(p!=null){
                p.requestDisallowInterceptTouchEvent(lock);
                p=p.getParent();
            }
        }

        void setHorizontalPageSwipe(
                boolean enabled,
                Runnable previousAction,
                Runnable nextAction){
            horizontalPageSwipeEnabled=enabled;
            previousPageAction=previousAction;
            nextPageAction=nextAction;
        }

        void setPdfBitmap(Bitmap bitmap){
            if(bitmap==null || bitmap.isRecycled()){
                currentPdfBitmap=null;
                setImageDrawable(null);
                pageZoom=1f;
                pageMatrix.reset();
                return;
            }
            if(currentPdfBitmap==bitmap && getDrawable()!=null) return;
            currentPdfBitmap=bitmap;
            setImageBitmap(bitmap);
            post(this::resetTransform);
        }

        void clearPdfBitmap(){
            currentPdfBitmap=null;
            setImageDrawable(null);
            pageZoom=1f;
            pageMatrix.reset();
            setImageMatrix(pageMatrix);
        }

        private void resetTransform(){
            if(currentPdfBitmap==null || currentPdfBitmap.isRecycled()) return;
            int vw=getWidth(),vh=getHeight();
            if(vw<=0 || vh<=0) return;

            pageBaseScale=Math.min(
                    vw/(float)currentPdfBitmap.getWidth(),
                    vh/(float)currentPdfBitmap.getHeight());
            if(pageBaseScale<=0f) pageBaseScale=1f;

            float dw=currentPdfBitmap.getWidth()*pageBaseScale;
            float dh=currentPdfBitmap.getHeight()*pageBaseScale;
            float dx=(vw-dw)/2f;
            float dy=(vh-dh)/2f;

            pageMatrix.reset();
            pageMatrix.setScale(pageBaseScale,pageBaseScale);
            pageMatrix.postTranslate(dx,dy);
            pageZoom=1f;
            setImageMatrix(pageMatrix);

            android.view.ViewParent p=getParent();
            if(p!=null) p.requestDisallowInterceptTouchEvent(false);
        }

        private void scaleAround(float factor,float focusX,float focusY){
            if(currentPdfBitmap==null || currentPdfBitmap.isRecycled()) return;

            float target=pageZoom*factor;
            if(target<1f) factor=1f/pageZoom;
            else if(target>5f) factor=5f/pageZoom;

            if(Math.abs(factor-1f)<0.0001f) return;

            pageMatrix.postScale(factor,factor,focusX,focusY);
            pageZoom=Math.max(1f,Math.min(5f,pageZoom*factor));
            constrainMatrix();
            setImageMatrix(pageMatrix);

            if(pageZoom<=1.01f) resetTransform();
        }

        private void constrainMatrix(){
            if(currentPdfBitmap==null || currentPdfBitmap.isRecycled()) return;
            int vw=getWidth(),vh=getHeight();
            if(vw<=0 || vh<=0) return;

            RectF rect=new RectF(
                    0,0,currentPdfBitmap.getWidth(),currentPdfBitmap.getHeight());
            pageMatrix.mapRect(rect);

            float dx=0f,dy=0f;
            if(rect.width()<=vw){
                dx=vw/2f-rect.centerX();
            }else{
                if(rect.left>0) dx=-rect.left;
                else if(rect.right<vw) dx=vw-rect.right;
            }

            if(rect.height()<=vh){
                dy=vh/2f-rect.centerY();
            }else{
                if(rect.top>0) dy=-rect.top;
                else if(rect.bottom<vh) dy=vh-rect.bottom;
            }

            if(dx!=0f || dy!=0f) pageMatrix.postTranslate(dx,dy);
        }
    }

    private void showViewerContinuousPages(){
        if(viewerViewport==null || viewerPdfRenderer==null) return;

        if(viewerContinuousList!=null){
            viewerViewport.removeView(viewerContinuousList);
        }

        final int generation=viewerRenderGeneration;
        final int pageCount=viewerPdfRenderer.getPageCount();
        final int screenWidth=getResources().getDisplayMetrics().widthPixels;
        final int targetWidth=Math.max(dp(240),screenWidth-dp(16));

        viewerPageCache=new android.util.LruCache<Integer,Bitmap>(24*1024){
            @Override protected int sizeOf(Integer key,Bitmap value){
                if(value==null) return 0;
                return Math.max(1,value.getByteCount()/1024);
            }
        };
        viewerRenderExecutor=java.util.concurrent.Executors.newSingleThreadExecutor();

        viewerContinuousList=new ListView(this);
        viewerContinuousList.setBackgroundColor(BG);
        viewerContinuousList.setDividerHeight(dp(8));
        viewerContinuousList.setDivider(new android.graphics.drawable.ColorDrawable(BG));
        viewerContinuousList.setPadding(dp(4),dp(4),dp(4),dp(8));
        viewerContinuousList.setClipToPadding(false);
        viewerContinuousList.setFastScrollEnabled(pageCount>12);
        viewerContinuousList.setVerticalScrollBarEnabled(true);

        final BaseAdapter adapter=new BaseAdapter(){
            @Override public int getCount(){return pageCount;}
            @Override public Object getItem(int position){return position;}
            @Override public long getItemId(int position){return position;}

            @Override public View getView(int position,View convertView,ViewGroup parent){
                LinearLayout card;
                ContinuousPdfPageView image;
                TextView label;

                if(convertView instanceof LinearLayout){
                    card=(LinearLayout)convertView;
                    label=(TextView)card.getChildAt(0);
                    image=(ContinuousPdfPageView)card.getChildAt(1);
                }else{
                    card=new LinearLayout(MainActivity.this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(0,0,0,dp(4));
                    card.setBackgroundColor(BG);

                    label=tv("",12,SOFT);
                    label.setGravity(Gravity.CENTER);
                    label.setPadding(dp(8),dp(3),dp(8),dp(3));
                    card.addView(label,new LinearLayout.LayoutParams(-1,dp(28)));

                    image=new ContinuousPdfPageView(MainActivity.this);
                    image.setHorizontalPageSwipe(false,null,null);
                    image.setAdjustViewBounds(false);
                    card.addView(image,new LinearLayout.LayoutParams(-1,dp(520)));
                }

                label.setText(L("Page ","पेज ")+(position+1)+" / "+pageCount);
                image.setTag(position);

                Bitmap cached=viewerPageCache==null?null:viewerPageCache.get(position);
                if(cached!=null && !cached.isRecycled()){
                    int h=Math.max(dp(180),(int)Math.round(
                            targetWidth*(cached.getHeight()/(double)cached.getWidth())));
                    image.setLayoutParams(new LinearLayout.LayoutParams(-1,h));
                    image.setPdfBitmap(cached);
                }else{
                    image.clearPdfBitmap();
                    image.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(520)));
                    queueViewerContinuousPage(position,targetWidth,generation,this);
                }
                return card;
            }
        };

        viewerContinuousList.setAdapter(adapter);
        viewerViewport.addView(viewerContinuousList,new FrameLayout.LayoutParams(-1,-1));
    }

    private void queueViewerContinuousPage(
            final int position,
            final int targetWidth,
            final int generation,
            final BaseAdapter adapter){

        if(viewerRenderExecutor==null
                || viewerPageCache==null
                || viewerPageCache.get(position)!=null
                || !viewerPagesLoading.add(position)) return;

        viewerRenderExecutor.submit(()->{
            Bitmap rendered=null;
            try{
                synchronized(viewerRendererLock){
                    if(!viewerContinuousMode
                            || generation!=viewerRenderGeneration
                            || viewerPdfRenderer==null) return;

                    android.graphics.pdf.PdfRenderer.Page page=viewerPdfRenderer.openPage(position);
                    try{
                        int w=Math.min(1000,Math.max(dp(320),targetWidth));
                        int h=Math.max(1,(int)Math.round(
                                w*(page.getHeight()/(double)page.getWidth())));
                        rendered=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                        Canvas c=new Canvas(rendered);
                        c.drawColor(Color.WHITE);
                        page.render(rendered,null,null,
                                android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    }finally{
                        try{page.close();}catch(Exception ignored){}
                    }
                }

                final Bitmap ready=rendered;
                rendered=null;
                if(ready!=null){
                    runOnUiThread(()->{
                        if(viewerContinuousMode
                                && generation==viewerRenderGeneration
                                && viewerPageCache!=null){
                            viewerPageCache.put(position,ready);
                            if(adapter!=null) adapter.notifyDataSetChanged();
                        }else if(!ready.isRecycled()){
                            ready.recycle();
                        }
                    });
                }
            }catch(Exception ignored){
            }finally{
                viewerPagesLoading.remove(position);
                if(rendered!=null && !rendered.isRecycled()){
                    try{rendered.recycle();}catch(Exception ignored){}
                }
            }
        });
    }

    private void renderViewerPage(){
        if(viewerContinuousMode || viewerPdfRenderer==null || viewerImage==null) return;

        Bitmap bm;
        synchronized(viewerRendererLock){
            if(viewerPdfPage!=null){try{viewerPdfPage.close();}catch(Exception ignored){} viewerPdfPage=null;}
            viewerPdfPage=viewerPdfRenderer.openPage(viewerPageIndex);

            int screen=getResources().getDisplayMetrics().widthPixels;
            int w=Math.min(1600,Math.max(screen*2,900));
            int h=Math.max(1,(int)(w*(viewerPdfPage.getHeight()/(double)viewerPdfPage.getWidth())));
            bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(bm);c.drawColor(Color.WHITE);
            viewerPdfPage.render(bm,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        }

        Bitmap oldViewerBitmap=viewerBitmap;
        viewerBitmap=bm;
        viewerImage.setPdfBitmap(viewerBitmap);
        if(oldViewerBitmap!=null
                && oldViewerBitmap!=viewerBitmap
                && !oldViewerBitmap.isRecycled()){
            try{oldViewerBitmap.recycle();}catch(Exception ignored){}
        }
        if(viewerPageLabel!=null){
            viewerPageLabel.setText(L("Page ","पेज ")+(viewerPageIndex+1)+" / "+viewerPdfRenderer.getPageCount()
                    +"  •  "+L("Swipe • Pinch zoom • Double-tap","Swipe • Pinch zoom • Double-tap"));
        }
    }

    private void showPdfViewerMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(L("SHARE","शेयर"));
        menu.getMenu().add(L("PRINT","प्रिंट"));
        menu.getMenu().add(L("DOWNLOAD","डाउनलोड"));
        menu.setOnMenuItemClickListener(item->{
            String t=item.getTitle().toString();
            if(t.equals(L("SHARE","शेयर"))){shareViewerPdf();return true;}
            if(t.equals(L("PRINT","प्रिंट"))){printViewerPdf();return true;}
            if(t.equals(L("DOWNLOAD","डाउनलोड"))){showViewerDownloadOptions();return true;}
            return false;
        });
        menu.show();
    }

    private void shareViewerPdf(){
        if(viewerPdfUri==null) return;
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("application/pdf");
        send.putExtra(Intent.EXTRA_STREAM,viewerPdfUri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(Intent.createChooser(send,L("Share PDF","PDF शेयर करें")));}catch(Exception ignored){}
    }

    private void printViewerPdf(){
        if(viewerPdfUri==null) return;
        android.print.PrintManager pm=(android.print.PrintManager)getSystemService(PRINT_SERVICE);
        if(pm==null) return;
        final Uri source=viewerPdfUri;
        final String name=getDisplayName(source);
        android.print.PrintDocumentAdapter adapter=new android.print.PrintDocumentAdapter(){
            @Override public void onLayout(android.print.PrintAttributes oldAttributes,
                                           android.print.PrintAttributes newAttributes,
                                           android.os.CancellationSignal cancellationSignal,
                                           LayoutResultCallback callback,
                                           Bundle extras){
                if(cancellationSignal.isCanceled()){callback.onLayoutCancelled();return;}
                android.print.PrintDocumentInfo info=new android.print.PrintDocumentInfo.Builder(name)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build();
                callback.onLayoutFinished(info,true);
            }

            @Override public void onWrite(android.print.PageRange[] pages,
                                          android.os.ParcelFileDescriptor destination,
                                          android.os.CancellationSignal cancellationSignal,
                                          WriteResultCallback callback){
                try{
                    java.io.InputStream in=getContentResolver().openInputStream(source);
                    java.io.OutputStream out=new java.io.FileOutputStream(destination.getFileDescriptor());
                    byte[] buf=new byte[32768];int n;
                    while((n=in.read(buf))>0){
                        if(cancellationSignal.isCanceled()){callback.onWriteCancelled();in.close();out.close();return;}
                        out.write(buf,0,n);
                    }
                    in.close();out.flush();out.close();
                    callback.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});
                }catch(Exception e){
                    callback.onWriteFailed(e.getMessage());
                }
            }
        };
        pm.print("STS DigiKit - "+name,adapter,null);
    }

    private void showViewerDownloadOptions(){
        new AlertDialog.Builder(this)
                .setTitle(L("DOWNLOAD AS","इस format में डाउनलोड"))
                .setItems(new String[]{"JPG","JPEG","PNG","PDF"},(d,which)->{
                    pendingViewerExportFormat=new String[]{"JPG","JPEG","PNG","PDF"}[which];
                    if("PDF".equals(pendingViewerExportFormat)){
                        try{
                            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            save.addCategory(Intent.CATEGORY_OPENABLE);
                            save.setType("application/pdf");
                            String base=getDisplayName(viewerPdfUri);
                            if(!base.toLowerCase(java.util.Locale.US).endsWith(".pdf")) base+=".pdf";
                            save.putExtra(Intent.EXTRA_TITLE,base);
                            startActivityForResult(save,REQ_SAVE_VIEWER_PDF);
                        }catch(Exception ignored){}
                    }else{
                        try{
                            Intent folder=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                            startActivityForResult(folder,REQ_SAVE_VIEWER_IMAGES_DIR);
                        }catch(Exception ignored){}
                    }
                })
                .show();
    }

    private void copyUri(Uri source,Uri destination) throws Exception{
        java.io.InputStream in=getContentResolver().openInputStream(source);
        java.io.OutputStream out=getContentResolver().openOutputStream(destination);
        if(in==null || out==null) throw new java.io.IOException("File unavailable");
        byte[] buf=new byte[32768];int n;
        while((n=in.read(buf))>0) out.write(buf,0,n);
        out.flush();in.close();out.close();
    }

    private void exportViewerPagesToTree(Uri treeUri,String format){
        final Uri source=viewerPdfUri;
        new Thread(()->{
            android.os.ParcelFileDescriptor pfd=null;
            android.graphics.pdf.PdfRenderer renderer=null;
            int saved=0;
            try{
                Uri parent=treeRootDocument(treeUri);
                pfd=getContentResolver().openFileDescriptor(source,"r");
                if(pfd==null) throw new java.io.IOException("PDF unavailable");
                renderer=new android.graphics.pdf.PdfRenderer(pfd);
                for(int i=0;i<renderer.getPageCount();i++){
                    android.graphics.pdf.PdfRenderer.Page page=renderer.openPage(i);
                    try{
                        int w=Math.min(1800,Math.max(900,page.getWidth()*2));
                        int h=Math.max(1,(int)(w*(page.getHeight()/(double)page.getWidth())));
                        Bitmap bm=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                        Canvas c=new Canvas(bm);c.drawColor(Color.WHITE);
                        page.render(bm,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                        Uri file=android.provider.DocumentsContract.createDocument(
                                getContentResolver(),parent,exportMime(format),
                                String.format(java.util.Locale.US,"STS-PDF-page-%03d%s",i+1,exportExtension(format)));
                        if(file!=null){
                            java.io.OutputStream out=getContentResolver().openOutputStream(file);
                            if(out!=null){writeBitmapExport(bm,out,format,0);out.close();saved++;}
                        }
                        bm.recycle();
                    }finally{page.close();}
                }
                final int c=saved;
                runOnUiThread(()->Toast.makeText(this,L("Saved ","सेव हुए ")+c+" "+L("pages","pages"),Toast.LENGTH_LONG).show());
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,L("Export failed","Export नहीं हो सका"),Toast.LENGTH_LONG).show());
            }finally{
                if(renderer!=null) try{renderer.close();}catch(Exception ignored){}
                if(pfd!=null) try{pfd.close();}catch(Exception ignored){}
            }
        }).start();
    }

    private void showGst(){
        showEmiInterest();
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

                    boolean androidRemote=all.contains("android tv") || all.contains("google tv")
                            || portOpen(ip,6466,450) || portOpen(ip,6467,450);
                    boolean tvLike=all.contains("roku") || all.contains("samsung") || all.contains("webos")
                            || all.contains("lg electronics") || all.contains("mediarenderer")
                            || all.contains("smarttv") || all.contains("television") || all.contains("dial")
                            || androidRemote;
                    if(!tvLike) continue;

                    String type="UPNP";
                    if(all.contains("roku")) type="ROKU";
                    else if(all.contains("samsung")) type="SAMSUNG";
                    else if(all.contains("webos") || all.contains("lg electronics")) type="LG";
                    else if(androidRemote) type="ANDROID_TV";

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
        if("IR".equals(d.type)) return hasIrEmitter();
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
        if("PLAY".equals(key) || "PAUSE".equals(key)) return "Play";
        if("REW".equals(key)) return "Rev";
        if("FF".equals(key)) return "Fwd";
        if("INFO".equals(key) || "MENU".equals(key)) return "Info";
        if("GUIDE".equals(key)) return "Guide";
        if("INPUT".equals(key)) return "InputTuner";
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
        if("PAUSE".equals(key)) return "KEY_PAUSE";
        if("STOP".equals(key)) return "KEY_STOP";
        if("REW".equals(key)) return "KEY_REWIND";
        if("FF".equals(key)) return "KEY_FF";
        if("INPUT".equals(key)) return "KEY_SOURCE";
        if("MENU".equals(key)) return "KEY_MENU";
        if("INFO".equals(key)) return "KEY_INFO";
        if("GUIDE".equals(key)) return "KEY_GUIDE";
        if("EXIT".equals(key)) return "KEY_EXIT";
        if(key.matches("[0-9]")) return "KEY_"+key;
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
        if("BACK".equals(key) || "EXIT".equals(key)) return 4;
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
        if("PLAY".equals(key) || "PAUSE".equals(key)) return 85;
        if("STOP".equals(key)) return 86;
        if("REW".equals(key)) return 89;
        if("FF".equals(key)) return 90;
        if("MENU".equals(key)) return 82;
        if("INFO".equals(key)) return 165;
        if("GUIDE".equals(key)) return 172;
        if("INPUT".equals(key)) return 178;
        if(key.matches("[0-9]")) return 7+Integer.parseInt(key);
        return 0;
    }

    private boolean hasIrEmitter(){
        try{
            android.hardware.ConsumerIrManager ir=(android.hardware.ConsumerIrManager)getSystemService(Context.CONSUMER_IR_SERVICE);
            return ir!=null && ir.hasIrEmitter();
        }catch(Exception e){
            return false;
        }
    }

    private boolean transmitNec(long code){
        try{
            android.hardware.ConsumerIrManager ir=(android.hardware.ConsumerIrManager)getSystemService(Context.CONSUMER_IR_SERVICE);
            if(ir==null || !ir.hasIrEmitter()) return false;

            java.util.ArrayList<Integer> p=new java.util.ArrayList<>();
            p.add(9000); p.add(4500);
            for(int byteIndex=3;byteIndex>=0;byteIndex--){
                int b=(int)((code>>(byteIndex*8))&0xff);
                for(int bit=0;bit<8;bit++){
                    p.add(560);
                    p.add(((b>>bit)&1)==1?1690:560);
                }
            }
            p.add(560);
            int[] pattern=new int[p.size()];
            for(int i=0;i<p.size();i++) pattern[i]=p.get(i);
            ir.transmit(38000,pattern);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    private boolean transmitSony12(int code){
        try{
            android.hardware.ConsumerIrManager ir=(android.hardware.ConsumerIrManager)getSystemService(Context.CONSUMER_IR_SERVICE);
            if(ir==null || !ir.hasIrEmitter()) return false;

            int[] pattern=new int[2+(12*2)];
            int n=0;
            pattern[n++]=2400; pattern[n++]=600;
            for(int bit=0;bit<12;bit++){
                pattern[n++]=((code>>bit)&1)==1?1200:600;
                pattern[n++]=600;
            }
            ir.transmit(40000,pattern);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    private boolean transmitPanasonic48(long command24,boolean lsbWithinBytes){
        try{
            android.hardware.ConsumerIrManager ir=(android.hardware.ConsumerIrManager)
                    getSystemService(Context.CONSUMER_IR_SERVICE);
            if(ir==null || !ir.hasIrEmitter()) return false;

            long frame=(0x400401L<<24)|(command24&0xFFFFFFL);
            java.util.ArrayList<Integer> p=new java.util.ArrayList<>();
            p.add(3553); p.add(1726);

            if(lsbWithinBytes){
                for(int byteIndex=5;byteIndex>=0;byteIndex--){
                    int b=(int)((frame>>(byteIndex*8))&0xff);
                    for(int bit=0;bit<8;bit++){
                        p.add(472);
                        p.add(((b>>bit)&1)==1?1275:402);
                    }
                }
            }else{
                for(int bit=47;bit>=0;bit--){
                    p.add(472);
                    p.add(((frame>>bit)&1L)==1L?1275:402);
                }
            }

            p.add(485);
            int[] pattern=new int[p.size()];
            for(int i=0;i<p.size();i++) pattern[i]=p.get(i);
            ir.transmit(37000,pattern);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    private boolean sendPanasonicIrKey(String profile,String key){
        java.util.HashMap<String,Long> m=new java.util.HashMap<>();
        m.put("POWER",0x00BCBDL);
        m.put("INPUT",0x00A0A1L);
        m.put("VOL_UP",0x000405L);
        m.put("VOL_DOWN",0x008485L);
        m.put("MUTE",0x004C4DL);
        m.put("CH_UP",0x002C2DL);
        m.put("CH_DOWN",0x00ACADL);
        m.put("MENU",0x004A4BL);
        m.put("HOME",0x90A938L);
        m.put("INFO",0x009C9DL);
        m.put("GUIDE",0x90E170L);
        m.put("EXIT",0x00CBCAL);
        m.put("BACK",0x002B2AL);
        m.put("UP",0x005253L);
        m.put("DOWN",0x00D2D3L);
        m.put("LEFT",0x007273L);
        m.put("RIGHT",0x00F2F3L);
        m.put("OK",0x009293L);
        m.put("1",0x000809L);
        m.put("2",0x008889L);
        m.put("3",0x004849L);
        m.put("4",0x00C8C9L);
        m.put("5",0x002829L);
        m.put("6",0x00A8A9L);
        m.put("7",0x006869L);
        m.put("8",0x00E8E9L);
        m.put("9",0x001819L);
        m.put("0",0x009899L);
        m.put("PLAY",0x900392L);
        m.put("PAUSE",0x908312L);
        m.put("STOP",0x9043D2L);
        m.put("REW",0x9023B2L);
        m.put("FF",0x90C352L);

        Long code=m.get(key);
        if(code==null) return false;
        return transmitPanasonic48(code,"PANASONIC_LSB".equals(profile));
    }

    private String[] universalIrProfiles(){
        // Internal profiles only. User never has to choose a brand/name.
        // Two Panasonic bit-order variants are kept because different Android IR
        // implementations/remotes expose this family differently.
        return new String[]{
                "SAMSUNG",
                "LG",
                "SONY",
                "PANASONIC_LSB",
                "PANASONIC_MSB"
        };
    }

    private String savedUniversalIrProfile(){
        return getSharedPreferences("sts",0).getString("universal_ir_profile","");
    }

    private void saveUniversalIrProfile(String profile){
        getSharedPreferences("sts",0).edit()
                .putString("universal_ir_profile",profile==null?"":profile)
                .apply();
    }

    private void activateUniversalIr(
            String profile,
            TextView status,
            java.util.function.Consumer<TvDevice> connected){

        if(profile==null || profile.trim().isEmpty()) return;
        TvDevice irTv=new TvDevice("Universal IR",profile,"IR");
        connected.accept(irTv);
        status.setText(L(
                "Universal IR ready — use remote buttons directly",
                "Universal IR तैयार है — remote buttons सीधे चलाएं"));
    }

    private void showUniversalIrAutoTest(
            TextView status,
            java.util.function.Consumer<TvDevice> connected){

        if(!hasIrEmitter()){
            new AlertDialog.Builder(this)
                    .setTitle(L("Universal IR Remote","Universal IR Remote"))
                    .setMessage(L(
                            "This phone does not have an IR blaster.",
                            "इस फोन में IR blaster नहीं है।"))
                    .setPositiveButton("OK",null)
                    .show();
            return;
        }

        final String[] profiles=universalIrProfiles();
        final int[] index={0};

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14),dp(12),dp(14),dp(8));
        box.setBackgroundColor(BG);

        TextView info=tv("",16,WHITE);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(8),dp(8),dp(8),dp(12));
        box.addView(info,new LinearLayout.LayoutParams(-1,dp(88)));

        Button test=btn(L("TEST POWER","POWER TEST"));
        Button next=btn(L("NEXT CODE","अगला CODE"));
        Button working=btn(L("WORKING / SAVE","काम कर रहा है / SAVE"));

        box.addView(test,controlParams(56));
        box.addView(next,controlParams(56));
        box.addView(working,controlParams(56));

        Runnable refresh=()->info.setText(
                L("Universal IR code ","Universal IR code ")
                        +(index[0]+1)+" / "+profiles.length+"\n"
                        +L("Point phone at TV, tap TEST POWER. If TV responds, tap WORKING / SAVE.",
                        "फोन को TV की ओर रखें, TEST POWER दबाएं। TV respond करे तो WORKING / SAVE दबाएं।"));
        refresh.run();

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(L("UNIVERSAL IR AUTO TEST","UNIVERSAL IR AUTO TEST"))
                .setView(box)
                .setNegativeButton(L("CLOSE","बंद करें"),null)
                .create();

        test.setOnClickListener(v->{
            boolean sent=sendIrKey(profiles[index[0]],"POWER");
            if(sent){
                status.setText(L(
                        "IR power code sent — check the TV",
                        "IR power code भेजा गया — TV देखें"));
            }else{
                status.setText(L(
                        "IR code could not be sent",
                        "IR code नहीं भेजा जा सका"));
            }
        });

        next.setOnClickListener(v->{
            index[0]=(index[0]+1)%profiles.length;
            refresh.run();
            boolean sent=sendIrKey(profiles[index[0]],"POWER");
            if(sent){
                status.setText(L(
                        "Next IR power code sent — check the TV",
                        "अगला IR power code भेजा गया — TV देखें"));
            }
        });

        working.setOnClickListener(v->{
            String profile=profiles[index[0]];
            saveUniversalIrProfile(profile);
            activateUniversalIr(profile,status,connected);
            Toast.makeText(this,
                    L("Universal IR profile saved","Universal IR profile सेव हो गया"),
                    Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private boolean sendIrKey(String profile,String key){
        if(profile==null) return false;

        if("SAMSUNG".equals(profile)){
            java.util.HashMap<String,Long> m=new java.util.HashMap<>();
            m.put("POWER",0xE0E040BFL); m.put("INPUT",0xE0E0807FL);
            m.put("VOL_UP",0xE0E0E01FL); m.put("VOL_DOWN",0xE0E0D02FL); m.put("MUTE",0xE0E0F00FL);
            m.put("CH_UP",0xE0E048B7L); m.put("CH_DOWN",0xE0E008F7L);
            m.put("MENU",0xE0E058A7L); m.put("INFO",0xE0E0F807L);
            m.put("UP",0xE0E006F9L); m.put("DOWN",0xE0E08679L);
            m.put("LEFT",0xE0E0A659L); m.put("RIGHT",0xE0E046B9L); m.put("OK",0xE0E016E9L);
            m.put("BACK",0xE0E01AE5L); m.put("EXIT",0xE0E0B44BL);
            m.put("1",0xE0E020DFL); m.put("2",0xE0E0A05FL); m.put("3",0xE0E0609FL);
            m.put("4",0xE0E010EFL); m.put("5",0xE0E0906FL); m.put("6",0xE0E050AFL);
            m.put("7",0xE0E030CFL); m.put("8",0xE0E0B04FL); m.put("9",0xE0E0708FL); m.put("0",0xE0E08877L);
            Long code=m.get(key);
            return code!=null && transmitNec(code);
        }

        if("LG".equals(profile)){
            java.util.HashMap<String,Long> m=new java.util.HashMap<>();
            m.put("POWER",0x20DF10EFL); m.put("INPUT",0x20DFD02FL);
            m.put("VOL_UP",0x20DF40BFL); m.put("VOL_DOWN",0x20DFC03FL); m.put("MUTE",0x20DF906FL);
            m.put("CH_UP",0x20DF00FFL); m.put("CH_DOWN",0x20DF807FL);
            m.put("MENU",0x20DFC23DL); m.put("HOME",0x20DFC23DL);
            m.put("UP",0x20DF02FDL); m.put("DOWN",0x20DF827DL);
            m.put("LEFT",0x20DFE01FL); m.put("RIGHT",0x20DF609FL); m.put("OK",0x20DF22DDL);
            m.put("BACK",0x20DF14EBL);
            m.put("1",0x20DF8877L); m.put("2",0x20DF48B7L); m.put("3",0x20DFC837L);
            m.put("4",0x20DF28D7L); m.put("5",0x20DFA857L); m.put("6",0x20DF6897L);
            m.put("7",0x20DFE817L); m.put("8",0x20DF18E7L); m.put("9",0x20DF9867L); m.put("0",0x20DF08F7L);
            Long code=m.get(key);
            return code!=null && transmitNec(code);
        }

        if("SONY".equals(profile)){
            java.util.HashMap<String,Integer> m=new java.util.HashMap<>();
            m.put("POWER",0xA90); m.put("VOL_UP",0x490); m.put("VOL_DOWN",0xC90);
            m.put("MUTE",0x290); m.put("CH_UP",0x090); m.put("CH_DOWN",0x890);
            Integer code=m.get(key);
            return code!=null && transmitSony12(code);
        }

        if("PANASONIC_LSB".equals(profile) || "PANASONIC_MSB".equals(profile)){
            return sendPanasonicIrKey(profile,key);
        }

        return false;
    }

    private boolean sendTvKey(TvDevice d,String key){
        if(d==null) return false;
        if("ROKU".equals(d.type)) return sendRokuKey(d.ip,key);
        if("SAMSUNG".equals(d.type)) return sendSamsungKey(d.ip,key);
        if("IR".equals(d.type)) return sendIrKey(d.ip,key);
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
                .putLong("tv_last_connected",System.currentTimeMillis())
                .apply();
    }

    private void reconnectSavedTv(
            TvDevice saved,
            TextView status,
            java.util.function.Consumer<TvDevice> connected){

        if(saved==null) return;

        if(!"ANDROID_TV".equals(saved.type)){
            connectUniversalTv(saved,status,connected);
            return;
        }

        if(androidTvV2==null) androidTvV2=new AndroidTvV2(this);
        status.setText(L(
                "Reconnecting saved Android TV...",
                "सेव Android TV दोबारा कनेक्ट कर रहे हैं..."));

        new Thread(()->{
            try{
                if(androidTvV2.connect(saved.ip)){
                    runOnUiThread(()->{
                        saveConnectedTv(saved);
                        status.setText(L("Connected: ","कनेक्टेड: ")+saved.name);
                        connected.accept(saved);
                    });
                    return;
                }
            }catch(Exception ignored){}

            runOnUiThread(()->status.setText(L(
                    "Saved TV did not answer on its old IP. Searching this Wi-Fi...",
                    "सेव TV पुराने IP पर नहीं मिला। इस Wi-Fi पर खोज रहे हैं...")));

            java.util.ArrayList<TvDevice> found=discoverTvs();
            java.util.ArrayList<TvDevice> androidCandidates=new java.util.ArrayList<>();

            // Prefer the same saved TV name first, then other Android/Google TVs.
            for(TvDevice d:found){
                if(!"ANDROID_TV".equals(d.type)) continue;
                if(saved.name!=null && d.name!=null
                        && saved.name.trim().equalsIgnoreCase(d.name.trim())){
                    androidCandidates.add(0,d);
                }else{
                    androidCandidates.add(d);
                }
            }

            for(TvDevice candidate:androidCandidates){
                try{
                    if(androidTvV2.connect(candidate.ip)){
                        final TvDevice restored=new TvDevice(
                                candidate.name==null || candidate.name.trim().isEmpty()
                                        ?saved.name:candidate.name,
                                candidate.ip,
                                "ANDROID_TV");
                        runOnUiThread(()->{
                            saveConnectedTv(restored);
                            status.setText(L(
                                    "Reconnected saved TV: ",
                                    "सेव TV फिर कनेक्ट हो गया: ")+restored.name);
                            connected.accept(restored);
                        });
                        return;
                    }
                }catch(Exception ignored){}
            }

            runOnUiThread(()->{
                status.setText(L(
                        "Saved TV is still remembered, but it is not reachable now. Keep TV and phone on the same Wi-Fi, then use ⋮ > AUTO FIND TV. Re-pair only if the TV asks for a code.",
                        "सेव TV अभी भी याद है, लेकिन अभी reachable नहीं है। TV और phone को एक ही Wi-Fi पर रखें, फिर ⋮ > AUTO FIND TV करें। TV code तभी दोबारा डालें जब TV खुद मांगे।"));
            });
        }).start();
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

    private String detectTvType(String ip){
        if(ip==null || ip.trim().isEmpty()) return "UPNP";
        String clean=ip.trim();

        String roku=fetchLanText("http://"+clean+":8060/query/device-info",1000);
        if(!roku.isEmpty()) return "ROKU";
        if(portOpen(clean,6466,700) || portOpen(clean,6467,700)) return "ANDROID_TV";
        if(portOpen(clean,8001,700) || portOpen(clean,8002,700)) return "SAMSUNG";
        if(portOpen(clean,3000,700) || portOpen(clean,3001,700)) return "LG";
        return "UPNP";
    }

    private void remoteShell(String name){
        toolPickerOpen=false;

        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(screenBg());

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int accent=toolAccent();
        header.setBackground(grad(
                mixColor(PANEL,accent,0.08f),
                mixColor(PANEL2,accent,0.12f),0));
        header.setElevation(dp(4));

        TextView left=tv("",1,WHITE);
        header.addView(left,new LinearLayout.LayoutParams(dp(56),dp(64)));

        TextView label=tv(name,21,WHITE);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(null,1);
        label.setShadowLayer(4f,0f,2f,Color.argb(120,0,0,0));
        header.addView(label,new LinearLayout.LayoutParams(0,dp(64),1));

        TextView right=tv("",1,WHITE);
        header.addView(right,new LinearLayout.LayoutParams(dp(56),dp(64)));

        outer.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));

        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(dp(8),dp(8),dp(8),dp(8));
        root.setBackground(screenBg());

        outer.addView(root,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(outer);

        label.setOnClickListener(v->{haptic();showToolPicker(name);});
        right.setOnClickListener(v->{haptic();showToolPicker(name);});
    }

    private void connectUniversalTv(TvDevice d,TextView status,java.util.function.Consumer<TvDevice> connected){
        if(d==null) return;

        if("IR".equals(d.type)){
            if(!hasIrEmitter()){
                status.setText(L("This phone does not have an IR blaster.","इस फोन में IR blaster नहीं है।"));
                return;
            }
            // IR is direct hardware control: no pairing/connect and do not touch
            // the separately saved Wi-Fi TV.
            connected.accept(d);
            status.setText(L(
                    "Universal IR ready — direct control",
                    "Universal IR तैयार है — direct control"));
            return;
        }

        if("ANDROID_TV".equals(d.type)){
            connectAndroidTv(d,status,()->connected.accept(d));
            return;
        }

        status.setText(L("Connecting: ","कनेक्ट कर रहे हैं: ")+d.name);
        new Thread(()->{
            boolean ok=checkTvDevice(d);
            runOnUiThread(()->{
                if(ok && ("ROKU".equals(d.type) || "SAMSUNG".equals(d.type))){
                    saveConnectedTv(d);
                    connected.accept(d);
                    status.setText(L("Connected: ","कनेक्टेड: ")+d.name);
                }else if(ok && "LG".equals(d.type)){
                    status.setText(L(
                            "LG webOS TV detected. Use IR mode on phones with IR, or select another supported network protocol.",
                            "LG webOS TV मिला। IR वाले फोन में IR mode इस्तेमाल करें या दूसरा supported network protocol चुनें।"));
                }else{
                    status.setText(L(
                            "TV is not reachable with the selected protocol. Check Wi-Fi/IP and TV remote permissions.",
                            "चुने गए protocol से TV नहीं जुड़ा। Wi-Fi/IP और TV remote permission जांचें।"));
                }
            });
        }).start();
    }

    private void showRemote(){
        currentTool="REMOTE";
        if(androidTvV2==null) androidTvV2=new AndroidTvV2(this);

        remoteShell(L("UNIVERSAL TV REMOTE","यूनिवर्सल TV रिमोट"));

        LinearLayout statusBar=new LinearLayout(this);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setBackground(grad(PANEL2,mixColor(PANEL2,ACCENT,0.10f),12));

        TextView status=tv(L("Open ⋮ to connect TV","TV कनेक्ट करने के लिए ⋮ खोलें"),16,SOFT);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(10),dp(8),dp(6),dp(8));
        statusBar.addView(status,new LinearLayout.LayoutParams(0,-1,1));

        TextView settings=tv("⋮",34,WHITE);
        settings.setGravity(Gravity.CENTER);
        settings.setBackground(touchBg(PANEL,10));
        LinearLayout.LayoutParams settingsParams=new LinearLayout.LayoutParams(dp(58),-1);
        settingsParams.setMargins(dp(2),dp(4),dp(4),dp(4));
        statusBar.addView(settings,settingsParams);

        root.addView(statusBar,controlParams(64));

        LinearLayout remoteBox=new LinearLayout(this);
        remoteBox.setOrientation(LinearLayout.VERTICAL);
        remoteBox.setPadding(dp(3),dp(3),dp(3),dp(3));
        remoteBox.setBackground(grad(SURFACE,mixColor(PANEL,ACCENT,0.06f),14));
        root.addView(remoteBox,new LinearLayout.LayoutParams(-1,0,1));

        final TvDevice[] active={null};

        java.util.function.Consumer<String[]> addRow=(String[] specs)->{
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for(String spec:specs){
                String[] parts=spec.split("\\|",2);
                String label=parts[0];
                String key=parts.length>1?parts[1]:"";
                Button b=btn(label);
                b.setTextSize(16);
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);
                bp.setMargins(dp(2),dp(2),dp(2),dp(2));
                row.addView(b,bp);

                if("POWER".equals(key)) b.setBackground(touchBg(RED,12));
                else if("OK".equals(key)) b.setBackground(touchBg(GREEN,12));
                else if("HOME".equals(key)) b.setBackground(touchBg(PURPLE,12));
                else if("INPUT".equals(key)) b.setBackground(touchBg(ORANGE,12));

                b.setOnClickListener(v->{
                    TvDevice d=active[0];
                    if(d==null){
                        Toast.makeText(this,L("Open ⋮ and connect a TV first","पहले ⋮ खोलकर TV कनेक्ट करें"),Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new Thread(()->{
                        boolean ok=sendTvKey(d,key);
                        runOnUiThread(()->{
                            if(ok){
                                if("IR".equals(d.type)){
                                    status.setText(L(
                                            "Universal IR command sent",
                                            "Universal IR command भेजा गया"));
                                }else{
                                    status.setText(L("Connected: ","कनेक्टेड: ")+d.name);
                                }
                            }else status.setText(L(
                                    "This command is not supported by the current TV / connection.",
                                    "यह command वर्तमान TV / connection पर supported नहीं है।"));
                        });
                    }).start();
                });
            }
            remoteBox.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        };

        addRow.accept(new String[]{"⏻|POWER","INPUT|INPUT","MUTE|MUTE"});
        addRow.accept(new String[]{"VOL −|VOL_DOWN","HOME|HOME","VOL +|VOL_UP"});
        addRow.accept(new String[]{"CH −|CH_DOWN","▲|UP","CH +|CH_UP"});
        addRow.accept(new String[]{"◀|LEFT","OK|OK","▶|RIGHT"});
        addRow.accept(new String[]{"BACK|BACK","▼|DOWN","MENU|MENU"});
        addRow.accept(new String[]{"INFO|INFO","GUIDE|GUIDE","EXIT|EXIT"});
        addRow.accept(new String[]{"⏪|REW","▶/Ⅱ|PLAY","⏩|FF"});
        addRow.accept(new String[]{"1|1","2|2","3|3"});
        addRow.accept(new String[]{"4|4","5|5","6|6"});
        addRow.accept(new String[]{"7|7","8|8","9|9"});
        addRow.accept(new String[]{"STOP|STOP","0|0","PAUSE|PAUSE"});

        java.util.function.Consumer<TvDevice> onConnected=d->active[0]=d;

        settings.setOnClickListener(v->{
            haptic();
            PopupMenu menu=new PopupMenu(this,settings);
            menu.getMenu().add(L("AUTO FIND TV","TV अपने-आप खोजें"));
            menu.getMenu().add(L("MANUAL IP / TV TYPE","MANUAL IP / TV TYPE"));
            menu.getMenu().add(L("SAVED TV / RECONNECT","सेव TV / दोबारा कनेक्ट"));
            menu.getMenu().add(L("IR REMOTE MODE","IR रिमोट मोड"));
            menu.getMenu().add(L("IR AUTO TEST / RESET","IR AUTO TEST / RESET"));
            menu.getMenu().add(L("CONNECTION INFO","कनेक्शन जानकारी"));
            menu.getMenu().add(L("FORGET SAVED TV","सेव TV हटाएं"));

            menu.setOnMenuItemClickListener(item->{
                String title=item.getTitle().toString();

                if(title.equals(L("AUTO FIND TV","TV अपने-आप खोजें"))){
                    status.setText(L("Searching TVs on this Wi-Fi...","इस Wi-Fi पर TV खोज रहे हैं..."));
                    new Thread(()->{
                        java.util.ArrayList<TvDevice> found=discoverTvs();
                        runOnUiThread(()->{
                            if(found.isEmpty()){
                                status.setText(L(
                                        "No TV found. Keep phone and TV on the same Wi-Fi, or use Manual IP / IR.",
                                        "TV नहीं मिला। Phone और TV एक Wi-Fi पर रखें, या Manual IP / IR इस्तेमाल करें।"));
                                return;
                            }

                            String[] names=new String[found.size()];
                            for(int i=0;i<found.size();i++) names[i]=found.get(i).toString();

                            new AlertDialog.Builder(this)
                                    .setTitle(L("Select TV","TV चुनें"))
                                    .setItems(names,(d,which)->{
                                        if(which>=0 && which<found.size()){
                                            connectUniversalTv(found.get(which),status,onConnected);
                                        }
                                    })
                                    .setNegativeButton(L("CANCEL","रद्द करें"),null)
                                    .show();
                        });
                    }).start();
                    return true;
                }

                if(title.equals(L("MANUAL IP / TV TYPE","MANUAL IP / TV TYPE"))){
                    LinearLayout box=new LinearLayout(this);
                    box.setOrientation(LinearLayout.VERTICAL);
                    box.setPadding(dp(14),dp(4),dp(14),0);

                    EditText ip=new EditText(this);
                    ip.setHint("TV IP  e.g. 192.168.1.20");
                    ip.setSingleLine(true);
                    ip.setTextColor(Color.BLACK);
                    ip.setHintTextColor(Color.DKGRAY);
                    ip.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
                    box.addView(ip,new LinearLayout.LayoutParams(-1,dp(54)));

                    Spinner type=new Spinner(this);
                    String[] types={
                            L("AUTO DETECT","AUTO DETECT"),
                            "Android / Google TV",
                            "Roku TV",
                            "Samsung Smart TV",
                            "LG webOS TV"
                    };
                    ArrayAdapter<String> ta=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types);
                    type.setAdapter(ta);
                    box.addView(type,new LinearLayout.LayoutParams(-1,dp(54)));

                    new AlertDialog.Builder(this)
                            .setTitle(L("Manual TV Connection","Manual TV Connection"))
                            .setView(box)
                            .setNegativeButton(L("CANCEL","रद्द करें"),null)
                            .setPositiveButton(L("CONNECT","कनेक्ट"),(d,w)->{
                                String host=ip.getText().toString().trim();
                                if(host.isEmpty()){
                                    status.setText(L("Enter TV IP address","TV का IP address लिखें"));
                                    return;
                                }

                                int p=type.getSelectedItemPosition();
                                status.setText(L("Checking TV...","TV जांच रहे हैं..."));
                                new Thread(()->{
                                    String tvType;
                                    if(p==0) tvType=detectTvType(host);
                                    else if(p==1) tvType="ANDROID_TV";
                                    else if(p==2) tvType="ROKU";
                                    else if(p==3) tvType="SAMSUNG";
                                    else tvType="LG";

                                    TvDevice manual=new TvDevice(
                                            tvType.replace("_"," ")+" TV",host,tvType);
                                    runOnUiThread(()->connectUniversalTv(manual,status,onConnected));
                                }).start();
                            })
                            .show();
                    return true;
                }

                if(title.equals(L("SAVED TV / RECONNECT","सेव TV / दोबारा कनेक्ट"))){
                    android.content.SharedPreferences sp=getSharedPreferences("sts",0);
                    String ip=sp.getString("tv_ip","");
                    String type=sp.getString("tv_type","");
                    String name=sp.getString("tv_name","");
                    if(ip.isEmpty() || type.isEmpty()){
                        status.setText(L("No saved TV yet","अभी कोई TV सेव नहीं है"));
                    }else{
                        reconnectSavedTv(
                                new TvDevice(name.isEmpty()?"Saved TV":name,ip,type),
                                status,
                                onConnected);
                    }
                    return true;
                }

                if(title.equals(L("IR REMOTE MODE","IR रिमोट मोड"))){
                    if(!hasIrEmitter()){
                        new AlertDialog.Builder(this)
                                .setTitle(L("Universal IR Remote","Universal IR Remote"))
                                .setMessage(L(
                                        "This phone does not have an IR blaster.",
                                        "इस फोन में IR blaster नहीं है।"))
                                .setPositiveButton("OK",null)
                                .show();
                        return true;
                    }

                    String savedIr=savedUniversalIrProfile();
                    if(savedIr.isEmpty()){
                        showUniversalIrAutoTest(status,onConnected);
                    }else{
                        activateUniversalIr(savedIr,status,onConnected);
                    }
                    return true;
                }

                if(title.equals(L("IR AUTO TEST / RESET","IR AUTO TEST / RESET"))){
                    showUniversalIrAutoTest(status,onConnected);
                    return true;
                }

                if(title.equals(L("CONNECTION INFO","कनेक्शन जानकारी"))){
                    TvDevice d=active[0];
                    String msg;
                    if(d==null){
                        msg=L("No TV is connected.","कोई TV कनेक्ट नहीं है।");
                    }else if("IR".equals(d.type)){
                        msg=L(
                                "Mode: Universal IR\nConnection: Not required\nSaved profile: Yes",
                                "Mode: Universal IR\nConnection: जरूरी नहीं\nSaved profile: हाँ");
                    }else{
                        msg=L("Name: ","नाम: ")+d.name
                                +"\n"+L("Type: ","प्रकार: ")+d.type
                                +"\n"+L("Address / Profile: ","पता / प्रोफाइल: ")+d.ip;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle(L("TV Connection","TV कनेक्शन"))
                            .setMessage(msg)
                            .setPositiveButton("OK",null)
                            .show();
                    return true;
                }

                if(title.equals(L("FORGET SAVED TV","सेव TV हटाएं"))){
                    getSharedPreferences("sts",0).edit()
                            .remove("tv_name").remove("tv_ip").remove("tv_type").apply();
                    active[0]=null;
                    status.setText(L("Saved TV removed","सेव TV हटा दिया गया"));
                    return true;
                }

                return false;
            });
            menu.show();
        });

        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        String savedIp=sp.getString("tv_ip","");
        String savedType=sp.getString("tv_type","");
        String savedName=sp.getString("tv_name","Saved TV");

        if(!savedIp.isEmpty() && !savedType.isEmpty()){
            TvDevice saved=new TvDevice(savedName,savedIp,savedType);
            status.setText(L("Reconnecting saved TV...","सेव TV दोबारा कनेक्ट कर रहे हैं..."));
            reconnectSavedTv(saved,status,onConnected);
        }
    }

    private void setUnitDropdownItems(final Spinner spinner,String[] items){
        ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,items){
            @Override public View getView(int pos,View convert,android.view.ViewGroup parent){
                TextView t=(TextView)super.getView(pos,convert,parent);
                t.setText(String.valueOf(getItem(pos))+"    ▾");
                t.setTextColor(WHITE);
                t.setTextSize(17);
                t.setTypeface(null,1);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(dp(18),0,dp(18),0);
                GradientDrawable g=grad(
                        mixColor(PANEL2,toolAccent(),0.24f),
                        mixColor(PANEL,toolAccent(),0.15f),16);
                g.setStroke(dp(2),mixColor(toolAccent(),Color.WHITE,0.24f));
                t.setBackground(g);
                return t;
            }
            @Override public View getDropDownView(int pos,View convert,android.view.ViewGroup parent){
                TextView t=(TextView)super.getDropDownView(pos,convert,parent);
                boolean chosen=pos==spinner.getSelectedItemPosition();
                t.setText((chosen?"✓  ":"    ")+String.valueOf(getItem(pos)));
                t.setTextColor(chosen?WHITE:mixColor(SOFT,WHITE,0.28f));
                t.setTextSize(17);
                t.setTypeface(null,chosen?1:0);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(dp(58));
                t.setPadding(dp(18),dp(12),dp(18),dp(12));
                GradientDrawable g=grad(
                        chosen?mixColor(PANEL2,toolAccent(),0.42f):mixColor(PANEL,toolAccent(),0.10f),
                        chosen?mixColor(SURFACE,toolAccent(),0.34f):mixColor(PANEL2,toolAccent(),0.13f),
                        12);
                g.setStroke(dp(chosen?2:1),
                        chosen?mixColor(toolAccent(),WHITE,0.36f):Color.argb(90,Color.red(toolAccent()),Color.green(toolAccent()),Color.blue(toolAccent())));
                t.setBackground(g);
                return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(a);
        styleProfessionalSpinner(spinner);
        spinner.setBackground(touchBg(mixColor(PANEL2,toolAccent(),0.24f),16));
    }

    private String[] landStateNames(){
        return new String[]{
                "Bihar","Jharkhand","Uttar Pradesh","West Bengal","Assam",
                "Punjab","Haryana","Maharashtra","Karnataka","Telangana",
                "Andhra Pradesh","Tamil Nadu","Kerala",L("Other State","अन्य राज्य")
        };
    }

    private String[] landUnitNamesForState(int state){
        java.util.ArrayList<String> list=new java.util.ArrayList<>();
        list.add(L("Acre","एकड़"));
        list.add(L("Hectare","हेक्टेयर"));
        list.add(L("Decimal / Dismil","डिसमिल / डेसिमल"));
        list.add(L("Square Foot","वर्ग फुट"));
        list.add(L("Square Meter","वर्ग मीटर"));
        list.add(L("Square Yard / Gaj","वर्ग गज"));

        switch(state){
            case 0:
            case 1:
                list.add(L("Bigha","बीघा"));
                list.add(L("Katha","कट्ठा"));
                list.add(L("Dhur","धुर"));
                break;
            case 2:
                list.add(L("Bigha","बीघा"));
                list.add(L("Biswa","बिस्वा"));
                break;
            case 3:
                list.add(L("Bigha","बीघा"));
                list.add(L("Katha","कट्ठा"));
                list.add(L("Chhatak","छटाक"));
                break;
            case 4:
                list.add(L("Bigha","बीघा"));
                list.add(L("Katha","कट्ठा"));
                list.add(L("Lecha","लेचा"));
                break;
            case 5:
            case 6:
                list.add(L("Kanal","कनाल"));
                list.add(L("Marla","मरला"));
                break;
            case 7:
                list.add(L("Guntha","गुंठा"));
                break;
            case 8:
            case 9:
            case 10:
                list.add(L("Gunta","गुंटा"));
                break;
            case 11:
                list.add(L("Cent","सेंट"));
                list.add(L("Ground","ग्राउंड"));
                break;
            case 12:
                list.add(L("Cent","सेंट"));
                break;
        }
        return list.toArray(new String[0]);
    }

    private double[] landUnitFactorsSqFt(int state){
        java.util.ArrayList<Double> f=new java.util.ArrayList<>();
        f.add(43560.0);
        f.add(107639.1041670972);
        f.add(435.6);
        f.add(1.0);
        f.add(10.7639104167097);
        f.add(9.0);

        switch(state){
            case 0:
            case 1:
                f.add(27225.0);
                f.add(1361.25);
                f.add(68.0625);
                break;
            case 2:
                f.add(27000.0);
                f.add(1350.0);
                break;
            case 3:
                f.add(14400.0);
                f.add(720.0);
                f.add(45.0);
                break;
            case 4:
                f.add(14400.0);
                f.add(2880.0);
                f.add(144.0);
                break;
            case 5:
            case 6:
                f.add(5445.0);
                f.add(272.25);
                break;
            case 7:
            case 8:
            case 9:
            case 10:
                f.add(1089.0);
                break;
            case 11:
                f.add(435.6);
                f.add(2400.0);
                break;
            case 12:
                f.add(435.6);
                break;
        }

        double[] out=new double[f.size()];
        for(int i=0;i<f.size();i++) out[i]=f.get(i);
        return out;
    }

    private String[] unitNamesForCategory(int category){
        switch(category){
            case 1:
                return new String[]{
                        L("Millimeter","मिलीमीटर"),L("Centimeter","सेंटीमीटर"),
                        L("Meter","मीटर"),L("Kilometer","किलोमीटर"),
                        L("Inch","इंच"),L("Foot","फुट"),L("Yard","गज"),L("Mile","मील")
                };
            case 2:
                return new String[]{
                        L("Gram","ग्राम"),L("Kilogram","किलोग्राम"),
                        L("Quintal","क्विंटल"),L("Tonne","टन"),
                        L("Pound","पाउंड"),L("Ounce","औंस")
                };
            case 3:
                return new String[]{
                        L("Celsius","सेल्सियस"),L("Fahrenheit","फारेनहाइट"),L("Kelvin","केल्विन")
                };
            default:
                return new String[]{
                        L("Milliliter","मिलीलीटर"),L("Liter","लीटर"),
                        L("Cubic Meter","घन मीटर"),L("Cubic Foot","घन फुट"),
                        L("US Gallon","यूएस गैलन")
                };
        }
    }

    private double[] unitFactorsForCategory(int category){
        switch(category){
            case 1:
                return new double[]{0.001,0.01,1.0,1000.0,0.0254,0.3048,0.9144,1609.344};
            case 2:
                return new double[]{0.001,1.0,100.0,1000.0,0.45359237,0.028349523125};
            default:
                return new double[]{0.001,1.0,1000.0,28.316846592,3.785411784};
        }
    }

    private String buildFullUnitConversion(int category,int state,int from,double x){
        StringBuilder res=new StringBuilder();

        if(category==0){
            String[] names=landUnitNamesForState(state);
            double[] factors=landUnitFactorsSqFt(state);
            if(from<0 || from>=factors.length) return "";

            double baseSqFt=x*factors[from];
            res.append(trim(x)).append(" ").append(names[from]).append("\n\n");

            for(int i=0;i<names.length;i++){
                double y=baseSqFt/factors[i];
                res.append(names[i]).append(": ").append(trim(y));
                if(i<names.length-1) res.append("\n");
            }

            res.append("\n\n").append(L(
                    "State standard: ","राज्य मानक: ")).append(landStateNames()[state]);
            res.append("\n").append(L(
                    "Local land measures can vary by district/region.",
                    "स्थानीय जमीन माप जिला/क्षेत्र के अनुसार अलग हो सकता है।"));
            return res.toString();
        }

        String[] names=unitNamesForCategory(category);
        if(from<0 || from>=names.length) return "";

        res.append(trim(x)).append(" ").append(names[from]).append("\n\n");

        if(category==3){
            double celsius;
            if(from==0) celsius=x;
            else if(from==1) celsius=(x-32.0)*5.0/9.0;
            else celsius=x-273.15;

            double[] vals=new double[]{
                    celsius,
                    celsius*9.0/5.0+32.0,
                    celsius+273.15
            };

            for(int i=0;i<names.length;i++){
                res.append(names[i]).append(": ").append(trim(vals[i]));
                if(i<names.length-1) res.append("\n");
            }
            return res.toString();
        }

        double[] factors=unitFactorsForCategory(category);
        double base=x*factors[from];
        for(int i=0;i<names.length;i++){
            res.append(names[i]).append(": ").append(trim(base/factors[i]));
            if(i<names.length-1) res.append("\n");
        }
        return res.toString();
    }

    private void showUnitConverter(){
        currentTool="UNIT";
        shell(L("UNIT CONVERTER","यूनिट कन्वर्टर"));

        Spinner category=dropdown(new String[]{
                L("FIELD / LAND AREA","खेत / जमीन क्षेत्रफल"),
                L("LENGTH","लंबाई"),
                L("WEIGHT","वजन"),
                L("TEMPERATURE","तापमान"),
                L("VOLUME","मात्रा / वॉल्यूम")
        });
        root.addView(category,controlParams(56));

        Spinner state=dropdown(landStateNames());
        state.setSelection(0);
        root.addView(state,controlParams(56));

        Spinner from=dropdown(landUnitNamesForState(0));
        root.addView(from,controlParams(56));

        EditText in=input(L("Enter value","मान दर्ज करें"));
        root.addView(in);

        TextView out=tv(L("Enter one value to see complete details","एक मान डालें, पूरा विवरण यहाँ दिखेगा"),19,WHITE);
        styleResult(out);
        out.setGravity(Gravity.LEFT|Gravity.TOP);
        out.setPadding(dp(18),dp(16),dp(18),dp(16));

        ScrollView resultScroll=new ScrollView(this);
        resultScroll.setFillViewport(true);
        resultScroll.addView(out,new ScrollView.LayoutParams(-1,-1));
        root.addView(resultScroll,new LinearLayout.LayoutParams(-1,0,1));

        addHistoryShareBar(root,"unit",L("UNIT CONVERTER","यूनिट कन्वर्टर"),out);

        final Runnable refresh=()->{
            String raw=in.getText().toString().trim();
            if(raw.isEmpty()){
                out.setText(L("Enter one value to see complete details","एक मान डालें, पूरा विवरण यहाँ दिखेगा"));
                return;
            }

            int cat=category.getSelectedItemPosition();
            int st=state.getSelectedItemPosition();
            int fp=from.getSelectedItemPosition();
            double x=val(in);
            String res=buildFullUnitConversion(cat,st,fp,x);
            out.setText(res);
        };

        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                if(pos==0){
                    state.setVisibility(View.VISIBLE);
                    setUnitDropdownItems(from,landUnitNamesForState(state.getSelectedItemPosition()));
                }else{
                    state.setVisibility(View.GONE);
                    setUnitDropdownItems(from,unitNamesForCategory(pos));
                }
                refresh.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });

        state.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                if(category.getSelectedItemPosition()==0){
                    setUnitDropdownItems(from,landUnitNamesForState(pos));
                    refresh.run();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });

        from.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                refresh.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });

        in.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){ refresh.run(); }
            @Override public void afterTextChanged(android.text.Editable e){}
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
            p.setColor(mixColor(PANEL2,ACCENT,0.12f));
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
        meter.setBackground(grad(SURFACE,mixColor(PANEL,ACCENT,0.07f),16));
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

    private String quickBillInitials(String shopName){
        String name=shopName==null?"":shopName.trim().toUpperCase(java.util.Locale.US);
        if(name.isEmpty()) return "ST";

        String cleaned=name.replaceAll("[^A-Z0-9 ]+"," ").replaceAll("\\s+"," ").trim();
        if(cleaned.isEmpty()) return "ST";

        String[] words=cleaned.split(" ");
        StringBuilder code=new StringBuilder();
        if(words.length>1){
            for(String word:words){
                if(word.isEmpty()) continue;
                char c=word.charAt(0);
                if(Character.isLetterOrDigit(c)) code.append(c);
                if(code.length()>=4) break;
            }
        }else{
            String word=words[0];
            for(int i=0;i<word.length() && code.length()<2;i++){
                char c=word.charAt(i);
                if(Character.isLetterOrDigit(c)) code.append(c);
            }
        }
        return code.length()==0?"ST":code.toString();
    }

    private String quickBillCounterKey(String shopName){
        String normalized=shopName==null?"":shopName.trim().toLowerCase(java.util.Locale.US)
                .replaceAll("\\s+"," ");
        if(normalized.isEmpty()) normalized="sts digikit";
        return "quick_bill_counter_"+Integer.toHexString(normalized.hashCode());
    }

    private int quickBillNextNumber(String shopName){
        int last=getSharedPreferences("sts",0).getInt(quickBillCounterKey(shopName),0);
        return Math.max(1,last+1);
    }

    private String quickBillNumber(String shopName,int number){
        return quickBillInitials(shopName)+String.format(java.util.Locale.US,"%02d",Math.max(1,number));
    }

    private void commitQuickBillNumber(String shopName,int number){
        getSharedPreferences("sts",0).edit()
                .putInt(quickBillCounterKey(shopName),Math.max(1,number))
                .apply();
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

        if(gstTotal>0){
            res.append(qbPadRight(L("Sub Total","उप-योग"),23))
                    .append(qbPadLeft(df.format(subTotal),11)).append("\n");
            res.append(qbPadRight(L("GST","जीएसटी"),23))
                    .append(qbPadLeft(df.format(gstTotal),11)).append("\n");
            res.append(line).append("\n");
        }

        res.append(qbPadRight(L("TOTAL","कुल"),22))
                .append(qbPadLeft("Rs "+df.format(total),12)).append("\n");
        res.append(line).append("\n");
        return res.toString();
    }

    private String buildNotepadText(String noteTitle,String noteBody){
        String titleText=noteTitle==null?"":noteTitle.trim();
        String bodyText=noteBody==null?"":noteBody.trim();
        StringBuilder out=new StringBuilder();
        if(!titleText.isEmpty()){
            out.append(titleText).append("\n");
            out.append("--------------------------------").append("\n");
        }
        out.append(bodyText);
        return out.toString().trim();
    }

    private void saveNotepadHistory(String noteTitle,String noteBody){
        String titleText=noteTitle==null?"":noteTitle.trim();
        String bodyText=noteBody==null?"":noteBody.trim();
        if(titleText.isEmpty() && bodyText.isEmpty()) return;

        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        String old=sp.getString("notepad_history","");
        String raw=titleText+"\u001f"+bodyText;
        String payload=android.util.Base64.encodeToString(
                raw.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP);

        if(old!=null && !old.isEmpty()){
            String[] rows=old.split("\\u001e",-1);
            if(rows.length>0){
                String[] top=rows[0].split("\\|",2);
                if(top.length==2 && top[1].equals(payload)) return;
            }
        }

        String rec=System.currentTimeMillis()+"|"+payload;
        String merged=(old==null || old.isEmpty())?rec:rec+"\u001e"+old;
        String[] rows=merged.split("\\u001e",-1);
        StringBuilder keep=new StringBuilder();
        for(int i=0;i<rows.length && i<50;i++){
            if(i>0) keep.append("\u001e");
            keep.append(rows[i]);
        }
        sp.edit().putString("notepad_history",keep.toString()).apply();
    }

    private String[] decodeNotepadHistoryRow(String row){
        try{
            String[] p=row.split("\\|",2);
            if(p.length!=2) return null;
            long time=Long.parseLong(p[0]);
            String decoded=new String(
                    android.util.Base64.decode(p[1],android.util.Base64.NO_WRAP),
                    java.nio.charset.StandardCharsets.UTF_8);
            String[] note=decoded.split("\\u001f",-1);
            String titleText=note.length>0?note[0]:"";
            String bodyText=note.length>1?note[1]:"";
            String stamp=new java.text.SimpleDateFormat(
                    "dd/MM/yyyy hh:mm a",
                    java.util.Locale.getDefault()).format(new java.util.Date(time));
            return new String[]{stamp,titleText,bodyText};
        }catch(Exception e){
            return null;
        }
    }

    private void showNotepadEntry(String stamp,String noteTitle,String noteBody){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10),dp(8),dp(10),dp(8));
        box.setBackgroundColor(BG);

        if(stamp!=null && !stamp.isEmpty()){
            TextView date=tv(stamp,14,SOFT);
            box.addView(date,new LinearLayout.LayoutParams(-1,dp(38)));
        }

        ScrollView sc=new ScrollView(this);
        TextView content=tv(buildNotepadText(noteTitle,noteBody),17,WHITE);
        content.setGravity(Gravity.LEFT|Gravity.TOP);
        content.setTextIsSelectable(true);
        content.setPadding(dp(14),dp(14),dp(14),dp(14));
        content.setBackground(bg(PANEL,10));
        sc.addView(content,new ScrollView.LayoutParams(-1,-2));
        box.addView(sc,new LinearLayout.LayoutParams(-1,dp(430)));

        String dialogTitle=(noteTitle==null || noteTitle.trim().isEmpty())
                ?L("NOTEPAD NOTE","नोटपैड नोट")
                :noteTitle.trim();

        new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setView(box)
                .setPositiveButton(L("CLOSE","बंद करें"),null)
                .setNegativeButton(L("PRINT COPY","प्रिंट कॉपी"),(d,w)->
                        printNotepad58mm(buildNotepadText(noteTitle,noteBody)))
                .setNeutralButton(L("SHARE","शेयर"),(d,w)->
                        sharePanelText(dialogTitle,noteBody))
                .show();
    }

    private void showNotepadHistory(){
        android.content.SharedPreferences sp=getSharedPreferences("sts",0);
        String raw=sp.getString("notepad_history","");

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8),dp(8),dp(8),dp(8));
        box.setBackgroundColor(BG);

        ScrollView sc=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(2),dp(2),dp(2),dp(2));
        sc.addView(list,new ScrollView.LayoutParams(-1,-2));
        box.addView(sc,new LinearLayout.LayoutParams(-1,dp(500)));

        if(raw==null || raw.isEmpty()){
            TextView empty=tv(L("No notepad history yet.","अभी कोई नोटपैड हिस्ट्री नहीं है।"),16,SOFT);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
        }else{
            String[] rows=raw.split("\\u001e",-1);
            for(String row:rows){
                String[] item=decodeNotepadHistoryRow(row);
                if(item==null) continue;

                final String stamp=item[0];
                final String entryTitle=item[1];
                final String entryBody=item[2];

                LinearLayout card=new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(10),dp(8),dp(10),dp(8));
                card.setBackground(contentCardBg());

                String heading=entryTitle.trim().isEmpty()?L("Untitled Note","बिना शीर्षक नोट"):entryTitle.trim();
                TextView name=tv(heading,18,WHITE);
                name.setTypeface(null,1);
                card.addView(name,new LinearLayout.LayoutParams(-1,dp(42)));

                TextView date=tv(stamp,13,SOFT);
                card.addView(date,new LinearLayout.LayoutParams(-1,dp(32)));

                String preview=entryBody.replace("\n"," ").trim();
                if(preview.length()>120) preview=preview.substring(0,120)+"…";
                if(preview.isEmpty()) preview=L("(No body text)","(कोई मुख्य टेक्स्ट नहीं)");
                TextView p=tv(preview,15,WHITE);
                p.setGravity(Gravity.LEFT|Gravity.TOP);
                card.addView(p,new LinearLayout.LayoutParams(-1,dp(64)));

                LinearLayout buttons=new LinearLayout(this);
                buttons.setOrientation(LinearLayout.HORIZONTAL);
                Button view=btn(L("VIEW","देखें"));
                Button print=btn(L("PRINT COPY","प्रिंट कॉपी"));
                Button share=btn(L("SHARE","शेयर"));
                buttons.addView(view,new LinearLayout.LayoutParams(0,dp(50),1));
                buttons.addView(print,new LinearLayout.LayoutParams(0,dp(50),1));
                buttons.addView(share,new LinearLayout.LayoutParams(0,dp(50),1));
                card.addView(buttons,new LinearLayout.LayoutParams(-1,dp(52)));

                view.setOnClickListener(v->showNotepadEntry(stamp,entryTitle,entryBody));
                print.setOnClickListener(v->printNotepad58mm(buildNotepadText(entryTitle,entryBody)));
                share.setOnClickListener(v->sharePanelText(
                        entryTitle.trim().isEmpty()?L("NOTEPAD","नोटपैड"):entryTitle.trim(),
                        entryBody));

                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
                cp.setMargins(0,0,0,dp(10));
                list.addView(card,cp);
            }

            if(list.getChildCount()==0){
                TextView empty=tv(L("No notepad history yet.","अभी कोई नोटपैड हिस्ट्री नहीं है।"),16,SOFT);
                empty.setGravity(Gravity.CENTER);
                list.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
            }
        }

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(L("NOTEPAD HISTORY","नोटपैड हिस्ट्री"))
                .setView(box)
                .setPositiveButton(L("CLOSE","बंद करें"),null)
                .setNegativeButton(L("CLEAR HISTORY","हिस्ट्री साफ करें"),null)
                .create();

        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
            sp.edit().remove("notepad_history").apply();
            list.removeAllViews();
            TextView empty=tv(L("No notepad history yet.","अभी कोई नोटपैड हिस्ट्री नहीं है।"),16,SOFT);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty,new LinearLayout.LayoutParams(-1,dp(120)));
        }));
        dialog.show();
    }

    private void showNotepad(){
        currentTool="NOTEPAD";
        shell(L("NOTEPAD","नोटपैड"));
        root.setPadding(dp(4),dp(4),dp(4),dp(4));

        android.content.SharedPreferences sp=getSharedPreferences("sts",0);

        EditText noteTitle=input(L("Note Title (Optional)","नोट शीर्षक (वैकल्पिक)"));
        noteTitle.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                |android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        noteTitle.setText(sp.getString("notepad_draft_title",""));
        root.addView(noteTitle);

        EditText noteBody=new EditText(this);
        noteBody.setHint(L("Write or paste your note here...","यहाँ अपना नोट लिखें या पेस्ट करें..."));
        noteBody.setHintTextColor(SOFT);
        noteBody.setTextColor(WHITE);
        noteBody.setTextSize(18);
        noteBody.setGravity(Gravity.TOP|Gravity.LEFT);
        noteBody.setSingleLine(false);
        noteBody.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                |android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                |android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        noteBody.setPadding(dp(16),dp(14),dp(16),dp(14));
        noteBody.setBackground(fieldBg());
        noteBody.setElevation(dp(1));
        noteBody.setText(sp.getString("notepad_draft_body",""));
        attachInputBehavior(noteBody);
        LinearLayout.LayoutParams noteParams=new LinearLayout.LayoutParams(-1,0,1);
        noteParams.setMargins(0,dp(4),0,dp(6));
        root.addView(noteBody,noteParams);

        LinearLayout row1=new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button save=btn(L("SAVE","सेव"));
        Button history=btn(L("HISTORY","हिस्ट्री"));
        row1.addView(save,new LinearLayout.LayoutParams(0,dp(54),1));
        row1.addView(history,new LinearLayout.LayoutParams(0,dp(54),1));
        root.addView(row1,new LinearLayout.LayoutParams(-1,dp(56)));

        LinearLayout row2=new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button share=btn(L("SHARE","शेयर"));
        Button print=btn(L("PRINT 58MM","58MM प्रिंट"));
        row2.addView(share,new LinearLayout.LayoutParams(0,dp(56),1));
        row2.addView(print,new LinearLayout.LayoutParams(0,dp(56),1));
        LinearLayout.LayoutParams row2p=new LinearLayout.LayoutParams(-1,dp(58));
        row2p.setMargins(0,dp(4),0,0);
        root.addView(row2,row2p);

        android.text.TextWatcher draftWatcher=new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){
                sp.edit()
                        .putString("notepad_draft_title",noteTitle.getText().toString())
                        .putString("notepad_draft_body",noteBody.getText().toString())
                        .apply();
            }
            @Override public void afterTextChanged(android.text.Editable e){}
        };
        noteTitle.addTextChangedListener(draftWatcher);
        noteBody.addTextChangedListener(draftWatcher);

        save.setOnClickListener(v->{
            String t=noteTitle.getText().toString();
            String b=noteBody.getText().toString();
            if(t.trim().isEmpty() && b.trim().isEmpty()){
                Toast.makeText(this,L("Write something first","पहले कुछ लिखें"),Toast.LENGTH_SHORT).show();
                return;
            }
            saveNotepadHistory(t,b);
            Toast.makeText(this,L("Note saved to history","नोट हिस्ट्री में सेव हो गया"),Toast.LENGTH_SHORT).show();
        });

        history.setOnClickListener(v->{
            String t=noteTitle.getText().toString();
            String b=noteBody.getText().toString();
            if(!t.trim().isEmpty() || !b.trim().isEmpty()) saveNotepadHistory(t,b);
            showNotepadHistory();
        });

        share.setOnClickListener(v->{
            String t=noteTitle.getText().toString();
            String b=noteBody.getText().toString();
            if(t.trim().isEmpty() && b.trim().isEmpty()){
                Toast.makeText(this,L("Write something first","पहले कुछ लिखें"),Toast.LENGTH_SHORT).show();
                return;
            }
            saveNotepadHistory(t,b);
            sharePanelText(
                    t.trim().isEmpty()?L("NOTEPAD","नोटपैड"):t.trim(),
                    b.trim().isEmpty()?buildNotepadText(t,b):b);
        });

        print.setOnClickListener(v->{
            String t=noteTitle.getText().toString();
            String b=noteBody.getText().toString();
            String printable=buildNotepadText(t,b);
            if(!meaningfulResult(printable)){
                Toast.makeText(this,L("Write something first","पहले कुछ लिखें"),Toast.LENGTH_SHORT).show();
                return;
            }
            saveNotepadHistory(t,b);
            printNotepad58mm(printable);
        });
    }

    private boolean hasThermalBluetoothPermission(){
        return Build.VERSION.SDK_INT<31 ||
                checkSelfPermission("android.permission.BLUETOOTH_CONNECT")==PackageManager.PERMISSION_GRANTED;
    }

    private void ensureThermalBluetoothPermission(){
        if(hasThermalBluetoothPermission()){
            if(pendingThermalSetup){
                pendingThermalSetup=false;
                showThermalPrinterSetup();
            }else if(pendingThermalPrintText!=null && !pendingThermalPrintText.trim().isEmpty()){
                String p=pendingThermalPrintText;
                String mode=pendingThermalPrintMode;
                pendingThermalPrintText="";
                pendingThermalPrintMode="GENERIC";
                directThermalPrint(p,mode);
            }
            return;
        }

        if(Build.VERSION.SDK_INT>=31){
            try{
                requestPermissions(
                        new String[]{"android.permission.BLUETOOTH_CONNECT"},
                        REQ_THERMAL_BLUETOOTH);
            }catch(Exception e){
                pendingThermalSetup=false;
                pendingThermalPrintText="";
                pendingThermalPrintMode="GENERIC";
                Toast.makeText(this,
                        L("Bluetooth permission could not be requested","Bluetooth permission request नहीं हो सकी"),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String thermalPrinterAddress(){
        return getSharedPreferences("sts",0).getString("thermal_printer_address","");
    }

    private String thermalPrinterName(){
        return getSharedPreferences("sts",0).getString("thermal_printer_name","");
    }

    private void openBluetoothSettings(){
        try{
            startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
        }catch(Exception e){
            Toast.makeText(this,
                    L("Bluetooth settings could not be opened","Bluetooth settings नहीं खुल सकी"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showThermalPrinterSetup(){
        if(!hasThermalBluetoothPermission()){
            pendingThermalSetup=true;
            ensureThermalBluetoothPermission();
            return;
        }

        android.bluetooth.BluetoothAdapter adapter=android.bluetooth.BluetoothAdapter.getDefaultAdapter();
        if(adapter==null){
            Toast.makeText(this,
                    L("Bluetooth is not available on this device","इस डिवाइस में Bluetooth उपलब्ध नहीं है"),
                    Toast.LENGTH_LONG).show();
            return;
        }

        if(!adapter.isEnabled()){
            new AlertDialog.Builder(this)
                    .setTitle(L("Bluetooth is OFF","Bluetooth बंद है"))
                    .setMessage(L(
                            "Turn on Bluetooth, pair your 58mm thermal printer in phone settings, then return here.",
                            "Bluetooth चालू करें, फोन की settings में 58mm thermal printer pair करें, फिर यहाँ वापस आएँ।"))
                    .setPositiveButton(L("BLUETOOTH SETTINGS","BLUETOOTH SETTINGS"),(d,w)->openBluetoothSettings())
                    .setNegativeButton(L("CANCEL","रद्द करें"),null)
                    .show();
            return;
        }

        java.util.ArrayList<android.bluetooth.BluetoothDevice> devices=new java.util.ArrayList<>();
        try{
            java.util.Set<android.bluetooth.BluetoothDevice> bonded=adapter.getBondedDevices();
            if(bonded!=null) devices.addAll(bonded);
        }catch(SecurityException e){
            pendingThermalSetup=true;
            ensureThermalBluetoothPermission();
            return;
        }

        java.util.Collections.sort(devices,(a,b)->{
            String an=a.getName()==null?"":a.getName();
            String bn=b.getName()==null?"":b.getName();
            return an.compareToIgnoreCase(bn);
        });

        if(devices.isEmpty()){
            new AlertDialog.Builder(this)
                    .setTitle(L("No paired printer found","कोई paired printer नहीं मिला"))
                    .setMessage(L(
                            "First pair the 58mm thermal printer in Bluetooth settings. Then open Thermal Printer Setup again.",
                            "पहले Bluetooth settings में 58mm thermal printer को pair करें। फिर Thermal Printer Setup खोलें।"))
                    .setPositiveButton(L("BLUETOOTH SETTINGS","BLUETOOTH SETTINGS"),(d,w)->openBluetoothSettings())
                    .setNegativeButton(L("CLOSE","बंद करें"),null)
                    .show();
            return;
        }

        String saved=thermalPrinterAddress();
        String[] labels=new String[devices.size()];
        int checked=-1;
        for(int i=0;i<devices.size();i++){
            android.bluetooth.BluetoothDevice d=devices.get(i);
            String name=d.getName();
            if(name==null || name.trim().isEmpty()) name=L("Unnamed device","बिना नाम डिवाइस");
            labels[i]=name+"\n"+d.getAddress();
            if(d.getAddress().equalsIgnoreCase(saved)) checked=i;
        }

        final int[] selected={checked};
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(L("58MM THERMAL PRINTER","58MM थर्मल प्रिंटर"))
                .setSingleChoiceItems(labels,checked,(d,which)->selected[0]=which)
                .setPositiveButton(L("SAVE DEFAULT","DEFAULT सेव करें"),null)
                .setNeutralButton(L("TEST PRINT","टेस्ट प्रिंट"),null)
                .setNegativeButton(L("PAIR / SETTINGS","PAIR / SETTINGS"),null)
                .create();

        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(selected[0]<0 || selected[0]>=devices.size()){
                    Toast.makeText(this,L("Select a printer first","पहले printer चुनें"),Toast.LENGTH_SHORT).show();
                    return;
                }
                android.bluetooth.BluetoothDevice d=devices.get(selected[0]);
                String name=d.getName();
                if(name==null || name.trim().isEmpty()) name="Thermal Printer";
                getSharedPreferences("sts",0).edit()
                        .putString("thermal_printer_address",d.getAddress())
                        .putString("thermal_printer_name",name)
                        .apply();
                Toast.makeText(this,
                        L("Default printer saved: ","Default printer सेव: ")+name,
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();

                if(pendingThermalPrintText!=null && !pendingThermalPrintText.trim().isEmpty()){
                    String pending=pendingThermalPrintText;
                    String mode=pendingThermalPrintMode;
                    pendingThermalPrintText="";
                    pendingThermalPrintMode="GENERIC";
                    directThermalPrint(pending,mode);
                }
            });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                if(selected[0]<0 || selected[0]>=devices.size()){
                    Toast.makeText(this,L("Select a printer first","पहले printer चुनें"),Toast.LENGTH_SHORT).show();
                    return;
                }
                android.bluetooth.BluetoothDevice d=devices.get(selected[0]);
                String name=d.getName();
                if(name==null || name.trim().isEmpty()) name="Thermal Printer";
                getSharedPreferences("sts",0).edit()
                        .putString("thermal_printer_address",d.getAddress())
                        .putString("thermal_printer_name",name)
                        .apply();
                directThermalPrint(
                        "STS DigiKit\n58mm Thermal Printer Test\n\n"+
                        "English: OK\n"+
                        "हिंदी: प्रिंटर परीक्षण सफल\n"+
                        new java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a",java.util.Locale.getDefault())
                                .format(new java.util.Date()),"TEST");
            });

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->openBluetoothSettings());
        });
        dialog.show();
    }

    private java.util.ArrayList<String> thermalWrapLines(String text,android.graphics.Paint paint,int maxWidth){
        java.util.ArrayList<String> lines=new java.util.ArrayList<>();
        String safe=text==null?"":text.replace("\r","");
        String[] paragraphs=safe.split("\n",-1);

        for(String paragraph:paragraphs){
            if(paragraph.length()==0){
                lines.add("");
                continue;
            }

            String remaining=paragraph;
            while(remaining.length()>0){
                int fit=paint.breakText(remaining,true,maxWidth,null);
                if(fit<=0) fit=Math.min(1,remaining.length());

                // If the complete fixed-width row fits, preserve every padding space.
                if(fit>=remaining.length()){
                    lines.add(remaining);
                    break;
                }

                // Long free text may wrap, but do not destroy leading column padding.
                int split=fit;
                int space=remaining.lastIndexOf(' ',fit-1);
                if(space>0 && space>fit/2) split=space+1;

                lines.add(remaining.substring(0,split));
                remaining=remaining.substring(split);
                while(remaining.startsWith(" ")) remaining=remaining.substring(1);
            }
        }
        return lines;
    }

    private void thermalDrawFit(
            android.graphics.Canvas canvas,
            android.graphics.Paint paint,
            String text,
            float x,
            float y,
            float maxWidth,
            android.graphics.Paint.Align align){

        String value=text==null?"":text;
        float oldSize=paint.getTextSize();
        android.graphics.Paint.Align oldAlign=paint.getTextAlign();
        paint.setTextAlign(align);

        float size=oldSize;
        paint.setTextSize(size);
        while(size>15f && paint.measureText(value)>maxWidth){
            size-=1f;
            paint.setTextSize(size);
        }

        canvas.drawText(value,x,y,paint);
        paint.setTextSize(oldSize);
        paint.setTextAlign(oldAlign);
    }

    private android.graphics.Bitmap renderCashThermalBitmap(String content){
        final int width=384;
        final int left=18;
        final int right=374;
        final int top=10;
        final int lineHeight=31;

        String safe=content==null?"":content.replace("\r","");
        String[] lines=safe.split("\n",-1);
        int height=Math.max(90,top*2+lines.length*lineHeight+18);

        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(
                width,height,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(22f);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));

        java.util.regex.Pattern cashRow=java.util.regex.Pattern.compile(
                "^\\s*(₹[^\\s]+)\\s+x\\s+([^\\s]+)\\s+=\\s+(₹[^\\s]+)\\s*$");

        float y=top-paint.getFontMetrics().ascent;
        for(String raw:lines){
            String t=raw==null?"":raw.trim();

            if(t.isEmpty()){
                y+=lineHeight;
                continue;
            }

            if(t.matches("-{5,}")){
                android.graphics.Paint linePaint=new android.graphics.Paint();
                linePaint.setColor(Color.BLACK);
                linePaint.setStrokeWidth(1.4f);
                canvas.drawLine(left,y-9,right,y-9,linePaint);
                y+=lineHeight;
                continue;
            }

            java.util.regex.Matcher row=cashRow.matcher(t);
            if(row.matches()){
                // Fixed physical columns: these never depend on spaces/font width.
                thermalDrawFit(canvas,paint,row.group(1),88,y,70,android.graphics.Paint.Align.RIGHT);
                thermalDrawFit(canvas,paint,"x",111,y,18,android.graphics.Paint.Align.CENTER);
                thermalDrawFit(canvas,paint,row.group(2),184,y,62,android.graphics.Paint.Align.RIGHT);
                thermalDrawFit(canvas,paint,"=",211,y,18,android.graphics.Paint.Align.CENTER);
                thermalDrawFit(canvas,paint,row.group(3),right,y,145,android.graphics.Paint.Align.RIGHT);
                y+=lineHeight;
                continue;
            }

            if(t.startsWith("TOTAL")){
                String amount=t.substring(Math.min(5,t.length())).trim();
                thermalDrawFit(canvas,paint,"TOTAL",left,y,120,android.graphics.Paint.Align.LEFT);
                thermalDrawFit(canvas,paint,amount,right,y,170,android.graphics.Paint.Align.RIGHT);
                y+=lineHeight;
                continue;
            }

            if(t.startsWith("Party:")){
                thermalDrawFit(canvas,paint,t,left,y,right-left,android.graphics.Paint.Align.LEFT);
                y+=lineHeight;
                continue;
            }

            if(t.startsWith("STS DigiKit - Cash Counter")){
                thermalDrawFit(canvas,paint,t,width/2f,y,right-left,android.graphics.Paint.Align.CENTER);
                y+=lineHeight;
                continue;
            }

            if(t.matches("\\d{2}/\\d{2}/\\d{4}.*")){
                thermalDrawFit(canvas,paint,t,width/2f,y,right-left,android.graphics.Paint.Align.CENTER);
                y+=lineHeight;
                continue;
            }

            thermalDrawFit(canvas,paint,t,left,y,right-left,android.graphics.Paint.Align.LEFT);
            y+=lineHeight;
        }
        return bitmap;
    }

    private String thermalPadTo(String value,int width){
        String x=value==null?"":value;
        StringBuilder b=new StringBuilder(x);
        while(b.length()<width) b.append(' ');
        return b.toString();
    }

    private String[] thermalSplitTotalRow(String raw){
        String line=raw==null?"":raw;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\s{2,}").matcher(line);
        int split=-1,end=-1;
        while(m.find()){
            split=m.start();
            end=m.end();
        }
        if(split>0 && end>=0 && end<line.length()){
            return new String[]{line.substring(0,split).trim(),line.substring(end).trim()};
        }
        return new String[]{line.trim(),""};
    }

    private android.graphics.Bitmap renderBillThermalBitmap(String content){
        final int width=384;
        final int left=18;
        final int right=374;
        final int top=10;
        final int lineHeight=31;

        String safe=content==null?"":content.replace("\r","");
        String[] lines=safe.split("\n",-1);
        int height=Math.max(100,top*2+lines.length*lineHeight+20);

        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(
                width,height,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(22f);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));

        int separatorCount=0;
        float y=top-paint.getFontMetrics().ascent;

        for(String raw:lines){
            String original=raw==null?"":raw;
            String t=original.trim();

            if(t.isEmpty()){
                y+=lineHeight;
                continue;
            }

            if(t.matches("-{5,}")){
                android.graphics.Paint linePaint=new android.graphics.Paint();
                linePaint.setColor(Color.BLACK);
                linePaint.setStrokeWidth(1.4f);
                canvas.drawLine(left,y-9,right,y-9,linePaint);
                separatorCount++;
                y+=lineHeight;
                continue;
            }

            if(separatorCount==0){
                // Shop name and INVOICE.
                thermalDrawFit(canvas,paint,t,width/2f,y,right-left,android.graphics.Paint.Align.CENTER);
                y+=lineHeight;
                continue;
            }

            if(separatorCount==1){
                // Date, Bill No, Customer.
                thermalDrawFit(canvas,paint,t,left,y,right-left,android.graphics.Paint.Align.LEFT);
                y+=lineHeight;
                continue;
            }

            if(separatorCount==2){
                // Item | Qty | Amount heading.
                String padded=thermalPadTo(original,34);
                String item=padded.substring(0,Math.min(18,padded.length())).trim();
                String qty=padded.length()>18?padded.substring(18,Math.min(24,padded.length())).trim():"";
                String amount=padded.length()>24?padded.substring(24,Math.min(34,padded.length())).trim():"";

                thermalDrawFit(canvas,paint,item,left,y,190,android.graphics.Paint.Align.LEFT);
                thermalDrawFit(canvas,paint,qty,257,y,55,android.graphics.Paint.Align.RIGHT);
                thermalDrawFit(canvas,paint,amount,right,y,105,android.graphics.Paint.Align.RIGHT);
                y+=lineHeight;
                continue;
            }

            if(separatorCount==3){
                // Item rows. Fixed physical columns, never wrap Amount.
                String padded=thermalPadTo(original,34);

                if(t.startsWith("GST") || t.startsWith("जीएसटी")
                        || t.startsWith("GST ") || t.startsWith("  GST")){
                    String label=padded.substring(0,Math.min(24,padded.length())).trim();
                    String amount=padded.length()>24?padded.substring(24,Math.min(34,padded.length())).trim():"";
                    thermalDrawFit(canvas,paint,label,left+12,y,230,android.graphics.Paint.Align.LEFT);
                    thermalDrawFit(canvas,paint,amount,right,y,105,android.graphics.Paint.Align.RIGHT);
                }else{
                    String item=padded.substring(0,Math.min(18,padded.length())).trim();
                    String qty=padded.length()>18?padded.substring(18,Math.min(24,padded.length())).trim():"";
                    String amount=padded.length()>24?padded.substring(24,Math.min(34,padded.length())).trim():"";

                    thermalDrawFit(canvas,paint,item,left,y,190,android.graphics.Paint.Align.LEFT);
                    thermalDrawFit(canvas,paint,qty,257,y,55,android.graphics.Paint.Align.RIGHT);
                    thermalDrawFit(canvas,paint,amount,right,y,105,android.graphics.Paint.Align.RIGHT);
                }
                y+=lineHeight;
                continue;
            }

            // Totals section: label stays left, amount is always right aligned.
            String[] total=thermalSplitTotalRow(original);
            if(total[1].isEmpty()){
                // Fallback for older history rows.
                java.util.regex.Matcher m=java.util.regex.Pattern
                        .compile("^(.*?)(Rs\\s+[^\\s]+|₹[^\\s]+|[0-9,]+(?:\\.[0-9]+)?)$")
                        .matcher(t);
                if(m.matches()){
                    total[0]=m.group(1).trim();
                    total[1]=m.group(2).trim();
                }
            }

            if(!total[1].isEmpty()){
                thermalDrawFit(canvas,paint,total[0],left,y,210,android.graphics.Paint.Align.LEFT);
                thermalDrawFit(canvas,paint,total[1],right,y,150,android.graphics.Paint.Align.RIGHT);
            }else{
                thermalDrawFit(canvas,paint,t,left,y,right-left,android.graphics.Paint.Align.LEFT);
            }
            y+=lineHeight;
        }
        return bitmap;
    }

    private android.graphics.Bitmap renderPlainThermalBitmap(String content){
        final int width=384;
        final int leftPad=18;
        final int rightPad=2;
        final int topMargin=10;
        final int usable=width-leftPad-rightPad;

        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(24f);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));

        java.util.ArrayList<String> lines=thermalWrapLines(content,paint,usable);
        android.graphics.Paint.FontMetrics fm=paint.getFontMetrics();
        int lineHeight=Math.max(28,(int)Math.ceil(fm.descent-fm.ascent)+4);
        int height=Math.max(80,topMargin*2+(lines.size()*lineHeight)+18);

        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(
                width,height,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        float y=topMargin-fm.ascent;
        for(String line:lines){
            canvas.drawText(line,leftPad,y,paint);
            y+=lineHeight;
        }
        return bitmap;
    }

    private android.graphics.Bitmap renderThermalBitmap(String content,String printMode){
        if("CASH".equals(printMode)){
            return renderCashThermalBitmap(content);
        }
        if("BILL".equals(printMode)){
            return renderBillThermalBitmap(content);
        }
        return renderPlainThermalBitmap(content);
    }

    private void sendEscPosBitmapSafe(
            java.io.OutputStream out,
            android.graphics.Bitmap bitmap,
            String mode) throws Exception{

        final int width=bitmap.getWidth();
        final int widthBytes=(width+7)/8;

        // 384-dot raster stays unchanged. Small stripes and paced writes protect
        // common 58mm Bluetooth printer receive buffers from overflow/corruption.
        final int stripeHeight=16;
        final int chunkSize=256;
        final int stripeDelayMs="NOTEPAD".equals(mode)?105:80;

        out.write(new byte[]{0x1B,0x40});          // ESC @ reset
        out.write(new byte[]{0x1B,0x61,0x00});     // left align
        out.flush();
        try{Thread.sleep(160);}catch(InterruptedException ignored){}

        for(int startY=0;startY<bitmap.getHeight();startY+=stripeHeight){
            int h=Math.min(stripeHeight,bitmap.getHeight()-startY);
            int[] pixels=new int[width*h];
            bitmap.getPixels(pixels,0,width,0,startY,width,h);

            byte[] data=new byte[widthBytes*h];
            for(int y=0;y<h;y++){
                for(int x=0;x<width;x++){
                    int c=pixels[y*width+x];
                    int a=Color.alpha(c);
                    int lum=(Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000;
                    if(a>80 && lum<185){
                        int index=y*widthBytes+(x/8);
                        data[index]|=(byte)(0x80>>(x&7));
                    }
                }
            }

            byte[] header=new byte[]{
                    0x1D,0x76,0x30,0x00,
                    (byte)(widthBytes&0xFF),(byte)((widthBytes>>8)&0xFF),
                    (byte)(h&0xFF),(byte)((h>>8)&0xFF)
            };

            byte[] packet=new byte[header.length+data.length];
            System.arraycopy(header,0,packet,0,header.length);
            System.arraycopy(data,0,packet,header.length,data.length);

            for(int offset=0;offset<packet.length;offset+=chunkSize){
                int len=Math.min(chunkSize,packet.length-offset);
                out.write(packet,offset,len);
                out.flush();
                if(offset+len<packet.length){
                    try{Thread.sleep(8);}catch(InterruptedException ignored){}
                }
            }

            try{Thread.sleep(stripeDelayMs);}catch(InterruptedException ignored){}
        }

        out.write(new byte[]{0x0A,0x0A,0x0A});
        out.flush();
        try{Thread.sleep(220);}catch(InterruptedException ignored){}
    }

    private android.bluetooth.BluetoothSocket openThermalSocket(android.bluetooth.BluetoothDevice device) throws Exception{
        final java.util.UUID spp=java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        android.bluetooth.BluetoothSocket socket=null;

        try{
            socket=device.createRfcommSocketToServiceRecord(spp);
            socket.connect();
            return socket;
        }catch(Exception first){
            if(socket!=null) try{socket.close();}catch(Exception ignored){}
            try{
                java.lang.reflect.Method m=device.getClass().getMethod("createRfcommSocket",int.class);
                socket=(android.bluetooth.BluetoothSocket)m.invoke(device,1);
                socket.connect();
                return socket;
            }catch(Exception second){
                if(socket!=null) try{socket.close();}catch(Exception ignored){}
                throw first;
            }
        }
    }

    private void directThermalPrint(String content,String printMode){
        if(!meaningfulResult(content)){
            Toast.makeText(this,
                    L("Nothing to print yet","अभी print करने के लिए कुछ नहीं है"),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if(!hasThermalBluetoothPermission()){
            pendingThermalPrintText=content;
            pendingThermalPrintMode=printMode;
            pendingThermalSetup=false;
            ensureThermalBluetoothPermission();
            return;
        }

        android.bluetooth.BluetoothAdapter adapter=android.bluetooth.BluetoothAdapter.getDefaultAdapter();
        if(adapter==null){
            Toast.makeText(this,
                    L("Bluetooth is not available","Bluetooth उपलब्ध नहीं है"),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if(!adapter.isEnabled()){
            pendingThermalPrintText=content;
            pendingThermalPrintMode=printMode;
            new AlertDialog.Builder(this)
                    .setTitle(L("Bluetooth is OFF","Bluetooth बंद है"))
                    .setMessage(L(
                            "Turn on Bluetooth and keep the saved thermal printer ON.",
                            "Bluetooth चालू करें और saved thermal printer को ON रखें।"))
                    .setPositiveButton(L("BLUETOOTH SETTINGS","BLUETOOTH SETTINGS"),(d,w)->openBluetoothSettings())
                    .setNegativeButton(L("CANCEL","रद्द करें"),null)
                    .show();
            return;
        }

        final String address=thermalPrinterAddress();
        if(address==null || address.trim().isEmpty()){
            pendingThermalPrintText=content;
            pendingThermalPrintMode=printMode;
            showThermalPrinterSetup();
            return;
        }

        final String printerName=thermalPrinterName();
        Toast.makeText(this,
                L("Connecting to ","Printer से connect हो रहा है: ")+
                        (printerName==null||printerName.isEmpty()?address:printerName),
                Toast.LENGTH_SHORT).show();

        new Thread(()->{
            android.bluetooth.BluetoothSocket socket=null;
            android.graphics.Bitmap bitmap=null;
            try{
                android.bluetooth.BluetoothDevice device=adapter.getRemoteDevice(address);
                socket=openThermalSocket(device);
                bitmap=renderThermalBitmap(content,printMode);
                java.io.OutputStream out=socket.getOutputStream();
                sendEscPosBitmapSafe(out,bitmap,printMode);
                try{out.flush();}catch(Exception ignored){}

                runOnUiThread(()->Toast.makeText(
                        MainActivity.this,
                        L("Print sent successfully","Print सफलतापूर्वक भेज दिया गया"),
                        Toast.LENGTH_SHORT).show());
            }catch(SecurityException e){
                pendingThermalPrintText=content;
                pendingThermalPrintMode=printMode;
                runOnUiThread(()->{
                    pendingThermalSetup=false;
                    ensureThermalBluetoothPermission();
                });
            }catch(Exception e){
                final String msg=e.getMessage()==null?"":e.getMessage();
                runOnUiThread(()->new AlertDialog.Builder(MainActivity.this)
                        .setTitle(L("Printer connection failed","Printer connect नहीं हुआ"))
                        .setMessage(L(
                                "Check that the saved 58mm printer is ON and paired. You can change the printer from Thermal Printer Setup.",
                                "Saved 58mm printer ON और paired है या नहीं जाँचें। Thermal Printer Setup से printer बदल सकते हैं।")
                                +(msg.isEmpty()?"":"\n\n"+msg))
                        .setPositiveButton(L("PRINTER SETUP","PRINTER SETUP"),(d,w)->{
                            pendingThermalPrintText=content;
                            pendingThermalPrintMode=printMode;
                            showThermalPrinterSetup();
                        })
                        .setNegativeButton(L("CLOSE","बंद करें"),null)
                        .show());
            }finally{
                if(bitmap!=null) try{bitmap.recycle();}catch(Exception ignored){}
                if(socket!=null) try{socket.close();}catch(Exception ignored){}
            }
        }).start();
    }

    // Single gateway for every 58mm thermal print in STS DigiKit.
    // Main print buttons and history Print Copy actions must route through here.
    private void print58mmText(String content,String fileName,String jobName,String emptyEn,String emptyHi,String printMode){
        if(!meaningfulResult(content)){
            Toast.makeText(this,L(emptyEn,emptyHi),Toast.LENGTH_SHORT).show();
            return;
        }
        directThermalPrint(content,printMode);
    }

    private void printQuickBill58mm(String content){
        print58mmText(
                content,
                "STS-DigiKit-Bill.pdf",
                L("STS DigiKit Bill","STS DigiKit बिल"),
                "Nothing to print yet",
                "अभी print करने के लिए bill नहीं है",
                "BILL");
    }

    private void printNotepad58mm(String content){
        print58mmText(
                content,
                "STS-DigiKit-Notepad.pdf",
                L("STS DigiKit Notepad","STS DigiKit नोटपैड"),
                "Nothing to print yet",
                "अभी print करने के लिए note नहीं है",
                "NOTEPAD");
    }

    private void printCashSummary58mm(String content){
        print58mmText(
                content,
                "STS-DigiKit-Cash-Counter.pdf",
                L("STS DigiKit Cash Counter","STS DigiKit कैश काउंटर"),
                "Nothing to print yet",
                "अभी print करने के लिए cash summary नहीं है",
                "CASH");
    }

    private void showQuickBill(){
        currentTool="BILL";
        shell(L("QUICK BILL","क्विक बिल"));

        final String[] billDate={new java.text.SimpleDateFormat("dd/MM/yyyy, hh:mm a",java.util.Locale.getDefault()).format(new java.util.Date())};
        final java.util.ArrayList<QuickBillItem> billItems=new java.util.ArrayList<>();
        final int[] editingItemIndex={-1};

        android.content.SharedPreferences qbPrefs=getSharedPreferences("sts",0);
        final String[] shopCompanyName={qbPrefs.getString("quick_bill_default_shop_name","")};
        final int[] billSequence={quickBillNextNumber(shopCompanyName[0])};
        final String[] billNo={quickBillNumber(shopCompanyName[0],billSequence[0])};
        final boolean[] billNumberCommitted={false};

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

        Button editItems=btn(L("ITEMS / EDIT","आइटम / एडिट"));
        root.addView(editItems,controlParams(52));

        TextView out=tv(L("Bill preview will appear here","बिल यहाँ दिखाई देगा"),14,WHITE);
        out.setTypeface("HINDI".equals(language)
                ?android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL)
                :android.graphics.Typeface.MONOSPACE);
        out.setGravity(Gravity.TOP|Gravity.LEFT);
        out.setTextIsSelectable(true);
        out.setPadding(dp(12),dp(12),dp(12),dp(12));
        out.setBackground(grad(PANEL2,mixColor(PANEL2,ACCENT,0.15f),12));
        root.addView(out,new LinearLayout.LayoutParams(-1,0,1));

        addHistoryShareBar(root,"bill",L("QUICK BILL","क्विक बिल"),out);

        LinearLayout billActions=new LinearLayout(this);
        billActions.setOrientation(LinearLayout.HORIZONTAL);
        Button newBill=btn(L("NEW / CLEAR BILL","नया / क्लियर बिल"));
        Button print=btn(L("PRINT","प्रिंट"));
        LinearLayout.LayoutParams newBillParams=new LinearLayout.LayoutParams(0,dp(60),1);
        newBillParams.setMargins(0,0,dp(3),0);
        LinearLayout.LayoutParams printParams=new LinearLayout.LayoutParams(0,dp(60),1);
        printParams.setMargins(dp(3),0,0,0);
        billActions.addView(newBill,newBillParams);
        billActions.addView(print,printParams);
        root.addView(billActions,new LinearLayout.LayoutParams(-1,dp(62)));

        Runnable updateBill=()->out.setText(
                buildQuickBillPreview(shopCompanyName[0],customer,billItems,name,qty,rate,gst,billNo[0],billDate[0]));

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
                            if(!billNumberCommitted[0]){
                                billSequence[0]=quickBillNextNumber(value);
                                billNo[0]=quickBillNumber(value,billSequence[0]);
                            }
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

            if(editingItemIndex[0]>=0 && editingItemIndex[0]<billItems.size()){
                billItems.set(editingItemIndex[0],pending);
                editingItemIndex[0]=-1;
                addItem.setText("+");
            }else{
                billItems.add(pending);
            }

            name.setText("");
            qty.setText("");
            rate.setText("");
            gst.setText("");
            updateBill.run();
        });

        editItems.setOnClickListener(v->{
            if(billItems.isEmpty()){
                Toast.makeText(this,
                        L("No added items yet","अभी कोई item add नहीं है"),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            LinearLayout list=new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(8),dp(8),dp(8),dp(8));
            list.setBackgroundColor(BG);

            ScrollView scroll=new ScrollView(this);
            LinearLayout cards=new LinearLayout(this);
            cards.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(cards,new ScrollView.LayoutParams(-1,-2));
            list.addView(scroll,new LinearLayout.LayoutParams(-1,dp(480)));

            AlertDialog dialog=new AlertDialog.Builder(this)
                    .setTitle(L("EDIT / DELETE ITEMS","ITEM EDIT / DELETE"))
                    .setView(list)
                    .setPositiveButton(L("CLOSE","बंद करें"),null)
                    .create();

            final Runnable[] rebuild={null};
            rebuild[0]=()->{
                cards.removeAllViews();
                for(int i=0;i<billItems.size();i++){
                    final int index=i;
                    QuickBillItem row=billItems.get(i);

                    LinearLayout card=new LinearLayout(this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(10),dp(8),dp(10),dp(8));
                    card.setBackground(contentCardBg());

                    String details=row.name+"   |   "
                            +L("Qty ","मात्रा ")+trim(row.qty)+"   |   "
                            +L("Rate ₹","दर ₹")+df.format(row.rate)
                            +(row.gst>0?"   |   GST "+trim(row.gst)+"%":"");
                    TextView itemText=tv(details,15,WHITE);
                    itemText.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
                    card.addView(itemText,new LinearLayout.LayoutParams(-1,dp(54)));

                    LinearLayout actions=new LinearLayout(this);
                    actions.setOrientation(LinearLayout.HORIZONTAL);
                    Button edit=btn(L("EDIT","एडिट"));
                    Button delete=btn(L("DELETE","डिलीट"));
                    actions.addView(edit,new LinearLayout.LayoutParams(0,dp(50),1));
                    actions.addView(delete,new LinearLayout.LayoutParams(0,dp(50),1));
                    card.addView(actions,new LinearLayout.LayoutParams(-1,dp(52)));

                    edit.setOnClickListener(x->{
                        QuickBillItem selected=billItems.get(index);
                        editingItemIndex[0]=index;
                        name.setText(selected.name);
                        qty.setText(trim(selected.qty));
                        rate.setText(trim(selected.rate));
                        gst.setText(selected.gst>0?trim(selected.gst):"");
                        addItem.setText(L("UPDATE","अपडेट"));
                        dialog.dismiss();
                    });

                    delete.setOnClickListener(x->{
                        billItems.remove(index);
                        if(editingItemIndex[0]==index){
                            editingItemIndex[0]=-1;
                            addItem.setText("+");
                            name.setText("");
                            qty.setText("");
                            rate.setText("");
                            gst.setText("");
                        }else if(editingItemIndex[0]>index){
                            editingItemIndex[0]--;
                        }
                        updateBill.run();
                        if(billItems.isEmpty()){
                            dialog.dismiss();
                        }else{
                            rebuild[0].run();
                        }
                    });

                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
                    cp.setMargins(0,0,0,dp(8));
                    cards.addView(card,cp);
                }
            };

            rebuild[0].run();
            dialog.show();
        });

        newBill.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle(L("NEW / CLEAR BILL","नया / क्लियर बिल"))
                .setMessage(L(
                        "Clear the current customer and all items to start a new bill? Shop / Company Name will stay saved.",
                        "मौजूदा customer और सभी items साफ करके नया bill शुरू करें? Shop / Company Name सेव रहेगा।"))
                .setNegativeButton(L("CANCEL","रद्द करें"),null)
                .setPositiveButton(L("CLEAR & NEW","क्लियर करके नया"),(d,w)->{
                    customer.setText("");
                    billItems.clear();
                    editingItemIndex[0]=-1;
                    addItem.setText("+");
                    name.setText("");
                    qty.setText("");
                    rate.setText("");
                    gst.setText("");

                    billNumberCommitted[0]=false;
                    billSequence[0]=quickBillNextNumber(shopCompanyName[0]);
                    billNo[0]=quickBillNumber(shopCompanyName[0],billSequence[0]);
                    billDate[0]=new java.text.SimpleDateFormat(
                            "dd/MM/yyyy, hh:mm a",
                            java.util.Locale.getDefault()).format(new java.util.Date());

                    updateBill.run();
                })
                .show());

        print.setOnClickListener(v->{
            String bill=buildQuickBillPreview(shopCompanyName[0],customer,billItems,name,qty,rate,gst,billNo[0],billDate[0]);
            out.setText(bill);
            if(meaningfulResult(bill)){
                if(!billNumberCommitted[0]){
                    commitQuickBillNumber(shopCompanyName[0],billSequence[0]);
                    billNumberCommitted[0]=true;
                }
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
        result.setBackground(grad(PANEL,mixColor(PANEL,SURFACE,0.35f),0));

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
                        ds.setColor(ACCENT);
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

    private android.hardware.Camera findScannerLegacyCamera(Object obj,java.util.IdentityHashMap<Object,Boolean> seen,int depth){
        if(obj==null || depth>5 || seen.containsKey(obj)) return null;
        seen.put(obj,Boolean.TRUE);
        if(obj instanceof android.hardware.Camera) return (android.hardware.Camera)obj;

        Class<?> c=obj.getClass();
        while(c!=null && c!=Object.class){
            java.lang.reflect.Field[] fields;
            try{fields=c.getDeclaredFields();}catch(Throwable e){break;}
            for(java.lang.reflect.Field field:fields){
                try{
                    field.setAccessible(true);
                    Object value=field.get(obj);
                    if(value instanceof android.hardware.Camera) return (android.hardware.Camera)value;
                    if(value!=null){
                        String cn=value.getClass().getName();
                        if(cn.startsWith("com.journeyapps.barcodescanner") || cn.toLowerCase(java.util.Locale.US).contains("camera")){
                            android.hardware.Camera found=findScannerLegacyCamera(value,seen,depth+1);
                            if(found!=null) return found;
                        }
                    }
                }catch(Throwable ignored){}
            }
            c=c.getSuperclass();
        }
        return null;
    }

    private void applyScannerPinchZoom(float scaleFactor){
        if(embeddedScanner==null || !scannerActive) return;
        try{
            Object barcodeView=embeddedScanner.getBarcodeView();
            android.hardware.Camera camera=findScannerLegacyCamera(
                    barcodeView,new java.util.IdentityHashMap<>(),0);
            if(camera==null) return;

            android.hardware.Camera.Parameters params=camera.getParameters();
            if(params==null || !params.isZoomSupported()) return;
            int max=params.getMaxZoom();
            if(max<=0) return;

            float delta=(scaleFactor-1f)*0.70f;
            scannerZoomLevel=Math.max(0f,Math.min(1f,scannerZoomLevel+delta));
            int target=Math.max(0,Math.min(max,Math.round(scannerZoomLevel*max)));
            if(params.getZoom()!=target){
                params.setZoom(target);
                camera.setParameters(params);
            }
        }catch(Throwable ignored){}
    }

    private void installScannerPinchZoom(){
        if(scannerViewport==null) return;
        scannerScaleDetector=new android.view.ScaleGestureDetector(
                this,
                new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
                    @Override public boolean onScale(android.view.ScaleGestureDetector detector){
                        applyScannerPinchZoom(detector.getScaleFactor());
                        return true;
                    }
                });

        scannerViewport.setOnTouchListener((v,event)->{
            if(event.getPointerCount()>1) v.getParent().requestDisallowInterceptTouchEvent(true);
            boolean handled=scannerScaleDetector!=null && scannerScaleDetector.onTouchEvent(event);
            if(event.getActionMasked()==MotionEvent.ACTION_UP ||
                    event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return event.getPointerCount()>1 || handled;
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
        scannerZoomLevel=0f;

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
        configureSystemBars();

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
        scannerZoomLevel=0f;
        installScannerPinchZoom();

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
            return;
        }

        if(requestCode==REQ_THERMAL_BLUETOOTH){
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                if(pendingThermalSetup){
                    pendingThermalSetup=false;
                    showThermalPrinterSetup();
                }else if(pendingThermalPrintText!=null && !pendingThermalPrintText.trim().isEmpty()){
                    String p=pendingThermalPrintText;
                    String mode=pendingThermalPrintMode;
                    pendingThermalPrintText="";
                    pendingThermalPrintMode="GENERIC";
                    directThermalPrint(p,mode);
                }
            }else{
                pendingThermalSetup=false;
                pendingThermalPrintText="";
                pendingThermalPrintMode="GENERIC";
                Toast.makeText(this,
                        L("Nearby devices permission is required for direct thermal printing.",
                          "Direct thermal printing के लिए Nearby devices permission जरूरी है।"),
                        Toast.LENGTH_LONG).show();
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
        if(requestCode==REQ_SAVE_QR_IMAGE){
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null){
                if(lastGeneratedQrBitmap==null || lastGeneratedQrBitmap.isRecycled()){
                    Toast.makeText(this,L("QR image is no longer available","QR image उपलब्ध नहीं है"),Toast.LENGTH_SHORT).show();
                    return;
                }
                java.io.OutputStream out=null;
                try{
                    out=getContentResolver().openOutputStream(data.getData());
                    if(out==null) throw new java.io.IOException("No output stream");
                    writeBitmapExport(lastGeneratedQrBitmap,out,pendingQrSaveFormat,0);
                    out.flush();
                    Toast.makeText(this,L("QR saved as ","QR सेव हुआ: ")+pendingQrSaveFormat,Toast.LENGTH_SHORT).show();
                }catch(Exception e){
                    Toast.makeText(this,L("QR could not be saved","QR सेव नहीं हो सका"),Toast.LENGTH_SHORT).show();
                }finally{
                    if(out!=null) try{out.close();}catch(Exception ignored){}
                }
            }
            return;
        }

        if(requestCode==REQ_PICK_RESIZER_IMAGE){
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null){
                Uri uri=data.getData();
                try{
                    getContentResolver().takePersistableUriPermission(
                            uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }catch(Exception ignored){}
                try{
                    Bitmap bm=loadWorkBitmap(uri,2600);
                    if(resizerSourceBitmap!=null && !resizerSourceBitmap.isRecycled()){
                        try{resizerSourceBitmap.recycle();}catch(Exception ignored){}
                    }
                    if(resizerOutputBitmap!=null && resizerOutputBitmap!=resizerSourceBitmap && !resizerOutputBitmap.isRecycled()){
                        try{resizerOutputBitmap.recycle();}catch(Exception ignored){}
                    }
                    resizerSourceUri=uri;
                    resizerSourceBitmap=bm;
                    resizerOutputBitmap=null;
                    showPhotoSignatureResizer();
                }catch(Exception e){
                    Toast.makeText(this,L("Image could not open","Image नहीं खुल सकी"),Toast.LENGTH_LONG).show();
                }
            }
            return;
        }

        if(requestCode==REQ_SAVE_RESIZER){
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null && resizerOutputBitmap!=null){
                java.io.OutputStream out=null;
                try{
                    out=getContentResolver().openOutputStream(data.getData());
                    if(out==null) throw new java.io.IOException("No output stream");
                    writeBitmapExport(resizerOutputBitmap,out,pendingResizerFormat,resizerTargetKb);
                    out.flush();
                    Toast.makeText(this,L("Saved as ","सेव हुआ: ")+pendingResizerFormat,Toast.LENGTH_SHORT).show();
                }catch(Exception e){
                    Toast.makeText(this,L("Image could not be saved","Image सेव नहीं हो सकी"),Toast.LENGTH_LONG).show();
                }finally{
                    if(out!=null) try{out.close();}catch(Exception ignored){}
                }
            }
            return;
        }

        if(requestCode==REQ_PICK_PDFS){
            if(resultCode==RESULT_OK && data!=null){
                java.util.ArrayList<Uri> incoming=new java.util.ArrayList<>();
                if(data.getClipData()!=null){
                    android.content.ClipData cd=data.getClipData();
                    for(int i=0;i<cd.getItemCount();i++){
                        Uri u=cd.getItemAt(i).getUri();
                        if(u!=null) incoming.add(u);
                    }
                }else if(data.getData()!=null){
                    incoming.add(data.getData());
                }
                for(Uri u:incoming){
                    if(!pdfToolUris.contains(u)) pdfToolUris.add(u);
                    try{
                        getContentResolver().takePersistableUriPermission(
                                u,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }catch(Exception ignored){}
                }
                lastJoinedPdfBytes=null;
                showPdfJoinResize();
            }
            return;
        }

        if(requestCode==REQ_SAVE_JOINED_PDF){
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null && lastJoinedPdfBytes!=null){
                java.io.OutputStream out=null;
                try{
                    out=getContentResolver().openOutputStream(data.getData());
                    if(out==null) throw new java.io.IOException("No output stream");
                    out.write(lastJoinedPdfBytes);
                    out.flush();
                    Toast.makeText(this,L("PDF saved","PDF सेव हो गया"),Toast.LENGTH_SHORT).show();
                }catch(Exception e){
                    Toast.makeText(this,L("PDF could not be saved","PDF सेव नहीं हो सका"),Toast.LENGTH_LONG).show();
                }finally{
                    if(out!=null) try{out.close();}catch(Exception ignored){}
                }
            }
            return;
        }

        if(requestCode==REQ_SAVE_PDF_IMAGES_DIR){
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null){
                exportPdfSourcesAsImages(data.getData(),pendingPdfExportFormat);
            }
            return;
        }

        if(requestCode==REQ_SAVE_VIEWER_PDF){
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null && viewerPdfUri!=null){
                try{
                    copyUri(viewerPdfUri,data.getData());
                    Toast.makeText(this,L("PDF downloaded","PDF डाउनलोड हो गया"),Toast.LENGTH_SHORT).show();
                }catch(Exception e){
                    Toast.makeText(this,L("PDF could not be saved","PDF सेव नहीं हो सका"),Toast.LENGTH_LONG).show();
                }
            }
            return;
        }

        if(requestCode==REQ_SAVE_VIEWER_IMAGES_DIR){
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null && viewerPdfUri!=null){
                exportViewerPagesToTree(data.getData(),pendingViewerExportFormat);
            }
            return;
        }

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
        if("PDF_VIEWER".equals(currentTool)){
            closePdfViewerResources();
            finish();
            return;
        }
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

    @Override protected void onDestroy(){
        closePdfViewerResources();
        super.onDestroy();
    }
}
