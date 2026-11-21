package com.antigravity.wifigamepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "WiFiGamepadPrefs";
    private static final String PREF_IP = "last_ip";
    private static final String PREF_PORT = "last_port";
    private static final String PREF_HAPTIC = "haptic_enabled";
    private static final String PREF_RUMBLE = "rumble_enabled";
    private static final String PREF_THEME = "current_theme";

    private GamepadView gamepadView;
    private NetworkClient networkClient;
    private VibrationManager vibrationManager;
    private SensorController sensorController;
    private SharedPreferences prefs;

    private String discoveredIp = "";
    private int discoveredPort = NetworkClient.DEFAULT_PORT;
    private String discoveredName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen awake
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Immersive sticky fullscreen
        hideSystemUI();

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        vibrationManager = new VibrationManager(this);
        vibrationManager.setHapticTouchEnabled(prefs.getBoolean(PREF_HAPTIC, true));
        vibrationManager.setGameRumbleEnabled(prefs.getBoolean(PREF_RUMBLE, true));

        networkClient = new NetworkClient(vibrationManager);
        sensorController = new SensorController(this);

        gamepadView = new GamepadView(this);
        gamepadView.setManagers(networkClient.getState(), networkClient, vibrationManager);

        // Restore saved theme
        String savedThemeName = prefs.getString(PREF_THEME, GamepadView.Theme.SPIDER.name());
        try {
            gamepadView.setTheme(GamepadView.Theme.valueOf(savedThemeName));
        } catch (Exception e) {
            gamepadView.setTheme(GamepadView.Theme.SPIDER);
        }

        setContentView(gamepadView);

        // Setup network listener
        networkClient.setListener(new NetworkClient.ConnectionListener() {
            @Override
            public void onConnectionStateChanged(boolean connected, String message) {
                gamepadView.updateStatus(message, -1);
            }

            @Override
            public void onPingUpdated(int pingMs) {
                String ip = networkClient.getServerIp();
                gamepadView.updateStatus("Connected: " + ip, pingMs);
            }

            @Override
            public void onServerDiscovered(String ip, int port, String pcName) {
                discoveredIp = ip;
                discoveredPort = port;
                discoveredName = pcName;

                // If not connected, auto connect or notify
                if (!networkClient.isConnected()) {
                    Toast.makeText(MainActivity.this, "Found PC: " + pcName + " (" + ip + ")", Toast.LENGTH_SHORT).show();
                    // If no IP saved, automatically connect
                    String savedIp = prefs.getString(PREF_IP, "");
                    if (savedIp.isEmpty() || savedIp.equals(ip)) {
                        connectTo(ip, port);
                    }
                }
            }
        });

        // Gyro listener: handles both Racing Steer (Left Stick X) and FPS Aim (Right Stick X/Y)
        sensorController.setListener((mode, x, y) -> {
            GamepadState state = networkClient.getState();
            synchronized (state) {
                if (mode == SensorController.GyroMode.RACING_STEER) {
                    state.leftStickX = (short) (x * 32767f);
                } else if (mode == SensorController.GyroMode.FPS_AIM) {
                    state.rightStickX = (short) (x * 32767f);
                    state.rightStickY = (short) (y * 32767f);
                }
            }
            networkClient.markDirty();
        });

        // Setup settings, gyro cycle, turbo, and trackpad toggle click listeners
        gamepadView.setOnSettingsClickListener(new GamepadView.OnSettingsClickListener() {
            @Override
            public void onSettingsClicked() {
                showSettingsDialog();
            }

            @Override
            public void onGyroCycle() {
                SensorController.GyroMode mode = sensorController.cycleMode();
                gamepadView.setGyroMode(mode);
                String msg;
                switch (mode) {
                    case RACING_STEER:
                        msg = "🏎️ Gyro Steering: ON (Tilt phone left/right to steer)";
                        break;
                    case FPS_AIM:
                        msg = "🎯 Gyro Aiming: ON (Tilt phone to aim crosshair)";
                        break;
                    case OFF:
                    default:
                        msg = "⚪ Gyro Motion: OFF";
                        break;
                }
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTurboToggled(boolean enabled) {
                String msg = enabled ? "⚡ Rapid-Fire Turbo: ACTIVE (18 Hz Auto-Click)" : "Rapid-Fire Turbo: OFF";
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTrackpadToggled(boolean enabled) {
                String msg = enabled ? "🖱️ Touchpad Mouse Mode: ACTIVE" : "Gamepad Mode: ACTIVE";
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        // Start auto discovery
        networkClient.startAutoDiscovery();

        // Auto-reconnect to last known IP if available
        String lastIp = prefs.getString(PREF_IP, "");
        int lastPort = prefs.getInt(PREF_PORT, NetworkClient.DEFAULT_PORT);
        if (!lastIp.isEmpty()) {
            connectTo(lastIp, lastPort);
        } else {
            // Prompt settings if first run
            gamepadView.postDelayed(this::showSettingsDialog, 600);
        }
    }

    private void connectTo(String ip, int port) {
        prefs.edit().putString(PREF_IP, ip).putInt(PREF_PORT, port).apply();
        networkClient.connect(ip, port);
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙️ Controller & Connection Settings");

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        // Discovered PC Section
        if (!discoveredIp.isEmpty()) {
            TextView autoText = new TextView(this);
            autoText.setText("💡 Auto-Discovered PC on Wi-Fi:\n" + discoveredName + " (" + discoveredIp + ":" + discoveredPort + ")");
            autoText.setTextColor(Color.parseColor("#FF1744"));
            autoText.setTextSize(14f);
            layout.addView(autoText);

            Button autoConnectBtn = new Button(this);
            autoConnectBtn.setText("⚡ Connect to " + discoveredName);
            autoConnectBtn.setOnClickListener(v -> {
                connectTo(discoveredIp, discoveredPort);
                Toast.makeText(this, "Connecting to " + discoveredName + "...", Toast.LENGTH_SHORT).show();
            });
            layout.addView(autoConnectBtn);

            View div = new View(this);
            div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
            div.setBackgroundColor(Color.DKGRAY);
            layout.addView(div);
        }

        // Manual IP input
        TextView ipLabel = new TextView(this);
        ipLabel.setText("PC IP Address (shown in PC Server window):");
        layout.addView(ipLabel);

        final EditText ipInput = new EditText(this);
        ipInput.setHint("e.g. 192.168.1.100");
        String currentIp = prefs.getString(PREF_IP, discoveredIp);
        ipInput.setText(currentIp);
        layout.addView(ipInput);

        TextView portLabel = new TextView(this);
        portLabel.setText("Port (Default: 8888):");
        layout.addView(portLabel);

        final EditText portInput = new EditText(this);
        portInput.setText(String.valueOf(prefs.getInt(PREF_PORT, NetworkClient.DEFAULT_PORT)));
        layout.addView(portInput);

        // Theme Selection Section
        TextView themeHeader = new TextView(this);
        themeHeader.setText("\n🎨 Controller Visual Themes:");
        themeHeader.setTextColor(Color.parseColor("#FF1744"));
        themeHeader.setTextSize(15f);
        layout.addView(themeHeader);

        String[] themes = {
            "🕷️ Spider Edition (Crimson & Symbiote Black)",
            "🎮 DualSense Ice (Clean White & Cyan)",
            "💚 Xbox Velocity (Neon Green & Carbon)",
            "🌆 Cyberpunk 2077 (High-Volt Yellow & Magenta)"
        };
        final GamepadView.Theme[] themeValues = {
            GamepadView.Theme.SPIDER,
            GamepadView.Theme.DUALSENSE,
            GamepadView.Theme.VELOCITY,
            GamepadView.Theme.CYBERPUNK
        };

        for (int i = 0; i < themes.length; i++) {
            final GamepadView.Theme t = themeValues[i];
            Button themeBtn = new Button(this);
            themeBtn.setText(themes[i]);
            themeBtn.setOnClickListener(v -> {
                gamepadView.setTheme(t);
                prefs.edit().putString(PREF_THEME, t.name()).apply();
                Toast.makeText(this, "Theme switched to " + t.name(), Toast.LENGTH_SHORT).show();
            });
            layout.addView(themeBtn);
        }

        // Vibration options
        TextView vibHeader = new TextView(this);
        vibHeader.setText("\n📳 Venom Force Feedback & Haptics:");
        vibHeader.setTextColor(Color.parseColor("#FF1744"));
        vibHeader.setTextSize(15f);
        layout.addView(vibHeader);

        final CheckBox cbRumble = new CheckBox(this);
        cbRumble.setText("Game Force Feedback Rumble (PC Game Vibration)");
        cbRumble.setChecked(vibrationManager.isGameRumbleEnabled());
        cbRumble.setOnCheckedChangeListener((btn, isChecked) -> {
            vibrationManager.setGameRumbleEnabled(isChecked);
            prefs.edit().putBoolean(PREF_RUMBLE, isChecked).apply();
        });
        layout.addView(cbRumble);

        final CheckBox cbHaptic = new CheckBox(this);
        cbHaptic.setText("Button Touch Haptic Feedback (Clicks on Press)");
        cbHaptic.setChecked(vibrationManager.isHapticTouchEnabled());
        cbHaptic.setOnCheckedChangeListener((btn, isChecked) -> {
            vibrationManager.setHapticTouchEnabled(isChecked);
            prefs.edit().putBoolean(PREF_HAPTIC, isChecked).apply();
        });
        layout.addView(cbHaptic);

        Button testVibBtn = new Button(this);
        testVibBtn.setText("Test Dual Vibration Actuators");
        testVibBtn.setOnClickListener(v -> vibrationManager.testVibration());
        layout.addView(testVibBtn);

        scroll.addView(layout);
        builder.setView(scroll);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            String ip = ipInput.getText().toString().trim();
            int port = NetworkClient.DEFAULT_PORT;
            try {
                port = Integer.parseInt(portInput.getText().toString().trim());
            } catch (Exception ignored) {}

            if (!ip.isEmpty()) {
                connectTo(ip, port);
            }
            hideSystemUI();
        });

        builder.setNeutralButton("Disconnect", (dialog, which) -> {
            networkClient.disconnect();
            hideSystemUI();
        });

        builder.setNegativeButton("Close", (dialog, which) -> hideSystemUI());

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> hideSystemUI());
        dialog.show();
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sensorController != null) {
            sensorController.setMode(SensorController.GyroMode.OFF);
        }
        if (networkClient != null) {
            networkClient.stopAutoDiscovery();
            networkClient.disconnect();
        }
    }
}
