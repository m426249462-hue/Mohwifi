package com.example.wifiautoconnect;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WifiConnector {

    private static final String TAG = "WifiConnector";
    private final Context context;

    public WifiConnector(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean tryConnect(String ssid, String bssid,
                              String password, long timeoutSec) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        try {
            WifiNetworkSpecifier.Builder builder =
                    new WifiNetworkSpecifier.Builder().setSsid(ssid);

            if (bssid != null && !bssid.trim().isEmpty()) {
                try {
                    builder.setBssid(MacAddress.fromString(bssid.trim()));
                } catch (Exception e) {
                    Log.w(TAG, "Invalid BSSID: " + bssid);
                }
            }

            try {
                builder.setWpa2Passphrase(password);
            } catch (Exception e1) {
                try {
                    builder.setWpa3Passphrase(password);
                } catch (Exception e2) {
                    Log.w(TAG, "Both WPA2 and WPA3 failed: " + e2.getMessage());
                }
            }

            WifiNetworkSpecifier specifier = builder.build();

            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build();

            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] success = {false};

            ConnectivityManager.NetworkCallback callback =
                    new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    success[0] = true;
                    latch.countDown();
                }

                @Override
                public void onLost(@NonNull Network network) {
                    latch.countDown();
                }

                @Override
                public void onUnavailable() {
                    latch.countDown();
                }
            };

            try {
                cm.requestNetwork(request, callback);
                latch.await(timeoutSec, TimeUnit.SECONDS);
                return success[0];
            } finally {
                try {
                    cm.unregisterNetworkCallback(callback);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "tryConnect failed: " + e.getMessage());
            return false;
        }
    }
                }
