package com.sts.fastbrowser;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends android.app.Activity {
    private static final String PREFS = "sts_fast_browser_prefs";
    private static final String KEY_SITES = "sites_json";
    private static final String KEY_ADBLOCK = "adblock";
    private static final String KEY_SLOT1_NAME = "slot1_name";
    private static final String KEY_SLOT1_URL = "slot1_url";
    private static final String KEY_SLOT2_NAME = "slot2_name";
    private static final String KEY_SLOT2_URL = "slot2_url";
    private static final String GOOGLE_NAME = "Google";
    private static final String GOOGLE_URL = "https://www.google.com/";

    private SharedPreferences prefs;
    private final List<Site> sites = new ArrayList<>();
    private LinearLayout slot1Container;
    private LinearLayout slot2Container;
    private TextView slot1Button;
    private TextView slot2Button;
    private ImageView slot1HomeIcon;
    private ImageView slot1RefreshIcon;
    private ImageView slot2HomeIcon;
    private ImageView slot2RefreshIcon;
    private TextView menuButton;
    private WebView webView;
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
            "smartadserver.com", "adform.net", "quantserve.com", "yieldmo.com"
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
        configureWebView();
        updateSlotLabels();
        webView.loadUrl(slot1Url);
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

        menuButton = makeTopButton("⋮", Color.parseColor("#DDD2E8"));
        menuButton.setTextSize(25);
        menuButton.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams slotLp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        slotLp1.setMargins(0, 0, dp(3), 0);
        LinearLayout.LayoutParams slotLp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        slotLp2.setMargins(0, 0, dp(3), 0);
        top.addView(slot1Container, slotLp1);
        top.addView(slot2Container, slotLp2);
        top.addView(menuButton, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                return openExternal(uri.toString());
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
        activeSlot = slot;
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

        for (Site site : sites) {
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
        activeSlot = slot;
        if (slot == 1) {
            slot1Name = site.name;
            slot1Url = site.url;
        } else {
            slot2Name = site.name;
            slot2Url = site.url;
        }
        saveSlots();
        updateSlotLabels();
        webView.animate().alpha(0.82f).setDuration(80).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                webView.loadUrl(site.url);
                webView.animate().alpha(1f).setDuration(160).setListener(null).start();
            }
        }).start();
    }

    private void goHome(int slot) {
        activeSlot = slot;
        updateSlotLabels();
        String url = slot == 1 ? slot1Url : slot2Url;
        webView.loadUrl(url);
    }

    private void refreshSlot(int slot) {
        boolean wasActive = activeSlot == slot;
        activeSlot = slot;
        updateSlotLabels();
        if (wasActive) {
            webView.reload();
        } else {
            webView.loadUrl(slot == 1 ? slot1Url : slot2Url);
        }
    }

    private void showMainMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor, Gravity.END);
        pm.getMenu().add(adBlockEnabled ? "Ad Blocker: ON" : "Ad Blocker: OFF");
        pm.getMenu().add("Add Website");
        pm.getMenu().add("Manage Websites");
        pm.getMenu().add("Settings");
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
            if (t.equals("Settings")) {
                showSettingsDialog();
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
            Site s = new Site(n, u);
            sites.add(s);
            saveSites();
            dialog.dismiss();
            selectSite(slot, s);
        }));
        dialog.show();
    }

    private void showManageSitesDialog() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));

        if (sites.size() <= 1) {
            TextView none = makePopupItem("Custom website अभी add नहीं है");
            list.addView(none, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        } else {
            for (int i = 1; i < sites.size(); i++) {
                Site s = sites.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView title = new TextView(this);
                title.setText(s.name + "\n" + s.url);
                title.setTextColor(Color.parseColor("#172326"));
                title.setTextSize(14);
                title.setPadding(dp(10), dp(6), dp(10), dp(6));
                TextView del = makePopupItem("REMOVE");
                del.setTextColor(Color.parseColor("#8C3030"));
                row.addView(title, new LinearLayout.LayoutParams(0, dp(58), 1f));
                row.addView(del, new LinearLayout.LayoutParams(dp(92), dp(44)));
                del.setOnClickListener(v -> confirmDeleteSite(s));
                list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
            }
        }

        ScrollView sc = new ScrollView(this);
        sc.addView(list);
        new AlertDialog.Builder(this)
                .setTitle("Manage Websites")
                .setView(sc)
                .setPositiveButton("Close", null)
                .show();
    }

    private void confirmDeleteSite(Site site) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + site.name + "?")
                .setMessage(site.url)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    sites.remove(site);
                    if (slot1Url.equals(site.url)) { slot1Name = GOOGLE_NAME; slot1Url = GOOGLE_URL; }
                    if (slot2Url.equals(site.url)) { slot2Name = GOOGLE_NAME; slot2Url = GOOGLE_URL; }
                    saveSites();
                    saveSlots();
                    updateSlotLabels();
                    Toast.makeText(this, "Website removed", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void showSettingsDialog() {
        CheckBox cb = new CheckBox(this);
        cb.setText("Ad Blocker ON");
        cb.setTextSize(16);
        cb.setTextColor(Color.parseColor("#172326"));
        cb.setChecked(adBlockEnabled);
        cb.setPadding(dp(20), dp(12), dp(20), dp(12));
        cb.setOnCheckedChangeListener((buttonView, isChecked) -> setAdBlockEnabled(isChecked, false));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(cb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(body)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("STS Fast Browser")
                .setMessage("Version 1.0.1\n\nSimple • Fast • Two Quick Slots\nAd Blocker can be switched ON/OFF from the common menu.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void setAdBlockEnabled(boolean enabled, boolean showToast) {
        adBlockEnabled = enabled;
        prefs.edit().putBoolean(KEY_ADBLOCK, enabled).apply();
        if (showToast) Toast.makeText(this, enabled ? "Ad Blocker ON" : "Ad Blocker OFF", Toast.LENGTH_SHORT).show();
        if (webView != null) webView.reload();
    }

    private void updateSlotLabels() {
        if (slot1Button != null) slot1Button.setText((activeSlot == 1 ? "● " : "") + slot1Name + " ▾");
        if (slot2Button != null) slot2Button.setText((activeSlot == 2 ? "● " : "") + slot2Name + " ▾");
    }

    private void loadSites() {
        sites.clear();
        sites.add(new Site(GOOGLE_NAME, GOOGLE_URL));
        String json = prefs.getString(KEY_SITES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String n = o.optString("name", "").trim();
                String u = normalizeUrl(o.optString("url", "").trim());
                if (!n.isEmpty() && u != null && !GOOGLE_URL.equalsIgnoreCase(u)) sites.add(new Site(n, u));
            }
        } catch (Exception ignored) {}
    }

    private void saveSites() {
        JSONArray arr = new JSONArray();
        try {
            for (int i = 1; i < sites.size(); i++) {
                JSONObject o = new JSONObject();
                o.put("name", sites.get(i).name);
                o.put("url", sites.get(i).url);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        prefs.edit().putString(KEY_SITES, arr.toString()).apply();
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

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private static class Site {
        final String name;
        final String url;
        Site(String name, String url) { this.name = name; this.url = url; }
    }
}
