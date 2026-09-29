package com.sts.fastbrowser;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.webkit.URLUtil;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.MotionEvent;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class MainActivity extends android.app.Activity {
    private static final int REQ_LOCATION = 812;
    private static final String PREFS = "sts_fast_browser_prefs";
    private static final String KEY_SITES = "sites_json";
    private static final String KEY_SITES_D1 = "sites_d1_json";
    private static final String KEY_SITES_D2 = "sites_d2_json";
    private static final String KEY_SITES_MIGRATED = "sites_split_migrated";
    private static final String KEY_ADBLOCK = "adblock";
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
    // Active-slot alias. D1 and D2 themselves stay alive independently.
    private WebView webView;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private boolean adBlockEnabled = true;
    private int activeSlot = 1;
    private String slot1Name = GOOGLE_NAME;
    private String slot1Url = GOOGLE_URL;
    private String slot2Name = GOOGLE_NAME;
    private String slot2Url = GOOGLE_URL;

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

        targetWebView.addJavascriptInterface(new PdfBridge(), "STSPdf");
        targetWebView.addJavascriptInterface(new RdBridge(), "STSRD");

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
                            if (adBlockEnabled && isBlocked(popupUri)) {
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
            } else {
                openExternal(url);
            }
        });
        targetWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    if (isPdfCandidate(uri.toString(), null)) {
                        openPdfTask(uri.toString(), guessPdfName(uri.toString(), null));
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
                if (adBlockEnabled) injectAdCleanup(view);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (adBlockEnabled && !request.isForMainFrame() && isBlocked(request.getUrl())) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
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

    private void injectAdCleanup(WebView view) {
        if (view == null) return;
        String js =
                "(function(){" +
                "if(window.__stsAdCleanerInstalled){try{window.__stsAdClean&&window.__stsAdClean();}catch(e){}return;}" +
                "window.__stsAdCleanerInstalled=true;" +
                "var selectors=[" +
                "'ins.adsbygoogle','.adsbygoogle','[id^=\"google_ads_\"]','[id^=\"div-gpt-ad\"]'," +
                "'iframe[src*=\"doubleclick.net\"]','iframe[src*=\"googlesyndication.com\"]'," +
                "'iframe[src*=\"googleadservices.com\"]','[data-ad-client]','[data-ad-slot]'," +
                "'amp-ad','amp-embed[type=\"taboola\"]','.advertisement','.ad-container','.ad-banner','.ad-wrapper'" +
                "];" +
                "window.__stsAdClean=function(){for(var i=0;i<selectors.length;i++){var n=document.querySelectorAll(selectors[i]);" +
                "for(var j=0;j<n.length;j++){try{n[j].style.setProperty('display','none','important');" +
                "n[j].style.setProperty('visibility','hidden','important');n[j].setAttribute('aria-hidden','true');}catch(e){}}}};" +
                "window.__stsAdClean();" +
                "try{new MutationObserver(function(){window.__stsAdClean();}).observe(document.documentElement||document," +
                "{childList:true,subtree:true,attributes:false});}catch(e){}" +
                "})();";
        try { view.evaluateJavascript(js, null); } catch (Exception ignored) {}
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
        ensureSlotPageLoaded(slot);
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

        if (slot == 1) {
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
        if (!sameSelection || target.getUrl() == null) {
            target.animate().alpha(0.82f).setDuration(80).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    target.loadUrl(site.url);
                    target.animate().alpha(1f).setDuration(160).setListener(null).start();
                }
            }).start();
        }
    }

    private void loadInitialPage() {
        showWebView(1);
        ensureSlotPageLoaded(1);
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
        showWebView(slot);
        WebView target = webViewForSlot(slot);
        String url = slot == 1 ? slot1Url : slot2Url;
        target.loadUrl(url);
    }

    private void refreshSlot(int slot) {
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
        pm.getMenu().add("Add Website");
        pm.getMenu().add("Manage Websites");
        pm.getMenu().add("About");
        pm.setOnMenuItemClickListener(item -> {
            String t = String.valueOf(item.getTitle());
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
                .setMessage("Version 1.0.23\n\nSimple • Fast • Two Quick Slots\nAd Blocker can be switched ON/OFF from the common menu.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void setAdBlockEnabled(boolean enabled, boolean showToast) {
        adBlockEnabled = enabled;
        prefs.edit().putBoolean(KEY_ADBLOCK, enabled).apply();

        if (enabled) {
            if (webView1 != null) injectAdCleanup(webView1);
            if (webView2 != null) injectAdCleanup(webView2);
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

    private void updateSlotLabels() {
        if (slot1Button != null) slot1Button.setText((activeSlot == 1 ? "● " : "") + slot1Name + " ▾");
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

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
