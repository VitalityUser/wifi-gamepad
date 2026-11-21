package com.antigravity.wifigamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

public class GamepadView extends View {

    public enum Theme {
        SPIDER,       // Symbiote Black & Spider Crimson
        DUALSENSE,    // DualSense Ice White & Cyber Cyan
        VELOCITY,     // Xbox Velocity Green & Carbon
        CYBERPUNK     // Cyberpunk Neon Yellow & Hot Pink
    }

    public interface OnSettingsClickListener {
        void onSettingsClicked();
        void onGyroCycle();
        void onTurboToggled(boolean enabled);
        void onTrackpadToggled(boolean enabled);
    }

    private GamepadState state;
    private NetworkClient networkClient;
    private VibrationManager vibrationManager;
    private OnSettingsClickListener settingsClickListener;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tempRect = new RectF();

    // Multi-touch tracking
    private static final int TARGET_NONE = 0;
    private static final int TARGET_LEFT_STICK = 1;
    private static final int TARGET_RIGHT_STICK = 2;
    private static final int TARGET_DPAD = 3;
    private static final int TARGET_ACTIONS = 4;
    private static final int TARGET_LB = 5;
    private static final int TARGET_RB = 6;
    private static final int TARGET_LT = 7;
    private static final int TARGET_RT = 8;
    private static final int TARGET_BACK = 9;
    private static final int TARGET_START = 10;
    private static final int TARGET_GUIDE = 11;
    private static final int TARGET_L3 = 12;
    private static final int TARGET_R3 = 13;
    private static final int TARGET_SETTINGS = 14;
    private static final int TARGET_GYRO = 15;
    private static final int TARGET_TURBO = 16;
    private static final int TARGET_TRACKPAD_TOGGLE = 17;
    private static final int TARGET_TRACKPAD_ZONE = 18;

    private static class TouchInfo {
        int target;
        float startX, startY;
        float currentX, currentY;
    }

    private final Map<Integer, TouchInfo> activePointers = new HashMap<>();

    // Layout coordinates
    private float width, height;

    // Left Stick
    private float leftStickCenterX, leftStickCenterY, stickRadius, stickKnobRadius;
    private float leftStickKnobX, leftStickKnobY;

    // Right Stick
    private float rightStickCenterX, rightStickCenterY;
    private float rightStickKnobX, rightStickKnobY;

    // D-Pad
    private float dpadCenterX, dpadCenterY, dpadArmLength, dpadArmWidth;
    private final RectF dpadUpRect = new RectF();
    private final RectF dpadDownRect = new RectF();
    private final RectF dpadLeftRect = new RectF();
    private final RectF dpadRightRect = new RectF();

    // Action Buttons
    private float actionCenterX, actionCenterY, actionSpacing, buttonRadius;
    private float btnAX, btnAY, btnBX, btnBY, btnXX, btnXY, btnYX, btnYY;

    // Shoulders
    private final RectF lbRect = new RectF();
    private final RectF rbRect = new RectF();
    private final RectF ltRect = new RectF();
    private final RectF rtRect = new RectF();

    // Center buttons
    private final RectF backRect = new RectF();
    private final RectF startRect = new RectF();
    private final RectF guideRect = new RectF();
    private final RectF l3Rect = new RectF();
    private final RectF r3Rect = new RectF();

    // Quick toggles on top
    private final RectF settingsRect = new RectF();
    private final RectF gyroRect = new RectF();
    private final RectF turboRect = new RectF();
    private final RectF trackpadToggleRect = new RectF();
    private final RectF trackpadZoneRect = new RectF();

    // Status display & modes
    private String statusText = "Disconnected";
    private int pingMs = -1;
    private boolean isRumbling = false;

    private Theme currentTheme = Theme.SPIDER;
    private SensorController.GyroMode gyroMode = SensorController.GyroMode.OFF;
    private boolean turboEnabled = false;
    private boolean trackpadMode = false;
    private long lastTurboTick = 0;
    private boolean turboPulse = false;

    private float trackpadPrevX = 0, trackpadPrevY = 0;
    private long trackpadDownTime = 0;
    private float mouseSensitivity = 1.25f; // Smooth, calibrated trackpad sensitivity

    public GamepadView(Context context) {
        super(context);
        init();
    }

    public GamepadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setTheme(Theme theme) {
        this.currentTheme = theme;
        postInvalidate();
    }

    public Theme getTheme() {
        return currentTheme;
    }

    public void setGyroMode(SensorController.GyroMode mode) {
        this.gyroMode = mode;
        postInvalidate();
    }

    public void setTurboEnabled(boolean enabled) {
        this.turboEnabled = enabled;
        postInvalidate();
    }

    public boolean isTurboEnabled() {
        return turboEnabled;
    }

    public void setTrackpadMode(boolean enabled) {
        this.trackpadMode = enabled;
        postInvalidate();
    }

    public boolean isTrackpadMode() {
        return trackpadMode;
    }

    public void setMouseSensitivity(float sensitivity) {
        this.mouseSensitivity = Math.max(0.2f, Math.min(5.0f, sensitivity));
    }

    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    public void setManagers(GamepadState state, NetworkClient client, VibrationManager vibrationManager) {
        this.state = state;
        this.networkClient = client;
        this.vibrationManager = vibrationManager;

        if (vibrationManager != null) {
            vibrationManager.setRumbleListener((large, small, active) -> {
                this.isRumbling = active;
                postInvalidate();
            });
        }
    }

    public void setOnSettingsClickListener(OnSettingsClickListener listener) {
        this.settingsClickListener = listener;
    }

    public void updateStatus(String status, int ping) {
        this.statusText = status;
        this.pingMs = ping;
        postInvalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.width = w;
        this.height = h;

        float unit = Math.min(w / 18f, h / 10f);

        // Shoulders
        float shoulderW = unit * 3.4f;
        float shoulderH = unit * 1.3f;
        float shoulderMargin = unit * 0.4f;

        ltRect.set(shoulderMargin, shoulderMargin, shoulderMargin + shoulderW, shoulderMargin + shoulderH);
        lbRect.set(shoulderMargin, shoulderMargin + shoulderH + unit * 0.2f, shoulderMargin + shoulderW, shoulderMargin + shoulderH * 2 + unit * 0.2f);

        rtRect.set(w - shoulderMargin - shoulderW, shoulderMargin, w - shoulderMargin, shoulderMargin + shoulderH);
        rbRect.set(w - shoulderMargin - shoulderW, shoulderMargin + shoulderH + unit * 0.2f, w - shoulderMargin, shoulderMargin + shoulderH * 2 + unit * 0.2f);

        // Left Analog Stick
        stickRadius = unit * 2.2f;
        stickKnobRadius = unit * 1.1f;
        leftStickCenterX = unit * 3.6f;
        leftStickCenterY = h * 0.63f;
        leftStickKnobX = leftStickCenterX;
        leftStickKnobY = leftStickCenterY;

        // D-Pad
        dpadCenterX = unit * 7.5f;
        dpadCenterY = h * 0.72f;
        dpadArmLength = unit * 1.35f;
        dpadArmWidth = unit * 1.0f;

        dpadUpRect.set(dpadCenterX - dpadArmWidth / 2f, dpadCenterY - dpadArmLength, dpadCenterX + dpadArmWidth / 2f, dpadCenterY);
        dpadDownRect.set(dpadCenterX - dpadArmWidth / 2f, dpadCenterY, dpadCenterX + dpadArmWidth / 2f, dpadCenterY + dpadArmLength);
        dpadLeftRect.set(dpadCenterX - dpadArmLength, dpadCenterY - dpadArmWidth / 2f, dpadCenterX, dpadCenterY + dpadArmWidth / 2f);
        dpadRightRect.set(dpadCenterX, dpadCenterY - dpadArmWidth / 2f, dpadCenterX + dpadArmLength, dpadCenterY + dpadArmWidth / 2f);

        // Action Buttons Diamond
        actionCenterX = w - unit * 3.6f;
        actionCenterY = h * 0.48f;
        actionSpacing = unit * 1.7f;
        buttonRadius = unit * 0.95f;

        btnYX = actionCenterX;
        btnYY = actionCenterY - actionSpacing;
        btnAX = actionCenterX;
        btnAY = actionCenterY + actionSpacing;
        btnXX = actionCenterX - actionSpacing;
        btnXY = actionCenterY;
        btnBX = actionCenterX + actionSpacing;
        btnBY = actionCenterY;

        // Right Analog Stick
        rightStickCenterX = w - unit * 7.5f;
        rightStickCenterY = h * 0.72f;
        rightStickKnobX = rightStickCenterX;
        rightStickKnobY = rightStickCenterY;

        // L3 / R3 badges
        float stickBtnW = unit * 1.4f;
        float stickBtnH = unit * 0.8f;
        l3Rect.set(leftStickCenterX - stickBtnW / 2, leftStickCenterY + stickRadius + unit * 0.25f, leftStickCenterX + stickBtnW / 2, leftStickCenterY + stickRadius + stickBtnH + unit * 0.25f);
        r3Rect.set(rightStickCenterX - stickBtnW / 2, rightStickCenterY + stickRadius + unit * 0.25f, rightStickCenterX + stickBtnW / 2, rightStickCenterY + stickRadius + stickBtnH + unit * 0.25f);

        // Center Buttons: Back, Guide, Start
        float centerW = unit * 1.8f;
        float centerH = unit * 0.95f;
        float centerSpacing = unit * 0.3f;
        float centerTop = h * 0.45f;

        guideRect.set(w / 2 - centerW / 2, centerTop - unit * 0.3f, w / 2 + centerW / 2, centerTop + centerH + unit * 0.3f);
        backRect.set(guideRect.left - centerSpacing - centerW, centerTop, guideRect.left - centerSpacing, centerTop + centerH);
        startRect.set(guideRect.right + centerSpacing, centerTop, guideRect.right + centerSpacing + centerW, centerTop + centerH);

        // Quick Toggles on Center-Top: Settings | Gyro | Turbo | Trackpad
        float iconW = unit * 1.15f;
        float iconH = unit * 0.95f;
        float spacing = unit * 0.25f;
        float totalW = iconW * 4 + spacing * 3;
        float startX = w / 2 - totalW / 2;
        float topY = unit * 0.45f;

        settingsRect.set(startX, topY, startX + iconW, topY + iconH);
        gyroRect.set(startX + iconW + spacing, topY, startX + iconW * 2 + spacing, topY + iconH);
        turboRect.set(startX + (iconW + spacing) * 2, topY, startX + iconW * 3 + spacing * 2, topY + iconH);
        trackpadToggleRect.set(startX + (iconW + spacing) * 3, topY, startX + iconW * 4 + spacing * 3, topY + iconH);

        // Trackpad Center Zone (when activated)
        float padW = unit * 6.5f;
        float padH = unit * 4.5f;
        trackpadZoneRect.set(w / 2 - padW / 2, h * 0.40f, w / 2 + padW / 2, h * 0.40f + padH);
    }

    // ----------------- Drawing Routines -----------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Handle Turbo Pulse update
        long now = System.currentTimeMillis();
        if (turboEnabled && now - lastTurboTick > 55) {
            lastTurboTick = now;
            turboPulse = !turboPulse;
        }

        // 1. Theme-Specific Background
        drawThemedBackground(canvas);

        // 2. Status Bar HUD
        drawStatusBar(canvas);

        // 3. Triggers & Bumpers
        drawShoulders(canvas);

        // 4. Center System Buttons (BACK, GUIDE / SPIDER, START)
        if (!trackpadMode) {
            drawCenterButtons(canvas);
        } else {
            drawTrackpadZone(canvas);
        }

        // 5. Directional Pad (D-Pad)
        drawDPad(canvas);

        // 6. Action Buttons (A, B, X, Y)
        drawActionButtons(canvas);

        // 7. Analog Joysticks
        drawJoystick(canvas, leftStickCenterX, leftStickCenterY, leftStickKnobX, leftStickKnobY, true, state != null && state.isButtonPressed(GamepadState.BTN_L3));
        drawJoystick(canvas, rightStickCenterX, rightStickCenterY, rightStickKnobX, rightStickKnobY, false, state != null && state.isButtonPressed(GamepadState.BTN_R3));

        // 8. L3 / R3 badges
        drawButtonBadge(canvas, l3Rect, "L3", state != null && state.isButtonPressed(GamepadState.BTN_L3), getAccentColor());
        drawButtonBadge(canvas, r3Rect, "R3", state != null && state.isButtonPressed(GamepadState.BTN_R3), getAccentColor());

        // 9. Quick Toggles Bar
        drawQuickToggles(canvas);
    }

    private int getAccentColor() {
        switch (currentTheme) {
            case DUALSENSE:
                return Color.parseColor("#00E5FF");
            case VELOCITY:
                return Color.parseColor("#107C10");
            case CYBERPUNK:
                return Color.parseColor("#FCEE0A");
            case SPIDER:
            default:
                return Color.parseColor("#FF1744");
        }
    }

    private void drawThemedBackground(Canvas canvas) {
        switch (currentTheme) {
            case DUALSENSE:
                // Midnight & Ice White
                canvas.drawColor(Color.parseColor("#0A0D14"));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#182030"));
                Path dsPath = new Path();
                dsPath.moveTo(width * 0.50f, 0);
                dsPath.lineTo(width, 0);
                dsPath.lineTo(width, height);
                dsPath.lineTo(width * 0.42f, height);
                dsPath.close();
                canvas.drawPath(dsPath, paint);
                break;

            case VELOCITY:
                // Xbox Carbon Black & Velocity Green
                canvas.drawColor(Color.parseColor("#090B0D"));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#0E1A11"));
                Path velPath = new Path();
                velPath.moveTo(width * 0.52f, 0);
                velPath.lineTo(width, 0);
                velPath.lineTo(width, height);
                velPath.lineTo(width * 0.38f, height);
                velPath.close();
                canvas.drawPath(velPath, paint);
                break;

            case CYBERPUNK:
                // Night City Charcoal & High-Voltage Neon
                canvas.drawColor(Color.parseColor("#0B0B0E"));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#262308"));
                Path cpPath = new Path();
                cpPath.moveTo(width * 0.55f, 0);
                cpPath.lineTo(width, 0);
                cpPath.lineTo(width, height);
                cpPath.lineTo(width * 0.40f, height);
                cpPath.close();
                canvas.drawPath(cpPath, paint);
                break;

            case SPIDER:
            default:
                // Symbiote Black & Spider Crimson
                canvas.drawColor(Color.parseColor("#08090D"));
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#8E0D14"));
                Path crimsonPanel = new Path();
                crimsonPanel.moveTo(width * 0.52f, 0);
                crimsonPanel.lineTo(width, 0);
                crimsonPanel.lineTo(width, height);
                crimsonPanel.lineTo(width * 0.38f, height);
                crimsonPanel.close();
                canvas.drawPath(crimsonPanel, paint);

                // Organic Symbiote tendrils
                paint.setColor(Color.parseColor("#08090D"));
                Path tendril1 = new Path();
                tendril1.moveTo(width * 0.52f, height * 0.12f);
                tendril1.quadTo(width * 0.70f, height * 0.22f, width * 0.58f, height * 0.38f);
                tendril1.quadTo(width * 0.46f, height * 0.28f, width * 0.52f, height * 0.12f);
                canvas.drawPath(tendril1, paint);

                Path tendril2 = new Path();
                tendril2.moveTo(width * 0.44f, height * 0.62f);
                tendril2.quadTo(width * 0.76f, height * 0.75f, width * 0.52f, height * 0.94f);
                tendril2.quadTo(width * 0.38f, height * 0.82f, width * 0.44f, height * 0.62f);
                canvas.drawPath(tendril2, paint);

                // Spiderwebs
                drawSpiderweb(canvas, width * 0.22f, height * 0.62f, height * 0.70f);
                drawSpiderweb(canvas, width * 0.78f, height * 0.50f, height * 0.70f);
                break;
        }

        // Divider Line
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(70, 255, 23, 68));
        paint.setStrokeWidth(2.5f);
        canvas.drawLine(width * 0.52f, 0, width * 0.38f, height, paint);
    }

    private void drawSpiderweb(Canvas canvas, float cx, float cy, float radius) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(35, 255, 23, 68));
        paint.setStrokeWidth(1.4f);

        int numRays = 12;
        float angleStep = (float) (2 * Math.PI / numRays);

        for (int i = 0; i < numRays; i++) {
            float angle = i * angleStep;
            float ex = cx + (float) Math.cos(angle) * radius;
            float ey = cy + (float) Math.sin(angle) * radius;
            canvas.drawLine(cx, cy, ex, ey, paint);
        }

        int rings = 5;
        for (int r = 1; r <= rings; r++) {
            float rRad = radius * (r / (float) rings);
            for (int i = 0; i < numRays; i++) {
                float a1 = i * angleStep;
                float a2 = (i + 1) * angleStep;
                float x1 = cx + (float) Math.cos(a1) * rRad;
                float y1 = cy + (float) Math.sin(a1) * rRad;
                float x2 = cx + (float) Math.cos(a2) * rRad;
                float y2 = cy + (float) Math.sin(a2) * rRad;
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
        }
    }

    private void drawStatusBar(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#12141F"));
        tempRect.set(width * 0.20f, 10, width * 0.80f, 65);
        canvas.drawRoundRect(tempRect, 28, 28, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#4A1520"));
        paint.setStrokeWidth(2f);
        canvas.drawRoundRect(tempRect, 28, 28, paint);

        boolean connected = networkClient != null && networkClient.isConnected();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(connected ? getAccentColor() : Color.parseColor("#555555"));
        canvas.drawCircle(width * 0.23f, 37, 7, paint);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.LEFT);

        String themeTag = (currentTheme == Theme.SPIDER) ? "🕷️ SPIDER ED." : (currentTheme == Theme.DUALSENSE ? "⚪ DUALSENSE" : (currentTheme == Theme.VELOCITY ? "🟢 VELOCITY" : "🟨 CYBERPUNK"));
        String info = (connected ? themeTag + " | " + statusText : themeTag) + (connected && pingMs >= 0 ? " | " + pingMs + "ms" : "");
        canvas.drawText(info, width * 0.25f, 45, textPaint);

        if (isRumbling) {
            paint.setColor(Color.parseColor("#FF3D00"));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(width * 0.77f, 37, 10, paint);
            textPaint.setColor(Color.parseColor("#FF3D00"));
            textPaint.setTextSize(22f);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("RUMBLE", width * 0.75f, 45, textPaint);
        }
    }

    private void drawShoulders(Canvas canvas) {
        float ltVal = (state != null) ? (state.leftTrigger / 255f) : 0f;
        drawTrigger(canvas, ltRect, "LT", ltVal);

        float rtVal = (state != null) ? (state.rightTrigger / 255f) : 0f;
        drawTrigger(canvas, rtRect, "RT", rtVal);

        boolean lbPressed = state != null && state.isButtonPressed(GamepadState.BTN_LB);
        drawBumper(canvas, lbRect, "LB", lbPressed);

        boolean rbPressed = state != null && state.isButtonPressed(GamepadState.BTN_RB);
        drawBumper(canvas, rbRect, "RB", rbPressed);
    }

    private void drawTrigger(Canvas canvas, RectF rect, String label, float value) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#12141D"));
        canvas.drawRoundRect(rect, 18, 18, paint);

        if (value > 0.05f) {
            tempRect.set(rect.left, rect.bottom - (rect.height() * value), rect.right, rect.bottom);
            paint.setColor(getAccentColor());
            canvas.drawRoundRect(tempRect, 18, 18, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(Color.WHITE);
            canvas.drawLine(rect.left + 4, tempRect.top, rect.right - 4, tempRect.top, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(value > 0.1f ? 3.5f : 2f);
        paint.setColor(value > 0.1f ? getAccentColor() : Color.parseColor("#38171E"));
        canvas.drawRoundRect(rect, 18, 18, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(rect.height() * 0.42f);
        textPaint.setColor(value > 0.1f ? Color.WHITE : Color.parseColor("#E0E0E0"));
        canvas.drawText(label + (value > 0.05f ? " " + (int)(value * 100) + "%" : ""), rect.centerX(), rect.centerY() + rect.height() * 0.15f, textPaint);
    }

    private void drawBumper(Canvas canvas, RectF rect, String label, boolean pressed) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? getAccentColor() : Color.parseColor("#181B26"));
        canvas.drawRoundRect(rect, 18, 18, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(pressed ? 3.5f : 2f);
        paint.setColor(pressed ? Color.WHITE : Color.parseColor("#44171E"));
        canvas.drawRoundRect(rect, 18, 18, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(rect.height() * 0.42f);
        textPaint.setColor(pressed ? Color.WHITE : Color.parseColor("#F5F5F5"));
        canvas.drawText(label, rect.centerX(), rect.centerY() + rect.height() * 0.15f, textPaint);
    }

    private void drawDPad(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#0F1118"));
        canvas.drawCircle(dpadCenterX, dpadCenterY, dpadArmWidth * 0.65f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(getAccentColor());
        canvas.drawCircle(dpadCenterX, dpadCenterY, dpadArmWidth * 0.65f, paint);

        boolean up = state != null && state.isButtonPressed(GamepadState.BTN_DPAD_UP);
        boolean down = state != null && state.isButtonPressed(GamepadState.BTN_DPAD_DOWN);
        boolean left = state != null && state.isButtonPressed(GamepadState.BTN_DPAD_LEFT);
        boolean right = state != null && state.isButtonPressed(GamepadState.BTN_DPAD_RIGHT);

        drawDPadArm(canvas, dpadUpRect, "▲", up);
        drawDPadArm(canvas, dpadDownRect, "▼", down);
        drawDPadArm(canvas, dpadLeftRect, "◀", left);
        drawDPadArm(canvas, dpadRightRect, "▶", right);
    }

    private void drawDPadArm(Canvas canvas, RectF rect, String arrow, boolean pressed) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? getAccentColor() : Color.parseColor("#151822"));
        canvas.drawRoundRect(rect, 12, 12, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(pressed ? 3f : 1.5f);
        paint.setColor(pressed ? Color.WHITE : Color.parseColor("#3A171F"));
        canvas.drawRoundRect(rect, 12, 12, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(rect.width() * 0.45f);
        textPaint.setColor(pressed ? Color.WHITE : getAccentColor());
        canvas.drawText(arrow, rect.centerX(), rect.centerY() + rect.height() * 0.16f, textPaint);
    }

    private void drawActionButtons(Canvas canvas) {
        boolean a = state != null && state.isButtonPressed(GamepadState.BTN_A);
        boolean b = state != null && state.isButtonPressed(GamepadState.BTN_B);
        boolean x = state != null && state.isButtonPressed(GamepadState.BTN_X);
        boolean y = state != null && state.isButtonPressed(GamepadState.BTN_Y);

        drawCircleButton(canvas, btnAX, btnAY, buttonRadius, "A", a, Color.parseColor("#00E676"));
        drawCircleButton(canvas, btnBX, btnBY, buttonRadius, "B", b, Color.parseColor("#FF1744"));
        drawCircleButton(canvas, btnXX, btnXY, buttonRadius, "X", x, Color.parseColor("#00B0FF"));
        drawCircleButton(canvas, btnYX, btnYY, buttonRadius, "Y", y, Color.parseColor("#FFD600"));
    }

    private void drawCircleButton(Canvas canvas, float cx, float cy, float r, String label, boolean pressed, int color) {
        if (pressed) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(110, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawCircle(cx, cy, r + 14, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? color : Color.parseColor("#141622"));
        canvas.drawCircle(cx, cy, r, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(pressed ? 4.5f : 2.5f);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, r, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(r * 0.9f);
        textPaint.setColor(pressed ? Color.BLACK : color);
        textPaint.setFakeBoldText(true);
        canvas.drawText(label, cx, cy + r * 0.32f, textPaint);
    }

    private void drawJoystick(Canvas canvas, float cx, float cy, float knobX, float knobY, boolean isLeft, boolean pressed) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#0C0E15"));
        canvas.drawCircle(cx, cy, stickRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(Color.parseColor("#44171E"));
        canvas.drawCircle(cx, cy, stickRadius, paint);

        paint.setColor(Color.argb(40, 255, 23, 68));
        paint.setStrokeWidth(1.5f);
        for (int i = 0; i < 8; i++) {
            float ang = (float) (i * Math.PI / 4);
            float ex = cx + (float) Math.cos(ang) * stickRadius;
            float ey = cy + (float) Math.sin(ang) * stickRadius;
            canvas.drawLine(cx, cy, ex, ey, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? getAccentColor() : Color.parseColor("#161822"));
        canvas.drawCircle(knobX, knobY, stickKnobRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(pressed ? 4f : 2.5f);
        paint.setColor(pressed ? Color.WHITE : getAccentColor());
        canvas.drawCircle(knobX, knobY, stickKnobRadius, paint);

        if (currentTheme == Theme.SPIDER) {
            drawSpiderEye(canvas, knobX, knobY, stickKnobRadius * 0.45f, pressed, isLeft);
        } else {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(stickKnobRadius * 0.7f);
            textPaint.setColor(pressed ? Color.WHITE : getAccentColor());
            textPaint.setFakeBoldText(true);
            canvas.drawText(isLeft ? "L" : "R", knobX, knobY + stickKnobRadius * 0.25f, textPaint);
        }
    }

    private void drawSpiderEye(Canvas canvas, float cx, float cy, float r, boolean pressed, boolean isLeft) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? Color.WHITE : Color.parseColor("#EDEDED"));

        Path eyePath = new Path();
        float dir = isLeft ? -1f : 1f;
        eyePath.moveTo(cx - r * 0.7f * dir, cy + r * 0.15f);
        eyePath.lineTo(cx + r * 0.6f * dir, cy - r * 0.55f);
        eyePath.lineTo(cx + r * 0.25f * dir, cy + r * 0.55f);
        eyePath.close();
        canvas.drawPath(eyePath, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.parseColor("#08090D"));
        canvas.drawPath(eyePath, paint);
    }

    private void drawCenterButtons(Canvas canvas) {
        boolean back = state != null && state.isButtonPressed(GamepadState.BTN_BACK);
        boolean guide = state != null && state.isButtonPressed(GamepadState.BTN_GUIDE);
        boolean start = state != null && state.isButtonPressed(GamepadState.BTN_START);

        drawButtonBadge(canvas, backRect, "BACK", back, getAccentColor());

        if (currentTheme == Theme.SPIDER) {
            drawSpiderInsignia(canvas, guideRect.centerX(), guideRect.centerY(), guideRect.height() * 0.45f, guide);
        } else {
            drawXboxGuideBadge(canvas, guideRect.centerX(), guideRect.centerY(), guideRect.height() * 0.45f, guide);
        }

        drawButtonBadge(canvas, startRect, "START", start, getAccentColor());
    }

    private void drawSpiderInsignia(Canvas canvas, float cx, float cy, float size, boolean pressed) {
        if (pressed) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(120, 255, 23, 68));
            canvas.drawCircle(cx, cy, size * 1.5f, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? Color.parseColor("#FF1744") : Color.parseColor("#12141F"));
        canvas.drawCircle(cx, cy, size * 1.15f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(pressed ? 4f : 2.5f);
        paint.setColor(pressed ? Color.WHITE : Color.parseColor("#FF1744"));
        canvas.drawCircle(cx, cy, size * 1.15f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? Color.BLACK : Color.WHITE);
        canvas.drawCircle(cx, cy - size * 0.22f, size * 0.15f, paint);
        tempRect.set(cx - size * 0.16f, cy - size * 0.08f, cx + size * 0.16f, cy + size * 0.38f);
        canvas.drawRoundRect(tempRect, size * 0.10f, size * 0.10f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2.5f, size * 0.09f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(pressed ? Color.BLACK : Color.WHITE);

        Path leg = new Path();
        leg.moveTo(cx - size * 0.10f, cy - size * 0.10f);
        leg.lineTo(cx - size * 0.42f, cy - size * 0.42f);
        leg.lineTo(cx - size * 0.55f, cy - size * 0.24f);
        canvas.drawPath(leg, paint);

        leg.reset();
        leg.moveTo(cx + size * 0.10f, cy - size * 0.10f);
        leg.lineTo(cx + size * 0.42f, cy - size * 0.42f);
        leg.lineTo(cx + size * 0.55f, cy - size * 0.24f);
        canvas.drawPath(leg, paint);

        leg.reset();
        leg.moveTo(cx - size * 0.12f, cy + size * 0.02f);
        leg.lineTo(cx - size * 0.55f, cy - size * 0.08f);
        leg.lineTo(cx - size * 0.65f, cy + size * 0.12f);
        canvas.drawPath(leg, paint);

        leg.reset();
        leg.moveTo(cx + size * 0.12f, cy + size * 0.02f);
        leg.lineTo(cx + size * 0.55f, cy - size * 0.08f);
        leg.lineTo(cx + size * 0.65f, cy + size * 0.12f);
        canvas.drawPath(leg, paint);

        leg.reset();
        leg.moveTo(cx - size * 0.10f, cy + size * 0.18f);
        leg.lineTo(cx - size * 0.50f, cy + size * 0.30f);
        leg.lineTo(cx - size * 0.42f, cy + size * 0.52f);
        canvas.drawPath(leg, paint);

        leg.reset();
        leg.moveTo(cx + size * 0.10f, cy + size * 0.18f);
        leg.lineTo(cx + size * 0.50f, cy + size * 0.30f);
        leg.lineTo(cx + size * 0.42f, cy + size * 0.52f);
        canvas.drawPath(leg, paint);

        leg.reset();
        leg.moveTo(cx - size * 0.08f, cy + size * 0.28f);
        leg.lineTo(cx - size * 0.32f, cy + size * 0.48f);
        leg.lineTo(cx - size * 0.20f, cy + size * 0.68f);
        canvas.drawPath(leg, paint);

        leg.reset();
        leg.moveTo(cx + size * 0.08f, cy + size * 0.28f);
        leg.lineTo(cx + size * 0.32f, cy + size * 0.48f);
        leg.lineTo(cx + size * 0.20f, cy + size * 0.68f);
        canvas.drawPath(leg, paint);
    }

    private void drawXboxGuideBadge(Canvas canvas, float cx, float cy, float size, boolean pressed) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? getAccentColor() : Color.parseColor("#151824"));
        canvas.drawCircle(cx, cy, size * 1.15f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(pressed ? 4f : 2.5f);
        paint.setColor(getAccentColor());
        canvas.drawCircle(cx, cy, size * 1.15f, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(size * 1.0f);
        textPaint.setColor(pressed ? Color.WHITE : getAccentColor());
        textPaint.setFakeBoldText(true);
        canvas.drawText("X", cx, cy + size * 0.35f, textPaint);
    }

    private void drawTrackpadZone(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(200, 18, 21, 32));
        canvas.drawRoundRect(trackpadZoneRect, 22, 22, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(getAccentColor());
        canvas.drawRoundRect(trackpadZoneRect, 22, 22, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(trackpadZoneRect.height() * 0.16f);
        textPaint.setColor(Color.WHITE);
        textPaint.setFakeBoldText(true);
        canvas.drawText("🖱️ VIRTUAL TRACKPAD", trackpadZoneRect.centerX(), trackpadZoneRect.top + trackpadZoneRect.height() * 0.35f, textPaint);

        textPaint.setTextSize(trackpadZoneRect.height() * 0.11f);
        textPaint.setColor(Color.LTGRAY);
        canvas.drawText("Swipe to Move Mouse  |  Tap to Left Click", trackpadZoneRect.centerX(), trackpadZoneRect.top + trackpadZoneRect.height() * 0.60f, textPaint);
    }

    private void drawButtonBadge(Canvas canvas, RectF rect, String label, boolean pressed, int accentColor) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? accentColor : Color.parseColor("#151824"));
        canvas.drawRoundRect(rect, 14, 14, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(pressed ? 3f : 1.5f);
        paint.setColor(pressed ? Color.WHITE : Color.parseColor("#44171E"));
        canvas.drawRoundRect(rect, 14, 14, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(rect.height() * 0.42f);
        textPaint.setColor(pressed ? Color.WHITE : Color.parseColor("#EDEDED"));
        textPaint.setFakeBoldText(true);
        canvas.drawText(label, rect.centerX(), rect.centerY() + rect.height() * 0.15f, textPaint);
    }

    private void drawQuickToggles(Canvas canvas) {
        // 1. Settings (Gear)
        drawToggleIcon(canvas, settingsRect, "⚙", false, Color.parseColor("#38171E"), getAccentColor());

        // 2. Gyro Toggle (OFF / STEER / AIM)
        String gyroLabel = (gyroMode == SensorController.GyroMode.RACING_STEER) ? "🏎️" : (gyroMode == SensorController.GyroMode.FPS_AIM ? "🎯" : "⚡");
        drawToggleIcon(canvas, gyroRect, gyroLabel, gyroMode != SensorController.GyroMode.OFF, Color.parseColor("#38171E"), getAccentColor());

        // 3. Turbo Toggle
        drawToggleIcon(canvas, turboRect, "🔥", turboEnabled, Color.parseColor("#38171E"), Color.parseColor("#FF6D00"));

        // 4. Trackpad Mode Toggle
        drawToggleIcon(canvas, trackpadToggleRect, "🖱️", trackpadMode, Color.parseColor("#38171E"), Color.parseColor("#00E5FF"));
    }

    private void drawToggleIcon(Canvas canvas, RectF rect, String text, boolean active, int strokeColor, int activeFill) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? activeFill : Color.parseColor("#151824"));
        canvas.drawRoundRect(rect, 14, 14, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(active ? Color.WHITE : strokeColor);
        paint.setStrokeWidth(2f);
        canvas.drawRoundRect(rect, 14, 14, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(rect.height() * 0.50f);
        textPaint.setColor(active ? Color.WHITE : Color.LTGRAY);
        canvas.drawText(text, rect.centerX(), rect.centerY() + rect.height() * 0.18f, textPaint);
    }

    // ----------------- Multi-touch Event Handling -----------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int actionMasked = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float x = event.getX(actionIndex);
                float y = event.getY(actionIndex);
                TouchInfo info = new TouchInfo();
                info.startX = x;
                info.startY = y;
                info.currentX = x;
                info.currentY = y;
                info.target = determineTarget(x, y);
                activePointers.put(pointerId, info);
                handlePointerDown(info);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pId = event.getPointerId(i);
                    TouchInfo info = activePointers.get(pId);
                    if (info != null) {
                        info.currentX = event.getX(i);
                        info.currentY = event.getY(i);
                        handlePointerMove(info);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP: {
                TouchInfo info = activePointers.remove(pointerId);
                if (info != null) {
                    handlePointerUp(info);
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                for (TouchInfo info : activePointers.values()) {
                    handlePointerUp(info);
                }
                activePointers.clear();
                break;
            }
        }

        if (networkClient != null) {
            networkClient.markDirty();
        }
        invalidate();
        return true;
    }

    private int determineTarget(float x, float y) {
        // Quick toggles
        if (settingsRect.contains(x, y)) return TARGET_SETTINGS;
        if (gyroRect.contains(x, y)) return TARGET_GYRO;
        if (turboRect.contains(x, y)) return TARGET_TURBO;
        if (trackpadToggleRect.contains(x, y)) return TARGET_TRACKPAD_TOGGLE;

        // If Trackpad mode is active and touch is in trackpad zone
        if (trackpadMode && trackpadZoneRect.contains(x, y)) return TARGET_TRACKPAD_ZONE;

        // Shoulders
        if (ltRect.contains(x, y)) return TARGET_LT;
        if (rtRect.contains(x, y)) return TARGET_RT;
        if (lbRect.contains(x, y)) return TARGET_LB;
        if (rbRect.contains(x, y)) return TARGET_RB;

        // Center buttons
        if (!trackpadMode) {
            if (backRect.contains(x, y)) return TARGET_BACK;
            if (guideRect.contains(x, y)) return TARGET_GUIDE;
            if (startRect.contains(x, y)) return TARGET_START;
        }

        // Stick Click Badges
        if (l3Rect.contains(x, y)) return TARGET_L3;
        if (r3Rect.contains(x, y)) return TARGET_R3;

        // Joysticks
        float distLeftStick = dist(x, y, leftStickCenterX, leftStickCenterY);
        if (distLeftStick <= stickRadius * 1.5f) return TARGET_LEFT_STICK;

        float distRightStick = dist(x, y, rightStickCenterX, rightStickCenterY);
        if (distRightStick <= stickRadius * 1.5f) return TARGET_RIGHT_STICK;

        // D-Pad
        if (dist(x, y, dpadCenterX, dpadCenterY) <= dpadArmLength * 1.6f) return TARGET_DPAD;

        // Action Buttons
        if (dist(x, y, actionCenterX, actionCenterY) <= actionSpacing * 2.2f) return TARGET_ACTIONS;

        return TARGET_NONE;
    }

    private void handlePointerDown(TouchInfo info) {
        if (state == null) return;

        switch (info.target) {
            case TARGET_LEFT_STICK:
                updateStick(info.currentX, info.currentY, true);
                break;

            case TARGET_RIGHT_STICK:
                updateStick(info.currentX, info.currentY, false);
                break;

            case TARGET_DPAD:
                updateDPad(info.currentX, info.currentY);
                break;

            case TARGET_ACTIONS:
                updateActionButtons(info.currentX, info.currentY);
                break;

            case TARGET_LB:
                state.setButton(GamepadState.BTN_LB, true);
                if (vibrationManager != null) vibrationManager.triggerButtonHaptic();
                break;

            case TARGET_RB:
                state.setButton(GamepadState.BTN_RB, true);
                if (vibrationManager != null) vibrationManager.triggerButtonHaptic();
                break;

            case TARGET_LT:
                state.leftTrigger = 255;
                if (vibrationManager != null) vibrationManager.triggerHeavyHaptic();
                break;

            case TARGET_RT:
                state.rightTrigger = 255;
                if (vibrationManager != null) vibrationManager.triggerHeavyHaptic();
                break;

            case TARGET_BACK:
                state.setButton(GamepadState.BTN_BACK, true);
                if (vibrationManager != null) vibrationManager.triggerButtonHaptic();
                break;

            case TARGET_GUIDE:
                state.setButton(GamepadState.BTN_GUIDE, true);
                if (vibrationManager != null) vibrationManager.triggerButtonHaptic();
                break;

            case TARGET_START:
                state.setButton(GamepadState.BTN_START, true);
                if (vibrationManager != null) vibrationManager.triggerButtonHaptic();
                break;

            case TARGET_L3:
                state.setButton(GamepadState.BTN_L3, true);
                if (vibrationManager != null) vibrationManager.triggerHeavyHaptic();
                break;

            case TARGET_R3:
                state.setButton(GamepadState.BTN_R3, true);
                if (vibrationManager != null) vibrationManager.triggerHeavyHaptic();
                break;

            case TARGET_SETTINGS:
                if (settingsClickListener != null) {
                    settingsClickListener.onSettingsClicked();
                }
                break;

            case TARGET_GYRO:
                if (settingsClickListener != null) {
                    settingsClickListener.onGyroCycle();
                }
                break;

            case TARGET_TURBO:
                turboEnabled = !turboEnabled;
                if (vibrationManager != null) vibrationManager.triggerButtonHaptic();
                if (settingsClickListener != null) {
                    settingsClickListener.onTurboToggled(turboEnabled);
                }
                break;

            case TARGET_TRACKPAD_TOGGLE:
                trackpadMode = !trackpadMode;
                if (vibrationManager != null) vibrationManager.triggerButtonHaptic();
                if (settingsClickListener != null) {
                    settingsClickListener.onTrackpadToggled(trackpadMode);
                }
                break;

            case TARGET_TRACKPAD_ZONE:
                trackpadPrevX = info.currentX;
                trackpadPrevY = info.currentY;
                trackpadDownTime = System.currentTimeMillis();
                break;
        }
    }

    private void handlePointerMove(TouchInfo info) {
        if (state == null) return;

        switch (info.target) {
            case TARGET_LEFT_STICK:
                updateStick(info.currentX, info.currentY, true);
                break;

            case TARGET_RIGHT_STICK:
                updateStick(info.currentX, info.currentY, false);
                break;

            case TARGET_DPAD:
                updateDPad(info.currentX, info.currentY);
                break;

            case TARGET_ACTIONS:
                updateActionButtons(info.currentX, info.currentY);
                break;

            case TARGET_LT:
                updateTrigger(info, true);
                break;

            case TARGET_RT:
                updateTrigger(info, false);
                break;

            case TARGET_TRACKPAD_ZONE:
                float dx = info.currentX - trackpadPrevX;
                float dy = info.currentY - trackpadPrevY;
                trackpadPrevX = info.currentX;
                trackpadPrevY = info.currentY;

                // Smooth, calibrated natural mouse movement
                float distance = (float) Math.hypot(dx, dy);
                float acceleration = 1.0f + Math.min(distance * 0.02f, 0.8f);
                int moveX = Math.round(dx * mouseSensitivity * acceleration);
                int moveY = Math.round(dy * mouseSensitivity * acceleration);

                synchronized (state) {
                    state.gyroX = (short) Math.max(-32768, Math.min(32767, moveX));
                    state.gyroY = (short) Math.max(-32768, Math.min(32767, moveY));
                }
                break;
        }
    }

    private void handlePointerUp(TouchInfo info) {
        if (state == null) return;

        switch (info.target) {
            case TARGET_LEFT_STICK:
                leftStickKnobX = leftStickCenterX;
                leftStickKnobY = leftStickCenterY;
                state.leftStickX = 0;
                state.leftStickY = 0;
                break;

            case TARGET_RIGHT_STICK:
                rightStickKnobX = rightStickCenterX;
                rightStickKnobY = rightStickCenterY;
                state.rightStickX = 0;
                state.rightStickY = 0;
                break;

            case TARGET_DPAD:
                state.setButton(GamepadState.BTN_DPAD_UP, false);
                state.setButton(GamepadState.BTN_DPAD_DOWN, false);
                state.setButton(GamepadState.BTN_DPAD_LEFT, false);
                state.setButton(GamepadState.BTN_DPAD_RIGHT, false);
                break;

            case TARGET_ACTIONS:
                state.setButton(GamepadState.BTN_A, false);
                state.setButton(GamepadState.BTN_B, false);
                state.setButton(GamepadState.BTN_X, false);
                state.setButton(GamepadState.BTN_Y, false);
                break;

            case TARGET_LB:
                state.setButton(GamepadState.BTN_LB, false);
                break;

            case TARGET_RB:
                state.setButton(GamepadState.BTN_RB, false);
                break;

            case TARGET_LT:
                state.leftTrigger = 0;
                break;

            case TARGET_RT:
                state.rightTrigger = 0;
                break;

            case TARGET_BACK:
                state.setButton(GamepadState.BTN_BACK, false);
                break;

            case TARGET_GUIDE:
                state.setButton(GamepadState.BTN_GUIDE, false);
                break;

            case TARGET_START:
                state.setButton(GamepadState.BTN_START, false);
                break;

            case TARGET_L3:
                state.setButton(GamepadState.BTN_L3, false);
                break;

            case TARGET_R3:
                state.setButton(GamepadState.BTN_R3, false);
                break;

            case TARGET_TRACKPAD_ZONE:
                long duration = System.currentTimeMillis() - trackpadDownTime;
                float distMoved = dist(info.startX, info.startY, info.currentX, info.currentY);
                if (duration < 250 && distMoved < 15) {
                    // Tap = Left Click!
                    state.setButton(GamepadState.BTN_MOUSE_CLICK, true);
                    if (vibrationManager != null) vibrationManager.triggerButtonHaptic();
                    postDelayed(() -> {
                        if (state != null) state.setButton(GamepadState.BTN_MOUSE_CLICK, false);
                        if (networkClient != null) networkClient.markDirty();
                    }, 50);
                }
                synchronized (state) {
                    state.gyroX = 0;
                    state.gyroY = 0;
                }
                break;
        }
    }

    private void updateTrigger(TouchInfo info, boolean isLeft) {
        RectF rect = isLeft ? ltRect : rtRect;
        float dy = Math.max(0, info.currentY - rect.top);
        float dragRatio = Math.min(1.0f, dy / rect.height());
        int val = (dragRatio > 0.1f) ? (int)(dragRatio * 255f) : 255;
        if (isLeft) {
            state.leftTrigger = val;
        } else {
            state.rightTrigger = val;
        }
    }

    private void updateStick(float touchX, float touchY, boolean isLeft) {
        float centerX = isLeft ? leftStickCenterX : rightStickCenterX;
        float centerY = isLeft ? leftStickCenterY : rightStickCenterY;

        float dx = touchX - centerX;
        float dy = touchY - centerY;
        float dist = (float) Math.hypot(dx, dy);

        float maxDist = stickRadius - stickKnobRadius * 0.3f;
        if (dist > maxDist) {
            dx = (dx / dist) * maxDist;
            dy = (dy / dist) * maxDist;
        }

        if (isLeft) {
            leftStickKnobX = centerX + dx;
            leftStickKnobY = centerY + dy;
            state.leftStickX = (short) Math.max(-32768, Math.min(32767, (dx / maxDist) * 32767f));
            state.leftStickY = (short) Math.max(-32768, Math.min(32767, (-dy / maxDist) * 32767f));
        } else {
            rightStickKnobX = centerX + dx;
            rightStickKnobY = centerY + dy;
            state.rightStickX = (short) Math.max(-32768, Math.min(32767, (dx / maxDist) * 32767f));
            state.rightStickY = (short) Math.max(-32768, Math.min(32767, (-dy / maxDist) * 32767f));
        }
    }

    private void updateDPad(float x, float y) {
        float dx = x - dpadCenterX;
        float dy = y - dpadCenterY;

        boolean up = dy < -dpadArmWidth * 0.3f;
        boolean down = dy > dpadArmWidth * 0.3f;
        boolean left = dx < -dpadArmWidth * 0.3f;
        boolean right = dx > dpadArmWidth * 0.3f;

        boolean prevUp = state.isButtonPressed(GamepadState.BTN_DPAD_UP);
        boolean prevDown = state.isButtonPressed(GamepadState.BTN_DPAD_DOWN);
        boolean prevLeft = state.isButtonPressed(GamepadState.BTN_DPAD_LEFT);
        boolean prevRight = state.isButtonPressed(GamepadState.BTN_DPAD_RIGHT);

        state.setButton(GamepadState.BTN_DPAD_UP, up);
        state.setButton(GamepadState.BTN_DPAD_DOWN, down);
        state.setButton(GamepadState.BTN_DPAD_LEFT, left);
        state.setButton(GamepadState.BTN_DPAD_RIGHT, right);

        if ((up && !prevUp) || (down && !prevDown) || (left && !prevLeft) || (right && !prevRight)) {
            if (vibrationManager != null) vibrationManager.triggerButtonHaptic();
        }
    }

    private void updateActionButtons(float x, float y) {
        float dA = dist(x, y, btnAX, btnAY);
        float dB = dist(x, y, btnBX, btnBY);
        float dX = dist(x, y, btnXX, btnXY);
        float dY = dist(x, y, btnYX, btnYY);

        float threshold = buttonRadius * 1.35f;

        boolean pressedA = (dA <= threshold);
        boolean pressedB = (dB <= threshold);
        boolean pressedX = (dX <= threshold);
        boolean pressedY = (dY <= threshold);

        // If Turbo is active, pulse button state
        if (turboEnabled && !turboPulse) {
            pressedA = false;
            pressedB = false;
            pressedX = false;
            pressedY = false;
        }

        boolean prevA = state.isButtonPressed(GamepadState.BTN_A);
        boolean prevB = state.isButtonPressed(GamepadState.BTN_B);
        boolean prevX = state.isButtonPressed(GamepadState.BTN_X);
        boolean prevY = state.isButtonPressed(GamepadState.BTN_Y);

        state.setButton(GamepadState.BTN_A, pressedA);
        state.setButton(GamepadState.BTN_B, pressedB);
        state.setButton(GamepadState.BTN_X, pressedX);
        state.setButton(GamepadState.BTN_Y, pressedY);

        if ((pressedA && !prevA) || (pressedB && !prevB) || (pressedX && !prevX) || (pressedY && !prevY)) {
            if (vibrationManager != null) vibrationManager.triggerButtonHaptic();
        }
    }

    private float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }
}
