# PGR4-Android

An Android (ARM64) static recompilation port of **Project Gotham Racing 4** (Xbox 360) built with **ReXGlue SDK** and **Vulkan**.

> [!NOTE]
> This project does **not** include any copyrighted game files or Xbox 360 binaries. You must provide your own legally dumped copy of the base Xbox 360 disc (Title ID `4D5307F9`, Media ID `5A381A4E`, version `0.0.0.1`).

---

## Current Status

- **Playable on Android (ARM64)** with Vulkan, touch controls, and full original graphics quality (including dynamic road shadows and reflections).
- **Performance (OnePlus Ace 5 / Snapdragon 8 Gen 3, Turnip v39, 1280×720):** **37.6–39.3 FPS** (`25.4–26.6 ms` average frame time) in street races on Palace Square.
- **No main-thread shader/pipeline stalls** during gameplay.
- **Known limitations:**
  - Some surfaces may still show minor visual glitches.
  - Requires a capable ARM64 Vulkan device; Snapdragon devices with Turnip drivers are strongly recommended.

---

## Quick Start (Running on Android)

1. **Extract your Xbox 360 disc**
   - Dump your base *Project Gotham Racing 4* disc so that `default.xex` is at the root of your extracted folder (e.g. `PGR4-Files/`).
2. **Build or install the APK**
   - See the [Android Build Guide](android/README.md) to compile `:app:assembleBenchmark` from source.
   - Install the APK via ADB:
     ```sh
     adb install -r android/app/build/outputs/apk/benchmark/app-benchmark.apk
     ```
3. **Copy game files to the device**
   - Push the extracted disc contents into the app's storage:
     ```sh
     adb shell run-as dev.pgr4port.android mkdir -p files/game
     tar -C ../PGR4-Files -cf - . | adb shell -T 'run-as dev.pgr4port.android tar -xf - -C files/game'
     ```
4. **Launch the game**
   ```sh
   adb shell am start -n dev.pgr4port.android/.GameActivity
   ```

---

## Building from Source

Full toolchain requirements (JDK 17+, Android SDK Platform 35, NDK 28.2, CMake 3.30.5), host codegen instructions, and SDK patches are documented in [android/README.md](android/README.md).

---

## Credits

- [beatrixzy/PGR4-Recomp](https://github.com/beatrixzy/PGR4-Recomp) — Upstream PGR4 static recompilation project
- [ReXGlue SDK](https://github.com/rexglue/rexglue-sdk) — Xbox 360 AOT recompilation framework
- [Gabrick75/fh2-recompiled](https://github.com/Gabrick75/fh2-recompiled) — Android-enabled ReXGlue SDK & Vulkan optimizations
- [shaunsingh/pgr4-native](https://github.com/shaunsingh/pgr4-native) — NORMPACKED64 unpacking patch & PGR4 rendering insights
