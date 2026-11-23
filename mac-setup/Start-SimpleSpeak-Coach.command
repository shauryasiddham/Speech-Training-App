#!/bin/bash
# Double-click to start the SimpleSpeak coach on this Mac.
open -a Ollama
echo "Starting the SimpleSpeak coach…"
for i in $(seq 1 20); do curl -s http://127.0.0.1:11434/api/version >/dev/null 2>&1 && break; sleep 1; done
if curl -s http://127.0.0.1:11434/api/version >/dev/null 2>&1; then
  echo "✓ The coach is on. The phone will connect in a few seconds."
else
  echo "Ollama did not start. Open the Ollama app from Applications."
fi
sleep 3
osascript -e 'tell application "Terminal" to close front window' >/dev/null 2>&1 &
