package de.badener.companion_browser.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.webkit.WebResourceResponse;

import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import de.badener.companion_browser.R;

/** @noinspection deprecation */
public class AdBlocking {
    private static final int AD_HOSTS_FILE = R.raw.hosts;
    private static final Set<String> AD_HOSTS = new HashSet<>();

    public static void init(final Context context) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    loadFromAssets(context);
                } catch (IOException ignored) {
                }
                return null;
            }
        }.execute();
    }

    @WorkerThread
    private static void loadFromAssets(Context context) throws IOException {
        InputStream stream = context.getResources().openRawResource(AD_HOSTS_FILE);
        BufferedReader buffer = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = buffer.readLine()) != null) {
            AD_HOSTS.add(line);
        }
        buffer.close();
        stream.close();
    }

    public static boolean isAd(String url) {
        try {
            URL netUrl = new URL(url);
            return isAdHost(netUrl.getHost());
        } catch (MalformedURLException ignored) {
        }
        return false;
    }

    private static boolean isAdHost(String host) {
        if (TextUtils.isEmpty(host)) {
            return false;
        }
        int index = host.indexOf('.');
        return index >= 0 && (AD_HOSTS.contains(host) || index + 1 < host.length() && isAdHost(host.substring(index + 1)));
    }

    public static WebResourceResponse createEmptyResource() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
    }
}
