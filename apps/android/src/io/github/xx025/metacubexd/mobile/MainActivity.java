package io.github.xx025.metacubexd.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.MimeTypeMap;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONException;

/** A bundled dashboard only. No proxy kernel, VPN service, or native JS bridge. */
public final class MainActivity extends Activity {
    // A stable HTTP origin supports both HTTP and HTTPS router APIs and keeps
    // localStorage stable across activity recreation and signed app updates.
    private static final String ORIGIN = "http://localhost/";
    private WebView webView;
    private View statusBarBackground;
    private View navigationBarBackground;
    private ValueCallback<Uri[]> fileCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xff101827);
        statusBarBackground = new View(this);
        navigationBarBackground = new View(this);
        root.addView(statusBarBackground, new FrameLayout.LayoutParams(-1, 0, Gravity.TOP));
        root.addView(navigationBarBackground, new FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM));
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        // Draw our own theme backgrounds behind transparent system bars. This
        // works with Android 15's enforced edge-to-edge layout and older releases.
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        applySystemBarColors(getPreferences(0).getInt("statusBarColor", 0xff101827),
            getPreferences(0).getInt("navigationBarColor", 0xff101827));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                FrameLayout.LayoutParams content = (FrameLayout.LayoutParams) webView.getLayoutParams();
                content.setMargins(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                webView.setLayoutParams(content);
                statusBarBackground.setLayoutParams(new FrameLayout.LayoutParams(-1,
                    insets.getSystemWindowInsetTop(), Gravity.TOP));
                navigationBarBackground.setLayoutParams(new FrameLayout.LayoutParams(-1,
                    insets.getSystemWindowInsetBottom(), Gravity.BOTTOM));
                return insets.consumeSystemWindowInsets();
            }
        });
        root.requestApplyInsets();
        WebSettings settings = webView.getSettings();
        // Make dashboard text more compact while retaining Android's font scale.
        settings.setTextZoom(Math.round(settings.getTextZoom() * 0.85f));
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // Android document picker imports
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (!isLocal(Uri.parse(url))) return;
                try (java.io.InputStream input = getAssets().open("system-bars.js")) {
                    java.io.ByteArrayOutputStream script = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) script.write(buffer, 0, count);
                    view.evaluateJavascript(script.toString("UTF-8")
                        + "\n//# sourceURL=http://localhost/system-bars.js", null);
                } catch (IOException ignored) { /* cached colors remain available */ }
            }
            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!isLocal(uri)) return null; // router REST calls use normal networking
                String path = uri.getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                if (!request.getMethod().equals("GET") || path.contains("..")
                        || path.contains("\\")) return notFound();
                try {
                    return new WebResourceResponse(mime(path), "UTF-8", 200, "OK",
                        Collections.singletonMap("Cache-Control", "no-cache"),
                        getAssets().open("public" + path));
                } catch (IOException missing) {
                    return notFound();
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,
                    WebResourceRequest request) {
                if (isLocal(request.getUrl())) return false;
                // External documents/links open outside the dashboard storage context.
                if (request.isForMainFrame()) openExternal(request.getUrl());
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                String prefix = "MCXD_SYSTEM_BARS:";
                if (message.message().startsWith(prefix)
                        && isLocal(Uri.parse(message.sourceId()))
                        && webView.getUrl() != null && isLocal(Uri.parse(webView.getUrl()))) {
                    try {
                        JSONArray colors = new JSONArray(message.message().substring(prefix.length()));
                        if (colors.length() == 2) applySystemBarColors(
                            0xff000000 | (colors.getInt(0) & 0xffffff),
                            0xff000000 | (colors.getInt(1) & 0xffffff));
                    } catch (JSONException ignored) { /* malformed color message */ }
                    return true;
                }
                return super.onConsoleMessage(message);
            }
            @Override public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), 100);
                } catch (ActivityNotFoundException unavailable) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                }
                return true;
            }
        });
        if (state == null || webView.restoreState(state) == null) webView.loadUrl(ORIGIN);
    }

    private void applySystemBarColors(int status, int navigation) {
        statusBarBackground.setBackgroundColor(status);
        navigationBarBackground.setBackgroundColor(navigation);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility()
            & ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (isLight(status)) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (isLight(navigation)) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
        getPreferences(0).edit().putInt("statusBarColor", status)
            .putInt("navigationBarColor", navigation).apply();
    }

    private static boolean isLight(int color) {
        // Relative luminance chooses black/white icons for the better contrast.
        double[] channels = { Color.red(color) / 255.0, Color.green(color) / 255.0,
            Color.blue(color) / 255.0 };
        for (int i = 0; i < channels.length; i++) channels[i] = channels[i] <= 0.04045
            ? channels[i] / 12.92 : Math.pow((channels[i] + 0.055) / 1.055, 2.4);
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722 > 0.179;
    }

    private static boolean isLocal(Uri uri) {
        return "http".equals(uri.getScheme()) && "localhost".equals(uri.getHost())
            && (uri.getPort() == -1 || uri.getPort() == 80);
    }

    private static String mime(String path) {
        if (path.endsWith(".js") || path.endsWith(".mjs")) return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".woff2")) return "font/woff2";
        String extension = MimeTypeMap.getFileExtensionFromUrl(path);
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return type == null ? "application/octet-stream" : type;
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
            Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private void openExternal(Uri uri) {
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException ignored) { /* no browser installed */ }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 100 && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override protected void onPause() {
        webView.onPause();
        android.webkit.CookieManager.getInstance().flush();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }
    @Override protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        webView.destroy();
        super.onDestroy();
    }
}
