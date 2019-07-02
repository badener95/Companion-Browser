package de.badener.links;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import de.badener.links.utils.AdBlocking;

public class MainActivity extends AppCompatActivity {

    private static final String startPage = "https://www.google.com/";

    private WebView webView;
    private AppCompatImageView bottomBarShadow;
    private RelativeLayout bottomBar;
    private ImageButton webViewControlButton;
    private ImageButton openDefaultAppButton;
    private AppCompatTextView textViewURL;
    private ImageButton menuButton;
    private ProgressBar progressBar;
    private FrameLayout fullScreen;

    private boolean isLoading = true;
    private boolean isAdBlockingEnabled = true;
    private boolean isIncognitoMode = false;
    private boolean isFullScreen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        bottomBarShadow = findViewById(R.id.bottomBarShadow);
        bottomBar = findViewById(R.id.bottomBar);
        webViewControlButton = findViewById(R.id.webViewControlButton);
        openDefaultAppButton = findViewById(R.id.openDefaultAppButton);
        textViewURL = findViewById(R.id.textViewURL);
        menuButton = findViewById(R.id.menuButton);
        progressBar = findViewById(R.id.progressBar);
        fullScreen = findViewById(R.id.fullScreenContainer);

        // Change WebView settings
        changeWebViewSettings();

        // Initialize ad blocking
        AdBlocking.init(MainActivity.this);

        // Handle "WebView control button" in URL text field
        webViewControlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isLoading) {
                    webView.stopLoading();
                } else {
                    webView.reload();
                }
            }
        });

        // Handle "open default app button" in URL text field
        openDefaultAppButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl()));
                startActivity(intent);
            }
        });

        // Handle clicks on the URL text field
        textViewURL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                searchURL();
            }
        });

        // Handle "menu button" in bottom bar
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPopupMenu();
            }
        });

        // Handle downloads
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                // Check if storage permission is granted and start download if applicable
                if (isStoragePermissionGranted()) {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    request.setMimeType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url)));
                    DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    Objects.requireNonNull(downloadManager).enqueue(request);
                    Snackbar.make(findViewById(R.id.coordinatorLayout), R.string.download_started, Snackbar.LENGTH_SHORT).show();
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
        // Load either the start page or the URL provided by an intent
        webView.loadUrl(url);

        webView.setWebChromeClient(new WebChromeClient() {

            // Update the progress bar
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
            }

            // Enter fullscreen
            @Override
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                bottomBarShadow.setVisibility(View.GONE);
                bottomBar.setVisibility(View.GONE);
                fullScreen.setVisibility(View.VISIBLE);
                fullScreen.addView(view);
                enableFullScreen();
                isFullScreen = true;
            }

            // Exit fullscreen
            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                bottomBarShadow.setVisibility(View.VISIBLE);
                bottomBar.setVisibility(View.VISIBLE);
                fullScreen.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                isFullScreen = false;
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            // Ad blocking based on this:
            // https://github.com/CarbonROM/android_packages_apps_Quarks/commit/a9abee9694c8dd239cda403bd99ea9e0922b90b5
            private final Map<String, Boolean> loadedUrls = new HashMap<>();

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                boolean ad;
                String url = request.getUrl().toString();
                if (isAdBlockingEnabled) {
                    if (!loadedUrls.containsKey(url)) {
                        ad = AdBlocking.isAd(url);
                        loadedUrls.put(url, ad);
                    } else {
                        ad = loadedUrls.get(url);
                    }
                    return ad ? AdBlocking.createEmptyResource() : super.shouldInterceptRequest(view, request);
                } else {
                    return super.shouldInterceptRequest(view, request);
                }
            }

            // Show the progress bar and update the URL in the URL text field
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                textViewURL.setText(webView.getUrl());
                webViewControlButton.setImageDrawable(getDrawable(R.drawable.ic_cancel));
                showHideOpenDefaultAppButton();
                isLoading = true;
            }

            // Hide the progress bar
            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                webViewControlButton.setImageDrawable(getDrawable(R.drawable.ic_reload));
                isLoading = false;
            }

            // Handle external links
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!URLUtil.isValidUrl(url)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    PackageManager packageManager = MainActivity.this.getPackageManager();
                    List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
                    if (list.size() > 1) { // There is more than one app, show a chooser
                        startActivity(Intent.createChooser(intent, getString(R.string.chooser_open_app)));
                        return true;
                    } else if (list.size() > 0) { // There is just one app
                        startActivity(intent);
                        return true;
                    } else {
                        return false; // There is no app
                    }
                } else {
                    return false;
                }
            }
        });
    }

    // Change WebView settings
    @SuppressLint("SetJavaScriptEnabled")
    private void changeWebViewSettings() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setAppCacheEnabled(!isIncognitoMode);
        webView.getSettings().setDatabaseEnabled(!isIncognitoMode);
        webView.getSettings().setDomStorageEnabled(!isIncognitoMode);
        webView.getSettings().setGeolocationEnabled(false);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setAppCachePath(getApplicationContext().getCacheDir().getAbsolutePath());
        CookieManager.getInstance().setAcceptCookie(!isIncognitoMode); // Enable/disable cookies
    }

    // Search for a term or load a given URL
    private void searchURL() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        final TextInputLayout textInputLayout = new TextInputLayout(MainActivity.this);
        final TextInputEditText textInput = new TextInputEditText(MainActivity.this);
        textInputLayout.setPadding(getResources().getDimensionPixelOffset(R.dimen.text_input_layout_padding), 0, getResources().getDimensionPixelOffset(R.dimen.text_input_layout_padding), 0);
        textInput.setSingleLine(true);
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (isIncognitoMode) textInput.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        textInput.setText(webView.getUrl());
        textInput.setSelectAllOnFocus(true);
        textInputLayout.addView(textInput);
        builder.setMessage(R.string.search_message);
        builder.setView(textInputLayout);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                if (textInput.getText() == null || textInput.getText().toString().equals(webView.getUrl())) {
                    dialog.dismiss(); // No input
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
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
            }
        });
        final AlertDialog dialog = builder.create();
        // Show keyboard when alert dialog is shown
        textInput.requestFocus();
        Objects.requireNonNull(dialog.getWindow()).setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
    }

    // Show and handle the popup menu
    private void showPopupMenu() {
        final PopupMenu popup = new PopupMenu(MainActivity.this, menuButton);
        popup.inflate(R.menu.menu_main);

        // Change icon and text off "toggle ad blocking" option
        MenuItem toggleAdBlocking = popup.getMenu().findItem(R.id.action_toggle_ad_blocking);
        toggleAdBlocking.setTitle(getString(isAdBlockingEnabled ?
                R.string.action_disable_ad_blocking : R.string.action_enable_ad_blocking));
        toggleAdBlocking.setIcon(getDrawable(isAdBlockingEnabled ?
                R.drawable.ic_shield_outline : R.drawable.ic_shield_off_outline));

        // Change icon and text off "toggle icognito mode" option
        MenuItem toggleIncognitoMode = popup.getMenu().findItem(R.id.action_toggle_incognito_mode);
        toggleIncognitoMode.setTitle(getString(isIncognitoMode ?
                R.string.action_disable_incognito_mode : R.string.action_enable_incognito_mode));
        toggleIncognitoMode.setIcon(getDrawable(isIncognitoMode ?
                R.drawable.ic_sunglasses : R.drawable.ic_glasses));

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {

                    case R.id.action_new_window:
                        // Open new window
                        Intent newWindowIntent = new Intent(MainActivity.this, MainActivity.class);
                        newWindowIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                        startActivity(newWindowIntent);
                        return true;

                    case R.id.action_toggle_ad_blocking:
                        // Toggle ad blocking
                        if (isAdBlockingEnabled) {
                            isAdBlockingEnabled = false;
                            Snackbar.make(findViewById(R.id.coordinatorLayout), R.string.ad_blocking_disabled, Snackbar.LENGTH_SHORT).show();
                            webView.reload();
                        } else {
                            isAdBlockingEnabled = true;
                            Snackbar.make(findViewById(R.id.coordinatorLayout), R.string.ad_blocking_enabled, Snackbar.LENGTH_SHORT).show();
                            webView.reload();
                        }
                        return true;

                    case R.id.action_toggle_incognito_mode:
                        // Toggle incognito mode
                        if (isIncognitoMode) {
                            isIncognitoMode = false;
                            changeWebViewSettings();
                            Snackbar.make(findViewById(R.id.coordinatorLayout), R.string.icognito_mode_disabled, Snackbar.LENGTH_SHORT).show();
                        } else {
                            isIncognitoMode = true;
                            changeWebViewSettings();
                            Snackbar.make(findViewById(R.id.coordinatorLayout), R.string.icognito_mode_enabled, Snackbar.LENGTH_SHORT).show();
                        }
                        return true;

                    case R.id.action_share:
                        // Share URL
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.chooser_share)));
                        return true;

                    case R.id.action_add_shortcut:
                        // Pin website shortcut to launcher
                        pinShortcut();
                        return true;

                    case R.id.action_clear_data:
                        // Clear browsing data
                        clearBrowsingData();
                        return true;

                    case R.id.action_close_window:
                        // Close window
                        finishAndRemoveTask();
                        return true;

                    default:
                        return false;
                }
            }
        });
        // Show icons in menu
        final MenuPopupHelper menuHelper = new MenuPopupHelper(MainActivity.this, (MenuBuilder) popup.getMenu(), menuButton);
        menuHelper.setForceShowIcon(true);
        menuHelper.show();
    }

    // Pin website shortcut to launcher
    private void pinShortcut() {
        // Create a launcher icon for the shortcut first
        String[] colorArray = {"#d50000", "#c51162", "#aa00ff", "#2962ff",
                "#00bfa5", "#00c853", "#ffd600", "#ff6d00"};
        // Get a random color from the ones provide by the array
        String randomColor = (colorArray[new Random().nextInt(colorArray.length)]);
        // Draw a round background with the random color
        Bitmap icon = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);
        Paint paintCircle = new Paint();
        paintCircle.setAntiAlias(true);
        paintCircle.setColor(Color.parseColor(randomColor));
        paintCircle.setStyle(Paint.Style.FILL);
        canvas.drawCircle(96, 96, 96, paintCircle);

        // Get first two characters of website title
        String iconText = webView.getTitle().substring(0, 2);
        // Draw the first two characters on the background
        Paint paintText = new Paint();
        paintText.setAntiAlias(true);
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(112);
        paintText.setFakeBoldText(true);
        paintText.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(iconText, 192 / 2.0f, 192 / 2.0f - (paintText.descent() + paintText.ascent()) / 2.0f, paintText);
        // Create icon
        Icon launcherIcon = Icon.createWithBitmap(icon);

        // Create the shortcut
        Intent pinShortcutIntent = new Intent(MainActivity.this, MainActivity.class);
        pinShortcutIntent.setData(Uri.parse(webView.getUrl()));
        pinShortcutIntent.setAction(Intent.ACTION_MAIN);
        String shortcutTitle = webView.getTitle();
        ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(MainActivity.this, shortcutTitle)
                .setShortLabel(shortcutTitle)
                .setIcon(launcherIcon)
                .setIntent(pinShortcutIntent)
                .build();
        Objects.requireNonNull(getSystemService(ShortcutManager.class)).requestPinShortcut(shortcutInfo, null);
    }

    // Clear browsing data
    private void clearBrowsingData() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.clear_data_title)
                .setMessage(R.string.clear_data_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        webView.clearCache(true);
                        CookieManager.getInstance().removeAllCookies(null);
                        WebStorage.getInstance().deleteAllData();
                        webView.reload();
                        Snackbar.make(findViewById(R.id.coordinatorLayout), R.string.clear_data_confirmation, Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    // Show/hide "open default app button" in URL text field
    private void showHideOpenDefaultAppButton() {
        String packageName = "de.badener.links";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl()));
        PackageManager packageManager = MainActivity.this.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : list) {
            packageName = info.activityInfo.packageName;
        }
        if (packageName.equals("de.badener.links")) { // No other app can handle this link or "Links" is the default app
            openDefaultAppButton.setVisibility(View.GONE);
        } else {
            openDefaultAppButton.setVisibility(View.VISIBLE); // There is another app for this link
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

    // Enter picture in picture mode
    @Override
    public void onUserLeaveHint() {
        PackageManager packageManager = MainActivity.this.getPackageManager();
        if (isFullScreen && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPictureInPictureMode();
        }
    }

    // Check if permission is granted to write on storage for downloading files
    private boolean isStoragePermissionGranted() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true; // Permission is granted
        } else {
            // Ask for permission because it is not granted yet
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            Snackbar.make(findViewById(R.id.coordinatorLayout), R.string.storage_permission_needed, Snackbar.LENGTH_SHORT).show();
            return false;
        }
    }

    // Prevent the back button from closing the app
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finishAndRemoveTask();
        }
    }
}
