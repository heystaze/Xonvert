# Xonvert — Offline Native Image & PDF Compression

A privacy-first, 100% offline native Android application designed to reliably shrink image and PDF file sizes from megabytes to kilobytes for government job portals, identity document uploads, passport photo submissions, and scanned forms without ever uploading files to any external server.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following key decisions have been aligned based on user preferences and requirements:

- **Confirmed PDF Processing Strategy**: Native Android PDF engine (`PdfRenderer` & `android.graphics.pdf.PdfDocument`) with intelligent page downsampling, quality profiles, and explicit rasterized-scanned-document optimization. Zero third-party proprietary dependencies or bloated binaries.
- **Confirmed Accent Color & Theme**: **Emerald Green** (`#0D9488` / `#10B981` palette) with deep slate neutrals, supporting seamless Material 3 Light and Dark themes, giving a crisp, modern, privacy-utility aesthetic.
- **Confirmed Default Target Size Preset**: **100 KB** (the strict standard limit for portal passport photos, signatures, and civil service uploads), alongside one-tap presets for 20 KB, 50 KB, 100 KB, 200 KB, 500 KB, 1 MB, 2 MB, and custom KB/MB inputs.
- **Zero Network Permissions**: The app requires no internet access (`android.permission.INTERNET` is omitted), guaranteeing complete on-device privacy.

---

## 1. Overview & Core Concept

- **What It Does**: Xonvert provides reliable, bounded-target compression for images (JPEG, PNG, WebP) and PDF documents. It iteratively searches for the ideal encoding quality and dimension scale to meet strict upload size ceilings (e.g., "Must be under 100 KB") without false reporting or visual corruption.
- **Target Audience / Persona**: Everyday applicants, job seekers, students, and citizens uploading identity documents (passport photos, PAN/Aadhaar/National IDs, scanned certificates, tax forms) to strict government and corporate application portals.
- **Key Value**: Guaranteed honesty (only confirms a target was met after verifying actual saved byte counts), privacy assurance (everything runs in-memory and in cache on the local CPU), and rapid batch processing.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Home Screen**:
   - Header with Xonvert branding, privacy badge ("100% On-Device • No Internet Access"), and Quick Settings shortcut.
   - Core Action Cards: **Compress Image** and **Compress PDF**.
   - Secondary Quick Action: **Batch Compress (Multiple Images)**.
   - **Recent Results List**: Clean history cards showing filename, original size, output size, reduction percentage, and quick share/open actions.

2. **Single Image Compression Flow**:
   - File picker via system Storage Access Framework (`ActivityResultContracts.GetContent`).
   - Image inspection: displays original dimensions (e.g., `4032 × 3024`), format (`JPEG`/`PNG`/`WebP`), and size (`3.4 MB`).
   - Configuration pane:
     - Target size selection (Presets: 20KB, 50KB, 100KB, 200KB, 500KB, 1MB, or Custom).
     - Dimension constraint (Keep Original, Scale to 1920p, 1280p, 800p, or Custom W×H).
     - Format selection (Auto, Original, JPEG, WebP, PNG) with transparency preservation safety.
   - Live iterative compression with bounded progress indicator.
   - Result screen showing actual compressed size, % saved, zoomable side-by-side preview, Save As (`CreateDocument`), and Share (`Intent.ACTION_SEND`).

3. **PDF Compression Flow**:
   - PDF file picker with metadata inspection (page count, source byte size).
   - Profiles:
     - **Light**: Lossless stream recompression & moderate DPI (200 DPI).
     - **Balanced**: Standard downsampling (150 DPI) with quality optimization (Target ~100KB–500KB).
     - **Strong**: Aggressive downsampling (100–120 DPI, JPEG 65%) for heavy scanned documents.
     - Optional **Explicit Scanned Rasterization** toggle with clear accessibility/text disclaimer.
   - Page-by-page progress reporting.
   - Verification of generated output by re-querying actual file stream size and verifying integrity.

4. **Batch Image Compression Flow**:
   - Select multiple photos at once (`GetMultipleContents`).
   - Shared target size (e.g., "All under 100 KB") or percentage reduction.
   - Queue execution with individual item status (Pending, Processing, Done, Target Exceeded).
   - Export all or share directly.

### Visual Identity & Theme

- **Aesthetic Direction**: High-utility precision engineering with clean Material 3 surfaces, rounded geometry (16dp–24dp corners), and purposeful data visualization.
- **Color Palette (Emerald Slate)**:
  - Primary: Emerald 600 (`#059669`) / Dark Emerald (`#10B981`)
  - Primary Container: Mint Tint (`#D1FAE5`) / Dark Forest (`#064E3B`)
  - Surface: Pure Light Slate (`#F8FAFC`) / Deep Obsidian Slate (`#0F172A`)
  - Accent / Warnings: Amber 500 (`#F59E0B`) for size warnings, Rose 500 (`#EF4444`) for errors, Emerald 500 (`#10B981`) for targets met.
- **Typography & Hierarchy**:
  - Headlines: Clean, semi-bold sans-serif styling for numbers, sizes, and file metrics.
  - Metrics Display: Large tabular numbers (e.g. `100 KB` vs `1.2 MB`) with reduction badges (`-92%`).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Native Android Engine over Heavy Third-Party C++ Binaries**
  - *Chosen Approach*: Utilize Android's built-in `BitmapFactory`, `ExifInterface`, `PdfRenderer`, and `PdfDocument`.
  - *Why*: Guarantees small APK size, 100% compatibility with Android 16 and modern 64-bit architectures, zero native crash vulnerabilities, and strict Apache-2.0 open-source compliance without dependency bloat.
  - *Alternative Considered*: Bundling Apache PDFBox or Poppler would add 20MB+ to APK size and increase memory overhead on low-end devices.

- **Decision 2: Target-Size Bounded Binary Search Algorithm**
  - *Chosen Approach*: A two-stage bounded search:
    1. Try quality reduction (Binary search between quality 10 and 95, max 5 iterations).
    2. If lowest acceptable quality still exceeds target, progressively downscale dimensions (e.g., 0.8x, 0.6x, 0.4x) while preserving aspect ratio and EXIF orientation.
  - *Why*: Reaches optimal quality in under 6 iterations without freezing UI or exceeding memory limits.

- **Decision 3: Transparent PNG Preservation**
  - *Chosen Approach*: Inspect alpha channel. If transparent, preserve PNG or convert to WebP (lossy/lossless with alpha support). Never convert to JPEG without user consent, preventing black background glitches.

- **Decision 4: Local Room Database for Compression History**
  - *Chosen Approach*: Room DAO with Flow reactive updates storing recent file compression records (metadata only, no cached private file contents).
  - *Why*: Allows users to track their saved space and re-share recent outputs while keeping all operations private and local.

---

## 4. Technical Architecture & Data Strategy

### Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  ┌───────────────┐ ┌────────────────┐ ┌──────────────────┐  │
│  │  HomeScreen   │ │ ImageCompress  │ │   PdfCompress    │  │
│  │  & History    │ │ Screen & State │ │   Screen & State │  │
│  └───────┬───────┘ └───────┬────────┘ └────────┬─────────┘  │
│          │                 │                   │            │
│          └────────────┐    │    ┌──────────────┘            │
│                       ▼    ▼    ▼                           │
│                ┌───────────────────────┐                    │
│                │      ViewModels       │                    │
│                │ (StateFlow / Coroutine│                    │
│                └───────────┬───────────┘                    │
└────────────────────────────┼────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                       Domain & Engine                       │
│  ┌────────────────────────┐    ┌─────────────────────────┐  │
│  │ ImageCompressionEngine │    │  PdfCompressionEngine   │  │
│  │ • EXIF Orientation     │    │  • PdfRenderer Pipeline │  │
│  │ • Binary Search Qual   │    │  • DPI / Scale Matrix   │  │
│  │ • Aspect Downscaling   │    │  • PdfDocument Re-encode│  │
│  │ • Transparent Alpha    │    │  • Page Integrity Check │  │
│  └───────────┬────────────┘    └────────────┬────────────┘  │
│              │                              │               │
│              └──────────────┬───────────────┘               │
│                             ▼                               │
│              ┌─────────────────────────────┐                │
│              │ Storage Access & History    │                │
│              │ • ContentResolver Streams   │                │
│              │ • Temp File Auto-Cleanup    │                │
│              │ • Room History Database     │                │
│              └─────────────────────────────┘                │
└─────────────────────────────────────────────────────────────┘
```

### Core Entities & State

- **`CompressionRecord`**: Room Entity (`id`, `fileName`, `fileType`, `originalBytes`, `compressedBytes`, `timestamp`, `targetBytes`, `success`, `savedUri`).
- **`ImageCompressConfig`**: Data class (`targetMaxBytes`, `quality`, `maxWidth`, `maxHeight`, `keepAspectRatio`, `outputFormat`).
- **`PdfCompressConfig`**: Data class (`targetMaxBytes`, `profile`, `targetDpi`, `imageQuality`, `rasterizeScannedDoc`).
- **`CompressionResult`**: Sealed class (`Success`, `TargetExceeded`, `Error`).

---

## 5. Testing & Verification Plan

- **Robolectric Local Unit Tests**:
  - File size conversion utilities (`formatFileSize(bytes)`, `parseSizeInput(string, unit)`).
  - Target size calculation & percentage savings math (`calculateSavingsPercent`).
  - Binary search boundary conditions & iteration cap tests.
  - Aspect ratio calculation logic (preserving 16:9, 4:3, 1:1, custom orientations).
- **Compilation & Device Verification**:
  - Run `compile_applet` to confirm clean compilation with Kotlin 2.2 and Compose M3.
  - Verify absence of `INTERNET` permission in `AndroidManifest.xml`.
  - Validate Storage Access Framework file reading and saving.
