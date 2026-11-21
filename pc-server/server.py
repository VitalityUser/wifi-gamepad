"""
WiFi Gamepad PC Companion Server (Spider Edition)
------------------------------------------------
- Ultra-low-latency UDP receiver (60-120 Hz)
- Emulates genuine Xbox 360 controller via ViGEmBus (supported by 100% of PC games)
- Multi-Player Local Co-Op support: Connect up to 4 phones (Player 1 to 4)
- Bidirectional Force Feedback (Game Vibration / Rumble) streamed directly to Android phone
- Touchpad Mouse Mode: Smooth mouse cursor movement and tap-to-click via Windows SendInput
- Fallback Keyboard & Mouse emulation mode
- UDP Auto-Discovery beacon for zero-config phone pairing
- Real-time Gamer Dashboard GUI with live input visualizer & rumble monitor
"""

import sys
import os
import socket
import struct
import threading
import time
import urllib.request
import subprocess
import tkinter as tk
from tkinter import ttk, messagebox

# Ensure local vgamepad module can be loaded
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

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

# Windows SendInput structure definitions for mouse & fallback keyboard mode
import ctypes
from ctypes import wintypes

user32 = ctypes.windll.user32

INPUT_MOUSE    = 0
INPUT_KEYBOARD = 1

KEYEVENTF_KEYUP     = 0x0002
MOUSEEVENTF_MOVE    = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP   = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP   = 0x0010

class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_void_p),
    ]

class MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", wintypes.LONG),
        ("dy", wintypes.LONG),
        ("mouseData", wintypes.DWORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_void_p),
    ]

class _INPUT_UNION(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT)]

class INPUT(ctypes.Structure):
    _fields_ = [("type", wintypes.DWORD), ("u", _INPUT_UNION)]

def send_key(vk_code, down):
    inp = INPUT()
    inp.type = INPUT_KEYBOARD
    inp.u.ki.wVk = vk_code
    inp.u.ki.dwFlags = 0 if down else KEYEVENTF_KEYUP
    user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))

def send_mouse_move(dx, dy):
    inp = INPUT()
    inp.type = INPUT_MOUSE
    inp.u.mi.dx = int(dx)
    inp.u.mi.dy = int(dy)
    inp.u.mi.dwFlags = MOUSEEVENTF_MOVE
    user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))

def send_mouse_click(button_left, down):
    inp = INPUT()
    inp.type = INPUT_MOUSE
    if button_left:
        inp.u.mi.dwFlags = MOUSEEVENTF_LEFTDOWN if down else MOUSEEVENTF_LEFTUP
    else:
        inp.u.mi.dwFlags = MOUSEEVENTF_RIGHTDOWN if down else MOUSEEVENTF_RIGHTUP
    user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))

# Virtual keys
VK_W = 0x57
VK_A = 0x41
VK_S = 0x53
VK_D = 0x44
VK_SPACE = 0x20
VK_SHIFT = 0x10
VK_CTRL = 0x11
VK_E = 0x45
VK_R = 0x52
VK_Q = 0x51
VK_F = 0x46
VK_ESCAPE = 0x1B


class PlayerSession:
    """Manages an individual connected controller (Player 1-4)"""
    def __init__(self, player_id, addr, gamepad=None):
        self.player_id = player_id
        self.addr = addr
        self.gamepad = gamepad
        self.last_seen = time.time()
        self.mouse_clicked = False
        self.large_motor = 0
        self.small_motor = 0


class GamepadControllerServer:
    def __init__(self, ui_callback=None):
        self.ui_callback = ui_callback
        self.running = False
        self.sock = None
        self.discovery_sock = None

        self.vg = None
        self.vigem_available = False
        self.vigem_error_msg = ""
        self.mode = "XBOX"  # "XBOX" or "KB_MOUSE"

        self.players = {}  # addr -> PlayerSession
        self.lock = threading.Lock()

        self.packets_count = 0
        self.fps = 0

        self.init_vigem()

    def init_vigem(self):
        try:
            import vgamepad as vg
            self.vg = vg
            self.vigem_available = True
            self.mode = "XBOX"
            print("[ViGEm] Virtual Xbox 360 Controller Engine initialized successfully!")
        except Exception as e:
            self.vigem_available = False
            self.vigem_error_msg = str(e)
            self.mode = "KB_MOUSE"
            print(f"[ViGEm] Not available: {e}. Falling back to Keyboard/Mouse mode.")

    def get_or_create_player(self, addr):
        with self.lock:
            if addr in self.players:
                session = self.players[addr]
                session.last_seen = time.time()
                return session

            if len(self.players) >= 4:
                # Max 4 players reached
                return None

            # Allocate next available player ID (1 to 4)
            used_ids = {p.player_id for p in self.players.values()}
            assigned_id = 1
            for i in range(1, 5):
                if i not in used_ids:
                    assigned_id = i
                    break

            gamepad = None
            if self.vigem_available and self.vg:
                try:
                    gamepad = self.vg.VX360Gamepad()
                    # Closure for player-specific rumble feedback
                    def make_rumble_cb(player_addr):
                        return lambda client, target, large, small, led, udata: self.on_game_rumble(player_addr, large, small)

                    gamepad.register_notification(make_rumble_cb(addr))
                    print(f"[Server] Assigned Player {assigned_id} to {addr}")
                except Exception as e:
                    print(f"[Server] Error instantiating virtual controller: {e}")

            session = PlayerSession(assigned_id, addr, gamepad)
            self.players[addr] = session

            if self.ui_callback:
                self.ui_callback("PLAYER_JOINED", (assigned_id, addr[0]))

            return session

    def on_game_rumble(self, player_addr, large_motor, small_motor):
        """Called by ViGEm when a game sends force-feedback rumble to Player's controller"""
        with self.lock:
            if player_addr in self.players:
                session = self.players[player_addr]
                session.large_motor = large_motor
                session.small_motor = small_motor

        # Transmit rumble packet to phone over UDP
        if self.sock:
            try:
                pkt = f"RUMBLE:{large_motor}:{small_motor}".encode('utf-8')
                self.sock.sendto(pkt, player_addr)
            except Exception:
                pass

        if self.ui_callback:
            self.ui_callback("RUMBLE", (large_motor, small_motor))

    def send_test_rumble(self):
        """Send test rumble to all connected devices"""
        with self.lock:
            addrs = list(self.players.keys())

        for addr in addrs:
            if self.sock:
                try:
                    self.sock.sendto(b"RUMBLE:255:255", addr)
                except Exception:
                    pass

        threading.Thread(target=self._clear_test_rumble, args=(addrs,), daemon=True).start()

    def _clear_test_rumble(self, addrs):
        time.sleep(0.4)
        for addr in addrs:
            if self.sock:
                try:
                    self.sock.sendto(b"RUMBLE:0:0", addr)
                except Exception:
                    pass

    def start(self):
        self.running = True
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(("0.0.0.0", UDP_PORT))

        threading.Thread(target=self._receive_loop, daemon=True, name="UDP-Server").start()
        threading.Thread(target=self._discovery_broadcast_loop, daemon=True, name="UDP-Discovery").start()
        threading.Thread(target=self._fps_counter_loop, daemon=True, name="FPS-Counter").start()
        threading.Thread(target=self._session_cleanup_loop, daemon=True, name="Session-Cleaner").start()

    def stop(self):
        self.running = False
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass

    def _discovery_broadcast_loop(self):
        """Broadcasts server beacon on UDP 8889 so phone finds PC automatically"""
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

    def _session_cleanup_loop(self):
        """Cleans up inactive clients after 6 seconds of silence"""
        while self.running:
            time.sleep(2.0)
            now = time.time()
            to_remove = []
            with self.lock:
                for addr, session in self.players.items():
                    if now - session.last_seen > 6.0:
                        to_remove.append((addr, session))
                for addr, session in to_remove:
                    del self.players[addr]
                    if session.gamepad:
                        try:
                            del session.gamepad
                        except Exception:
                            pass
                    print(f"[Server] Player {session.player_id} ({addr[0]}) timed out.")
                    if self.ui_callback:
                        self.ui_callback("PLAYER_LEFT", (session.player_id, addr[0]))

    def _receive_loop(self):
        while self.running:
            try:
                data, addr = self.sock.recvfrom(1024)
                if not data:
                    continue

                self.packets_count += 1
                session = self.get_or_create_player(addr)
                if not session:
                    continue

                # Handle text commands (handshake/ping)
                if data.startswith(b"HELLO") or data == b"HELLO_GAMEPAD":
                    self.sock.sendto(b"WELCOME", addr)
                    continue
                elif data.startswith(b"PING:"):
                    ts = data.split(b":")[1]
                    self.sock.sendto(b"PONG:" + ts, addr)
                    continue
                elif data == b"BYE":
                    with self.lock:
                        if addr in self.players:
                            p = self.players.pop(addr)
                            if self.ui_callback:
                                self.ui_callback("PLAYER_LEFT", (p.player_id, addr[0]))
                    continue

                # Handle binary 20-byte gamepad packets:
                # 'G', 'P', seq(2), buttons(2), lt(1), rt(1), lx(2), ly(2), rx(2), ry(2), gx(2), gy(2)
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

                    # 1. Handle Virtual Touchpad Mouse Mode (gx, gy & mouse click)
                    if gx != 0 or gy != 0:
                        send_mouse_move(gx, gy)

                    is_mouse_click = bool(buttons & BTN_MOUSE_CLICK)
                    if is_mouse_click != session.mouse_clicked:
                        session.mouse_clicked = is_mouse_click
                        send_mouse_click(True, is_mouse_click)

                    # 2. Process controller input for this player
                    self.process_input(session, buttons, lt, rt, lx, ly, rx, ry)

                    if self.ui_callback and session.player_id == 1:
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
                    print(f"[Server] Recv error: {e}")

    def process_input(self, session, buttons, lt, rt, lx, ly, rx, ry):
        if self.mode == "XBOX" and session.gamepad:
            # Map to ViGEm Xbox 360 controller
            vg = self.vg
            gamepad = session.gamepad

            btn_map = [
                (BTN_A, vg.XUSB_BUTTON.XUSB_GAMEPAD_A),
                (BTN_B, vg.XUSB_BUTTON.XUSB_GAMEPAD_B),
                (BTN_X, vg.XUSB_GAMEPAD_X if hasattr(vg, 'XUSB_GAMEPAD_X') else vg.XUSB_BUTTON.XUSB_GAMEPAD_X),
                (BTN_Y, vg.XUSB_BUTTON.XUSB_GAMEPAD_Y),
                (BTN_LB, vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER),
                (BTN_RB, vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER),
                (BTN_BACK, vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK),
                (BTN_START, vg.XUSB_BUTTON.XUSB_GAMEPAD_START),
                (BTN_GUIDE, vg.XUSB_BUTTON.XUSB_GAMEPAD_GUIDE),
                (BTN_L3, vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB),
                (BTN_R3, vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB),
                (BTN_DPAD_UP, vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP),
                (BTN_DPAD_DOWN, vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN),
                (BTN_DPAD_LEFT, vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT),
                (BTN_DPAD_RIGHT, vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT),
            ]

            for mask, vg_btn in btn_map:
                if buttons & mask:
                    gamepad.press_button(button=vg_btn)
                else:
                    gamepad.release_button(button=vg_btn)

            # Triggers (0 - 255)
            gamepad.left_trigger(value=lt)
            gamepad.right_trigger(value=rt)

            # Analog sticks (-32768 to 32767)
            gamepad.left_joystick(x_value=lx, y_value=ly)
            gamepad.right_joystick(x_value=rx, y_value=ry)

            gamepad.update()

        elif self.mode == "KB_MOUSE" and session.player_id == 1:
            # Fallback Keyboard / Mouse simulation for Player 1
            deadzone = 8000
            send_key(VK_W, ly > deadzone)
            send_key(VK_S, ly < -deadzone)
            send_key(VK_A, lx < -deadzone)
            send_key(VK_D, lx > deadzone)

            # Right stick -> Mouse Look
            if abs(rx) > deadzone or abs(ry) > deadzone:
                sens = 0.0004
                send_mouse_move(rx * sens, -ry * sens)

            # Triggers -> Left / Right Mouse click
            send_mouse_click(True, rt > 100)
            send_mouse_click(False, lt > 100)

            # Action buttons
            send_key(VK_SPACE, bool(buttons & BTN_A))      # Jump
            send_key(VK_CTRL, bool(buttons & BTN_B))       # Crouch
            send_key(VK_R, bool(buttons & BTN_X))          # Reload
            send_key(VK_E, bool(buttons & BTN_Y))          # Interact
            send_key(VK_SHIFT, bool(buttons & BTN_L3))     # Sprint
            send_key(VK_ESCAPE, bool(buttons & BTN_START)) # Pause


def get_local_ips():
    """Retrieve all local IPv4 addresses"""
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


# ------------------- Graphical User Interface -------------------

class GamepadServerGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("🕷️ Xbox Gamepad: Spider Edition PC Server")
        self.root.geometry("760x650")
        self.root.minsize(720, 600)
        self.root.configure(bg="#08090E")

        self.server = GamepadControllerServer(ui_callback=self.on_server_event)

        self.setup_ui()
        self.server.start()

        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def setup_ui(self):
        # Header banner - Spider Edition
        header = tk.Frame(self.root, bg="#12141F", pady=12)
        header.pack(fill=tk.X)

        title_lbl = tk.Label(header, text="🕷️ XBOX SPIDER EDITION PC SERVER", font=("Segoe UI", 18, "bold"), fg="#FF1744", bg="#12141F")
        title_lbl.pack()

        sub_lbl = tk.Label(header, text="Symbiote & Crimson Edition | 4-Player Co-Op | Force Feedback Rumble | Touchpad Mouse", font=("Segoe UI", 10), fg="#A0A5B5", bg="#12141F")
        sub_lbl.pack()

        # Connection Info Frame
        info_frame = tk.Frame(self.root, bg="#0E1018", padx=15, pady=12, relief=tk.RIDGE, bd=1)
        info_frame.pack(fill=tk.X, padx=15, pady=10)

        ips = get_local_ips()
        ip_str = " | ".join(ips)

        lbl_ip_title = tk.Label(info_frame, text="YOUR PC WI-FI IP ADDRESS:", font=("Segoe UI", 10, "bold"), fg="#FF1744", bg="#0E1018")
        lbl_ip_title.grid(row=0, column=0, sticky="w")

        self.lbl_ip = tk.Label(info_frame, text=f"{ip_str}  (Port: {UDP_PORT})", font=("Consolas", 14, "bold"), fg="#FFFFFF", bg="#0E1018")
        self.lbl_ip.grid(row=1, column=0, sticky="w", pady=(2, 6))

        # Status row
        status_row = tk.Frame(info_frame, bg="#0E1018")
        status_row.grid(row=2, column=0, sticky="w")

        self.lbl_players = tk.Label(status_row, text="Connected: 0 Devices (Waiting for Phone...)", font=("Segoe UI", 10, "bold"), fg="#FFB300", bg="#0E1018")
        self.lbl_players.pack(side=tk.LEFT, padx=(0, 20))

        self.lbl_rate = tk.Label(status_row, text="Stream Rate: 0 Hz", font=("Segoe UI", 10), fg="#8A99AD", bg="#0E1018")
        self.lbl_rate.pack(side=tk.LEFT)

        # Mode & ViGEm Status Frame
        mode_frame = tk.Frame(self.root, bg="#0E1018", padx=15, pady=10, relief=tk.RIDGE, bd=1)
        mode_frame.pack(fill=tk.X, padx=15, pady=(0, 10))

        if self.server.vigem_available:
            mode_text = "🔴 ViGEmBus Active: Emulating genuine Xbox 360 controller with Force Feedback Rumble"
            mode_color = "#FF1744"
        else:
            mode_text = "🟡 ViGEmBus Driver Not Installed (Running Keyboard & Mouse Fallback Mode)"
            mode_color = "#FFD600"

        self.lbl_mode = tk.Label(mode_frame, text=mode_text, font=("Segoe UI", 10, "bold"), fg=mode_color, bg="#0E1018")
        self.lbl_mode.pack(side=tk.LEFT)

        if not self.server.vigem_available:
            btn_install = tk.Button(mode_frame, text="📥 1-Click Install ViGEm Driver", bg="#FF1744", fg="#FFFFFF", font=("Segoe UI", 9, "bold"), command=self.download_vigem_driver, cursor="hand2")
            btn_install.pack(side=tk.RIGHT, padx=5)

        # Controller Visualizer Canvas
        vis_frame = tk.Frame(self.root, bg="#12141F", padx=10, pady=10, relief=tk.GROOVE, bd=1)
        vis_frame.pack(fill=tk.BOTH, expand=True, padx=15, pady=5)

        tk.Label(vis_frame, text="PLAYER 1 LIVE TELEMETRY & INPUT MONITOR", font=("Segoe UI", 10, "bold"), fg="#FF1744", bg="#12141F").pack(anchor="w")

        self.canvas = tk.Canvas(vis_frame, bg="#0A0B10", highlightthickness=0, height=220)
        self.canvas.pack(fill=tk.BOTH, expand=True, pady=6)
        self.canvas.bind("<Configure>", self.draw_controller)

        # Force Feedback Rumble Monitor
        rumble_frame = tk.Frame(self.root, bg="#0E1018", padx=15, pady=10, relief=tk.RIDGE, bd=1)
        rumble_frame.pack(fill=tk.X, padx=15, pady=5)

        tk.Label(rumble_frame, text="📳 DUAL-MOTOR FORCE FEEDBACK (RUMBLE) MONITOR", font=("Segoe UI", 10, "bold"), fg="#FF3D00", bg="#0E1018").pack(anchor="w")

        bars_frame = tk.Frame(rumble_frame, bg="#0E1018", pady=4)
        bars_frame.pack(fill=tk.X)

        tk.Label(bars_frame, text="Heavy Motor (Impacts/Venom):", font=("Segoe UI", 9), fg="#8A99AD", bg="#0E1018", width=25, anchor="w").grid(row=0, column=0)
        self.pb_heavy = ttk.Progressbar(bars_frame, length=300, maximum=255)
        self.pb_heavy.grid(row=0, column=1, sticky="ew", padx=10)

        tk.Label(bars_frame, text="Light Motor (Webs/Paces):", font=("Segoe UI", 9), fg="#8A99AD", bg="#0E1018", width=25, anchor="w").grid(row=1, column=0)
        self.pb_light = ttk.Progressbar(bars_frame, length=300, maximum=255)
        self.pb_light.grid(row=1, column=1, sticky="ew", padx=10)

        bars_frame.columnconfigure(1, weight=1)

        # Bottom Bar
        bottom_bar = tk.Frame(self.root, bg="#08090E", pady=8)
        bottom_bar.pack(fill=tk.X, padx=15)

        btn_test_vib = tk.Button(bottom_bar, text="🕷️ Test Dual Haptic Vibration", bg="#D50000", fg="white", font=("Segoe UI", 9, "bold"), command=self.server.send_test_rumble, cursor="hand2")
        btn_test_vib.pack(side=tk.LEFT)

        self.last_state = {"buttons": 0, "lt": 0, "rt": 0, "lx": 0, "ly": 0, "rx": 0, "ry": 0}

    def on_server_event(self, event_type, data):
        self.root.after(0, self._handle_event, event_type, data)

    def _handle_event(self, event_type, data):
        if event_type == "PLAYER_JOINED":
            p_id, ip = data
            count = len(self.server.players)
            self.lbl_players.config(text=f"Connected: {count} Player(s) [P{p_id}: {ip}] 🟢", fg="#00E676")
        elif event_type == "PLAYER_LEFT":
            p_id, ip = data
            count = len(self.server.players)
            self.lbl_players.config(text=f"Connected: {count} Player(s) (P{p_id} disconnected) 🔴" if count > 0 else "Connected: 0 Devices (Waiting for Phone...)", fg="#00E676" if count > 0 else "#FFB300")
        elif event_type == "FPS":
            self.lbl_rate.config(text=f"Stream Rate: {data} Hz")
        elif event_type == "RUMBLE":
            large, small = data
            self.pb_heavy["value"] = large
            self.pb_light["value"] = small
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
        lt = state.get("lt", 0)
        rt = state.get("rt", 0)

        # Left Stick
        cx_l = w * 0.22
        cy_l = h * 0.55
        r_base = min(w, h) * 0.28
        r_knob = r_base * 0.45

        canvas.create_oval(cx_l - r_base, cy_l - r_base, cx_l + r_base, cy_l + r_base, outline="#222C3D", width=2, fill="#121824")
        knob_x = cx_l + (lx / 32767.0) * (r_base - r_knob)
        knob_y = cy_l - (ly / 32767.0) * (r_base - r_knob)
        l3_active = bool(buttons & BTN_L3)
        canvas.create_oval(knob_x - r_knob, knob_y - r_knob, knob_x + r_knob, knob_y + r_knob, fill="#00E5FF" if l3_active else "#1B4D68", outline="#00E5FF", width=2)
        canvas.create_text(knob_x, knob_y, text="LS", fill="white", font=("Segoe UI", 9, "bold"))

        # Right Stick
        cx_r = w * 0.62
        cy_r = h * 0.65
        canvas.create_oval(cx_r - r_base, cy_r - r_base, cx_r + r_base, cy_r + r_base, outline="#222C3D", width=2, fill="#121824")
        knob_rx = cx_r + (rx / 32767.0) * (r_base - r_knob)
        knob_ry = cy_r - (ry / 32767.0) * (r_base - r_knob)
        r3_active = bool(buttons & BTN_R3)
        canvas.create_oval(knob_rx - r_knob, knob_ry - r_knob, knob_rx + r_knob, knob_ry + r_knob, fill="#00E5FF" if r3_active else "#1B4D68", outline="#00E5FF", width=2)
        canvas.create_text(knob_rx, knob_ry, text="RS", fill="white", font=("Segoe UI", 9, "bold"))

        # Action Buttons (A, B, X, Y)
        act_cx = w * 0.85
        act_cy = h * 0.45
        sp = r_base * 0.7
        br = r_base * 0.35

        btn_list = [
            (act_cx, act_cy + sp, "A", BTN_A, "#00E676"),
            (act_cx + sp, act_cy, "B", BTN_B, "#FF5252"),
            (act_cx - sp, act_cy, "X", BTN_X, "#448AFF"),
            (act_cx, act_cy - sp, "Y", BTN_Y, "#FFD600"),
        ]

        for bx, by, lbl, mask, col in btn_list:
            pressed = bool(buttons & mask)
            canvas.create_oval(bx - br, by - br, bx + br, by + br, fill=col if pressed else "#151B26", outline=col, width=2)
            canvas.create_text(bx, by, text=lbl, fill="#0B0E14" if pressed else col, font=("Segoe UI", 10, "bold"))

        # Triggers / Bumpers
        canvas.create_rectangle(w * 0.08, 15, w * 0.22, 45, fill="#00E5FF" if (buttons & BTN_LB) else "#151B26", outline="#222C3D")
        canvas.create_text(w * 0.15, 30, text="LB", fill="white", font=("Segoe UI", 9, "bold"))

        lt_pct = int((lt / 255.0) * 100)
        canvas.create_rectangle(w * 0.08, 52, w * 0.22, 82, fill="#1B4D68" if lt > 10 else "#151B26", outline="#00E5FF" if lt > 10 else "#222C3D")
        canvas.create_text(w * 0.15, 67, text=f"LT {lt_pct}%", fill="#00E5FF" if lt > 10 else "white", font=("Segoe UI", 9, "bold"))

        canvas.create_rectangle(w * 0.78, 15, w * 0.92, 45, fill="#00E5FF" if (buttons & BTN_RB) else "#151B26", outline="#222C3D")
        canvas.create_text(w * 0.85, 30, text="RB", fill="white", font=("Segoe UI", 9, "bold"))

        rt_pct = int((rt / 255.0) * 100)
        canvas.create_rectangle(w * 0.78, 52, w * 0.92, 82, fill="#1B4D68" if rt > 10 else "#151B26", outline="#00E5FF" if rt > 10 else "#222C3D")
        canvas.create_text(w * 0.85, 67, text=f"RT {rt_pct}%", fill="#00E5FF" if rt > 10 else "white", font=("Segoe UI", 9, "bold"))

        # Center buttons
        canvas.create_rectangle(w * 0.40, 30, w * 0.46, 55, fill="#FF1744" if (buttons & BTN_BACK) else "#151B26", outline="#3A171F")
        canvas.create_text(w * 0.43, 42, text="BACK", fill="white", font=("Segoe UI", 8, "bold"))

        # Spider Guide Insignia Button
        guide_pressed = bool(buttons & BTN_GUIDE)
        canvas.create_oval(w * 0.47, 20, w * 0.53, 65, fill="#FF1744" if guide_pressed else "#10121C", outline="#FF1744", width=2)
        canvas.create_text(w * 0.50, 42, text="🕷", fill="white", font=("Segoe UI", 16, "bold"))

        canvas.create_rectangle(w * 0.54, 30, w * 0.60, 55, fill="#FF1744" if (buttons & BTN_START) else "#151B26", outline="#3A171F")
        canvas.create_text(w * 0.57, 42, text="START", fill="white", font=("Segoe UI", 8, "bold"))

    def download_vigem_driver(self):
        url = "https://github.com/nefarius/ViGEmBus/releases/download/v1.22.0/ViGEmBus_1.22.0_x64_x86_arm64.exe"
        installer_path = os.path.join(SCRIPT_DIR, "ViGEmBus_Setup.exe")

        def run_dl():
            try:
                messagebox.showinfo("Downloading ViGEmBus", "Downloading official signed ViGEmBus driver installer...\nPlease accept the UAC prompt when it appears.")
                urllib.request.urlretrieve(url, installer_path)
                subprocess.Popen([installer_path])
                messagebox.showinfo("Installer Launched", "ViGEmBus installation wizard has started!\nAfter installation completes, restart this server to enable Xbox 360 emulation.")
            except Exception as e:
                messagebox.showerror("Download Failed", f"Could not download installer: {e}\nYou can install manually from: https://github.com/nefarius/ViGEmBus/releases")

        threading.Thread(target=run_dl, daemon=True).start()

    def on_close(self):
        self.server.stop()
        self.root.destroy()


def main():
    root = tk.Tk()
    app = GamepadServerGUI(root)
    root.mainloop()

if __name__ == "__main__":
    main()
