# SimpleSpeak

An Android app that helps autistic people of all ages turn their ideas into clear, grammatical spoken sentences. It is built with Kotlin and Jetpack Compose, and Claude gives the feedback.

## What's in version 1

| Exercise | How it works | Needs Claude? |
|---|---|---|
| 🧩 **Build a sentence** | Tap word tiles into the right order, then say the sentence out loud. | No |
| 🛠️ **Fix the sentence** | See a sentence with a grammar mistake and say it correctly. | Optional (checks on the phone without it) |
| 🖼️ **Describe a picture** | Describe a built-in scene or one of the caregiver's own photos. | Yes |
| 🗺️ **Plan my sentence** | Answer Who / Did what / Where / When / Why, get one clear sentence, then say it. | Optional |
| 💬 **Help me say it** | Say a messy thought and get a clear sentence (plus a short version) to read or say. | Yes |

- **Gentle feedback:** praise comes first. Then "You said" is shown next to "A clear way to say it", with the changed words highlighted and one simple tip.
- **Adaptive levels (1–3)** for each exercise. The level goes up after 4 of the last 5 answers are right, and down after 3 of the last 4 are wrong.
- **Predictable sessions:** the same order every time, a step indicator, and a clear "finished" screen.
- **Calm design:** muted colours, large text, no flashing or sounds, and picture cues next to the words.
- **Read aloud:** text-to-speech at a speed the caregiver sets. **Record my voice** saves clips the caregiver can play back later.
- **Caregiver tab** (not locked):
  - progress for the last 7 days and each exercise's level
  - the grammar areas the learner finds hardest, plus day-by-day totals and recent answers
  - voice recordings
  - settings: name, age group, session length, which exercises are included, and reward style (stars and streak / progress bar / none)
  - the Claude API key, with a test button
  - personal photos

Everything is stored only on the phone. When Claude is used, the learner's sentences and any photos used in practice are sent to Anthropic to be checked.

## Install on a phone

1. Copy `release/SimpleSpeak-v1.0.apk` to the Android phone (for example through Google Drive, email or a USB cable). The phone needs Android 8.0 or newer.
2. Open the file on the phone. If Android asks, allow your browser or Files app to "install unknown apps".
3. Open **SimpleSpeak**, enter the learner's name and age group, and allow the microphone when asked.
4. To turn on smart feedback, go to **Caregiver → Claude**, paste an API key from https://console.anthropic.com, and tap **Test**.

## Build from source

```bash
./gradlew :app:assembleRelease     # needs the Android SDK (compileSdk 36) and JDK 17+
./gradlew :app:testDebugUnitTest   # unit tests, plus screenshot tests run with Robolectric
```

Release signing reads `signing/keystore.properties`, which is not committed. Without that file the build falls back to the debug key.
