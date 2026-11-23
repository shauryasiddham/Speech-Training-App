# SimpleSpeak

An Android app that helps autistic people of all ages turn their ideas into clear, grammatical spoken sentences. It is built with Kotlin and Jetpack Compose. The feedback comes from a speaking coach that runs on your Mac (Ollama) or on the phone itself (Gemma 4 through LiteRT-LM). Nothing is sent to the internet.

## What's in version 2

**Speaking comes first, and practice is in real time.**
- Every exercise is answered by voice.
- Before the microphone opens there's a short think time (the caregiver sets 0–15 seconds), then it opens by itself.
- Typing appears only if the microphone fails twice in a row.

**A speaking coach on your Mac (new in 2.1).** The phone uses a model running on a Mac on the same Wi-Fi through Ollama, which is much faster than the phone. See [`mac-setup/README.md`](mac-setup/README.md). When the Mac is off, the phone's own model takes over (if downloaded). A badge always shows where the coach is running.

**Or a speaking coach that runs on the phone.**
- It uses Google's Gemma 4 E2B model through LiteRT-LM. It's free, private and works offline.
- The model is a one-time 2.6 GB download from the Caregiver tab.

| Exercise | What he does |
|---|---|
| 💬 **Answer the question** | Hears a question, thinks, then answers in full sentences |
| 🛠️ **Fix the sentence** | Says a sentence with mistakes correctly. The AI writes fresh sentences at his level and the app adds realistic mistakes |
| 🔗 **Join the ideas** | Says 2–3 short sentences as one linked sentence (because, so, although…) |
| 🔢 **Tell it in order** | Puts the steps in order, then tells them as a story (first, then, finally) |
| 🖼️ **Describe a picture** | Built-in scenes or the caregiver's photos. The coach can look at the photos |
| 🗺️ **Plan my sentence** | Who / What / Where / When / Why, then says the whole sentence |
| 🧩 **Build a sentence** | Word tiles (off by default, as a warm-up) |
| 🗣️ **Talk with your coach** | A spoken conversation on a topic he chooses, with a clearer version of each answer as he goes |

**Feedback** on each answer covers:
- grammar, with the changed words highlighted and a one-line rule
- better words to use
- how to organise his ideas
- a "level up" version of what he said, so he can hear the same idea expressed at a higher level

Praise mentions real improvements, which the app works out from his progress numbers.

**Levels 1–6** adjust automatically, and the caregiver can also set a starting level. Levels 4–6 use sentences written by the coach.

**Motivation:**
- a day streak with a 7-day calendar
- a daily goal ring
- streak badges (3, 7, 14, 30, 60 and 100 days)
- stars
- a celebration screen when a session ends

**Caregiver tab:**
- downloading and testing the coach, and choosing GPU or CPU
- fluency trends: how many words he says per answer, how long he takes to start, and how fast he speaks
- grammar areas to work on
- **his saved spoken answers** (on Android 13+, when the phone's speech service allows it)
- settings, photos and his interests

Pronunciation feedback is not included yet. Gemma 4 can take audio input, so it could be added in a later version.

## Install on a phone

1. Copy `release/SimpleSpeak-v2.1.apk` to the Android phone (for example through Google Drive, email or a USB cable). The phone needs Android 8.0 or newer and a 64-bit ARM processor. Each new version installs over the old one and keeps his progress.
2. Open the file on the phone. If Android asks, allow your browser or Files app to "install unknown apps".
3. Open **SimpleSpeak**, enter the learner's name and age group, and allow the microphone when asked.
4. Set up the Mac with [`mac-setup/README.md`](mac-setup/README.md). Then on the phone go to **Caregiver → Speaking coach → On my Mac**, tap **Find my Mac** and **Save & test**.

## Build from source

```bash
./gradlew :app:assembleRelease     # needs the Android SDK (compileSdk 36) and JDK 17+
./gradlew :app:testDebugUnitTest   # unit tests, plus screenshot tests run with Robolectric
```

Release signing reads `signing/keystore.properties`, which is not committed. Without that file the build falls back to the debug key.
