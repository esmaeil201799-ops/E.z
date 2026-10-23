package com.esmaeil.groceryapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_CREATE_BACKUP = 4101;
    private static final int REQUEST_PICK_BACKUP = 4102;
    private WebView webView;
    private String pendingBackupJson;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportMultipleWindows(false);
        webView.addJavascriptInterface(new BackupBridge(), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                Intent intent = params.createIntent();
                try { startActivityForResult(intent, REQUEST_PICK_BACKUP); }
                catch (ActivityNotFoundException e) {
                    fileChooserCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "تعذر فتح مدير الملفات.", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return route(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return route(url); }
        });
        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl("file:///android_asset/index.html");
    }

    private final class BackupBridge {
        @JavascriptInterface public void exportBackup(String json) {
            runOnUiThread(() -> {
                pendingBackupJson = json;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "محاسبة-بقالتي-نسخة-احتياطية.json");
                try { startActivityForResult(intent, REQUEST_CREATE_BACKUP); }
                catch (ActivityNotFoundException e) {
                    pendingBackupJson = null;
                    Toast.makeText(MainActivity.this, "تعذر فتح نافذة حفظ الملف.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CREATE_BACKUP) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingBackupJson != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new java.io.IOException("Output stream unavailable");
                    out.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "تم حفظ النسخة الاحتياطية.", Toast.LENGTH_LONG).show();
                    if (webView != null) webView.evaluateJavascript("if(window.backupExportComplete)window.backupExportComplete(true)", null);
                } catch (Exception e) {
                    Toast.makeText(this, "تعذر حفظ النسخة الاحتياطية.", Toast.LENGTH_LONG).show();
                    if (webView != null) webView.evaluateJavascript("if(window.backupExportComplete)window.backupExportComplete(false)", null);
                }
            }
            if (resultCode != RESULT_OK && webView != null) webView.evaluateJavascript("if(window.backupExportComplete)window.backupExportComplete(false)", null);
            pendingBackupJson = null;
        } else if (requestCode == REQUEST_PICK_BACKUP && fileChooserCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
        }
    }

    private boolean route(String url) {
        if (url.startsWith("file:///android_asset/")) return false;
        if (url.startsWith("tel:") || url.startsWith("https://wa.me/") || url.startsWith("https://api.whatsapp.com/")) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { Toast.makeText(this, "لا يوجد تطبيق مناسب لهذا الرابط.", Toast.LENGTH_SHORT).show(); }
            return true;
        }
        if (url.startsWith("https://")) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { Toast.makeText(this, "تعذر فتح الرابط.", Toast.LENGTH_SHORT).show(); }
            return true;
        }
        return true;
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (fileChooserCallback != null) { fileChooserCallback.onReceiveValue(null); fileChooserCallback = null; }
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
    }
}
