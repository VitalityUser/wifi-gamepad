#!/bin/bash
cd "$(dirname "$0")"

echo "========================================================"
echo "    ARACHNIDPAD MACOS COMPANION SERVER (MACBOOK)"
echo "========================================================"
echo ""

# Find available python3 on macOS
if command -v python3 &>/dev/null; then
    python3 server_mac.py
elif command -v python &>/dev/null; then
    python server_mac.py
else
    echo "[ERROR] Python 3 not found."
    echo "Please install Python 3 from https://www.python.org/downloads/ or via Homebrew: brew install python"
    read -p "Press enter to exit..."
fi
