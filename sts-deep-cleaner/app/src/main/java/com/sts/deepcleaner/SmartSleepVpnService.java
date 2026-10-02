package com.sts.deepcleaner;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SmartSleepVpnService extends VpnService {
    public static final String ACTION_START = "com.sts.deepcleaner.smart_sleep.START";
    public static final String ACTION_STOP = "com.sts.deepcleaner.smart_sleep.STOP";
    public static final String ACTION_REFRESH = "com.sts.deepcleaner.smart_sleep.REFRESH";

    private static final String CHANNEL_ID = "sts_smart_sleep";
    private static final int NOTIFICATION_ID = 7841;
    private static final long CHECK_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> lastBlocked = new HashSet<>();

    private ParcelFileDescriptor vpnInterface;
    private Thread drainThread;
    private volatile boolean running;

    private final Runnable scanner = new Runnable() {
        @Override public void run() {
            if (!isMasterEnabled()) {
                stopEverything();
                stopSelf();
                return;
            }
            scanAndApply();
            handler.postDelayed(this, CHECK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            getSharedPreferences("sts_smart_sleep", MODE_PRIVATE).edit()
                    .putBoolean("enabled", false)
                    .putStringSet("sleeping_packages", new HashSet<>())
                    .apply();
            stopEverything();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification(0));
        handler.removeCallbacks(scanner);
        handler.post(scanner);
        return START_STICKY;
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

    private boolean isMasterEnabled() {
        return getSharedPreferences("sts_smart_sleep", MODE_PRIVATE)
                .getBoolean("enabled", false);
    }

    private void scanAndApply() {
        SharedPreferences prefs = getSharedPreferences("sts_smart_sleep", MODE_PRIVATE);
        if (!hasUsageAccess()) {
            prefs.edit().putStringSet("sleeping_packages", new HashSet<>()).apply();
            if (!lastBlocked.isEmpty()) {
                lastBlocked.clear();
                stopVpnOnly();
            }
            updateNotification(0);
            return;
        }

        int minutes = Math.max(1, prefs.getInt("sleep_minutes", 10));
        long threshold = minutes * 60_000L;
        long now = System.currentTimeMillis();

        Set<String> protectedPkgs = new HashSet<>(
                prefs.getStringSet("protected_packages", new HashSet<>()));
        protectedPkgs.add(getPackageName());

        Map<String, UsageStats> usage = new HashMap<>();
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            if (usm != null) {
                Map<String, UsageStats> m = usm.queryAndAggregateUsageStats(
                        now - 24L * 60L * 60L * 1000L, now);
                if (m != null) usage.putAll(m);
            }
        } catch (Exception ignored) {}

        Set<String> blocked = new HashSet<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(0);
        } catch (Exception e) {
            apps = new ArrayList<>();
        }

        for (ApplicationInfo ai : apps) {
            if (ai == null || ai.packageName == null) continue;
            if (ai.packageName.equals(getPackageName())) continue;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (protectedPkgs.contains(ai.packageName)) continue;
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;

            long wakeUntil = prefs.getLong("wake_until_" + ai.packageName, 0L);
            if (wakeUntil > now) continue;

            UsageStats us = usage.get(ai.packageName);
            long last = us == null ? 0L : us.getLastTimeUsed();
            if (last <= 0L || now - last >= threshold) {
                blocked.add(ai.packageName);
            }
        }

        prefs.edit()
                .putStringSet("sleeping_packages", blocked)
                .putLong("last_scan", now)
                .apply();

        if (!blocked.equals(lastBlocked)) {
            lastBlocked.clear();
            lastBlocked.addAll(blocked);
            rebuildVpn(blocked);
        }
        updateNotification(blocked.size());
    }

    private void rebuildVpn(Set<String> blocked) {
        stopVpnOnly();
        if (blocked == null || blocked.isEmpty()) return;

        try {
            Builder b = new Builder()
                    .setSession("STS Smart Sleep")
                    .setMtu(1500)
                    .addAddress("10.253.0.1", 32)
                    .addRoute("0.0.0.0", 0)
                    .addAddress("fd00:253::1", 128)
                    .addRoute("::", 0);

            int allowed = 0;
            for (String pkg : blocked) {
                try {
                    b.addAllowedApplication(pkg);
                    allowed++;
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
            if (allowed == 0) return;

            vpnInterface = b.establish();
            if (vpnInterface == null) return;

            running = true;
            final ParcelFileDescriptor local = vpnInterface;
            drainThread = new Thread(() -> drainPackets(local), "sts-smart-sleep-vpn");
            drainThread.start();
        } catch (Throwable ignored) {
            stopVpnOnly();
        }
    }

    private void drainPackets(ParcelFileDescriptor fd) {
        byte[] buffer = new byte[32767];
        try (FileInputStream in = new FileInputStream(fd.getFileDescriptor())) {
            while (running && !Thread.currentThread().isInterrupted()) {
                int n = in.read(buffer);
                if (n < 0) break;
            }
        } catch (IOException ignored) {
        } catch (Throwable ignored) {
        }
    }

    private void stopVpnOnly() {
        running = false;
        Thread t = drainThread;
        drainThread = null;
        if (t != null) t.interrupt();

        ParcelFileDescriptor fd = vpnInterface;
        vpnInterface = null;
        if (fd != null) {
            try { fd.close(); } catch (IOException ignored) {}
        }
    }

    private void stopEverything() {
        handler.removeCallbacks(scanner);
        stopVpnOnly();
        lastBlocked.clear();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "STS Smart Sleep", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows when Master Smart Sleep internet blocking is active");
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(int count) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return b.setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("STS Smart Sleep active")
                .setContentText(count + " apps sleeping • internet blocked")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification(int count) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(count));
    }

    @Override
    public void onRevoke() {
        getSharedPreferences("sts_smart_sleep", MODE_PRIVATE).edit()
                .putBoolean("enabled", false)
                .putStringSet("sleeping_packages", new HashSet<>())
                .apply();
        stopEverything();
        stopForeground(true);
        stopSelf();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stopEverything();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
