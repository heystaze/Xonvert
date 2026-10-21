# Xonvert — Compress files. Keep control.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_Native-green.svg)](https://developer.android.com)
[![Privacy](https://img.shields.io/badge/Privacy-100%25_Offline-emerald.svg)]()

**Xonvert** is a lightweight, privacy-focused native Android utility designed to compress image and PDF files from megabytes to exact kilobyte targets. It is engineered specifically for government employment portals, passport photo submissions, visa applications, civil service portals, and online scanned document uploads.

---

## Key Features

- **100% Offline & Private**: Zero internet permissions (`android.permission.INTERNET` is omitted). No servers, no tracking, no telemetry, and no account registration.
- **Image Target-Size Compression**:
  - Supports JPEG, PNG, and WebP.
  - Bounded iterative binary search that stops only when your exact target ceiling (e.g., 20 KB, 50 KB, 100 KB, 200 KB, 500 KB, 1 MB) is verified on disk.
  - EXIF orientation correction and alpha transparency preservation (transparent PNGs are never converted to black-background JPEGs).
  - Progressive resolution downscaling fallback with aspect ratio preservation.
- **Native PDF Compression**:
  - Uses Android's built-in `PdfRenderer` and `PdfDocument` engine with zero external third-party binary bloat.
  - Compression profiles: **Light** (200 DPI), **Balanced** (144 DPI), and **Strong** (96 DPI).
  - Scanned document optimization mode for multi-page certificates and IDs.
  - Independent verification: re-reads the output PDF before confirming success to guarantee structural validity.
- **Batch Image Compression**: Select multiple images at once and compress them all against a shared KB limit with safe failure isolation.
- **Local History Database**: Room persistence stores compression records and calculated savings entirely on your device.
- **Storage Access Framework (SAF)**: Uses Android system file pickers and document savers without demanding broad storage permissions.

---

## Architecture & Technology Stack

- **Language**: Kotlin 2.2
- **UI Framework**: Jetpack Compose with Material 3 design system
- **Architecture**: MVVM (Model-View-ViewModel) with Kotlin Coroutines & StateFlow
- **Data Persistence**: AndroidX Room Database with KSP
- **Target OS**: Android 7.0 (API 24) to Android 16 (API 36)

---

## Building from Source

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17 or JDK 21
- Android SDK 36

### Build Steps

```bash
# Clone the repository
git clone https://github.com/example/xonvert.git
cd xonvert

# Compile the debug APK
gradle :app:assembleDebug

# Run unit tests
gradle :app:testDebugUnitTest
```

---

## Dependency Inventory & Licenses

| Dependency | Purpose | License |
|---|---|---|
| `androidx.compose.*` | Declarative UI framework | Apache 2.0 |
| `androidx.room.*` | Local SQLite database abstraction | Apache 2.0 |
| `io.coil-kt:coil-compose` | Local image rendering in Compose | Apache 2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` | Asynchronous operations | Apache 2.0 |
| `org.robolectric:robolectric` | JVM local testing | Apache 2.0 |

---

## Known Limitations

- **Encrypted PDFs**: Password-protected PDFs must be unlocked before compression because Android's native `PdfRenderer` requires credentials to access encrypted document content.
- **Extreme Downscaling Limits**: An extremely high-resolution image (e.g. 50 MP) compressed to 20 KB will automatically downscale dimensions to maintain valid image encoding limits.

---

## License

Copyright 2026 Xonvert Authors.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
