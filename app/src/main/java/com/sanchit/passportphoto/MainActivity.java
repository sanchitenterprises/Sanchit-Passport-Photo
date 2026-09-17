package com.sanchit.passportphoto;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION = 40;
    private static final int FILE_PICK = 41;
    private static final int MODE_NONE = 0;
    private static final int MODE_CAMERA = 1;
    private static final int MODE_GALLERY = 2;
    private static final String HOME = "https://chatgpt.com/";

    private static final String FIXED_PROMPT =
            "इस फोटो को professional 35×45 mm passport-size photo में तैयार करें। व्यक्ति की exact identity, face shape, eyes, eyebrows, nose, lips, ears, jawline, hairstyle और natural proportions बिल्कुल न बदलें। चेहरा noticeably fairer, brighter और clean करें लेकिन realistic skin texture रखें। Pimples, dark spots, blemishes, uneven tone और dullness साफ करें। Background clean medium-light blue #4A90C2 करें। Person, hair, ears, neck, shoulders और कपड़ों को सुरक्षित रखें; background और extra unwanted objects हटाएँ। Straight front-facing passport framing रखें, head और shoulders properly centered हों, soft even lighting हो और final photo sharp high-resolution हो।";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private int pendingMode = MODE_NONE;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        configureWebView();
        web.loadUrl(HOME);
    }

    private Button makeButton(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(14);
        b.setAllCaps(false);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(6, 4, 6, 4);

        Button refresh = makeButton("⟳ Refresh");
        Button camera = makeButton("📷 Camera");
        Button gallery = makeButton("🖼 Gallery");

        bar.addView(refresh, new LinearLayout.LayoutParams(0, dp(58), 1f));
        bar.addView(camera, new LinearLayout.LayoutParams(0, dp(58), 1f));
        bar.addView(gallery, new LinearLayout.LayoutParams(0, dp(58), 1f));
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        refresh.setOnClickListener(v -> web.reload());
        camera.setOnClickListener(v -> triggerSiteUpload(MODE_CAMERA));
        gallery.setOnClickListener(v -> triggerSiteUpload(MODE_GALLERY));

        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        String ua = s.getUserAgentString();
        if (ua != null) s.setUserAgentString(ua.replace("; wv", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                if (pendingMode == MODE_CAMERA) {
                    openCameraForWeb();
                } else {
                    openGalleryForWeb(params);
                }
                return true;
            }
        });
    }

    private void triggerSiteUpload(int mode) {
        pendingMode = mode;
        String js = "(function(){" +
                "var i=document.querySelector('input[type=file]');" +
                "if(i){i.click();return 'input';}" +
                "var bs=[].slice.call(document.querySelectorAll('button'));" +
                "var b=bs.find(function(x){var t=((x.getAttribute('aria-label')||'')+' '+(x.getAttribute('title')||'')+' '+(x.innerText||'')).toLowerCase();return /attach|upload|photo|image|file|add photos/.test(t);});" +
                "if(b){b.click();setTimeout(function(){var f=document.querySelector('input[type=file]');if(f)f.click();},450);return 'button';}" +
                "return 'none';})()";
        web.evaluateJavascript(js, value -> {
            if (value != null && value.contains("none")) {
                pendingMode = MODE_NONE;
                toast("पहले ChatGPT में login करके नया chat खोलें");
            }
        });
    }

    private void openCameraForWeb() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission("android.permission.CAMERA") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.CAMERA"}, CAMERA_PERMISSION);
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "Sanchit_ChatGPT_" + System.currentTimeMillis() + ".jpg");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29)
                v.put(MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/Sanchit Passport Photo/Camera");
            cameraUri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (cameraUri == null) {
                finishFileChooser(null);
                toast("Camera file नहीं बन पाया");
                return;
            }
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("camera-output", cameraUri));
            if (i.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(i, FILE_PICK);
            } else {
                finishFileChooser(null);
                toast("Camera उपलब्ध नहीं है");
            }
        } catch (Exception e) {
            finishFileChooser(null);
            toast("Camera error");
        }
    }

    private void openGalleryForWeb(WebChromeClient.FileChooserParams params) {
        try {
            Intent i;
            try {
                i = params != null ? params.createIntent() : null;
            } catch (Exception ex) {
                i = null;
            }
            if (i == null) {
                i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
            }
            startActivityForResult(i, FILE_PICK);
        } catch (Exception e) {
            finishFileChooser(null);
            toast("Gallery नहीं खुली");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                openCameraForWeb();
            } else {
                finishFileChooser(null);
                toast("Camera permission जरूरी है");
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_PICK || fileCallback == null) return;

        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (pendingMode == MODE_CAMERA && cameraUri != null) {
                result = new Uri[]{cameraUri};
            } else if (data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int x = 0; x < n; x++) result[x] = data.getClipData().getItemAt(x).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
        }
        finishFileChooser(result);
        if (result != null && result.length > 0) scheduleFixedPrompt();
    }

    private void finishFileChooser(Uri[] result) {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
        pendingMode = MODE_NONE;
    }

    private void scheduleFixedPrompt() {
        web.postDelayed(this::insertPromptAndTrySend, 2500);
        web.postDelayed(this::insertPromptAndTrySend, 5200);
    }

    private String jsQuoted(String s) {
        return "'" + s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "") + "'";
    }

    private void insertPromptAndTrySend() {
        String p = jsQuoted(FIXED_PROMPT);
        String js = "(function(){" +
                "var p=" + p + ";" +
                "var e=document.querySelector('#prompt-textarea')||document.querySelector('textarea')||document.querySelector('[contenteditable=true]');" +
                "if(!e)return 'no-editor';" +
                "e.focus();" +
                "if(e.tagName==='TEXTAREA'){var d=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');if(d&&d.set)d.set.call(e,p);else e.value=p;e.dispatchEvent(new Event('input',{bubbles:true}));}" +
                "else{e.innerHTML='';try{document.execCommand('insertText',false,p);}catch(x){e.textContent=p;}e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:p}));}" +
                "var s=document.querySelector('[data-testid=send-button]')||document.querySelector('button[aria-label*=Send]')||document.querySelector('button[aria-label*=send]');" +
                "if(s&&!s.disabled){s.click();return 'sent';}return 'filled';})()";
        web.evaluateJavascript(js, null);
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
