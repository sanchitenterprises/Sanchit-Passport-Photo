package com.sts.deepcleaner;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.StatFs;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends Activity {
    private static final int PURPLE = Color.rgb(90,74,227);
    private static final int PURPLE2 = Color.rgb(117,92,255);
    private static final int TEAL = Color.rgb(41,214,194);
    private static final int AMBER = Color.rgb(255,179,71);
    private static final int ROSE = Color.rgb(255,103,137);
    private static final int BLUE = Color.rgb(73,144,226);
    private static final int BG = Color.rgb(247,248,255);
    private static final int INK = Color.rgb(22,27,40);
    private static final int MUTED = Color.rgb(102,108,128);

    private ScanSummary lastSummary;
    private boolean pendingScanAfterAccess = false;
    private boolean pendingDeepAfterAccess = false;
    private String pendingToolAfterAccess = null;
    private ToolResult activeToolResult = null;
    private ExoPlayer activePlayer = null;
    private PdfRenderer activePdfRenderer = null;
    private ParcelFileDescriptor activePdfFd = null;
    private StorageAnalytics cachedAnalytics = null;
    private long cachedAnalyticsAt = 0L;
    private boolean pendingUsageAnalyzer = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(PURPLE);
        w.setNavigationBarColor(BG);
        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingUsageAnalyzer && hasUsageAccess()) {
            pendingUsageAnalyzer = false;
            cachedAnalytics = null;
            cachedAnalyticsAt = 0L;
            showSystemAnalyzer();
            return;
        }
        if (pendingToolAfterAccess != null && hasAllFilesAccess()) {
            String tool = pendingToolAfterAccess;
            pendingToolAfterAccess = null;
            openTool(tool);
            return;
        }
        if (pendingScanAfterAccess && hasAllFilesAccess()) {
            boolean deep = pendingDeepAfterAccess;
            pendingScanAfterAccess = false;
            pendingDeepAfterAccess = false;
            startScan(deep);
            return;
        }
        if (getWindow().getDecorView().getTag() != null &&
                "home".equals(getWindow().getDecorView().getTag())) {
            showHome();
        }
    }

    private void showHome() {
        releasePreviewResources();
        purgeExpiredTrashAsync();
        getWindow().getDecorView().setTag("home");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(BG);

        LinearLayout root = column();
        root.setPadding(0, 0, 0, dp(24));

        LinearLayout header = column();
        header.setPadding(dp(20), dp(22), dp(20), dp(26));
        header.setBackground(gradient(PURPLE, PURPLE2, 0, 0, 0, 0));

        LinearLayout brandRow = row();
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.sts.deepcleaner.R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        brandRow.addView(logo, logoLp);

        LinearLayout brandText = column();
        brandText.setPadding(dp(12), 0, 0, 0);
        TextView title = text("STS Deep Cleaner", 27, Color.WHITE, true);
        TextView sub = text("Safe • Smart • Deep", 14, Color.argb(220,255,255,255), false);
        brandText.addView(title);
        brandText.addView(space(3));
        brandText.addView(sub);
        brandRow.addView(brandText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(brandRow, matchWrap());
        header.addView(space(18));

        long[] st = storage();
        StorageRing ring = new StorageRing(this);
        ring.setStorage(st[0], st[1]);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(dp(196), dp(196));
        ringLp.gravity = Gravity.CENTER_HORIZONTAL;
        header.addView(ring, ringLp);
        root.addView(header, matchWrap());

        LinearLayout content = column();
        content.setPadding(dp(16), dp(16), dp(16), 0);

        LinearLayout modeCard = card();
        modeCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        TextView free = text("Free storage", 14, MUTED, false);
        TextView freeValue = text(format(st[0] - st[1]), 27, PURPLE, true);
        TextView mode = pill(hasAllFilesAccess() ? "Full storage access ON" : "Full scan access OFF",
                hasAllFilesAccess() ? Color.rgb(18,145,123) : PURPLE,
                hasAllFilesAccess() ? Color.rgb(228,252,248) : Color.rgb(244,240,255));
        modeCard.addView(free);
        modeCard.addView(freeValue);
        modeCard.addView(space(8));
        modeCard.addView(mode);
        content.addView(modeCard, matchWrap());

        content.addView(section("Storage by File Type"));
        LinearLayout analyticsCard = card();
        analyticsCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView analyticsStatus = text("Analyzing visible files…", 12, MUTED, false);
        analyticsCard.addView(analyticsStatus);

        analyticsCard.addView(space(12));
        StorageBreakdownView breakdown = new StorageBreakdownView(this);
        analyticsCard.addView(breakdown, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        analyticsCard.addView(space(12));
        LinearLayout analyticsRows = column();
        analyticsCard.addView(analyticsRows, matchWrap());
        content.addView(analyticsCard, matchWrap());

        loadStorageAnalytics(st[1], breakdown, analyticsRows, analyticsStatus);

        content.addView(section("Storage Tools"));
        LinearLayout row1 = row();
        LinearLayout junkTool = toolCard("✦", "Junk & Cache", "Temporary files", TEAL);
        junkTool.setOnClickListener(v -> openTool("junk"));
        row1.addView(junkTool, weight());
        row1.addView(spaceH(12));
        LinearLayout largeTool = toolCard("⬢", "Large Files", "Review first", AMBER);
        largeTool.setOnClickListener(v -> openTool("large"));
        row1.addView(largeTool, weight());
        content.addView(row1, matchWrap());

        content.addView(space(12));
        LinearLayout row2 = row();
        LinearLayout duplicateTool = toolCard("⧉", "Duplicates", "Hash finder", BLUE);
        duplicateTool.setOnClickListener(v -> openTool("duplicates"));
        row2.addView(duplicateTool, weight());
        row2.addView(spaceH(12));
        LinearLayout residualTool = toolCard("⌁", "Residual", "App leftovers", ROSE);
        residualTool.setOnClickListener(v -> openTool("residual"));
        row2.addView(residualTool, weight());
        content.addView(row2, matchWrap());

        content.addView(space(12));
        LinearLayout trashTool = toolCard("♻", "STS Trash", "Restore • 7 days", Color.rgb(72,120,210));
        trashTool.setOnClickListener(v -> showTrashScreen());
        content.addView(trashTool, matchWrap());

        content.addView(space(12));
        LinearLayout systemTool = toolCard("◉", "Apps & System", "Find hidden storage", Color.rgb(82,88,110));
        systemTool.setOnClickListener(v -> openSystemAnalyzer());
        content.addView(systemTool, matchWrap());

        content.addView(space(22));
        TextView scan = actionButton("SMART SCAN", PURPLE);
        touch(scan);
        scan.setOnClickListener(v -> beginScan(false));
        content.addView(scan, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        content.addView(space(10));
        TextView deep = actionButton(hasAllFilesAccess() ? "DEEP SCAN • ACCESS ON" : "DEEP SCAN • ENABLE ACCESS",
                Color.rgb(236,233,255));
        deep.setTextColor(PURPLE);
        touch(deep);
        deep.setOnClickListener(v -> beginScan(true));
        content.addView(deep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        content.addView(space(16));
        TextView safety = text("✓ Personal photos, videos और documents auto-delete नहीं होंगे\n✓ System-critical paths हमेशा protected रहेंगे", 13, MUTED, false);
        safety.setLineSpacing(dp(3), 1f);
        content.addView(safety);

        root.addView(content, matchWrap());
        scroll.addView(root);
        setContentView(scroll);
        fadeIn(root);
        popIn(logo);
    }

    private void loadStorageAnalytics(long usedStorage, StorageBreakdownView chart,
                                      LinearLayout rows, TextView status) {
        if (cachedAnalytics != null && System.currentTimeMillis() - cachedAnalyticsAt < 5L*60*1000) {
            renderStorageAnalytics(cachedAnalytics, usedStorage, chart, rows, status);
            return;
        }

        new Thread(() -> {
            StorageAnalytics a = new StorageAnalytics();
            File root = Environment.getExternalStorageDirectory();
            Set<String> seen = new HashSet<>();
            scanAnalytics(root, seen, 0, a);

            if (hasUsageAccess()) {
                fillPrivateStorageStats(a, usedStorage);
            } else {
                long hiddenBytes = Math.max(0L, usedStorage - a.visibleBytes);
                a.add(StorageCategory.HIDDEN_UNCLASSIFIED, hiddenBytes, hiddenBytes > 0 ? 1 : 0);
            }
            a.usedStorage = usedStorage;

            cachedAnalytics = a;
            cachedAnalyticsAt = System.currentTimeMillis();

            runOnUiThread(() -> {
                Object tag = getWindow().getDecorView().getTag();
                if ("home".equals(tag)) renderStorageAnalytics(a, usedStorage, chart, rows, status);
            });
        }, "sts-storage-analytics").start();
    }

    private void scanAnalytics(File f, Set<String> seen, int depth, StorageAnalytics a) {
        if (f == null || depth > 24) return;
        String path;
        try { path = f.getCanonicalPath(); } catch (Exception e) { path = f.getAbsolutePath(); }
        if (!seen.add(path)) return;

        String lowPath = path.toLowerCase(Locale.ROOT);
        if (lowPath.contains("/android/data/") || lowPath.contains("/android/obb/") ||
                lowPath.contains("/sts_trash/")) {
            return;
        }

        if (f.isDirectory()) {
            File[] children;
            try { children = f.listFiles(); } catch (Exception e) { children = null; }
            if (children == null) return;
            for (File x : children) scanAnalytics(x, seen, depth+1, a);
            return;
        }

        long len = Math.max(0L, f.length());
        a.visibleBytes += len;
        a.visibleFiles++;

        StorageCategory cat = categoryForAnalytics(f, lowPath);
        a.add(cat, len, 1);
    }

    private StorageCategory categoryForAnalytics(File f, String lowPath) {
        String n = f.getName().toLowerCase(Locale.ROOT);

        if (isImageFile(f)) return StorageCategory.PHOTOS;
        if (isVideoFile(f)) return StorageCategory.VIDEOS;
        if (isAudioFile(n)) return StorageCategory.AUDIO;

        if (n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") ||
                n.endsWith(".xls") || n.endsWith(".xlsx") || n.endsWith(".ppt") ||
                n.endsWith(".pptx") || n.endsWith(".txt") || n.endsWith(".rtf") ||
                n.endsWith(".csv") || n.endsWith(".odt") || n.endsWith(".ods") ||
                n.endsWith(".odp")) {
            return StorageCategory.DOCUMENTS;
        }

        if (n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") ||
                n.endsWith(".aab")) {
            return StorageCategory.APK;
        }

        if (isWhatsAppChatBackup(n, lowPath) || isDatabaseLike(n, lowPath) ||
                lowPath.contains("/backup/") || lowPath.contains("/backups/") ||
                n.contains("backup") || n.endsWith(".bak")) {
            return StorageCategory.BACKUPS;
        }

        if (lowPath.contains("/cache/") || lowPath.contains("/.cache/") ||
                lowPath.contains("/.thumbnails/") || lowPath.contains("/temp/") ||
                lowPath.contains("/tmp/") || n.endsWith(".tmp") || n.endsWith(".temp") ||
                n.endsWith(".log") || n.endsWith(".dmp") || n.endsWith(".crash")) {
            return StorageCategory.JUNK;
        }

        return StorageCategory.OTHERS;
    }

    private void renderStorageAnalytics(StorageAnalytics a, long usedStorage,
                                        StorageBreakdownView chart,
                                        LinearLayout rows, TextView status) {
        rows.removeAllViews();
        chart.setAnalytics(a, Math.max(1L, usedStorage));

        status.setText(format(a.visibleBytes) + " visible files scanned • " +
                a.visibleFiles + " files • % of used storage");

        for (StorageCategory cat : StorageCategory.values()) {
            long bytes = a.bytes(cat);
            int count = a.count(cat);
            if (bytes <= 0) continue;

            float pct = usedStorage <= 0 ? 0f : (bytes * 100f / usedStorage);
            LinearLayout r = row();
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(6), 0, dp(6));

            TextView dot = text("●", 18, cat.color, true);
            r.addView(dot, new LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout labels = column();
            labels.addView(text(cat.label, 13, INK, true));
            String countText;
            if (cat == StorageCategory.SYSTEM_RESERVED) countText = "Android / reserved estimate";
            else if (cat == StorageCategory.APP_CODE) countText = "installed app code";
            else if (cat == StorageCategory.APP_DATA) countText = "private app data";
            else if (cat == StorageCategory.APP_CACHE) countText = "reclaimable app cache";
            else if (cat == StorageCategory.HIDDEN_UNCLASSIFIED) countText = "tap Apps & System to split";
            else countText = count + (count == 1 ? " file" : " files");
            labels.addView(text(countText, 10, MUTED, false));
            r.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView value = text(format(bytes) + "  •  " + percentText(pct), 12, cat.color, true);
            value.setGravity(Gravity.END);
            r.addView(value);

            rows.addView(r, matchWrap());
        }
    }

    private String percentText(float pct) {
        if (pct > 0f && pct < 0.1f) return "<0.1%";
        if (pct >= 10f) return String.format(Locale.US, "%.0f%%", pct);
        return String.format(Locale.US, "%.1f%%", pct);
    }

    private boolean hasUsageAccess() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void fillPrivateStorageStats(StorageAnalytics a, long usedStorage) {
        long appBytes = 0L;
        long dataBytes = 0L;
        long cacheBytes = 0L;
        try {
            StorageStatsManager mgr = (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
            StorageStats stats = mgr.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle());
            appBytes = Math.max(0L, stats.getAppBytes());
            dataBytes = Math.max(0L, stats.getDataBytes());
            cacheBytes = Math.max(0L, stats.getCacheBytes());
        } catch (Exception ignored) {}

        a.add(StorageCategory.APP_CODE, appBytes, appBytes > 0 ? 1 : 0);
        a.add(StorageCategory.APP_DATA, dataBytes, dataBytes > 0 ? 1 : 0);
        a.add(StorageCategory.APP_CACHE, cacheBytes, cacheBytes > 0 ? 1 : 0);

        long accounted = a.visibleBytes + appBytes + dataBytes + cacheBytes;
        long systemReserved = Math.max(0L, usedStorage - accounted);
        a.add(StorageCategory.SYSTEM_RESERVED, systemReserved, systemReserved > 0 ? 1 : 0);
        a.privateStatsAvailable = true;
    }

    private void openSystemAnalyzer() {
        haptic();
        if (!hasUsageAccess()) {
            pendingUsageAnalyzer = true;
            new AlertDialog.Builder(this)
                    .setTitle("Apps & System Analyzer")
                    .setMessage("78 GB जैसे hidden/private हिस्से को Apps, App Data, Cache और System में अलग करने के लिए Android का Usage Access चाहिए। यह permission files delete नहीं करती; केवल storage statistics पढ़ने देती है।")
                    .setNegativeButton("Cancel", (d,w) -> pendingUsageAnalyzer = false)
                    .setPositiveButton("GRANT USAGE ACCESS", (d,w) -> {
                        try {
                            Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                            startActivity(i);
                        } catch (Exception e) {
                            pendingUsageAnalyzer = false;
                        }
                    }).show();
            return;
        }
        showSystemAnalyzer();
    }

    private void showSystemAnalyzer() {
        getWindow().getDecorView().setTag("systemAnalyzer");
        LinearLayout root = column();
        root.setPadding(dp(18), dp(24), dp(18), dp(28));
        root.setBackgroundColor(BG);

        root.addView(text("Apps & System Analyzer", 27, INK, true));
        root.addView(space(5));
        TextView status = text("Installed apps और private storage analyze हो रहा है…", 13, MUTED, false);
        root.addView(status);
        root.addView(space(20));

        ScanRing ring = new ScanRing(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(190), dp(190));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(ring, rlp);

        root.addView(space(18));
        LinearLayout live = card();
        live.setPadding(dp(18),dp(16),dp(18),dp(16));
        TextView appCode = text("App code: …", 14, Color.rgb(82,88,110), true);
        TextView appData = text("App data: …", 14, PURPLE, true);
        TextView appCache = text("App cache: …", 14, TEAL, true);
        TextView sys = text("Android/System: …", 14, MUTED, true);
        live.addView(appCode);
        live.addView(space(6));
        live.addView(appData);
        live.addView(space(6));
        live.addView(appCache);
        live.addView(space(6));
        live.addView(sys);
        root.addView(live, matchWrap());

        Space flex = new Space(this);
        root.addView(flex,new LinearLayout.LayoutParams(1,0,1f));
        TextView back = actionButton("BACK",Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> showHome());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        setContentView(root);
        fadeIn(root);

        new Thread(() -> {
            SystemStorageResult result = collectSystemStorageResult();
            runOnUiThread(() -> {
                ring.setDone();
                appCode.setText("App code: " + format(result.appCodeBytes));
                appData.setText("App data: " + format(result.appDataBytes));
                appCache.setText("App cache: " + format(result.appCacheBytes));
                sys.setText("Android/System/Reserved: " + format(result.systemReservedBytes));
                showSystemAnalyzerResult(result);
            });
        }, "sts-system-analyzer").start();
    }

    private SystemStorageResult collectSystemStorageResult() {
        SystemStorageResult result = new SystemStorageResult();
        long[] st = storage();
        result.usedBytes = st[1];

        try {
            StorageStatsManager mgr = (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
            StorageStats userStats = mgr.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle());
            result.appCodeBytes = Math.max(0L,userStats.getAppBytes());
            result.appDataBytes = Math.max(0L,userStats.getDataBytes());
            result.appCacheBytes = Math.max(0L,userStats.getCacheBytes());
        } catch (Exception ignored) {}

        long visible = cachedAnalytics != null ? cachedAnalytics.visibleBytes : 0L;
        if (visible <= 0) {
            StorageAnalytics temp = new StorageAnalytics();
            scanAnalytics(Environment.getExternalStorageDirectory(), new HashSet<>(), 0, temp);
            visible = temp.visibleBytes;
        }
        result.visibleBytes = visible;
        result.systemReservedBytes = Math.max(0L, result.usedBytes - visible -
                result.appCodeBytes - result.appDataBytes - result.appCacheBytes);

        try {
            PackageManager pm = getPackageManager();
            StorageStatsManager mgr = (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo ai : apps) {
                try {
                    StorageStats ss = mgr.queryStatsForPackage(StorageManager.UUID_DEFAULT,
                            ai.packageName, Process.myUserHandle());
                    long code = Math.max(0L,ss.getAppBytes());
                    long data = Math.max(0L,ss.getDataBytes());
                    long cache = Math.max(0L,ss.getCacheBytes());
                    long total = code + data + cache;
                    if (total <= 0) continue;
                    CharSequence label = pm.getApplicationLabel(ai);
                    boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    result.apps.add(new AppStorageEntry(
                            label == null ? ai.packageName : label.toString(),
                            ai.packageName, code, data, cache, total, system));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        Collections.sort(result.apps, (a,b) -> Long.compare(b.totalBytes,a.totalBytes));
        return result;
    }

    private void showSystemAnalyzerResult(SystemStorageResult result) {
        getWindow().getDecorView().setTag("systemAnalyzerResult");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(16),dp(22),dp(16),dp(28));

        root.addView(text("Apps & System",28,INK,true));
        root.addView(space(5));
        root.addView(text("Hidden/private storage breakdown • Android-reported statistics",12,MUTED,false));
        root.addView(space(16));

        LinearLayout totals = card();
        totals.setPadding(dp(18),dp(16),dp(18),dp(16));
        totals.addView(systemRow("Installed app code", result.appCodeBytes, Color.rgb(82,88,110)));
        totals.addView(space(7));
        totals.addView(systemRow("Private app data", result.appDataBytes, PURPLE));
        totals.addView(space(7));
        totals.addView(systemRow("App cache", result.appCacheBytes, TEAL));
        totals.addView(space(7));
        totals.addView(systemRow("Android / System / Reserved", result.systemReservedBytes, ROSE));
        totals.addView(space(9));
        totals.addView(text("System/Reserved एक estimate है। Android कुछ OEM/reserved/snapshot storage को third-party apps से पूरी तरह अलग नहीं बताता।",11,MUTED,false));
        root.addView(totals,matchWrap());

        root.addView(space(14));
        TextView systemSettings = actionButton("OPEN ANDROID STORAGE SETTINGS",PURPLE);
        touch(systemSettings);
        systemSettings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        root.addView(systemSettings,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)));

        root.addView(section("Apps using most storage"));
        int limit=Math.min(60,result.apps.size());
        for(int i=0;i<limit;i++){
            AppStorageEntry e=result.apps.get(i);
            LinearLayout c=card();
            c.setPadding(dp(15),dp(13),dp(15),dp(13));

            LinearLayout top=row();
            top.setGravity(Gravity.CENTER_VERTICAL);
            try {
                Drawable d=getPackageManager().getApplicationIcon(e.packageName);
                ImageView iv=new ImageView(this);
                iv.setImageDrawable(d);
                top.addView(iv,new LinearLayout.LayoutParams(dp(38),dp(38)));
                top.addView(spaceH(10));
            } catch(Exception ignored){}
            LinearLayout labels=column();
            labels.addView(text(e.appName,14,INK,true));
            labels.addView(text(e.systemApp ? "System app" : "User app",10,e.systemApp?MUTED:PURPLE,false));
            top.addView(labels,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            top.addView(text(format(e.totalBytes),14,PURPLE,true));
            c.addView(top,matchWrap());

            c.addView(space(9));
            c.addView(text("App " + format(e.codeBytes) + "  •  Data " + format(e.dataBytes) +
                    "  •  Cache " + format(e.cacheBytes),11,MUTED,false));

            c.addView(space(9));
            TextView manage=pill("OPEN APP STORAGE",PURPLE,Color.rgb(239,236,255));
            touch(manage);
            manage.setOnClickListener(v -> openAppStorageSettings(e.packageName));
            c.addView(manage);
            root.addView(c,matchWrap());
            root.addView(space(9));
        }

        root.addView(space(10));
        TextView back=actionButton("BACK",Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> showHome());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        scroll.addView(root);
        setContentView(scroll);
        fadeIn(root);
    }

    private LinearLayout systemRow(String label,long bytes,int color){
        LinearLayout r=row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot=text("●",18,color,true);
        r.addView(dot,new LinearLayout.LayoutParams(dp(24),ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(text(label,13,INK,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        r.addView(text(format(bytes),13,color,true));
        return r;
    }

    private void openAppStorageSettings(String packageName){
        try{
            Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+packageName));
            startActivity(i);
        }catch(Exception e){
            try{ startActivity(new Intent(Settings.ACTION_SETTINGS)); }catch(Exception ignored){}
        }
    }

    private void openTool(String type) {
        haptic();
        if (Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess()) {
            pendingToolAfterAccess = type;
            requestToolAccess(type);
            return;
        }
        showToolScan(type);
    }

    private String toolTitle(String type) {
        if ("junk".equals(type)) return "Junk & Cache";
        if ("large".equals(type)) return "Large Files";
        if ("duplicates".equals(type)) return "Duplicates";
        return "Residual";
    }

    private int toolAccent(String type) {
        if ("junk".equals(type)) return TEAL;
        if ("large".equals(type)) return AMBER;
        if ("duplicates".equals(type)) return BLUE;
        return ROSE;
    }

    private void requestToolAccess(String type) {
        new AlertDialog.Builder(this)
                .setTitle(toolTitle(type) + " Access")
                .setMessage("इस tool को पूरे shared storage में real scan करने के लिए Full Storage Access चाहिए। Permission ON करके वापस आते ही यह tool अपने-आप scan शुरू करेगा।")
                .setNegativeButton("Cancel", (d,w) -> pendingToolAfterAccess = null)
                .setPositiveButton("ALLOW", (d,w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                }).show();
    }

    private void showToolScan(String type) {
        getWindow().getDecorView().setTag("toolScan");
        LinearLayout root = column();
        root.setPadding(dp(20), dp(26), dp(20), dp(28));
        root.setBackgroundColor(BG);

        int accent = toolAccent(type);
        TextView title = text(toolTitle(type), 28, INK, true);
        String explain = "junk".equals(type) ? "Safe temporary/cache files scan हो रहे हैं"
                : "large".equals(type) ? "100 MB से बड़ी files scan हो रही हैं"
                : "duplicates".equals(type) ? "Same-size files का SHA-256 hash compare हो रहा है"
                : "Empty folders और old residual/temp candidates scan हो रहे हैं";
        root.addView(title);
        root.addView(space(5));
        root.addView(text(explain, 13, MUTED, false));
        root.addView(space(24));

        ScanRing ring = new ScanRing(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(205), dp(205));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(ring, rlp);

        TextView path = text("Preparing…", 12, MUTED, false);
        path.setGravity(Gravity.CENTER);
        path.setMaxLines(2);
        root.addView(space(9));
        root.addView(path, matchWrap());

        root.addView(space(18));
        LinearLayout live = card();
        live.setPadding(dp(18), dp(15), dp(18), dp(15));
        TextView scanned = text("0 files scanned", 16, INK, true);
        TextView analyzed = text("0 B analyzed", 13, MUTED, false);
        TextView found = text("0 candidates", 14, accent, true);
        TextView reclaim = text("0 B found", 13, MUTED, false);
        live.addView(text("LIVE " + toolTitle(type).toUpperCase(Locale.ROOT), 12, accent, true));
        live.addView(space(8));
        live.addView(scanned);
        live.addView(space(4));
        live.addView(analyzed);
        live.addView(space(8));
        live.addView(found);
        live.addView(space(4));
        live.addView(reclaim);
        root.addView(live, matchWrap());

        Space flex = new Space(this);
        root.addView(flex, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView back = actionButton("BACK", Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> handleBack());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        setContentView(root);
        fadeIn(root);

        scanTool(type, new ToolCallback() {
            @Override public void live(String currentPath, int files, long bytes, int items, long itemBytes) {
                runOnUiThread(() -> {
                    ring.setLiveFiles(files);
                    path.setText(shortPath(currentPath));
                    scanned.setText(files + " files scanned");
                    analyzed.setText(format(bytes) + " analyzed");
                    found.setText(items + ("duplicates".equals(type) ? " duplicate copies" : " candidates"));
                    reclaim.setText(format(itemBytes) + ("large".equals(type) ? " listed" : " reclaimable"));
                });
            }

            @Override public void done(ToolResult result) {
                runOnUiThread(() -> {
                    ring.setDone();
                    showToolResult(result);
                });
            }
        });
    }

    private void showToolResult(ToolResult result) {
        activeToolResult = result;
        getWindow().getDecorView().setTag("toolResult");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(24), dp(16), dp(28));

        int accent = toolAccent(result.type);
        root.addView(text(toolTitle(result.type), 28, INK, true));
        root.addView(space(5));
        String sub = "duplicates".equals(result.type)
                ? result.groups + " duplicate groups • first copy हमेशा keep"
                : result.scannedFiles + " files checked • " + format(result.scannedBytes) + " analyzed";
        root.addView(text(sub, 13, MUTED, false));
        root.addView(space(18));

        LinearLayout summary = card();
        summary.setPadding(dp(18), dp(16), dp(18), dp(16));
        summary.addView(text(result.items.size() + " items found", 17, INK, true));
        summary.addView(space(5));
        summary.addView(text(format(result.totalBytes), 26, accent, true));
        summary.addView(space(4));
        summary.addView(text("large".equals(result.type) ? "total size listed" : "potential reclaim", 12, MUTED, false));
        root.addView(summary, matchWrap());

        if (result.items.isEmpty()) {
            root.addView(space(18));
            LinearLayout empty = card();
            empty.setPadding(dp(18), dp(22), dp(18), dp(22));
            empty.addView(centerText("✓", 38, TEAL, true));
            empty.addView(space(8));
            empty.addView(centerText("इस category में अभी कुछ नहीं मिला", 15, INK, true));
            root.addView(empty, matchWrap());
        } else {
            root.addView(section("Found Items"));
            int limit = Math.min(80, result.items.size());
            for (int i=0; i<limit; i++) {
                ToolItem item = result.items.get(i);
                AppFileInfo info = item.directory ? AppFileInfo.folder() : describeAppFile(item.file);

                LinearLayout itemCard = card();
                itemCard.setPadding(dp(15), dp(13), dp(15), dp(13));

                if (!item.directory) {
                    LinearLayout appRow = row();
                    appRow.setGravity(Gravity.CENTER_VERTICAL);
                    Drawable appIcon = appIcon(info.packageName);
                    if (appIcon != null) {
                        ImageView iv = new ImageView(this);
                        iv.setImageDrawable(appIcon);
                        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        appRow.addView(iv, new LinearLayout.LayoutParams(dp(34), dp(34)));
                        appRow.addView(spaceH(9));
                    }
                    LinearLayout appText = column();
                    appText.addView(text(info.appName, 13, INK, true));
                    if (info.packageName != null && info.packageName.length() > 0) {
                        appText.addView(text(info.packageName, 9, MUTED, false));
                    }
                    appRow.addView(appText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    itemCard.addView(appRow, matchWrap());
                    itemCard.addView(space(9));
                }

                itemCard.addView(text(item.file.getName().length() == 0 ? item.file.getAbsolutePath() : item.file.getName(), 14, INK, true));
                itemCard.addView(space(3));
                itemCard.addView(text(item.directory ? "Folder" : format(item.bytes), 12, accent, true));

                if (!item.directory) {
                    itemCard.addView(space(7));
                    itemCard.addView(text(info.category, 12, INK, true));
                    itemCard.addView(space(5));
                    TextView risk = pill(info.status, info.statusColor, info.statusBg);
                    itemCard.addView(risk);
                    itemCard.addView(space(6));
                    itemCard.addView(text(info.explanation, 11, MUTED, false));
                } else {
                    itemCard.addView(space(4));
                    itemCard.addView(text(item.note, 11, MUTED, false));
                }

                itemCard.addView(space(6));
                itemCard.addView(text(shortPath(item.file.getAbsolutePath()), 10, MUTED, false));

                if (!item.directory) {
                    itemCard.addView(space(10));
                    LinearLayout actions = row();

                    String openLabel = info.detailsOnly ? "DETAILS"
                            : isImageFile(item.file) ? "PREVIEW"
                            : isVideoFile(item.file) ? "PLAY"
                            : "OPEN";
                    TextView open = pill(openLabel, PURPLE, Color.rgb(239,236,255));
                    touch(open);
                    open.setOnClickListener(v -> {
                        if (info.detailsOnly) showFileDetails(item.file, info);
                        else openFoundFile(item.file);
                    });
                    actions.addView(open, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    if ("large".equals(result.type) || "duplicates".equals(result.type)) {
                        actions.addView(spaceH(8));
                        if (info.protectedFile) {
                            TextView keep = pill("KEEP / PROTECTED", Color.rgb(18,145,123), Color.rgb(228,252,248));
                            actions.addView(keep, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                        } else {
                            TextView del = pill("DELETE", ROSE, Color.rgb(255,238,243));
                            touch(del);
                            del.setOnClickListener(v -> confirmDeleteToolItem(result, item));
                            actions.addView(del, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                        }
                    }
                    itemCard.addView(actions, matchWrap());
                }
                root.addView(itemCard, matchWrap());
                root.addView(space(9));
            }
            if (result.items.size() > limit) {
                root.addView(text("+ " + (result.items.size()-limit) + " more items", 12, MUTED, false));
            }
        }

        if (("junk".equals(result.type) || "residual".equals(result.type) || "duplicates".equals(result.type))
                && !result.items.isEmpty()) {
            root.addView(space(16));
            String label = "duplicates".equals(result.type) ? "DELETE DUPLICATE COPIES"
                    : "residual".equals(result.type) ? "CLEAN SAFE RESIDUAL"
                    : "CLEAN JUNK & CACHE";
            TextView clean = actionButton(label, accent);
            touch(clean);
            clean.setOnClickListener(v -> confirmCleanToolResult(result));
            root.addView(clean, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        }

        root.addView(space(10));
        TextView rescan = actionButton("RESCAN", Color.WHITE);
        rescan.setTextColor(PURPLE);
        touch(rescan);
        rescan.setOnClickListener(v -> showToolScan(result.type));
        root.addView(rescan, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        root.addView(space(9));
        TextView back = actionButton("BACK", Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> handleBack());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        scroll.addView(root);
        setContentView(scroll);
        fadeIn(root);
    }

    private AppFileInfo describeAppFile(File file) {
        String path = file.getAbsolutePath();
        String lowPath = path.toLowerCase(Locale.ROOT);
        String name = file.getName().toLowerCase(Locale.ROOT);
        String pkg = packageFromPath(path);
        String appName = resolveAppName(pkg, path);

        if (isWhatsAppChatBackup(name, lowPath)) {
            boolean dated = containsDate(name);
            boolean current = name.startsWith("msgstore.db.") || name.equals("msgstore.db");
            if (current && !dated) {
                return new AppFileInfo(appName, pkg, "Encrypted Chat Backup",
                        "CURRENT BACKUP • KEEP",
                        Color.rgb(18,145,123), Color.rgb(228,252,248),
                        "यह current/latest local chat backup है। Delete करने से restore option प्रभावित हो सकता है।",
                        true, true);
            }
            return new AppFileInfo(appName, pkg, "Encrypted Chat Backup",
                    "OLD BACKUP • REVIEW",
                    AMBER, Color.rgb(255,247,230),
                    "यह dated/older local chat backup है। Delete करने पर उस तारीख की local restore history खत्म हो सकती है।",
                    false, true);
        }

        if (isDatabaseLike(name, lowPath)) {
            boolean old = containsDate(name) || name.endsWith(".bak") || name.endsWith(".old") ||
                    lowPath.contains("/backup/") || lowPath.contains("/backups/");
            if (old) {
                return new AppFileInfo(appName, pkg, "App Database / Backup",
                        "BACKUP • REVIEW",
                        AMBER, Color.rgb(255,247,230),
                        "यह app database/backup file है। Delete करने से पुराने restore/history data पर असर पड़ सकता है।",
                        false, true);
            }
            return new AppFileInfo(appName, pkg, "Active App Database",
                    "IMPORTANT • KEEP",
                    Color.rgb(18,145,123), Color.rgb(228,252,248),
                    "यह app का active/current database लग रहा है। इसे delete करने से app data या history खराब हो सकती है।",
                    true, true);
        }

        if (isImageFile(file)) {
            return new AppFileInfo(appName, pkg, "Photo / Image",
                    "PERSONAL FILE • REVIEW", BLUE, Color.rgb(235,245,255),
                    "यह image file है। Preview करके ही delete करें।", false, false);
        }

        if (isVideoFile(file)) {
            return new AppFileInfo(appName, pkg, "Video",
                    "PERSONAL FILE • REVIEW", BLUE, Color.rgb(235,245,255),
                    "यह video file है। Play/preview करके ही delete करें।", false, false);
        }

        if (isAudioFile(name)) {
            return new AppFileInfo(appName, pkg, "Audio / Voice / Music",
                    "USER MEDIA • REVIEW", BLUE, Color.rgb(235,245,255),
                    "यह audio/media file है। Delete करने से saved audio या voice content हट सकता है।", false, false);
        }

        if (lowPath.contains("/cache/") || lowPath.contains("/.cache/") ||
                name.endsWith(".tmp") || name.endsWith(".temp")) {
            return new AppFileInfo(appName, pkg, "App Cache / Temporary File",
                    "CACHE • USUALLY SAFE", Color.rgb(18,145,123), Color.rgb(228,252,248),
                    "यह cache/temporary data है। App जरूरत पड़ने पर इसे दोबारा बना सकता है।", false, false);
        }

        if (name.endsWith(".apk")) {
            return new AppFileInfo(appName, pkg, "Android Installer (APK)",
                    "INSTALLER • REVIEW", AMBER, Color.rgb(255,247,230),
                    "यह APK installer file है। Installed app पर असर नहीं पड़ता, लेकिन future reinstall के लिए काम आ सकती है।",
                    false, false);
        }

        if (isArchiveFile(name)) {
            return new AppFileInfo(appName, pkg, "Archive / Compressed File",
                    "ARCHIVE • REVIEW", AMBER, Color.rgb(255,247,230),
                    "यह ZIP/RAR/7Z जैसी archive file है। Open करके contents पहचानने के बाद delete करें।",
                    false, false);
        }

        if (lowPath.contains("/offline/") || lowPath.contains("/downloads/") ||
                lowPath.contains("/download/")) {
            return new AppFileInfo(appName, pkg, "Downloaded / Offline Content",
                    "OFFLINE DATA • REVIEW", AMBER, Color.rgb(255,247,230),
                    "यह downloaded/offline content हो सकता है। Delete करने पर app में offline access खत्म हो सकता है।",
                    false, false);
        }

        if (lowPath.contains("/backup/") || lowPath.contains("/backups/") ||
                name.contains("backup") || name.endsWith(".bak")) {
            return new AppFileInfo(appName, pkg, "App Backup",
                    "BACKUP • REVIEW", AMBER, Color.rgb(255,247,230),
                    "यह backup file लग रही है। Delete करने पर restore history कम हो सकती है।",
                    false, true);
        }

        return new AppFileInfo(appName, pkg, fileTypeFromExtension(name),
                "UNKNOWN / APP FILE • REVIEW", PURPLE, Color.rgb(239,236,255),
                "यह app/storage file है। Type पूरी तरह सुरक्षित रूप से पहचान नहीं पाया, इसलिए delete से पहले OPEN/DETAILS से जांचें।",
                false, false);
    }

    private String packageFromPath(String path) {
        String normalized = path.replace('\\','/');
        String[] roots = {"/Android/media/", "/Android/data/", "/Android/obb/"};
        for (String root : roots) {
            int i = normalized.indexOf(root);
            if (i < 0) {
                i = normalized.toLowerCase(Locale.ROOT).indexOf(root.toLowerCase(Locale.ROOT));
            }
            if (i >= 0) {
                String tail = normalized.substring(i + root.length());
                int slash = tail.indexOf('/');
                String pkg = slash >= 0 ? tail.substring(0, slash) : tail;
                if (pkg.contains(".") && pkg.length() > 3) return pkg;
            }
        }
        return null;
    }

    private String resolveAppName(String pkg, String path) {
        if (pkg != null) {
            String known = knownPackageName(pkg);
            if (known != null) return known;
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(ai);
                if (label != null && label.length() > 0) return label.toString();
            } catch (Exception ignored) {}
        }

        String low = path.toLowerCase(Locale.ROOT);
        if (low.contains("whatsapp business")) return "WhatsApp Business";
        if (low.contains("/whatsapp/")) return "WhatsApp";
        if (low.contains("/telegram/")) return "Telegram";
        if (low.contains("/instagram/")) return "Instagram";
        if (low.contains("/facebook/")) return "Facebook";
        if (low.contains("/messenger/")) return "Messenger";
        if (low.contains("/snapchat/")) return "Snapchat";
        if (low.contains("/signal/")) return "Signal";
        if (low.contains("/youtube music/")) return "YouTube Music";
        if (low.contains("/youtube/")) return "YouTube";
        if (low.contains("/spotify/")) return "Spotify";
        if (pkg != null) return pkg;
        return "Device / App Storage";
    }

    private String knownPackageName(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.equals("com.whatsapp")) return "WhatsApp";
        if (p.equals("com.whatsapp.w4b")) return "WhatsApp Business";
        if (p.equals("org.telegram.messenger")) return "Telegram";
        if (p.equals("org.thunderdog.challegram")) return "Telegram X";
        if (p.equals("com.instagram.android")) return "Instagram";
        if (p.equals("com.facebook.katana")) return "Facebook";
        if (p.equals("com.facebook.orca")) return "Messenger";
        if (p.equals("com.android.chrome")) return "Google Chrome";
        if (p.equals("com.google.android.youtube")) return "YouTube";
        if (p.equals("com.google.android.apps.youtube.music")) return "YouTube Music";
        if (p.equals("com.google.android.apps.maps")) return "Google Maps";
        if (p.equals("com.snapchat.android")) return "Snapchat";
        if (p.equals("org.thoughtcrime.securesms")) return "Signal";
        if (p.equals("com.twitter.android") || p.equals("com.x.android")) return "X / Twitter";
        if (p.equals("com.zhiliaoapp.musically")) return "TikTok";
        if (p.equals("com.lemon.lvoverseas")) return "CapCut";
        if (p.equals("org.videolan.vlc")) return "VLC";
        if (p.startsWith("com.mxtech.videoplayer")) return "MX Player";
        if (p.equals("com.microsoft.teams")) return "Microsoft Teams";
        if (p.equals("us.zoom.videomeetings")) return "Zoom";
        if (p.equals("com.google.android.gm")) return "Gmail";
        if (p.equals("com.google.android.apps.docs")) return "Google Drive";
        if (p.equals("com.microsoft.skydrive")) return "OneDrive";
        if (p.equals("com.dropbox.android")) return "Dropbox";
        if (p.equals("com.spotify.music")) return "Spotify";
        if (p.equals("com.netflix.mediaclient")) return "Netflix";
        if (p.equals("com.amazon.avod.thirdpartyclient")) return "Prime Video";
        return null;
    }

    private Drawable appIcon(String pkg) {
        if (pkg == null) return null;
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isWhatsAppChatBackup(String name, String lowPath) {
        return lowPath.contains("whatsapp") && name.startsWith("msgstore") &&
                (name.contains(".crypt") || name.endsWith(".db"));
    }

    private boolean isDatabaseLike(String name, String lowPath) {
        return name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".sqlite3") ||
                name.matches(".*\\.crypt\\d+$") || lowPath.contains("/databases/");
    }

    private boolean containsDate(String name) {
        return name.matches(".*(19|20)\\d{2}[-_.](0[1-9]|1[0-2])[-_.]([0-2]\\d|3[01]).*");
    }

    private boolean isAudioFile(String name) {
        return name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".aac") ||
                name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".opus") ||
                name.endsWith(".flac") || name.endsWith(".amr");
    }

    private boolean isArchiveFile(String name) {
        return name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ||
                name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".tgz");
    }

    private String fileTypeFromExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length()-1) {
            return name.substring(dot+1).toUpperCase(Locale.ROOT) + " File";
        }
        return "App / Storage File";
    }

    private void showFileDetails(File file, AppFileInfo info) {
        StringBuilder b = new StringBuilder();
        b.append("App: ").append(info.appName).append("\n");
        if (info.packageName != null) b.append("Package: ").append(info.packageName).append("\n");
        b.append("Type: ").append(info.category).append("\n");
        b.append("Status: ").append(info.status).append("\n");
        b.append("Size: ").append(format(file.length())).append("\n\n");
        b.append(info.explanation).append("\n\n");
        b.append(file.getAbsolutePath());

        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setMessage(b.toString())
                .setNegativeButton("CLOSE", null)
                .setPositiveButton(info.protectedFile ? "KEEP" : "OPEN", (d,w) -> {
                    if (!info.protectedFile) openExternalFile(file);
                })
                .show();
    }

    private boolean isImageFile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
                n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
                n.endsWith(".heic") || n.endsWith(".heif");
    }

    private boolean isVideoFile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".mov") ||
                n.endsWith(".avi") || n.endsWith(".webm") || n.endsWith(".3gp") ||
                n.endsWith(".m4v");
    }

    private String mimeForFile(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length()-1) {
            String ext = name.substring(dot+1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "*/*";
    }

    private void openFoundFile(File file) {
        haptic();
        if (!file.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("File not found")
                    .setMessage("यह file अब storage में मौजूद नहीं है।")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (isImageFile(file)) {
            showPhotoPreview(file);
        } else if (isVideoFile(file) || isAudioFile(file.getName().toLowerCase(Locale.ROOT))) {
            showMediaPreview(file, isVideoFile(file));
        } else if (isPdfFile(file)) {
            showPdfPreview(file);
        } else if (isTextFile(file)) {
            showTextPreview(file);
        } else {
            showInternalFilePage(file);
        }
    }

    private void showPhotoPreview(File file) {
        getWindow().getDecorView().setTag("preview");
        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(12,14,20));
        root.setPadding(dp(12), dp(12), dp(12), dp(14));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("‹ BACK", Color.WHITE, Color.rgb(40,43,54));
        touch(back);
        back.setOnClickListener(v -> returnFromPreview());
        top.addView(back);
        TextView name = text(file.getName(), 14, Color.WHITE, true);
        name.setPadding(dp(12),0,0,0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, matchWrap());
        root.addView(space(10));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(Color.BLACK);
        try {
            image.setImageURI(Uri.fromFile(file));
        } catch (Exception ignored) {}
        root.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(space(10));
        TextView info = centerText(format(file.length()) + "  •  " + shortPath(file.getAbsolutePath()), 11, Color.LTGRAY, false);
        root.addView(info, matchWrap());

        root.addView(space(8));
        TextView external = actionButton("OPEN IN OTHER APP", PURPLE);
        touch(external);
        external.setOnClickListener(v -> openExternalFile(file));
        root.addView(external, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        setContentView(root);
        fadeIn(root);
    }

    private void showMediaPreview(File file, boolean videoMode) {
        releasePreviewResources();
        getWindow().getDecorView().setTag("preview");

        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(12,14,20));
        root.setPadding(dp(12), dp(12), dp(12), dp(14));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("‹ BACK", Color.WHITE, Color.rgb(40,43,54));
        touch(back);
        back.setOnClickListener(v -> returnFromPreview());
        top.addView(back);
        TextView name = text(file.getName(), 14, Color.WHITE, true);
        name.setPadding(dp(12),0,0,0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, matchWrap());
        root.addView(space(10));

        TextView status = centerText(videoMode ? "Loading video…" : "Loading audio…", 12, Color.LTGRAY, false);

        if (!videoMode) {
            LinearLayout audioHero = column();
            audioHero.setGravity(Gravity.CENTER);
            audioHero.setBackgroundColor(Color.rgb(22,25,34));
            audioHero.addView(centerText("♪", 64, TEAL, true));
            audioHero.addView(space(8));
            audioHero.addView(centerText(file.getName(), 15, Color.WHITE, true));
            root.addView(audioHero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));
            root.addView(space(8));
        }

        PlayerView playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setBackgroundColor(Color.BLACK);
        int playerHeight = videoMode ? 0 : dp(110);
        LinearLayout.LayoutParams playerLp = videoMode
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, playerHeight);
        root.addView(playerView, playerLp);

        root.addView(space(8));
        root.addView(status, matchWrap());
        root.addView(space(8));

        TextView external = actionButton("OPEN IN OTHER PLAYER", PURPLE);
        touch(external);
        external.setOnClickListener(v -> openExternalFile(file));
        root.addView(external, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        setContentView(root);
        fadeIn(root);

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            activePlayer = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(activePlayer);
            activePlayer.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        status.setText((videoMode ? "VIDEO READY" : "AUDIO READY") + " • " + format(file.length()));
                    } else if (state == Player.STATE_BUFFERING) {
                        status.setText("Buffering… • " + format(file.length()));
                    } else if (state == Player.STATE_ENDED) {
                        status.setText("Playback complete • " + format(file.length()));
                    }
                }
                @Override public void onPlayerError(PlaybackException error) {
                    status.setText("इस codec को device/player decode नहीं कर पा रहा • Other Player try करें");
                }
            });
            activePlayer.setMediaItem(MediaItem.fromUri(uri));
            activePlayer.prepare();
            activePlayer.setPlayWhenReady(true);
        } catch (Exception e) {
            status.setText("Player start नहीं हुआ • Other Player try करें");
        }
    }

    private boolean isPdfFile(File f) {
        return f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private boolean isTextFile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".txt") || n.endsWith(".log") || n.endsWith(".json") ||
                n.endsWith(".xml") || n.endsWith(".csv") || n.endsWith(".md") ||
                n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".ini") ||
                n.endsWith(".conf") || n.endsWith(".properties");
    }

    private void showPdfPreview(File file) {
        releasePreviewResources();
        getWindow().getDecorView().setTag("preview");

        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(20,22,30));
        root.setPadding(dp(10), dp(10), dp(10), dp(12));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("‹ BACK", Color.WHITE, Color.rgb(40,43,54));
        touch(back);
        back.setOnClickListener(v -> returnFromPreview());
        top.addView(back);
        TextView name = text(file.getName(), 14, Color.WHITE, true);
        name.setPadding(dp(12),0,0,0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, matchWrap());
        root.addView(space(8));

        ImageView pageView = new ImageView(this);
        pageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pageView.setBackgroundColor(Color.WHITE);
        root.addView(pageView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView pageInfo = centerText("Opening PDF…", 12, Color.LTGRAY, false);
        root.addView(space(7));
        root.addView(pageInfo, matchWrap());

        LinearLayout controls = row();
        TextView prev = pill("‹ PREV", Color.WHITE, Color.rgb(55,58,72));
        TextView next = pill("NEXT ›", Color.WHITE, Color.rgb(55,58,72));
        controls.addView(prev, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        controls.addView(spaceH(8));
        controls.addView(next, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(space(7));
        root.addView(controls, matchWrap());

        setContentView(root);
        fadeIn(root);

        try {
            activePdfFd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            activePdfRenderer = new PdfRenderer(activePdfFd);
            final int[] index = {0};
            renderPdfPage(activePdfRenderer, index[0], pageView, pageInfo);

            prev.setOnClickListener(v -> {
                if (activePdfRenderer != null && index[0] > 0) {
                    index[0]--;
                    renderPdfPage(activePdfRenderer, index[0], pageView, pageInfo);
                }
            });
            next.setOnClickListener(v -> {
                if (activePdfRenderer != null && index[0] < activePdfRenderer.getPageCount()-1) {
                    index[0]++;
                    renderPdfPage(activePdfRenderer, index[0], pageView, pageInfo);
                }
            });
            touch(prev); touch(next);
        } catch (Exception e) {
            pageInfo.setText("PDF preview नहीं खुला • file damaged/encrypted हो सकती है");
        }
    }

    private void renderPdfPage(PdfRenderer renderer, int index, ImageView target, TextView info) {
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(index);
            int width = Math.max(900, getResources().getDisplayMetrics().widthPixels - dp(20));
            float ratio = (float) page.getHeight() / Math.max(1, page.getWidth());
            int height = Math.max(1, Math.round(width * ratio));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            target.setImageBitmap(bitmap);
            info.setText("Page " + (index+1) + " / " + renderer.getPageCount());
        } catch (Exception e) {
            info.setText("Page render failed");
        } finally {
            if (page != null) page.close();
        }
    }

    private void showTextPreview(File file) {
        releasePreviewResources();
        getWindow().getDecorView().setTag("preview");
        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(245,246,251));
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("‹ BACK", PURPLE, Color.WHITE);
        touch(back);
        back.setOnClickListener(v -> returnFromPreview());
        top.addView(back);
        TextView name = text(file.getName(), 14, INK, true);
        name.setPadding(dp(12),0,0,0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top, matchWrap());
        root.addView(space(8));

        ScrollView textScroll = new ScrollView(this);
        TextView body = text(readTextPreview(file), 12, INK, false);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(dp(10),dp(10),dp(10),dp(10));
        textScroll.addView(body);
        root.addView(textScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(space(8));
        TextView other = actionButton("OPEN IN OTHER APP", PURPLE);
        touch(other);
        other.setOnClickListener(v -> openExternalFile(file));
        root.addView(other, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        setContentView(root);
        fadeIn(root);
    }

    private String readTextPreview(File file) {
        StringBuilder out = new StringBuilder();
        int chars = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null && chars < 500000) {
                out.append(line).append('\n');
                chars += line.length()+1;
            }
            if (br.readLine() != null) out.append("\n… preview limited to first 500 KB of text");
        } catch (Exception e) {
            return "इस file को text के रूप में read नहीं किया जा सका।";
        }
        return out.toString();
    }

    private void showInternalFilePage(File file) {
        releasePreviewResources();
        getWindow().getDecorView().setTag("preview");
        AppFileInfo info = describeAppFile(file);

        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = pill("‹ BACK", PURPLE, Color.WHITE);
        touch(back);
        back.setOnClickListener(v -> returnFromPreview());
        top.addView(back);
        root.addView(top, matchWrap());
        root.addView(space(20));

        root.addView(text(file.getName(), 22, INK, true));
        root.addView(space(10));
        root.addView(text("App: " + info.appName, 14, INK, true));
        root.addView(space(6));
        root.addView(text("Type: " + info.category, 13, MUTED, false));
        root.addView(space(6));
        root.addView(text("Size: " + format(file.length()), 13, MUTED, false));
        root.addView(space(10));
        root.addView(pill(info.status, info.statusColor, info.statusBg));
        root.addView(space(14));
        root.addView(text(info.explanation, 13, MUTED, false));
        root.addView(space(14));
        TextView path = text(file.getAbsolutePath(), 11, MUTED, false);
        path.setTextIsSelectable(true);
        root.addView(path);

        Space flex = new Space(this);
        root.addView(flex, new LinearLayout.LayoutParams(1,0,1f));

        TextView other = actionButton("OPEN IN COMPATIBLE APP", PURPLE);
        touch(other);
        other.setOnClickListener(v -> openExternalFile(file));
        root.addView(other, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        setContentView(root);
        fadeIn(root);
    }

    private void openExternalFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeForFile(file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Open file")
                    .setMessage("इस file type को खोलने के लिए compatible app नहीं मिला।")
                    .setPositiveButton("OK", null).show();
        }
    }

    private void returnFromPreview() {
        haptic();
        releasePreviewResources();
        if (activeToolResult != null) showToolResult(activeToolResult);
        else showHome();
    }

    private void releasePreviewResources() {
        try {
            if (activePlayer != null) {
                activePlayer.stop();
                activePlayer.release();
                activePlayer = null;
            }
        } catch (Exception ignored) {}
        try {
            if (activePdfRenderer != null) {
                activePdfRenderer.close();
                activePdfRenderer = null;
            }
        } catch (Exception ignored) {}
        try {
            if (activePdfFd != null) {
                activePdfFd.close();
                activePdfFd = null;
            }
        } catch (Exception ignored) {}
    }

    private void confirmDeleteToolItem(ToolResult result, ToolItem item) {
        AppFileInfo info = describeAppFile(item.file);
        if (info.protectedFile) {
            showFileDetails(item.file, info);
            return;
        }
        String warning = "duplicates".equals(result.type)
                ? "यह duplicate copy STS Trash में जाएगी। Original/first copy रखी जाएगी।"
                : "यह file पहले STS Trash में जाएगी। 7 दिन तक Restore कर सकते हैं; उसके बाद auto-delete होगी।";
        new AlertDialog.Builder(this)
                .setTitle("Move to STS Trash?")
                .setMessage(warning + "\n\nApp: " + info.appName + "\nType: " + info.category +
                        "\nStatus: " + info.status + "\n\n" + item.file.getAbsolutePath() + "\n" + format(item.bytes))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("MOVE TO TRASH", (d,w) -> {
                    boolean ok = moveToTrash(item.file);
                    if (ok) {
                        result.items.remove(item);
                        result.totalBytes = Math.max(0, result.totalBytes - item.bytes);
                        showToolResult(result);
                    } else {
                        new AlertDialog.Builder(this).setTitle("Move failed")
                                .setMessage("इस file को STS Trash में move नहीं किया जा सका।")
                                .setPositiveButton("OK", null).show();
                    }
                }).show();
    }

    private void confirmCleanToolResult(ToolResult result) {
        String msg;
        if ("duplicates".equals(result.type)) {
            msg = "हर duplicate hash group की पहली/original copy रखी जाएगी। Current databases/backups जैसे protected app files auto-delete नहीं होंगे।";
        } else if ("residual".equals(result.type)) {
            msg = "केवल scan में मिले safe empty folders और non-personal old residual/temp candidates delete होंगे। Important app data protected रहेगा।";
        } else {
            msg = "केवल safe junk/cache candidates delete होंगे। Personal media और important app database/backup protected रहेंगे।";
        }

        int deletable = 0;
        long bytes = 0;
        for (ToolItem item : result.items) {
            if (item.directory || !describeAppFile(item.file).protectedFile) {
                deletable++;
                bytes += item.bytes;
            }
        }
        if (deletable == 0) {
            new AlertDialog.Builder(this)
                    .setTitle("Nothing safe to clean")
                    .setMessage("इस list में अभी सभी items protected/important हैं।")
                    .setPositiveButton("OK", null).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Clean")
                .setMessage(msg + "\n\nSelected: " + deletable + " items • " + format(bytes))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("CLEAN", (d,w) -> cleanToolResult(result))
                .show();
    }

    private void cleanToolResult(ToolResult result) {
        new Thread(() -> {
            long freed = 0;
            int count = 0;
            int failed = 0;
            List<ToolItem> copy = new ArrayList<>(result.items);
            boolean useTrash = "duplicates".equals(result.type);
            for (ToolItem item : copy) {
                try {
                    if (!item.directory && describeAppFile(item.file).protectedFile) continue;
                    long n = item.bytes;
                    boolean ok = useTrash ? moveToTrash(item.file) : item.file.delete();
                    if (ok) { freed += n; count++; }
                    else failed++;
                } catch (Exception e) { failed++; }
            }
            long f = freed;
            int c = count;
            int x = failed;
            runOnUiThread(() -> {
                if (useTrash) showTrashMovedDone(f, c, x);
                else showCleanDone(f, c, x);
            });
        }, "sts-tool-clean").start();
    }

    private File trashDir() {
        File base = getExternalFilesDir(null);
        File dir = new File(base != null ? base : getFilesDir(), "STS_Trash");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private boolean moveToTrash(File source) {
        if (source == null || !source.exists()) return false;
        File dir = trashDir();
        long now = System.currentTimeMillis();
        String safeName = source.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        File target = new File(dir, now + "_" + safeName);
        File meta = new File(dir, target.getName() + ".stsmeta");

        boolean moved = source.renameTo(target);
        if (!moved && source.isFile()) {
            moved = copyFile(source, target);
            if (moved && !source.delete()) {
                target.delete();
                moved = false;
            }
        }
        if (!moved) return false;

        try (FileWriter w = new FileWriter(meta)) {
            w.write(source.getAbsolutePath());
            w.write("\n");
            w.write(Long.toString(now));
            w.write("\n");
        } catch (Exception e) {
            // file remains safely in STS Trash even if metadata write fails
        }
        return true;
    }

    private boolean copyFile(File source, File target) {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[256*1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf,0,n);
            out.flush();
            return true;
        } catch (Exception e) {
            try { target.delete(); } catch (Exception ignored) {}
            return false;
        }
    }

    private String[] readTrashMeta(File meta) {
        String[] out = new String[]{"", "0"};
        try (BufferedReader br = new BufferedReader(new FileReader(meta))) {
            String p = br.readLine();
            String t = br.readLine();
            if (p != null) out[0] = p;
            if (t != null) out[1] = t;
        } catch (Exception ignored) {}
        return out;
    }

    private void showTrashScreen() {
        releasePreviewResources();
        getWindow().getDecorView().setTag("trash");
        purgeExpiredTrash();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(16),dp(24),dp(16),dp(28));

        root.addView(text("STS Trash",28,INK,true));
        root.addView(space(5));
        root.addView(text("Deleted user files 7 दिन तक restore की जा सकती हैं",13,MUTED,false));
        root.addView(space(18));

        File dir = trashDir();
        File[] metas = dir.listFiles((d,n) -> n.endsWith(".stsmeta"));
        if (metas == null) metas = new File[0];
        java.util.Arrays.sort(metas, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));

        if (metas.length == 0) {
            LinearLayout empty = card();
            empty.setPadding(dp(18),dp(26),dp(18),dp(26));
            empty.addView(centerText("♻",44,TEAL,true));
            empty.addView(space(8));
            empty.addView(centerText("STS Trash खाली है",16,INK,true));
            root.addView(empty,matchWrap());
        } else {
            for (File meta : metas) {
                String baseName = meta.getName().substring(0,meta.getName().length()-8);
                File data = new File(dir,baseName);
                if (!data.exists()) { meta.delete(); continue; }
                String[] md = readTrashMeta(meta);
                String original = md[0];

                LinearLayout c = card();
                c.setPadding(dp(15),dp(13),dp(15),dp(13));
                c.addView(text(data.getName().replaceFirst("^\\d+_",""),14,INK,true));
                c.addView(space(4));
                c.addView(text(format(data.length()),12,PURPLE,true));
                c.addView(space(4));
                c.addView(text("Original: " + shortPath(original),10,MUTED,false));

                LinearLayout actions = row();
                actions.setPadding(0,dp(10),0,0);
                TextView restore = pill("RESTORE",Color.rgb(18,145,123),Color.rgb(228,252,248));
                TextView del = pill("DELETE FOREVER",ROSE,Color.rgb(255,238,243));
                touch(restore); touch(del);
                restore.setOnClickListener(v -> {
                    if (restoreTrashItem(data,meta,original)) showTrashScreen();
                    else new AlertDialog.Builder(this).setTitle("Restore failed")
                            .setMessage("Original location पर file restore नहीं हो सकी।")
                            .setPositiveButton("OK",null).show();
                });
                del.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("Delete permanently?")
                        .setMessage("यह file STS Trash से भी हमेशा के लिए delete हो जाएगी।")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("DELETE",(d,w) -> {
                            data.delete(); meta.delete(); showTrashScreen();
                        }).show());
                actions.addView(restore,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
                actions.addView(spaceH(8));
                actions.addView(del,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
                c.addView(actions,matchWrap());
                root.addView(c,matchWrap());
                root.addView(space(9));
            }
        }

        root.addView(space(14));
        TextView back = actionButton("BACK",Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> showHome());
        root.addView(back,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));

        scroll.addView(root);
        setContentView(scroll);
        fadeIn(root);
    }

    private boolean restoreTrashItem(File data, File meta, String originalPath) {
        if (originalPath == null || originalPath.length() == 0) return false;
        File target = new File(originalPath);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (target.exists()) {
            target = new File(parent, "restored_" + System.currentTimeMillis() + "_" + target.getName());
        }

        boolean ok = data.renameTo(target);
        if (!ok && data.isFile()) {
            ok = copyFile(data,target);
            if (ok && !data.delete()) {
                target.delete();
                ok = false;
            }
        }
        if (ok) meta.delete();
        return ok;
    }

    private void purgeExpiredTrashAsync() {
        new Thread(this::purgeExpiredTrash,"sts-trash-expiry").start();
    }

    private void purgeExpiredTrash() {
        File dir = trashDir();
        File[] metas = dir.listFiles((d,n) -> n.endsWith(".stsmeta"));
        if (metas == null) return;
        long cutoff = System.currentTimeMillis() - 7L*24*60*60*1000;
        for (File meta : metas) {
            String[] md = readTrashMeta(meta);
            long when = 0;
            try { when = Long.parseLong(md[1]); } catch (Exception ignored) {}
            if (when > 0 && when < cutoff) {
                String baseName = meta.getName().substring(0,meta.getName().length()-8);
                new File(dir,baseName).delete();
                meta.delete();
            }
        }
    }

    private void showTrashMovedDone(long bytes, int count, int failed) {
        getWindow().getDecorView().setTag("done");
        LinearLayout root = column();
        root.setPadding(dp(24),dp(48),dp(24),dp(36));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);

        TextView icon = text("♻",62,TEAL,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(circle(Color.rgb(225,250,246)));
        root.addView(icon,new LinearLayout.LayoutParams(dp(130),dp(130)));
        root.addView(space(24));
        root.addView(centerText("Moved to STS Trash",28,INK,true));
        root.addView(space(10));
        root.addView(centerText(format(bytes),30,PURPLE,true));
        root.addView(centerText(count + " files protected for 7 days",14,MUTED,false));
        root.addView(space(8));
        root.addView(centerText(failed + " failed",12,MUTED,false));

        Space flex = new Space(this);
        root.addView(flex,new LinearLayout.LayoutParams(1,0,1f));
        TextView trash = actionButton("OPEN STS TRASH",PURPLE);
        touch(trash);
        trash.setOnClickListener(v -> showTrashScreen());
        root.addView(trash,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(58)));
        root.addView(space(8));
        TextView done = actionButton("DONE",Color.WHITE);
        done.setTextColor(PURPLE);
        touch(done);
        done.setOnClickListener(v -> showHome());
        root.addView(done,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
        setContentView(root);
        popIn(icon);
    }

    private void scanTool(String type, ToolCallback cb) {
        new Thread(() -> {
            ToolResult out = new ToolResult(type);
            File root = Environment.getExternalStorageDirectory();
            Set<String> seen = new HashSet<>();

            if ("duplicates".equals(type)) {
                Map<Long,List<File>> bySize = new HashMap<>();
                collectDuplicateCandidates(root, seen, 0, out, bySize, cb);
                hashDuplicateCandidates(bySize, out, cb);
            } else {
                walkTool(root, seen, 0, out, cb);
            }
            if ("large".equals(type)) {
                Collections.sort(out.items, (a,b) -> Long.compare(b.bytes, a.bytes));
            }
            cb.live("Scan complete", out.scannedFiles, out.scannedBytes, out.items.size(), out.totalBytes);
            cb.done(out);
        }, "sts-tool-"+type).start();
    }

    private void walkTool(File f, Set<String> seen, int depth, ToolResult out, ToolCallback cb) {
        if (f == null || depth > 22) return;
        String path;
        try { path = f.getCanonicalPath(); } catch (Exception e) { path = f.getAbsolutePath(); }
        if (!seen.add(path)) return;
        String low = path.toLowerCase(Locale.ROOT);
        if (isCritical(low)) return;

        if (f.isDirectory()) {
            File[] children;
            try { children = f.listFiles(); } catch (Exception e) { children = null; }
            if (children == null) return;

            if ("residual".equals(out.type) && children.length == 0 && depth > 1 && safeResidualDirectory(f)) {
                addToolItem(out, new ToolItem(f, 0, "Empty leftover folder", true));
            }

            for (File x : children) walkTool(x, seen, depth+1, out, cb);
            emitToolLive(out, cb, path, false);
            return;
        }

        long len = Math.max(0, f.length());
        long age = System.currentTimeMillis() - Math.max(0, f.lastModified());
        String name = f.getName().toLowerCase(Locale.ROOT);
        String parent = f.getParent() == null ? "" : f.getParent().toLowerCase(Locale.ROOT);

        out.scannedFiles++;
        out.scannedBytes += len;

        if ("junk".equals(out.type)) {
            boolean junk = (name.endsWith(".tmp") || name.endsWith(".temp") || name.endsWith(".log") ||
                    name.endsWith(".bak") || name.endsWith(".old") || name.endsWith(".dmp") ||
                    name.endsWith(".crash") || ((parent.contains("/cache") || parent.contains("/.cache") ||
                    parent.contains("/.thumbnails")) && !personal(name)));
            if (junk && !personal(name)) addToolItem(out, new ToolItem(f, len, "Safe temporary/cache candidate", false));
        } else if ("large".equals(out.type)) {
            if (len >= 100L*1024*1024) addToolItem(out, new ToolItem(f, len, "Large file • review before delete", false));
        } else if ("residual".equals(out.type)) {
            boolean residual = !personal(name) && age > 7L*24*60*60*1000 &&
                    (name.endsWith(".old") || name.endsWith(".bak") || name.endsWith(".log") ||
                     name.endsWith(".dmp") || name.endsWith(".crash") ||
                     parent.contains("/temp") || parent.contains("/tmp") || parent.contains("/logs"));
            if (residual) addToolItem(out, new ToolItem(f, len, "Old non-personal residual candidate", false));
        }

        emitToolLive(out, cb, path, false);
    }

    private boolean safeResidualDirectory(File f) {
        String p = f.getAbsolutePath().toLowerCase(Locale.ROOT);
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (p.endsWith("/dcim") || p.endsWith("/pictures") || p.endsWith("/movies") ||
                p.endsWith("/music") || p.endsWith("/documents") || p.endsWith("/download") ||
                p.endsWith("/downloads") || p.endsWith("/android") || p.endsWith("/notifications") ||
                p.endsWith("/ringtones") || p.endsWith("/podcasts") || p.endsWith("/alarms")) return false;
        return n.equals("cache") || n.equals(".cache") || n.equals("temp") || n.equals("tmp") ||
                n.equals("logs") || n.equals("crash") || n.startsWith(".tmp") || n.startsWith("temp_");
    }

    private void collectDuplicateCandidates(File f, Set<String> seen, int depth, ToolResult out,
                                            Map<Long,List<File>> bySize, ToolCallback cb) {
        if (f == null || depth > 22) return;
        String path;
        try { path = f.getCanonicalPath(); } catch (Exception e) { path = f.getAbsolutePath(); }
        if (!seen.add(path)) return;
        String low = path.toLowerCase(Locale.ROOT);
        if (isCritical(low)) return;

        if (f.isDirectory()) {
            File[] children;
            try { children = f.listFiles(); } catch (Exception e) { children = null; }
            if (children == null) return;
            for (File x : children) collectDuplicateCandidates(x, seen, depth+1, out, bySize, cb);
            return;
        }

        long len = Math.max(0, f.length());
        out.scannedFiles++;
        out.scannedBytes += len;
        if (len >= 256L*1024) {
            List<File> list = bySize.get(len);
            if (list == null) {
                list = new ArrayList<>();
                bySize.put(len, list);
            }
            list.add(f);
        }
        emitToolLive(out, cb, "Indexing: " + path, false);
    }

    private void hashDuplicateCandidates(Map<Long,List<File>> bySize, ToolResult out, ToolCallback cb) {
        Map<String,List<File>> groups = new HashMap<>();
        for (Map.Entry<Long,List<File>> e : bySize.entrySet()) {
            if (e.getValue().size() < 2) continue;
            for (File f : e.getValue()) {
                String hash = sha256(f);
                if (hash == null) continue;
                String key = e.getKey() + ":" + hash;
                List<File> group = groups.get(key);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.put(key, group);
                }
                group.add(f);
                cb.live("Hashing: " + f.getAbsolutePath(), out.scannedFiles, out.scannedBytes,
                        out.items.size(), out.totalBytes);
            }
        }

        for (List<File> group : groups.values()) {
            if (group.size() < 2) continue;
            Collections.sort(group, Comparator.comparing(File::getAbsolutePath));
            File keep = group.get(0);
            out.groups++;
            for (int i=1; i<group.size(); i++) {
                File dup = group.get(i);
                addToolItem(out, new ToolItem(dup, dup.length(),
                        "Duplicate • keeping: " + keep.getName(), false));
            }
        }
    }

    private String sha256(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64*1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length*2);
            for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void addToolItem(ToolResult out, ToolItem item) {
        out.items.add(item);
        out.totalBytes += Math.max(0, item.bytes);
    }

    private void emitToolLive(ToolResult out, ToolCallback cb, String path, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - out.lastLiveUpdate < 100) return;
        out.lastLiveUpdate = now;
        cb.live(path, out.scannedFiles, out.scannedBytes, out.items.size(), out.totalBytes);
    }

    private void beginScan(boolean deep) {
        haptic();
        if (Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess()) {
            pendingScanAfterAccess = true;
            pendingDeepAfterAccess = deep;
            requestAllFiles(deep);
            return;
        }
        startScan(deep);
    }

    private void startScan(boolean deep) {
        getWindow().getDecorView().setTag("scan");
        LinearLayout root = column();
        root.setPadding(dp(22), dp(24), dp(22), dp(28));
        root.setBackgroundColor(BG);

        TextView title = text(deep ? "Deep Scan" : "Smart Scan", 28, INK, true);
        TextView sub = text("Live storage analysis चल रहा है", 14, MUTED, false);
        root.addView(title); root.addView(space(5)); root.addView(sub); root.addView(space(22));

        ScanRing ring = new ScanRing(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(220), dp(220));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(ring, rlp);

        TextView status = text("Preparing storage map…", 13, MUTED, false);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(2);
        root.addView(space(10)); root.addView(status, matchWrap());

        root.addView(space(18));
        LinearLayout live = card();
        live.setPadding(dp(18), dp(16), dp(18), dp(16));
        TextView filesLive = text("0 files scanned", 16, INK, true);
        TextView dataLive = text("0 B analyzed", 13, MUTED, false);
        TextView safeLive = text("Safe junk: 0 • 0 B", 13, Color.rgb(18,145,123), true);
        TextView reviewLive = text("Review: 0 items", 13, AMBER, true);
        live.addView(text("LIVE SCAN", 12, PURPLE, true));
        live.addView(space(8));
        live.addView(filesLive);
        live.addView(space(4));
        live.addView(dataLive);
        live.addView(space(8));
        live.addView(safeLive);
        live.addView(space(4));
        live.addView(reviewLive);
        root.addView(live, matchWrap());

        root.addView(space(14));
        LinearLayout note = card();
        note.setPadding(dp(18), dp(14), dp(18), dp(14));
        note.addView(text("Safety lock active", 14, INK, true));
        note.addView(space(4));
        note.addView(text("Personal media auto-delete नहीं होगा। System-critical files protected रहेंगी।", 12, MUTED, false));
        root.addView(note, matchWrap());

        Space flex = new Space(this);
        root.addView(flex, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView back = actionButton("BACK", Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> handleBack());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        setContentView(root);
        fadeIn(root);

        scanStorage(new ScanCallback() {
            @Override public void progress(int pct, String label) {
                runOnUiThread(() -> {
                    if (pct >= 100) ring.setDone();
                    status.setText(label);
                });
            }

            @Override public void live(String currentPath, int files, long bytes,
                                       int safeCount, long safeBytes, int reviewCount) {
                runOnUiThread(() -> {
                    ring.setLiveFiles(files);
                    status.setText("Scanning: " + shortPath(currentPath));
                    filesLive.setText(files + " files scanned");
                    dataLive.setText(format(bytes) + " analyzed");
                    safeLive.setText("Safe junk: " + safeCount + " • " + format(safeBytes));
                    reviewLive.setText("Review: " + reviewCount + " items");
                });
            }

            @Override public void done(ScanSummary sum) {
                lastSummary = sum;
                runOnUiThread(() -> {
                    ring.setDone();
                    showResult(sum);
                });
            }
        });
    }

    private void showResult(ScanSummary s) {
        getWindow().getDecorView().setTag("result");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(18), dp(26), dp(18), dp(28));

        root.addView(text("Scan Result", 28, INK, true));
        root.addView(space(5));
        root.addView(text("Actual storage scan • delete से पहले review", 14, MUTED, false));
        root.addView(space(18));

        LinearLayout scanned = card();
        scanned.setPadding(dp(18), dp(17), dp(18), dp(17));
        scanned.addView(text((s.fullAccess ? "FULL STORAGE SCAN" : "LIMITED SCAN"), 12,
                s.fullAccess ? Color.rgb(18,145,123) : ROSE, true));
        scanned.addView(space(7));
        scanned.addView(text(s.scannedFiles + " files scanned", 18, INK, true));
        scanned.addView(space(4));
        scanned.addView(text(format(s.scannedBytes) + " visible storage analyzed", 14, MUTED, false));
        if (!s.fullAccess) {
            scanned.addView(space(7));
            scanned.addView(text("Full Storage Access ON करने पर ज्यादा folders दिखाई देंगे।", 12, ROSE, false));
        }
        root.addView(scanned, matchWrap());
        root.addView(space(14));

        LinearLayout safeCard = resultCard("Safe Junk", s.safeCount, s.safeBytes, TEAL, "AUTO-SAFE");
        safeCard.setOnClickListener(v -> showItemDetails(s, true));
        touch(safeCard);
        root.addView(safeCard, matchWrap());
        root.addView(space(12));

        LinearLayout reviewCard = resultCard("Review Before Delete", s.reviewCount, s.reviewBytes, AMBER, "TAP TO VIEW");
        reviewCard.setOnClickListener(v -> showItemDetails(s, false));
        touch(reviewCard);
        root.addView(reviewCard, matchWrap());
        root.addView(space(12));

        root.addView(resultCard("Personal Files (Protected)", s.personalCount, s.personalBytes, BLUE, "NEVER AUTO"));
        root.addView(space(12));
        root.addView(resultCard("Restricted Android Area", s.restrictedCount, 0, ROSE, "PROTECTED"));
        root.addView(space(18));

        LinearLayout reclaim = card();
        reclaim.setPadding(dp(18), dp(18), dp(18), dp(18));
        reclaim.addView(text("Selected safe reclaim", 14, MUTED, false));
        reclaim.addView(space(5));
        reclaim.addView(text(format(s.safeBytes), 30, PURPLE, true));
        reclaim.addView(space(5));
        reclaim.addView(text("Photos, videos और documents auto-selected नहीं हैं", 12, MUTED, false));
        root.addView(reclaim, matchWrap());

        root.addView(space(22));
        TextView clean = actionButton("CLEAN SAFE JUNK", PURPLE);
        touch(clean);
        clean.setOnClickListener(v -> confirmClean(s));
        root.addView(clean, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        root.addView(space(10));
        TextView back = actionButton("BACK", Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> handleBack());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        scroll.addView(root);
        setContentView(scroll);
        fadeIn(root);
    }

    private void showItemDetails(ScanSummary s, boolean safeOnly) {
        StringBuilder b = new StringBuilder();
        int shown = 0;
        for (ScanItem i : s.items) {
            if (i.safe != safeOnly) continue;
            if (shown >= 30) break;
            b.append("• ").append(i.file.getName()).append("  ").append(format(i.bytes)).append("\n");
            String parent = i.file.getParent();
            if (parent != null) b.append("  ").append(parent).append("\n");
            shown++;
        }
        if (shown == 0) b.append(safeOnly ? "Safe junk item नहीं मिला।" : "Review item नहीं मिला।");
        if ((safeOnly ? s.safeCount : s.reviewCount) > shown) {
            b.append("\n+ ").append((safeOnly ? s.safeCount : s.reviewCount) - shown).append(" more items");
        }
        new AlertDialog.Builder(this)
                .setTitle(safeOnly ? "Safe Junk Details" : "Review Before Delete")
                .setMessage(b.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmClean(ScanSummary s) {
        haptic();
        if (s.safeBytes <= 0) {
            new AlertDialog.Builder(this).setTitle("Safe Clean")
                    .setMessage("अभी कोई safe-selected junk नहीं मिला।")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Safe Clean")
                .setMessage("केवल SAFE junk हटेगा। Personal photos/videos/documents और review items नहीं हटेंगे।")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clean", (d,w) -> cleanAsync(s))
                .show();
    }

    private void cleanAsync(ScanSummary s) {
        new Thread(() -> {
            long bytes = 0; int count = 0; int failed = 0;
            for (ScanItem i : s.items) {
                if (!i.safe) continue;
                try {
                    long n = i.file.length();
                    if (i.file.delete()) { bytes += n; count++; }
                    else failed++;
                } catch (Exception e) { failed++; }
            }
            long finalBytes = bytes; int finalCount = count; int finalFailed = failed;
            runOnUiThread(() -> showCleanDone(finalBytes, finalCount, finalFailed));
        }, "sts-clean").start();
    }

    private void showCleanDone(long bytes, int count, int failed) {
        getWindow().getDecorView().setTag("done");
        LinearLayout root = column();
        root.setPadding(dp(24), dp(48), dp(24), dp(36));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);

        TextView check = text("✓", 62, TEAL, true);
        check.setGravity(Gravity.CENTER);
        check.setBackground(circle(Color.rgb(225,250,246)));
        root.addView(check, new LinearLayout.LayoutParams(dp(130), dp(130)));
        root.addView(space(24));
        root.addView(centerText("Cleaning Complete", 28, INK, true));
        root.addView(space(12));
        root.addView(centerText(format(bytes), 36, PURPLE, true));
        root.addView(centerText("storage reclaimed", 14, MUTED, false));
        root.addView(space(24));

        LinearLayout card = card();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.addView(text(count + " files cleaned", 15, INK, true));
        card.addView(space(6));
        card.addView(text(failed + " restricted/failed", 13, MUTED, false));
        root.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Space flex = new Space(this);
        root.addView(flex, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView done = actionButton("DONE", PURPLE);
        touch(done);
        done.setOnClickListener(v -> showHome());
        root.addView(done, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        setContentView(root);
        popIn(check);
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        Object tag = getWindow().getDecorView().getTag();
        if ("preview".equals(tag)) {
            returnFromPreview();
        } else if (tag == null || "home".equals(tag)) {
            moveTaskToBack(true);
        } else {
            haptic();
            showHome();
        }
    }

    private void requestAllFiles(boolean deep) {
        if (Build.VERSION.SDK_INT < 30) {
            pendingScanAfterAccess = false;
            startScan(deep);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Full Storage Scan Access")
                .setMessage("अभी Android app को पूरे shared storage की file-list देखने नहीं दे रहा है, इसलिए result 0 या बहुत कम आ सकता है। Full Storage Access ON करें; वापस आते ही scan अपने-आप शुरू होगा।")
                .setNegativeButton("Cancel", (d,w) -> {
                    pendingScanAfterAccess = false;
                    pendingDeepAfterAccess = false;
                })
                .setPositiveButton("ALLOW & SCAN", (d,w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                }).show();
    }

    private boolean hasAllFilesAccess() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private void scanStorage(ScanCallback cb) {
        new Thread(() -> {
            ScanSummary out = new ScanSummary();
            out.fullAccess = hasAllFilesAccess();
            cb.progress(1, "Storage map तैयार कर रहे हैं…");

            List<File> roots = new ArrayList<>();
            if (out.fullAccess) {
                roots.add(Environment.getExternalStorageDirectory());
            } else {
                File[] appRoots = getExternalFilesDirs(null);
                if (appRoots != null) for (File f : appRoots) if (f != null) roots.add(f);
                File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (d != null) roots.add(d);
            }

            Set<String> seen = new HashSet<>();
            for (File root : roots) {
                if (root == null || !root.exists()) continue;
                emitLive(out, cb, root.getAbsolutePath(), true);
                walk(root, out, seen, 0, cb);
            }

            emitLive(out, cb, "Final verification", true);
            cb.progress(98, "Junk / large / old files verify कर रहे हैं…");
            cb.progress(100, "Scan complete • " + out.scannedFiles + " files");
            cb.done(out);
        }, "sts-scan").start();
    }

    private void walk(File f, ScanSummary out, Set<String> seen, int depth, ScanCallback cb) {
        if (f == null || depth > 22) return;
        String path;
        try { path = f.getCanonicalPath(); } catch (Exception e) { path = f.getAbsolutePath(); }
        if (!seen.add(path)) return;
        String low = path.toLowerCase(Locale.ROOT);

        if (isCritical(low)) {
            out.restrictedCount++;
            emitLive(out, cb, path, false);
            return;
        }

        if (f.isDirectory()) {
            emitLive(out, cb, path, false);
            File[] children;
            try { children = f.listFiles(); } catch (Exception e) { children = null; }
            if (children == null) {
                out.restrictedCount++;
                emitLive(out, cb, path, true);
                return;
            }
            for (File x : children) walk(x, out, seen, depth + 1, cb);
            return;
        }

        String name = f.getName().toLowerCase(Locale.ROOT);
        String parent = f.getParent() == null ? "" : f.getParent().toLowerCase(Locale.ROOT);
        long len = Math.max(0, f.length());
        long age = System.currentTimeMillis() - Math.max(0, f.lastModified());

        out.scannedFiles++;
        out.scannedBytes += len;

        boolean isPersonal = personal(name);
        if (isPersonal) {
            out.personalCount++;
            out.personalBytes += len;
        }

        boolean safe = false;
        boolean review = false;

        if (name.endsWith(".tmp") || name.endsWith(".temp") || name.endsWith(".log") ||
                name.endsWith(".bak") || name.endsWith(".old") || name.endsWith(".dmp") ||
                name.endsWith(".crash")) {
            safe = !isPersonal;
        } else if ((parent.contains("/cache") || parent.contains("/.cache") ||
                parent.contains("/.thumbnails")) && !isPersonal) {
            safe = true;
        } else if (name.endsWith(".apk") && age > 7L*24*60*60*1000) {
            review = true;
        } else if (low.contains("/.trash") || name.startsWith(".trashed-")) {
            review = true;
        } else if (len >= 100L*1024*1024) {
            review = true;
        } else if ((parent.contains("/download") || parent.contains("/downloads")) &&
                age > 90L*24*60*60*1000 && !isPersonal) {
            review = true;
        }

        if (safe) {
            out.items.add(new ScanItem(f, len, true));
            out.safeBytes += len;
            out.safeCount++;
        } else if (review) {
            out.items.add(new ScanItem(f, len, false));
            out.reviewBytes += len;
            out.reviewCount++;
        }

        emitLive(out, cb, path, false);
    }

    private void emitLive(ScanSummary out, ScanCallback cb, String path, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - out.lastLiveUpdate < 90) return;
        out.lastLiveUpdate = now;
        cb.live(path, out.scannedFiles, out.scannedBytes, out.safeCount, out.safeBytes, out.reviewCount);
    }

    private boolean isCritical(String p) {
        return p.startsWith("/system") || p.startsWith("/vendor") || p.startsWith("/product") ||
                p.startsWith("/data/system") || p.startsWith("/data/adb") ||
                p.contains("/android/data") || p.contains("/android/obb");
    }

    private boolean personal(String n) {
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") ||
                n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".mov") || n.endsWith(".avi") ||
                n.endsWith(".pdf") || n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".xls") ||
                n.endsWith(".xlsx") || n.endsWith(".ppt") || n.endsWith(".pptx") || n.endsWith(".zip");
    }

    private LinearLayout toolCard(String icon, String title, String sub, int accent) {
        LinearLayout c = card();
        c.setPadding(dp(15), dp(16), dp(15), dp(16));
        TextView ic = text(icon, 22, accent, true);
        c.addView(ic);
        c.addView(space(12));
        c.addView(text(title, 15, INK, true));
        c.addView(space(4));
        c.addView(text(sub, 12, MUTED, false));
        touch(c);
        return c;
    }

    private LinearLayout resultCard(String title, int count, long bytes, int accent, String badge) {
        LinearLayout c = card();
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.addView(text(title, 16, INK, true));
        c.addView(space(5));
        c.addView(text(count + " items", 12, MUTED, false));
        c.addView(space(8));
        c.addView(text(format(bytes), 20, accent, true));
        c.addView(space(7));
        TextView b = pill(badge, accent, Color.argb(25, Color.red(accent), Color.green(accent), Color.blue(accent)));
        c.addView(b);
        return c;
    }

    private long[] storage() {
        StatFs st = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        long total = st.getTotalBytes(), free = st.getAvailableBytes();
        return new long[]{total, total-free};
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout l = column();
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.WHITE);
        d.setCornerRadius(dp(24));
        l.setBackground(d);
        l.setElevation(dp(3));
        return l;
    }

    private TextView section(String s) {
        TextView v = text(s, 16, INK, true);
        v.setPadding(0, dp(22), 0, dp(12));
        return v;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }

    private TextView centerText(String s, float size, int color, boolean bold) {
        TextView v = text(s,size,color,bold);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private TextView pill(String s, int color, int bg) {
        TextView v = text(s, 12, color, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg); d.setCornerRadius(dp(18));
        v.setBackground(d);
        return v;
    }

    private TextView actionButton(String s, int color) {
        TextView v = text(s, 17, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(28));
        RippleDrawable r = new RippleDrawable(ColorStateList.valueOf(Color.argb(45,255,255,255)), d, null);
        v.setBackground(r);
        return v;
    }

    private GradientDrawable gradient(int a, int b, float tl, float tr, float br, float bl) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a,b});
        d.setCornerRadii(new float[]{tl,tl,tr,tr,br,br,bl,bl});
        return d;
    }

    private GradientDrawable circle(int c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL); d.setColor(c);
        return d;
    }

    private void touch(View v) {
        v.setOnTouchListener((view,e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(.97f).scaleY(.97f).setDuration(90).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            }
            return false;
        });
    }

    private void fadeIn(View v) {
        v.setAlpha(0f); v.setTranslationY(dp(12));
        v.animate().alpha(1f).translationY(0).setDuration(330).start();
    }

    private void popIn(View v) {
        v.setScaleX(.5f); v.setScaleY(.5f); v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(420).start();
    }

    private void haptic() {
        try {
            Vibrator v = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(30,90));
        } catch (Exception ignored) {}
    }

    private View space(int h) { Space s = new Space(this); s.setMinimumHeight(dp(h)); return s; }
    private View spaceH(int w) { Space s = new Space(this); s.setMinimumWidth(dp(w)); return s; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private String shortPath(String path) {
        if (path == null || path.length() == 0) return "";
        if (path.length() <= 68) return path;
        return "…" + path.substring(path.length() - 67);
    }

    private String format(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] u={"B","KB","MB","GB","TB"}; double v=bytes; int i=0;
        while(v>=1024 && i<u.length-1){v/=1024;i++;}
        return String.format(Locale.US, v>=100?"%.0f %s":v>=10?"%.1f %s":"%.2f %s",v,u[i]);
    }

    private enum StorageCategory {
        PHOTOS("Photos", Color.rgb(231, 76, 120)),
        VIDEOS("Videos", Color.rgb(108, 76, 230)),
        AUDIO("Audio / Music", Color.rgb(35, 180, 155)),
        DOCUMENTS("Documents / PDF", Color.rgb(61, 132, 224)),
        APK("APK / Installers", Color.rgb(255, 159, 64)),
        BACKUPS("Backups / Databases", Color.rgb(160, 94, 210)),
        JUNK("Junk / Cache", Color.rgb(72, 196, 120)),
        OTHERS("Other visible files", Color.rgb(124, 132, 150)),
        APP_CODE("Installed Apps", Color.rgb(86, 92, 118)),
        APP_DATA("Private App Data", Color.rgb(122, 86, 214)),
        APP_CACHE("App Cache", Color.rgb(38, 184, 150)),
        SYSTEM_RESERVED("Android / System / Reserved", Color.rgb(217, 87, 101)),
        HIDDEN_UNCLASSIFIED("Hidden / Private (not split yet)", Color.rgb(70, 78, 100));

        final String label;
        final int color;
        StorageCategory(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private static final class StorageAnalytics {
        final long[] bytes = new long[StorageCategory.values().length];
        final int[] counts = new int[StorageCategory.values().length];
        long visibleBytes;
        int visibleFiles;
        long usedStorage;
        boolean privateStatsAvailable;

        void add(StorageCategory c, long b, int count) {
            bytes[c.ordinal()] += Math.max(0L, b);
            counts[c.ordinal()] += Math.max(0, count);
        }
        long bytes(StorageCategory c) { return bytes[c.ordinal()]; }
        int count(StorageCategory c) { return counts[c.ordinal()]; }
    }

    private static final class StorageBreakdownView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private StorageAnalytics analytics;
        private long denominator = 1L;

        StorageBreakdownView(Context c) { super(c); }

        void setAnalytics(StorageAnalytics a, long denominator) {
            this.analytics = a;
            this.denominator = Math.max(1L, denominator);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float left = 0f;
            float top = 0f;
            float right = getWidth();
            float bottom = getHeight();
            float radius = getHeight()/2f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(232,234,243));
            c.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, p);

            if (analytics == null) return;

            float x = 0f;
            StorageCategory[] cats = StorageCategory.values();
            for (StorageCategory cat : cats) {
                long b = analytics.bytes(cat);
                if (b <= 0) continue;
                float w = (float) getWidth() * ((float)b / (float)denominator);
                if (w < 1f && b > 0) w = 1f;
                float end = Math.min(getWidth(), x + w);
                p.setColor(cat.color);
                c.drawRect(x, top, end, bottom, p);
                x = end;
                if (x >= getWidth()) break;
            }

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(Color.argb(45,0,0,0));
            c.drawRoundRect(new RectF(left,top,right,bottom),radius,radius,p);
        }
    }

    private static final class AppStorageEntry {
        final String appName;
        final String packageName;
        final long codeBytes;
        final long dataBytes;
        final long cacheBytes;
        final long totalBytes;
        final boolean systemApp;

        AppStorageEntry(String appName,String packageName,long codeBytes,long dataBytes,
                        long cacheBytes,long totalBytes,boolean systemApp){
            this.appName=appName;
            this.packageName=packageName;
            this.codeBytes=codeBytes;
            this.dataBytes=dataBytes;
            this.cacheBytes=cacheBytes;
            this.totalBytes=totalBytes;
            this.systemApp=systemApp;
        }
    }

    private static final class SystemStorageResult {
        long usedBytes;
        long visibleBytes;
        long appCodeBytes;
        long appDataBytes;
        long appCacheBytes;
        long systemReservedBytes;
        final List<AppStorageEntry> apps=new ArrayList<>();
    }

    private static final class AppFileInfo {
        final String appName;
        final String packageName;
        final String category;
        final String status;
        final int statusColor;
        final int statusBg;
        final String explanation;
        final boolean protectedFile;
        final boolean detailsOnly;

        AppFileInfo(String appName, String packageName, String category, String status,
                    int statusColor, int statusBg, String explanation,
                    boolean protectedFile, boolean detailsOnly) {
            this.appName = appName;
            this.packageName = packageName;
            this.category = category;
            this.status = status;
            this.statusColor = statusColor;
            this.statusBg = statusBg;
            this.explanation = explanation;
            this.protectedFile = protectedFile;
            this.detailsOnly = detailsOnly;
        }

        static AppFileInfo folder() {
            return new AppFileInfo("Device Storage", null, "Folder", "FOLDER",
                    PURPLE, Color.rgb(239,236,255), "Storage folder", false, true);
        }
    }

    private static final class ToolItem {
        final File file;
        final long bytes;
        final String note;
        final boolean directory;
        ToolItem(File file, long bytes, String note, boolean directory) {
            this.file = file;
            this.bytes = bytes;
            this.note = note;
            this.directory = directory;
        }
    }

    private static final class ToolResult {
        final String type;
        final List<ToolItem> items = new ArrayList<>();
        int scannedFiles;
        int groups;
        long scannedBytes;
        long totalBytes;
        long lastLiveUpdate;
        ToolResult(String type) { this.type = type; }
    }

    private interface ToolCallback {
        void live(String currentPath, int files, long bytes, int items, long itemBytes);
        void done(ToolResult result);
    }

    private static final class ScanItem {
        final File file; final long bytes; final boolean safe;
        ScanItem(File f,long b,boolean s){file=f;bytes=b;safe=s;}
    }
    private static final class ScanSummary {
        long safeBytes, reviewBytes, scannedBytes, personalBytes, lastLiveUpdate;
        int safeCount, reviewCount, restrictedCount, scannedFiles, personalCount;
        boolean fullAccess;
        final List<ScanItem> items = new ArrayList<>();
    }
    private interface ScanCallback {
        void progress(int pct,String label);
        void live(String currentPath, int files, long bytes, int safeCount, long safeBytes, int reviewCount);
        void done(ScanSummary sum);
    }

    private static final class StorageRing extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long total, used;
        StorageRing(Context c){super(c);}
        void setStorage(long t,long u){total=t;used=u;invalidate();}
        @Override protected void onDraw(Canvas c){
            float cx=getWidth()/2f, cy=getHeight()/2f, r=Math.min(cx,cy)-22;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(17);p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.argb(70,255,255,255));c.drawCircle(cx,cy,r,p);
            float f=total<=0?.65f:Math.min(1f,(float)used/total);
            p.setColor(TEAL);c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),-90,360*f,false,p);
            p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(27*getResources().getDisplayMetrics().scaledDensity);
            c.drawText(formatStatic(used),cx,cy+2,p);
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
            c.drawText("used of "+formatStatic(total),cx,cy+30,p);
        }
    }

    private static final class ScanRing extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int liveFiles;
        private boolean done;
        ScanRing(Context c){super(c);}
        void setLiveFiles(int n){liveFiles=Math.max(0,n);invalidate();}
        void setDone(){done=true;invalidate();}
        @Override protected void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-26;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(18);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(229,232,246));
            c.drawCircle(cx,cy,r,p);

            if (done) {
                p.setColor(TEAL);
                c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),-90,360,false,p);
            } else {
                long t = System.currentTimeMillis() % 1600L;
                float start = -90f + (t / 1600f) * 360f;
                p.setColor(PURPLE);
                c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),start,105,false,p);
                p.setStrokeWidth(8);
                p.setColor(TEAL);
                c.drawArc(new RectF(cx-r+24,cy-r+24,cx+r-24,cy+r-24),start-65,55,false,p);
                postInvalidateDelayed(16);
            }

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(INK);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(31*getResources().getDisplayMetrics().scaledDensity);
            c.drawText(done ? "DONE" : "LIVE",cx,cy-2,p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);
            p.setColor(MUTED);
            c.drawText(liveFiles+" files",cx,cy+29,p);
        }
    }

    private static String formatStatic(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] u={"B","KB","MB","GB","TB"}; double v=bytes; int i=0;
        while(v>=1024 && i<u.length-1){v/=1024;i++;}
        return String.format(Locale.US, v>=100?"%.0f %s":v>=10?"%.1f %s":"%.2f %s",v,u[i]);
    }
}
