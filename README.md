# PGR4-Android

An Android (ARM64) static recompilation port of **Project Gotham Racing 4** (Xbox 360) built with **ReXGlue SDK** and **Vulkan**, featuring a standalone on-device launcher.

> [!NOTE]
> This project does **not** include any copyrighted game files or Xbox 360 binaries. You must provide your own legally dumped copy of the base Xbox 360 disc (Title ID `4D5307F9`, Media ID `5A381A4E`, version `0.0.0.1`).

---

## Features

- **Standalone Android Launcher (no PC required after installing the APK):**
  - Pick an extracted game folder (`default.xex`) or mount a **`.iso` Xbox 360 disc image directly** without extracting it on the phone.
  - **Automatic GPU detection:** configures optimal fast settings on Snapdragon (Adreno) and safe compatible defaults on Mali / Dimensity devices.
  - **Built-in GPU Driver Manager:** import custom Turnip `.zip` driver packages directly on your phone and test them in an isolated probe process before launching the game.
  - **Graphics & Control Settings:** one-tap presets (*Performance 720p*, *Compatible*, *High Quality*), resolution scaling, VSync, FPS/heat overlay toggle, and touch controls toggle/opacity for gamepad users.
  - **Diagnostics:** view cache size, clear shader cache, and share the latest log file directly from the app.
- **Performance (OnePlus Ace 5 / Snapdragon 8 Gen 3, Turnip v39, 1280×720):** **37.6–39.3 FPS** (`25.4–26.6 ms` average frame time) in street races on Palace Square with full original graphics quality and no main-thread shader stalls.

---

## Quick Start (On-Device Setup)

1. **Copy your game dump to the phone**
   - Transfer either your extracted *Project Gotham Racing 4* folder (containing `default.xex` at its root) or your Xbox 360 `.iso` image to internal storage (e.g. `Download/PGR4`).
2. **Install the APK**
   - Install `app-benchmark.apk` (see [android/README.md](android/README.md) for building from source).
3. **Set up in the launcher**
   - Open **PGR4** on your phone.
   - Allow storage access when prompted and select your game folder or `.iso` file.
   - *(Optional, Snapdragon only)* Open the **Drivers** tab to install a custom Turnip `.zip` driver.
   - Tap **PLAY**.

---

## Building from Source

Full toolchain requirements (JDK 17+, Android SDK Platform 35, NDK 28.2, CMake 3.30.5), host codegen instructions, ADB automation (`quick-race.cmd`), and SDK patches are documented in [android/README.md](android/README.md).

---

## Credits

- [beatrixzy/PGR4-Recomp](https://github.com/beatrixzy/PGR4-Recomp) — Upstream PGR4 static recompilation project
- [ReXGlue SDK](https://github.com/rexglue/rexglue-sdk) — Xbox 360 AOT recompilation framework
- [Gabrick75/fh2-recompiled](https://github.com/Gabrick75/fh2-recompiled) — Android-enabled ReXGlue SDK & Vulkan optimizations
- [shaunsingh/pgr4-native](https://github.com/shaunsingh/pgr4-native) — NORMPACKED64 unpacking patch & PGR4 rendering insights
