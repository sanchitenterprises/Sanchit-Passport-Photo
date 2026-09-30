package com.sts.deepcleaner;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        getWindow().getDecorView().setTag("home");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(18), 0, dp(18), dp(26));

        LinearLayout header = column();
        header.setPadding(dp(20), dp(24), dp(20), dp(24));
        header.setBackground(gradient(PURPLE, PURPLE2, 0, 0, 0, 0));
        TextView title = text("STS Deep Cleaner", 29, Color.WHITE, true);
        TextView sub = text("Safe • Smart • Deep", 14, Color.argb(220,255,255,255), false);
        header.addView(title);
        header.addView(space(4));
        header.addView(sub);
        header.addView(space(18));

        long[] s = storage();
        StorageRing ring = new StorageRing(this);
        ring.setStorage(s[0], s[1]);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(dp(190), dp(190));
        ringLp.gravity = Gravity.CENTER_HORIZONTAL;
        header.addView(ring, ringLp);
        root.addView(header, matchWrap());

        LinearLayout modeCard = card();
        modeCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        TextView free = text("Free storage", 14, MUTED, false);
        TextView freeValue = text(format(s[0] - s[1]), 27, PURPLE, true);
        TextView mode = pill(hasAllFilesAccess() ? "Full storage access ON" : "Full scan access OFF",
                hasAllFilesAccess() ? Color.rgb(18,145,123) : PURPLE,
                hasAllFilesAccess() ? Color.rgb(228,252,248) : Color.rgb(244,240,255));
        modeCard.addView(free);
        modeCard.addView(freeValue);
        modeCard.addView(space(8));
        modeCard.addView(mode);
        root.addView(space(16));
        root.addView(modeCard, matchWrap());

        root.addView(section("Storage Tools"));
        LinearLayout row1 = row();
        row1.addView(toolCard("✦", "Junk & Cache", "Temporary files", TEAL), weight());
        row1.addView(spaceH(12));
        row1.addView(toolCard("⬢", "Large Files", "Review first", AMBER), weight());
        root.addView(row1, matchWrap());

        root.addView(space(12));
        LinearLayout row2 = row();
        row2.addView(toolCard("⧉", "Duplicates", "Hash finder", BLUE), weight());
        row2.addView(spaceH(12));
        row2.addView(toolCard("⌁", "Residual", "App leftovers", ROSE), weight());
        root.addView(row2, matchWrap());

        root.addView(space(22));
        TextView scan = actionButton("SMART SCAN", PURPLE);
        touch(scan);
        scan.setOnClickListener(v -> beginScan(false));
        root.addView(scan, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        root.addView(space(10));
        TextView deep = actionButton(hasAllFilesAccess() ? "DEEP SCAN • ACCESS ON" : "DEEP SCAN • ENABLE ACCESS",
                Color.rgb(236,233,255));
        deep.setTextColor(PURPLE);
        touch(deep);
        deep.setOnClickListener(v -> beginScan(true));
        root.addView(deep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        root.addView(space(16));
        TextView safety = text("✓ Personal photos, videos और documents auto-delete नहीं होंगे\n✓ System-critical paths हमेशा protected रहेंगे", 13, MUTED, false);
        safety.setLineSpacing(dp(3), 1f);
        root.addView(safety);

        scroll.addView(root);
        setContentView(scroll);
        fadeIn(root);
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
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setBackgroundColor(BG);

        TextView title = text(deep ? "Deep Scan" : "Smart Scan", 28, INK, true);
        TextView sub = text("Storage को safely analyze कर रहे हैं", 14, MUTED, false);
        root.addView(title); root.addView(space(5)); root.addView(sub); root.addView(space(30));

        ScanRing ring = new ScanRing(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(250), dp(250));
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(ring, rlp);
        TextView status = text("Preparing…", 14, MUTED, false);
        status.setGravity(Gravity.CENTER);
        root.addView(space(14)); root.addView(status, matchWrap());

        root.addView(space(30));
        LinearLayout note = card();
        note.setPadding(dp(18), dp(16), dp(18), dp(16));
        note.addView(text("Safety lock active", 15, INK, true));
        note.addView(space(5));
        note.addView(text("Personal media review-only है। System-critical files scan-clean list में नहीं आएँगी।", 13, MUTED, false));
        root.addView(note, matchWrap());

        Space flex = new Space(this);
        root.addView(flex, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView back = actionButton("BACK", Color.WHITE);
        back.setTextColor(PURPLE);
        touch(back);
        back.setOnClickListener(v -> showHome());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        setContentView(root);
        fadeIn(root);

        scanStorage(new ScanCallback() {
            @Override public void progress(int pct, String label) {
                runOnUiThread(() -> {
                    ring.setProgress(pct);
                    status.setText(label);
                });
            }
            @Override public void done(ScanSummary sum) {
                lastSummary = sum;
                runOnUiThread(() -> showResult(sum));
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
        back.setOnClickListener(v -> showHome());
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
            cb.progress(4, "Storage map पढ़ रहे हैं…");

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
            int idx = 0;
            for (File root : roots) {
                if (root == null || !root.exists()) continue;
                idx++;
                cb.progress(Math.min(86, 8 + idx * 24), "Scanning: " + root.getAbsolutePath());
                walk(root, out, seen, 0);
            }

            cb.progress(92, "Junk / large / old files verify कर रहे हैं…");
            cb.progress(100, "Scan complete • " + out.scannedFiles + " files");
            cb.done(out);
        }, "sts-scan").start();
    }

    private void walk(File f, ScanSummary out, Set<String> seen, int depth) {
        if (f == null || depth > 22) return;
        String path;
        try { path = f.getCanonicalPath(); } catch (Exception e) { path = f.getAbsolutePath(); }
        if (!seen.add(path)) return;
        String low = path.toLowerCase(Locale.ROOT);

        if (isCritical(low)) {
            out.restrictedCount++;
            return;
        }

        if (f.isDirectory()) {
            File[] children;
            try { children = f.listFiles(); } catch (Exception e) { children = null; }
            if (children == null) {
                out.restrictedCount++;
                return;
            }
            for (File x : children) walk(x, out, seen, depth + 1);
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

    private String format(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] u={"B","KB","MB","GB","TB"}; double v=bytes; int i=0;
        while(v>=1024 && i<u.length-1){v/=1024;i++;}
        return String.format(Locale.US, v>=100?"%.0f %s":v>=10?"%.1f %s":"%.2f %s",v,u[i]);
    }

    private static final class ScanItem {
        final File file; final long bytes; final boolean safe;
        ScanItem(File f,long b,boolean s){file=f;bytes=b;safe=s;}
    }
    private static final class ScanSummary {
        long safeBytes, reviewBytes, scannedBytes, personalBytes;
        int safeCount, reviewCount, restrictedCount, scannedFiles, personalCount;
        boolean fullAccess;
        final List<ScanItem> items = new ArrayList<>();
    }
    private interface ScanCallback {
        void progress(int pct,String label);
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
        private int pct;
        ScanRing(Context c){super(c);}
        void setProgress(int n){pct=Math.max(0,Math.min(100,n));invalidate();}
        @Override protected void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-26;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(18);p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Color.rgb(229,232,246));c.drawCircle(cx,cy,r,p);
            p.setColor(PURPLE);c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),-90,3.6f*pct,false,p);
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(INK);
            p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(38*getResources().getDisplayMetrics().scaledDensity);
            c.drawText(pct+"%",cx,cy+13,p);
        }
    }

    private static String formatStatic(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] u={"B","KB","MB","GB","TB"}; double v=bytes; int i=0;
        while(v>=1024 && i<u.length-1){v/=1024;i++;}
        return String.format(Locale.US, v>=100?"%.0f %s":v>=10?"%.1f %s":"%.2f %s",v,u[i]);
    }
}
