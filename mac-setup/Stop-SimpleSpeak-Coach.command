#!/bin/bash
# Double-click to stop the SimpleSpeak coach and free all of its memory.
echo "Stopping the SimpleSpeak coach…"
osascript -e 'quit app "Ollama"' >/dev/null 2>&1
sleep 2
pkill -x ollama >/dev/null 2>&1   # anything still running in the background
pkill -f "ollama runner" >/dev/null 2>&1
if curl -s http://127.0.0.1:11434/api/version >/dev/null 2>&1; then
  echo "Something is still running. Click the llama in the menu bar and choose Quit Ollama."
else
  echo "✓ The coach is off and its memory is free. The phone will show the coach as off."
fi
sleep 3
osascript -e 'tell application "Terminal" to close front window' >/dev/null 2>&1 &
