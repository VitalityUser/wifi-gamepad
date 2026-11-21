package com.antigravity.wifigamepad;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class NetworkClient {

    private static final String TAG = "NetworkClient";
    public static final int DEFAULT_PORT = 8888;
    public static final int DISCOVERY_PORT = 8889;

    public interface ConnectionListener {
        void onConnectionStateChanged(boolean connected, String message);
        void onPingUpdated(int pingMs);
        void onServerDiscovered(String ip, int port, String pcName);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final VibrationManager vibrationManager;
    private ConnectionListener listener;

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private int serverPort = DEFAULT_PORT;
    private String serverIp = "";

    private volatile boolean running = false;
    private volatile boolean connected = false;

    private Thread senderThread;
    private Thread receiverThread;
    private Thread discoveryThread;

    private final GamepadState currentState = new GamepadState();
    private volatile boolean stateDirty = true;

    private long lastPingSent = 0;
    private long lastPongReceived = 0;

    public NetworkClient(VibrationManager vibrationManager) {
        this.vibrationManager = vibrationManager;
    }

    public void setListener(ConnectionListener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getServerIp() {
        return serverIp;
    }

    public int getServerPort() {
        return serverPort;
    }

    public GamepadState getState() {
        return currentState;
    }

    public synchronized void markDirty() {
        stateDirty = true;
    }

    public synchronized void connect(String ip, int port) {
        disconnect();

        this.serverIp = ip;
        this.serverPort = port;
        this.running = true;

        new Thread(() -> {
            try {
                serverAddress = InetAddress.getByName(ip);
                socket = new DatagramSocket();
                socket.setSoTimeout(1000);

                // Start receiver thread
                receiverThread = new Thread(this::receiveLoop, "UDP-Receiver");
                receiverThread.start();

                // Start sender loop
                senderThread = new Thread(this::sendLoop, "UDP-Sender");
                senderThread.start();

                // Send initial handshake
                sendHandshake();

                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onConnectionStateChanged(true, "Connecting to " + ip + ":" + port + "...");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Connect error: " + e.getMessage());
                disconnect();
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onConnectionStateChanged(false, "Connection error: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    public synchronized void disconnect() {
        running = false;
        connected = false;

        if (socket != null && !socket.isClosed()) {
            try {
                // Send goodbye packet
                if (serverAddress != null) {
                    byte[] bye = "BYE".getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(bye, bye.length, serverAddress, serverPort));
                }
                socket.close();
            } catch (Exception ignored) {}
        }

        if (senderThread != null) {
            senderThread.interrupt();
            senderThread = null;
        }

        if (receiverThread != null) {
            receiverThread.interrupt();
            receiverThread = null;
        }

        if (vibrationManager != null) {
            vibrationManager.stopRumble();
        }

        mainHandler.post(() -> {
            if (listener != null) {
                listener.onConnectionStateChanged(false, "Disconnected");
            }
        });
    }

    private void sendHandshake() {
        try {
            byte[] msg = "HELLO_GAMEPAD".getBytes(StandardCharsets.UTF_8);
            DatagramPacket pkt = new DatagramPacket(msg, msg.length, serverAddress, serverPort);
            socket.send(pkt);
        } catch (Exception ignored) {}
    }

    private void sendLoop() {
        byte[] packetBytes;
        long lastStateSend = 0;
        long lastPing = 0;

        while (running && socket != null && !socket.isClosed()) {
            try {
                long now = System.currentTimeMillis();

                // Send ping every 1.5 seconds
                if (now - lastPing > 1500 && serverAddress != null) {
                    lastPing = now;
                    lastPingSent = now;
                    byte[] ping = ("PING:" + now).getBytes(StandardCharsets.UTF_8);
                    DatagramPacket pingPkt = new DatagramPacket(ping, ping.length, serverAddress, serverPort);
                    socket.send(pingPkt);
                }

                // Send gamepad state at ~80Hz (every 12ms) or when state is dirty
                if (stateDirty || (now - lastStateSend >= 15)) {
                    synchronized (currentState) {
                        packetBytes = currentState.toBinaryPacket();
                        stateDirty = false;
                    }
                    if (serverAddress != null) {
                        DatagramPacket pkt = new DatagramPacket(packetBytes, packetBytes.length, serverAddress, serverPort);
                        socket.send(pkt);
                        lastStateSend = now;
                    }
                }

                // Heartbeat timeout check: if connected but no response for 5s
                if (connected && (now - lastPongReceived > 5000) && lastPongReceived > 0) {
                    connected = false;
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onConnectionStateChanged(false, "Connection timed out");
                        }
                    });
                }

                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.w(TAG, "Send error: " + e.getMessage());
            }
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running && socket != null && !socket.isClosed()) {
            try {
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

                if (msg.startsWith("PONG") || msg.startsWith("WELCOME")) {
                    lastPongReceived = System.currentTimeMillis();
                    if (!connected) {
                        connected = true;
                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onConnectionStateChanged(true, "Connected to PC (" + serverIp + ")");
                            }
                        });
                    }

                    if (msg.startsWith("PONG:")) {
                        try {
                            long sentTime = Long.parseLong(msg.substring(5).trim());
                            int ping = (int) (System.currentTimeMillis() - sentTime);
                            mainHandler.post(() -> {
                                if (listener != null) {
                                    listener.onPingUpdated(ping);
                                }
                            });
                        } catch (Exception ignored) {}
                    }
                } else if (msg.startsWith("RUMBLE:")) {
                    // Force feedback vibration packet from PC game!
                    // Format: RUMBLE:<large_motor>:<small_motor>
                    lastPongReceived = System.currentTimeMillis();
                    String[] parts = msg.split(":");
                    if (parts.length >= 3) {
                        try {
                            int large = Integer.parseInt(parts[1].trim());
                            int small = Integer.parseInt(parts[2].trim());
                            if (vibrationManager != null) {
                                vibrationManager.onGameRumble(large, small);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // Socket read timeout, normal in loop
            } catch (Exception e) {
                if (running) {
                    Log.w(TAG, "Receive error: " + e.getMessage());
                }
                break;
            }
        }
    }

    /**
     * Listen for PC Server broadcast beacons on Wi-Fi (UDP port 8889)
     */
    public void startAutoDiscovery() {
        stopAutoDiscovery();

        discoveryThread = new Thread(() -> {
            DatagramSocket discoverySocket = null;
            try {
                discoverySocket = new DatagramSocket(DISCOVERY_PORT);
                discoverySocket.setBroadcast(true);
                discoverySocket.setSoTimeout(3000);

                byte[] buf = new byte[512];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        discoverySocket.receive(packet);
                        String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

                        // Format: WIFI_GAMEPAD_SERVER:<port>:<name>
                        if (data.startsWith("WIFI_GAMEPAD_SERVER:")) {
                            String[] parts = data.split(":");
                            int port = (parts.length > 1) ? Integer.parseInt(parts[1]) : DEFAULT_PORT;
                            String name = (parts.length > 2) ? parts[2] : "Gaming PC";
                            String ip = packet.getAddress().getHostAddress();

                            mainHandler.post(() -> {
                                if (listener != null) {
                                    listener.onServerDiscovered(ip, port, name);
                                }
                            });
                        }
                    } catch (SocketTimeoutException ignored) {
                    } catch (Exception e) {
                        break;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Discovery socket failed: " + e.getMessage());
            } finally {
                if (discoverySocket != null && !discoverySocket.isClosed()) {
                    discoverySocket.close();
                }
            }
        }, "UDP-AutoDiscovery");

        discoveryThread.start();
    }

    public void stopAutoDiscovery() {
        if (discoveryThread != null) {
            discoveryThread.interrupt();
            discoveryThread = null;
        }
    }
}
