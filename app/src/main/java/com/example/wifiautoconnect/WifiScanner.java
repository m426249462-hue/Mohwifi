package com.example.wifiautoconnect;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.List;

public class WifiScanner {

    private static final String TAG = "WifiScanner";
    private final Context context;

    public WifiScanner(Context context) {
        this.context = context.getApplicationContext();
    }

    public String verify(String targetSsid, String targetBssid) {
        if (targetSsid == null || targetSsid.isEmpty()) return "gray";

        try {
            WifiManager wifi = (WifiManager)
                    context.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return "gray";

            wifi.startScan();
            Thread.sleep(2500);

            List<ScanResult> results = wifi.getScanResults();
            if (results == null || results.isEmpty()) return "gray";

            String wantedBssid = (targetBssid == null || targetBssid.trim().isEmpty())
                    ? null
                    : targetBssid.trim().toUpperCase();

            boolean ssidFound = false;
            boolean bssidMatch = (wantedBssid == null);

            for (ScanResult net : results) {
                if (targetSsid.equals(net.SSID)) {
                    ssidFound = true;
                    if (wantedBssid != null
                            && net.BSSID != null
                            && net.BSSID.toUpperCase().equals(wantedBssid)) {
                        bssidMatch = true;
                        break;
                    }
                }
            }

            if (ssidFound && bssidMatch) return "green";
            return "red";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "gray";
        } catch (Exception e) {
            Log.e(TAG, "verify failed: " + e.getMessage());
            return "gray";
        }
    }
}
