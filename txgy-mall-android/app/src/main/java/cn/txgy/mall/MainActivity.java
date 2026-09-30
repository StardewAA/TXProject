package cn.txgy.mall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Small Android host for the existing mobile mall. No JavaScript/native bridge. */
public final class MainActivity extends Activity {
    private static final int GREEN = 0xff0f806d;
    private static final int DARK = 0xff17343c;
    private static final int BG = 0xfff2f6f5;
    private static final int FILE_PICKER = 21;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService requests = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private EditText address;
    private TextView message;
    private Button connectButton;
    private ProgressBar progress;
    private WebView browser;
    private LinearLayout errorPanel;
    private String loadingPageUrl;
    private ValueCallback<Uri[]> fileCallback;
    private SharedPreferences preferences;
    private ConnectionTarget target;
    private int requestGeneration;
    private boolean closed;
    private boolean pageFailed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("mall-connection", MODE_PRIVATE);
        if (!handleConnectionIntent(getIntent())) {
            String saved = preferences.getString("url", "");
            if (saved.isEmpty()) showConnection("");
            else {
                try { showLoading(); verifyConnection(ConnectionTarget.parse(saved)); }
                catch (IllegalArgumentException error) { showConnection(error.getMessage()); }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleConnectionIntent(intent);
    }

    private boolean handleConnectionIntent(Intent intent) {
        Uri link = intent == null ? null : intent.getData();
        if (link == null) return false;
        if (!"txgymall".equals(link.getScheme()) || !"connect".equals(link.getHost())) return false;
        String value;
        try { value = link.getQueryParameter("url"); } catch (Exception e) { value = null; }
        if (value == null) { showConnection("连接链接不完整，请重新打开电脑商城的下载页"); return true; }
        try {
            ConnectionTarget selected = ConnectionTarget.parse(value);
            String saved = preferences.getString("url", "");
            if (!saved.isEmpty() && !saved.equals(selected.entryUrl)) {
                showConnection("");
                address.setText(value);
                connect(true);
            } else {
                showLoading();
                verifyConnection(selected);
            }
        } catch (IllegalArgumentException error) { showConnection(error.getMessage()); }
        return true;
    }

    private void showLoading() {
        requestGeneration++;
        releaseBrowser();
        newRoot();
        LinearLayout content = column(24, 30);
        content.setGravity(Gravity.CENTER);
        root.addView(content, new LinearLayout.LayoutParams(-1, -1));
        ProgressBar spinner = new ProgressBar(this);
        content.addView(spinner);
        TextView status = label("正在打开商城…", 16, DARK);
        status.setGravity(Gravity.CENTER);
        add(content, status, 18, 0);
        address = null;
        message = null;
        connectButton = null;
    }

    private void newRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
    }

    private void showConnection(String error) {
        requestGeneration++;
        releaseBrowser();
        newRoot();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(24, 30);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        TextView mark = label("防", 34, Color.WHITE);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(GREEN, 20));
        LinearLayout.LayoutParams markSize = new LinearLayout.LayoutParams(dp(68), dp(68));
        markSize.gravity = Gravity.CENTER; markSize.bottomMargin = dp(18);
        content.addView(mark, markSize);
        TextView title = label("防腐保温商城", 26, DARK);
        title.setTypeface(null, Typeface.BOLD); title.setGravity(Gravity.CENTER);
        content.addView(title);
        TextView subtitle = label("连接商城，开始手机采购", 14, 0xff75887f);
        subtitle.setGravity(Gravity.CENTER); add(content, subtitle, 10, 28);
        LinearLayout card = column(20, 24);
        card.setBackground(rounded(Color.WHITE, 20)); content.addView(card);
        TextView heading = label("连接你的商城", 20, DARK); heading.setTypeface(null, Typeface.BOLD); card.addView(heading);
        add(card, label("先用手机扫码打开电脑商城的下载页，再点击“打开并连接 App”。也可以填写移动商城地址。", 14, 0xff70827a), 14, 18);
        address = new EditText(this);
        address.setSingleLine(true); address.setTextSize(14);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint("http://电脑局域网IP:3000");
        address.setText(preferences.getString("url", ""));
        address.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable inputBackground = rounded(0xfff6f9f8, 10); inputBackground.setStroke(dp(1), 0xffd9e5df); address.setBackground(inputBackground);
        card.addView(address, new LinearLayout.LayoutParams(-1, dp(50)));
        message = label(error, 13, 0xffb4492e); add(card, message, 12, 6);
        connectButton = button("连接并进入商城", true); connectButton.setOnClickListener(v -> connect(true)); add(card, connectButton, 8, 0);
        add(content, label("手机和部署商城的电脑应连接同一网络，电脑保持开机。地址变化后，在手机浏览器重新打开电脑商城下载页，点击“打开并连接 App”即可切换。", 13, 0xff70827a), 22, 16);
        add(content, label("Android 教学演示版 · cn.txgy.mall", 12, 0xff8b9a92), 0, 10);
    }

    private void connect(boolean ask) {
        final ConnectionTarget selected;
        try { selected = ConnectionTarget.parse(address.getText().toString()); }
        catch (IllegalArgumentException e) { message.setText(e.getMessage()); return; }
        String saved = preferences.getString("url", "");
        if (ask && !saved.isEmpty() && !selected.entryUrl.equals(saved)) {
            new AlertDialog.Builder(this).setTitle("连接商城")
                    .setMessage(selected.entryUrl + "\n\n切换商城地址后需重新登录。")
                    .setNegativeButton("取消", null).setPositiveButton("连接", (dialog, which) -> verifyConnection(selected)).show();
        } else verifyConnection(selected);
    }

    private void verifyConnection(ConnectionTarget selected) {
        final int generation = ++requestGeneration;
        if (connectButton != null) { connectButton.setEnabled(false); connectButton.setText("正在连接…"); }
        if (message != null) message.setText("");
        requests.execute(() -> {
            String error = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(selected.markerUrl).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(6000); connection.setReadTimeout(6000);
                connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() != 200) throw new IllegalArgumentException("此地址未找到移动商城，请核对 H5 地址和部署版本");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream stream = connection.getInputStream()) {
                    byte[] buffer = new byte[1024]; int count;
                    while ((count = stream.read(buffer)) != -1) {
                        if (bytes.size() + count > 8192) throw new IllegalArgumentException("连接配置不正确，请核对移动商城地址");
                        bytes.write(buffer, 0, count);
                    }
                }
                JSONObject marker = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                if (!"cn.txgy.mall".equals(marker.optString("app")) || marker.optInt("connectionVersion") != 1) {
                    throw new IllegalArgumentException("此地址不是支持连接的移动商城，请更新部署版本");
                }
            } catch (IllegalArgumentException e) { error = e.getMessage(); }
            catch (Exception e) { error = "无法连接，请检查商城已启动、手机和电脑在同一网络，以及端口是否允许访问。HTTPS 地址需使用有效证书。"; }
            finally { if (connection != null) connection.disconnect(); }
            final String problem = error;
            ui.post(() -> {
                if (closed || generation != requestGeneration) return;
                if (connectButton != null) { connectButton.setEnabled(true); connectButton.setText("连接并进入商城"); }
                if (problem != null) { showConnection(problem); return; }
                boolean changed = !selected.entryUrl.equals(preferences.getString("url", ""));
                target = selected;
                preferences.edit().putString("url", selected.entryUrl).apply();
                if (changed) {
                    WebStorage.getInstance().deleteAllData();
                    CookieManager.getInstance().removeAllCookies(done -> { if (!closed && generation == requestGeneration) openMall(); });
                } else openMall();
            });
        });
    }

    private void openMall() {
        releaseBrowser(); newRoot(); pageFailed = false; loadingPageUrl = null;
        // The mobile mall already renders its own navigation. A second native
        // toolbar leaves a blank band and duplicates the back button on phones.
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        browser = new WebView(this);
        WebSettings settingsApi = browser.getSettings();
        settingsApi.setJavaScriptEnabled(true); settingsApi.setDomStorageEnabled(true);
        settingsApi.setAllowFileAccess(false); settingsApi.setAllowContentAccess(false);
        settingsApi.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settingsApi.setJavaScriptCanOpenWindowsAutomatically(false); settingsApi.setMediaPlaybackRequiresUserGesture(true);
        settingsApi.setUserAgentString(settingsApi.getUserAgentString() + " TxgyMallAndroid/1.0");
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (target.contains(url)) return false;
                if (request.hasGesture()) openExternal(url);
                else toast("该链接需要在浏览器中打开");
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                loadingPageUrl = url;
                pageFailed = false;
                if (errorPanel != null) errorPanel.setVisibility(View.GONE);
                view.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) { if (!closed && !pageFailed && progress != null) progress.setVisibility(View.GONE); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) { if (request.isForMainFrame()) pageError("商城页面未能加载，请检查网络或重新连接"); }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) { if (request.isForMainFrame()) pageError("商城服务暂不可用（HTTP " + response.getStatusCode() + "）"); }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                // This callback also fires for product images on a different host. A broken
                // image must not destroy the signed-in mall page or its navigation state.
                if (PageLoadPolicy.isMainDocumentSslError(error.getUrl(), loadingPageUrl)) pageError("商城 HTTPS 证书校验失败，请检查服务器证书");
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { if (progress != null) { progress.setProgress(value); progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE); } }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try { startActivityForResult(params.createIntent(), FILE_PICKER); }
                catch (Exception e) { fileCallback.onReceiveValue(null); fileCallback = null; toast("无法打开系统文件选择器"); }
                return true;
            }
        });
        browser.setDownloadListener((url, agent, disposition, mime, length) -> openExternal(url));
        root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        browser.loadUrl(target.entryUrl);
    }

    private void pageError(String text) {
        if (pageFailed || closed) return;
        pageFailed = true;
        ui.post(() -> {
            if (closed || !pageFailed || browser == null) return;
            if (errorPanel != null) root.removeView(errorPanel);
            errorPanel = column(24, 24);
            errorPanel.setGravity(Gravity.CENTER);
            TextView message = label(text, 16, DARK);
            message.setGravity(Gravity.CENTER);
            errorPanel.addView(message);
            Button retry = button("重试加载", true);
            retry.setOnClickListener(v -> {
                pageFailed = false;
                errorPanel.setVisibility(View.GONE);
                browser.setVisibility(View.VISIBLE);
                String url = browser.getUrl();
                browser.loadUrl(url != null && target.contains(url) ? url : target.entryUrl);
            });
            add(errorPanel, retry, 22, 0);
            Button change = button("切换商城地址", false);
            change.setOnClickListener(v -> showConnection(""));
            add(errorPanel, change, 8, 0);
            browser.setVisibility(View.GONE);
            if (progress != null) progress.setVisibility(View.GONE);
            root.addView(errorPanel, new LinearLayout.LayoutParams(-1, 0, 1));
        });
    }

    private void openExternal(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        boolean web = ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) && uri.getHost() != null && uri.getUserInfo() == null;
        boolean phone = "tel".equalsIgnoreCase(scheme) && uri.getSchemeSpecificPart().matches("[+0-9][0-9 -]*");
        if (!web && !phone) { toast("暂不支持此类外部链接"); return; }
        new AlertDialog.Builder(this).setTitle(phone ? "打开拨号界面" : "在浏览器打开")
                .setMessage(phone ? uri.getSchemeSpecificPart() : uri.getHost())
                .setNegativeButton("取消", null).setPositiveButton("打开", (dialog, which) -> {
                    try { startActivity(new Intent(phone ? Intent.ACTION_DIAL : Intent.ACTION_VIEW, uri)); }
                    catch (Exception e) { toast("手机没有可打开此链接的应用"); }
                }).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_PICKER && fileCallback != null) {
            Uri[] files = WebChromeClient.FileChooserParams.parseResult(result, data);
            if (files != null) for (Uri file : files) if (file == null || !"content".equals(file.getScheme())) { files = null; break; }
            fileCallback.onReceiveValue(files); fileCallback = null;
        }
    }

    private void back() { if (browser != null && browser.canGoBack()) browser.goBack(); else finish(); }
    @Override public void onBackPressed() { back(); }
    @Override protected void onDestroy() { closed = true; requestGeneration++; requests.shutdownNow(); ui.removeCallbacksAndMessages(null); releaseBrowser(); super.onDestroy(); }
    private void releaseBrowser() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (browser != null) {
            browser.stopLoading(); browser.setWebViewClient(new WebViewClient()); browser.setWebChromeClient(null);
            if (browser.getParent() instanceof ViewGroup) ((ViewGroup) browser.getParent()).removeView(browser);
            browser.destroy(); browser = null;
        }
        progress = null;
        errorPanel = null;
        loadingPageUrl = null;
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String text, int size, int color) { TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(4), 1); return view; }
    private LinearLayout column(int horizontal, int vertical) { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); view.setPadding(dp(horizontal), dp(vertical), dp(horizontal), dp(vertical)); return view; }
    private GradientDrawable rounded(int color, int radius) { GradientDrawable background = new GradientDrawable(); background.setColor(color); background.setCornerRadius(dp(radius)); return background; }
    private Button button(String text, boolean primary) { Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setTextSize(15); button.setTextColor(primary ? Color.WHITE : GREEN); button.setBackground(rounded(primary ? GREEN : Color.TRANSPARENT, 10)); return button; }
    private void add(LinearLayout parent, View view, int top, int bottom) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); params.bottomMargin = dp(bottom); parent.addView(view, params); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
