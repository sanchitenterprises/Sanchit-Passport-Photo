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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
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
                LinearLayout itemCard = card();
                itemCard.setPadding(dp(15), dp(13), dp(15), dp(13));
                itemCard.addView(text(item.file.getName().length() == 0 ? item.file.getAbsolutePath() : item.file.getName(), 14, INK, true));
                itemCard.addView(space(3));
                itemCard.addView(text(item.directory ? "Folder" : format(item.bytes), 12, accent, true));
                itemCard.addView(space(3));
                itemCard.addView(text(item.note, 11, MUTED, false));
                itemCard.addView(space(3));
                itemCard.addView(text(shortPath(item.file.getAbsolutePath()), 10, MUTED, false));

                if ("large".equals(result.type) || "duplicates".equals(result.type)) {
                    itemCard.addView(space(8));
                    TextView del = pill("TAP TO REVIEW / DELETE", ROSE, Color.rgb(255,238,243));
                    itemCard.addView(del);
                    itemCard.setOnClickListener(v -> confirmDeleteToolItem(result, item));
                    touch(itemCard);
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

    private void confirmDeleteToolItem(ToolResult result, ToolItem item) {
        String warning = "duplicates".equals(result.type)
                ? "यह duplicate copy delete होगी। इसी hash group की एक original copy scan में keep की गई है।"
                : "यह file permanently delete होगी। Large Files review-only हैं, इसलिए delete आपकी confirmation के बाद ही होगा।";
        new AlertDialog.Builder(this)
                .setTitle("Delete " + item.file.getName() + "?")
                .setMessage(warning + "\n\n" + item.file.getAbsolutePath() + "\n" + format(item.bytes))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("DELETE", (d,w) -> {
                    boolean ok = false;
                    try { ok = item.file.delete(); } catch (Exception ignored) {}
                    if (ok) {
                        result.items.remove(item);
                        result.totalBytes = Math.max(0, result.totalBytes - item.bytes);
                        showToolResult(result);
                    } else {
                        new AlertDialog.Builder(this).setTitle("Delete failed")
                                .setMessage("Android ने इस item को delete करने की permission नहीं दी।")
                                .setPositiveButton("OK", null).show();
                    }
                }).show();
    }

    private void confirmCleanToolResult(ToolResult result) {
        String msg;
        if ("duplicates".equals(result.type)) {
            msg = "हर duplicate hash group की पहली/original copy रखी जाएगी। केवल duplicate copies delete होंगी।";
        } else if ("residual".equals(result.type)) {
            msg = "केवल scan में मिले empty folders और non-personal old residual/temp candidates delete होंगे।";
        } else {
            msg = "केवल safe junk/cache candidates delete होंगे। Personal photos, videos और documents नहीं हटेंगे।";
        }
        new AlertDialog.Builder(this)
                .setTitle("Confirm Clean")
                .setMessage(msg + "\n\nSelected: " + result.items.size() + " items • " + format(result.totalBytes))
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
            for (ToolItem item : copy) {
                try {
                    long n = item.bytes;
                    boolean ok = item.file.delete();
                    if (ok) { freed += n; count++; }
                    else failed++;
                } catch (Exception e) { failed++; }
            }
            long f = freed;
            int c = count;
            int x = failed;
            runOnUiThread(() -> showCleanDone(f, c, x));
        }, "sts-tool-clean").start();
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
        if (tag == null || "home".equals(tag)) {
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
