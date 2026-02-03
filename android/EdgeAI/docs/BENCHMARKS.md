# Performance Benchmarks

**Version**: 1.10.0
**Date**: 2026-01-30
**Test Device**: Google Pixel 7a, Dimensity 9400 Dev Kit

Availability of high-performance on-device AI is the core value proposition of BreezeApp-engine. Use this document to record confirmed benchmarks on target hardware.

---

## 🛑 Measurement Instructions

**1. LLM Inference Speed**:
   - Open **BreezeApp Engine** app (or launch `EngineSettingsActivity`).
   - Navigate to **Quick Test**.
   - Input Prompt: "Count from 1 to 50."
   - **Check the 'Stress Mode' box.**
   - Click 'Run Test'.
   - Wait for the **5 iterations** to complete.
   - Record the **Avg Speed** (Tokens/Sec) from the 'Statistics' section.

**2. Memory Usage**:
   - Connect device to **Android Studio**.
   - Open **Profiler** tab.
   - Select `com.mtkresearch.breezeapp.engine` process.
   - Run **Stress Mode** in Quick Test.
   - Record the **Peak Native Memory** from the Profiler graph.
   - (Optional) Record the "Peak VM Memory" displayed in the app result.

**3. Latency**:
   - In **Quick Test**, measure the time between clicking "Run" and the first character appearing.

---

## 1. LLM Inference Speed

**Model**: `Breeze2-3B-8W16A-250630-npu` (3B Parameters)

| Device / Chipset | Runner | Average Tokens/Sec | Result |
| :--- | :--- | :--- | :--- |
| **Google Pixel 7a** | Executorch (CPU) | **4.37 t/s** | Marginal (<5 t/s) |
| **Dimensity 9400 Dev** | Executorch (CPU) | **11.08 t/s** | Pass |
| **Dimensity 9400 Dev** | Executorch (NPU) | **[To Be Verified]** | Investigation Needed |

---

## 2. Memory Usage

| Scenario | Idle Memory | Peak Memory (During Inference) | Leaks Detected |
| :--- | :--- | :--- | :--- |
| **3B Model Load (Pixel 7a)** | ~200 MB | **3.0 GB (Native) / 3.9 GB (Total)** | No |
| **3B Model Load (D9400)** | ~200 MB | **2.0 GB (Native) / 3.8 GB (Total)** | No |
| **Tiny 1B Model Load** | ~150 MB | **[To Be Verified]** | [To Be Verified] |
| **Continuous Chat (1hr)** | ~250 MB | **[To Be Verified]** | [To Be Verified] |

> **Target**: Peak memory should be < 2GB for 1B models and < 4GB for 3B models.

---

## 3. Latency (Response Time)

| Operation | Cold Start (First Run) | Warm Start (Subsequent) |
| :--- | :--- | :--- |
| **Model Load + Inference** | [To Be Verified] | **[To Be Verified]** |
| **ASR Stream Init** | - | **Functionality Verified** (Latency Pending) |
| **TTS Generation** | - | **Functionality Verified** (Latency Pending) |

---

## 4. Stress Testing Reliability

| Test Case | Duration | Request Count | Result |
| :--- | :--- | :--- | :--- |
| **Rapid Fire Chat** | 5 mins | 500 requests | **Functionality Verified** (Short duration) |
| **Concurrent ASR+LLM** | 10 mins | 100 mixed reqs | **[To Be Verified]** |
| **Long-Running Session** | 60 mins | Continuous | **[To Be Verified]** |
