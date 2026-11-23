#!/bin/bash
# SimpleSpeak – set up this Mac as the speaking coach for the phone app.
# Usage:  bash setup-simplespeak-mac.sh            (installs the recommended model)
#         bash setup-simplespeak-mac.sh qwen3.5:4b (or choose another Ollama model)
set -euo pipefail

MODEL="${1:-gemma4:e4b-it-qat}"
AGENT="$HOME/Library/LaunchAgents/com.simplespeak.ollama-env.plist"

echo "== SimpleSpeak Mac setup =="

# 1. Ollama must be installed.
if ! command -v ollama >/dev/null 2>&1 && [ ! -d "/Applications/Ollama.app" ]; then
  echo "Ollama is not installed yet."
  echo "Download it from https://ollama.com/download, drag it to Applications, open it once, then run this script again."
  exit 1
fi
OLLAMA_BIN="$(command -v ollama || echo /Applications/Ollama.app/Contents/Resources/ollama)"

# 2. Let the phone reach Ollama on the home Wi-Fi, and keep memory use low:
#    - listen on the local network (port 11434)
#    - free the model's memory after 10 idle minutes
#    - only one model loaded, one request at a time
#    A small login item re-applies these settings after every restart.
mkdir -p "$HOME/Library/LaunchAgents"
cat > "$AGENT" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>com.simplespeak.ollama-env</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/sh</string><string>-c</string>
    <string>launchctl setenv OLLAMA_HOST 0.0.0.0:11434; launchctl setenv OLLAMA_KEEP_ALIVE 10m; launchctl setenv OLLAMA_MAX_LOADED_MODELS 1; launchctl setenv OLLAMA_NUM_PARALLEL 1</string>
  </array>
  <key>RunAtLoad</key><true/>
</dict>
</plist>
PLIST
launchctl unload "$AGENT" >/dev/null 2>&1 || true
launchctl load "$AGENT"
launchctl setenv OLLAMA_HOST 0.0.0.0:11434
launchctl setenv OLLAMA_KEEP_ALIVE 10m
launchctl setenv OLLAMA_MAX_LOADED_MODELS 1
launchctl setenv OLLAMA_NUM_PARALLEL 1
echo "✓ Network and memory settings saved"

# 3. Restart Ollama so it picks up the settings.
osascript -e 'quit app "Ollama"' >/dev/null 2>&1 || true
sleep 2
open -a Ollama
for i in $(seq 1 30); do
  curl -s http://127.0.0.1:11434/api/version >/dev/null 2>&1 && break
  sleep 1
done
curl -s http://127.0.0.1:11434/api/version >/dev/null || { echo "Ollama did not start. Open the Ollama app and run this script again."; exit 1; }
echo "✓ Ollama is running"

# 4. Download the model (one time).
echo "Downloading $MODEL (this can take a few minutes)…"
"$OLLAMA_BIN" pull "$MODEL"
echo "✓ Model ready"

# 5. Quick test, the same way the app asks.
echo "Testing…"
START=$(date +%s)
curl -s http://127.0.0.1:11434/api/chat -d "{\"model\":\"$MODEL\",\"stream\":false,\"think\":false,\"keep_alive\":\"10m\",
  \"format\":{\"type\":\"object\",\"properties\":{\"improved\":{\"type\":\"string\"}},\"required\":[\"improved\"]},
  \"messages\":[{\"role\":\"user\",\"content\":\"Fix the grammar: yesterday I go to the shop. Reply in JSON.\"}]}" \
  | grep -o '"content":"[^}]*}' | head -1 | sed 's/^/  Answer: /'
echo "  Took $(( $(date +%s) - START )) seconds (the first answer includes loading the model)."

# 6. Show the address to type into the app.
IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || true)"
echo
echo "== Done =="
if [ -n "$IP" ]; then
  echo "In SimpleSpeak on the phone: Caregiver → Speaking coach → On my Mac"
  echo "Type this address:   $IP"
  echo "(or tap “Find my Mac”). The phone must be on the same Wi-Fi."
else
  echo "Couldn't find this Mac's Wi-Fi address. Use “Find my Mac” in the app."
fi
echo
echo "If macOS asks whether Ollama may accept incoming network connections, click Allow."
