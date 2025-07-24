# How to Integrate sherpa-onnx into Your Android Project (Using Prebuilt AARs)

This guide walks you through the simplest and most professional method: **using the official prebuilt AARs from [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.6)**.

---

## 1. Prerequisites

- Android Studio (with NDK installed)
- Git (for project management)
- You do **NOT** need to clone or build sherpa-onnx yourself!

---

## 2. Download the Prebuilt AAR

Go to the latest [sherpa-onnx release page](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.6).

- Download the unified AAR:  
  **`sherpa-onnx-android-v1.12.6.aar`**  
  (or a newer version if available)

**Use wget to download:**
```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.6/sherpa-onnx-1.12.6.aar
```

If you only need a specific feature (ASR, TTS, VAD), you may download the corresponding AAR file from the release page using wget in the same way.

---

## 3. Add the AAR to Your Android Project

- Move the downloaded `.aar` file into your app module's `libs/` directory.  
  (Create the `libs/` folder if it doesn't exist.)

- Edit your app module's `build.gradle.kts` or `build.gradle` to add:

```kotlin
dependencies {
    implementation(files("libs/sherpa-onnx-android-v1.12.6.aar"))
}
```

---

## 4. Download Pretrained ASR/TTS Models

Visit the [sherpa-onnx pretrained models page](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html).

- Download the models you need for ASR/TTS.
- Place them in your app's `assets/` folder.

---

## 5. Use sherpa-onnx API in Your Code

Import the classes:

```kotlin
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineRecognizer
```

Example usage (refer to the official sample for details):

```kotlin
val tts = OfflineTts(config)
val audio = tts.generate("Hello world!")

val recognizer = OfflineRecognizer(config)
val result = recognizer.recognize(audioData)
```

---

## 6. Run & Test

- Build and run your app.
- Make sure your assets/model files are correctly placed.
- Refer to the official Android sample: [`android/SherpaOnnxTts`](https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxTts)

---

## Notes

- **You do NOT need to clone or build sherpa-onnx yourself.** Just use the official prebuilt AAR.
- For more API coverage, check if the AAR includes the classes you need. If not, you may need to request the maintainers to add them in future releases.
- For library updates, download newer AARs from the release page and update your `libs/` folder.

---

## References

- [sherpa-onnx GitHub](https://github.com/k2-fsa/sherpa-onnx)
- [Prebuilt AARs (Release Page)](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.6)
- [Pretrained Models](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html)
- [TTS Sample](https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxTts)
- [Android Sample](https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxJavaDemo)