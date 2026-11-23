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

## Turning the coach on and off

**Stop the coach** and free all of its memory, using either:
- the menu bar: click the llama, then choose **Quit Ollama**
- Terminal:
  ```bash
  osascript -e 'quit app "Ollama"'
  ```

**Start it again** by opening **Ollama** from Applications, or in Terminal:
```bash
open -a Ollama
```

It also starts by itself the next time you log in.

For one-click shortcuts, copy `Start-SimpleSpeak-Coach.command` and `Stop-SimpleSpeak-Coach.command` to your Desktop and double-click them.

The phone's badge updates within about 20 seconds: **Coach: Mac** when the Mac is on, and **Coach: Phone · Mac off** or **Coach: off** when it isn't.

## Good to know

- **The Mac needs to be on and awake.** If it's off or asleep, the app automatically uses the phone's own model, if it's downloaded and **Use the phone when the Mac is off** is on. This is slower but keeps working. The badge on the phone shows **Coach: Phone · Mac off**. Without the phone model, only the built-in exercises work until the Mac is back. To stop the Mac sleeping while he practises, keep it plugged in, or run `caffeinate -d` in Terminal (press Ctrl+C to stop).
- **After a restart,** log in to the Mac. About 10 seconds later the login item restarts Ollama with the right settings. Nothing runs while the Mac is on the login screen.
- **If the Mac's address changes** (for example after the router restarts), the app looks for the Mac on the Wi-Fi again by itself. You can also tap **Find my Mac**.
- **Privacy:** his sentences go only to your Mac or stay on his phone, never to the internet. Anyone on your home Wi-Fi could also use Ollama on this Mac, so only do this on a private home network.
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
