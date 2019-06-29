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
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.drawable.DrawableCompat;

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
    private RelativeLayout bottomBarContainer;
    private ImageButton webViewControlButton;
    private ImageButton openDefaultAppButton;
    private AppCompatTextView textViewURL;
    private ImageButton menuButton;
    private ProgressBar progressBar;
    private FrameLayout fullScreen;

    private Icon launcherIcon;
    private boolean isLoading = true;
    private boolean isAdBlockingEnabled = true;
    private boolean isFullScreen = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        bottomBarContainer = findViewById(R.id.bottomBarContainer);
        webViewControlButton = findViewById(R.id.webViewControlButton);
        openDefaultAppButton = findViewById(R.id.openDefaultAppButton);
        textViewURL = findViewById(R.id.textViewURL);
        menuButton = findViewById(R.id.menuButton);
        progressBar = findViewById(R.id.progressBar);
        fullScreen = findViewById(R.id.fullScreenContainer);

        // WebView options
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

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
                    Toast toast = Toast.makeText(MainActivity.this, R.string.download_started, Toast.LENGTH_SHORT);
                    View view = toast.getView();
                    DrawableCompat.setTint(view.getBackground(), getColor(R.color.colorPrimaryDark));
                    TextView text = view.findViewById(android.R.id.message);
                    text.setTextColor(Color.WHITE);
                    toast.show();
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

            // Update the progress bar
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
            }

            // Enter fullscreen
            @Override
            public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                bottomBarContainer.setVisibility(View.GONE);
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
                bottomBarContainer.setVisibility(View.VISIBLE);
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
                    PackageManager manager = MainActivity.this.getPackageManager();
                    List<ResolveInfo> list = manager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
                    if (list.size() > 1) { // There is more than one app, show a chooser
                        startActivity(Intent.createChooser(intent, getString(R.string.open_title)));
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

    // Search for a term or load a given URL
    private void searchURL() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.AlertDialogTheme);
        final TextInputLayout textInputLayout = new TextInputLayout(MainActivity.this);
        final TextInputEditText textInput = new TextInputEditText(MainActivity.this);

        textInputLayout.setPadding(getResources().getDimensionPixelOffset(R.dimen.text_input_layout_padding), 0, getResources().getDimensionPixelOffset(R.dimen.text_input_layout_padding), 0);
        textInput.setSingleLine(true);
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        textInput.setText(webView.getUrl());
        textInput.setSelectAllOnFocus(true);
        textInputLayout.addView(textInput);
        builder.setMessage(R.string.search_message);
        builder.setView(textInputLayout);

        builder.setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, int whichButton) {
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

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
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
        PopupMenu popup = new PopupMenu(MainActivity.this, menuButton);
        popup.inflate(R.menu.menu_main);

        // Change icon and text off "toggle ad blocking" option
        MenuItem toggleAdBlocking = popup.getMenu().findItem(R.id.action_toggle_ad_blocking);
        toggleAdBlocking.setTitle(getString(isAdBlockingEnabled ?
                R.string.action_disable_ad_blocking : R.string.action_enable_ad_blocking));
        toggleAdBlocking.setIcon(getDrawable(isAdBlockingEnabled ?
                R.drawable.ic_shield_outline : R.drawable.ic_shield_off_outline));

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
                            Toast toast = Toast.makeText(MainActivity.this, R.string.ad_blocking_disabled, Toast.LENGTH_SHORT);
                            View view = toast.getView();
                            DrawableCompat.setTint(view.getBackground(), getColor(R.color.colorPrimaryDark));
                            TextView text = view.findViewById(android.R.id.message);
                            text.setTextColor(Color.WHITE);
                            toast.show();
                            webView.reload();
                        } else {
                            isAdBlockingEnabled = true;
                            Toast toast = Toast.makeText(MainActivity.this, R.string.ad_blocking_enabled, Toast.LENGTH_SHORT);
                            View view = toast.getView();
                            DrawableCompat.setTint(view.getBackground(), getColor(R.color.colorPrimaryDark));
                            TextView text = view.findViewById(android.R.id.message);
                            text.setTextColor(Color.WHITE);
                            toast.show();
                            webView.reload();
                        }
                        return true;

                    case R.id.action_share:
                        // Share URL
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_title)));
                        return true;

                    case R.id.action_add_shortcut:
                        // Pin website shortcut to launcher
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
                        Objects.requireNonNull(getSystemService(ShortcutManager.class)).requestPinShortcut(shortcutInfo, null);
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
        MenuPopupHelper menuHelper = new MenuPopupHelper(MainActivity.this, (MenuBuilder) popup.getMenu(), menuButton);
        menuHelper.setForceShowIcon(true);
        menuHelper.show();
    }

    // Show/hide "open default app button" in URL text field
    private void showHideOpenDefaultAppButton() {
        String packageName = "de.badener.links";
        PackageManager packageManager = MainActivity.this.getPackageManager();
        Intent packagesIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl()));
        List<ResolveInfo> list = packageManager.queryIntentActivities(packagesIntent, PackageManager.MATCH_DEFAULT_ONLY);
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

    // Create a launcher icon for shortcuts
    private void getLauncherIcon() {

        // Get a random color from the ones provide by the array
        String[] colorArray = {"#d50000", "#c51162", "#aa00ff", "#2962ff",
                "#00bfA5", "#00c853", "#ffd600", "#ff6d00"};
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
        String title = webView.getTitle().substring(0, 2);

        // Draw the first two characters on the background
        Paint paintText = new Paint();
        paintText.setAntiAlias(true);
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(112);
        paintText.setFakeBoldText(true);
        paintText.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title, 192 / 2.0f, 192 / 2.0f - (paintText.descent() + paintText.ascent()) / 2.0f, paintText);

        // Create icon
        launcherIcon = Icon.createWithBitmap(icon);
    }

    // Check if permission is granted to write on storage for downloading files
    private boolean isStoragePermissionGranted() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true; // Permission is granted
        } else {
            // Ask for permission because it is not granted yet
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            Toast toast = Toast.makeText(MainActivity.this, R.string.storage_permission_needed, Toast.LENGTH_SHORT);
            View view = toast.getView();
            DrawableCompat.setTint(view.getBackground(), getColor(R.color.colorPrimaryDark));
            TextView text = view.findViewById(android.R.id.message);
            text.setTextColor(Color.WHITE);
            toast.show();
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
