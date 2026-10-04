package com.sts.fastbrowser;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.URLUtil;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class MainActivity extends android.app.Activity {
    private static final int REQ_LOCATION = 812;
    private static final int REQ_DOCUMENT_ACCESS = 813;
    private static final String PREFS = "sts_fast_browser_prefs";
    private static final String KEY_SITES = "sites_json";
    private static final String KEY_SITES_D1 = "sites_d1_json";
    private static final String KEY_SITES_D2 = "sites_d2_json";
    private static final String KEY_SITES_MIGRATED = "sites_split_migrated";
    private static final String KEY_ADBLOCK = "adblock";
    private static final String KEY_HARD_ADBLOCK = "hard_adblock";
    private static final String KEY_SLOT1_NAME = "slot1_name";
    private static final String KEY_SLOT1_URL = "slot1_url";
    private static final String KEY_SLOT2_NAME = "slot2_name";
    private static final String KEY_SLOT2_URL = "slot2_url";
    private static final String GOOGLE_NAME = "Google";
    private static final String GOOGLE_URL = "https://www.google.com/";

    private SharedPreferences prefs;
    private final List<Site> sitesD1 = new ArrayList<>();
    private final List<Site> sitesD2 = new ArrayList<>();
    private LinearLayout slot1Container;
    private LinearLayout slot2Container;
    private TextView slot1Button;
    private TextView slot2Button;
    private ImageView slot1HomeIcon;
    private ImageView slot1RefreshIcon;
    private ImageView slot2HomeIcon;
    private ImageView slot2RefreshIcon;
    private ImageView menuButton;
    private FrameLayout webStage;
    private WebView webView1;
    private WebView webView2;
    private final java.util.WeakHashMap<WebView, Float> browserPageZoom = new java.util.WeakHashMap<>();
    // Active-slot alias. D1 and D2 themselves stay alive independently.
    private WebView webView;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private boolean adBlockEnabled = true;
    private boolean hardAdBlockEnabled = true;
    private int activeSlot = 1;
    private String slot1Name = GOOGLE_NAME;
    private String slot1Url = GOOGLE_URL;
    private String slot2Name = GOOGLE_NAME;
    private String slot2Url = GOOGLE_URL;
    private volatile boolean slot1DocumentsHome = true;
    private volatile String phoneDataMode = "Documents";
    private volatile boolean documentAccessRequested = false;
    private int documentsLoadGeneration = 0;

    private final String[] blockedHosts = new String[] {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.", "adsystem.com", "adnxs.com", "adsrvr.org",
            "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
            "scorecardresearch.com", "zedo.com", "pubmatic.com", "rubiconproject.com",
            "openx.net", "moatads.com", "amazon-adsystem.com", "serving-sys.com",
            "smartadserver.com", "adform.net", "quantserve.com", "yieldmo.com",
            "casalemedia.com", "lijit.com", "smaato.net", "advertising.com",
            "media.net", "revcontent.com", "mgid.com", "exoclick.com",
            "propellerads.com", "popads.net", "popcash.net", "onclickads.net",
            "hilltopads.net", "juicyads.com", "trafficjunky.net", "adsafeprotected.com"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#355C62"));
        w.setNavigationBarColor(Color.parseColor("#F2F5F4"));

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        adBlockEnabled = prefs.getBoolean(KEY_ADBLOCK, true);
        hardAdBlockEnabled = prefs.getBoolean(KEY_HARD_ADBLOCK, true);
        loadSites();
        loadSlots();
        setContentView(buildUi());
        configureWebView(webView1);
        configureWebView(webView2);
        showWebView(1);
        updateSlotLabels();
        if (!handleBrowserIntent(getIntent())) {
            loadInitialPage();
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), dp(3), dp(4), dp(3));
        GradientDrawable topBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#D9F0EE"), Color.parseColor("#E3EAF4"), Color.parseColor("#EEE8F4")}
        );
        top.setBackground(topBg);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        slot1Container = makeSlotContainer(Color.parseColor("#BFE3DF"));
        slot2Container = makeSlotContainer(Color.parseColor("#CAD7E7"));

        slot1HomeIcon = makeIconButton(R.drawable.ic_home);
        slot1Button = makeSlotLabel("Google ▾");
        slot1RefreshIcon = makeIconButton(R.drawable.ic_refresh);

        slot2HomeIcon = makeIconButton(R.drawable.ic_home);
        slot2Button = makeSlotLabel("Google ▾");
        slot2RefreshIcon = makeIconButton(R.drawable.ic_refresh);

        slot1Container.addView(slot1HomeIcon, new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.MATCH_PARENT));
        slot1Container.addView(slot1Button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        slot1Container.addView(slot1RefreshIcon, new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.MATCH_PARENT));

        slot2Container.addView(slot2HomeIcon, new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.MATCH_PARENT));
        slot2Container.addView(slot2Button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        slot2Container.addView(slot2RefreshIcon, new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.MATCH_PARENT));

        menuButton = makeIconButton(R.drawable.ic_more);
        menuButton.setBackground(makeRipple(Color.parseColor("#DDD2E8")));
        menuButton.setContentDescription("Menu");

        LinearLayout.LayoutParams slotLp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        slotLp1.setMargins(0, 0, dp(3), 0);
        LinearLayout.LayoutParams slotLp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        slotLp2.setMargins(0, 0, dp(3), 0);
        top.addView(slot1Container, slotLp1);
        top.addView(slot2Container, slotLp2);
        top.addView(menuButton, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT));

        webStage = new FrameLayout(this);
        webView1 = new WebView(this);
        webView2 = new WebView(this);
        webView1.setBackgroundColor(Color.WHITE);
        webView2.setBackgroundColor(Color.WHITE);
        webStage.addView(webView1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        webStage.addView(webView2, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        webView2.setVisibility(View.GONE);
        webView = webView1;
        root.addView(webStage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        slot1Button.setOnClickListener(v -> showSitePopup(slot1Container, 1));
        slot2Button.setOnClickListener(v -> showSitePopup(slot2Container, 2));
        slot1HomeIcon.setOnClickListener(v -> goHome(1));
        slot2HomeIcon.setOnClickListener(v -> goHome(2));
        slot1RefreshIcon.setOnClickListener(v -> refreshSlot(1));
        slot2RefreshIcon.setOnClickListener(v -> refreshSlot(2));
        menuButton.setOnClickListener(this::showMainMenu);

        applyPressAnimation(slot1Button);
        applyPressAnimation(slot2Button);
        applyPressAnimation(slot1HomeIcon);
        applyPressAnimation(slot2HomeIcon);
        applyPressAnimation(slot1RefreshIcon);
        applyPressAnimation(slot2RefreshIcon);
        applyPressAnimation(menuButton);

        return root;
    }

    private LinearLayout makeSlotContainer(int baseColor) {
        LinearLayout slot = new LinearLayout(this);
        slot.setOrientation(LinearLayout.HORIZONTAL);
        slot.setGravity(Gravity.CENTER_VERTICAL);
        slot.setPadding(dp(1), 0, dp(1), 0);
        slot.setBackground(makeRipple(baseColor));
        return slot;
    }

    private TextView makeSlotLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#162326"));
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(dp(2), 0, dp(2), 0);
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    private ImageView makeIconButton(int drawableRes) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(drawableRes);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        iv.setClickable(true);
        iv.setFocusable(true);
        iv.setBackgroundColor(Color.TRANSPARENT);
        return iv;
    }

    private TextView makeTopButton(String text, int baseColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#162326"));
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(dp(8), 0, dp(8), 0);
        tv.setBackground(makeRipple(baseColor));
        tv.setClickable(true);
        tv.setFocusable(true);
        return tv;
    }

    private RippleDrawable makeRipple(int baseColor) {
        GradientDrawable base = new GradientDrawable();
        base.setColor(baseColor);
        base.setCornerRadius(dp(10));
        base.setStroke(dp(1), darken(baseColor, 0.12f));
        ColorStateList ripple = ColorStateList.valueOf(withAlpha(Color.parseColor("#203B40"), 48));
        return new RippleDrawable(ripple, base, null);
    }

    private void applyPressAnimation(View v) {
        v.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            }
            return false;
        });
    }

    private void installReliablePinchZoomOut(WebView targetWebView) {
        // This is browser-page zoom only. PDF/Image viewers are separate activities.
        //
        // Native WebView pinch-out can stop at its minimum/overview scale. For the user's
        // "make the whole website smaller so the right side becomes visible" gesture,
        // apply a Chromium CSS page zoom below 100%. While CSS zoom is below 100%,
        // the reverse two-finger gesture restores it toward 100%. At 100%, normal
        // WebView zoom-in remains untouched.
        browserPageZoom.put(targetWebView, 1.0f);
        final boolean[] customGesture = {false};
        final boolean[] multiTouch = {false};

        ScaleGestureDetector detector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float factor = detector.getScaleFactor();
                        if (Float.isNaN(factor) || Float.isInfinite(factor)) return false;

                        float current = browserPageZoom.containsKey(targetWebView) ? browserPageZoom.get(targetWebView) : 1.0f;

                        // At normal 100% zoom, keep pinch-to-zoom-in completely native.
                        // Switch to custom page scaling only when the fingers move inward
                        // (zoom out), or while restoring an already zoomed-out page.
                        if (!customGesture[0] && current >= 0.999f && factor >= 0.995f) {
                            return false;
                        }

                        customGesture[0] = true;

                        float next = current * factor;
                        next = Math.max(0.25f, Math.min(1.0f, next));
                        if (Math.abs(next - current) < 0.001f) return true;

                        browserPageZoom.put(targetWebView, next);
                        applyBrowserPageZoom(targetWebView, next);
                        return true;
                    }
                });

        targetWebView.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {
                customGesture[0] = false;
                multiTouch[0] = false;
            }

            if (event.getPointerCount() >= 2) multiTouch[0] = true;

            boolean wasCustom = customGesture[0];
            if (event.getPointerCount() >= 2 || multiTouch[0] || customGesture[0]) {
                detector.onTouchEvent(event);
            }

            // Cancel WebView's native pinch only after this gesture has actually
            // become a custom zoom-out gesture. Single-finger touch stays untouched.
            if (!wasCustom && customGesture[0]) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                try {
                    targetWebView.onTouchEvent(cancel);
                } finally {
                    cancel.recycle();
                }
            }

            boolean consume = customGesture[0];

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                customGesture[0] = false;
                multiTouch[0] = false;
            } else if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerCount() <= 2) {
                customGesture[0] = false;
                multiTouch[0] = false;
            }

            return consume;
        });

    }

    private void applyBrowserPageZoom(WebView view, float scale) {
        if (view == null) return;
        final float safe = Math.max(0.25f, Math.min(1.0f, scale));
        String js =
                "(function(){" +
                "try{" +
                "var z=" + String.format(Locale.US, "%.4f", safe) + ";" +
                "var d=document.documentElement;" +
                "if(d){" +
                "d.style.setProperty('zoom',String(z),'important');" +
                "d.style.setProperty('transform-origin','0 0','important');" +
                "}" +
                "}catch(e){}" +
                "})();";
        try { view.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private void configureWebView(WebView targetWebView) {
        WebSettings s = targetWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(targetWebView, true);

        installReliablePinchZoomOut(targetWebView);

        targetWebView.addJavascriptInterface(new PdfBridge(), "STSPdf");
        targetWebView.addJavascriptInterface(new RdBridge(), "STSRD");
        if (targetWebView == webView1) {
            targetWebView.addJavascriptInterface(new DocumentsBridge(), "STSDocuments");
        }

        targetWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                    return;
                }

                pendingGeoOrigin = origin;
                pendingGeoCallback = callback;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, REQ_LOCATION);
                } else {
                    callback.invoke(origin, true, false);
                    pendingGeoOrigin = null;
                    pendingGeoCallback = null;
                }
            }

            @Override
            public void onGeolocationPermissionsHidePrompt() {
                // Keep Android runtime permission flow independent from the webpage prompt.
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView child = new WebView(MainActivity.this);
                WebSettings childSettings = child.getSettings();
                childSettings.setJavaScriptEnabled(true);
                childSettings.setDomStorageEnabled(true);
                CookieManager.getInstance().setAcceptCookie(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(child, true);

                child.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
                    String mt = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
                    if (mt.contains("application/pdf") || isPdfCandidate(url, null)) {
                        openPdfTask(url, guessPdfName(url, contentDisposition));
                    } else if (isOfficeCandidate(url, mt)) {
                        openOfficeTask(url, guessOfficeName(url, contentDisposition, mt));
                    } else if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                        targetWebView.loadUrl(url);
                    }
                    child.destroy();
                });

                child.setWebViewClient(new WebViewClient() {
                    private boolean handled = false;

                    private boolean handle(String url) {
                        if (handled || url == null) return false;
                        try {
                            Uri popupUri = Uri.parse(url);
                            if ((adBlockEnabled && isBlocked(popupUri)) ||
                                    (hardAdBlockEnabled && isHardAdRequest(popupUri))) {
                                handled = true;
                                child.post(child::destroy);
                                return true;
                            }
                        } catch (Exception ignored) {}
                        if (isPdfCandidate(url, null)) {
                            handled = true;
                            openPdfTask(url, guessPdfName(url, null));
                            child.post(child::destroy);
                            return true;
                        }
                        if (isOfficeCandidate(url, null)) {
                            handled = true;
                            openOfficeTask(url, guessOfficeName(url, null, null));
                            child.post(child::destroy);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        if (handle(url)) return true;
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            handled = true;
                            targetWebView.loadUrl(url);
                            child.post(child::destroy);
                            return true;
                        }
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                        if (handle(url)) v.stopLoading();
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(child);
                resultMsg.sendToTarget();
                return true;
            }
        });
        targetWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            String mt = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
            if (mt.contains("application/pdf") || isPdfCandidate(url, mt)) {
                openPdfTask(url, guessPdfName(url, contentDisposition));
            } else if (isOfficeCandidate(url, mt)) {
                openOfficeTask(url, guessOfficeName(url, contentDisposition, mt));
            } else {
                openExternal(url);
            }
        });
        targetWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                browserPageZoom.put(view, 1.0f);
                applyBrowserPageZoom(view, 1.0f);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    if (isPdfCandidate(uri.toString(), null)) {
                        openPdfTask(uri.toString(), guessPdfName(uri.toString(), null));
                        return true;
                    }
                    if (isOfficeCandidate(uri.toString(), null)) {
                        openOfficeTask(uri.toString(), guessOfficeName(uri.toString(), null, null));
                        return true;
                    }
                    return false;
                }
                return openExternal(uri.toString());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectPdfHook(view);
                injectBrowserCompatibility(view);
                if (adBlockEnabled) injectNormalAdCleanup(view);
                if (hardAdBlockEnabled) injectHardAdCleanup(view);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) {
                    Uri requestUri = request.getUrl();
                    boolean blockNormal = adBlockEnabled && isBlocked(requestUri);
                    boolean blockHard = hardAdBlockEnabled && isHardAdRequest(requestUri);
                    if (blockNormal || blockHard) {
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
    }

    private void injectBrowserCompatibility(WebView view) {
        String js =
                "(function(){" +
                "try{" +
                "var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';document.head&&document.head.appendChild(m);}" +
                "if(m){var ct=m.getAttribute('content')||'';" +
                "ct=ct.replace(/user-scalable\\s*=\\s*no/ig,'user-scalable=yes')" +
                ".replace(/maximum-scale\\s*=\\s*1(?:\\.0+)?/ig,'maximum-scale=5.0');" +
                "if(!/user-scalable\\s*=/i.test(ct))ct+=(ct?', ':'')+'user-scalable=yes';" +
                "if(!/maximum-scale\\s*=/i.test(ct))ct+=(ct?', ':'')+'maximum-scale=5.0';" +
                "m.setAttribute('content',ct);}" +
                "}catch(e){}" +
                "if(window.__stsRdBridgeInstalled)return;window.__stsRdBridgeInstalled=true;" +
                "function rd(u,method){try{var a=document.createElement('a');a.href=String(u||'');" +
                "var h=(a.hostname||'').toLowerCase(),p=parseInt(a.port||'80',10);" +
                "return (h==='127.0.0.1'||h==='localhost')&&p>=11100&&p<=11120;}catch(e){return false;}}" +
                "function callNative(method,url,body,ctype){var r=STSRD.request(String(method||'GET'),String(url||'')," +
                "body==null?'':String(body),String(ctype||''));return JSON.parse(r);}" +
                "var of=window.fetch?window.fetch.bind(window):null;" +
                "if(of){window.fetch=function(input,init){var u=(typeof input==='string')?input:(input&&input.url)||'';" +
                "var method=(init&&init.method)||(input&&input.method)||'GET';" +
                "if(!rd(u,method))return of(input,init);" +
                "return new Promise(function(resolve,reject){setTimeout(function(){try{" +
                "var body=init&&init.body!=null?init.body:'';var ct='';" +
                "try{ct=(init&&init.headers&&((init.headers['Content-Type'])||(init.headers['content-type'])))||'';}catch(x){}" +
                "var z=callNative(method,u,body,ct);if(!z||!z.ok){reject(new TypeError((z&&z.error)||'RD Service unavailable'));return;}" +
                "resolve(new Response(z.body||'',{status:z.status||200,statusText:z.statusText||'OK'," +
                "headers:{'Content-Type':z.contentType||'text/xml'}}));}catch(e){reject(e);}},0);});};}" +
                "var O=window.XMLHttpRequest;" +
                "if(O&&window.Proxy){window.XMLHttpRequest=function(){var x=new O();var s={rd:false,method:'GET',url:'',async:true," +
                "body:'',ctype:'',readyState:0,status:0,statusText:'',responseText:'',response:'',responseURL:''," +
                "handlers:{},listeners:{},headers:{}};" +
                "function fire(n){var ev={type:n,target:px,currentTarget:px};" +
                "try{if(typeof s.handlers['on'+n]==='function')s.handlers['on'+n].call(px,ev);}catch(e){}" +
                "var ls=s.listeners[n]||[];for(var i=0;i<ls.length;i++){try{ls[i].call(px,ev);}catch(e){}}}" +
                "function complete(z){if(z&&z.ok){s.status=z.status||200;s.statusText=z.statusText||'OK';s.responseText=z.body||'';" +
                "s.response=s.responseText;s.responseURL=s.url;s.readyState=4;s.headers={'content-type':z.contentType||'text/xml'};" +
                "fire('readystatechange');fire('load');fire('loadend');}else{s.status=0;s.readyState=4;" +
                "fire('readystatechange');fire('error');fire('loadend');}}" +
                "var px=new Proxy(x,{get:function(t,p){if(p==='open')return function(method,url,async,user,password){" +
                "s.method=String(method||'GET').toUpperCase();s.url=String(url||'');s.async=async!==false;s.rd=rd(s.url,s.method);" +
                "if(!s.rd)return t.open(method,url,async,user,password);s.readyState=1;fire('readystatechange');};" +
                "if(p==='send')return function(body){if(!s.rd)return t.send(body);s.body=body==null?'':String(body);" +
                "var run=function(){try{complete(callNative(s.method,s.url,s.body,s.ctype));}catch(e){complete({ok:false,error:String(e)});}};" +
                "if(s.async)setTimeout(run,0);else run();};" +
                "if(p==='setRequestHeader')return function(k,v){if(!s.rd)return t.setRequestHeader(k,v);s.headers[String(k).toLowerCase()]=String(v);" +
                "if(String(k).toLowerCase()==='content-type')s.ctype=String(v);};" +
                "if(p==='getResponseHeader')return function(k){if(!s.rd)return t.getResponseHeader(k);return s.headers[String(k).toLowerCase()]||null;};" +
                "if(p==='getAllResponseHeaders')return function(){if(!s.rd)return t.getAllResponseHeaders();return 'content-type: '+(s.headers['content-type']||'text/xml')+'\\r\\n';};" +
                "if(p==='addEventListener')return function(n,fn){if(!s.listeners[n])s.listeners[n]=[];s.listeners[n].push(fn);try{t.addEventListener(n,fn);}catch(e){}};" +
                "if(p==='removeEventListener')return function(n,fn){var a=s.listeners[n]||[];var q=a.indexOf(fn);if(q>=0)a.splice(q,1);try{t.removeEventListener(n,fn);}catch(e){}};" +
                "if(p==='abort')return function(){if(!s.rd)return t.abort();s.readyState=0;fire('abort');fire('loadend');};" +
                "if(/^on/.test(String(p)))return s.handlers[p];" +
                "if(s.rd&&(p==='readyState'||p==='status'||p==='statusText'||p==='responseText'||p==='response'||p==='responseURL'))return s[p];" +
                "var v=t[p];return typeof v==='function'?v.bind(t):v;}," +
                "set:function(t,p,v){if(/^on/.test(String(p))){s.handlers[p]=v;try{t[p]=v;}catch(e){}return true;}" +
                "if(s.rd&&(p==='responseType'||p==='timeout'||p==='withCredentials')){s[p]=v;return true;}try{t[p]=v;}catch(e){}return true;}});" +
                "return px;};window.XMLHttpRequest.prototype=O.prototype;}" +
                "})();";
        try { view.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private class RdBridge {
        @JavascriptInterface
        public String request(String method, String url, String body, String contentType) {
            JSONObject result = new JSONObject();
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                int port = uri.getPort();
                if (host == null ||
                        !(host.equalsIgnoreCase("127.0.0.1") || host.equalsIgnoreCase("localhost")) ||
                        port < 11100 || port > 11120) {
                    result.put("ok", false);
                    result.put("error", "Blocked non-RD address");
                    return result.toString();
                }

                String verb = method == null ? "GET" : method.trim().toUpperCase(Locale.ROOT);
                if (verb.isEmpty()) verb = "GET";
                String target;
                if ("RDSERVICE".equals(verb) || "RDERVICE".equals(verb)) {
                    target = "*";
                } else {
                    target = uri.getEncodedPath();
                    if (target == null || target.isEmpty()) target = "/";
                    if (uri.getEncodedQuery() != null) target += "?" + uri.getEncodedQuery();
                }

                byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
                String ct = (contentType == null || contentType.trim().isEmpty()) ? "text/xml" : contentType.trim();

                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1", port), 2500);
                    socket.setSoTimeout(30000);

                    OutputStream out = socket.getOutputStream();
                    StringBuilder req = new StringBuilder();
                    req.append(verb).append(" ").append(target).append(" HTTP/1.1\r\n");
                    req.append("Host: 127.0.0.1:").append(port).append("\r\n");
                    req.append("Connection: close\r\n");
                    req.append("Accept: text/xml, application/xml, */*\r\n");
                    if ("RDSERVICE".equals(verb) || "RDERVICE".equals(verb)) {
                        req.append("EXT: STS Fast Browser\r\n");
                    }
                    if (payload.length > 0 || "POST".equals(verb)) {
                        req.append("Content-Type: ").append(ct).append("\r\n");
                        req.append("Content-Length: ").append(payload.length).append("\r\n");
                    }
                    req.append("\r\n");
                    out.write(req.toString().getBytes(StandardCharsets.ISO_8859_1));
                    if (payload.length > 0) out.write(payload);
                    out.flush();

                    InputStream in = socket.getInputStream();
                    ByteArrayOutputStream all = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) all.write(buf, 0, n);
                    byte[] raw = all.toByteArray();

                    int split = headerEnd(raw);
                    if (split < 0) throw new IllegalStateException("Invalid RD response");
                    String headers = new String(raw, 0, split, StandardCharsets.ISO_8859_1);
                    byte[] responseBody = Arrays.copyOfRange(raw, split + 4, raw.length);
                    if (headers.toLowerCase(Locale.ROOT).contains("transfer-encoding: chunked")) {
                        responseBody = decodeChunked(responseBody);
                    }

                    String[] lines = headers.split("\\r?\\n");
                    int status = 200;
                    String statusText = "OK";
                    if (lines.length > 0) {
                        String[] p = lines[0].split(" ", 3);
                        if (p.length > 1) {
                            try { status = Integer.parseInt(p[1]); } catch (Exception ignored) {}
                        }
                        if (p.length > 2) statusText = p[2];
                    }

                    String responseCt = "text/xml";
                    for (String line : lines) {
                        int colon = line.indexOf(':');
                        if (colon > 0 && "content-type".equalsIgnoreCase(line.substring(0, colon).trim())) {
                            responseCt = line.substring(colon + 1).trim();
                        }
                    }

                    result.put("ok", status >= 200 && status < 400);
                    result.put("status", status);
                    result.put("statusText", statusText);
                    result.put("contentType", responseCt);
                    result.put("body", new String(responseBody, StandardCharsets.UTF_8));
                    return result.toString();
                }
            } catch (Exception e) {
                try {
                    result.put("ok", false);
                    result.put("status", 0);
                    result.put("error", e.getClass().getSimpleName() + ": " +
                            (e.getMessage() == null ? "RD Service unavailable" : e.getMessage()));
                } catch (Exception ignored) {}
                return result.toString();
            }
        }
    }

    private int headerEnd(byte[] data) {
        for (int i = 0; i + 3 < data.length; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' &&
                    data[i + 2] == '\r' && data[i + 3] == '\n') return i;
        }
        return -1;
    }

    private byte[] decodeChunked(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = 0;
        while (pos < data.length) {
            int lineEnd = -1;
            for (int i = pos; i + 1 < data.length; i++) {
                if (data[i] == '\r' && data[i + 1] == '\n') {
                    lineEnd = i;
                    break;
                }
            }
            if (lineEnd < 0) break;
            String sizeLine = new String(data, pos, lineEnd - pos, StandardCharsets.US_ASCII).trim();
            int semi = sizeLine.indexOf(';');
            if (semi >= 0) sizeLine = sizeLine.substring(0, semi);
            int size = Integer.parseInt(sizeLine.trim(), 16);
            pos = lineEnd + 2;
            if (size == 0) break;
            if (pos + size > data.length) throw new IllegalStateException("Invalid chunk");
            out.write(data, pos, size);
            pos += size + 2;
        }
        return out.toByteArray();
    }

    private boolean isPdfCandidate(String url, String typeHint) {
        if (typeHint != null && typeHint.toLowerCase(Locale.ROOT).contains("application/pdf")) return true;
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        String clean = lower;
        int h = clean.indexOf('#');
        if (h >= 0) clean = clean.substring(0, h);
        if (clean.endsWith(".pdf") || clean.contains(".pdf?") || clean.contains(".pdf&")) return true;
        return lower.contains("/pdf/") ||
                lower.contains("format=pdf") ||
                lower.contains("type=pdf") ||
                lower.contains("contenttype=application%2fpdf") ||
                lower.contains("content-type=application%2fpdf") ||
                lower.contains("application/pdf");
    }

    private void injectPdfHook(WebView view) {
        String js = "(function(){"
                + "if(window.__stsPdfHook)return;window.__stsPdfHook=1;"
                + "document.addEventListener('click',function(e){"
                + "var a=e.target&&e.target.closest?e.target.closest('a'):null;if(!a)return;"
                + "var h=a.href||'';var t=(a.getAttribute('type')||'').toLowerCase();"
                + "var d=(a.getAttribute('download')||'').toLowerCase();"
                + "var hl=h.toLowerCase();"
                + "var p=t.indexOf('application/pdf')>=0||d.endsWith('.pdf')||/\\.pdf([?#&]|$)/i.test(h)||hl.indexOf('format=pdf')>=0||hl.indexOf('type=pdf')>=0;"
                + "if(p&&h.indexOf('http')===0){e.preventDefault();e.stopPropagation();"
                + "var n=a.getAttribute('download')||a.textContent||'Document.pdf';"
                + "try{STSPdf.openPdf(h,n);}catch(x){}}"
                + "},true);"
                + "var es=document.querySelectorAll('embed[type=\"application/pdf\"],object[type=\"application/pdf\"],iframe[src*=\".pdf\"]');"
                + "for(var i=0;i<es.length;i++){var u=es[i].src||es[i].data||'';if(u.indexOf('http')===0){try{STSPdf.openPdf(u,'Document.pdf');}catch(x){}break;}}"
                + "})();";
        try { view.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private class PdfBridge {
        @JavascriptInterface
        public void openPdf(String url, String name) {
            if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
            runOnUiThread(() -> openPdfTask(url, guessPdfName(url, name)));
        }
    }

    private String guessPdfName(String url, String contentDisposition) {
        try {
            String guessed = URLUtil.guessFileName(url, contentDisposition, "application/pdf");
            if (guessed != null && !guessed.trim().isEmpty()) return guessed;
        } catch (Exception ignored) {}
        return "Document.pdf";
    }

    private void openPdfTask(String url, String name) {
        try {
            Intent intent = new Intent(this, PdfViewerActivity.class);
            intent.putExtra("pdf_url", url);
            intent.putExtra("pdf_name", name);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) intent.putExtra("pdf_cookie", cookie);
            intent.putExtra("pdf_user_agent", webView.getSettings().getUserAgentString());
            intent.putExtra("pdf_slot", activeSlot);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "PDF open नहीं हो पाया", Toast.LENGTH_SHORT).show();
        }
    }


    private boolean isOfficeCandidate(String url, String typeHint) {
        String type = typeHint == null ? "" : typeHint.toLowerCase(Locale.ROOT);
        if (type.contains("wordprocessingml") || type.contains("spreadsheetml") ||
                type.contains("presentationml") || type.contains("application/msword") ||
                type.contains("application/vnd.ms-word") || type.contains("application/vnd.ms-excel") ||
                type.contains("application/excel") || type.contains("application/vnd.ms-powerpoint") ||
                type.contains("text/csv") || type.contains("text/plain") || type.contains("application/rtf") ||
                type.contains("text/rtf") || type.contains("opendocument") || type.contains("application/ofd")) {
            return true;
        }
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        int hash = lower.indexOf('#');
        if (hash >= 0) lower = lower.substring(0, hash);
        return lower.endsWith(".docx") || lower.endsWith(".doc") ||
                lower.endsWith(".xlsx") || lower.endsWith(".xls") ||
                lower.endsWith(".pptx") || lower.endsWith(".ppt") ||
                lower.endsWith(".csv") || lower.endsWith(".txt") || lower.endsWith(".rtf") ||
                lower.endsWith(".odt") || lower.endsWith(".ods") || lower.endsWith(".odp") || lower.endsWith(".ofd") ||
                lower.contains(".docx?") || lower.contains(".doc?") ||
                lower.contains(".xlsx?") || lower.contains(".xls?") ||
                lower.contains(".pptx?") || lower.contains(".ppt?") ||
                lower.contains(".csv?") || lower.contains(".txt?") || lower.contains(".rtf?") ||
                lower.contains(".odt?") || lower.contains(".ods?") || lower.contains(".odp?") || lower.contains(".ofd?") ||
                lower.contains(".docx&") || lower.contains(".doc&") ||
                lower.contains(".xlsx&") || lower.contains(".xls&") ||
                lower.contains(".pptx&") || lower.contains(".ppt&") ||
                lower.contains(".csv&") || lower.contains(".txt&") || lower.contains(".rtf&") ||
                lower.contains(".odt&") || lower.contains(".ods&") || lower.contains(".odp&") || lower.contains(".ofd&");
    }

    private String guessOfficeName(String url, String contentDisposition, String mimeType) {
        try {
            String mime = mimeType;
            if (TextUtils.isEmpty(mime)) {
                String l = url == null ? "" : url.toLowerCase(Locale.ROOT);
                if (l.contains(".xlsx")) mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                else if (l.contains(".xls")) mime = "application/vnd.ms-excel";
                else if (l.contains(".docx")) mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                else if (l.contains(".doc")) mime = "application/msword";
                else if (l.contains(".pptx")) mime = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                else if (l.contains(".ppt")) mime = "application/vnd.ms-powerpoint";
                else if (l.contains(".csv")) mime = "text/csv";
                else if (l.contains(".txt")) mime = "text/plain";
                else if (l.contains(".rtf")) mime = "application/rtf";
                else if (l.contains(".odt")) mime = "application/vnd.oasis.opendocument.text";
                else if (l.contains(".ods")) mime = "application/vnd.oasis.opendocument.spreadsheet";
                else if (l.contains(".odp")) mime = "application/vnd.oasis.opendocument.presentation";
                else if (l.contains(".ofd")) mime = "application/ofd";
                else mime = "application/octet-stream";
            }
            String guessed = URLUtil.guessFileName(url, contentDisposition, mime);
            if (!TextUtils.isEmpty(guessed)) return guessed;
        } catch (Exception ignored) {}
        return "Document.docx";
    }

    private void openOfficeTask(String url, String name) {
        try {
            Intent intent = new Intent(this, OfficeViewerActivity.class);
            intent.putExtra("office_url", url);
            intent.putExtra("office_name", name);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) intent.putExtra("office_cookie", cookie);
            intent.putExtra("office_user_agent", webView.getSettings().getUserAgentString());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Word/Excel file open नहीं हो पाई", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean openExternal(String url) {
        try {
            if (url.startsWith("intent:")) {
                Intent parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                try {
                    startActivity(parsed);
                    return true;
                } catch (ActivityNotFoundException e) {
                    String fallback = parsed.getStringExtra("browser_fallback_url");
                    if (fallback != null) webView.loadUrl(fallback);
                    return true;
                }
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Link open नहीं हो पाया", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private void injectNormalAdCleanup(WebView view) {
        if (view == null) return;
        String js =
                "(function(){" +
                "if(window.__stsNormalAdBlockInstalled){try{window.__stsNormalAdClean&&window.__stsNormalAdClean();}catch(e){}return;}" +
                "window.__stsNormalAdBlockInstalled=true;" +
                "var sel=[" +
                "'ins.adsbygoogle','.adsbygoogle','[id^=\"google_ads_\"]','[id^=\"div-gpt-ad\"]'," +
                "'iframe[src*=\"doubleclick.net\"]','iframe[src*=\"googlesyndication.com\"]'," +
                "'iframe[src*=\"googleadservices.com\"]','[data-ad-client]','[data-ad-slot]'," +
                "'amp-ad','.advertisement','.ad-container','.ad-banner','.ad-wrapper'" +
                "];" +
                "window.__stsNormalAdClean=function(){for(var i=0;i<sel.length;i++){var n=[];try{n=document.querySelectorAll(sel[i]);}catch(e){}" +
                "for(var j=0;j<n.length;j++){try{n[j].style.setProperty('display','none','important');" +
                "n[j].style.setProperty('visibility','hidden','important');}catch(e){}}}};" +
                "window.__stsNormalAdClean();" +
                "try{new MutationObserver(function(){window.__stsNormalAdClean();}).observe(document.documentElement||document," +
                "{childList:true,subtree:true});}catch(e){}" +
                "})();";
        try { view.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private void injectHardAdCleanup(WebView view) {
        if (view == null) return;
        String js =
                "(function(){" +
                "if(window.__stsHardAdBlockInstalled){try{window.__stsHardAdClean&&window.__stsHardAdClean();}catch(e){}return;}" +
                "window.__stsHardAdBlockInstalled=true;" +
                "var sel=[" +
                "'amp-embed[type=\"taboola\"]','.sponsored','.promoted','.promoted-content'," +
                "'#player-ads','ytd-ad-slot-renderer','ytd-display-ad-renderer','ytd-promoted-sparkles-web-renderer'," +
                "'ytd-in-feed-ad-layout-renderer','ytd-promoted-video-renderer','ytm-promoted-video-renderer'," +
                "'ytm-companion-ad-renderer','ytm-display-ad-renderer','ytm-ad-slot-renderer','tp-yt-paper-dialog ytd-mealbar-promo-renderer'" +
                "];" +
                "function hideAds(){for(var i=0;i<sel.length;i++){var n=[];try{n=document.querySelectorAll(sel[i]);}catch(e){}" +
                "for(var j=0;j<n.length;j++){try{n[j].style.setProperty('display','none','important');" +
                "n[j].style.setProperty('visibility','hidden','important');n[j].setAttribute('aria-hidden','true');}catch(e){}}}}" +
                "function clickSkip(){var q=['.ytp-ad-skip-button-modern','.ytp-ad-skip-button','.ytp-skip-ad-button'," +
                "'button.ytp-ad-skip-button','button[id*=skip]'];for(var i=0;i<q.length;i++){var b=null;try{b=document.querySelector(q[i]);}catch(e){}" +
                "if(b&&b.offsetParent!==null){try{b.click();return true;}catch(e){}}}return false;}" +
                "function youtube(){try{" +
                "var p=document.querySelector('.html5-video-player');if(!p)return;" +
                "var ad=p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting');" +
                "var v=p.querySelector('video');" +
                "if(ad&&v){" +
                "if(!window.__stsYtAdActive){window.__stsYtAdActive=true;window.__stsYtPrevMuted=!!v.muted;" +
                "window.__stsYtPrevRate=(v.playbackRate&&isFinite(v.playbackRate))?v.playbackRate:1;}" +
                "clickSkip();" +
                "try{v.muted=true;}catch(e){}" +
                "try{if(v.playbackRate<8)v.playbackRate=8;}catch(e){}" +
                "}else if(window.__stsYtAdActive&&v){" +
                "window.__stsYtAdActive=false;" +
                "try{v.playbackRate=window.__stsYtPrevRate||1;}catch(e){}" +
                "try{v.muted=!!window.__stsYtPrevMuted;}catch(e){}" +
                "}" +
                "}catch(e){}}" +
                "window.__stsHardAdClean=function(){hideAds();youtube();};" +
                "window.__stsHardAdClean();" +
                "try{new MutationObserver(function(){window.__stsHardAdClean();}).observe(document.documentElement||document," +
                "{childList:true,subtree:true,attributes:true,attributeFilter:['class']});}catch(e){}" +
                "try{window.__stsHardAdTimer=setInterval(window.__stsHardAdClean,500);}catch(e){}" +
                "})();";
        try { view.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private boolean isHardAdRequest(Uri uri) {
        if (uri == null) return false;

        String host = uri.getHost();
        String path = uri.getEncodedPath();
        String query = uri.getEncodedQuery();
        host = host == null ? "" : host.toLowerCase(Locale.ROOT);
        path = path == null ? "" : path.toLowerCase(Locale.ROOT);
        query = query == null ? "" : query.toLowerCase(Locale.ROOT);

        // YouTube can keep the player waiting on a black frame when its own
        // ad endpoints are cancelled. Let YouTube requests complete and handle
        // its web ads in-page by auto-skip + safe fast playback instead.
        if (host.endsWith("googlevideo.com") ||
                host.equals("youtube.com") || host.endsWith(".youtube.com") ||
                host.equals("youtu.be") || host.endsWith(".youtu.be")) {
            return false;
        }

        // Aggressive same-host filtering remains enabled for other websites.
        return path.contains("/gampad/") ||
                path.contains("/pagead/") ||
                path.contains("/adserver/") ||
                path.contains("/adservice/") ||
                path.contains("/prebid/") ||
                path.contains("/vast/") ||
                path.contains("/vmap/") ||
                query.contains("google_ad_client=") ||
                query.contains("ad_slot=") ||
                query.contains("adunit=");
    }

    private boolean isBlocked(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String blocked : blockedHosts) {
            if (blocked.endsWith(".")) {
                if (host.startsWith(blocked)) return true;
            } else if (host.equals(blocked) || host.endsWith("." + blocked)) {
                return true;
            }
        }
        return false;
    }

    private void showSitePopup(View anchor, int slot) {
        showWebView(slot);
        if (!(slot == 1 && slot1DocumentsHome)) ensureSlotPageLoaded(slot);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(5), dp(4), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        box.setBackground(bg);

        int popupWidth = Math.max(anchor.getWidth(), dp(180));
        PopupWindow popup = new PopupWindow(box, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));
        popup.setAnimationStyle(android.R.style.Animation_Dialog);

        if (slot == 1) {
            TextView phoneData = makePopupItem("Phone Data");
            phoneData.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            phoneData.setOnClickListener(v -> {
                popup.dismiss();
                showDocumentsHome();
            });
            box.addView(phoneData, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        }

        for (Site site : getSites(slot)) {
            TextView item = makePopupItem(site.name);
            item.setOnClickListener(v -> {
                popup.dismiss();
                selectSite(slot, site);
            });
            box.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        }

        TextView add = makePopupItem("＋  Add website");
        add.setTextColor(Color.parseColor("#355C62"));
        add.setOnClickListener(v -> {
            popup.dismiss();
            showAddWebsiteDialog(slot);
        });
        box.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        popup.showAsDropDown(anchor, 0, dp(2));
    }

    private TextView makePopupItem(String text) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextSize(15);
        item.setTextColor(Color.parseColor("#172326"));
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(14), 0, dp(10), 0);
        GradientDrawable base = new GradientDrawable();
        base.setColor(Color.WHITE);
        base.setCornerRadius(dp(10));
        item.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#334F8F8B")), base, null));
        applyPressAnimation(item);
        return item;
    }

    private void selectSite(int slot, Site site) {
        if (site == null) return;
        if (site.hasPassword()) {
            showSitePasswordDialog(site, () -> performSelectSite(slot, site));
        } else {
            performSelectSite(slot, site);
        }
    }

    private WebView webViewForSlot(int slot) {
        return slot == 2 ? webView2 : webView1;
    }

    private void showWebView(int slot) {
        activeSlot = slot == 2 ? 2 : 1;
        webView = webViewForSlot(activeSlot);
        if (webView1 != null) webView1.setVisibility(activeSlot == 1 ? View.VISIBLE : View.GONE);
        if (webView2 != null) webView2.setVisibility(activeSlot == 2 ? View.VISIBLE : View.GONE);
        updateSlotLabels();
    }

    private void ensureSlotPageLoaded(int slot) {
        WebView target = webViewForSlot(slot);
        if (target == null || target.getUrl() != null) return;

        String selectedUrl = slot == 2 ? slot2Url : slot1Url;
        Site selected = findSiteByUrl(slot, selectedUrl);
        Runnable load = () -> {
            if (target.getUrl() == null) target.loadUrl(selectedUrl);
        };
        if (selected != null && selected.hasPassword()) {
            showSitePasswordDialog(selected, load);
        } else {
            load.run();
        }
    }

    private void performSelectSite(int slot, Site site) {
        String previousSelectedUrl = slot == 2 ? slot2Url : slot1Url;
        boolean sameSelection = previousSelectedUrl != null &&
                previousSelectedUrl.equalsIgnoreCase(site.url);
        boolean wasDocumentsHome = slot == 1 && slot1DocumentsHome;

        if (slot == 1) {
            slot1DocumentsHome = false;
            documentsLoadGeneration++;
            slot1Name = site.name;
            slot1Url = site.url;
        } else {
            slot2Name = site.name;
            slot2Url = site.url;
        }
        saveSlots();
        showWebView(slot);

        WebView target = webViewForSlot(slot);
        // Selecting the same dropdown site is only a slot switch: keep its exact live page,
        // history, forms and scroll state. A different site selection is an explicit navigation.
        if (wasDocumentsHome || !sameSelection || target.getUrl() == null) {
            target.animate().alpha(0.82f).setDuration(80).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    target.loadUrl(site.url);
                    target.animate().alpha(1f).setDuration(160).setListener(null).start();
                }
            }).start();
        }
    }

    private void loadInitialPage() {
        showDocumentsHome();
    }

    private void showSitePasswordDialog(Site site, Runnable onSuccess) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(18), dp(22), dp(12));

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.parseColor("#F7FAF9"));
        panelBg.setCornerRadius(dp(18));
        panelBg.setStroke(dp(1), Color.parseColor("#B7CBC8"));
        body.setBackground(panelBg);

        TextView lockBadge = new TextView(this);
        lockBadge.setText("SECURE WEBSITE");
        lockBadge.setTextColor(Color.parseColor("#355C62"));
        lockBadge.setTextSize(11);
        lockBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        lockBadge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#D9F0EE"));
        badgeBg.setCornerRadius(dp(12));
        lockBadge.setBackground(badgeBg);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(142), dp(28));
        badgeLp.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(lockBadge, badgeLp);

        TextView title = new TextView(this);
        title.setText(site.name);
        title.setTextColor(Color.parseColor("#172326"));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, dp(4));
        body.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("Website खोलने के लिए password डालें");
        subtitle.setTextColor(Color.parseColor("#5B676A"));
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(14));
        body.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout passRow = new LinearLayout(this);
        passRow.setOrientation(LinearLayout.HORIZONTAL);
        passRow.setGravity(Gravity.CENTER_VERTICAL);
        passRow.setPadding(dp(4), 0, dp(4), 0);
        GradientDrawable passBg = new GradientDrawable();
        passBg.setColor(Color.WHITE);
        passBg.setCornerRadius(dp(12));
        passBg.setStroke(dp(1), Color.parseColor("#8FB5B1"));
        passRow.setBackground(passBg);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Password");
        input.setTextColor(Color.parseColor("#172326"));
        input.setHintTextColor(Color.parseColor("#7A8587"));
        input.setTextSize(16);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(14), 0, dp(8), 0);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());

        TextView show = new TextView(this);
        show.setText("SHOW");
        show.setTextColor(Color.parseColor("#4F8F8B"));
        show.setTextSize(12);
        show.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        show.setGravity(Gravity.CENTER);
        show.setPadding(dp(10), 0, dp(10), 0);
        show.setClickable(true);
        show.setFocusable(true);

        final boolean[] visible = {false};
        show.setOnClickListener(v -> {
            int pos = input.getSelectionStart();
            visible[0] = !visible[0];
            if (visible[0]) {
                input.setTransformationMethod(null);
                show.setText("HIDE");
            } else {
                input.setTransformationMethod(PasswordTransformationMethod.getInstance());
                show.setText("SHOW");
            }
            if (pos >= 0) input.setSelection(Math.min(pos, input.length()));
        });

        passRow.addView(input, new LinearLayout.LayoutParams(0, dp(56), 1f));
        passRow.addView(show, new LinearLayout.LayoutParams(dp(66), dp(56)));
        body.addView(passRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        TextView error = new TextView(this);
        error.setTextColor(Color.parseColor("#8B3D3D"));
        error.setTextSize(12);
        error.setPadding(dp(4), dp(5), dp(4), 0);
        error.setVisibility(View.GONE);
        body.addView(error, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(6), 0, 0);

        TextView cancel = makePasswordAction("CANCEL", false);
        TextView open = makePasswordAction("OPEN", true);
        LinearLayout.LayoutParams actionLp1 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        actionLp1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams actionLp2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        actionLp2.setMargins(dp(6), 0, 0, 0);
        actions.addView(cancel, actionLp1);
        actions.addView(open, actionLp2);
        body.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(body)
                .create();

        cancel.setOnClickListener(v -> dialog.dismiss());
        open.setOnClickListener(v -> {
            String entered = input.getText().toString();
            if (site.passwordHash.equals(hashPassword(entered))) {
                dialog.dismiss();
                if (onSuccess != null) onSuccess.run();
            } else {
                error.setText("Password गलत है");
                error.setVisibility(View.VISIBLE);
                input.requestFocus();
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            open.performClick();
            return true;
        });

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            input.requestFocus();
        });
        dialog.show();
    }

    private TextView makePasswordAction(String text, boolean primary) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(primary ? Color.WHITE : Color.parseColor("#355C62"));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? Color.parseColor("#4F8F8B") : Color.parseColor("#E7EFEE"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), primary ? Color.parseColor("#3D7774") : Color.parseColor("#AEC7C4"));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(primary ? 0x33FFFFFF : 0x22355C62), bg, null));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private boolean handleBrowserIntent(Intent intent) {
        if (intent == null) return false;
        String url = intent.getStringExtra("browser_open_url");
        if (TextUtils.isEmpty(url)) return false;

        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            return false;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) return false;

        int slot = intent.getIntExtra("browser_slot", activeSlot);
        if (slot != 2) slot = 1;
        if (slot == 1) {
            slot1DocumentsHome = false;
            documentsLoadGeneration++;
        }
        showWebView(slot);
        WebView target = webViewForSlot(slot);
        target.loadUrl(url);
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleBrowserIntent(intent);
    }

    private void goHome(int slot) {
        if (slot == 1) {
            showDocumentsHome();
            return;
        }
        showWebView(2);
        WebView target = webViewForSlot(2);
        target.loadUrl(slot2Url);
    }

    private void refreshSlot(int slot) {
        if (slot == 1 && slot1DocumentsHome) {
            showDocumentsHome();
            return;
        }
        showWebView(slot);
        WebView target = webViewForSlot(slot);
        if (target.getUrl() == null) {
            ensureSlotPageLoaded(slot);
        } else {
            target.reload();
        }
    }

    private void showMainMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor, Gravity.END);
        pm.getMenu().add(adBlockEnabled ? "Ad Blocker: ON" : "Ad Blocker: OFF");
        pm.getMenu().add(hardAdBlockEnabled ? "Hard Ad Blocker: ON" : "Hard Ad Blocker: OFF");
        pm.getMenu().add("Add Website");
        pm.getMenu().add("Manage Websites");
        pm.getMenu().add("About");
        pm.setOnMenuItemClickListener(item -> {
            String t = String.valueOf(item.getTitle());
            if (t.startsWith("Hard Ad Blocker")) {
                setHardAdBlockEnabled(!hardAdBlockEnabled, true);
                return true;
            }
            if (t.startsWith("Ad Blocker")) {
                setAdBlockEnabled(!adBlockEnabled, true);
                return true;
            }
            if (t.equals("Add Website")) {
                showAddWebsiteDialog(activeSlot);
                return true;
            }
            if (t.equals("Manage Websites")) {
                showManageSitesDialog();
                return true;
            }
            if (t.equals("About")) {
                showAboutDialog();
                return true;
            }
            return false;
        });
        pm.show();
    }

    private void showAddWebsiteDialog(int slot) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(8), dp(20), 0);

        EditText name = new EditText(this);
        name.setHint("Website name");
        name.setSingleLine(true);
        EditText url = new EditText(this);
        url.setHint("URL (example.com)");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        body.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        body.addView(url, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Website")
                .setView(body)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String u = normalizeUrl(url.getText().toString().trim());
            if (n.isEmpty() || u == null) {
                Toast.makeText(this, "Name और सही URL डालें", Toast.LENGTH_SHORT).show();
                return;
            }
            Site s = new Site(n, u, "");
            List<Site> target = getSites(slot);
            if (containsUrl(target, u)) {
                Toast.makeText(this, "यह website इस dropdown में पहले से है", Toast.LENGTH_SHORT).show();
                return;
            }
            target.add(s);
            saveSites(slot);
            dialog.dismiss();
            selectSite(slot, s);
        }));
        dialog.show();
    }

    private void showManageSitesDialog() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(8), dp(10), dp(8));

        addManageSection(list, "D1 Websites", 1);
        addManageSection(list, "D2 Websites", 2);

        ScrollView sc = new ScrollView(this);
        sc.addView(list);
        new AlertDialog.Builder(this)
                .setTitle("Manage Websites")
                .setView(sc)
                .setPositiveButton("Close", null)
                .show();
    }

    private void addManageSection(LinearLayout list, String heading, int slot) {
        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextColor(Color.parseColor("#172326"));
        h.setTextSize(15);
        h.setTypeface(null, android.graphics.Typeface.BOLD);
        h.setPadding(dp(8), dp(10), dp(8), dp(5));
        list.addView(h, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        List<Site> source = getSites(slot);
        if (source.size() <= 1) {
            TextView none = new TextView(this);
            none.setText("Custom website अभी add नहीं है");
            none.setTextColor(Color.DKGRAY);
            none.setTextSize(13);
            none.setPadding(dp(12), dp(6), dp(8), dp(10));
            list.addView(none, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
            return;
        }

        for (int i = 1; i < source.size(); i++) {
            Site s = source.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(2), dp(2), dp(2));

            TextView title = new TextView(this);
            title.setText(s.name + (s.hasPassword() ? "  🔒" : ""));
            title.setTextColor(Color.parseColor("#172326"));
            title.setTextSize(13);
            title.setPadding(dp(8), dp(4), dp(6), dp(4));

            ImageButton more = new ImageButton(this);
            more.setImageResource(R.drawable.ic_more);
            more.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            more.setPadding(dp(10), dp(10), dp(10), dp(10));
            more.setBackgroundColor(Color.TRANSPARENT);
            more.setContentDescription("Website options");

            row.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
            row.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
            more.setOnClickListener(v -> showManagedSiteMenu(v, s, slot, title));

            list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        }
    }

    private void showManagedSiteMenu(View anchor, Site site, int slot, TextView titleView) {
        PopupMenu pm = new PopupMenu(this, anchor, Gravity.END);
        pm.getMenu().add("Edit");
        pm.getMenu().add(site.hasPassword() ? "Change Password" : "Set Password");
        pm.getMenu().add(slot == 1 ? "Move to D2" : "Move to D1");
        pm.getMenu().add("Remove Website");
        pm.setOnMenuItemClickListener(item -> {
            String t = String.valueOf(item.getTitle());
            if (t.equals("Edit")) {
                Runnable openEditor = () -> showEditWebsiteDialog(site, slot, titleView);
                if (site.hasPassword()) {
                    showSitePasswordDialog(site, openEditor);
                } else {
                    openEditor.run();
                }
                return true;
            }
            if (t.equals("Set Password") || t.equals("Change Password")) {
                showSetSitePasswordDialog(site, slot);
                return true;
            }
            if (t.startsWith("Move to D")) {
                moveSite(site, slot);
                return true;
            }
            if (t.equals("Remove Website")) {
                confirmDeleteSite(site, slot);
                return true;
            }
            return false;
        });
        pm.show();
    }

    private void showEditWebsiteDialog(Site site, int slot, TextView titleView) {
        if (site == null) return;

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(8), dp(20), 0);

        EditText name = new EditText(this);
        name.setHint("Website name");
        name.setSingleLine(true);
        name.setText(site.name);

        EditText url = new EditText(this);
        url.setHint("URL (example.com)");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(site.url);

        body.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        body.addView(url, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Website")
                .setView(body)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newName = name.getText().toString().trim();
            String newUrl = normalizeUrl(url.getText().toString().trim());
            if (newName.isEmpty() || newUrl == null) {
                Toast.makeText(this, "Name और सही URL डालें", Toast.LENGTH_SHORT).show();
                return;
            }

            for (Site other : getSites(slot)) {
                if (other != site && newUrl.equalsIgnoreCase(other.url)) {
                    Toast.makeText(this, "यह website इस dropdown में पहले से है", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            String oldUrl = site.url;
            site.name = newName;
            site.url = newUrl;
            saveSites(slot);

            if (slot == 1 && oldUrl.equals(slot1Url)) {
                slot1Name = newName;
                slot1Url = newUrl;
            } else if (slot == 2 && oldUrl.equals(slot2Url)) {
                slot2Name = newName;
                slot2Url = newUrl;
            }
            saveSlots();
            updateSlotLabels();

            if (titleView != null) {
                titleView.setText(site.name + (site.hasPassword() ? "  🔒" : ""));
            }

            dialog.dismiss();
            Toast.makeText(this, "Website updated", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void showSetSitePasswordDialog(Site site, int slot) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(4), dp(18), 0);

        EditText pass1 = new EditText(this);
        pass1.setHint("New password");
        pass1.setSingleLine(true);
        pass1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText pass2 = new EditText(this);
        pass2.setHint("Confirm password");
        pass2.setSingleLine(true);
        pass2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        body.addView(pass1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        body.addView(pass2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(site.hasPassword() ? "Change Password" : "Set Password")
                .setView(body)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String p1 = pass1.getText().toString();
            String p2 = pass2.getText().toString();
            if (p1.length() < 4) {
                pass1.setError("कम से कम 4 अक्षर");
                return;
            }
            if (!p1.equals(p2)) {
                pass2.setError("Password match नहीं है");
                return;
            }
            site.passwordHash = hashPassword(p1);
            saveSites(slot);
            dialog.dismiss();
            Toast.makeText(this, "Password saved", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void moveSite(Site site, int fromSlot) {
        int toSlot = fromSlot == 1 ? 2 : 1;
        List<Site> from = getSites(fromSlot);
        List<Site> to = getSites(toSlot);

        if (containsUrl(to, site.url)) {
            Toast.makeText(this, "Website D" + toSlot + " में पहले से है", Toast.LENGTH_SHORT).show();
            return;
        }

        from.remove(site);
        to.add(site);
        if (fromSlot == 1 && slot1Url.equals(site.url)) {
            slot1Name = GOOGLE_NAME;
            slot1Url = GOOGLE_URL;
        } else if (fromSlot == 2 && slot2Url.equals(site.url)) {
            slot2Name = GOOGLE_NAME;
            slot2Url = GOOGLE_URL;
        }
        saveSites(fromSlot);
        saveSites(toSlot);
        saveSlots();
        updateSlotLabels();
        Toast.makeText(this, "Website D" + toSlot + " में move हो गई", Toast.LENGTH_SHORT).show();
    }

    private void confirmDeleteSite(Site site, int slot) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + site.name + "?")
                .setMessage("इस website को list से हटाना है?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    getSites(slot).remove(site);
                    if (slot == 1 && slot1Url.equals(site.url)) {
                        slot1Name = GOOGLE_NAME;
                        slot1Url = GOOGLE_URL;
                    }
                    if (slot == 2 && slot2Url.equals(site.url)) {
                        slot2Name = GOOGLE_NAME;
                        slot2Url = GOOGLE_URL;
                    }
                    saveSites(slot);
                    saveSlots();
                    updateSlotLabels();
                    Toast.makeText(this, "Website removed", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("STS Fast Browser")
                .setMessage("Version 1.0.27\n\nSimple • Fast • Two Quick Slots\nNormal Ad Blocker and Hard Ad Blocker are separate ON/OFF options in the common menu.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void setAdBlockEnabled(boolean enabled, boolean showToast) {
        adBlockEnabled = enabled;
        prefs.edit().putBoolean(KEY_ADBLOCK, enabled).apply();

        if (enabled) {
            if (webView1 != null) injectNormalAdCleanup(webView1);
            if (webView2 != null) injectNormalAdCleanup(webView2);
        }

        if (showToast) {
            Toast.makeText(this,
                    enabled ? "Ad Blocker ON" : "Ad Blocker OFF",
                    Toast.LENGTH_SHORT).show();
        }

        // The toggle is an explicit user action. Reload only the visible slot so
        // network-level blocking starts immediately without disturbing hidden D1/D2 state.
        if (webView != null) webView.reload();
    }

    private void setHardAdBlockEnabled(boolean enabled, boolean showToast) {
        hardAdBlockEnabled = enabled;
        prefs.edit().putBoolean(KEY_HARD_ADBLOCK, enabled).apply();

        if (enabled) {
            if (webView1 != null) injectHardAdCleanup(webView1);
            if (webView2 != null) injectHardAdCleanup(webView2);
        }

        if (showToast) {
            Toast.makeText(this,
                    enabled ? "Hard Ad Blocker ON" : "Hard Ad Blocker OFF",
                    Toast.LENGTH_SHORT).show();
        }

        if (webView != null) webView.reload();
    }

    private void updateSlotLabels() {
        if (slot1Button != null) {
            String d1Label = slot1DocumentsHome ? "Phone Data" : slot1Name;
            slot1Button.setText((activeSlot == 1 ? "● " : "") + d1Label + " ▾");
        }
        if (slot2Button != null) slot2Button.setText((activeSlot == 2 ? "● " : "") + slot2Name + " ▾");
    }

    private List<Site> getSites(int slot) {
        return slot == 2 ? sitesD2 : sitesD1;
    }

    private boolean containsUrl(List<Site> list, String url) {
        for (Site s : list) {
            if (s.url.equalsIgnoreCase(url)) return true;
        }
        return false;
    }

    private Site findSiteByUrl(int slot, String url) {
        if (url == null) return null;
        for (Site s : getSites(slot)) {
            if (s.url.equalsIgnoreCase(url)) return s;
        }
        return null;
    }

    private void loadSites() {
        sitesD1.clear();
        sitesD2.clear();
        sitesD1.add(new Site(GOOGLE_NAME, GOOGLE_URL, ""));
        sitesD2.add(new Site(GOOGLE_NAME, GOOGLE_URL, ""));

        boolean splitExists = prefs.contains(KEY_SITES_D1) || prefs.contains(KEY_SITES_D2);
        if (splitExists) {
            loadSiteList(KEY_SITES_D1, sitesD1);
            loadSiteList(KEY_SITES_D2, sitesD2);
            return;
        }

        // One-time migration from the old shared website list.
        String legacy = prefs.getString(KEY_SITES, "[]");
        try {
            JSONArray arr = new JSONArray(legacy);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String n = o.optString("name", "").trim();
                String u = normalizeUrl(o.optString("url", "").trim());
                if (!n.isEmpty() && u != null && !GOOGLE_URL.equalsIgnoreCase(u)) {
                    sitesD1.add(new Site(n, u, ""));
                    sitesD2.add(new Site(n, u, ""));
                }
            }
        } catch (Exception ignored) {}
        saveSites(1);
        saveSites(2);
        prefs.edit().putBoolean(KEY_SITES_MIGRATED, true).apply();
    }

    private void loadSiteList(String key, List<Site> target) {
        String json = prefs.getString(key, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String n = o.optString("name", "").trim();
                String u = normalizeUrl(o.optString("url", "").trim());
                String p = o.optString("passwordHash", "");
                if (!n.isEmpty() && u != null && !GOOGLE_URL.equalsIgnoreCase(u)) {
                    target.add(new Site(n, u, p));
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveSites(int slot) {
        JSONArray arr = new JSONArray();
        List<Site> source = getSites(slot);
        try {
            for (int i = 1; i < source.size(); i++) {
                Site s = source.get(i);
                JSONObject o = new JSONObject();
                o.put("name", s.name);
                o.put("url", s.url);
                o.put("passwordHash", s.passwordHash == null ? "" : s.passwordHash);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        prefs.edit().putString(slot == 2 ? KEY_SITES_D2 : KEY_SITES_D1, arr.toString()).apply();
    }

    private String hashPassword(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return raw == null ? "" : raw;
        }
    }

    private void loadSlots() {
        slot1Name = prefs.getString(KEY_SLOT1_NAME, GOOGLE_NAME);
        slot1Url = prefs.getString(KEY_SLOT1_URL, GOOGLE_URL);
        slot2Name = prefs.getString(KEY_SLOT2_NAME, GOOGLE_NAME);
        slot2Url = prefs.getString(KEY_SLOT2_URL, GOOGLE_URL);
    }

    private void saveSlots() {
        prefs.edit()
                .putString(KEY_SLOT1_NAME, slot1Name).putString(KEY_SLOT1_URL, slot1Url)
                .putString(KEY_SLOT2_NAME, slot2Name).putString(KEY_SLOT2_URL, slot2Url)
                .apply();
    }

    private String normalizeUrl(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.isEmpty()) return null;
        if (!raw.matches("(?i)^[a-z][a-z0-9+.-]*://.*$")) raw = "https://" + raw;
        try {
            Uri u = Uri.parse(raw);
            String scheme = u.getScheme();
            String host = u.getHost();
            if (scheme == null || host == null) return null;
            if (!(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return null;
            return u.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int darken(int color, float amount) {
        int r = Math.max(0, (int)(Color.red(color) * (1f - amount)));
        int g = Math.max(0, (int)(Color.green(color) * (1f - amount)));
        int b = Math.max(0, (int)(Color.blue(color) * (1f - amount)));
        return Color.rgb(r, g, b);
    }


    // ---------------- Built-in D1 Documents home ----------------

    private void showDocumentsHome() {
        slot1DocumentsHome = true;
        showWebView(1);
        updateSlotLabels();
        final int generation = ++documentsLoadGeneration;
        final String selectedMode = phoneDataMode;

        String loading = documentsShellHtml(
                "<div class='center'><div class='spinner'></div><div>" + escapeDocsHtml(selectedMode) + " loading...</div></div>",
                0, false, selectedMode);
        webView1.loadDataWithBaseURL("https://sts.documents/", loading, "text/html", "UTF-8", null);

        new Thread(() -> {
            final String html;
            if (!hasDocumentAccess()) {
                html = documentsShellHtml(
                        "<div class='permission'><div class='folder'>▣</div>" +
                        "<h2>Phone Data</h2>" +
                        "<p>Documents, photos, videos और other files दिखाने के लिए file access Allow करें।</p>" +
                        "<button onclick='STSDocuments.requestAccess()'>Allow File Access</button></div>",
                        0, true, selectedMode);
            } else {
                List<DocumentEntry> entries = queryPhoneData(selectedMode);
                html = buildPhoneDataHtml(entries, selectedMode);
            }

            runOnUiThread(() -> {
                if (!slot1DocumentsHome || generation != documentsLoadGeneration || isFinishing() ||
                        !selectedMode.equals(phoneDataMode)) return;
                webView1.loadDataWithBaseURL("https://sts.documents/", html, "text/html", "UTF-8", null);
            });
        }, "SFB-Phone-Data").start();
    }

    private boolean hasDocumentAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestDocumentAccess() {
        runOnUiThread(() -> {
            try {
                documentAccessRequested = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_DOCUMENT_ACCESS);
                } else {
                    documentAccessRequested = false;
                    showDocumentsHome();
                }
            } catch (Exception first) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    } else {
                        documentAccessRequested = false;
                    }
                } catch (Exception ignored) {
                    documentAccessRequested = false;
                    Toast.makeText(this, "File access settings नहीं खुल पाई", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (documentAccessRequested && slot1DocumentsHome && webView1 != null) {
            documentAccessRequested = false;
            webView1.postDelayed(this::showDocumentsHome, 250);
        }
    }

    private List<DocumentEntry> queryPhoneData(String mode) {
        List<DocumentEntry> out = new ArrayList<>();
        Cursor cursor = null;
        try {
            Uri collection = MediaStore.Files.getContentUri("external");
            String[] projection = new String[] {
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
            };

            cursor = getContentResolver().query(
                    collection,
                    projection,
                    null,
                    null,
                    MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
            );
            if (cursor == null) return out;

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE);
            int sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE);
            int dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                String name = cursor.getString(nameCol);
                if (TextUtils.isEmpty(name)) continue;
                String mime = mimeCol >= 0 ? cursor.getString(mimeCol) : null;
                String category = categoryForPhoneData(mode, name, mime);
                if (category == null) continue;

                long id = cursor.getLong(idCol);
                long size = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0L;
                long modifiedSec = dateCol >= 0 ? cursor.getLong(dateCol) : 0L;

                // Skip folder-like MediaStore rows from Other Data.
                if ("Other Data".equals(mode) && TextUtils.isEmpty(mime) && size <= 0L && name.indexOf('.') < 0) {
                    continue;
                }

                Uri uri = ContentUris.withAppendedId(collection, id);
                out.add(new DocumentEntry(
                        uri.toString(),
                        name,
                        TextUtils.isEmpty(mime) ? mimeForPhoneDataName(name) : mime,
                        category,
                        Math.max(0L, size),
                        Math.max(0L, modifiedSec) * 1000L
                ));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return out;
    }

    private String categoryForPhoneData(String mode, String name, String mime) {
        if ("Documents".equals(mode)) return documentCategory(name);
        if ("Photo".equals(mode)) return isPhotoCandidate(name, mime) ? "PHOTO" : null;
        if ("Video".equals(mode)) return isVideoFileCandidate(name, mime) ? "VIDEO" : null;
        if ("Other Data".equals(mode)) {
            if (documentCategory(name) != null || isPhotoCandidate(name, mime) || isVideoFileCandidate(name, mime)) {
                return null;
            }
            return otherDataCategory(name, mime);
        }
        return null;
    }

    private String documentCategory(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return "PDF";
        if (n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".odt") || n.endsWith(".rtf")) return "DOC";
        if (n.endsWith(".xls") || n.endsWith(".xlsx") || n.endsWith(".csv") || n.endsWith(".ods")) return "XLS";
        if (n.endsWith(".ppt") || n.endsWith(".pptx") || n.endsWith(".odp")) return "PPT";
        if (n.endsWith(".ofd")) return "OFD";
        if (n.endsWith(".txt")) return "TXT";
        return null;
    }

    private boolean isPhotoCandidate(String name, String mime) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (m.startsWith("image/")) return true;
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
                n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
                n.endsWith(".heic") || n.endsWith(".heif") || n.endsWith(".avif");
    }

    private boolean isVideoFileCandidate(String name, String mime) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (m.startsWith("video/")) return true;
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") ||
                n.endsWith(".3gp") || n.endsWith(".mov") || n.endsWith(".avi") ||
                n.endsWith(".wmv") || n.endsWith(".m4v") || n.endsWith(".mpeg") ||
                n.endsWith(".mpg");
    }

    private String otherDataCategory(String name, String mime) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (m.startsWith("audio/") || n.endsWith(".mp3") || n.endsWith(".wav") ||
                n.endsWith(".aac") || n.endsWith(".m4a") || n.endsWith(".ogg") || n.endsWith(".flac")) return "AUDIO";
        if (n.endsWith(".apk")) return "APK";
        if (n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z") ||
                n.endsWith(".tar") || n.endsWith(".gz")) return "ZIP";
        return "FILE";
    }

    private String mimeForPhoneDataName(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".doc")) return "application/msword";
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".xls")) return "application/vnd.ms-excel";
        if (n.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (n.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".rtf")) return "application/rtf";
        if (n.endsWith(".odt")) return "application/vnd.oasis.opendocument.text";
        if (n.endsWith(".ods")) return "application/vnd.oasis.opendocument.spreadsheet";
        if (n.endsWith(".odp")) return "application/vnd.oasis.opendocument.presentation";
        if (n.endsWith(".ofd")) return "application/ofd";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".bmp")) return "image/bmp";
        if (n.endsWith(".heic") || n.endsWith(".heif")) return "image/heic";
        if (n.endsWith(".avif")) return "image/avif";
        if (n.endsWith(".mp4") || n.endsWith(".m4v")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".3gp")) return "video/3gpp";
        if (n.endsWith(".mkv")) return "video/x-matroska";
        if (n.endsWith(".mov")) return "video/quicktime";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".wav")) return "audio/wav";
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (n.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    private String buildPhoneDataHtml(List<DocumentEntry> docs, String mode) {
        StringBuilder rows = new StringBuilder(Math.max(8192, docs.size() * 340));
        SimpleDateFormat groupFmt = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        SimpleDateFormat itemFmt = new SimpleDateFormat("d MMMM", Locale.getDefault());
        String lastGroup = "";

        for (DocumentEntry d : docs) {
            String group = d.modified > 0 ? groupFmt.format(new Date(d.modified)) : "Unknown date";
            if (!group.equals(lastGroup)) {
                rows.append("<div class='date-group' data-group='").append(escapeDocsAttr(group)).append("'>")
                        .append(escapeDocsHtml(group)).append("</div>");
                lastGroup = group;
            }

            String size = formatDocumentSize(d.size);
            String itemDate = d.modified > 0 ? itemFmt.format(new Date(d.modified)) : "";
            String sub = size;
            if (!TextUtils.isEmpty(itemDate)) sub += "  |  " + itemDate;

            rows.append("<div class='doc-item' data-cat='").append(d.category)
                    .append("' data-name='").append(escapeDocsAttr(d.name.toLowerCase(Locale.ROOT))).append("'")
                    .append(" data-size='").append(d.size).append("'")
                    .append(" data-modified='").append(d.modified).append("' onclick='openDoc(this)'")
                    .append(" data-uri='").append(escapeDocsAttr(d.uri)).append("'")
                    .append(" data-filename='").append(escapeDocsAttr(d.name)).append("'")
                    .append(" data-mime='").append(escapeDocsAttr(d.mime)).append("'>")
                    .append("<div class='file-icon ").append(d.category.toLowerCase(Locale.ROOT)).append("'><span>")
                    .append(escapeDocsHtml(iconText(d.category))).append("</span></div>")
                    .append("<div class='file-body'><div class='file-name'>").append(escapeDocsHtml(d.name)).append("</div>")
                    .append("<div class='file-meta'>").append(escapeDocsHtml(sub)).append("</div></div></div>");
        }

        String body = "<div class='doc-list' id='docList'>" + rows + "</div>" +
                (docs.isEmpty()
                        ? "<div class='empty'>कोई " + escapeDocsHtml(mode) + " data नहीं मिला।</div>"
                        : "");
        return documentsShellHtml(body, docs.size(), false, mode);
    }

    private String documentsShellHtml(String body, int count, boolean permissionPage, String mode) {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
                "<style>" +
                "*{box-sizing:border-box}html,body{margin:0;background:#050505;color:#f4f4f4;font-family:Arial,sans-serif;min-height:100%}" +
                ".header{position:sticky;top:0;z-index:10;background:#050505;padding:16px 16px 0;border-bottom:1px solid #111}" +
                ".title-row{display:flex;align-items:center;height:48px}.mode-title{flex:1;border:0;background:transparent;color:#fff;text-align:left;font-size:25px;font-weight:700;padding:0;white-space:nowrap}.top-count{font-size:13px;color:#a8a8a8;white-space:nowrap;margin-right:4px}.mode-menu{display:none;position:fixed;left:12px;top:58px;z-index:55;width:max-content;min-width:150px;background:#1a1a1a;border:1px solid #3a3a3a;border-radius:10px;overflow:hidden;box-shadow:0 6px 24px rgba(0,0,0,.55)}.mode-menu.show{display:block}.mode-option{display:block;width:100%;border:0;border-bottom:1px solid #2d2d2d;background:#1a1a1a;color:#fff;text-align:left;padding:12px 16px;font-size:15px;white-space:nowrap}.mode-option:active{background:#303030}" +
                ".head-btn{width:44px;height:44px;border:0;background:transparent;color:#fff;font-size:28px;border-radius:22px}" +
                ".head-btn:active{background:#242424}.tabs{display:flex;overflow-x:auto;gap:4px;height:58px;align-items:flex-end;padding:0 4px}" +
                ".tab{border:0;background:transparent;color:#8c8c8c;font-size:18px;padding:16px 13px 13px;white-space:nowrap;border-bottom:3px solid transparent}" +
                ".tab.active{color:#fff;border-bottom-color:#fff;font-weight:700}.search{display:none;padding:0 0 12px}.search.show{display:block}" +
                ".search input{width:100%;height:42px;border-radius:10px;border:1px solid #555;background:#171717;color:#fff;padding:0 12px;font-size:16px}" +
                ".doc-menu{display:none;position:fixed;right:12px;top:58px;z-index:50;width:max-content;max-width:72vw;background:#1a1a1a;border:1px solid #3a3a3a;border-radius:10px;overflow:hidden;box-shadow:0 6px 24px rgba(0,0,0,.55)}.doc-menu.show{display:block}.menu-item,.sort-option{display:block;width:100%;min-width:0;border:0;border-bottom:1px solid #2d2d2d;background:#1a1a1a;color:#fff;text-align:left;padding:12px 16px;font-size:15px;white-space:nowrap}.menu-item:active,.sort-option:active{background:#303030}.sort-sub{display:none;background:#121212}.sort-sub.show{display:block}.sort-sub .sort-option{padding-left:28px;color:#ddd}" +
                ".date-group{font-size:22px;font-weight:700;padding:14px 16px 10px}.doc-list{padding-bottom:28px}" +
                ".doc-item{display:flex;align-items:center;min-height:100px;padding:10px 16px;border-bottom:1px solid #202020}" +
                ".doc-item:active{background:#181818}.file-icon{width:54px;height:66px;margin-right:16px;display:flex;align-items:center;justify-content:center;" +
                "border-radius:4px 4px 3px 3px;color:#fff;font-weight:700;font-size:18px;clip-path:polygon(0 0,76% 0,100% 20%,100% 100%,0 100%)}" +
                ".file-icon.pdf{background:#e34d4d}.file-icon.doc{background:#3778c2}.file-icon.xls{background:#388e4a}" +
                ".file-icon.ppt{background:#f05b23}.file-icon.ofd{background:#7856a8}.file-icon.txt{background:#6f7780}.file-icon.photo{background:#5B7FA3}.file-icon.video{background:#7C7399}.file-icon.audio{background:#4F8F8B}.file-icon.apk{background:#7E9B76}.file-icon.zip{background:#B39B7A}.file-icon.file{background:#6f7780}" +
                ".file-body{min-width:0;flex:1}.file-name{font-size:18px;font-weight:700;line-height:1.28;word-break:break-word}" +
                ".file-meta{font-size:14px;color:#8f8f8f;margin-top:5px}.empty,.center,.permission{text-align:center;padding:70px 24px;color:#aaa}" +
                ".permission{padding-top:100px}.permission h2{color:#fff}.permission p{line-height:1.5}.permission button{margin-top:14px;border:0;" +
                "border-radius:10px;padding:13px 20px;background:#4F8F8B;color:#fff;font-size:16px;font-weight:700}.folder{font-size:54px;color:#f0a348}" +
                ".spinner{width:34px;height:34px;border:4px solid #333;border-top-color:#eee;border-radius:50%;margin:0 auto 14px;animation:r 1s linear infinite}" +
                "@keyframes r{to{transform:rotate(360deg)}}body.grid .doc-list{display:grid;grid-template-columns:1fr 1fr;gap:1px}" +
                "body.grid .date-group{grid-column:1/-1}body.grid .doc-item{display:block;min-height:170px;text-align:center;padding:16px 8px}" +
                "body.grid .file-icon{margin:0 auto 10px}.hide{display:none!important}" +
                "</style></head><body>" +
                "<div class='header'><div class='title-row'>" +
                "<button id='modeButton' class='mode-title' onclick='toggleModeMenu(event)'>" + escapeDocsHtml(mode) + " ▾</button>" +
                "<div class='top-count'><span id='shownCount'>" + count + "</span> items</div>" +
                "<button class='head-btn' onclick='toggleSearch()'>⌕</button>" +
                "<button id='menuButton' class='head-btn' onclick='toggleDocMenu(event)'>⋮</button></div>" +
                "<div id='searchBox' class='search'><input id='q' placeholder='Search " + escapeDocsAttr(mode.toLowerCase(Locale.ROOT)) + "' oninput='applyFilter()'></div>" +
                "<div class='tabs'>" + phoneDataTabsHtml(mode) + "</div></div>" +
                "<div id='modeMenu' class='mode-menu'>" +
                modeOptionHtml("Documents", mode) + modeOptionHtml("Photo", mode) +
                modeOptionHtml("Video", mode) + modeOptionHtml("Other Data", mode) +
                "</div>" +
                "<div id='docMenu' class='doc-menu'>" +
                "<button class='menu-item' onclick='STSDocuments.refresh()'>Refresh</button>" +
                "<button id='gridMenuItem' class='menu-item' onclick='toggleGridFromMenu()'>Grid view</button>" +
                "<button class='menu-item' onclick='toggleSortSub(event)'>Sort by ▸</button>" +
                "<div id='sortSub' class='sort-sub'>" +
                "<button class='sort-option' onclick=\"sortDocs('dateDesc')\">Date modified</button>" +
                "<button class='sort-option' onclick=\"sortDocs('dateAsc')\">Oldest first</button>" +
                "<button class='sort-option' onclick=\"sortDocs('nameAsc')\">Name A–Z</button>" +
                "<button class='sort-option' onclick=\"sortDocs('nameDesc')\">Name Z–A</button>" +
                "<button class='sort-option' onclick=\"sortDocs('sizeDesc')\">Largest first</button>" +
                "<button class='sort-option' onclick=\"sortDocs('sizeAsc')\">Smallest first</button>" +
                "</div>" +
                "</div>" +
                body +
                "<script>" +
                "var cat='All';" +
                "function toggleSearch(){document.getElementById('searchBox').classList.toggle('show');var q=document.getElementById('q');if(document.getElementById('searchBox').classList.contains('show'))q.focus();}" +
                "function toggleModeMenu(e){if(e)e.stopPropagation();document.getElementById('docMenu').classList.remove('show');document.getElementById('sortSub').classList.remove('show');document.getElementById('modeMenu').classList.toggle('show');}" +
                "function chooseMode(v){document.getElementById('modeMenu').classList.remove('show');STSDocuments.selectMode(v);}" +
                "function toggleDocMenu(e){if(e)e.stopPropagation();document.getElementById('modeMenu').classList.remove('show');var m=document.getElementById('docMenu');m.classList.toggle('show');if(!m.classList.contains('show'))document.getElementById('sortSub').classList.remove('show');}" +
                "function toggleGridFromMenu(){document.body.classList.toggle('grid');var b=document.getElementById('gridMenuItem');if(b)b.textContent=document.body.classList.contains('grid')?'List view':'Grid view';document.getElementById('docMenu').classList.remove('show');}" +
                "function toggleSortSub(e){if(e)e.stopPropagation();document.getElementById('sortSub').classList.toggle('show');}" +
                "function dateGroup(ms){var d=new Date(Number(ms)||0);if(!d.getTime())return 'Unknown date';" +
                "var y=d.getFullYear(),m=String(d.getMonth()+1).padStart(2,'0'),day=String(d.getDate()).padStart(2,'0');return y+'/'+m+'/'+day;}" +
                "function sortDocs(mode){var list=document.getElementById('docList');if(!list)return;" +
                "var items=Array.from(list.querySelectorAll('.doc-item'));list.querySelectorAll('.date-group').forEach(function(x){x.remove();});" +
                "items.sort(function(a,b){if(mode==='nameAsc'||mode==='nameDesc'){var x=a.dataset.name||'',y=b.dataset.name||'';var r=x.localeCompare(y);return mode==='nameAsc'?r:-r;}" +
                "if(mode==='sizeDesc'||mode==='sizeAsc'){var x=Number(a.dataset.size)||0,y=Number(b.dataset.size)||0;return mode==='sizeDesc'?(y-x):(x-y);}" +
                "var x=Number(a.dataset.modified)||0,y=Number(b.dataset.modified)||0;return mode==='dateAsc'?(x-y):(y-x);});" +
                "var dateMode=(mode==='dateDesc'||mode==='dateAsc'),last='';items.forEach(function(x){if(dateMode){var g=dateGroup(x.dataset.modified);" +
                "if(g!==last){var h=document.createElement('div');h.className='date-group';h.textContent=g;list.appendChild(h);last=g;}}list.appendChild(x);});" +
                "document.getElementById('sortSub').classList.remove('show');document.getElementById('docMenu').classList.remove('show');applyFilter();}" +
                "document.addEventListener('click',function(e){var m=document.getElementById('docMenu'),b=document.getElementById('menuButton');if(m&&b&&!m.contains(e.target)&&e.target!==b){m.classList.remove('show');document.getElementById('sortSub').classList.remove('show');}" +
                "var mm=document.getElementById('modeMenu'),mb=document.getElementById('modeButton');if(mm&&mb&&!mm.contains(e.target)&&e.target!==mb)mm.classList.remove('show');});" +
                "function setCat(v,b){cat=v;document.querySelectorAll('.tab').forEach(function(x){x.classList.remove('active')});b.classList.add('active');applyFilter();}" +
                "function applyFilter(){var q=(document.getElementById('q').value||'').toLowerCase();var n=0;" +
                "document.querySelectorAll('.doc-item').forEach(function(x){var ok=(cat==='All'||x.dataset.cat===cat)&&(!q||x.dataset.name.indexOf(q)>=0);x.classList.toggle('hide',!ok);if(ok)n++;});" +
                "document.getElementById('shownCount').textContent=n;document.querySelectorAll('.date-group').forEach(function(g){var x=g.nextElementSibling;var any=false;" +
                "while(x&&!x.classList.contains('date-group')){if(x.classList.contains('doc-item')&&!x.classList.contains('hide')){any=true;break;}x=x.nextElementSibling;}g.classList.toggle('hide',!any);});}" +
                "function openDoc(x){STSDocuments.open(x.dataset.uri,x.dataset.filename,x.dataset.mime);}" +
                "</script></body></html>";
    }

    private String phoneDataTabsHtml(String mode) {
        if ("Documents".equals(mode)) {
            return tabHtml("All", true) + tabHtml("DOC", false) + tabHtml("XLS", false) +
                    tabHtml("PPT", false) + tabHtml("PDF", false) + tabHtml("OFD", false) +
                    tabHtml("TXT", false);
        }
        return tabHtml("All", true);
    }

    private String modeOptionHtml(String label, String selected) {
        String shown = label.equals(selected) ? "✓ " + label : label;
        return "<button class='mode-option' onclick=\"chooseMode('" + escapeDocsAttr(label) + "')\">" +
                escapeDocsHtml(shown) + "</button>";
    }

    private String tabHtml(String label, boolean active) {
        return "<button class='tab" + (active ? " active" : "") + "' onclick=\"setCat('" +
                label + "',this)\">" + label + "</button>";
    }

    private String iconText(String category) {
        if ("PDF".equals(category)) return "PDF";
        if ("DOC".equals(category)) return "W";
        if ("XLS".equals(category)) return "X";
        if ("PPT".equals(category)) return "P";
        if ("OFD".equals(category)) return "O";
        if ("TXT".equals(category)) return "TXT";
        if ("PHOTO".equals(category)) return "IMG";
        if ("VIDEO".equals(category)) return "VID";
        if ("AUDIO".equals(category)) return "AUD";
        if ("APK".equals(category)) return "APK";
        if ("ZIP".equals(category)) return "ZIP";
        return "FILE";
    }

    private String formatDocumentSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.getDefault(), "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024.0) return String.format(Locale.getDefault(), "%.1f MB", mb);
        return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0);
    }

    private String escapeDocsHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escapeDocsAttr(String s) {
        return escapeDocsHtml(s);
    }

    private void openDocumentFromHome(String uriString, String name, String mime) {
        runOnUiThread(() -> {
            if (!slot1DocumentsHome) return;
            try {
                Uri uri = Uri.parse(uriString);
                String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
                Intent intent;

                if (isPhotoCandidate(name, mime)) {
                    intent = new Intent(this, ImageViewerActivity.class);
                    intent.setData(uri);
                } else if (lower.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(mime)) {
                    intent = new Intent(this, PdfViewerActivity.class);
                    intent.putExtra("pdf_name", name);
                    intent.putExtra("pdf_slot", 1);
                    intent.setData(uri);
                } else if (documentCategory(name) != null) {
                    intent = new Intent(this, OfficeViewerActivity.class);
                    intent.putExtra("office_name", name);
                    intent.setData(uri);
                } else {
                    intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, TextUtils.isEmpty(mime) ? mimeForPhoneDataName(name) : mime);
                }

                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "इस file के लिए viewer नहीं मिला", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "File open नहीं हो पाई", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class DocumentsBridge {
        @JavascriptInterface
        public void open(String uri, String name, String mime) {
            if (!slot1DocumentsHome) return;
            openDocumentFromHome(uri, name, mime);
        }

        @JavascriptInterface
        public void selectMode(String mode) {
            if (!slot1DocumentsHome) return;
            if (!"Documents".equals(mode) && !"Photo".equals(mode) &&
                    !"Video".equals(mode) && !"Other Data".equals(mode)) return;
            phoneDataMode = mode;
            runOnUiThread(MainActivity.this::showDocumentsHome);
        }

        @JavascriptInterface
        public void requestAccess() {
            if (!slot1DocumentsHome) return;
            requestDocumentAccess();
        }

        @JavascriptInterface
        public void refresh() {
            if (!slot1DocumentsHome) return;
            runOnUiThread(MainActivity.this::showDocumentsHome);
        }
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_DOCUMENT_ACCESS) {
            documentAccessRequested = false;
            if (slot1DocumentsHome) showDocumentsHome();
            return;
        }

        if (requestCode != REQ_LOCATION) return;

        boolean granted = hasLocationPermission();
        if (pendingGeoCallback != null && pendingGeoOrigin != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
        }
        pendingGeoCallback = null;
        pendingGeoOrigin = null;

        if (!granted) {
            showLocationPermissionHelp();
        }
    }

    private void showLocationPermissionHelp() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Location Permission")
                .setMessage("इस website को location चाहिए। Location permission Allow करें। अगर popup दोबारा नहीं आता है, App Settings में Location permission ON करें।")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("App Settings", (d, w) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void destroyWebView(WebView target) {
        if (target == null) return;
        target.stopLoading();
        target.loadUrl("about:blank");
        target.clearHistory();
        target.removeAllViews();
        target.destroy();
    }

    @Override
    protected void onDestroy() {
        destroyWebView(webView1);
        destroyWebView(webView2);
        webView1 = null;
        webView2 = null;
        webView = null;
        super.onDestroy();
    }

    private static class DocumentEntry {
        final String uri;
        final String name;
        final String mime;
        final String category;
        final long size;
        final long modified;

        DocumentEntry(String uri, String name, String mime, String category, long size, long modified) {
            this.uri = uri;
            this.name = name;
            this.mime = mime;
            this.category = category;
            this.size = size;
            this.modified = modified;
        }
    }

    private static class Site {
        String name;
        String url;
        String passwordHash;

        Site(String name, String url, String passwordHash) {
            this.name = name;
            this.url = url;
            this.passwordHash = passwordHash == null ? "" : passwordHash;
        }

        boolean hasPassword() {
            return passwordHash != null && !passwordHash.isEmpty();
        }
    }
}
