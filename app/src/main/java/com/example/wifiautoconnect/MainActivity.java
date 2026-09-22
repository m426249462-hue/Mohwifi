package com.example.wifiautoconnect;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQ_PERMS = 100;
    private static final int FILE_REQ = 1001;

    private EditText ssidInput, bssidInput, pathInput;
    private TextView verifyDot, speedLabel, statusLabel, logBox;
    private Button verifyBtn, loadBtn, browseBtn, startBtn, copyBtn, showBtn, resetBtn;
    private SeekBar speedSlider;
    private ScrollView logScroll;

    private List<String> passwords = new ArrayList<>();
    private boolean running = false;
    private String lastResult = "";
    private long startTime = 0L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private WifiConnector connector;
    private WifiScanner scanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connector = new WifiConnector(this);
        scanner = new WifiScanner(this);

        ssidInput = findViewById(R.id.ssidInput);
        bssidInput = findViewById(R.id.bssidInput);
        pathInput = findViewById(R.id.pathInput);
        verifyDot = findViewById(R.id.verifyDot);
        verifyBtn = findViewById(R.id.verifyBtn);
        loadBtn = findViewById(R.id.loadBtn);
        browseBtn = findViewById(R.id.browseBtn);
        speedSlider = findViewById(R.id.speedSlider);
        speedLabel = findViewById(R.id.speedLabel);
        startBtn = findViewById(R.id.startBtn);
        statusLabel = findViewById(R.id.statusLabel);
        logBox = findViewById(R.id.logBox);
        logScroll = findViewById(R.id.logScroll);
        copyBtn = findViewById(R.id.copyBtn);
        showBtn = findViewById(R.id.showBtn);
        resetBtn = findViewById(R.id.resetBtn);

        requestPermissions();
        updateSpeedLabel(speedSlider.getProgress());

        verifyBtn.setOnClickListener(v -> onVerify());
        loadBtn.setOnClickListener(v -> onLoad());
        browseBtn.setOnClickListener(v -> onBrowse());
        startBtn.setOnClickListener(v -> onStartStop());
        copyBtn.setOnClickListener(v -> onCopy());
        showBtn.setOnClickListener(v -> onShowFound());
        resetBtn.setOnClickListener(v -> onReset());

        speedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                updateSpeedLabel(p);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void requestPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_WIFI_STATE);
        perms.add(Manifest.permission.CHANGE_WIFI_STATE);
        perms.add(Manifest.permission.ACCESS_NETWORK_STATE);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        List<String> toAsk = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                toAsk.add(p);
            }
        }
        if (!toAsk.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    toAsk.toArray(new String[0]), REQ_PERMS);
        }
    }

    private void updateSpeedLabel(int progress) {
        float seconds = 0.5f + progress * 0.5f;
        speedLabel.setText(String.format(Locale.US,
                "المهلة لكل محاولة: %.1f ثانية", seconds));
    }

    private float getSpeedSeconds() {
        return 0.5f + speedSlider.getProgress() * 0.5f;
    }

    private void onVerify() {
        final String ssid = ssidInput.getText().toString().trim();
        final String bssid = bssidInput.getText().toString().trim();

        if (ssid.isEmpty()) {
            setDot("gray");
            setStatus("أدخل اسم الشبكة للتحقق.");
            return;
        }

        setDot("gray");
        verifyBtn.setEnabled(false);
        setStatus("جارٍ التحقق...");

        executor.execute(() -> {
            final String result = scanner.verify(ssid, bssid);
            ui.post(() -> {
                verifyBtn.setEnabled(true);
                setDot(result);
                switch (result) {
                    case "green":
                        setStatus("✓ الشبكة موجودة والمطابقة صحيحة.");
                        break;
                    case "red":
                        setStatus("✗ الشبكة غير موجودة أو BSSID غير مطابق.");
                        break;
                    default:
                        setStatus("⚠ فشل الفحص (أذونات / تحديد المعدل).");
                        break;
                }
            });
        });
    }

    private void setDot(String status) {
        int color;
        switch (status) {
            case "green": color = Color.rgb(51, 255, 77); break;
            case "red":   color = Color.rgb(255, 64, 64); break;
            default:      color = Color.rgb(128, 128, 128); break;
        }
        verifyDot.setTextColor(color);
    }

    private void onLoad() {
        String path = pathInput.getText().toString().trim();
        if (path.isEmpty()) path = "/storage/emulated/0/hako.txt";

        File file = new File(path);
        if (!file.exists()) {
            setStatus("الملف غير موجود: " + path);
            log("لم يتم العثور على الملف.");
            return;
        }

        try {
            List<String> lines = new ArrayList<>();
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(new java.io.FileInputStream(file)));
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed);
                }
            }
            br.close();

            if (lines.isEmpty()) {
                setStatus("الملف فارغ أو يحتوي تعليقات فقط.");
                return;
            }

            passwords = lines;
            setStatus("تم تحميل " + lines.size() + " كلمة مرور.");
            log("تم تحميل " + lines.size() + " كلمة من: " + path);
        } catch (Exception e) {
            setStatus("خطأ في القراءة: " + e.getMessage());
        }
    }

    private void onBrowse() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, FILE_REQ);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != FILE_REQ) return;

        if (resultCode != RESULT_OK || data == null) {
            setStatus("تم إلغاء اختيار الملف.");
            return;
        }

        final Uri uri = data.getData();
        if (uri == null) {
            setStatus("خطأ: URI فارغ.");
            return;
        }

        setStatus("جارٍ قراءة الملف...");

        executor.execute(() -> {
            try {
                List<String> lines = new ArrayList<>();
                InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String line;
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            lines.add(trimmed);
                        }
                    }
                    br.close();
                }

                final List<String> result = lines;
                ui.post(() -> {
                    if (result.isEmpty()) {
                        setStatus("الملف فارغ أو يحتوي تعليقات فقط.");
                        return;
                    }
                    passwords = result;
                    setStatus("تم تحميل " + result.size() + " كلمة مرور.");
                    log("تم التحميل من: " + uri);
                });
            } catch (Exception e) {
                ui.post(() -> setStatus("خطأ في القراءة: " + e.getMessage()));
            }
        });
    }

    private void onStartStop() {
        if (running) {
            running = false;
            setStatus("جارٍ الإيقاف...");
            log("تم طلب الإيقاف بواسطة المستخدم.");
            return;
        }

        final String ssid = ssidInput.getText().toString().trim();
        if (ssid.isEmpty()) {
            setStatus("أدخل اسم الشبكة أولاً.");
            return;
        }
        if (passwords.isEmpty()) {
            setStatus("حمّل ملف كلمات المرور أولاً.");
            return;
        }

        final String bssid = bssidInput.getText().toString().trim();
        final long speed = (long) getSpeedSeconds();

        running = true;
        startTime = System.currentTimeMillis();
        startBtn.setText("إيقاف");
        startBtn.setBackgroundColor(Color.rgb(217, 77, 77));
        setStatus("جارٍ فحص '" + ssid + "'...");
        log("بدء فحص الشبكة: " + ssid);

        executor.execute(() -> {
            String status = scanner.verify(ssid, bssid);
            ui.post(() -> {
                if ("green".equals(status)) {
                    log("تم العثور على الشبكة '" + ssid + "' في النطاق.");
                } else if ("red".equals(status)) {
                    log("'" + ssid + "' غير موجودة — محاولة على أي حال.");
                } else {
                    log("فشل الفحص — محاولة على أي حال.");
                }
                executor.execute(() -> worker(ssid, bssid, speed));
            });
        });
    }

    private void worker(String ssid, String bssid, long speed) {
        final int total = passwords.size();
        String success = null;

        for (int i = 0; i < total; i++) {
            if (!running) break;

            final String pwd = passwords.get(i);
            final int idx = i + 1;

            ui.post(() -> setStatus("[" + idx + "/" + total + "] محاولة: '" + pwd + "'"));

            boolean ok = connector.tryConnect(ssid, bssid, pwd, speed);

            if (ok) {
                success = pwd;
                break;
            }

            ui.post(() -> log("[" + idx + "/" + total + "] فشل: '" + pwd + "'"));

            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }

        final long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        final String finalSuccess = success;

        ui.post(() -> {
            running = false;
            startBtn.setText("ابدأ");
            startBtn.setBackgroundColor(Color.rgb(51, 179, 77));

            if (finalSuccess != null) {
                lastResult = finalSuccess;
                boolean saved = saveFound(ssid, finalSuccess);
                copyToClipboard(finalSuccess);
                setStatus("تم الاتصال بنجاح\n"
                        + "كلمة المرور: " + finalSuccess + "\n"
                        + "الوقت: " + elapsed + " ثانية | "
                        + "حُفظت: " + (saved ? "نعم" : "لا"));
                log("نجاح! كلمة المرور = '" + finalSuccess + "'");
            } else {
                setStatus("فشلت كل الكلمات (" + total + ").\n"
                        + "(" + elapsed + " ثانية)");
                log("انتهى: فشلت كل (" + total + ").");
            }
        });
    }

    private boolean saveFound(String ssid, String pwd) {
        try {
            File dir = new File(getExternalFilesDir(null), "WiFiAutoConnect");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "found_password.txt");
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date());
            String line = "[" + ts + "] SSID=" + ssid + " | Password=" + pwd + "\n";

            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write(line.getBytes("UTF-8"));
            fos.close();
            Log.i(TAG, "Saved to: " + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "saveFound failed: " + e.getMessage());
            return false;
        }
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("wifi_pwd", text));
            }
        } catch (Exception ignored) {}
    }

    private void onCopy() {
        String text = !lastResult.isEmpty() ? lastResult : statusLabel.getText().toString();
        copyToClipboard(text);
        setStatus("تم النسخ إلى الحافظة.");
    }

    private void onShowFound() {
        try {
            File dir = new File(getExternalFilesDir(null), "WiFiAutoConnect");
            File file = new File(dir, "found_password.txt");
            if (file.exists()) {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(new java.io.FileInputStream(file)));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                br.close();

                appendLog("\n── النتائج المحفوظة ──\n" + sb.toString());
                setStatus("الملف: " + file.getAbsolutePath());
            } else {
                setStatus("لا توجد نتائج محفوظة بعد.");
            }
        } catch (Exception e) {
            setStatus("خطأ في القراءة: " + e.getMessage());
        }
    }

    private void onReset() {
        if (running) {
            running = false;
            ui.postDelayed(this::doReset, 400);
        } else {
            doReset();
        }
    }

    private void doReset() {
        ssidInput.setText("");
        bssidInput.setText("");
        pathInput.setText("");
        passwords = new ArrayList<>();
        lastResult = "";
        setDot("gray");
        logBox.setText("");
        setStatus("جاهز.");
        speedSlider.setProgress(9);
        startBtn.setText("ابدأ");
        startBtn.setBackgroundColor(Color.rgb(51, 179, 77));
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void log(String text) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        appendLog("[" + ts + "] " + text + "\n");
    }

    private void appendLog(String text) {
        logBox.append(text);
        CharSequence cs = logBox.getText();
        String s = cs.toString();
        if (s.length() > 8000) {
            logBox.setText(s.substring(s.length() - 6000));
        }
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
        executor.shutdownNow();
    }
                          }
