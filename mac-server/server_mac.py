"""
ArachnidPad macOS Companion Server (MacBook / Mac mini / iMac)
-------------------------------------------------------------
- Ultra-low-latency UDP receiver (60-120 Hz) on port 8888
- UDP Auto-Discovery Beacon on port 8889 for 1-tap MacBook pairing
- Zero-dependency native macOS CoreGraphics mouse & trackpad injection
- Game-ready keyboard mapping for macOS native games, Apple Arcade & emulators (OpenEmu, Dolphin, RPCS3, RetroArch)
- Dark Crimson Arachnid Gamer Dashboard GUI with live input visualizer
"""

import sys
import os
import socket
import struct
import threading
import time
import tkinter as tk
from tkinter import ttk, messagebox
import ctypes
import ctypes.util

UDP_PORT = 8888
DISCOVERY_PORT = 8889

# Button masks (matching Android GamepadState)
BTN_A           = 1 << 0
BTN_B           = 1 << 1
BTN_X           = 1 << 2
BTN_Y           = 1 << 3
BTN_LB          = 1 << 4
BTN_RB          = 1 << 5
BTN_BACK        = 1 << 6
BTN_START       = 1 << 7
BTN_GUIDE       = 1 << 8
BTN_L3          = 1 << 9
BTN_R3          = 1 << 10
BTN_DPAD_UP     = 1 << 11
BTN_DPAD_DOWN   = 1 << 12
BTN_DPAD_LEFT   = 1 << 13
BTN_DPAD_RIGHT  = 1 << 14
BTN_MOUSE_CLICK = 1 << 15

# macOS Virtual Keycodes (Carbon / HIToolbox)
MAC_KEY_A = 0x00
MAC_KEY_S = 0x01
MAC_KEY_D = 0x02
MAC_KEY_W = 0x0D
MAC_KEY_Q = 0x0C
MAC_KEY_E = 0x0E
MAC_KEY_R = 0x0F
MAC_KEY_C = 0x08
MAC_KEY_SPACE = 0x31
MAC_KEY_TAB = 0x30
MAC_KEY_ESC = 0x35
MAC_KEY_SHIFT = 0x38
MAC_KEY_UP = 0x7E
MAC_KEY_DOWN = 0x7D
MAC_KEY_LEFT = 0x7B
MAC_KEY_RIGHT = 0x7C

# ----------------- macOS CoreGraphics Bridge -----------------
class CGPoint(ctypes.Structure):
    _fields_ = [("x", ctypes.c_double), ("y", ctypes.c_double)]

class CGSize(ctypes.Structure):
    _fields_ = [("width", ctypes.c_double), ("height", ctypes.c_double)]

class CGRect(ctypes.Structure):
    _fields_ = [("origin", CGPoint), ("size", CGSize)]

cg = None
try:
    cg_lib_name = ctypes.util.find_library('CoreGraphics') or '/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics'
    cg = ctypes.CDLL(cg_lib_name)
except Exception as e:
    print(f"[macOS] CoreGraphics load warning: {e}")

# CoreGraphics Constants
kCGHIDEventTap = 0
kCGEventLeftMouseDown = 1
kCGEventLeftMouseUp = 2
kCGEventRightMouseDown = 3
kCGEventRightMouseUp = 4
kCGEventMouseMoved = 5
kCGEventLeftMouseDragged = 6
kCGMouseButtonLeft = 0
kCGMouseButtonRight = 1

# Setup signatures if CG loaded
if cg:
    try:
        cg.CGEventCreateMouseEvent.restype = ctypes.c_void_p
        cg.CGEventCreateMouseEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint32, CGPoint, ctypes.c_uint32]

        cg.CGEventCreateKeyboardEvent.restype = ctypes.c_void_p
        cg.CGEventCreateKeyboardEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint16, ctypes.c_bool]

        cg.CGEventPost.argtypes = [ctypes.c_uint32, ctypes.c_void_p]
        cg.CFRelease.argtypes = [ctypes.c_void_p]

        cg.CGMainDisplayID.restype = ctypes.c_uint32
        cg.CGDisplayBounds.restype = CGRect
        cg.CGDisplayBounds.argtypes = [ctypes.c_uint32]
    except Exception as e:
        print(f"[macOS] CoreGraphics symbol setup warning: {e}")


class MacInputManager:
    """Manages low-latency mouse cursor movement and game key simulation on macOS"""
    def __init__(self):
        self.cursor_x = 700.0
        self.cursor_y = 450.0
        self.max_x = 1920.0
        self.max_y = 1080.0
        self.left_down = False
        self.right_down = False
        self.pressed_keys = set()

        self.init_screen_bounds()

    def init_screen_bounds(self):
        if cg and hasattr(cg, 'CGMainDisplayID') and hasattr(cg, 'CGDisplayBounds'):
            try:
                main_id = cg.CGMainDisplayID()
                bounds = cg.CGDisplayBounds(main_id)
                self.max_x = float(bounds.size.width)
                self.max_y = float(bounds.size.height)
                self.cursor_x = self.max_x / 2.0
                self.cursor_y = self.max_y / 2.0
            except Exception:
                pass

    def move_mouse(self, dx, dy):
        self.cursor_x = max(0.0, min(self.max_x, self.cursor_x + float(dx)))
        self.cursor_y = max(0.0, min(self.max_y, self.cursor_y + float(dy)))

        if not cg:
            return

        pt = CGPoint(self.cursor_x, self.cursor_y)
        ev_type = kCGEventLeftMouseDragged if self.left_down else kCGEventMouseMoved
        ev = cg.CGEventCreateMouseEvent(None, ev_type, pt, kCGMouseButtonLeft)
        if ev:
            cg.CGEventPost(kCGHIDEventTap, ev)
            cg.CFRelease(ev)

    def mouse_click(self, button_left, down):
        if not cg:
            return

        pt = CGPoint(self.cursor_x, self.cursor_y)
        if button_left:
            self.left_down = down
            ev_type = kCGEventLeftMouseDown if down else kCGEventLeftMouseUp
            ev = cg.CGEventCreateMouseEvent(None, ev_type, pt, kCGMouseButtonLeft)
        else:
            self.right_down = down
            ev_type = kCGEventRightMouseDown if down else kCGEventRightMouseUp
            ev = cg.CGEventCreateMouseEvent(None, ev_type, pt, kCGMouseButtonRight)

        if ev:
            cg.CGEventPost(kCGHIDEventTap, ev)
            cg.CFRelease(ev)

    def set_key(self, keycode, down):
        if not cg:
            return

        is_currently_down = keycode in self.pressed_keys
        if down and not is_currently_down:
            self.pressed_keys.add(keycode)
            ev = cg.CGEventCreateKeyboardEvent(None, keycode, True)
            if ev:
                cg.CGEventPost(kCGHIDEventTap, ev)
                cg.CFRelease(ev)
        elif not down and is_currently_down:
            self.pressed_keys.remove(keycode)
            ev = cg.CGEventCreateKeyboardEvent(None, keycode, False)
            if ev:
                cg.CGEventPost(kCGHIDEventTap, ev)
                cg.CFRelease(ev)


# ----------------- Server Network Core -----------------

class MacGamepadServer:
    def __init__(self, ui_callback=None):
        self.ui_callback = ui_callback
        self.running = False
        self.sock = None
        self.input_mgr = MacInputManager()

        self.phone_addr = None
        self.packets_count = 0
        self.fps = 0
        self.mouse_clicked = False

    def start(self):
        self.running = True
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(("0.0.0.0", UDP_PORT))

        threading.Thread(target=self._receive_loop, daemon=True, name="Mac-UDP-Server").start()
        threading.Thread(target=self._discovery_broadcast_loop, daemon=True, name="Mac-UDP-Discovery").start()
        threading.Thread(target=self._fps_counter_loop, daemon=True, name="Mac-FPS-Counter").start()

    def stop(self):
        self.running = False
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass

    def _discovery_broadcast_loop(self):
        """Broadcasts server beacon so Android app auto-detects the Mac on Wi-Fi"""
        bcast_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        bcast_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        hostname = socket.gethostname()
        beacon = f"WIFI_GAMEPAD_SERVER:{UDP_PORT}:{hostname}".encode('utf-8')

        while self.running:
            try:
                bcast_sock.sendto(beacon, ("255.255.255.255", DISCOVERY_PORT))
            except Exception:
                pass
            time.sleep(1.5)

    def _fps_counter_loop(self):
        while self.running:
            time.sleep(1.0)
            self.fps = self.packets_count
            self.packets_count = 0
            if self.ui_callback:
                self.ui_callback("FPS", self.fps)

    def _receive_loop(self):
        while self.running:
            try:
                data, addr = self.sock.recvfrom(1024)
                if not data:
                    continue

                self.packets_count += 1
                self.phone_addr = addr

                # Text handshake / ping
                if data.startswith(b"HELLO") or data == b"HELLO_GAMEPAD":
                    self.sock.sendto(b"WELCOME", addr)
                    if self.ui_callback:
                        self.ui_callback("CONNECTED", addr[0])
                    continue
                elif data.startswith(b"PING:"):
                    ts = data.split(b":")[1]
                    self.sock.sendto(b"PONG:" + ts, addr)
                    continue
                elif data == b"BYE":
                    if self.ui_callback:
                        self.ui_callback("DISCONNECTED", addr[0])
                    self.phone_addr = None
                    continue

                # Binary 20-byte packet
                if len(data) >= 20 and data[0:2] == b"GP":
                    unpacked = struct.unpack("<2sHHBBhhhhhh", data[:20])
                    buttons = unpacked[2]
                    lt = unpacked[3]
                    rt = unpacked[4]
                    lx = unpacked[5]
                    ly = unpacked[6]
                    rx = unpacked[7]
                    ry = unpacked[8]
                    gx = unpacked[9]
                    gy = unpacked[10]

                    # 1. Handle Touchpad Mouse movement & tap-to-click
                    if gx != 0 or gy != 0:
                        self.input_mgr.move_mouse(gx, gy)

                    is_mouse_click = bool(buttons & BTN_MOUSE_CLICK)
                    if is_mouse_click != self.mouse_clicked:
                        self.mouse_clicked = is_mouse_click
                        self.input_mgr.mouse_click(True, is_mouse_click)

                    # 2. Map controller actions to Mac keys
                    self.process_mac_inputs(buttons, lt, rt, lx, ly, rx, ry)

                    if self.ui_callback:
                        self.ui_callback("STATE", {
                            "buttons": buttons,
                            "lt": lt,
                            "rt": rt,
                            "lx": lx,
                            "ly": ly,
                            "rx": rx,
                            "ry": ry
                        })

            except Exception as e:
                if self.running:
                    print(f"[Mac-Server] Recv error: {e}")

    def process_mac_inputs(self, buttons, lt, rt, lx, ly, rx, ry):
        deadzone = 8000

        # Left Stick -> WASD
        self.input_mgr.set_key(MAC_KEY_W, ly > deadzone)
        self.input_mgr.set_key(MAC_KEY_S, ly < -deadzone)
        self.input_mgr.set_key(MAC_KEY_A, lx < -deadzone)
        self.input_mgr.set_key(MAC_KEY_D, lx > deadzone)

        # D-Pad -> Arrow keys
        self.input_mgr.set_key(MAC_KEY_UP, bool(buttons & BTN_DPAD_UP))
        self.input_mgr.set_key(MAC_KEY_DOWN, bool(buttons & BTN_DPAD_DOWN))
        self.input_mgr.set_key(MAC_KEY_LEFT, bool(buttons & BTN_DPAD_LEFT))
        self.input_mgr.set_key(MAC_KEY_RIGHT, bool(buttons & BTN_DPAD_RIGHT))

        # Action buttons
        self.input_mgr.set_key(MAC_KEY_SPACE, bool(buttons & BTN_A))     # A -> Space (Jump)
        self.input_mgr.set_key(MAC_KEY_C, bool(buttons & BTN_B))         # B -> C (Crouch)
        self.input_mgr.set_key(MAC_KEY_R, bool(buttons & BTN_X))         # X -> R (Reload)
        self.input_mgr.set_key(MAC_KEY_E, bool(buttons & BTN_Y))         # Y -> E (Interact)

        # Bumpers & Stick Clicks
        self.input_mgr.set_key(MAC_KEY_Q, bool(buttons & BTN_LB))        # LB -> Q
        self.input_mgr.set_key(MAC_KEY_SHIFT, bool(buttons & BTN_L3))    # L3 -> Shift (Sprint)
        self.input_mgr.set_key(MAC_KEY_TAB, bool(buttons & BTN_BACK))     # Back -> Tab
        self.input_mgr.set_key(MAC_KEY_ESC, bool(buttons & BTN_START))    # Start -> Esc

        # Triggers -> Left / Right Click
        self.input_mgr.mouse_click(True, rt > 100)
        self.input_mgr.mouse_click(False, lt > 100)

        # Right Stick -> Camera Look
        if abs(rx) > deadzone or abs(ry) > deadzone:
            sens = 0.0004
            self.input_mgr.move_mouse(rx * sens, -ry * sens)


def get_local_ips():
    """Retrieve all local IPv4 addresses on macOS"""
    ips = []
    try:
        hostname = socket.gethostname()
        for ip in socket.gethostbyname_ex(hostname)[2]:
            if not ip.startswith("127."):
                ips.append(ip)
    except Exception:
        pass
    if not ips:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ips.append(s.getsockname()[0])
            s.close()
        except Exception:
            ips.append("127.0.0.1")
    return ips


# ----------------- macOS Dark Gamer Dashboard UI -----------------

class MacGamepadServerGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("🕷️ ArachnidPad macOS Companion Server")
        self.root.geometry("740x600")
        self.root.minsize(700, 560)
        self.root.configure(bg="#08090E")

        self.server = MacGamepadServer(ui_callback=self.on_server_event)

        self.setup_ui()
        self.server.start()

        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def setup_ui(self):
        # Header banner
        header = tk.Frame(self.root, bg="#12141F", pady=12)
        header.pack(fill=tk.X)

        title_lbl = tk.Label(header, text="🕷️ ARACHNIDPAD MACOS SERVER", font=("Helvetica", 18, "bold"), fg="#FF1744", bg="#12141F")
        title_lbl.pack()

        sub_lbl = tk.Label(header, text="MacBook & macOS Edition | Low-Latency Wi-Fi Game Controller & Touchpad", font=("Helvetica", 10), fg="#A0A5B5", bg="#12141F")
        sub_lbl.pack()

        # Connection Info Frame
        info_frame = tk.Frame(self.root, bg="#0E1018", padx=15, pady=12, relief=tk.RIDGE, bd=1)
        info_frame.pack(fill=tk.X, padx=15, pady=10)

        ips = get_local_ips()
        ip_str = " | ".join(ips)

        lbl_ip_title = tk.Label(info_frame, text="YOUR MAC WI-FI IP ADDRESS:", font=("Helvetica", 10, "bold"), fg="#FF1744", bg="#0E1018")
        lbl_ip_title.grid(row=0, column=0, sticky="w")

        self.lbl_ip = tk.Label(info_frame, text=f"{ip_str}  (Port: {UDP_PORT})", font=("Menlo", 14, "bold"), fg="#FFFFFF", bg="#0E1018")
        self.lbl_ip.grid(row=1, column=0, sticky="w", pady=(2, 6))

        # Status row
        status_row = tk.Frame(info_frame, bg="#0E1018")
        status_row.grid(row=2, column=0, sticky="w")

        self.lbl_phone = tk.Label(status_row, text="Phone: Waiting for connection...", font=("Helvetica", 10, "bold"), fg="#FFB300", bg="#0E1018")
        self.lbl_phone.pack(side=tk.LEFT, padx=(0, 20))

        self.lbl_rate = tk.Label(status_row, text="Stream Rate: 0 Hz", font=("Helvetica", 10), fg="#8A99AD", bg="#0E1018")
        self.lbl_rate.pack(side=tk.LEFT)

        # Accessibility Permission Notice Frame
        perm_frame = tk.Frame(self.root, bg="#0E1018", padx=15, pady=8, relief=tk.RIDGE, bd=1)
        perm_frame.pack(fill=tk.X, padx=15, pady=(0, 8))

        perm_text = "💡 macOS Tip: Ensure Terminal / Python has 'Accessibility' permission in System Settings > Privacy & Security to allow game input."
        lbl_perm = tk.Label(perm_frame, text=perm_text, font=("Helvetica", 9), fg="#00E5FF", bg="#0E1018", wraplength=680, justify="left")
        lbl_perm.pack(anchor="w")

        # Visualizer Canvas
        vis_frame = tk.Frame(self.root, bg="#12141F", padx=10, pady=10, relief=tk.GROOVE, bd=1)
        vis_frame.pack(fill=tk.BOTH, expand=True, padx=15, pady=5)

        tk.Label(vis_frame, text="LIVE CONTROLLER TELEMETRY MONITOR", font=("Helvetica", 10, "bold"), fg="#FF1744", bg="#12141F").pack(anchor="w")

        self.canvas = tk.Canvas(vis_frame, bg="#0A0B10", highlightthickness=0, height=200)
        self.canvas.pack(fill=tk.BOTH, expand=True, pady=6)
        self.canvas.bind("<Configure>", self.draw_controller)

        self.last_state = {"buttons": 0, "lt": 0, "rt": 0, "lx": 0, "ly": 0, "rx": 0, "ry": 0}

    def on_server_event(self, event_type, data):
        self.root.after(0, self._handle_event, event_type, data)

    def _handle_event(self, event_type, data):
        if event_type == "CONNECTED":
            self.lbl_phone.config(text=f"Phone: Connected ({data}) 🟢", fg="#00E676")
        elif event_type == "DISCONNECTED":
            self.lbl_phone.config(text=f"Phone: Disconnected ({data}) 🔴", fg="#FF5252")
        elif event_type == "FPS":
            self.lbl_rate.config(text=f"Stream Rate: {data} Hz")
        elif event_type == "STATE":
            self.last_state = data
            self.draw_controller()

    def draw_controller(self, event=None):
        canvas = self.canvas
        canvas.delete("all")

        w = canvas.winfo_width()
        h = canvas.winfo_height()
        if w < 10 or h < 10:
            return

        state = self.last_state
        buttons = state.get("buttons", 0)
        lx = state.get("lx", 0)
        ly = state.get("ly", 0)
        rx = state.get("rx", 0)
        ry = state.get("ry", 0)

        # Left Stick Visualizer
        cx_l = w * 0.25
        cy_l = h * 0.55
        r_base = min(w, h) * 0.28
        r_knob = r_base * 0.45

        canvas.create_oval(cx_l - r_base, cy_l - r_base, cx_l + r_base, cy_l + r_base, outline="#222C3D", width=2, fill="#121824")
        knob_x = cx_l + (lx / 32767.0) * (r_base - r_knob)
        knob_y = cy_l - (ly / 32767.0) * (r_base - r_knob)
        canvas.create_oval(knob_x - r_knob, knob_y - r_knob, knob_x + r_knob, knob_y + r_knob, fill="#FF1744" if (buttons & BTN_L3) else "#1B4D68", outline="#00E5FF", width=2)
        canvas.create_text(knob_x, knob_y, text="LS", fill="white", font=("Helvetica", 9, "bold"))

        # Right Stick Visualizer
        cx_r = w * 0.60
        cy_r = h * 0.65
        canvas.create_oval(cx_r - r_base, cy_r - r_base, cx_r + r_base, cy_r + r_base, outline="#222C3D", width=2, fill="#121824")
        knob_rx = cx_r + (rx / 32767.0) * (r_base - r_knob)
        knob_ry = cy_r - (ry / 32767.0) * (r_base - r_knob)
        canvas.create_oval(knob_rx - r_knob, knob_ry - r_knob, knob_rx + r_knob, knob_ry + r_knob, fill="#FF1744" if (buttons & BTN_R3) else "#1B4D68", outline="#00E5FF", width=2)
        canvas.create_text(knob_rx, knob_ry, text="RS", fill="white", font=("Helvetica", 9, "bold"))

        # Action Buttons Visualizer
        act_cx = w * 0.85
        act_cy = h * 0.45
        sp = r_base * 0.65
        br = r_base * 0.32

        btn_list = [
            (act_cx, act_cy + sp, "A", BTN_A, "#00E676"),
            (act_cx + sp, act_cy, "B", BTN_B, "#FF5252"),
            (act_cx - sp, act_cy, "X", BTN_X, "#448AFF"),
            (act_cx, act_cy - sp, "Y", BTN_Y, "#FFD600"),
        ]

        for bx, by, lbl, mask, col in btn_list:
            pressed = bool(buttons & mask)
            canvas.create_oval(bx - br, by - br, bx + br, by + br, fill=col if pressed else "#151B26", outline=col, width=2)
            canvas.create_text(bx, by, text=lbl, fill="#0B0E14" if pressed else col, font=("Helvetica", 9, "bold"))

    def on_close(self):
        self.server.stop()
        self.root.destroy()


def main():
    root = tk.Tk()
    app = MacGamepadServerGUI(root)
    root.mainloop()

if __name__ == "__main__":
    main()
