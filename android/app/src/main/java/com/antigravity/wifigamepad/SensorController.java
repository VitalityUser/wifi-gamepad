package com.antigravity.wifigamepad;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class SensorController implements SensorEventListener {

    public enum GyroMode {
        OFF,
        RACING_STEER,   // Steers Left Stick X (Driving cars)
        FPS_AIM         // Steers Right Stick X & Y (Camera micro-aiming)
    }

    public interface GyroListener {
        void onGyroUpdate(GyroMode mode, float x, float y);
    }

    private final SensorManager sensorManager;
    private final Sensor accelSensor;
    private final Sensor gyroSensor;
    private GyroListener listener;
    private GyroMode currentMode = GyroMode.OFF;

    private static final float STEER_DEADZONE = 0.4f;
    private static final float STEER_MAX = 5.5f;

    public SensorController(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        } else {
            gyroSensor = null;
            accelSensor = null;
        }
    }

    public void setListener(GyroListener listener) {
        this.listener = listener;
    }

    public void setMode(GyroMode mode) {
        if (this.currentMode == mode) return;
        this.currentMode = mode;

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        if (mode != GyroMode.OFF && sensorManager != null) {
            Sensor targetSensor = (mode == GyroMode.FPS_AIM && gyroSensor != null) ? gyroSensor : accelSensor;
            if (targetSensor != null) {
                sensorManager.registerListener(this, targetSensor, SensorManager.SENSOR_DELAY_GAME);
            }
        }

        if (listener != null) {
            listener.onGyroUpdate(mode, 0.0f, 0.0f);
        }
    }

    public GyroMode getMode() {
        return currentMode;
    }

    public GyroMode cycleMode() {
        GyroMode next;
        switch (currentMode) {
            case OFF:
                next = GyroMode.RACING_STEER;
                break;
            case RACING_STEER:
                next = GyroMode.FPS_AIM;
                break;
            case FPS_AIM:
            default:
                next = GyroMode.OFF;
                break;
        }
        setMode(next);
        return next;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (currentMode == GyroMode.OFF || listener == null) return;

        if (currentMode == GyroMode.RACING_STEER) {
            // Landscape accelerometer tilt
            float tilt = event.values[1];
            if (Math.abs(tilt) < STEER_DEADZONE) {
                tilt = 0.0f;
            } else {
                if (tilt > 0) {
                    tilt = (tilt - STEER_DEADZONE) / (STEER_MAX - STEER_DEADZONE);
                } else {
                    tilt = (tilt + STEER_DEADZONE) / (STEER_MAX - STEER_DEADZONE);
                }
            }
            float steerX = Math.max(-1.0f, Math.min(1.0f, -tilt));
            listener.onGyroUpdate(GyroMode.RACING_STEER, steerX, 0.0f);

        } else if (currentMode == GyroMode.FPS_AIM) {
            // Angular velocity for camera micro-aim
            // In landscape: Z is yaw (left/right aim), X is pitch (up/down aim)
            float yawSpeed = (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) ? -event.values[2] : -event.values[1];
            float pitchSpeed = (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) ? -event.values[0] : event.values[0];

            float aimX = Math.max(-1.0f, Math.min(1.0f, yawSpeed * 0.45f));
            float aimY = Math.max(-1.0f, Math.min(1.0f, pitchSpeed * 0.45f));
            listener.onGyroUpdate(GyroMode.FPS_AIM, aimX, aimY);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
