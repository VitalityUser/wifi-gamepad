package com.antigravity.wifigamepad;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GamepadState {

    // Button bitmasks
    public static final int BTN_A          = 1 << 0;
    public static final int BTN_B          = 1 << 1;
    public static final int BTN_X          = 1 << 2;
    public static final int BTN_Y          = 1 << 3;
    public static final int BTN_LB         = 1 << 4;
    public static final int BTN_RB         = 1 << 5;
    public static final int BTN_BACK       = 1 << 6;
    public static final int BTN_START      = 1 << 7;
    public static final int BTN_GUIDE      = 1 << 8;
    public static final int BTN_L3         = 1 << 9;
    public static final int BTN_R3         = 1 << 10;
    public static final int BTN_DPAD_UP    = 1 << 11;
    public static final int BTN_DPAD_DOWN  = 1 << 12;
    public static final int BTN_DPAD_LEFT  = 1 << 13;
    public static final int BTN_DPAD_RIGHT = 1 << 14;
    public static final int BTN_MOUSE_CLICK= 1 << 15;

    public int buttons = 0;
    public int leftTrigger = 0;   // 0 - 255
    public int rightTrigger = 0;  // 0 - 255
    public short leftStickX = 0;  // -32768 to 32767
    public short leftStickY = 0;  // -32768 to 32767
    public short rightStickX = 0; // -32768 to 32767
    public short rightStickY = 0; // -32768 to 32767
    public short gyroX = 0;
    public short gyroY = 0;

    private static short sequence = 0;

    public void setButton(int bit, boolean pressed) {
        if (pressed) {
            buttons |= bit;
        } else {
            buttons &= ~bit;
        }
    }

    public boolean isButtonPressed(int bit) {
        return (buttons & bit) != 0;
    }

    /**
     * Serializes state into a compact 20-byte binary packet for ultra-low latency UDP transmission:
     * [0-1]   Magic 'G', 'P' (0x47, 0x50)
     * [2-3]   Seq num (uint16)
     * [4-5]   Buttons bitmask (uint16)
     * [6]     Left Trigger (0-255)
     * [7]     Right Trigger (0-255)
     * [8-9]   Left Stick X (int16)
     * [10-11] Left Stick Y (int16)
     * [12-13] Right Stick X (int16)
     * [14-15] Right Stick Y (int16)
     * [16-17] Gyro X (int16)
     * [18-19] Gyro Y (int16)
     */
    public byte[] toBinaryPacket() {
        ByteBuffer buf = ByteBuffer.allocate(20);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 'G');
        buf.put((byte) 'P');
        buf.putShort(sequence++);
        buf.putShort((short) buttons);
        buf.put((byte) (leftTrigger & 0xFF));
        buf.put((byte) (rightTrigger & 0xFF));
        buf.putShort(leftStickX);
        buf.putShort(leftStickY);
        buf.putShort(rightStickX);
        buf.putShort(rightStickY);
        buf.putShort(gyroX);
        buf.putShort(gyroY);
        return buf.array();
    }
}
