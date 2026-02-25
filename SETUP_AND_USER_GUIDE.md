# MindChat – Setup & User Guide

**Mental Health Chatbot with 3-Architecture Benchmark on Android**
Based on [Gemma-on-Android](https://github.com/NSTiwari/Gemma-on-Android) · Target device: Nokia 5.4 (6 GB RAM)

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Clone & Initialize the Repository](#2-clone--initialize-the-repository)
3. [Download the GGUF Model](#3-download-the-gguf-model)
4. [Install & Configure Android Studio](#4-install--configure-android-studio)
5. [Build the APK](#5-build-the-apk)
6. [Install on Device](#6-install-on-device)
7. [Push the Model to the Device](#7-push-the-model-to-the-device)
8. [User Guide – Using the App](#8-user-guide--using-the-app)
9. [Benchmarking Guide](#9-benchmarking-guide)
10. [Pulling Benchmark Results](#10-pulling-benchmark-results)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Prerequisites

Install the following on **Ubuntu 22.04**:

### 1.1 Java Development Kit (JDK 17)

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
java -version   # should print: openjdk 17.x.x
```

### 1.2 Android command-line tools & ADB

```bash
sudo apt install -y android-tools-adb
adb version     # should print: Android Debug Bridge version x.x.x
```

### 1.3 Android Studio (Hedgehog 2023.1.1 or later)

Download from: https://developer.android.com/studio

```bash
# Extract and install
tar -xzf android-studio-*.tar.gz -C ~/
~/android-studio/bin/studio.sh &
```

Follow the setup wizard. When prompted, install:
- **Android SDK** (API 34)
- **Android SDK Build-Tools** (34.x)
- **Android NDK** (26.1.10909125 or later)
- **CMake** (3.22.1 or later)

To verify NDK & CMake in Android Studio:
`File → Settings → Appearance & Behavior → System Settings → Android SDK → SDK Tools tab`
Check: ✓ NDK (Side by side) · ✓ CMake

### 1.4 Git

```bash
sudo apt install -y git
git --version
```

---

## 2. Clone & Initialize the Repository

### 2.1 Clone the repository

```bash
git clone https://github.com/<your-fork>/Gemma-on-Android.git
cd Gemma-on-Android
```

### 2.2 Initialize the llama.cpp submodule

The GGUF inference engine (llama.cpp) must be added as a submodule.
Run this **once** after cloning:

```bash
cd SciGemma_Android_App/app/src/main/cpp

git submodule add https://github.com/ggerganov/llama.cpp llama.cpp
git submodule update --init --recursive

cd -   # return to repo root
```

Verify the submodule directory exists and is not empty:

```bash
ls SciGemma_Android_App/app/src/main/cpp/llama.cpp/
# Should list: CMakeLists.txt  include/  src/  ggml/  ...
```

> **If you already cloned with `--recursive`**, run only:
> ```bash
> git submodule update --init --recursive
> ```

---

## 3. Download the GGUF Model

The app uses **Gemma 2B-IT in GGUF format** (Q4_K_M quantization, ~1.5 GB).

### Option A — Download from Hugging Face (recommended)

```bash
# Install huggingface-hub CLI
pip install huggingface-hub

# Download the model (requires HuggingFace account + Gemma access approval)
huggingface-cli download google/gemma-2b-it-GGUF \
    gemma-2b-it-q4_k_m.gguf \
    --local-dir ~/Downloads/
```

> **Get access**: Visit https://huggingface.co/google/gemma-2b-it and accept the license.

### Option B — Use llama.cpp to quantize your own model

If you already have the Gemma 2B-IT safetensors from Hugging Face:

```bash
# Build llama.cpp tools
cd SciGemma_Android_App/app/src/main/cpp/llama.cpp
mkdir build && cd build && cmake .. && make -j4

# Convert safetensors → GGUF
python3 convert_hf_to_gguf.py /path/to/gemma-2b-it/ \
    --outfile ~/Downloads/gemma-2b-it.gguf

# Quantize to Q4_K_M
./bin/llama-quantize ~/Downloads/gemma-2b-it.gguf \
    ~/Downloads/gemma-2b-it-q4_k_m.gguf Q4_K_M
cd -
```

The final file should be named **`gemma-2b-it-q4_k_m.gguf`** (~1.5 GB).

---

## 4. Install & Configure Android Studio

### 4.1 Open the project

1. Launch Android Studio
2. **File → Open** → select `SciGemma_Android_App/` folder
3. Wait for Gradle sync to complete (may take 5–10 minutes on first run)

### 4.2 Verify NDK & CMake paths

`File → Project Structure → SDK Location`:
- **Android SDK location**: e.g. `/home/<user>/Android/Sdk`
- **Android NDK location**: e.g. `/home/<user>/Android/Sdk/ndk/26.1.10909125`

If NDK is missing, install it:
`Tools → SDK Manager → SDK Tools → NDK (Side by side)` → Apply

### 4.3 Verify native build configuration

Open `SciGemma_Android_App/app/build.gradle.kts` and confirm:

```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
defaultConfig {
    ndk { abiFilters += listOf("arm64-v8a") }
}
```

This tells Android Studio to compile llama.cpp for the Nokia 5.4's ARM64 processor.

---

## 5. Build the APK

### Option A — Android Studio GUI

1. **Build → Make Project** (Ctrl+F9)
   - First build compiles llama.cpp from source (~10–20 minutes)
   - Subsequent builds are incremental (~1–2 minutes)

2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**

The APK is output at:
```
SciGemma_Android_App/app/build/outputs/apk/debug/app-debug.apk
```

### Option B — Command line (Gradle)

```bash
cd SciGemma_Android_App

# Build debug APK
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

Expected build output (first run):
```
> Task :app:buildCMakeDebug[arm64-v8a]
> Task :app:assembleDebug
BUILD SUCCESSFUL in 18m 32s
```

---

## 6. Install on Device

### 6.1 Enable USB debugging on Nokia 5.4

1. **Settings → About phone → tap "Build number" 7 times** (enables Developer Options)
2. **Settings → System → Developer options → USB debugging → ON**
3. Connect Nokia 5.4 to Ubuntu via USB cable
4. On the phone: tap **"Allow USB debugging"** when prompted

### 6.2 Verify device is detected

```bash
adb devices
# Expected:
# List of devices attached
# XXXXXXXX    device
```

If it shows `unauthorized`, unlock your phone and tap **"Allow"** on the dialog.

### 6.3 Install the APK

```bash
adb install -r SciGemma_Android_App/app/build/outputs/apk/debug/app-debug.apk
# Expected: Performing Streamed Install
#           Success
```

Or via Android Studio: click the **▶ Run** button (Shift+F10) with your device selected.

---

## 7. Push the Model to the Device

The GGUF model must be placed at `/data/local/tmp/llm/` on the Nokia 5.4.

```bash
# Create the directory on device
adb shell mkdir -p /data/local/tmp/llm

# Push the model (Nokia 5.4 must be unlocked, ~1.5 GB, takes 2-5 minutes)
adb push ~/Downloads/gemma-2b-it-q4_k_m.gguf /data/local/tmp/llm/

# Verify the file arrived
adb shell ls -lh /data/local/tmp/llm/
# Expected: -rw-rw-rw- 1 shell shell 1.5G ... gemma-2b-it-q4_k_m.gguf
```

> **Important**: The model filename in the code is `gemma-2b-it-q4_k_m.gguf`.
> If your file has a different name, update `MODEL_PATH` in:
> `app/src/main/java/com/example/scigemma/llm/LlamaCppModel.kt` (line ~95)

---

## 8. User Guide – Using the App

### 8.1 App Startup

1. Open **MindChat** on the Nokia 5.4
2. The **Architecture Selection** screen appears

```
┌─────────────────────────────────────────┐
│         Select Architecture             │
│                                         │
│  ┌──────────────────────────────────┐   │
│  │ Multitask                        │   │
│  │ Single LLM call handles all tasks│   │
│  │         [Chat with Multitask]    │   │
│  └──────────────────────────────────┘   │
│  ┌──────────────────────────────────┐   │
│  │ Separate                         │   │
│  │ Independent specialized modules  │   │
│  │         [Chat with Separate]     │   │
│  └──────────────────────────────────┘   │
│  ┌──────────────────────────────────┐   │
│  └──────────────────────────────────┘   │
│   [Run Full Benchmark (all 3 archs)]    │
└─────────────────────────────────────────┘
```

### 8.2 Architecture Descriptions

| Architecture | How it works | Best for |
|---|---|---|
| **Multitask** | Single LLM call: model detects emergency, anonymizes PII, and responds all in one structured prompt | Testing LLM multitask capability |
| **Separate** | Three independent modules: keyword emergency detector + regex anonymizer + LLM conversation | Most reliable PII removal |
| **Pipeline** | Sequential chain: emergency check → LLM response → anonymization; cloud path adds summarization | Clearest stage-by-stage timing |

### 8.3 Loading Screen

After selecting an architecture, a loading screen appears:

```
  ● Loading model…
  Architecture: Separate
  /data/local/tmp/llm/gemma-2b-it-q4_k_m.gguf
```

Loading takes **20–60 seconds** on Nokia 5.4 (model is ~1.5 GB).
If loading fails, the error message shows the path; verify the model was pushed correctly.

### 8.4 Chat Screen

```
┌─────────────────────────────────────────┐
│ MindChat – Chat         Architecture: Separate │
│─────────────────────────────────────────│
│                                         │
│  [User bubble] I've been feeling anxious│
│  lately. What should I do?              │
│                                         │
│  [Model bubble] It's completely normal  │
│  to feel anxious. Try deep breathing... │
│                                         │
│─────────────────────────────────────────│
│  2340 ms | 4.2 tok/s | 1.8 MB | 0/1/2338 ms │
│─────────────────────────────────────────│
│  [Message input field   ] [☁] [→]      │
└─────────────────────────────────────────┘
```

**Input area buttons:**
| Button | Icon | Action |
|--------|------|--------|
| Send | → (arrow) | Send message to local Gemma model |
| Cloud | ☁ (cloud) | Anonymize + summarize, then show cloud payload |

**Metrics strip** (bottom bar, updated after each response):
- `2340 ms` — total response latency
- `4.2 tok/s` — generation speed
- `1.8 MB` — RAM usage delta
- `0/1/2338 ms` — emergency detection / anonymization / LLM inference time

### 8.5 Sending a Message

1. Tap the text field and type your message
2. Tap the **→ send button** (or press Enter)
3. A loading spinner appears in the model bubble
4. Tokens appear in real-time as the model generates them
5. The metrics strip updates when generation is complete

### 8.6 Emergency Detection

If your message contains crisis-related content (suicidal ideation, self-harm, etc.),
the app automatically shows a crisis support dialog:

```
┌──────────────────────────────────────┐
│  Crisis Support Available            │
│                                      │
│  It seems you may be going through   │
│  a difficult time. Please reach out: │
│                                      │
│  • Indonesia — Into The Light: 119 ext 8  │
│  • International — findahelpline.com │
│  • USA — 988 Suicide & Crisis Lifeline│
│  • UK — Samaritans: 116 123          │
│                                      │
│  You are not alone. Help is available│
│                                      │
│              [OK, I understand]      │
└──────────────────────────────────────┘
```

The model's response is still generated and shown alongside the dialog.

> **Note**: For Multitask architecture, emergency detection is done by the LLM.
> For Separate and Pipeline, it uses keyword matching (faster, more reliable).

### 8.7 Cloud Upload (Mock Mode)

If you are not satisfied with the local model's response:

1. Tap the **☁ cloud button**
2. The app runs **anonymization** (replaces PII with `[NAME]`, `[PHONE]`, `[EMAIL]`, etc.)
3. If the conversation is long (>512 tokens), the app **summarizes** it first using Gemma
4. A preview dialog shows the payload that would be sent to a cloud API:

```
┌──────────────────────────────────────┐
│  Cloud Upload Payload (Mock)         │
│                                      │
│  Architecture: Separate              │
│  Tokens (original): 487              │
│  Tokens (final): 312                 │
│  Summarized: No                      │
│  Anonymization: 3 ms                 │
│                                      │
│  Payload preview:                    │
│  {                                   │
│    "anonymizedConversation": "...",  │
│    "summary": "...",                 │
│    "architectureUsed": "Separate",   │
│    "timestamp": 1234567890,          │
│    ...                               │
│  }                                   │
│                                      │
│  ℹ No data is transmitted.          │
│              [Close]                 │
└──────────────────────────────────────┘
```

> **This is mock mode** — no data is actually sent to any server.
> PII has been removed before the preview is shown.

---

## 9. Benchmarking Guide

### 9.1 Start the Benchmark

From the Architecture Selection screen, tap **"Run Full Benchmark"**.

The benchmark runs all 3 architectures through the **20-sample synthetic dataset**
(10 normal conversations + 10 emergency conversations, manually crafted — no real user data).

### 9.2 Benchmark Progress Screen

```
  Architecture Benchmark

  ████████████░░░░░░░░  67%

  [SEP] Sample 14/20: I don't want to live anymore…
```

Full benchmark takes **~30–90 minutes** on Nokia 5.4 (20 samples × 3 architectures × ~30s/sample).

For faster testing during development, tap **"Quick Benchmark (10 samples)"** instead.

### 9.3 Results Screen

After all architectures complete, the results comparison table is shown:

```
  Benchmark Results
  Best Architecture: Separate  (score 0.847)

  ┌────────────────────┬──────────┬──────────┬──────────┐
  │ Metric             │Multitask │ Separate │ Pipeline │
  ├────────────────────┼──────────┼──────────┼──────────┤
  │ Emerg. Recall ↑    │  80.0%   │  100.0%  │  100.0%  │
  │ Emerg. Precision ↑ │  88.9%   │  100.0%  │  100.0%  │
  │ Emerg. F1 ↑        │  84.2%   │  100.0%  │  100.0%  │
  ├────────────────────┼──────────┼──────────┼──────────┤
  │ Anon. Rate ↑       │  72.3%   │  91.5%   │  91.5%   │
  │ PII Leakage ↓      │  27.7%   │   8.5%   │   8.5%   │
  ├────────────────────┼──────────┼──────────┼──────────┤
  │ Mean Latency ↓     │ 4820 ms  │ 3210 ms  │ 3245 ms  │
  │ Tokens/sec ↑       │  3.8 t/s │  5.1 t/s │  5.0 t/s │
  ├────────────────────┼──────────┼──────────┼──────────┤
  │ Mean Peak RAM ↓    │  1.8 MB  │  1.6 MB  │  1.6 MB  │
  ├────────────────────┼──────────┼──────────┼──────────┤
  │ Overall Score ↑    │  0.721   │  0.847   │  0.843   │
  └────────────────────┴──────────┴──────────┴──────────┘

  Results saved to:
  /sdcard/Android/data/com.example.scigemma/files/benchmark_results/
```

> **Overall score formula**: 40% × Emergency Recall + 30% × Anonymization Rate + 30% × Latency Score
> (designed to prioritize safety, then privacy, then performance)

---

## 10. Pulling Benchmark Results

After running the benchmark, pull the result files to your Ubuntu machine:

```bash
adb pull \
    /sdcard/Android/data/com.example.scigemma/files/benchmark_results/ \
    ./benchmark_results/

ls benchmark_results/
# raw_20240301_143022.json      ← full per-sample results (all 60 samples)
# summary_20240301_143022.json  ← aggregated stats per architecture
# results_20240301_143022.csv   ← flat CSV for Excel / Google Sheets
# report_20240301_143022.md     ← Markdown comparison table (paste into thesis)
```

### File contents:

| File | Contents | Use for |
|------|----------|---------|
| `raw_*.json` | All 60 raw measurements (20 samples × 3 architectures) | Detailed analysis |
| `summary_*.json` | Aggregated metrics per architecture | Quick comparison |
| `results_*.csv` | Flat table, one row per sample | Excel/Python charts |
| `report_*.md` | Markdown comparison table | Copy into thesis |

### Open CSV in Python for analysis:

```python
import pandas as pd
df = pd.read_csv("results_20240301_143022.csv")
print(df.groupby("architecture")["total_latency_ms"].describe())
print(df.groupby("architecture")["tokens_per_second"].mean())
```

---

## 11. Troubleshooting

### Build fails: "NDK not found"

```
Error: NDK not installed at /home/user/Android/Sdk/ndk
```

**Fix**: Install NDK in Android Studio:
`Tools → SDK Manager → SDK Tools → NDK (Side by side)` → Apply

---

### Build fails: "llama.cpp directory not found" or CMake error

```
CMake Error: The source directory does not contain a CMakeLists.txt file.
```

**Fix**: The llama.cpp submodule is empty. Run:
```bash
cd SciGemma_Android_App/app/src/main/cpp
git submodule add https://github.com/ggerganov/llama.cpp llama.cpp
git submodule update --init --recursive
```

---

### App shows "Failed to load model"

```
Model not found at: /data/local/tmp/llm/gemma-2b-it-q4_k_m.gguf
```

**Fix**: Push the model to the device:
```bash
adb shell mkdir -p /data/local/tmp/llm
adb push gemma-2b-it-q4_k_m.gguf /data/local/tmp/llm/
adb shell ls -lh /data/local/tmp/llm/
```

---

### App shows "Failed to load model: Native library not found"

The JNI library (`libllama_bridge.so`) didn't load.

**Fix**: Ensure the build completed successfully and the APK contains the native library:
```bash
unzip -l app-debug.apk | grep llama
# Expected: lib/arm64-v8a/libllama_bridge.so
```

If the file is missing, rebuild with:
```bash
./gradlew clean assembleDebug
```

---

### Device not detected by adb

```bash
adb devices
# List of devices attached
# (empty)
```

**Fix**:
```bash
# Restart adb server
adb kill-server
adb start-server
adb devices

# If still empty, check USB mode on phone:
# Notification bar → USB → "File Transfer" mode
```

---

### Benchmark crashes mid-run (Out of Memory)

The Nokia 5.4 may run out of RAM during long benchmarks.

**Fix**: Use the Quick Benchmark (10 samples) for initial testing:
- Tap **"Quick Benchmark (10 samples)"** on the Benchmark screen.

If it still crashes, reduce the context window in `LlamaCppModel.kt`:
```kotlin
const val DEFAULT_N_CTX = 1024  // reduce from 2048
```

---

### Generated text is garbled or cut off

The model is generating unexpected tokens.

**Fix**: Verify the correct GGUF file is used. Check in `LlamaCppModel.kt`:
```kotlin
const val MODEL_PATH = "/data/local/tmp/llm/gemma-2b-it-q4_k_m.gguf"
```
The filename on the device must match exactly.

---

## Quick Reference

```bash
# One-time setup
git submodule add https://github.com/ggerganov/llama.cpp \
    SciGemma_Android_App/app/src/main/cpp/llama.cpp
git submodule update --init --recursive

# Build
cd SciGemma_Android_App && ./gradlew assembleDebug

# Install + push model
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell mkdir -p /data/local/tmp/llm
adb push ~/Downloads/gemma-2b-it-q4_k_m.gguf /data/local/tmp/llm/

# Pull benchmark results after running
adb pull \
    /sdcard/Android/data/com.example.scigemma/files/benchmark_results/ \
    ./benchmark_results/
```
