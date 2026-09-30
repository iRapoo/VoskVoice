# VoskVoice — voice input without Google for Wear OS 3+

[Русская версия](README.md)

[![Latest release](https://img.shields.io/github/v/release/iRapoo/VoskVoice)](https://github.com/iRapoo/VoskVoice/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/iRapoo/VoskVoice/total)](https://github.com/iRapoo/VoskVoice/releases)
[![Build](https://github.com/iRapoo/VoskVoice/actions/workflows/release.yml/badge.svg)](https://github.com/iRapoo/VoskVoice/actions/workflows/release.yml)
![Wear OS 3+](https://img.shields.io/badge/Wear%20OS-3%2B-4285F4)
[![License MIT](https://img.shields.io/github/license/iRapoo/VoskVoice)](LICENSE)

Brings **voice input** back to watches where Google voice input stopped working. Speech is
recognized **on the watch itself** with [Vosk](https://alphacephei.com/vosk/): Russian and
English, offline, without Google services. Works wherever there is a microphone button: search,
replies to messages, notes.

<p align="center">
  <a href="https://github.com/iRapoo/VoskVoice/releases/latest/download/VoskVoice.apk">
    <img src="https://img.shields.io/badge/Download-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" height="44">
  </a>
  <br>
  <sub>Latest version · <a href="https://github.com/iRapoo/VoskVoice/releases">all releases</a> · <a href="#installation">how to install</a></sub>
</p>

<p align="center">
  <img src="docs/screenshots/voice.png" width="230" alt="The Speak now screen">
  <img src="docs/screenshots/main.png" width="230" alt="Main screen">
  <img src="docs/screenshots/models.png" width="230" alt="Speech models">
</p>

<sub>Screenshots show the Russian UI; the app is also available in English.</sub>

- **Tap the mic — the watch buzzes — speak.** The phrase ends by itself after a pause; tapping
  the mic ends it earlier, swiping cancels.
- **Offline.** Audio never leaves the watch. Internet is needed only once, to download a model.
- **Russian and English**, switched with the **Русский / English** button right on the recording screen.
- **Instant start:** the model is preloaded into memory (can be turned off).

---

## Contents

- [Tested watches](#tested-watches)
- [How it works in short](#how-it-works-in-short)
- [Installation](#installation)
- [Models](#models)
- [Memory and battery](#memory-and-battery)
- [Privacy and security](#privacy-and-security)
- [Troubleshooting](#troubleshooting)
- [Reporting a problem](#reporting-a-problem)
- [Uninstalling](#uninstalling)
- [For developers](#for-developers)
- [License](#license)

---

## Tested watches

| Watch | Wear OS version | Result |
|---|---|---|
| Mobvoi TicWatch Pro 3 GPS | 3.5 (RMRB.240228.002) | voice input in apps works; voice replies to notifications not tested yet |

The project is young and has been tested on one model so far. If you try it on your watch,
[let us know](#reporting-a-problem) how it went and the model will be added to the table.

## How it works in short

1. When an app asks for voice input, VoskVoice's **Speak now** screen opens.
2. The microphone starts right away and the watch buzzes briefly. Audio goes to the **Vosk**
   recognizer (Kaldi) running on the watch CPU.
3. Text appears as you speak. After a ~1.2 s pause the phrase ends and the text is sent back to
   the app.
4. For apps that recognize speech themselves through `SpeechRecognizer`, VoskVoice also works as
   the **system recognizer**.

In detail, with measurements: [docs/HOW_IT_WORKS.md](docs/HOW_IT_WORKS.md).

## Installation

You need a watch with **Wear OS 3 or newer** (Android 11+) and a microphone, and a computer with
**ADB** ([Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools)).

### 1. Connect to the watch with ADB

On the watch:

1. **Settings → System → About → Versions**: tap **Build number** 7 times to unlock developer
   options.
2. **Settings → Developer options**: turn on **ADB debugging** and **Debug over Wi-Fi** (on some
   watches it is called **Wireless debugging**).
3. Connect the watch to **the same Wi-Fi network** as the computer and note the IP address and
   port shown under the debugging option.

On the computer:

```bash
# Wear OS 3 on TicWatch and similar: the address is shown as IP:5555
adb connect 192.168.1.50:5555

# Newer watches with "Wireless debugging" need pairing first:
#   on the watch: Wireless debugging → Pair new device → note IP:port and the code
adb pair 192.168.1.50:37123
adb connect 192.168.1.50:41234

adb devices   # the watch should be listed as "device"
```

If the watch asks to allow debugging, confirm.

> **Tip:** when the watch is connected to the phone over Bluetooth it turns Wi-Fi off to save
> power. If the connection drops, open the Wi-Fi settings on the watch and keep the screen on
> while running the commands.

### 2. Download the APK

Download [VoskVoice.apk](https://github.com/iRapoo/VoskVoice/releases/latest/download/VoskVoice.apk)
from the latest release. Every release is built by GitHub Actions straight from the source code
of the matching tag; the [build log](https://github.com/iRapoo/VoskVoice/actions) is public.

Want to build it yourself? See [For developers](#for-developers).

### 3. Install

```bash
adb install -r VoskVoice.apk
```

A new version installs over the old one with the same command. Models and settings are kept.

### 4. First launch

Open **VoskVoice** on the watch:

1. Tap **Allow microphone**.
2. Under **Models**, tap a language to download its model (Russian ~45 MB, English ~40 MB). The
   watch must be on Wi-Fi.
3. Tap **Try it** and say something. The recognized text appears under the button.

### 5. Make VoskVoice the default voice input

Tap the microphone in any app, for example in search or when replying to a message. The watch
asks which app to use: choose **VoskVoice** and **Always**.

The **System** section of the app's main screen shows what the voice input button opens now:
**VoskVoice ✓**, a chooser, or another app.

### 6. System recognizer (optional)

Some apps do not open a voice input screen but recognize speech themselves through the system
recognizer. To make them use VoskVoice too:

```bash
adb shell settings put secure voice_recognition_service xyz.quenix.voskvoice/.service.VoskRecognitionService
```

Check: the **System** section should say **System recognizer: VoskVoice ✓**.

## Models

| Language | Model | Download | On the watch |
|---|---|---|---|
| Russian | [vosk-model-small-ru-0.22](https://alphacephei.com/vosk/models) | ~45 MB | ~87 MB |
| English | [vosk-model-small-en-us-0.15](https://alphacephei.com/vosk/models) | ~40 MB | ~70 MB |

Only **small** models are used: big ones need 2+ GB of RAM, while a watch has about 300 MB free.
Small models handle short phrases and message replies well, but produce text without
punctuation. The first letter is capitalized automatically.

- **Download:** tap a language under **Models**. Models are downloaded from the Vosk project
  site (alphacephei.com).
- **Delete:** long-press a model.
- **Default language:** the **Default** button on the main screen. On the recording screen the
  **Русский / English** button switches languages, and the last one chosen becomes the default. If an app asks
  for a specific language, that one is used (if its model is downloaded).

### Keep model in memory

Loading a model takes **5–8 seconds** on a watch: the recognizer rebuilds its tables on load and
that is CPU-bound. So **Keep model in memory** is on by default: a small background service
loads the model ahead of time and keeps the system from unloading it, so voice input starts
instantly. It turns itself back on after the watch reboots.

The price is about **170 MB of RAM**. If your watch runs short of memory for other apps, turn the
mode off on the main screen: the model is then loaded on the first tap of the microphone and
unloaded after 5 minutes of inactivity. You can still talk right away in that case: audio is
recorded while the model loads and the text appears a few seconds later.

## Memory and battery

- **Memory.** With a model loaded the app takes ~150–180 MB. On a TicWatch Pro 3 (1 GB) about
  250 MB stay free.
- **Battery.** When idle the app does nothing: the service only holds the model in memory, the
  microphone and CPU are not used. The microphone works only while the Speak now screen is open.
- There are no precise battery measurements yet. If you notice a difference in battery life,
  please report it with your watch model.

## Privacy and security

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Recording your voice. Only while the Speak now screen is open or an app asked for recognition. |
| `INTERNET` | Only to download models from alphacephei.com when you tap the download button. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | The service that keeps the model in memory. It never uses the microphone. |
| `RECEIVE_BOOT_COMPLETED` | Load the model again after the watch reboots. |
| `VIBRATE` | The "you can speak" buzz. |

- **Audio and text never leave the watch.** Recognition is fully offline, audio is never stored,
  recognized text is never written to the log.
- No analytics, no ads. The only third-party libraries are
  [Vosk](https://github.com/alphacep/vosk-api) and [JNA](https://github.com/java-native-access/jna),
  which Vosk uses to call native code.
- The source code is short and commented.

### Verifying the APK

All official releases are signed with the same key. The SHA-256 certificate fingerprint will be
published here with the first release.

To check a downloaded file (`apksigner` is part of the Android SDK Build Tools):

```bash
apksigner verify --print-certs VoskVoice.apk
```

The `Signer #1 certificate SHA-256 digest` line must match the published fingerprint. If it does
not, it is not an official build.

## Troubleshooting

**Tapping the microphone opens Gboard instead of VoskVoice.**
Gboard and "Always" were chosen in the chooser. Reset it in the watch settings:
**Settings → Apps → Gboard**, the item about default actions (its name differs between watches).
If there is no such item, uninstall and reinstall VoskVoice: when a new voice input app appears,
Android shows the chooser again. Models will have to be downloaded again.

**"The Russian model is not downloaded. Tap to download".**
The model for the selected language is not downloaded. Tap the message or download the model on
the main screen.

**The model does not download.**
Make sure the watch is on Wi-Fi, not only connected to the phone over Bluetooth. If the download
was interrupted, tap the model again to start over.

**"Didn't catch that".**
There was no speech for 6 seconds. Speak closer to the watch. Tap the microphone to try again.

**Recognition makes mistakes.**
Small models are better with short phrases and common words. Speak a bit slower and clearer;
the model may not know names and rare words.

**The first time after a reboot the text appears with a delay.**
The model is still loading (5–8 seconds). Audio is already being recorded, you can talk right
away.

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE` when installing.**
The watch has a version signed with a different key (for example, one you built yourself).
Uninstall it (`adb uninstall xyz.quenix.voskvoice`) and install again. Models will have to be
downloaded again.

## Reporting a problem

Open an [issue](https://github.com/iRapoo/VoskVoice/issues/new/choose) and include:

- the watch model and Wear OS version (**Settings → System → About**);
- the app version;
- which app you used voice input in;
- what you did, what you expected and what happened.

"Works on my watch" reports are very helpful too.

## Uninstalling

```bash
adb uninstall xyz.quenix.voskvoice
```

or remove it from the app list on the watch. Models are removed with the app. If you set the
system recognizer, clear the setting:

```bash
adb shell settings delete secure voice_recognition_service
```

## For developers

### Building

```bash
git clone https://github.com/iRapoo/VoskVoice.git
cd VoskVoice
./gradlew testDebugUnitTest   # tests, no watch needed
./gradlew assembleRelease     # Windows: gradlew.bat assembleRelease
```

You need JDK 17+ and the Android SDK; the easiest way is to open the project in Android Studio.
The APK ends up in `app/build/outputs/apk/release/app-release.apk`. An APK you build yourself is
signed with your debug key and cannot update the official release.

### Project structure

```
app/src/main/java/xyz/quenix/voskvoice/
├── speech/
│   ├── Dictation.kt           # microphone → Vosk, buffering while loading, end of phrase by pause
│   ├── ModelCache.kt          # one model in memory, unloaded when idle or low on memory
│   ├── TrimPolicy.kt          # when to give the model back to the system (tested)
│   ├── ModelStore.kt          # where models live
│   ├── ModelDownloader.kt     # download and unpack on the fly
│   ├── Language.kt            # languages and model URLs
│   └── TextFormat.kt          # capital first letter, English "I" (tested)
├── service/
│   ├── VoskRecognitionService.kt  # system recognizer for SpeechRecognizer
│   └── ModelKeeperService.kt      # keeps the model in memory, restarts after reboot
├── settings/
│   └── VoiceSettings.kt       # default language, keep-in-memory mode
└── ui/
    ├── VoiceInputActivity.kt  # the Speak now screen (RECOGNIZE_SPEECH)
    └── MainActivity.kt        # models, settings, test
```

Like [WristGestures](https://github.com/iRapoo/WristGestures), the app uses only the Android
framework, without AndroidX, to stay light on watches with 1 GB of RAM.

### Debugging

```bash
./gradlew installDebug
adb logcat -s ModelCache Dictation ModelKeeper VoskRecognition ModelDownloader
```

The log shows model load time and recognition speed:

```
ModelCache: Loaded ru in 5539 ms
Dictation: Decoded 6200 ms of audio in 3344 ms
```

Open the Speak now screen without another app:

```bash
adb shell am start -n xyz.quenix.voskvoice/.ui.VoiceInputActivity -a android.speech.action.RECOGNIZE_SPEECH
```

To put a model into a **debug** build from the computer, without downloading on the watch:
unpack the model archive, rename the folder to `ru` or `en` (it must contain `am`, `conf`,
`graph`, …) and run:

```bash
adb push ru /data/local/tmp/
adb shell "chmod -R a+rX /data/local/tmp/ru && run-as xyz.quenix.voskvoice sh -c 'mkdir -p files/models && cp -r /data/local/tmp/ru files/models/' && rm -rf /data/local/tmp/ru"
```

### Documentation

- [docs/HOW_IT_WORKS.md](docs/HOW_IT_WORKS.md) — how recognition works, with measurements on a watch.
- [docs/RELEASING.md](docs/RELEASING.md) — publishing a release and signing the APK.

### Adding a language

1. Add a constant to `Language` in `speech/Language.kt`: code, label, URL of a small model from
   [alphacephei.com/vosk/models](https://alphacephei.com/vosk/models) and its size.
2. Add the language name to `res/values/strings.xml` and `res/values-ru/strings.xml` and to
   `MainActivity.languageName`.

Pull requests are welcome.

## License

[MIT](LICENSE). Free to use, modify and distribute.

[Vosk](https://github.com/alphacep/vosk-api) and the Vosk models are licensed under Apache 2.0,
[JNA](https://github.com/java-native-access/jna) under Apache 2.0 or LGPL.

Not affiliated with Alpha Cephei, Mobvoi or Google. TicWatch is a trademark of Mobvoi; Wear OS
and Gboard are trademarks of Google LLC. The app is provided "as is", without warranty.
