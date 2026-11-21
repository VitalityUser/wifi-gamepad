package com.antigravity.wifigamepad;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class VibrationManager {

    public interface RumbleListener {
        void onRumbleStateChanged(int largeMotor, int smallMotor, boolean isRumbling);
    }

    private final Vibrator vibrator;
    private final boolean hasAmplitude;
    private boolean hapticTouchEnabled = true;
    private boolean gameRumbleEnabled = true;
    private float rumbleScale = 1.0f; // 0.25 to 1.0
    private RumbleListener rumbleListener;

    private int currentLarge = 0;
    private int currentSmall = 0;
    private long lastRumbleTime = 0;

    public VibrationManager(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            this.vibrator = vm != null ? vm.getDefaultVibrator() : (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } else {
            this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (this.vibrator != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.hasAmplitude = this.vibrator.hasAmplitudeControl();
        } else {
            this.hasAmplitude = false;
        }
    }

    public void setRumbleListener(RumbleListener listener) {
        this.rumbleListener = listener;
    }

    public void setHapticTouchEnabled(boolean enabled) {
        this.hapticTouchEnabled = enabled;
    }

    public boolean isHapticTouchEnabled() {
        return hapticTouchEnabled;
    }

    public void setGameRumbleEnabled(boolean enabled) {
        this.gameRumbleEnabled = enabled;
        if (!enabled) {
            stopRumble();
        }
    }

    public boolean isGameRumbleEnabled() {
        return gameRumbleEnabled;
    }

    public void setRumbleScale(float scale) {
        this.rumbleScale = Math.max(0.1f, Math.min(1.0f, scale));
    }

    public float getRumbleScale() {
        return rumbleScale;
    }

    /**
     * Tactile click on button press
     */
    public void triggerButtonHaptic() {
        triggerDynamicHaptic(0.5f);
    }

    /**
     * Pressure-sensitive dynamic haptic feedback:
     * Light press delivers subtle crisp tick; hard press delivers punchy haptic kick!
     * @param pressure 0.0f (feather touch) to 1.0f (firm press)
     */
    public void triggerDynamicHaptic(float pressure) {
        if (!hapticTouchEnabled || vibrator == null) return;

        float clampedP = Math.max(0.1f, Math.min(1.0f, pressure));
        int amplitude = (int) (70 + clampedP * 185); // 70 to 255
        int duration = (int) (14 + clampedP * 24);   // 14ms to 38ms

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (hasAmplitude) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                vibrator.vibrate(duration);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Tactile trigger pull / bumper click
     */
    public void triggerHeavyHaptic() {
        triggerDynamicHaptic(1.0f);
    }

    /**
     * Real-time game force feedback rumble from PC
     * @param largeMotor Heavy bass rumble (0 - 255)
     * @param smallMotor Light treble rumble (0 - 255)
     */
    public synchronized void onGameRumble(int largeMotor, int smallMotor) {
        this.currentLarge = largeMotor;
        this.currentSmall = smallMotor;

        boolean active = (largeMotor > 0 || smallMotor > 0);
        if (rumbleListener != null) {
            rumbleListener.onRumbleStateChanged(largeMotor, smallMotor, active);
        }

        if (!gameRumbleEnabled || vibrator == null) return;

        if (!active) {
            stopRumble();
            return;
        }

        // Throttle rapid packet repeats within 35ms to avoid queue saturation
        long now = System.currentTimeMillis();
        if (now - lastRumbleTime < 35) {
            return;
        }
        lastRumbleTime = now;

        // Combine motors weighted: largeMotor has strong heavy shake, smallMotor has fast buzz
        int maxVal = Math.max(largeMotor, smallMotor);
        int targetAmplitude = (int) (maxVal * rumbleScale);
        targetAmplitude = Math.max(1, Math.min(255, targetAmplitude));

        int durationMs = (largeMotor > 100) ? 90 : 60;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (hasAmplitude) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, targetAmplitude));
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                vibrator.vibrate(durationMs);
            }
        } catch (Exception ignored) {}
    }

    public synchronized void stopRumble() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {}
        }
        if (rumbleListener != null) {
            rumbleListener.onRumbleStateChanged(0, 0, false);
        }
    }

    public void testVibration() {
        if (vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                long[] timings = new long[]{0, 100, 80, 200};
                int[] amplitudes = new int[]{0, 150, 0, 255};
                if (hasAmplitude) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                vibrator.vibrate(350);
            }
        } catch (Exception ignored) {}
    }
}
