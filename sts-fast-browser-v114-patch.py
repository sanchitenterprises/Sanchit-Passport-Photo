#!/usr/bin/env python3
from pathlib import Path
import re

root=Path('sts-fast-browser')
java=root/'app/src/main/java/com/sts/fastbrowser/PdfViewerActivity.java'
s=java.read_text()

old=r'''    private void openDocumentWithPassword(String password, boolean fromPrompt) {
        new Thread(() -> {
            PDDocument doc = null;
            try {
                doc = PDDocument.load(sourceFile, password == null ? "" : password);
                if (destroyed) {
                    doc.close();
                    return;
                }

                closePdfResources();
                textDocument = doc;
                pdfEditable = !doc.isEncrypted() && doc.getCurrentAccessPermission().canModify();

                if (doc.isEncrypted()) {
                    File dir = new File(getCacheDir(), "pdf");
                    pdfFile = new File(dir, "view_" + System.currentTimeMillis() + "_" + sanitize(fileName));
                    doc.setAllSecurityToBeRemoved(true);
                    doc.save(pdfFile);
                } else {
                    pdfFile = sourceFile;
                }

                pdfPfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(pdfPfd);
                synchronized (this) {
                    pageLinks.clear();
                    pageLinksScanned.clear();
                }
                renderAllPages();
            } catch (InvalidPasswordException e) {
                if (doc != null) try { doc.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> showPasswordDialog(fromPrompt));
            } catch (Exception e) {
                if (doc != null && doc != textDocument) try { doc.close(); } catch (Exception ignored) {}
                showOpenError();
            }
        }).start();
    }
'''
new=r'''    private void openDocumentWithPassword(String password, boolean fromPrompt) {
        if (sourceFile == null || !sourceFile.isFile()) {
            showOpenError("PDF file नहीं मिली। दोबारा खोलने के लिए टैप करें।");
            return;
        }
        new Thread(() -> {
            PDDocument doc = null;
            try {
                // Open the original stream first; Android PdfRenderer cannot accept PDF passwords
                // on our minSdk. Passwords are never persisted or sent to a server.
                doc = PDDocument.load(sourceFile, password == null ? "" : password);
                if (destroyed) {
                    doc.close();
                    return;
                }

                boolean encrypted = doc.isEncrypted();
                File candidate = sourceFile;
                if (encrypted) {
                    // The original password-protected PDF remains untouched. A private,
                    // short-lived decrypted view copy is needed for Android PdfRenderer.
                    File dir = new File(getCacheDir(), "pdf");
                    candidate = File.createTempFile("protected-view-", ".pdf", dir);
                    boolean created = false;
                    try {
                        doc.setAllSecurityToBeRemoved(true);
                        doc.save(candidate);
                        created = true;
                    } finally {
                        if (!created) candidate.delete();
                    }
                }
                if (destroyed) {
                    if (candidate != sourceFile) candidate.delete();
                    doc.close();
                    return;
                }

                closePdfResources();
                textDocument = doc;
                // Keep previous protected-document editing permission behavior unchanged.
                pdfEditable = !encrypted && doc.getCurrentAccessPermission().canModify();
                pdfFile = candidate;
                pdfPfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(pdfPfd);
                synchronized (this) {
                    pageLinks.clear();
                    pageLinksScanned.clear();
                }
                runOnUiThread(() -> {
                    if (status != null) {
                        status.setText("PDF खुल रही है...");
                        status.setOnClickListener(null);
                    }
                });
                renderAllPages();
            } catch (InvalidPasswordException e) {
                if (doc != null && doc != textDocument) try { doc.close(); } catch (Exception ignored) {}
                closePdfResources();
                runOnUiThread(() -> showPasswordDialog(fromPrompt));
            } catch (Exception e) {
                if (doc != null && doc != textDocument) try { doc.close(); } catch (Exception ignored) {}
                closePdfResources();
                if (isPdfPasswordFailure(e)) {
                    // Some PDFs/providers report their password failure as IOException or
                    // SecurityException rather than PDFBox InvalidPasswordException.
                    runOnUiThread(() -> {
                        if (fromPrompt) {
                            showOpenError("यह पासवर्ड स्वीकार नहीं हुआ या PDF की encryption समर्थित नहीं है।");
                        } else {
                            showPasswordDialog(false);
                        }
                    });
                } else {
                    showOpenError("PDF नहीं खुल सकी। File खराब, अधूरी या असमर्थित हो सकती है।");
                }
            }
        }, "STS-PDF-OPEN").start();
    }

    private boolean isPdfPasswordFailure(Throwable error) {
        int depth = 0;
        while (error != null && depth++ < 5) {
            if (error instanceof InvalidPasswordException) return true;
            String detail = (error.getClass().getName() + " " + String.valueOf(error.getMessage()))
                    .toLowerCase(Locale.ROOT);
            if (detail.contains("password") || detail.contains("decrypt")
                    || detail.contains("encrypted") || detail.contains("encryption")
                    || detail.contains("cryptfilter") || detail.contains("crypt filter")
                    || detail.contains("badpadding")) return true;
            error = error.getCause();
        }
        return false;
    }
'''
assert s.count(old)==1, 'password open block not found'
s=s.replace(old,new)
oldload='''                openDocumentWithPassword(null, false);
            } catch (Exception e) {
                showOpenError();
            }
        }).start();
    }
'''
newload='''                if (sourceFile.length() == 0) throw new java.io.IOException("Empty PDF");
                openDocumentWithPassword(null, false);
            } catch (Exception e) {
                showOpenError("PDF file डाउनलोड या पढ़ी नहीं जा सकी। दोबारा प्रयास के लिए टैप करें।");
            }
        }).start();
    }
'''
assert s.count(oldload)==1,'loadPdf catch not found'
s=s.replace(oldload,newload)

err='''    private void showOpenError() {
        runOnUiThread(() -> {
            if (status != null) status.setText("PDF open नहीं हो पाया");
            Toast.makeText(this, "PDF open नहीं हो पाया", Toast.LENGTH_SHORT).show();
        });
    }
'''
newerr='''    private void showOpenError() {
        showOpenError("PDF open नहीं हो पाया। दोबारा प्रयास के लिए टैप करें।");
    }

    private void showOpenError(String reason) {
        runOnUiThread(() -> {
            if (destroyed || isFinishing()) return;
            if (status != null) {
                status.setText(reason + "\\n\\nRetry");
                status.setOnClickListener(v -> {
                    status.setOnClickListener(null);
                    status.setText("PDF loading...");
                    if (sourceFile != null && sourceFile.isFile() && sourceFile.length() > 0) {
                        openDocumentWithPassword(null, false);
                    } else {
                        loadPdf();
                    }
                });
            }
            Toast.makeText(this, "PDF open नहीं हो पाया", Toast.LENGTH_SHORT).show();
        });
    }
'''
assert s.count(err)==1,'showOpenError method not found'
s=s.replace(err,newerr)

# Private decrypted PDF rendering copies should never survive after the Viewer closes.
destroy='''        closePdfResources();
        super.onDestroy();
    }
'''
newdestroy='''        closePdfResources();
        if (pdfFile != null && pdfFile != sourceFile) {
            try { pdfFile.delete(); } catch (Exception ignored) {}
        }
        if (sourceFile != null) {
            try { sourceFile.delete(); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
'''
assert s.count(destroy)==1,'onDestroy footer not found'
s=s.replace(destroy,newdestroy)

# Incorrect password retry path exists; keep it in the same PDF Viewer.
assert 'showPasswordDialog(fromPrompt)' in s
assert 'InvalidPasswordException' in s
assert 'new String[]{"Text", "Replace", "Highlighter", "Undo", "Redo"}' in s
assert 'PdfNativeTextReplace.replace' in s

java.write_text(s)
gradle=root/'app/build.gradle'
g=gradle.read_text()
assert "versionCode 114" in g and "versionName '1.0.113'" in g
g=g.replace('versionCode 114','versionCode 115').replace("versionName '1.0.113'","versionName '1.0.114'")
gradle.write_text(g)
# Keep version-locked regression expectations in sync with the new APK version.
test = root/'review-tests/test-pdf-delete.py'
txt = test.read_text()
assert "versionName '1.0.113'" in txt and "'versionCode 114'" in txt
txt = txt.replace("versionName '1.0.113'", "versionName '1.0.114'").replace("'versionCode 114'", "'versionCode 115'")
test.write_text(txt)
print("PATCH V114: password error classification, retry and cleanup applied")
