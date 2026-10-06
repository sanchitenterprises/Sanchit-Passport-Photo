package com.sts.fastbrowser;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DownloadsActivity extends Activity {
    private LinearLayout list;
    private TextView countView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#355C62"));
        getWindow().setNavigationBarColor(Color.parseColor("#F2F5F4"));
        setContentView(buildUi());
        loadDownloads();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDownloads();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F7F9F8"));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable topBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#BFE3DF"), Color.parseColor("#CAD7E7")}
        );
        top.setBackground(topBg);

        TextView back = buttonText("‹");
        back.setTextSize(30);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Downloads");
        title.setTextColor(Color.parseColor("#172326"));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleBox.addView(title);

        countView = new TextView(this);
        countView.setTextColor(Color.parseColor("#526164"));
        countView.setTextSize(12);
        titleBox.addView(countView);

        top.addView(titleBox, new LinearLayout.LayoutParams(0, dp(52), 1f));

        TextView refresh = buttonText("↻");
        refresh.setTextSize(28);
        refresh.setOnClickListener(v -> loadDownloads());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(46), dp(46)));

        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(8), dp(10), dp(18));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private TextView buttonText(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.parseColor("#172326"));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private void loadDownloads() {
        if (list == null) return;
        list.removeAllViews();
        countView.setText("Loading...");

        new Thread(() -> {
            List<DownloadItem> items = queryDownloads();
            runOnUiThread(() -> render(items));
        }, "SFB-Downloads").start();
    }

    private List<DownloadItem> queryDownloads() {
        List<DownloadItem> out = new ArrayList<>();
        Cursor cursor = null;
        try {
            Uri collection;
            String selection = null;
            String[] selectionArgs = null;
            String[] projection;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                projection = new String[] {
                        MediaStore.Downloads._ID,
                        MediaStore.Downloads.DISPLAY_NAME,
                        MediaStore.Downloads.MIME_TYPE,
                        MediaStore.Downloads.SIZE,
                        MediaStore.Downloads.DATE_ADDED
                };
            } else {
                collection = MediaStore.Files.getContentUri("external");
                projection = new String[] {
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.Files.FileColumns.DISPLAY_NAME,
                        MediaStore.Files.FileColumns.MIME_TYPE,
                        MediaStore.Files.FileColumns.SIZE,
                        MediaStore.Files.FileColumns.DATE_ADDED,
                        MediaStore.Files.FileColumns.DATA
                };
                selection = MediaStore.Files.FileColumns.DATA + " LIKE ?";
                selectionArgs = new String[]{"%/Download/%"};
            }

            cursor = getContentResolver().query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    MediaStore.MediaColumns.DATE_ADDED + " DESC"
            );
            if (cursor == null) return out;

            int idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID);
            int nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE);
            int sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
            int dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED);

            int limit = 0;
            while (cursor.moveToNext() && limit++ < 1000) {
                long id = idCol >= 0 ? cursor.getLong(idCol) : -1L;
                String name = nameCol >= 0 ? cursor.getString(nameCol) : "";
                String mime = mimeCol >= 0 ? cursor.getString(mimeCol) : null;
                long size = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0L;
                long dateSec = dateCol >= 0 ? cursor.getLong(dateCol) : 0L;
                if (id < 0 || TextUtils.isEmpty(name)) continue;

                Uri uri = ContentUris.withAppendedId(collection, id);
                out.add(new DownloadItem(uri, name, mime, size, dateSec * 1000L));
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return out;
    }

    private void render(List<DownloadItem> items) {
        if (list == null) return;
        list.removeAllViews();
        countView.setText(items.size() + (items.size() == 1 ? " file" : " files"));

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Downloads folder में अभी कोई file नहीं मिली।");
            empty.setTextSize(16);
            empty.setTextColor(Color.parseColor("#526164"));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(70), dp(20), dp(20));
            list.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        for (DownloadItem item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(11), dp(14), dp(11));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), Color.parseColor("#D5DDDC"));
            row.setBackground(bg);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> openItem(item));

            TextView name = new TextView(this);
            name.setText(item.name);
            name.setTextSize(16);
            name.setTextColor(Color.parseColor("#172326"));
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            name.setMaxLines(2);
            row.addView(name);

            TextView meta = new TextView(this);
            meta.setText(formatSize(item.size) + "  •  " + formatDate(item.dateMs) +
                    (TextUtils.isEmpty(item.mime) ? "" : "  •  " + item.mime));
            meta.setTextSize(12);
            meta.setTextColor(Color.parseColor("#667477"));
            meta.setPadding(0, dp(4), 0, 0);
            row.addView(meta);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(8));
            list.addView(row, lp);
        }
    }

    private void openItem(DownloadItem item) {
        try {
            Intent intent = new Intent(this, IntentRouterActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(item.uri, TextUtils.isEmpty(item.mime) ? "*/*" : item.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "File open नहीं हो पाई", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.1f GB", mb / 1024.0);
    }

    private String formatDate(long ms) {
        if (ms <= 0) return "";
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static class DownloadItem {
        final Uri uri;
        final String name;
        final String mime;
        final long size;
        final long dateMs;

        DownloadItem(Uri uri, String name, String mime, long size, long dateMs) {
            this.uri = uri;
            this.name = name;
            this.mime = mime;
            this.size = size;
            this.dateMs = dateMs;
        }
    }
}
