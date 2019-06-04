package de.badener.links;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;

import de.badener.links.utils.AdBlocker;

public class MainActivity extends AppCompatActivity {

    private FrameLayout topBarContainer;
    private AppCompatTextView textViewURL;
    private ProgressBar progressBar;
    private WebView webView;
    private BottomNavigationView bottomNavigation;
    private FrameLayout fullScreen;

    private static final String startPage = "https://www.google.com/";
    private Icon launcherIcon;
    private boolean adBlockEnabled = true;
    private boolean isFullScreen = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        topBarContainer = findViewById(R.id.topBarContainer);
        textViewURL = findViewById(R.id.textViewURL);
        progressBar = findViewById(R.id.progressBar);
        webView = findViewById(R.id.webView);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        fullScreen = findViewById(R.id.fullScreenContainer);

        // WebView options
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        AdBlocker.init(MainActivity.this);

        // Handle "close button" in the top bar
        final ImageButton closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        // Handle "toggle ad block button" in the top bar
        final ImageButton adBlockButton = findViewById(R.id.adBlockButton);
        adBlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (adBlockEnabled) {
                    adBlockEnabled = false;
                    adBlockButton.setImageDrawable(getDrawable(R.drawable.ic_shield_off_outline));
                    webView.reload();
                } else {
                    adBlockEnabled = true;
                    adBlockButton.setImageDrawable(getDrawable(R.drawable.ic_shield_outline));
                    webView.reload();
                }
            }
        });

        // Handle clicks on the top bar
        textViewURL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                searchURL();
            }
        });

        // Handle the bottom navigation bar
        bottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {

                    case R.id.action_reload:
                        // Reload
                        webView.reload();
                        break;

                    case R.id.action_search:
                        // Search or load an URL
                        searchURL();
                        break;

                    case R.id.action_pin:
                        // Pin website shortcut to launcher home sreen
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            Intent pinShortcut = new Intent(MainActivity.this, MainActivity.class);
                            pinShortcut.setData(Uri.parse(webView.getUrl()));
                            pinShortcut.setAction(Intent.ACTION_MAIN);
                            getLauncherIcon();
                            String title = webView.getTitle();
                            ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(MainActivity.this, title)
                                    .setShortLabel(title)
                                    .setIcon(launcherIcon)
                                    .setIntent(pinShortcut)
                                    .build();
                            getSystemService(ShortcutManager.class).requestPinShortcut(shortcutInfo, null);
                        } else {
                            Intent pinShortcut = new Intent(MainActivity.this, MainActivity.class);
                            pinShortcut.setData(Uri.parse(webView.getUrl()));
                            pinShortcut.setAction(Intent.ACTION_MAIN);
                            getLauncherIcon();
                            Intent addIntent = new Intent();
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, webView.getTitle());
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, launcherIcon);
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, pinShortcut);
                            addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
                            sendBroadcast(addIntent);
                        }
                        break;

                    case R.id.action_share:
                        // Share URL
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("text/plain");
                        share.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                        startActivity(Intent.createChooser(share, getString(R.string.action_share_title)));
                        break;
                }
                return false;
            }
        });

        // Handle downloads
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                // Check if permission is granted
                if (isStoragePermissionGranted()) {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    request.setMimeType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url)));
                    DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    downloadManager.enqueue(request);
                    Toast.makeText(getApplicationContext(), R.string.download_started, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, R.string.storage_permission_needed, Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Handle intents
        Intent intent = getIntent();
        Uri uri = intent.getData();
        String url;
        if (uri != null) {
            // App was opened from another app
            url = uri.toString();
        } else {
            // App was opened from launcher
            url = startPage;
        }

        // Load either the start page (Google in this case) or the URL provided by an intent
        webView.loadUrl(url);

        webView.setWebChromeClient(new WebChromeClient() {

            // Hide/show and update the progress bar
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                if (progress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            // Enter fullscreen
            @Override
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                topBarContainer.setVisibility(View.GONE);
                bottomNavigation.setVisibility(View.GONE);
                fullScreen.setVisibility(View.VISIBLE);
                fullScreen.addView(view);
                enableFullScreen();
                isFullScreen = true;
            }

            // Exit fullscreen
            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                fullScreen.setVisibility(View.GONE);
                topBarContainer.setVisibility(View.VISIBLE);
                bottomNavigation.setVisibility(View.VISIBLE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                isFullScreen = false;
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            // Display and update the URL in the top bar
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                textViewURL.setText(webView.getUrl());
            }

            // Handle some external links
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("sms:")
                        || url.startsWith("whatsapp:") || url.startsWith("tg:") || url.contains("play.google.com")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(MainActivity.this, R.string.app_not_installed, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                } else {
                    return false;
                }
            }

            // Ad blocker based on this:
            // https://github.com/CarbonROM/android_packages_apps_Quarks/commit/a9abee9694c8dd239cda403bd99ea9e0922b90b5
            private final Map<String, Boolean> loadedUrls = new HashMap<>();

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                boolean ad;
                String url = request.getUrl().toString();
                if (adBlockEnabled) {
                    if (!loadedUrls.containsKey(url)) {
                        ad = AdBlocker.isAd(url);
                        loadedUrls.put(url, ad);
                    } else {
                        ad = loadedUrls.get(url);
                    }
                    return ad ? AdBlocker.createEmptyResource() : super.shouldInterceptRequest(view, request);
                } else {
                    return super.shouldInterceptRequest(view, request);
                }
            }
        });
    }

    // Search for a term or load a given URL
    private void searchURL() {
        final TextInputLayout textInputLayout = new TextInputLayout(MainActivity.this);
        final TextInputEditText textInput = new TextInputEditText(MainActivity.this);
        textInputLayout.setPadding(getResources().getDimensionPixelOffset(R.dimen.text_input_layout_padding), 0, getResources().getDimensionPixelOffset(R.dimen.text_input_layout_padding), 0);
        textInput.setSingleLine(true);
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        textInput.setText(webView.getUrl());
        textInput.setSelectAllOnFocus(true);
        textInputLayout.addView(textInput);
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(R.string.action_search_message)
                .setView(textInputLayout)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, int whichButton) {
                        if (textInput.getText() == null || textInput.getText().toString().equals(webView.getUrl())) {
                            dialog.dismiss();
                        } else {
                            String text = textInput.getText().toString();
                            String url;
                            if (URLUtil.isValidUrl(text)) { // Input is a valid URL
                                url = text;
                            } else if (text.contains(" ") || !text.contains(".")) { // Input is obviously no URL, start Google search
                                url = "https://www.google.com/search?q=" + text;
                            } else {
                                url = URLUtil.guessUrl(text); // Try to guess URL
                            }
                            webView.loadUrl(url);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    // Handle new intents
    @Override
    public void onNewIntent(Intent intent) {
        Uri uri = intent.getData();
        if (uri != null && !uri.toString().equals(webView.getUrl())) {
            webView.loadUrl(uri.toString());
        }
    }

    // Restore fullscreen after losing and gaining focus
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullScreen) {
            enableFullScreen();
        }
    }

    // Hide status bar and nav bar when entering fullscreen
    private void enableFullScreen() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Get favicon if available for launcher shortcuts
    private void getLauncherIcon() {
        if (webView.getFavicon() != null) {
            launcherIcon = Icon.createWithBitmap(webView.getFavicon());
        } else {
            launcherIcon = Icon.createWithResource(MainActivity.this, R.mipmap.ic_launcher);
        }
    }

    // Check if permission is granted to write on storage for downloading files
    private boolean isStoragePermissionGranted() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            // Ask for permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
    }

    // Prevent the back button from closing the app
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
