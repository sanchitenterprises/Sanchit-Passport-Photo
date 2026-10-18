#!/usr/bin/env python3
from pathlib import Path
root = Path("sts-fast-browser")
p = root / "app/src/main/java/com/sts/fastbrowser/PdfViewerActivity.java"
source = p.read_text()
def replace_once(old, new):
    global source
    count = source.count(old)
    assert count == 1, f"Expected one occurrence, got {count} for {old[:100]!r}"
    source = source.replace(old, new)

# An unchanged protected document still needs an unencrypted Save/Save As output.
replace_once(
    "if (save && pdfMarks.isEmpty()) {",
    "if (save && pdfMarks.isEmpty() && !openedEncrypted) {",
)

# Never overwrite the original encrypted document by the ordinary Save path.
replace_once(
    'if (save && "pdf".equals(format) && sourceUri != null && !hasScannedEdits()) {',
    'if (save && "pdf".equals(format) && sourceUri != null && !hasScannedEdits() && !openedEncrypted) {',
)

# Protect any direct call to the write-original pathway, even if reached elsewhere.
replace_once(
    "private void savePdfEdits(boolean original) {\n        if(pdfSaving || replacementApplying || pdfFile==null)return;",
    'private void savePdfEdits(boolean original) {\n        if (original && openedEncrypted) { showDocumentFormats(false); return; }\n        if(pdfSaving || replacementApplying || pdfFile==null)return;',
)

# Wrong behavior in v117: writing the original encrypted download when no edits.
replace_once(
    '''                } else if (openedEncrypted && "pdf".equals(format) && sourceFile != null) {
                    // Preserve the source's existing PDF password when no edits are requested.
                    renderSource = sourceFile;
                }''',
    '''                }''',
)

# Rename of a downloaded protected document must not re-export its encrypted original.
replace_once(
    "if (pdfSaving || sourceFile == null) return;\n        pdfSaving=true;",
    "if (pdfSaving || pdfFile == null) return;\n        pdfSaving=true;",
)
replace_once("exportPdfCopy(sourceFile,newName);", "exportPdfCopy(pdfFile,newName);")

# Fail closed before publishing a PDF if the supplied export source is encrypted.
replace_once(
    '''    private void exportPdfCopy(File source, String outputName) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 29) {''',
    '''    private void exportPdfCopy(File source, String outputName) throws Exception {
        // The authenticated viewer file is decrypted in private cache; never
        // export a password-protected original via Save, Save As or Rename copy.
        try (PDDocument checked = PDDocument.load(source, "")) {
            if (checked.isEncrypted()) throw new java.io.IOException("Encrypted PDF export blocked");
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {''',
)
p.write_text(source)

gradle = root / "app/build.gradle"
g = gradle.read_text()
assert "versionCode 118" in g and "versionName '1.0.117'" in g
gradle.write_text(
    g.replace("versionCode 118", "versionCode 119")
    .replace("versionName '1.0.117'", "versionName '1.0.118'")
)
for test in (root / "review-tests").glob("*.py"):
    t = test.read_text()
    if "versionName '1.0.117'" in t:
        test.write_text(t.replace("versionName '1.0.117'", "versionName '1.0.118'"))
assert "renderSource = sourceFile" not in source
assert "exportPdfCopy(sourceFile" not in source
assert "doc.setAllSecurityToBeRemoved(true);" in source
assert "buildEditedPdf(pdfFile, edited, marks);" in source
assert "if (original && openedEncrypted)" in source
print("PASS: Save/Save As/Rename use authenticated password-free PDF and preserve encrypted original")