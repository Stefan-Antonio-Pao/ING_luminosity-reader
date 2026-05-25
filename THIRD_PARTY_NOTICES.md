# Third-Party Notices

LumiRead is built on top of, and gratefully acknowledges, the work of the
following projects. License information has been verified from the official
sources listed in each entry on 2026-05-24.

This file is the canonical, machine-readable list of dependencies. The
"Acknowledgements" section of the README is a human-readable summary of the
same information.

---

## 1. On-device AI models

### Gemma 4 E2B (instruction-tuned, `.litertlm` build)
- **Used for:** local large language model that produces the reading-companion replies (default text-only mode).
- **Copyright holder:** Google LLC.
- **License:** Apache License, Version 2.0.
- **Source:** https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- **Project page:** https://ai.google.dev/gemma
- **Notes:** The model file (~2.59 GB) is **not** included in this repository or
  in the released APK. End users download it from HuggingFace under the same
  Apache-2.0 license (the App opens the HF model page in the browser; the
  user accepts the Gemma license there and downloads, then imports the file
  via the App's settings page).

### Gemma 4 E4B (instruction-tuned, multimodal `.litertlm` build)
- **Used for:** optional multimodal mode (image + text) when the user enables it
  in settings. Required to use `OcrMode.MULTIMODAL`; the smaller E2B build is
  text-only and forces `OcrMode.OCR` regardless of the user's preference.
- **Copyright holder:** Google LLC.
- **License:** Apache License, Version 2.0.
- **Source:** https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
- **Project page:** https://ai.google.dev/gemma
- **Notes:** The model file (~3.66 GB) is **not** included in this repository or
  in the released APK. Installation flow is identical to E2B above (HF
  redirect + in-app SAF import). Either E2B or E4B alone is enough to launch
  the App; having both installed lets the user switch between them.

### vits-melo-tts-zh_en (TTS acoustic model)
- **Used for:** offline bilingual (Chinese + English) speech synthesis.
- **Upstream project:** MeloTTS by MyShell.ai.
- **Copyright holder:** MyShell.ai / MeloTTS contributors.
- **License:** MIT License.
- **Source:** https://github.com/myshell-ai/MeloTTS
- **Notes:** The ONNX export used at runtime (`vits-melo-tts-zh_en`, ~189 MB) is
  hosted by the sherpa-onnx project as a pre-converted artifact and is not
  bundled in this repository. End users download it on first launch.

---

## 2. On-device inference / TTS runtimes

### LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android:0.12.0`)
- **Used for:** loading and running the `.litertlm` Gemma 4 model on Android.
- **Copyright holder:** Google LLC.
- **License:** Apache License, Version 2.0.
- **Source:** https://github.com/google-ai-edge/LiteRT-LM

### sherpa-onnx (Android AAR `sherpa-onnx-1.13.2.aar`)
- **Used for:** running the `vits-melo-tts-zh_en` model on-device.
- **Copyright holder:** Xiaomi Corporation and the k2-fsa project.
- **License:** Apache License, Version 2.0.
- **Source:** https://github.com/k2-fsa/sherpa-onnx
- **Notes:** The AAR is not published to Maven Central. Developers building
  from source download it from the project's GitHub Releases page and place
  it in `libs/` (see the README's "Build from source" section).

---

## 3. Google ML Kit (proprietary, NOT open source)

The following ML Kit packages are used for fully on-device OCR, image
labeling, and language identification:

- `com.google.mlkit:text-recognition:16.0.1` — Latin script OCR.
- `com.google.mlkit:text-recognition-chinese:16.0.1` — Chinese script OCR.
- `com.google.mlkit:image-labeling:17.0.9` — generic image classifier.
- `com.google.mlkit:language-id:17.0.6` — script/language router.

These libraries are **proprietary Google SDKs**, distributed under the
**Google APIs Terms of Service** and the **ML Kit Terms of Service**, not
under an open-source license. They are listed here for full disclosure of
the third-party software that ships with the application binary.

- **Reference:** https://developers.google.com/ml-kit
- **Terms:** https://developers.google.com/terms

---

## 4. Android / Kotlin platform libraries (Apache-2.0)

All of the following are licensed under the **Apache License, Version 2.0**.

### AndroidX & Jetpack
- `androidx.core:core-ktx` — https://developer.android.com/jetpack/androidx/releases/core
- `androidx.lifecycle:lifecycle-runtime-ktx` —
  https://developer.android.com/jetpack/androidx/releases/lifecycle
- `androidx.activity:activity-compose` —
  https://developer.android.com/jetpack/androidx/releases/activity
- `androidx.appcompat:appcompat` —
  https://developer.android.com/jetpack/androidx/releases/appcompat
- `androidx.compose:compose-bom`,
  `androidx.compose.ui:ui`,
  `androidx.compose.ui:ui-graphics`,
  `androidx.compose.ui:ui-tooling-preview`,
  `androidx.compose.ui:ui-tooling` (debug only),
  `androidx.compose.material3:material3` —
  https://developer.android.com/jetpack/compose
- `androidx.camera:camera-core`,
  `androidx.camera:camera-camera2`,
  `androidx.camera:camera-lifecycle`,
  `androidx.camera:camera-view` —
  https://developer.android.com/jetpack/androidx/releases/camera
- `androidx.datastore:datastore-preferences` —
  https://developer.android.com/jetpack/androidx/releases/datastore
- `androidx.room:room-runtime`, `androidx.room:room-ktx`,
  `androidx.room:room-compiler` (KSP at build time) —
  https://developer.android.com/jetpack/androidx/releases/room

**Copyright holder:** The Android Open Source Project.

### Kotlin / Coroutines
- `org.jetbrains.kotlin:kotlin-stdlib` (pulled in by the Kotlin Gradle plugin) —
  https://github.com/JetBrains/kotlin
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` —
  https://github.com/Kotlin/kotlinx.coroutines

**Copyright holder:** JetBrains s.r.o. and Kotlin contributors.

### Android Gradle Plugin / Kotlin Gradle Plugin
- `com.android.application` — Android Gradle Plugin, https://developer.android.com/build
- `org.jetbrains.kotlin.android` — Kotlin Gradle Plugin.

**Copyright holders:** The Android Open Source Project; JetBrains s.r.o.

---

## 5. Fonts

### ZCOOL KuaiLe / 站酷快乐体 (`res/font/zcool_kuaile.ttf`)
- **Used for:** Comic-style display font in **Kids Mode** UI for **all glyphs**
  (Latin + Simplified Chinese). The parent-mode UI continues to use the system
  default sans-serif. Verified 2026-05-25.
- **Designer / Copyright holder:** ZCOOL (站酷网) / ZCOOL KuaiLe contributors.
- **License:** SIL Open Font License, Version 1.1.
- **Upstream source:** https://github.com/google/fonts/tree/main/ofl/zcoolkuaile
  (file fetched from `ofl/zcoolkuaile/ZCOOLKuaiLe-Regular.ttf` in the official
  Google Fonts repo).
- **Notes:** Single-weight (Heavy) display font, rounded comic style covering
  Latin + Simplified Chinese (GB2312 range). Bundled inside the APK (~1.5 MB).
  Used as the sole Kids-Mode display font — its built-in Latin coverage means
  no per-glyph fallback chain is required.

---

## 6. Test-only dependencies

### JUnit 4 (`junit:junit:4.13.2`)
- **License:** Eclipse Public License 1.0.
- **Source:** https://github.com/junit-team/junit4
- **Scope:** test only — does not ship inside the released APK.

### AndroidX Test (`androidx.test.ext:junit`, `androidx.test:runner`) and `kotlinx-coroutines-test`
- **License:** Apache License, Version 2.0.
- **Sources:**
  https://developer.android.com/jetpack/androidx/releases/test
  https://github.com/Kotlin/kotlinx.coroutines
- **Scope:** instrumented / unit test only — does not ship inside the released APK.

---

## License texts

The full text of the Apache License 2.0 is reproduced in [LICENSE](./LICENSE).
The MIT License text and the EPL 1.0 text are not reproduced here; see the
links above for the canonical texts at their respective project sources.
