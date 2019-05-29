package de.badener.links;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private FrameLayout topBarContainer;
    private AppCompatTextView textViewURL;
    private ProgressBar progressBar;
    private WebView webView;
    private BottomNavigationView bottomNavigation;
    private FrameLayout fullScreen;

    private static final String startPage = "https://www.google.com/";
    private Icon launcherIcon;
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

        // Handle close button in the top bar
        final ImageButton closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        // Handle the bottom navigation bar
        bottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {

                    case R.id.action_home:
                        if (!webView.getUrl().equals(startPage)) {
                            webView.loadUrl(startPage);
                        }
                        break;

                    case R.id.action_reload:
                        webView.reload();
                        break;

                    case R.id.action_load_url:
                        final TextInputLayout textInputLayout = new TextInputLayout(MainActivity.this);
                        final TextInputEditText textInput = new TextInputEditText(MainActivity.this);
                        textInputLayout.setPadding(getResources().getDimensionPixelOffset(R.dimen.text_input_layout_padding), 0,
                                getResources().getDimensionPixelOffset(R.dimen.text_input_layout_padding), 0);
                        textInputLayout.setHint(getString(R.string.action_load_url_hint));
                        textInput.setSingleLine(true);
                        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                        textInput.setText(webView.getUrl());
                        textInput.setSelectAllOnFocus(true);
                        textInputLayout.addView(textInput);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(R.string.action_load_url)
                                .setMessage(R.string.action_load_url_message)
                                .setView(textInputLayout)
                                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                                    public void onClick(final DialogInterface dialog, int whichButton) {
                                        String text = Objects.requireNonNull(textInput.getText()).toString();
                                        if (text.isEmpty() || text.equals(webView.getUrl())) {
                                            dialog.dismiss();
                                        } else {
                                            String url;
                                            if (text.startsWith("https://") || text.startsWith("http://")) {
                                                url = text;
                                            } else {
                                                url = "https://" + text;
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
                        break;

                    case R.id.action_pin:
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
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("text/plain");
                        share.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                        startActivity(Intent.createChooser(share, getString(R.string.action_share_title)));
                        break;
                }
                return false;
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
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                isFullScreen = false;
            }
        });

        // Display URL in the top bar
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                textViewURL.setText(webView.getUrl());
            }

            // Handle external links
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(request.getUrl().toString()));
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                    return true;
                } else {
                    return false;
                }
            }
        });
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
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
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

    // Prevent the back-button from closing the app
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
