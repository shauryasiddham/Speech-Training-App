# Use a Mac as SimpleSpeak's speaking coach

The phone sends what he says to a model running on your Mac over the home Wi-Fi. An M-series Mac should answer several times faster than the model on the phone. The setup script times a test answer so you can see the speed on your Mac.

## What it uses on the Mac

| | Recommended: `gemma4:e4b-it-qat` | Lighter: `qwen3.5:4b` |
|---|---|---|
| Download | about 6 GB | about 3.3 GB |
| Memory while he practises | about 6 GB | about 3.5 GB |
| Can look at his photos | yes | yes |

- **Memory:** the model is only loaded while he's practising. Ten minutes after his last answer, Ollama frees the memory automatically, so your Mac goes back to normal.
- **Switching models:** you can install both and switch in the app (Caregiver → Speaking coach → Model on the Mac).

## Set up (about 10 minutes, once)

1. **Install Ollama.** Download it from <https://ollama.com/download>, drag it into Applications and open it once.
2. **Run the setup script.** Open **Terminal**, go to this folder, and run:

   ```bash
   bash setup-simplespeak-mac.sh
   ```

   For the lighter model, run `bash setup-simplespeak-mac.sh qwen3.5:4b` instead.

   The script:
   - lets the phone reach Ollama on your Wi-Fi (port 11434)
   - sets memory-saving options: unload after 10 idle minutes, one model and one request at a time
   - re-applies these settings after every restart
   - downloads the model and runs a quick test
   - prints your Mac's address
3. **Allow incoming connections.** If macOS asks whether Ollama may accept them, click **Allow**.
4. **Connect the phone.** In SimpleSpeak, go to **Caregiver → Speaking coach → On my Mac**. Type the address the script printed, or tap **Find my Mac**, then tap **Save & test**.

## Good to know

- **The Mac needs to be on and awake.** If it's asleep, closed or away, the app shows "The Mac is away". It then uses Claude if a key is set and "Use Claude when the Mac is off" is on. Otherwise only the built-in exercises work until the Mac is back. To stop the Mac sleeping while he practises, keep it plugged in, or run `caffeinate -d` in Terminal (press Ctrl+C to stop).
- **If the Mac's address changes** (for example after the router restarts), the app looks for the Mac on the Wi-Fi again by itself. You can also tap **Find my Mac**.
- **Privacy:** his sentences go only to your Mac, never to the internet (unless the Claude fallback is used). Anyone on your home Wi-Fi could also use Ollama on this Mac, so only do this on a private home network.
- **To undo everything:**

  ```bash
  launchctl unload ~/Library/LaunchAgents/com.simplespeak.ollama-env.plist
  ```
  ```bash
  rm ~/Library/LaunchAgents/com.simplespeak.ollama-env.plist
  ```
  ```bash
  launchctl unsetenv OLLAMA_HOST
  ```

  Then quit and reopen Ollama.
