package com.sts.fastbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.Font;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

public class OfficeViewerActivity extends Activity {
    private static final int REQ_STORAGE = 901;

    private Uri sourceUri;
    private String sourceUrl;
    private String fileName;
    private String cookie;
    private String userAgent;
    private File sourceFile;
    private WebView webView;
    private TextView status;
    private boolean pendingSaveOriginal;
    private String pendingExportFormat;
    private boolean editing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#355C62"));
        getWindow().setNavigationBarColor(Color.parseColor("#F2F5F4"));

        sourceUri = getIntent().getData();
        sourceUrl = getIntent().getStringExtra("office_url");
        fileName = getIntent().getStringExtra("office_name");
        cookie = getIntent().getStringExtra("office_cookie");
        userAgent = getIntent().getStringExtra("office_user_agent");

        if (sourceUri != null) {
            String resolved = resolveDisplayName(sourceUri);
            if (!TextUtils.isEmpty(resolved)) fileName = resolved;
        }
        if (TextUtils.isEmpty(fileName)) fileName = "Document";

        setTitle(fileName);
        setContentView(buildUi());
        loadOfficeFile();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#EEF2F3"));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(3), dp(4), dp(3));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#D9F0EE"), Color.parseColor("#E3EAF4"), Color.parseColor("#EEE8F4")}
        );
        top.setBackground(bg);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        TextView title = new TextView(this);
        title.setText(fileName);
        title.setTextColor(Color.parseColor("#162326"));
        title.setTextSize(14);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(10), 0, dp(8), 0);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        ImageButton menu = new ImageButton(this);
        menu.setImageResource(R.drawable.ic_more);
        menu.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        menu.setPadding(dp(10), dp(10), dp(10), dp(10));
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setContentDescription("Menu");
        menu.setOnClickListener(this::showMenu);
        top.addView(menu, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setAllowFileAccess(false);
        webView.setBackgroundColor(Color.parseColor("#EEF2F3"));
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setText("Opening " + officeKindLabel() + "...");
        status.setTextColor(Color.DKGRAY);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(Color.parseColor("#EEF2F3"));
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        return root;
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor, Gravity.END);
        pm.getMenu().add(editing ? "Finish Edit" : "Edit");
        android.view.SubMenu saveAs = pm.getMenu().addSubMenu("Save As");
        saveAs.add("JPG");
        saveAs.add("PNG");
        saveAs.add("PDF");
        pm.getMenu().add("Share");
        pm.getMenu().add("Print");
        pm.getMenu().add("Save Original");
        pm.setOnMenuItemClickListener(item -> {
            if (sourceFile == null || !sourceFile.exists()) {
                Toast.makeText(this, "File अभी तैयार हो रही है", Toast.LENGTH_SHORT).show();
                return true;
            }
            String t = String.valueOf(item.getTitle());
            if ("Edit".equals(t)) setEditMode(true);
            else if ("Finish Edit".equals(t)) setEditMode(false);
            else if ("JPG".equals(t)) chooseImageExportScope("jpg");
            else if ("PNG".equals(t)) chooseImageExportScope("png");
            else if ("PDF".equals(t)) exportAsPdf();
            else if ("Share".equals(t)) shareOriginal();
            else if ("Print".equals(t)) printDocument();
            else if ("Save Original".equals(t)) saveOriginal();
            return true;
        });
        pm.show();
    }

    private void loadOfficeFile() {
        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "office");
                if (!dir.exists()) dir.mkdirs();

                String safe = sanitizeFileName(fileName);
                sourceFile = new File(dir, safe);
                if (sourceUri != null && ("content".equalsIgnoreCase(sourceUri.getScheme()) ||
                        "file".equalsIgnoreCase(sourceUri.getScheme()))) {
                    copyUriToFile(sourceUri, sourceFile);
                } else if (!TextUtils.isEmpty(sourceUrl)) {
                    download(sourceUrl, sourceFile);
                } else {
                    throw new IllegalStateException("No office source");
                }

                String lower = fileName.toLowerCase(Locale.ROOT);
                final String html;
                if (lower.endsWith(".xlsx")) {
                    html = renderXlsx(sourceFile);
                } else if (lower.endsWith(".xls")) {
                    html = renderXls(sourceFile);
                } else if (lower.endsWith(".docx")) {
                    html = renderDocx(sourceFile);
                } else if (lower.endsWith(".doc")) {
                    html = renderDoc(sourceFile);
                } else if (lower.endsWith(".pptx")) {
                    html = renderPptx(sourceFile);
                } else if (lower.endsWith(".ppt")) {
                    html = renderPpt(sourceFile);
                } else if (lower.endsWith(".csv")) {
                    html = renderCsv(sourceFile);
                } else if (lower.endsWith(".txt")) {
                    html = renderTextFile(sourceFile);
                } else if (lower.endsWith(".rtf")) {
                    html = renderRtf(sourceFile);
                } else if (lower.endsWith(".odt") || lower.endsWith(".ods") || lower.endsWith(".odp")) {
                    html = renderOdf(sourceFile, lower);
                } else if (lower.endsWith(".ofd")) {
                    html = renderOfd(sourceFile);
                } else {
                    throw new IllegalArgumentException("Unsupported document file type");
                }

                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    status.setVisibility(View.GONE);
                    webView.loadDataWithBaseURL("https://sts.local/", html, "text/html", "UTF-8", null);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setVisibility(View.VISIBLE);
                    status.setText("File open नहीं हो पाई");
                    Toast.makeText(this, "Document file open नहीं हो पाई", Toast.LENGTH_LONG).show();
                });
            }
        }, "SFB-Office-Load").start();
    }

    private String officeKindLabel() {
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".xls") || n.endsWith(".xlsx") || n.endsWith(".csv") || n.endsWith(".ods")) return "Excel";
        if (n.endsWith(".ppt") || n.endsWith(".pptx") || n.endsWith(".odp")) return "PowerPoint";
        if (n.endsWith(".txt") || n.endsWith(".rtf")) return "Text";
        if (n.endsWith(".ofd")) return "OFD";
        return "Word";
    }

    // ---------- DOCX ----------

    private String renderDocx(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            Document doc = parseZipXml(zip, "word/document.xml");
            Map<String, String> rels = parseRelationships(zip, "word/_rels/document.xml.rels");

            StringBuilder out = new StringBuilder(32768);
            out.append(wordHtmlHead());
            out.append("<div class='doc-page'>");

            Element body = firstDescendant(doc.getDocumentElement(), "body");
            if (body == null) throw new IllegalStateException("Word body missing");

            NodeList children = body.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (!(n instanceof Element)) continue;
                String ln = localName(n);
                if ("p".equals(ln)) out.append(renderDocxParagraph(zip, (Element) n, rels));
                else if ("tbl".equals(ln)) out.append(renderDocxTable(zip, (Element) n, rels));
            }

            out.append("</div></body></html>");
            return out.toString();
        }
    }

    private String renderDocxParagraph(ZipFile zip, Element p, Map<String, String> rels) throws Exception {
        StringBuilder out = new StringBuilder();
        String align = "left";
        Element pPr = firstChild(p, "pPr");
        if (pPr != null) {
            Element jc = firstDescendant(pPr, "jc");
            String v = jc == null ? "" : attrLocal(jc, "val");
            if ("center".equalsIgnoreCase(v)) align = "center";
            else if ("right".equalsIgnoreCase(v) || "end".equalsIgnoreCase(v)) align = "right";
            else if ("both".equalsIgnoreCase(v) || "distribute".equalsIgnoreCase(v)) align = "justify";
        }

        out.append("<p style='text-align:").append(align).append(";'>");
        NodeList children = p.getChildNodes();
        boolean had = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element)) continue;
            String ln = localName(n);
            if ("r".equals(ln)) {
                out.append(renderDocxRun(zip, (Element) n, rels));
                had = true;
            } else if ("hyperlink".equals(ln)) {
                Element h = (Element) n;
                String rid = attrLocal(h, "id");
                String target = rels.get(rid);
                if (!TextUtils.isEmpty(target)) out.append("<a href='").append(escapeAttr(target)).append("'>");
                NodeList hs = h.getChildNodes();
                for (int j = 0; j < hs.getLength(); j++) {
                    Node rn = hs.item(j);
                    if (rn instanceof Element && "r".equals(localName(rn))) {
                        out.append(renderDocxRun(zip, (Element) rn, rels));
                        had = true;
                    }
                }
                if (!TextUtils.isEmpty(target)) out.append("</a>");
            }
        }
        if (!had) out.append("&nbsp;");
        out.append("</p>");
        return out.toString();
    }

    private String renderDocxRun(ZipFile zip, Element run, Map<String, String> rels) throws Exception {
        StringBuilder style = new StringBuilder();
        Element rPr = firstChild(run, "rPr");
        if (rPr != null) {
            if (firstChild(rPr, "b") != null) style.append("font-weight:bold;");
            if (firstChild(rPr, "i") != null) style.append("font-style:italic;");
            if (firstChild(rPr, "u") != null) style.append("text-decoration:underline;");
            Element color = firstChild(rPr, "color");
            if (color != null) {
                String v = attrLocal(color, "val");
                if (v.matches("(?i)[0-9a-f]{6}")) style.append("color:#").append(v).append(";");
            }
            Element sz = firstChild(rPr, "sz");
            if (sz != null) {
                try {
                    double pt = Double.parseDouble(attrLocal(sz, "val")) / 2.0;
                    style.append("font-size:").append(pt).append("pt;");
                } catch (Exception ignored) {}
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("<span style='").append(style).append("'>");
        NodeList ch = run.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            Node n = ch.item(i);
            if (!(n instanceof Element)) continue;
            String ln = localName(n);
            if ("t".equals(ln)) {
                out.append(escapeHtml(n.getTextContent()));
            } else if ("tab".equals(ln)) {
                out.append("&emsp;");
            } else if ("br".equals(ln) || "cr".equals(ln)) {
                out.append("<br>");
            } else if ("drawing".equals(ln) || "pict".equals(ln)) {
                Element blip = firstDescendant((Element) n, "blip");
                if (blip != null) {
                    String rid = attrLocal(blip, "embed");
                    String target = rels.get(rid);
                    String data = imageDataUri(zip, target);
                    if (data != null) out.append("<img src='").append(data).append("'>");
                }
            }
        }
        out.append("</span>");
        return out.toString();
    }

    private String renderDocxTable(ZipFile zip, Element table, Map<String, String> rels) throws Exception {
        StringBuilder out = new StringBuilder("<table class='word-table'>");
        NodeList rows = table.getChildNodes();
        for (int i = 0; i < rows.getLength(); i++) {
            Node rn = rows.item(i);
            if (!(rn instanceof Element) || !"tr".equals(localName(rn))) continue;
            out.append("<tr>");
            NodeList cells = rn.getChildNodes();
            for (int j = 0; j < cells.getLength(); j++) {
                Node cn = cells.item(j);
                if (!(cn instanceof Element) || !"tc".equals(localName(cn))) continue;
                Element tc = (Element) cn;
                int colspan = 1;
                Element gridSpan = firstDescendant(tc, "gridSpan");
                if (gridSpan != null) {
                    try { colspan = Math.max(1, Integer.parseInt(attrLocal(gridSpan, "val"))); } catch (Exception ignored) {}
                }
                out.append("<td");
                if (colspan > 1) out.append(" colspan='").append(colspan).append("'");
                out.append(">");
                NodeList inner = tc.getChildNodes();
                for (int k = 0; k < inner.getLength(); k++) {
                    Node p = inner.item(k);
                    if (p instanceof Element && "p".equals(localName(p))) {
                        out.append(renderDocxParagraph(zip, (Element) p, rels));
                    }
                }
                out.append("</td>");
            }
            out.append("</tr>");
        }
        out.append("</table>");
        return out.toString();
    }

    private String imageDataUri(ZipFile zip, String target) {
        if (TextUtils.isEmpty(target)) return null;
        try {
            String p = target.replace("\\", "/");
            while (p.startsWith("../")) p = p.substring(3);
            if (p.startsWith("/")) p = p.substring(1);
            if (!p.startsWith("word/")) p = "word/" + p;
            ZipEntry e = zip.getEntry(p);
            if (e == null) return null;
            byte[] data = readAll(zip.getInputStream(e));
            String lower = p.toLowerCase(Locale.ROOT);
            String mime = lower.endsWith(".png") ? "image/png" :
                    (lower.endsWith(".gif") ? "image/gif" :
                    (lower.endsWith(".webp") ? "image/webp" : "image/jpeg"));
            return "data:" + mime + ";base64," + Base64.encodeToString(data, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- legacy DOC ----------

    private String renderDoc(File file) throws Exception {
        StringBuilder out = new StringBuilder(32768);
        out.append(wordHtmlHead()).append("<div class='doc-page'>");
        try (FileInputStream in = new FileInputStream(file); HWPFDocument doc = new HWPFDocument(in)) {
            Range range = doc.getRange();
            int count = range.numParagraphs();
            for (int i = 0; i < count; i++) {
                Paragraph p = range.getParagraph(i);
                String align = "left";
                int j = p.getJustification();
                if (j == 1) align = "center";
                else if (j == 2) align = "right";
                else if (j == 3) align = "justify";
                out.append("<p style='text-align:").append(align).append(";'>");
                int runs = p.numCharacterRuns();
                for (int r = 0; r < runs; r++) {
                    CharacterRun cr = p.getCharacterRun(r);
                    String txt = cr.text();
                    if (txt == null) continue;
                    StringBuilder style = new StringBuilder();
                    if (cr.isBold()) style.append("font-weight:bold;");
                    if (cr.isItalic()) style.append("font-style:italic;");
                    if (cr.getUnderlineCode() != 0) style.append("text-decoration:underline;");
                    int fs = cr.getFontSize();
                    if (fs > 0) style.append("font-size:").append(fs / 2.0).append("pt;");
                    out.append("<span style='").append(style).append("'>")
                            .append(escapeHtml(txt.replace("\r", "").replace("\u0007", "")))
                            .append("</span>");
                }
                out.append("</p>");
            }
        }
        out.append("</div></body></html>");
        return out.toString();
    }

    private String wordHtmlHead() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=yes,maximum-scale=5'>"
                + "<style>html,body{margin:0;padding:0;background:#dfe4e7;color:#111;font-family:Arial,sans-serif;}"
                + ".doc-page{box-sizing:border-box;width:794px;min-height:1123px;margin:16px auto;padding:58px 64px;background:#fff;"
                + "box-shadow:0 2px 12px rgba(0,0,0,.18);overflow:hidden;}p{margin:0 0 8px 0;min-height:1em;line-height:1.35;}"
                + ".word-table{border-collapse:collapse;width:100%;margin:8px 0}.word-table td,.word-table th{border:1px solid #999;padding:5px;vertical-align:top}"
                + "img{max-width:100%;height:auto}a{color:#1565c0;text-decoration:underline}</style></head><body>";
    }

    // ---------- XLSX ----------

    private String renderXlsx(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            Document wb = parseZipXml(zip, "xl/workbook.xml");
            Map<String, String> rels = parseRelationships(zip, "xl/_rels/workbook.xml.rels");
            List<String> shared = parseSharedStrings(zip);
            List<XlsxStyle> styles = parseXlsxStyles(zip);

            List<SheetInfo> sheets = new ArrayList<>();
            NodeList sheetNodes = wb.getElementsByTagNameNS("*", "sheet");
            for (int i = 0; i < sheetNodes.getLength(); i++) {
                Element s = (Element) sheetNodes.item(i);
                String name = attrLocal(s, "name");
                String rid = attrLocal(s, "id");
                String target = rels.get(rid);
                if (TextUtils.isEmpty(target)) continue;
                String path = target.replace("\\", "/");
                while (path.startsWith("../")) path = path.substring(3);
                if (path.startsWith("/")) path = path.substring(1);
                if (!path.startsWith("xl/")) path = "xl/" + path;
                sheets.add(new SheetInfo(name, path));
            }
            if (sheets.isEmpty()) throw new IllegalStateException("No Excel sheets");

            StringBuilder out = new StringBuilder(65536);
            out.append(excelHtmlHead());
            for (int i = 0; i < sheets.size(); i++) {
                SheetInfo s = sheets.get(i);
                out.append("<section id='sheet").append(i).append("' class='sheet")
                        .append(i == 0 ? " active" : "").append("'>")
                        .append(renderXlsxSheet(zip, s.path, shared, styles))
                        .append("</section>");
            }
            out.append("<div class='tabs'>");
            for (int i = 0; i < sheets.size(); i++) {
                out.append("<button id='tab").append(i).append("' class='tab")
                        .append(i == 0 ? " active" : "").append("' onclick='showSheet(")
                        .append(i).append(")'>").append(escapeHtml(sheets.get(i).name)).append("</button>");
            }
            out.append("</div><script>function showSheet(n){document.querySelectorAll('.sheet').forEach(function(x){x.classList.remove('active')});"
                    + "document.querySelectorAll('.tab').forEach(function(x){x.classList.remove('active')});"
                    + "document.getElementById('sheet'+n).classList.add('active');document.getElementById('tab'+n).classList.add('active');"
                    + "window.scrollTo(0,0);}</script></body></html>");
            return out.toString();
        }
    }

    private String renderXlsxSheet(ZipFile zip, String path, List<String> shared, List<XlsxStyle> styles) throws Exception {
        Document sheet = parseZipXml(zip, path);
        Map<String, MergeRange> merges = parseMerges(sheet);
        Map<Integer, TreeMap<Integer, CellData>> rows = new TreeMap<>();
        int maxCol = 0;

        NodeList rowNodes = sheet.getElementsByTagNameNS("*", "row");
        for (int ri = 0; ri < rowNodes.getLength(); ri++) {
            Element row = (Element) rowNodes.item(ri);
            int rowIndex;
            try { rowIndex = Integer.parseInt(attrLocal(row, "r")); }
            catch (Exception e) { rowIndex = ri + 1; }

            TreeMap<Integer, CellData> cells = new TreeMap<>();
            NodeList ch = row.getChildNodes();
            for (int ci = 0; ci < ch.getLength(); ci++) {
                Node n = ch.item(ci);
                if (!(n instanceof Element) || !"c".equals(localName(n))) continue;
                Element c = (Element) n;
                String ref = attrLocal(c, "r");
                int col = columnFromRef(ref);
                maxCol = Math.max(maxCol, col);
                int styleIndex = 0;
                try { styleIndex = Integer.parseInt(attrLocal(c, "s")); } catch (Exception ignored) {}
                String value = xlsxCellValue(c, shared, styles, styleIndex);
                String css = styleIndex >= 0 && styleIndex < styles.size() ? styles.get(styleIndex).css : "";
                cells.put(col, new CellData(value, css));
            }
            if (!cells.isEmpty()) rows.put(rowIndex, cells);
        }

        StringBuilder out = new StringBuilder();
        out.append("<div class='sheet-wrap'><table class='excel-table'><thead><tr><th class='corner'></th>");
        for (int c = 0; c <= maxCol; c++) out.append("<th class='colhead'>").append(columnName(c)).append("</th>");
        out.append("</tr></thead><tbody>");

        for (Map.Entry<Integer, TreeMap<Integer, CellData>> re : rows.entrySet()) {
            int rowNum = re.getKey();
            out.append("<tr><th class='rowhead'>").append(rowNum).append("</th>");
            TreeMap<Integer, CellData> cells = re.getValue();
            for (int col = 0; col <= maxCol; col++) {
                String key = cellRef(col, rowNum);
                MergeRange mr = merges.get(key);
                if (mr != null && !mr.topLeft) continue;

                CellData cd = cells.get(col);
                int colspan = mr == null ? 1 : mr.colspan;
                int rowspan = mr == null ? 1 : mr.rowspan;
                out.append("<td");
                if (colspan > 1) out.append(" colspan='").append(colspan).append("'");
                if (rowspan > 1) out.append(" rowspan='").append(rowspan).append("'");
                if (cd != null && !TextUtils.isEmpty(cd.css)) out.append(" style='").append(escapeAttr(cd.css)).append("'");
                out.append(">");
                if (cd != null) out.append(escapeHtml(cd.value).replace("\n", "<br>"));
                out.append("</td>");
            }
            out.append("</tr>");
        }
        out.append("</tbody></table></div>");
        return out.toString();
    }

    private String xlsxCellValue(Element c, List<String> shared, List<XlsxStyle> styles, int styleIndex) {
        String t = attrLocal(c, "t");
        if ("inlineStr".equals(t)) {
            Element is = firstChild(c, "is");
            return is == null ? "" : allText(is);
        }
        Element vNode = firstChild(c, "v");
        String v = vNode == null ? "" : vNode.getTextContent();
        if ("s".equals(t)) {
            try {
                int idx = Integer.parseInt(v);
                return idx >= 0 && idx < shared.size() ? shared.get(idx) : v;
            } catch (Exception e) { return v; }
        }
        if ("b".equals(t)) return "1".equals(v) ? "TRUE" : "FALSE";
        if ("str".equals(t) || "e".equals(t)) return v;
        if (TextUtils.isEmpty(v)) return "";

        boolean isDate = styleIndex >= 0 && styleIndex < styles.size() && styles.get(styleIndex).date;
        if (isDate) {
            try {
                double serial = Double.parseDouble(v);
                long millis = Math.round((serial - 25569d) * 86400000d);
                SimpleDateFormat fmt = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                return fmt.format(new Date(millis)).replace(" 00:00", "");
            } catch (Exception ignored) {}
        }
        try {
            BigDecimal bd = new BigDecimal(v).stripTrailingZeros();
            return bd.toPlainString();
        } catch (Exception ignored) {
            return v;
        }
    }

    private List<String> parseSharedStrings(ZipFile zip) {
        List<String> out = new ArrayList<>();
        try {
            Document d = parseZipXml(zip, "xl/sharedStrings.xml");
            NodeList sis = d.getElementsByTagNameNS("*", "si");
            for (int i = 0; i < sis.getLength(); i++) out.add(allText((Element) sis.item(i)));
        } catch (Exception ignored) {}
        return out;
    }

    private List<XlsxStyle> parseXlsxStyles(ZipFile zip) {
        List<XlsxStyle> out = new ArrayList<>();
        out.add(new XlsxStyle("", false));
        try {
            Document d = parseZipXml(zip, "xl/styles.xml");
            Map<Integer, String> customFmt = new HashMap<>();
            NodeList numFmts = d.getElementsByTagNameNS("*", "numFmt");
            for (int i = 0; i < numFmts.getLength(); i++) {
                Element e = (Element) numFmts.item(i);
                try { customFmt.put(Integer.parseInt(attrLocal(e, "numFmtId")), attrLocal(e, "formatCode")); }
                catch (Exception ignored) {}
            }

            List<String> fontCss = new ArrayList<>();
            Element fonts = firstDescendant(d.getDocumentElement(), "fonts");
            if (fonts != null) {
                for (Element f : directChildren(fonts, "font")) {
                    StringBuilder css = new StringBuilder();
                    if (firstChild(f, "b") != null) css.append("font-weight:bold;");
                    if (firstChild(f, "i") != null) css.append("font-style:italic;");
                    if (firstChild(f, "u") != null) css.append("text-decoration:underline;");
                    Element sz = firstChild(f, "sz");
                    if (sz != null && !TextUtils.isEmpty(attrLocal(sz, "val"))) css.append("font-size:").append(attrLocal(sz, "val")).append("pt;");
                    Element color = firstChild(f, "color");
                    if (color != null) {
                        String rgb = attrLocal(color, "rgb");
                        rgb = cssColor(rgb);
                        if (!TextUtils.isEmpty(rgb)) css.append("color:").append(rgb).append(";");
                    }
                    fontCss.add(css.toString());
                }
            }

            List<String> fillCss = new ArrayList<>();
            Element fills = firstDescendant(d.getDocumentElement(), "fills");
            if (fills != null) {
                for (Element fill : directChildren(fills, "fill")) {
                    String css = "";
                    Element fg = firstDescendant(fill, "fgColor");
                    if (fg != null) {
                        String rgb = cssColor(attrLocal(fg, "rgb"));
                        if (!TextUtils.isEmpty(rgb)) css = "background:" + rgb + ";";
                    }
                    fillCss.add(css);
                }
            }

            Element cellXfs = firstDescendant(d.getDocumentElement(), "cellXfs");
            if (cellXfs != null) {
                out.clear();
                for (Element xf : directChildren(cellXfs, "xf")) {
                    int fontId = intAttr(xf, "fontId", 0);
                    int fillId = intAttr(xf, "fillId", 0);
                    int numFmtId = intAttr(xf, "numFmtId", 0);
                    StringBuilder css = new StringBuilder();
                    if (fontId >= 0 && fontId < fontCss.size()) css.append(fontCss.get(fontId));
                    if (fillId >= 0 && fillId < fillCss.size()) css.append(fillCss.get(fillId));
                    Element a = firstChild(xf, "alignment");
                    if (a != null) {
                        String h = attrLocal(a, "horizontal");
                        if ("center".equals(h) || "right".equals(h) || "left".equals(h) || "justify".equals(h)) css.append("text-align:").append(h).append(";");
                        if ("1".equals(attrLocal(a, "wrapText"))) css.append("white-space:normal;");
                    }
                    String fmt = customFmt.get(numFmtId);
                    boolean date = isDateFormat(numFmtId, fmt);
                    out.add(new XlsxStyle(css.toString(), date));
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private boolean isDateFormat(int id, String fmt) {
        if ((id >= 14 && id <= 22) || (id >= 45 && id <= 47)) return true;
        if (fmt == null) return false;
        String f = fmt.toLowerCase(Locale.ROOT).replaceAll("\\[[^]]*]", "");
        return f.contains("yy") || f.contains("dd") || f.contains("hh") || f.contains("ss");
    }

    private Map<String, MergeRange> parseMerges(Document sheet) {
        Map<String, MergeRange> out = new HashMap<>();
        NodeList merges = sheet.getElementsByTagNameNS("*", "mergeCell");
        for (int i = 0; i < merges.getLength(); i++) {
            Element e = (Element) merges.item(i);
            String ref = attrLocal(e, "ref");
            if (TextUtils.isEmpty(ref) || !ref.contains(":")) continue;
            String[] p = ref.split(":");
            int c1 = columnFromRef(p[0]), c2 = columnFromRef(p[1]);
            int r1 = rowFromRef(p[0]), r2 = rowFromRef(p[1]);
            for (int r = r1; r <= r2; r++) {
                for (int c = c1; c <= c2; c++) {
                    out.put(cellRef(c, r), new MergeRange(c == c1 && r == r1, c2 - c1 + 1, r2 - r1 + 1));
                }
            }
        }
        return out;
    }

    private String excelHtmlHead() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=yes,maximum-scale=5'>"
                + "<style>html,body{margin:0;padding:0;background:#fff;color:#111;font-family:Arial,sans-serif;}body{padding-bottom:48px;}"
                + ".sheet{display:none}.sheet.active{display:block}.sheet-wrap{overflow:auto;width:100%;min-height:100vh;}"
                + ".excel-table{border-collapse:separate;border-spacing:0;font-size:13px;white-space:nowrap;min-width:100%;}"
                + ".excel-table td{min-width:76px;height:25px;padding:3px 6px;border-right:1px solid #d0d0d0;border-bottom:1px solid #d0d0d0;vertical-align:middle;background-clip:padding-box;}"
                + ".excel-table th{height:25px;padding:3px 6px;background:#eef1f3;border-right:1px solid #c4c8cb;border-bottom:1px solid #c4c8cb;color:#222;font-weight:normal;}"
                + ".rowhead{position:sticky;left:0;z-index:3;min-width:38px;text-align:center}.colhead{position:sticky;top:0;z-index:2;text-align:center}.corner{position:sticky;left:0;top:0;z-index:4;min-width:38px;}"
                + ".tabs{position:fixed;left:0;right:0;bottom:0;height:46px;background:#e8ecee;border-top:1px solid #c8cdcf;display:flex;overflow-x:auto;z-index:20;padding:4px 6px;box-sizing:border-box;}"
                + ".tab{border:0;border-radius:6px;padding:0 16px;margin-right:5px;background:#d5dadd;color:#111;white-space:nowrap}.tab.active{background:#b9d9d5;font-weight:bold}</style></head><body>";
    }

    // ---------- legacy XLS ----------

    private String renderXls(File file) throws Exception {
        StringBuilder out = new StringBuilder(65536);
        out.append(excelHtmlHead());
        try (FileInputStream in = new FileInputStream(file); Workbook wb = new HSSFWorkbook(in)) {
            DataFormatter formatter = new DataFormatter(Locale.getDefault());
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sh = wb.getSheetAt(si);
                out.append("<section id='sheet").append(si).append("' class='sheet").append(si == 0 ? " active" : "").append("'>");
                out.append(renderLegacySheet(wb, sh, formatter, evaluator));
                out.append("</section>");
            }

            out.append("<div class='tabs'>");
            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                out.append("<button id='tab").append(si).append("' class='tab").append(si == 0 ? " active" : "")
                        .append("' onclick='showSheet(").append(si).append(")'>")
                        .append(escapeHtml(wb.getSheetName(si))).append("</button>");
            }
            out.append("</div><script>function showSheet(n){document.querySelectorAll('.sheet').forEach(function(x){x.classList.remove('active')});"
                    + "document.querySelectorAll('.tab').forEach(function(x){x.classList.remove('active')});"
                    + "document.getElementById('sheet'+n).classList.add('active');document.getElementById('tab'+n).classList.add('active');window.scrollTo(0,0);}</script></body></html>");
        }
        return out.toString();
    }

    private String renderLegacySheet(Workbook wb, Sheet sh, DataFormatter formatter, FormulaEvaluator evaluator) {
        int maxCol = 0;
        List<Row> rows = new ArrayList<>();
        for (Row row : sh) {
            rows.add(row);
            if (row.getLastCellNum() > 0) maxCol = Math.max(maxCol, row.getLastCellNum() - 1);
        }

        Map<String, MergeRange> merges = new HashMap<>();
        for (int i = 0; i < sh.getNumMergedRegions(); i++) {
            CellRangeAddress a = sh.getMergedRegion(i);
            for (int r = a.getFirstRow(); r <= a.getLastRow(); r++) {
                for (int c = a.getFirstColumn(); c <= a.getLastColumn(); c++) {
                    merges.put(cellRef(c, r + 1), new MergeRange(r == a.getFirstRow() && c == a.getFirstColumn(),
                            a.getLastColumn() - a.getFirstColumn() + 1, a.getLastRow() - a.getFirstRow() + 1));
                }
            }
        }

        StringBuilder out = new StringBuilder("<div class='sheet-wrap'><table class='excel-table'><thead><tr><th class='corner'></th>");
        for (int c = 0; c <= maxCol; c++) out.append("<th class='colhead'>").append(columnName(c)).append("</th>");
        out.append("</tr></thead><tbody>");

        for (Row row : rows) {
            int r = row.getRowNum() + 1;
            out.append("<tr><th class='rowhead'>").append(r).append("</th>");
            for (int c = 0; c <= maxCol; c++) {
                MergeRange mr = merges.get(cellRef(c, r));
                if (mr != null && !mr.topLeft) continue;
                Cell cell = row.getCell(c);
                out.append("<td");
                if (mr != null && mr.colspan > 1) out.append(" colspan='").append(mr.colspan).append("'");
                if (mr != null && mr.rowspan > 1) out.append(" rowspan='").append(mr.rowspan).append("'");
                String css = legacyCellCss(wb, cell);
                if (!css.isEmpty()) out.append(" style='").append(escapeAttr(css)).append("'");
                out.append(">");
                if (cell != null) {
                    String value;
                    try { value = formatter.formatCellValue(cell, evaluator); }
                    catch (Exception e) { value = formatter.formatCellValue(cell); }
                    out.append(escapeHtml(value).replace("\n", "<br>"));
                }
                out.append("</td>");
            }
            out.append("</tr>");
        }
        out.append("</tbody></table></div>");
        return out.toString();
    }

    private String legacyCellCss(Workbook wb, Cell cell) {
        if (cell == null) return "";
        try {
            CellStyle cs = cell.getCellStyle();
            if (cs == null) return "";
            StringBuilder css = new StringBuilder();
            Font f = wb.getFontAt(cs.getFontIndex());
            if (f != null) {
                if (f.getBold()) css.append("font-weight:bold;");
                if (f.getItalic()) css.append("font-style:italic;");
                if (f.getUnderline() != Font.U_NONE) css.append("text-decoration:underline;");
                if (f.getFontHeightInPoints() > 0) css.append("font-size:").append(f.getFontHeightInPoints()).append("pt;");
            }
            String h = String.valueOf(cs.getAlignment()).toLowerCase(Locale.ROOT);
            if (h.contains("center")) css.append("text-align:center;");
            else if (h.contains("right")) css.append("text-align:right;");
            return css.toString();
        } catch (Exception e) {
            return "";
        }
    }


    // ---------- PPT / PPTX / Text / OpenDocument / OFD ----------

    private String presentationHtmlHead() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=yes,maximum-scale=5'>"
                + "<style>html,body{margin:0;padding:0;background:#d9dde0;color:#111;font-family:Arial,sans-serif;}"
                + ".slide{box-sizing:border-box;width:960px;min-height:540px;margin:18px auto;padding:48px 56px;background:#fff;"
                + "box-shadow:0 2px 12px rgba(0,0,0,.2);font-size:24px;overflow:hidden}.slide p{margin:0 0 18px;line-height:1.3}"
                + ".slide img{display:block;max-width:100%;max-height:460px;width:auto;height:auto;margin:10px auto;object-fit:contain}"
                + ".slide-title{font-size:36px;font-weight:bold;margin-bottom:28px}.textdoc{box-sizing:border-box;width:794px;min-height:1123px;"
                + "margin:16px auto;padding:58px 64px;background:#fff;box-shadow:0 2px 12px rgba(0,0,0,.18);white-space:pre-wrap;line-height:1.45}"
                + ".odf-page{box-sizing:border-box;width:794px;min-height:1123px;margin:16px auto;padding:50px 58px;background:#fff;"
                + "box-shadow:0 2px 12px rgba(0,0,0,.18)}table{border-collapse:collapse;width:100%}td,th{border:1px solid #aaa;padding:5px;vertical-align:top}"
                + "</style></head><body>";
    }

    private String renderPptx(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            Document pres = parseZipXml(zip, "ppt/presentation.xml");
            Map<String, String> rels = parseRelationships(zip, "ppt/_rels/presentation.xml.rels");
            List<String> slidePaths = new ArrayList<>();
            NodeList ids = pres.getElementsByTagNameNS("*", "sldId");
            for (int i = 0; i < ids.getLength(); i++) {
                Element e = (Element) ids.item(i);
                String target = rels.get(attrLocal(e, "id"));
                if (TextUtils.isEmpty(target)) continue;
                slidePaths.add(resolveZipRelative("ppt/presentation.xml", target));
            }

            StringBuilder out = new StringBuilder(65536);
            out.append(presentationHtmlHead());
            int index = 1;
            for (String path : slidePaths) {
                Document s = parseZipXml(zip, path);
                String relPath = slideRelationshipPath(path);
                Map<String, String> slideRels = parseRelationships(zip, relPath);

                out.append("<section class='slide' data-page='").append(index).append("'>");
                boolean hadContent = false;

                // Render embedded pictures first. A large number of training PPT/PPTX files
                // use screenshots/images for most of the visible slide content.
                NodeList blips = s.getElementsByTagNameNS("*", "blip");
                java.util.HashSet<String> shown = new java.util.HashSet<>();
                for (int i = 0; i < blips.getLength(); i++) {
                    Element blip = (Element) blips.item(i);
                    String rid = attrLocal(blip, "embed");
                    if (TextUtils.isEmpty(rid)) rid = attrLocal(blip, "link");
                    String target = slideRels.get(rid);
                    if (TextUtils.isEmpty(target)) continue;
                    String mediaPath = resolveZipRelative(path, target);
                    if (!shown.add(mediaPath)) continue;
                    String dataUri = zipEntryDataUri(zip, mediaPath);
                    if (dataUri != null) {
                        out.append("<img src='").append(dataUri).append("'>");
                        hadContent = true;
                    }
                }

                // Render text boxes/paragraphs.
                NodeList paras = s.getElementsByTagNameNS("*", "p");
                boolean firstText = true;
                for (int i = 0; i < paras.getLength(); i++) {
                    Element p = (Element) paras.item(i);
                    String text = allText(p).trim();
                    if (text.isEmpty()) continue;
                    out.append(firstText ? "<p class='slide-title'>" : "<p>")
                            .append(escapeHtml(text).replace("\n", "<br>")).append("</p>");
                    firstText = false;
                    hadContent = true;
                }

                // Some presentations keep visible images in slide-layout relationships.
                if (!hadContent) {
                    String layoutTarget = null;
                    for (Map.Entry<String, String> e : slideRels.entrySet()) {
                        String v = e.getValue();
                        if (v != null && v.toLowerCase(Locale.ROOT).contains("slidelayout")) {
                            layoutTarget = resolveZipRelative(path, v);
                            break;
                        }
                    }
                    if (!TextUtils.isEmpty(layoutTarget)) {
                        try {
                            Document layout = parseZipXml(zip, layoutTarget);
                            Map<String, String> layoutRels = parseRelationships(zip, slideRelationshipPath(layoutTarget));
                            NodeList lbs = layout.getElementsByTagNameNS("*", "blip");
                            for (int i = 0; i < lbs.getLength(); i++) {
                                Element blip = (Element) lbs.item(i);
                                String rid = attrLocal(blip, "embed");
                                String target = layoutRels.get(rid);
                                if (TextUtils.isEmpty(target)) continue;
                                String mediaPath = resolveZipRelative(layoutTarget, target);
                                String dataUri = zipEntryDataUri(zip, mediaPath);
                                if (dataUri != null) {
                                    out.append("<img src='").append(dataUri).append("'>");
                                    hadContent = true;
                                }
                            }
                            NodeList lps = layout.getElementsByTagNameNS("*", "p");
                            for (int i = 0; i < lps.getLength(); i++) {
                                String text = allText((Element) lps.item(i)).trim();
                                if (!text.isEmpty()) {
                                    out.append("<p>").append(escapeHtml(text).replace("\n", "<br>")).append("</p>");
                                    hadContent = true;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }

                if (!hadContent) out.append("<p>Slide ").append(index).append("</p>");
                out.append("</section>");
                index++;
            }
            out.append("</body></html>");
            return out.toString();
        }
    }

    private String renderPpt(File file) throws Exception {
        StringBuilder out = new StringBuilder(65536);
        out.append(presentationHtmlHead());
        try (FileInputStream in = new FileInputStream(file); HSLFSlideShow ppt = new HSLFSlideShow(in)) {
            int index = 1;
            for (HSLFSlide slide : ppt.getSlides()) {
                out.append("<section class='slide' data-page='").append(index++).append("'>");
                boolean had = false;
                boolean firstText = true;
                for (HSLFShape shape : slide.getShapes()) {
                    // Keep legacy PPT picture support reflection-based so it works across
                    // the POI version used by this Android build without tying the code to
                    // one concrete picture-data API.
                    try {
                        java.lang.reflect.Method gpd = shape.getClass().getMethod("getPictureData");
                        Object pd = gpd.invoke(shape);
                        if (pd != null) {
                            java.lang.reflect.Method gd = pd.getClass().getMethod("getData");
                            byte[] data = (byte[]) gd.invoke(pd);
                            String mime = "image/jpeg";
                            try {
                                java.lang.reflect.Method gm = pd.getClass().getMethod("getContentType");
                                Object mv = gm.invoke(pd);
                                if (mv != null) mime = String.valueOf(mv);
                            } catch (Exception x) {
                                try {
                                    java.lang.reflect.Method gm = pd.getClass().getMethod("getMimeType");
                                    Object mv = gm.invoke(pd);
                                    if (mv != null) mime = String.valueOf(mv);
                                } catch (Exception ignored) {}
                            }
                            if (data != null && data.length > 0) {
                                out.append("<img src='data:").append(escapeAttr(mime)).append(";base64,")
                                        .append(Base64.encodeToString(data, Base64.NO_WRAP)).append("'>");
                                had = true;
                            }
                        }
                    } catch (Exception ignored) {}

                    if (shape instanceof HSLFTextShape) {
                        String text = ((HSLFTextShape) shape).getText();
                        if (text == null || text.trim().isEmpty()) continue;
                        out.append(firstText ? "<p class='slide-title'>" : "<p>")
                                .append(escapeHtml(text.trim()).replace("\n", "<br>")).append("</p>");
                        firstText = false;
                        had = true;
                    }
                }
                if (!had) out.append("<p>Slide ").append(index - 1).append("</p>");
                out.append("</section>");
            }
        }
        out.append("</body></html>");
        return out.toString();
    }

    private String slideRelationshipPath(String partPath) {
        int slash = partPath.lastIndexOf('/');
        String dir = slash >= 0 ? partPath.substring(0, slash + 1) : "";
        String name = slash >= 0 ? partPath.substring(slash + 1) : partPath;
        return dir + "_rels/" + name + ".rels";
    }

    private String resolveZipRelative(String basePart, String target) {
        if (TextUtils.isEmpty(target)) return target;
        String t = target.replace("\\", "/");
        if (t.startsWith("/")) t = t.substring(1);
        if (t.startsWith("ppt/")) return normalizeZipPath(t);

        int slash = basePart.lastIndexOf('/');
        String baseDir = slash >= 0 ? basePart.substring(0, slash + 1) : "";
        return normalizeZipPath(baseDir + t);
    }

    private String normalizeZipPath(String path) {
        String[] parts = path.replace("\\", "/").split("/");
        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
        for (String p : parts) {
            if (p.isEmpty() || ".".equals(p)) continue;
            if ("..".equals(p)) {
                if (!stack.isEmpty()) stack.removeLast();
            } else stack.addLast(p);
        }
        StringBuilder out = new StringBuilder();
        for (String p : stack) {
            if (out.length() > 0) out.append('/');
            out.append(p);
        }
        return out.toString();
    }

    private String zipEntryDataUri(ZipFile zip, String path) {
        try {
            ZipEntry e = zip.getEntry(path);
            if (e == null) return null;
            byte[] data = readAll(zip.getInputStream(e));
            String lower = path.toLowerCase(Locale.ROOT);
            String mime;
            if (lower.endsWith(".png")) mime = "image/png";
            else if (lower.endsWith(".gif")) mime = "image/gif";
            else if (lower.endsWith(".webp")) mime = "image/webp";
            else if (lower.endsWith(".bmp")) mime = "image/bmp";
            else if (lower.endsWith(".svg")) mime = "image/svg+xml";
            else if (lower.endsWith(".emf")) mime = "image/emf";
            else if (lower.endsWith(".wmf")) mime = "image/wmf";
            else mime = "image/jpeg";
            return "data:" + mime + ";base64," + Base64.encodeToString(data, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private String renderCsv(File file) throws Exception {
        StringBuilder out = new StringBuilder(32768);
        out.append(excelHtmlHead()).append("<section id='sheet0' class='sheet active'><div class='sheet-wrap'><table class='excel-table'><tbody>");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            int row = 1;
            while ((line = br.readLine()) != null) {
                List<String> cells = parseCsvLine(line);
                out.append("<tr><th class='rowhead'>").append(row++).append("</th>");
                for (String cell : cells) out.append("<td>").append(escapeHtml(cell)).append("</td>");
                out.append("</tr>");
            }
        }
        out.append("</tbody></table></div></section><div class='tabs'><button class='tab active'>Sheet 1</button></div></body></html>");
        return out.toString();
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        StringBuilder cur = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"'); i++;
                } else quote = !quote;
            } else if (ch == ',' && !quote) {
                out.add(cur.toString()); cur.setLength(0);
            } else cur.append(ch);
        }
        out.add(cur.toString());
        return out;
    }

    private String renderTextFile(File file) throws Exception {
        byte[] data;
        try (InputStream in = new FileInputStream(file)) { data = readAll(in); }
        String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        return presentationHtmlHead() + "<div class='textdoc'>" + escapeHtml(text) + "</div></body></html>";
    }

    private String renderRtf(File file) throws Exception {
        byte[] data;
        try (InputStream in = new FileInputStream(file)) { data = readAll(in); }
        String rtf = new String(data, java.nio.charset.StandardCharsets.ISO_8859_1);
        String plain = rtf.replaceAll("\\\\par[d]?\\b", "\n")
                .replaceAll("\\\\'[0-9a-fA-F]{2}", " ")
                .replaceAll("\\\\[a-zA-Z]+-?\\d* ?", "")
                .replaceAll("[{}]", "");
        return presentationHtmlHead() + "<div class='textdoc'>" + escapeHtml(plain) + "</div></body></html>";
    }

    private String renderOdf(File file, String lowerName) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            Document d = parseZipXml(zip, "content.xml");
            StringBuilder out = new StringBuilder(32768);
            out.append(presentationHtmlHead());

            if (lowerName.endsWith(".ods")) {
                NodeList tables = d.getElementsByTagNameNS("*", "table");
                int si = 0;
                for (int t = 0; t < tables.getLength(); t++) {
                    Element table = (Element) tables.item(t);
                    String name = attrLocal(table, "name");
                    out.append("<section id='sheet").append(si).append("' class='sheet").append(si == 0 ? " active" : "")
                            .append("'><div class='sheet-wrap'><table class='excel-table'><tbody>");
                    NodeList rows = table.getElementsByTagNameNS("*", "table-row");
                    for (int r = 0; r < rows.getLength(); r++) {
                        out.append("<tr><th class='rowhead'>").append(r + 1).append("</th>");
                        NodeList cells = ((Element) rows.item(r)).getElementsByTagNameNS("*", "table-cell");
                        for (int cc = 0; cc < cells.getLength(); cc++) {
                            out.append("<td>").append(escapeHtml(((Element) cells.item(cc)).getTextContent())).append("</td>");
                        }
                        out.append("</tr>");
                    }
                    out.append("</tbody></table></div></section>");
                    si++;
                }
                out.append("<div class='tabs'>");
                for (int i = 0; i < Math.max(1, si); i++) {
                    out.append("<button id='tab").append(i).append("' class='tab").append(i == 0 ? " active" : "")
                            .append("' onclick='showSheet(").append(i).append(")'>Sheet ").append(i + 1).append("</button>");
                }
                out.append("</div><script>function showSheet(n){document.querySelectorAll('.sheet').forEach(function(x){x.classList.remove('active')});"
                        + "document.querySelectorAll('.tab').forEach(function(x){x.classList.remove('active')});document.getElementById('sheet'+n).classList.add('active');"
                        + "document.getElementById('tab'+n).classList.add('active');window.scrollTo(0,0);}</script>");
            } else if (lowerName.endsWith(".odp")) {
                NodeList pages = d.getElementsByTagNameNS("*", "page");
                for (int i = 0; i < pages.getLength(); i++) {
                    Element page = (Element) pages.item(i);
                    out.append("<section class='slide' data-page='").append(i + 1).append("'>");
                    NodeList ps = page.getElementsByTagNameNS("*", "p");
                    for (int j = 0; j < ps.getLength(); j++) {
                        String txt = ps.item(j).getTextContent();
                        if (!TextUtils.isEmpty(txt)) out.append("<p>").append(escapeHtml(txt)).append("</p>");
                    }
                    out.append("</section>");
                }
            } else {
                out.append("<div class='odf-page'>");
                Element body = firstDescendant(d.getDocumentElement(), "body");
                NodeList ps = body == null ? d.getElementsByTagNameNS("*", "p") : body.getElementsByTagNameNS("*", "p");
                for (int i = 0; i < ps.getLength(); i++) {
                    out.append("<p>").append(escapeHtml(ps.item(i).getTextContent())).append("</p>");
                }
                out.append("</div>");
            }
            out.append("</body></html>");
            return out.toString();
        }
    }

    private String renderOfd(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries());
            List<String> pageXml = new ArrayList<>();
            for (ZipEntry e : entries) {
                String n = e.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith("/content.xml") && n.contains("pages/")) pageXml.add(e.getName());
            }
            Collections.sort(pageXml);
            StringBuilder out = new StringBuilder(32768);
            out.append(presentationHtmlHead());
            if (pageXml.isEmpty()) {
                out.append("<div class='odf-page'>");
                for (ZipEntry e : entries) {
                    if (!e.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) continue;
                    try (InputStream in = zip.getInputStream(e)) {
                        DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
                        fac.setNamespaceAware(true);
                        Document d = fac.newDocumentBuilder().parse(in);
                        NodeList tc = d.getElementsByTagNameNS("*", "TextCode");
                        for (int i = 0; i < tc.getLength(); i++) out.append("<p>").append(escapeHtml(tc.item(i).getTextContent())).append("</p>");
                    } catch (Exception ignored) {}
                }
                out.append("</div>");
            } else {
                int page = 1;
                for (String p : pageXml) {
                    ZipEntry e = zip.getEntry(p);
                    if (e == null) continue;
                    out.append("<div class='odf-page' data-page='").append(page++).append("'>");
                    try (InputStream in = zip.getInputStream(e)) {
                        DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
                        fac.setNamespaceAware(true);
                        Document d = fac.newDocumentBuilder().parse(in);
                        NodeList tc = d.getElementsByTagNameNS("*", "TextCode");
                        for (int i = 0; i < tc.getLength(); i++) out.append("<p>").append(escapeHtml(tc.item(i).getTextContent())).append("</p>");
                    }
                    out.append("</div>");
                }
            }
            out.append("</body></html>");
            return out.toString();
        }
    }

    // ---------- common XML / helpers ----------

    private Document parseZipXml(ZipFile zip, String path) throws Exception {
        ZipEntry e = zip.getEntry(path);
        if (e == null) throw new IllegalStateException("Missing " + path);
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        try (InputStream in = zip.getInputStream(e)) {
            return f.newDocumentBuilder().parse(in);
        }
    }

    private Map<String, String> parseRelationships(ZipFile zip, String path) {
        Map<String, String> out = new HashMap<>();
        try {
            Document d = parseZipXml(zip, path);
            NodeList rels = d.getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < rels.getLength(); i++) {
                Element r = (Element) rels.item(i);
                out.put(attrLocal(r, "Id"), attrLocal(r, "Target"));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private String allText(Element e) {
        StringBuilder s = new StringBuilder();
        NodeList ts = e.getElementsByTagNameNS("*", "t");
        if (ts.getLength() == 0) return e.getTextContent() == null ? "" : e.getTextContent();
        for (int i = 0; i < ts.getLength(); i++) s.append(ts.item(i).getTextContent());
        return s.toString();
    }

    private String localName(Node n) {
        String ln = n.getLocalName();
        if (ln != null) return ln;
        String name = n.getNodeName();
        int p = name.indexOf(':');
        return p >= 0 ? name.substring(p + 1) : name;
    }

    private String attrLocal(Element e, String local) {
        NamedNodeMap a = e.getAttributes();
        for (int i = 0; i < a.getLength(); i++) {
            Node n = a.item(i);
            if (local.equals(localName(n))) return n.getNodeValue();
        }
        return "";
    }

    private Element firstChild(Element e, String local) {
        NodeList ch = e.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            Node n = ch.item(i);
            if (n instanceof Element && local.equals(localName(n))) return (Element) n;
        }
        return null;
    }

    private Element firstDescendant(Element e, String local) {
        NodeList ns = e.getElementsByTagNameNS("*", local);
        return ns.getLength() > 0 ? (Element) ns.item(0) : null;
    }

    private List<Element> directChildren(Element e, String local) {
        List<Element> out = new ArrayList<>();
        NodeList ch = e.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            Node n = ch.item(i);
            if (n instanceof Element && local.equals(localName(n))) out.add((Element) n);
        }
        return out;
    }

    private int intAttr(Element e, String name, int fallback) {
        try { return Integer.parseInt(attrLocal(e, name)); }
        catch (Exception x) { return fallback; }
    }

    private String cssColor(String rgb) {
        if (rgb == null) return "";
        String s = rgb.trim();
        if (s.length() == 8) s = s.substring(2);
        return s.matches("(?i)[0-9a-f]{6}") ? "#" + s : "";
    }

    private int columnFromRef(String ref) {
        if (ref == null) return 0;
        int col = 0;
        int count = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = Character.toUpperCase(ref.charAt(i));
            if (ch < 'A' || ch > 'Z') break;
            col = col * 26 + (ch - 'A' + 1);
            count++;
        }
        return count == 0 ? 0 : col - 1;
    }

    private int rowFromRef(String ref) {
        if (ref == null) return 1;
        int i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) i++;
        try { return Integer.parseInt(ref.substring(i)); }
        catch (Exception e) { return 1; }
    }

    private String columnName(int col) {
        StringBuilder s = new StringBuilder();
        int x = col + 1;
        while (x > 0) {
            int r = (x - 1) % 26;
            s.insert(0, (char) ('A' + r));
            x = (x - 1) / 26;
        }
        return s.toString();
    }

    private String cellRef(int col, int row) {
        return columnName(col) + row;
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escapeAttr(String s) {
        return escapeHtml(s);
    }

    private byte[] readAll(InputStream in) throws Exception {
        try (InputStream src = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = src.read(b)) != -1) out.write(b, 0, n);
            return out.toByteArray();
        }
    }


    // ---------- edit + export ----------

    private void setEditMode(boolean enabled) {
        editing = enabled;
        String js = "(function(){var on=" + (enabled ? "true" : "false") + ";"
                + "var q='.doc-page p,.doc-page td,.excel-table td,.slide p,.textdoc,.odf-page p,.odf-page td';"
                + "document.querySelectorAll(q).forEach(function(x){x.contentEditable=on?'true':'false';"
                + "x.style.outline=on?'1px dashed #5B7FA3':'none';});document.body.setAttribute('data-editing',on?'1':'0');})();";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
        Toast.makeText(this, enabled ? "Edit mode ON" : "Edit complete", Toast.LENGTH_SHORT).show();
    }

    private void chooseImageExportScope(String format) {
        new AlertDialog.Builder(this)
                .setTitle("Save As " + format.toUpperCase(Locale.ROOT))
                .setItems(new String[]{"Current View", "Full Document"}, (d, which) -> {
                    if (which == 0) exportImage(format, false);
                    else exportImage(format, true);
                })
                .show();
    }

    private void exportImage(String format, boolean fullDocument) {
        if (android.os.Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingExportFormat = format + (fullDocument ? ":full" : ":view");
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }

        webView.post(() -> {
            try {
                int width = Math.max(1, webView.getWidth());
                int visibleH = Math.max(1, webView.getHeight());
                int contentH = Math.max(visibleH, Math.round(webView.getContentHeight() * webView.getScale()));
                int totalH = fullDocument ? contentH : visibleH;
                int tileH = fullDocument ? Math.min(4096, visibleH * 4) : visibleH;
                int pages = Math.max(1, (int) Math.ceil(totalH / (double) tileH));
                int scrollY = webView.getScrollY();

                for (int i = 0; i < pages; i++) {
                    int y = fullDocument ? i * tileH : scrollY;
                    int h = fullDocument ? Math.min(tileH, totalH - y) : visibleH;
                    Bitmap bmp = Bitmap.createBitmap(width, Math.max(1, h), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bmp);
                    canvas.drawColor(Color.WHITE);
                    canvas.translate(0, -y);
                    webView.draw(canvas);
                    saveBitmapToDownloads(bmp, format, pages > 1 ? i + 1 : 0);
                    bmp.recycle();
                    if (!fullDocument) break;
                }
                Toast.makeText(this, "Save As complete", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Image save नहीं हो पाया", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveBitmapToDownloads(Bitmap bmp, String format, int pageNo) throws Exception {
        boolean png = "png".equalsIgnoreCase(format);
        String base = baseName(fileName);
        String suffix = pageNo > 0 ? "_Page_" + pageNo : "";
        String outName = base + suffix + (png ? ".png" : ".jpg");
        String mime = png ? "image/png" : "image/jpeg";
        Bitmap.CompressFormat cf = png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, outName);
            v.put(MediaStore.Images.Media.MIME_TYPE, mime);
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/STS Fast Browser");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("Unable to create image");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null || !bmp.compress(cf, 100, out)) throw new IllegalStateException("Image encode failed");
            }
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "STS Fast Browser");
            if (!dir.exists()) dir.mkdirs();
            File outFile = uniqueFile(dir, outName);
            try (OutputStream out = new FileOutputStream(outFile)) {
                if (!bmp.compress(cf, 100, out)) throw new IllegalStateException("Image encode failed");
            }
        }
    }

    private void exportAsPdf() {
        if (android.os.Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingExportFormat = "pdf";
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }

        webView.post(() -> {
            PdfDocument doc = new PdfDocument();
            try {
                int viewW = Math.max(1, webView.getWidth());
                int contentH = Math.max(webView.getHeight(), Math.round(webView.getContentHeight() * webView.getScale()));
                int pageW = 1240;
                int pageH = 1754;
                float scale = pageW / (float) viewW;
                int sourcePageH = Math.max(1, Math.round(pageH / scale));
                int count = Math.max(1, (int) Math.ceil(contentH / (double) sourcePageH));

                for (int i = 0; i < count; i++) {
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create();
                    PdfDocument.Page page = doc.startPage(info);
                    Canvas canvas = page.getCanvas();
                    canvas.drawColor(Color.WHITE);
                    canvas.save();
                    canvas.scale(scale, scale);
                    canvas.translate(0, -(i * sourcePageH));
                    webView.draw(canvas);
                    canvas.restore();
                    doc.finishPage(page);
                }

                savePdfDocument(doc, baseName(fileName) + ".pdf");
                Toast.makeText(this, "PDF Downloads में save हो गया", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "PDF save नहीं हो पाया", Toast.LENGTH_SHORT).show();
            } finally {
                try { doc.close(); } catch (Exception ignored) {}
            }
        });
    }

    private void savePdfDocument(PdfDocument doc, String outName) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, outName);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/STS Fast Browser");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("Unable to create PDF");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("No PDF output");
                doc.writeTo(out);
            }
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "STS Fast Browser");
            if (!dir.exists()) dir.mkdirs();
            try (OutputStream out = new FileOutputStream(uniqueFile(dir, outName))) {
                doc.writeTo(out);
            }
        }
    }

    private String baseName(String name) {
        if (TextUtils.isEmpty(name)) return "Document";
        int dot = name.lastIndexOf('.');
        String b = dot > 0 ? name.substring(0, dot) : name;
        return sanitizeFileName(b);
    }

    // ---------- original file actions ----------

    private void shareOriginal() {
        try {
            Uri uri = Uri.parse("content://" + getPackageName() + ".pdfshare/office/" + Uri.encode(sourceFile.getName()));
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mimeForName(fileName));
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share " + officeKindLabel()));
        } catch (Exception e) {
            Toast.makeText(this, "Share नहीं हो पाया", Toast.LENGTH_SHORT).show();
        }
    }

    private void printDocument() {
        try {
            PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
            pm.print(fileName, webView.createPrintDocumentAdapter(fileName), new PrintAttributes.Builder().build());
        } catch (Exception e) {
            Toast.makeText(this, "Print शुरू नहीं हो पाया", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveOriginal() {
        if (android.os.Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingSaveOriginal = true;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        new Thread(() -> {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    v.put(MediaStore.Downloads.MIME_TYPE, mimeForName(fileName));
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/STS Fast Browser");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new IllegalStateException("Unable to create download");
                    try (InputStream in = new FileInputStream(sourceFile);
                         OutputStream out = getContentResolver().openOutputStream(uri)) {
                        copy(in, out);
                    }
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "STS Fast Browser");
                    if (!dir.exists()) dir.mkdirs();
                    File outFile = uniqueFile(dir, fileName);
                    try (InputStream in = new FileInputStream(sourceFile); OutputStream out = new FileOutputStream(outFile)) {
                        copy(in, out);
                    }
                }
                runOnUiThread(() -> Toast.makeText(this, "Original file Downloads में save हो गई", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Save नहीं हो पाया", Toast.LENGTH_SHORT).show());
            }
        }, "SFB-Office-Save").start();
    }

    private File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int n = 2;
        do { f = new File(dir, base + " (" + n++ + ")" + ext); } while (f.exists());
        return f;
    }

    private String resolveDisplayName(Uri uri) {
        if (uri == null) return null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0) return c.getString(i);
                }
            } catch (Exception ignored) {}
        }
        String last = uri.getLastPathSegment();
        return TextUtils.isEmpty(last) ? null : last;
    }

    private void copyUriToFile(Uri uri, File out) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IllegalStateException("Unable to open file");
            copy(in, os);
        }
    }

    private void download(String urlString, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        if (!TextUtils.isEmpty(cookie)) c.setRequestProperty("Cookie", cookie);
        if (!TextUtils.isEmpty(userAgent)) c.setRequestProperty("User-Agent", userAgent);
        c.setRequestProperty("Accept", "*/*");
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) throw new IllegalStateException("HTTP " + code);
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            copy(in, os);
        } finally {
            c.disconnect();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        if (out == null) throw new IllegalStateException("No output");
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
    }

    private String sanitizeFileName(String s) {
        String v = s == null ? "Document" : s.replaceAll("[\\\\/:*?\\\"<>|]", "_").trim();
        return v.isEmpty() ? "Document" : v;
    }

    private String mimeForName(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".xls")) return "application/vnd.ms-excel";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".doc")) return "application/msword";
        if (n.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (n.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".rtf")) return "application/rtf";
        if (n.endsWith(".odt")) return "application/vnd.oasis.opendocument.text";
        if (n.endsWith(".ods")) return "application/vnd.oasis.opendocument.spreadsheet";
        if (n.endsWith(".odp")) return "application/vnd.oasis.opendocument.presentation";
        if (n.endsWith(".ofd")) return "application/ofd";
        return "application/octet-stream";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingSaveOriginal) {
                pendingSaveOriginal = false;
                saveOriginal();
            } else if (!TextUtils.isEmpty(pendingExportFormat)) {
                String action = pendingExportFormat;
                pendingExportFormat = null;
                if ("pdf".equals(action)) exportAsPdf();
                else {
                    boolean full = action.endsWith(":full");
                    String format = action.startsWith("png") ? "png" : "jpg";
                    exportImage(format, full);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private static class SheetInfo {
        final String name;
        final String path;
        SheetInfo(String name, String path) { this.name = name; this.path = path; }
    }

    private static class CellData {
        final String value;
        final String css;
        CellData(String value, String css) { this.value = value == null ? "" : value; this.css = css == null ? "" : css; }
    }

    private static class MergeRange {
        final boolean topLeft;
        final int colspan;
        final int rowspan;
        MergeRange(boolean topLeft, int colspan, int rowspan) {
            this.topLeft = topLeft; this.colspan = colspan; this.rowspan = rowspan;
        }
    }

    private static class XlsxStyle {
        final String css;
        final boolean date;
        XlsxStyle(String css, boolean date) { this.css = css == null ? "" : css; this.date = date; }
    }
}
