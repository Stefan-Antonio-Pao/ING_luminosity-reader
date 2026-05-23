<div align="right">

**English** | [简体中文](./README_ZH.md)

</div>

# LumiRead (光语伴读)

> Offline, privacy-first picture-book reading companion for children, powered
> by on-device **Gemma 4 E2B**. Every spoken word, every photo, every reply
> stays on the device.

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2026%2B-3DDC84.svg)](#system-requirements)
[![Powered by](https://img.shields.io/badge/powered%20by-Gemma%204%20E2B-FF6F00.svg)](https://ai.google.dev/gemma)

---

## Table of contents

1. [Overview](#1-overview)
2. [Features](#2-features)
3. [How it works](#3-how-it-works)
4. [System requirements](#4-system-requirements)
5. [Install &amp; run (end users)](#5-install--run-end-users)
6. [Build from source (developers)](#6-build-from-source-developers)
7. [Privacy](#7-privacy)
8. [Acknowledgements](#8-acknowledgements)
9. [License](#9-license)
10. [Contributing](#10-contributing)
11. [Roadmap](#11-roadmap)

---

## 1. Overview

LumiRead is an Android app that turns any paper picture book into a warm,
patient, knowledgeable reading companion for a child — **without sending a
single byte off the device**.

**The problem.** Children's "digital companions" usually demand a screen,
a microphone live-stream to a cloud API, and an account. Parents pay with
their child's voice data, screen time, and attention span.

**Our take.** A paper book is still the best anchor for a child's
imagination. LumiRead keeps the book as the center of attention: you point
the phone at a page, and the app whispers a short, age-appropriate piece
of praise, explanation, and an inviting question — purely from on-device
inference. The phone is the storyteller's assistant, not the storyteller.

**Designed for**
- Parents and grandparents who want a co-reading helper that respects a
  child's privacy and screen time.
- Bilingual households (Chinese + English) where the adult is comfortable
  in only one of the two languages on a given page.
- Edge-AI enthusiasts who want a real, end-to-end example of a 2.5 GB-class
  language model running on a consumer phone with no network.

**Why it is interesting**
- **Fully offline.** No telemetry, no analytics, no cloud calls. Airplane
  mode is a supported runtime configuration.
- **Privacy by construction.** The only network call in the entire app is
  the one-time model download, and it can be replaced with `adb push`.
- **Edge AI that is actually usable.** First spoken word in roughly 4–6
  seconds on a Snapdragon-8-class device, fully on the GPU backend.
- **AI for Social Good.** Built for the 2026 Gemma Developer Hackathon,
  Edge AI track.

---

## 2. Features

What ships in v1.0.0 and is actually wired up end-to-end:

- **Snap-a-page reading companion.** Take a single photo of a spread,
  align it inside the on-screen frame, and the app produces a spoken
  "praise → explanation → invitation" reply tied to what is visible.
- **Bilingual output (Chinese · English).** The output language is a
  setting inside the app and is **decoupled from the system locale** —
  a Chinese-system phone can read English pages aloud, and vice versa.
- **Three age bands.** *Toddler*, *Preschool*, *Preadolescent*. Each
  band changes vocabulary, sentence length, and the TTS speed.
- **Multi-turn conversation.** Keep talking about the same page, or
  switch to a fresh one — the model carries a short rolling history.
- **Conversation without a photo.** The companion can also start a
  free-form story when no book is at hand.
- **My Learning page.** A local-only record of session count, total
  minutes, languages used, and a recent-sessions list. Nothing leaves
  the device.
- **OCR-mode setting.** Default is the two-stage pipeline
  (ML Kit on-device OCR + image labeling, then text-only Gemma 4) for
  the fastest first-word latency. An experimental *native multimodal*
  mode is available behind a clearly-labeled toggle.

---

## 3. How it works

```
 ┌──────────┐     ┌────────────────────┐     ┌────────────────────┐
 │ CameraX  │ ──▶ │ ML Kit on-device   │ ──▶ │ Prompt builder     │
 │ single   │     │ • Text recognition │     │ • Persona          │
 │ capture  │     │   (Latin + 中文)   │     │ • Age band         │
 └──────────┘     │ • Image labeling   │     │ • Language         │
                  │ • Language ID      │     │ • Rolling history  │
                  └────────────────────┘     └─────────┬──────────┘
                                                       │
                                                       ▼
                                          ┌──────────────────────┐
                                          │ Gemma 4 E2B          │
                                          │ via LiteRT-LM 0.12   │
                                          │ • GPU backend        │
                                          │ • Streaming tokens   │
                                          └──────────┬───────────┘
                                                     │
                                                     ▼
                                          ┌──────────────────────┐
                                          │ sherpa-onnx + VITS   │
                                          │ MeloTTS zh_en        │
                                          │ • per-sentence       │
                                          │ • AudioTrack stream  │
                                          └──────────────────────┘
```

**Two-stage instead of native multimodal.** End-to-end multimodal models
on a phone today still cost 10+ seconds to first word, which destroys the
"conversation" feel for a small child. We instead extract OCR text and the
top image labels offline (a couple of hundred milliseconds), and feed a
**text-only** prompt to Gemma 4. The native-multimodal path is still in
the settings as an opt-in experiment for power users.

**Layered architecture.** The Android shell (`:app`) holds CameraX, ML Kit,
LiteRT-LM, and sherpa-onnx integration. The reasoning pipeline (`:core`)
is a plain Kotlin/JVM module behind small interfaces — `LlmEngine`,
`TtsEngine`, `OcrService`, `ImageLabelService` — each of which has a Fake
implementation that lets the whole UI flow run in a JVM unit test without
any of the heavy native libraries loaded.

---

## 4. System requirements

| Item | Minimum | Recommended |
|---|---|---|
| Android version | Android 8.0 (API 26) | Android 12+ (API 31+) |
| RAM | 6 GB | 8 GB |
| SoC | Snapdragon 7 Gen / Tensor G2 class | Snapdragon 8 Gen 2 / 3 / Tensor G3+ |
| GPU backend | OpenCL-capable Adreno / Mali / Xclipse | same |
| Free storage | **≥ 4 GB** after install (model + TTS data) | 5 GB+ |
| Network | Wi-Fi for the one-time model download | same |

> The CPU backend is available as a fallback for development, but at
> 4–5 tok/s it is not usable as a real co-reading experience.

---

## 5. Install &amp; run (end users)

### 5.1 Install the APK

1. Download `app-release.apk` from the project's [GitHub Releases page](https://github.com/LagrangeNSS/LumiRead/releases).
2. Verify the SHA-256 against the value printed in the Release notes.
3. Install on your phone (Settings → Install unknown apps may need a
   one-time permission). The APK is **roughly tens of MB**; it does
   **not** contain the Gemma 4 model.

### 5.2 First launch — download the model

On first launch the app guides you through downloading the **Gemma 4 E2B**
weights and the **MeloTTS** acoustic model.

- The Gemma 4 file (`gemma-4-E2B-it.litertlm`) is **~2.59 GB**. Use Wi-Fi.
  This is a one-time download; the file is kept under the app's external
  files directory and is never uploaded anywhere.
- The TTS model (`vits-melo-tts-zh_en`) is **~189 MB**.
- After the download, no further network access is needed. You can put
  the phone in airplane mode and keep using the app.

### 5.3 Side-loading the model (reviewers, evaluators, slow networks)

If you prefer not to download in-app, you can push the files via `adb`:

```bash
# Replace the source paths with wherever you downloaded the files.
adb push gemma-4-E2B-it.litertlm \
    /sdcard/Android/data/com.lumiread/files/
adb push vits-melo-tts-zh_en \
    /sdcard/Android/data/com.lumiread/files/
```

The app detects the files on startup and skips the download step.

---

## 6. Build from source (developers)

### 6.1 Toolchain

| Tool | Version |
|---|---|
| JDK | 17 (`JAVA_HOME` must point at a JDK 17 install) |
| Android Gradle Plugin | 9.2.1 (declared in `gradle/libs.versions.toml`) |
| Kotlin | 2.3.21 |
| Android SDK | platform 36, build-tools 36.x |
| Gradle | wrapper-managed (`./gradlew`) |

### 6.2 Clone &amp; configure

```bash
git clone https://github.com/LagrangeNSS/LumiRead.git
cd LumiRead

# Tell Gradle where your Android SDK is — this file is .gitignored.
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### 6.3 Get the sherpa-onnx AAR

`sherpa-onnx` is not published to Maven Central; the project loads it
from a local `libs/` directory.

1. Go to https://github.com/k2-fsa/sherpa-onnx/releases.
2. Download `sherpa-onnx-1.13.2.aar`.
3. Place it at `libs/sherpa-onnx-1.13.2.aar` (create the `libs/` folder
   in the repository root if it does not exist).

### 6.4 Get the models

These files are **not** in the repository, and they are **not** in the
release APK either — they live entirely on the end user's device.

- **Gemma 4 E2B (LiteRT-LM build).** Hugging Face repository
  [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm).
  You must accept the Gemma terms and the Apache-2.0 model license on
  the model page before the file becomes downloadable.

  ```bash
  pip install -U "huggingface_hub[cli]"
  huggingface-cli download litert-community/gemma-4-E2B-it-litert-lm \
      gemma-4-E2B-it.litertlm --local-dir ./models/
  ```

- **MeloTTS Chinese + English (`vits-melo-tts-zh_en`).** Available from
  the sherpa-onnx pre-trained model index:
  https://github.com/k2-fsa/sherpa-onnx/releases (look for the
  `vits-melo-tts-zh_en` archive). Extract under `./models/`.

For end-user testing without an in-app download, `adb push` the files to
`/sdcard/Android/data/com.lumiread/files/` as shown in §5.3.

### 6.5 Build &amp; run

```bash
# Debug build, install on a connected device:
./gradlew :app:installDebug

# Release APK (unsigned by default; you'll need your own keystore to ship):
./gradlew :app:assembleRelease

# Pure-JVM tests for the reasoning pipeline (no Android, no model needed):
./gradlew :core:test

# Tracer-bullet tests against the real LiteRT-LM and sherpa-onnx
# (requires a physical device with the models already in place):
./gradlew :app:connectedDebugAndroidTest
```

---

## 7. Privacy

- **No network requests at runtime.** The app contacts a network only
  during the optional first-launch model download. Once the model is on
  disk, the app works fully in airplane mode.
- **No telemetry, no analytics, no crash reporting backends.** No
  Firebase, no Crashlytics, no third-party SDK that phones home.
- **No accounts, no sign-in.**
- **No camera data leaves the device.** Photos taken inside the app are
  written to the app's private cache directory and deleted at the end of
  the session.
- **No audio recording.** LumiRead listens through the screen, not
  through a microphone — it only speaks; it never records the child.
- **All learning records are local.** The "My Learning" page reads from
  an on-device Room database that the app never uploads.

The ML Kit models that handle OCR and image labeling run **entirely
on-device** (`bundled` variants, no Google Play Services call-out).

---

## 8. Acknowledgements

LumiRead would not exist without the work of these projects. Please see
[THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) for the complete list
including license texts and source links.

**Open-source models**
- [Gemma 4 E2B](https://ai.google.dev/gemma) by Google — Apache-2.0.
  The instruction-tuned `.litertlm` build is hosted at
  [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm).
- [MeloTTS](https://github.com/myshell-ai/MeloTTS) by MyShell.ai — MIT.
  The `vits-melo-tts-zh_en` acoustic model used at runtime is derived
  from this project.

**Open-source frameworks**
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) by Google —
  Apache-2.0. The on-device LLM runtime.
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) by Xiaomi /
  k2-fsa — Apache-2.0. The on-device TTS runtime.
- [AndroidX / Jetpack Compose / CameraX](https://developer.android.com/jetpack) —
  Apache-2.0. UI, camera, and persistence.
- [Kotlin](https://kotlinlang.org/) and
  [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) by
  JetBrains — Apache-2.0.

**Proprietary SDK used in the build (full disclosure)**
- [Google ML Kit](https://developers.google.com/ml-kit) — proprietary
  Google SDK, used for on-device OCR (Latin + Chinese), language
  identification, and image labeling. Not open-source; listed here for
  honesty about what ships in the APK.

A heartfelt thank-you to Google for releasing Gemma 4 under a real
open-source license, and to the LiteRT-LM team for making it usable on
real consumer phones inside the 6-month window of this hackathon.

---

## 9. License

The LumiRead source code in this repository is licensed under
**Apache License 2.0** — see [LICENSE](./LICENSE) and [NOTICE](./NOTICE).

The models, frameworks, and SDKs used at runtime are licensed
**separately by their respective copyright holders**. See
[THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) and §8 above.

> **Honest statement about the APK.** The released `app-release.apk`
> **does not** contain the Gemma 4 model weights. End users download
> the model on first launch under its own Apache-2.0 license, directly
> from the upstream model host.

---

## 10. Contributing

This is an early hackathon release. Issues and pull requests are welcome.

Before submitting:
- Run `./gradlew :core:test` and `./gradlew :app:assembleDebug` locally.
- Keep the `:core` module free of Android UI / framework dependencies —
  it must remain a plain Kotlin/JVM module so the reasoning pipeline can
  be unit-tested without an emulator.

---

## 11. Roadmap

Things we would like to build next, but **have not built yet**. None of
the items below are present in v1.0.0.

- **Windows port** via UWP, sharing the `:core` pipeline.
- **Per-page bookmark / dialog history** the child can revisit later.
- **Parent-side weekly summary** generated locally and exported as PDF.
- **Custom voices** trained from a parent's own short voice sample
  (research direction, no commitment).
- **Tablet-optimized UI** with a side-by-side book + chat layout.

---

<sub>Built for the 2026 Gemma Developer Hackathon — Edge AI track.
Made with care, for paper books and the small humans who love them.</sub>
