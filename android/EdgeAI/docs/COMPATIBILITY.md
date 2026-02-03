# Device Compatibility Matrix

**Version**: 1.10.0
**Date**: 2026-01-30

This document outlines the Android devices and chipsets officially tested with the BreezeApp-engine.

---

## 🛑 How to Verify a Device

1. **Install Engine**: Install `BreezeApp-engine-release.apk` on the target device.
2. **Run Diagnostics**: Open **BreezeApp Engine** and look for **Quick Test** or **Diagnostics**.
3. **Check Support**:
   - Verify "NPU Supported" status (Yes/No).
   - Run "Quick Test" to verify basic functionality.
4. **Record**: Add the device to the appropriate table below.

---

## 1. Supported Ecosystems

| Component | Minimum Requirement | Recommended |
| :--- | :--- | :--- |
| **Android OS** | Android 12 (API 31) | Android 14 (API 34) |
| **RAM** | 6GB (for 3B models) | 12GB+ |
| **Storage** | 4GB free space | 10GB free space |
| **Chipset** | Snapdragon 8 Gen 1+ | **Dimensity 9300** (Full NPU Support) |

---

## 2. Tested Device List

### ✅ Tier 1: Full NPU Acceleration (Recommended)
*Best performance, lowest power consumption.*

| Manufacturer | Model | Chipset | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **MediaTek** | Dimensity 9400 Dev Kit | Dimensity 9400 | **Verified** | Reference NPU Platform |

### ✅ Tier 2: CPU Fallback (Supported)
*Stable functionality via ExecuTorch CPU runner, but slower inference.*

| Manufacturer | Model | Chipset | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Google** | Pixel 7a | Tensor G2 | **Verified** | CPU Fallback |

### ⚠️ Tier 3: Limited Support
*May experience OOM or thermal throttling.*

* Devices with < 8GB RAM (e.g., Pixel 6a)
* Older chipsets (Snapdragon 888 and below)

---

## 3. Known Issues

* **[DEVICE_MODEL]**: [Describe issue, e.g., Thermal throttling after 10 mins]
