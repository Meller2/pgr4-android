# PGR4 on Android

This branch targets the base Xbox 360 disc supported by upstream PGR4-Recomp v1.2.2. It uses the ReXGlue Xenos plugin with Vulkan and builds for arm64-v8a. The first tested codegen input reported Title ID `4D5307F9`, Media ID `5A381A4E`, and version `0.0.0.1`. Game binaries, disc contents and generated guest code stay local.

## Game build

Use JDK 17+, Android SDK Platform 35, NDK 28.2.13676358, and CMake 3.30.5. The host code generator runs on the build computer. On Windows, the [ReXGlue host workflow](../.github/workflows/rexglue-host.yml) builds it from a pinned source revision and checks its CLI before creating an artifact. The Android runtime uses the Android-enabled SDK source maintained by FH2 Recompiled at commit `ff062497da83f0c9ca8f9da924e90feab55ac416`. Populate its third-party source checkout, then apply the [XMP capture patch](patches/0001-xmp-capture-output.patch) from the FH2 repository root with `git apply /absolute/path/to/0001-xmp-capture-output.patch`. This accepts XMP capture registration while leaving title rendering disabled, matching the behavior used by the desktop PGR4 backend.

1. Extract your own base Xbox 360 disc under `PGR4-Files`, next to this repository. The extracted directory should contain `default.xex` at its root.
2. Use the host tool built by the workflow above, or build it from the pinned revision with the [vector unpacking patch](patches/0002-codegen-unpack-signed-20-bit.patch). From this repository, run `rexglue codegen pgr4_recompiled_manifest.toml`. When switching from an older generator, pass `--ignore-stamp` to regenerate the sources.
3. Set `PGR4_REXSDK_DIR` to the `sdk` directory of the FH2 source checkout. Run `gradlew.bat :app:assembleBenchmark` on Windows, or `./gradlew :app:assembleBenchmark` on Linux, from this `android` directory. The benchmark variant optimizes the native code and keeps debug signing and `adb run-as` access. Use `:app:assembleDebug` for a native Debug build.
4. Install `app/build/outputs/apk/benchmark/app-benchmark.apk` with `adb install -r`. With a binary-safe shell, stage the extracted disc in the app's own storage:

   ```sh
   adb shell run-as dev.pgr4port.android mkdir -p files/game
   tar -C ../PGR4-Files -cf - . | adb shell -T 'run-as dev.pgr4port.android tar -xf - -C files/game'
   adb shell am start -n dev.pgr4port.android/.GameActivity
   ```

   Run the commands from the repository root. A readable `game/default.xex` in app-specific external storage is also supported. Some Android builds restrict the app's access to directories created there by the ADB shell.

The ARM64 build runs the base game through the opening movie, profile creation, main menu and into an offline street race on an Adreno 750 with the system Vulkan driver. The touch overlay supports menu navigation, steering, throttle and brake. Some surfaces appear solid black or overexposed during a race. The standalone probe below requires only the Android toolchain and a Vulkan device.

## Vulkan probe

Build with the Android NDK, CMake and Ninja. From the repository root, with those tools on PATH:

```sh
cmake -S android/probe -B out/android-probe -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release
cmake --build out/android-probe
adb push out/android-probe/pgr4-vulkan-probe /data/local/tmp/pgr4-vulkan-probe
adb shell 'chmod 700 /data/local/tmp/pgr4-vulkan-probe && /data/local/tmp/pgr4-vulkan-probe'
adb shell rm /data/local/tmp/pgr4-vulkan-probe
```

Verified on Android 16 with Adreno 750. The stock driver reported Vulkan 1.3.128 and successfully created a Vulkan graphics device. This probe does not open a window or exercise the Xenos renderer.
