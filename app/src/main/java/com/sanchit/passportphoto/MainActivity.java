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
    private static final int REQ_CAMERA = 41;
    private static final int REQ_GALLERY = 42;
    private static final String HOME = "https://chatgpt.com/";

    private static final String FIXED_PROMPT =
            "इस फोटो को professional 35×45 mm passport-size photo में तैयार करें। व्यक्ति की exact identity, face shape, eyes, eyebrows, nose, lips, ears, jawline, hairstyle और natural proportions बिल्कुल न बदलें। चेहरा noticeably fairer, brighter और clean करें लेकिन realistic skin texture रखें। Pimples, dark spots, blemishes, uneven tone और dullness साफ करें। Background clean medium-light blue #4A90C2 करें। Person, hair, ears, neck, shoulders और कपड़ों को सुरक्षित रखें; background और extra unwanted objects हटाएँ। Straight front-facing passport framing रखें, head और shoulders properly centered हों, soft even lighting हो और final photo sharp high-resolution हो।";

    private WebView web;
    private ProgressBar progress;
    private ValueCallback<Uri[]> siteFileCallback;
    private Uri cameraUri;
    private Uri selectedUri;
    private boolean uploadPending = false;
    private boolean promptSent = false;
    private int uploadAttempt = 0;
    private int promptAttempt = 0;

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

        FrameLayout processArea = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(0xFFFFFFFF);
        processArea.addView(web, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, dp(3));
        pp.gravity = Gravity.TOP;
        processArea.addView(progress, pp);

        root.addView(processArea, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(6, 4, 6, 4);

        Button refresh = makeButton("⟳ Refresh");
        Button camera = makeButton("📷 Camera");
        Button gallery = makeButton("🖼 Gallery");

        bar.addView(refresh, new LinearLayout.LayoutParams(0, dp(58), 1f));
        bar.addView(camera, new LinearLayout.LayoutParams(0, dp(58), 1f));
        bar.addView(gallery, new LinearLayout.LayoutParams(0, dp(58), 1f));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        refresh.setOnClickListener(v -> {
            cancelPendingFlow();
            web.reload();
        });
        camera.setOnClickListener(v -> startNativeCamera());
        gallery.setOnClickListener(v -> startNativeGallery());

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
        s.setUseWideViewPort(true);
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkLoads(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        try {
            String ua = WebSettings.getDefaultUserAgent(this);
            if (ua != null) s.setUserAgentString(ua.replace("; wv", "").replace("Version/4.0 ", ""));
        } catch (Exception ignored) {}

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u != null ? u.getScheme() : null;
                if (scheme != null && !scheme.equals("http") && !scheme.equals("https")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (siteFileCallback != null) siteFileCallback.onReceiveValue(null);
                siteFileCallback = callback;

                if (uploadPending && selectedUri != null) {
                    Uri u = selectedUri;
                    selectedUri = null;
                    uploadPending = false;
                    siteFileCallback.onReceiveValue(new Uri[]{u});
                    siteFileCallback = null;
                    promptSent = false;
                    promptAttempt = 0;
                    toast("Photo upload हो रही है…");
                    schedulePromptTry(4500);
                    return true;
                }

                try {
                    Intent i = params != null ? params.createIntent() : new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    if (i.getType() == null) i.setType("image/*");
                    startActivityForResult(i, REQ_GALLERY);
                } catch (Exception e) {
                    if (siteFileCallback != null) {
                        siteFileCallback.onReceiveValue(null);
                        siteFileCallback = null;
                    }
                }
                return true;
            }
        });
    }

    private void startNativeCamera() {
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
                toast("Camera file नहीं बन पाया");
                return;
            }

            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("camera-output", cameraUri));
            if (i.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(i, REQ_CAMERA);
            } else {
                toast("Camera उपलब्ध नहीं है");
            }
        } catch (Exception e) {
            toast("Camera error");
        }
    }

    private void startNativeGallery() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, REQ_GALLERY);
        } catch (Exception e) {
            toast("Gallery नहीं खुली");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startNativeCamera();
            else toast("Camera permission जरूरी है");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CAMERA) {
            if (resultCode == RESULT_OK && cameraUri != null) {
                selectedUri = cameraUri;
                beginChatGptUpload();
            } else {
                cameraUri = null;
            }
            return;
        }

        if (requestCode == REQ_GALLERY) {
            if (siteFileCallback != null && !uploadPending) {
                Uri[] r = null;
                if (resultCode == RESULT_OK && data != null && data.getData() != null)
                    r = new Uri[]{data.getData()};
                siteFileCallback.onReceiveValue(r);
                siteFileCallback = null;
                return;
            }

            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                selectedUri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(
                            selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                beginChatGptUpload();
            }
        }
    }

    private void beginChatGptUpload() {
        if (selectedUri == null) return;
        uploadPending = true;
        uploadAttempt = 0;
        promptSent = false;
        toast("Photo चुनी गई — ChatGPT में भेज रहा हूँ…");
        kickAttachFlow();
    }

    private void kickAttachFlow() {
        if (!uploadPending || selectedUri == null) return;
        uploadAttempt++;

        String js = "(function(){" +
                "var f=document.querySelector('input[type=file]');" +
                "if(f){f.click();return 'file';}" +
                "var all=[].slice.call(document.querySelectorAll('button,[role=button]'));" +
                "var b=all.find(function(x){var t=((x.getAttribute('aria-label')||'')+' '+(x.getAttribute('title')||'')+' '+(x.innerText||'')).toLowerCase();" +
                "return /add|attach|upload|photo|image|file/.test(t);});" +
                "if(b){b.click();return 'opened';}" +
                "return 'none';})()";

        web.evaluateJavascript(js, value -> {
            if (!uploadPending) return;
            web.postDelayed(this::clickUploadMenuItem, 450);
            if (uploadAttempt < 6) web.postDelayed(this::kickAttachFlow, 900);
            else web.postDelayed(() -> {
                if (uploadPending) {
                    uploadPending = false;
                    selectedUri = null;
                    toast("Upload नहीं खुला — ऊपर नया Chat खोलकर फिर Camera/Gallery दबाएँ");
                }
            }, 1000);
        });
    }

    private void clickUploadMenuItem() {
        if (!uploadPending) return;
        String js = "(function(){" +
                "var f=document.querySelector('input[type=file]');if(f){f.click();return 'file';}" +
                "var xs=[].slice.call(document.querySelectorAll('[role=menuitem],[role=option],button,div'));" +
                "var m=xs.find(function(x){var t=((x.innerText||'')+' '+(x.getAttribute&&x.getAttribute('aria-label')||'')).toLowerCase();" +
                "return /upload|photo|image|file|computer|device/.test(t)&&t.length<90;});" +
                "if(m){m.click();setTimeout(function(){var q=document.querySelector('input[type=file]');if(q)q.click();},250);return 'menu';}" +
                "return 'none';})()";
        web.evaluateJavascript(js, null);
    }

    private void schedulePromptTry(long delay) {
        web.postDelayed(this::tryInsertAndSendPrompt, delay);
    }

    private void tryInsertAndSendPrompt() {
        if (promptSent) return;
        promptAttempt++;
        String p = jsQuoted(FIXED_PROMPT);
        String js = "(function(){" +
                "var p=" + p + ";" +
                "var e=document.querySelector('#prompt-textarea')||document.querySelector('textarea')||document.querySelector('[contenteditable=true]');" +
                "if(!e)return 'no-editor';" +
                "e.focus();" +
                "if(e.tagName==='TEXTAREA'){var d=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');if(d&&d.set)d.set.call(e,p);else e.value=p;e.dispatchEvent(new Event('input',{bubbles:true}));}" +
                "else{try{var s=window.getSelection();var r=document.createRange();r.selectNodeContents(e);s.removeAllRanges();s.addRange(r);document.execCommand('insertText',false,p);}catch(z){e.textContent=p;}e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:p}));}" +
                "var btn=document.querySelector('[data-testid=send-button]')||document.querySelector('button[aria-label*=Send]')||document.querySelector('button[aria-label*=send]');" +
                "if(btn&&!btn.disabled){btn.click();return 'sent';}" +
                "return 'filled';})()";

        web.evaluateJavascript(js, value -> {
            if (value != null && value.contains("sent")) {
                promptSent = true;
                toast("Prompt भेज दिया गया");
            } else if (promptAttempt < 8) {
                schedulePromptTry(2200);
            } else {
                toast("Photo upload हो गई; prompt auto-send नहीं हुआ तो एक बार Send दबाएँ");
            }
        });
    }

    private String jsQuoted(String s) {
        return "'" + s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "") + "'";
    }

    private void cancelPendingFlow() {
        uploadPending = false;
        selectedUri = null;
        promptSent = false;
        uploadAttempt = 0;
        promptAttempt = 0;
        if (siteFileCallback != null) {
            siteFileCallback.onReceiveValue(null);
            siteFileCallback = null;
        }
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
