<div align="center">
  <img src="screenshots/app_icon.png" alt="ArachnidPad WiFi Gamepad App Icon" width="128" style="border-radius: 28px;" />
  <h1>ArachnidPad 🕷️ — WiFi Gamepad (Crimson Arachnid Edition)</h1>
  <p><b>Ultra-Low Latency Android Wi-Fi Game Controller for Windows PC &amp; macOS (MacBook) with ViGEmBus Controller Emulation, Force Feedback Rumble, Gyro Motion &amp; 4-Player Local Co-Op</b><br/><i>UI Design &amp; Art Direction by <a href="https://github.com/vitalityuser">@vitalityuser</a></i></p>

  <p>
    <a href="https://github.com/VitalityUser/wifi-gamepad/releases"><img src="https://img.shields.io/badge/Release-v1.0.0-FF1744?style=for-the-badge&logo=xbox&logoColor=white" alt="Latest Release" /></a>
    <img src="https://img.shields.io/badge/Platform-Android_8.0%2B-22D3EE?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+" />
    <img src="https://img.shields.io/badge/Windows-10_%2F_11_(Xbox_360)-0078D4?style=for-the-badge&logo=windows&logoColor=white" alt="Windows 10/11" />
    <img src="https://img.shields.io/badge/macOS-MacBook_%2F_iMac-000000?style=for-the-badge&logo=apple&logoColor=white" alt="macOS Support" />
    <img src="https://img.shields.io/badge/Latency-%3C_2ms_(UDP_120Hz)-00E5FF?style=for-the-badge" alt="Ultra-Low Latency" />
    <img src="https://img.shields.io/badge/License-MIT-FFA940?style=for-the-badge" alt="MIT License" />
  </p>
</div>

> **ArachnidPad (WiFi Gamepad: Crimson Arachnid Edition)** turns any Android smartphone into a high-performance, ultra-responsive wireless game controller for **Windows PC (Windows 10 / 11)** and **Apple Mac (MacBook Pro / Air, iMac, Mac mini)** over local Wi-Fi. Featuring custom 20-byte packed binary UDP transmission at 60–120 Hz, sub-2ms network latency, native dual-motor Force Feedback (Rumble) from PC games, Gyroscopic Steering &amp; FPS Micro-Aiming, 18 Hz Rapid-Fire Turbo, Virtual Touchpad Mouse mode with in-app sensitivity calibration, and 4-Player local co-op.

---

## 💻 Operating System Support Matrix

| Platform | Machine Support | Emulation Mode | Features Supported |
| :--- | :--- | :--- | :--- |
| **🪟 Windows PC** | **Windows 10 / 11** (64-bit / 32-bit / ARM64) | **Genuine Xbox 360 Controller** *(via ViGEmBus driver)* + Keyboard/Mouse Fallback | ✅ Native in 100% of PC games (Steam, Game Pass, Epic Games)<br/>✅ Dual-Motor Force Feedback Rumble<br/>✅ 4-Player Local Co-Op (P1–P4)<br/>✅ Touchpad Mouse Cursor &amp; Click |
| **🍎 macOS** | **MacBook Pro, MacBook Air, Mac mini, iMac** *(Apple Silicon M1/M2/M3/M4 &amp; Intel)* | **CoreGraphics Virtual HID &amp; Keymapper** | ✅ Native Mac games &amp; Apple Arcade<br/>✅ Mac Emulators (OpenEmu, Dolphin, RPCS3, RetroArch)<br/>✅ Smooth Touchpad Mouse with Tap-to-Click<br/>✅ Zero external drivers required |

---

## 📱 Visual Showcase & Hardware Concepts

<div align="center">
  <table>
    <tr>
      <td align="center" width="50%">
        <b>🕷️ Crimson Arachnid Edition Hardware Concept</b><br/><br/>
        <img src="screenshots/hardware_concept.jpg" alt="Crimson Arachnid Edition Hardware Concept 3D Render" width="440" style="border-radius: 12px;"/>
      </td>
      <td align="center" width="50%">
        <b>🎮 ArachnidPad Real-Time Touch Controller UI</b><br/><br/>
        <img src="screenshots/app_preview.png" alt="ArachnidPad Android Touch Screen Controller UI" width="440" style="border-radius: 12px;"/>
      </td>
    </tr>
  </table>
</div>

---

## 🔍 Feature Comparison

| Feature | Generic Virtual Gamepads | ArachnidPad (WiFi Gamepad) |
| :--- | :--- | :--- |
| **Windows PC Compatibility** | ⚠️ Requires manual Joy2Key / x360ce | 🎮 **100% Native (Emulates Genuine Xbox 360 via ViGEmBus)** |
| **macOS (MacBook) Compatibility** | ❌ Mostly Windows only | 🍎 **Dedicated macOS Server with native CoreGraphics** |
| **Transmission Protocol** | ❌ High-latency TCP / HTTP (>30ms) | ⚡ **Custom 20-Byte Binary UDP (<2ms latency, 60–120 Hz)** |
| **Game Force Feedback** | ❌ None | 📳 **Bidirectional PC Game Rumble streamed to phone motors** |
| **Motion Controls** | ❌ None | 🏎️ **Dual Gyro: Tilt-to-Steer &amp; FPS Micro-Aiming** |
| **Rapid Fire** | ❌ Manual tapping only | ⚡ **Hardware-timed 18 Hz Rapid-Fire Turbo Mode** |
| **Desktop Navigation** | ❌ Separate mouse app needed | 🖱️ **Integrated Touchpad Mouse mode with Sensitivity Slider** |
| **Multi-Player Support** | ❌ Single player only | 👥 **Up to 4 Phones connected simultaneously (P1–P4 Co-Op)** |
| **Connection Setup** | ⚠️ Manual IP entry required | 📡 **UDP Auto-Discovery Beacon (Instant 1-Tap Connect)** |
| **Aesthetics &amp; Themes** | ⚠️ Outdated generic UI | 🕷️ **4 Built-in Themes (Crimson, Ice, Velocity, Cyberpunk)** |

---

## 🚀 Key Features

### 1. 🪟 Windows PC: 100% Native Xbox 360 Emulation &amp; Force Feedback
- On Windows PC, ArachnidPad connects to the official signed **ViGEmBus** virtual bus driver.
- Windows detects your phone as a **genuine Microsoft Xbox 360 controller**. It works instantly with Steam Input, Xbox Game Pass for PC, Epic Games, EA App, Ubisoft Connect, and PC emulators without key mapping.
- **True Game Vibration / Rumble**: When an explosion, crash, or impact occurs in your PC game, the server intercepts DirectX XInput vibration calls and streams heavy and light motor pulses back to your Android phone's haptic motors in real-time.

### 2. 🍎 macOS: Dedicated MacBook Companion Server
- On macOS, ArachnidPad connects via a driverless **CoreGraphics** event bridge built into macOS.
- Seamlessly controls native Mac games, Apple Arcade titles, and Mac emulators (*OpenEmu, Dolphin, RPCS3, PCSX2, RetroArch*).
- Left thumbstick maps to `W/A/S/D`, D-Pad maps to Arrow Keys, `A` maps to `Space`, `B` maps to `C`, `X` maps to `R`, `Y` maps to `E`, Triggers map to Left/Right Click.
- Turns your phone into a smooth Mac trackpad with gesture support and tap-to-click.

### 3. 🏎️ Gyroscopic Steering &amp; 🎯 FPS Micro-Aiming
- **Racing Steering Mode**: Tilt your phone left and right like a physical steering wheel to drive cars smoothly in racing games (mapped smoothly to Left Thumbstick X).
- **FPS Micro-Aiming Mode**: Tilt your device for fine crosshair aiming and precision adjustments in shooters (mapped directly to Right Thumbstick X/Y).
- Cycle effortlessly between **OFF**, **RACING_STEER**, and **FPS_AIM** directly from the top status bar.

### 4. ⚡ 18 Hz Rapid-Fire Turbo Mode
- Tap the **⚡ Turbo** button to engage hardware-timed 18 Hz cycling on action buttons (A, B, X, Y, RB, RT). Hold any button down to output relentless rapid-fire.

### 5. 🖱️ Integrated Touchpad Mouse &amp; In-App Sensitivity Slider
- Toggle into **Touchpad Mouse Mode** with a dedicated status bar button. Glide your thumb to steer the PC or Mac mouse cursor and tap to left-click.
- Fine-tune your cursor speed using the new **Mouse Sensitivity Slider** inside the app Settings (`0.2x` to `3.0x`).

### 6. 👥 4-Player Local Co-Op
- Connect up to 4 Android smartphones simultaneously over the same Wi-Fi network. The Windows PC server automatically provisions separate Player 1, Player 2, Player 3, and Player 4 virtual Xbox 360 controller slots.

### 7. 📡 UDP Auto-Discovery Beacon
- The server broadcasts a beacon on UDP 8889. The Android app automatically detects your PC or Mac on the local Wi-Fi and connects in 1 tap — no manual IP typing needed.

---

## 🎮 Controller Layout

```
 ____________________________________________________________________
| [LT]  [LB]      [GYRO: OFF/STEER/AIM]  [TURBO]  [MOUSE]     [RB]  [RT] |
|                                                                    |
|     ( ^ )           [BACK]     ( 🕷️ )     [START]            ( Y )   |
|   < DPAD >                      GUIDE                     ( X ) ( B )|
|     ( v )                                                    ( A )   |
|                                                                    |
|           (( LS / L3 ))                         (( RS / R3 ))      |
|____________________________________________________________________|
```

---

## ⚡ Quick Start &amp; Installation

### 📥 1-Click Pre-Built Downloads
- **📱 Android Controller App**: **[`WiFiGamepad.apk`](https://github.com/VitalityUser/wifi-gamepad/releases/download/v1.0.0/WiFiGamepad.apk)**
- **💻 Windows PC Server Package**: **[`ArachnidPad-PC-Server.zip`](https://github.com/VitalityUser/wifi-gamepad/releases/download/v1.0.0/ArachnidPad-PC-Server.zip)**
- **🍎 macOS Server Package**: **[`ArachnidPad-Mac-Server.zip`](https://github.com/VitalityUser/wifi-gamepad/releases/download/v1.0.0/ArachnidPad-Mac-Server.zip)**

---

### Step 1A: Setup on Windows PC (Windows 10 / 11)
1. Download and extract **[`ArachnidPad-PC-Server.zip`](https://github.com/VitalityUser/wifi-gamepad/releases/download/v1.0.0/ArachnidPad-PC-Server.zip)**.
2. Double-click **`start_server.bat`** (or run `python server.py`).
3. *If ViGEmBus is not yet installed*: Click the **"📥 1-Click Install ViGEm Driver"** button inside the server dashboard, then restart the server.
4. The server dashboard will open and show your PC's Wi-Fi IP address.

---

### Step 1B: Setup on macOS (MacBook Pro / Air, Mac mini, iMac)
1. Download and extract **[`ArachnidPad-Mac-Server.zip`](https://github.com/VitalityUser/wifi-gamepad/releases/download/v1.0.0/ArachnidPad-Mac-Server.zip)**.
2. Double-click **`start_server.command`** (or run `python3 server_mac.py` in Terminal).
3. *Accessibility Permission*: Go to **System Settings > Privacy &amp; Security > Accessibility** and ensure Terminal / Python is checked to allow game inputs.
4. The server dashboard will display your Mac's Wi-Fi IP address.

---

### Step 2: Install the Android App
- Download and open **[`WiFiGamepad.apk`](https://github.com/VitalityUser/wifi-gamepad/releases/download/v1.0.0/WiFiGamepad.apk)** on your Android device (Android 8.0 or newer).
- Grant vibration permissions when prompted.

---

### Step 3: Connect &amp; Play
1. Ensure your phone and your computer (Windows PC or MacBook) are connected to the same Wi-Fi network (or computer Mobile Hotspot).
2. Open the **ArachnidPad** app on your phone. It will automatically detect your computer name and connect!
3. Launch your favorite game and enjoy wireless console-grade gaming!

---

## 🛠️ Building from Source

### Android APK (Gradle)
```powershell
cd android
./gradlew assembleDebug
```
The compiled output APK will be located at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## 🔬 Network Protocol Specification

Ultra-compact 20-byte packed binary packet structure over UDP:

```c
struct __attribute__((__packed__)) GamepadPacket {
    char     magic[2];       // "GP" (0x47, 0x50)
    uint16_t seqNumber;      // Monotonically increasing sequence ID
    uint16_t buttons;        // 16-bit bitmask (A, B, X, Y, LB, RB, etc.)
    uint8_t  leftTrigger;    // 0 to 255
    uint8_t  rightTrigger;   // 0 to 255
    int16_t  leftStickX;     // -32768 to 32767
    int16_t  leftStickY;     // -32768 to 32767
    int16_t  rightStickX;    // -32768 to 32767
    int16_t  rightStickY;    // -32768 to 32767
    int16_t  gyroX_deltaX;   // Gyro yaw / Trackpad mouse delta X
    int16_t  gyroY_deltaY;   // Gyro pitch / Trackpad mouse delta Y
};
```

---

## 👤 Author &amp; Design
- **Developer &amp; Maintainer**: [@vitalityuser](https://github.com/vitalityuser)
- **UI Design &amp; Art Direction**: [@vitality_user](https://github.com/vitalityuser)
- **License**: MIT Open Source
