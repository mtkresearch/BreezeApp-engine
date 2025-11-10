# Google Play Store Graphics Requirements

**Purpose**: Asset specifications for BreezeApp-engine Play Store listing
**Last Updated**: 2025-11-03

---

## Overview

This document defines all graphic assets required for the BreezeApp-engine Google Play Store listing. Since this is a service component without a traditional UI, our graphics strategy focuses on:

1. **Architectural diagrams** showing the engine's role in the ecosystem
2. **Integration examples** demonstrating developer use cases
3. **Performance visualizations** highlighting on-device capabilities
4. **Companion app previews** showing end-user experience

---

## Required Assets Checklist

- [ ] App Icon (512x512px)
- [ ] Feature Graphic (1024x500px)
- [ ] Screenshots (minimum 2, maximum 8)
- [ ] Promo Video (optional, recommended)
- [ ] TV Banner (optional, not applicable)

---

## 1. App Icon (T102)

### Specifications

**Dimensions**: 512x512 pixels
**Format**: 32-bit PNG with alpha channel
**File Size**: <1MB
**Color Space**: sRGB

### Design Requirements

**Content**:
- BreezeApp logo with "Engine" badge or identifier
- Clean, minimalist design (service component, not user-facing)
- MediaTek branding if required by corporate guidelines

**Style Guidelines**:
- Follow Material Design 3 icon guidelines
- Avoid text (except short labels like "AI" or "Engine")
- Ensure visibility at small sizes (48x48dp on devices)
- Use adaptive icon principles (safe zone: centered 264x264px circle)

### Adaptive Icon Layers (Android 8.0+)

You should also provide adaptive icon layers:

**Foreground Layer**: 512x512px PNG (main icon content in center 264x264px)
**Background Layer**: 512x512px PNG (solid color or simple pattern)

**Safe Zone**: Keep critical content within centered 264x264px circle to account for device-specific masking.

### Color Palette Suggestion

```
Primary: #1976D2 (Material Blue 700) - AI/Technology theme
Accent:  #FFC107 (Amber 500) - MediaTek brand accent
Neutral: #FFFFFF / #000000 - High contrast
```

### File Naming

```
app-icon-512.png          # Main icon
app-icon-adaptive-fg.png  # Adaptive foreground
app-icon-adaptive-bg.png  # Adaptive background
```

---

## 2. Feature Graphic (T100)

### Specifications

**Dimensions**: 1024x500 pixels (2.048:1 aspect ratio)
**Format**: 24-bit PNG or JPEG
**File Size**: <1MB
**Color Space**: sRGB

### Design Requirements

**Content**:
The feature graphic should communicate the app's purpose in one glance. For BreezeApp-engine, recommended content:

1. **Left side (40%)**:
   - BreezeApp Engine logo
   - Tagline: "On-Device AI Inference Engine"
   - Subtle gradient or clean background

2. **Right side (60%)**:
   - Simplified architecture diagram showing:
     - Engine service (center)
     - Connected client apps (BreezeApp or other client apps)
     - AI capabilities icons (LLM, ASR, TTS, VLM)

**Style Guidelines**:
- Professional, technical aesthetic (developer tool)
- Avoid cluttered layouts
- Ensure text is readable at 320px wide (mobile thumbnail)
- Use consistent brand colors
- Include MediaTek branding if required

### Text Requirements

**Maximum Text**: Keep minimal
- Main headline: 1-3 words ("AI Engine Service")
- Subtitle: 1 short phrase ("For BreezeApp Ecosystem")
- Avoid full sentences

**Typography**:
- Use sans-serif fonts (Roboto, Inter, or brand font)
- Minimum font size: 24px for readability
- High contrast (dark text on light background or vice versa)

### File Naming

```
feature-graphic-1024x500.png
```

---

## 3. Screenshots (T101, T103)

### Specifications

**Minimum**: 2 screenshots
**Maximum**: 8 screenshots
**Recommended**: 4-6 screenshots

**Dimensions**:
- **Phone**: 1080x1920px or 1440x2560px (16:9 aspect ratio)
- **Tablet** (optional): 1920x1200px or 2560x1600px (16:10 aspect ratio)

**Format**: 24-bit PNG or JPEG
**File Size**: <8MB per screenshot
**Color Space**: sRGB

### Screenshot Strategy for Service Component

Since BreezeApp-engine has no UI, we need creative solutions:

#### Screenshot 1: Architecture Diagram (Required)

**Title**: "BreezeApp Ecosystem Architecture"

**Content**:
```
┌─────────────────────────────────────────┐
│        Client Applications Layer        │
│  ┌─────────────┐    ┌───────────────┐  │
│  │ BreezeApp   │    │ companion apps │  │
│  │ (Main App)  │    │ (Voice-First) │  │
│  └──────┬──────┘    └───────┬───────┘  │
└─────────┼───────────────────┼───────────┘
          │                   │
          │   AIDL Binding    │
          │   (Signature      │
          │    Protected)     │
          ▼                   ▼
┌─────────────────────────────────────────┐
│       BreezeApp AI Engine Service       │
│  ┌────────┐ ┌────────┐ ┌────────┐      │
│  │  LLM   │ │  ASR   │ │  TTS   │      │
│  │Inference│ │Whisper │ │Sherpa  │      │
│  └────────┘ └────────┘ └────────┘      │
│                                         │
│  🔒 Signature Verification              │
│  ⚡ On-Device Processing (No Cloud)     │
└─────────────────────────────────────────┘
```

**Design**:
- Use phone mockup frame (optional)
- High-quality diagram rendering
- Add annotations with arrows
- Include security badges (🔒 Signature Protected, 🔐 Privacy-First)

#### Screenshot 2: Integration Code Example (Required)

**Title**: "Simple Integration with AIDL"

**Content**: Show code snippet in IDE or terminal:

```kotlin
// AndroidManifest.xml
<uses-permission android:name=
    "com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE" />

// EngineClient.kt
val intent = Intent("com.mtkresearch.breezeapp.engine.AI_SERVICE")
    .setPackage("com.mtkresearch.breezeapp.engine")

context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

// After binding...
val result = engineService.inferText(
    "What is on-device AI?",
    Bundle()
)
```

**Design**:
- Use Android Studio screenshot or mockup
- Syntax highlighting
- Add callout annotations explaining key parts
- Include "✅ Works Offline" badge

#### Screenshot 3: Performance Metrics (Optional)

**Title**: "Optimized for Mobile Performance"

**Content**: Show performance benchmarks:

```
┌─────────────────────────────────────────┐
│       Performance Benchmarks            │
├─────────────────────────────────────────┤
│  📊 Inference Speed                     │
│      • Text Generation: ~50 tokens/sec  │
│      • Speech Recognition: Real-time    │
│      • Model Loading: <3 seconds        │
│                                         │
│  💾 Memory Usage                        │
│      • LLM (3B params): ~2GB RAM        │
│      • ASR Model: ~200MB RAM            │
│      • TTS Model: ~150MB RAM            │
│                                         │
│  🔋 Power Efficiency                    │
│      • NPU Acceleration: -40% battery   │
│      • Idle Power: <1% battery/hour     │
│                                         │
│  🔐 Security                            │
│      • Signature Verification: <10ms    │
│      • Zero Data Sent to Cloud: 100%    │
└─────────────────────────────────────────┘
```

**Design**:
- Clean infographic style
- Use icons and visual bars
- Brand colors
- Professional typography

#### Screenshot 4: Companion Apps Preview (Optional)

**Title**: "Powers Your Favorite Apps"

**Content**: Show BreezeApp and companion apps UI with arrows pointing to:
- "Powered by BreezeApp Engine ⚙️"
- Show chat interface, voice interface
- Emphasize offline capability

**Design**:
- Use actual app screenshots
- Add overlay badges
- Show multiple apps side-by-side

#### Screenshot 5: Security Model (Optional)

**Title**: "Enterprise-Grade Security"

**Content**:
```
┌─────────────────────────────────────────┐
│      Signature-Level Protection         │
│                                         │
│  Client App ──[Signature Check]──> ✅   │
│  (Authorized)                           │
│                                         │
│  Malicious App ──[Signature Check]──> ❌│
│  (Unauthorized)                         │
│                                         │
│  🔒 Only apps signed with matching      │
│     certificate can access engine       │
│                                         │
│  🔐 All AI processing stays on device   │
│     • No data sent to cloud             │
│     • No account required               │
│     • Open source security model        │
└─────────────────────────────────────────┘
```

**Design**:
- Use lock icons, checkmarks, X marks
- Green for authorized, red for unauthorized
- Flow diagram with arrows

#### Screenshot 6: Developer Resources (Optional)

**Title**: "Comprehensive Developer Documentation"

**Content**: Show documentation website or GitHub repo with:
- API Reference
- Integration Guides
- Code Examples
- Community Support

**Design**:
- Browser mockup
- Highlight key sections
- Include GitHub stars/forks if impressive

### Screenshot Layout Guidelines

**Text Overlays** (optional):
- Add descriptive titles at top of each screenshot
- Use consistent typography
- High contrast for readability

**Borders**:
- Optional device frame mockups for context
- Consistent styling across all screenshots

**Ordering**:
1. Architecture (most important)
2. Integration code
3. Performance metrics
4. Companion apps
5. Security model
6. Developer resources

### File Naming

```
screenshot-01-architecture-phone.png
screenshot-02-integration-phone.png
screenshot-03-performance-phone.png
screenshot-04-companion-apps-phone.png
screenshot-05-security-phone.png
screenshot-06-developer-docs-phone.png
```

---

## 4. Promo Video (T106)

### Specifications

**Duration**: 30 seconds to 2 minutes (recommended: 45-60 seconds)
**Format**: MP4 or MOV
**Resolution**: 1080p (1920x1080) minimum
**Aspect Ratio**: 16:9
**Frame Rate**: 24fps or 30fps
**File Size**: <100MB
**Audio**: Optional (recommended with background music or narration)

### Video Structure (60-second example)

**0:00-0:10** (Introduction)
- BreezeApp Engine logo animation
- Text overlay: "On-Device AI Inference Engine"
- Voiceover: "Introducing BreezeApp Engine - privacy-first AI for Android"

**0:10-0:25** (Problem/Solution)
- Show architecture diagram
- Highlight: "No Cloud Required 🔐"
- Voiceover: "All AI processing happens on your device, ensuring complete privacy"

**0:25-0:40** (Integration Demo)
- Screen recording of code integration
- Show binding to service
- Display inference result
- Voiceover: "Simple AIDL integration with just a few lines of code"

**0:40-0:50** (Features)
- Quick montage:
  - LLM text generation
  - Speech recognition
  - Text-to-speech
  - NPU acceleration icon
- Voiceover: "Supports LLM, ASR, TTS, and more"

**0:50-0:60** (Call to Action)
- Show companion apps (BreezeApp or other client apps)
- Text overlay: "Install BreezeApp to get started"
- GitHub logo: "Open Source - Apache 2.0"
- Voiceover: "Download BreezeApp or build your own integration today"

### Video Style Guidelines

- Professional, developer-focused aesthetic
- Clean animations (no flashy effects)
- Readable text (minimum 24px)
- Background music: subtle, non-intrusive
- Captions/subtitles recommended for accessibility
- Consistent brand colors throughout

### File Naming

```
promo-video-1080p.mp4
```

---

## 5. Placeholder Diagram for Screenshots (T103)

For development/testing before final graphics are ready:

### Quick Mockup Generation

**Tools**:
- Figma (recommended for UI mockups)
- draw.io / diagrams.net (for architecture diagrams)
- Canva (for quick graphics)
- Android Studio Layout Inspector (for code screenshots)

### Placeholder Template

Use this ASCII template as a guide for the architecture screenshot:

```
╔══════════════════════════════════════════════════════╗
║            BreezeApp Ecosystem Architecture          ║
╠══════════════════════════════════════════════════════╣
║                                                      ║
║  ┌─────────────────────────────────────────────┐    ║
║  │       Client Applications Layer             │    ║
║  │                                             │    ║
║  │    ┌─────────┐          ┌──────────┐       │    ║
║  │    │BreezeApp│          │BreezeApp │       │    ║
║  │    │  (UI)   │          │  Client  │       │    ║
║  │    └────┬────┘          └─────┬────┘       │    ║
║  └─────────┼─────────────────────┼────────────┘    ║
║            │                     │                  ║
║            │   AIDL Binding      │                  ║
║            │   (Signature        │                  ║
║            │    Protected 🔒)    │                  ║
║            ▼                     ▼                  ║
║  ┌─────────────────────────────────────────────┐    ║
║  │     BreezeApp AI Engine Service             │    ║
║  │                                             │    ║
║  │  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐       │    ║
║  │  │ LLM │  │ VLM │  │ ASR │  │ TTS │       │    ║
║  │  └─────┘  └─────┘  └─────┘  └─────┘       │    ║
║  │                                             │    ║
║  │  🔐 On-Device Processing (No Cloud)        │    ║
║  │  ⚡ NPU Acceleration (MediaTek)            │    ║
║  │  🔒 Signature-Level Security               │    ║
║  └─────────────────────────────────────────────┘    ║
║                                                      ║
╚══════════════════════════════════════════════════════╝
```

---

## Asset Delivery Checklist

Before submitting to Play Console:

- [ ] All assets meet dimension requirements exactly
- [ ] File sizes under limits
- [ ] Color space is sRGB
- [ ] PNG files use transparency appropriately
- [ ] Screenshots show actual product capabilities
- [ ] No placeholder text ("Lorem Ipsum")
- [ ] Brand colors consistent across all assets
- [ ] Text is readable at small sizes
- [ ] Proofread all text overlays
- [ ] Tested on multiple device sizes (preview in Play Console)
- [ ] Obtained approval from marketing/legal if needed
- [ ] MediaTek branding guidelines followed

---

## Resources

**Google Play Guidelines**:
- [Graphic Assets Guidelines](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Store Listing Best Practices](https://developer.android.com/distribute/best-practices/launch/store-listing)

**Design Tools**:
- Figma: https://figma.com
- draw.io: https://draw.io
- Android Asset Studio: https://romannurik.github.io/AndroidAssetStudio/

**Stock Resources**:
- Material Icons: https://fonts.google.com/icons
- Unsplash (backgrounds): https://unsplash.com
- Device Mockups: https://mockuphone.com

---

## File Organization

Recommended directory structure for assets:

```
play-store-assets/
├── app-icon/
│   ├── app-icon-512.png
│   ├── app-icon-adaptive-fg.png
│   └── app-icon-adaptive-bg.png
├── feature-graphic/
│   └── feature-graphic-1024x500.png
├── screenshots/
│   ├── phone/
│   │   ├── 01-architecture.png
│   │   ├── 02-integration.png
│   │   ├── 03-performance.png
│   │   ├── 04-companion-apps.png
│   │   ├── 05-security.png
│   │   └── 06-developer-docs.png
│   └── tablet/  (optional)
├── promo-video/
│   └── promo-video-1080p.mp4
└── source-files/  (Figma, PSD, AI files)
    └── README.md
```

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-03
