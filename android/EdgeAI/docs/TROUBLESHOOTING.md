# Troubleshooting Guide

**Version**: 1.10.0

This guide provides solutions for common issues encountered when integrating the BreezeApp-engine SDK.

---

## 1. Common Exceptions

### `ModelNotFoundException`
**Error Message**: "Model [model_name] not found in unified storage."
**Cause**: The requested model has not been downloaded or loaded into the model manager.
**Solution**:
1. Ensure you have triggered the download for the model variant.
2. Verify the model path configuration in `UnifiedModelManager`.
3. **Quick Fix**: Use `EdgeAI.downloadModel("breeze-7b-instruct-v0_1")` before chatting.

### `ServiceConnectionException`
**Error Message**: "Failed to bind to AIEngineService."
**Cause**:
1. The Engine APK is not installed on the device.
2. The calling app does not have the necessary permissions.
**Solution**:
1. Install `BreezeApp-engine-release.apk` on the device.
2. Add `<uses-permission android:name="com.mtkresearch.breeze.permission.BIND_ENGINE" />` to your `AndroidManifest.xml`.

### `OutOfMemoryError` (OOM)
**Symptoms**: App crashes silently or during model loading.
**Cause**: The device does not have enough free RAM to load the model (especially 7B models).
**Solution**:
1. Switch to a smaller model variant (e.g., breeze-tiny-instruct, 1B params).
2. Close other background apps.
3. Use `android:largeHeap="true"` in your application manifest (legacy devices).

---

## 2. LLM Issues

### Streaming Response is Empty
**Cause**: The listener might be attached after the flow has completed, or network issues (for OpenRouter).
**Solution**:
- Ensure you `collect` the flow immediately.
- If using OpenRouter, check your internet connection and API key validity.

### "Rate Limit Exceeded"
**Cause**: Too many requests sent rapidly to OpenRouter.
**Solution**: Implement exponential backoff or switch to local processing (ExecuTorch/MTK) which has no rate limits.

---

## 3. Deployment Issues

### `java.lang.UnsatisfiedLinkError`
**Cause**: Native libraries (`libexecutorch.so`, `libsherpa-onnx-jni.so`) missing for the device architecture.
**Solution**:
- Ensure your app's `build.gradle` includes the correct ABI filters (usually `arm64-v8a`). We do not support `armeabi-v7a` (32-bit).

---

## 4. Support

If you encounter an issue not listed here:
1. **Check Logs**: Run `adb logcat -s "EdgeAI" "BreezeEngine"`
2. **Contact**: info@mtkresearch.com
3. **File Issue**: Open a ticket on the GitHub repository.
