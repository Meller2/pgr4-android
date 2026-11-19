# PGR4 on Android

This branch targets the base Xbox 360 disc supported by upstream PGR4-Recomp v1.2.2. It uses the ReXGlue Xenos plugin with Vulkan and builds for arm64-v8a. The first tested codegen input reported Title ID `4D5307F9`, Media ID `5A381A4E`, and version `0.0.0.1`. Game binaries, disc contents and generated guest code stay local.

## Game build

Use JDK 17+, Android SDK Platform 35, NDK 28.2.13676358, and CMake 3.30.5. The host code generator runs on the build computer. On Windows, the [ReXGlue host workflow](../.github/workflows/rexglue-host.yml) builds it from a pinned source revision and checks its CLI before creating an artifact. The Android runtime uses the Android-enabled SDK source maintained by FH2 Recompiled at commit `ff062497da83f0c9ca8f9da924e90feab55ac416`. Populate its third-party source checkout, then apply the [XMP capture patch](patches/0001-xmp-capture-output.patch) from the FH2 repository root with `git apply /absolute/path/to/0001-xmp-capture-output.patch`. This accepts XMP capture registration while leaving title rendering disabled, matching the behavior used by the desktop PGR4 backend.

1. Extract your own base Xbox 360 disc under `PGR4-Files`, next to this repository. The extracted directory should contain `default.xex` at its root.
2. Use the host tool built by the workflow above, or build it from the pinned revision with the [NORMPACKED64 unpacking patch](patches/0002-codegen-unpack-normpacked64.patch). The patch comes from [pgr4-native](https://github.com/shaunsingh/pgr4-native). From this repository, run `rexglue codegen pgr4_recompiled_manifest.toml`. When switching from an older generator, pass `--ignore-stamp` to regenerate the sources.
3. Set `PGR4_REXSDK_DIR` to the `sdk` directory of the FH2 source checkout. Run `gradlew.bat :app:assembleBenchmark` on Windows, or `./gradlew :app:assembleBenchmark` on Linux, from this `android` directory. The benchmark variant optimizes the native code and keeps debug signing and `adb run-as` access. Use `:app:assembleDebug` for a native Debug build.
4. Install `app/build/outputs/apk/benchmark/app-benchmark.apk` with `adb install -r`. With a binary-safe shell, stage the extracted disc in the app's own storage:

   ```sh
   adb shell run-as dev.pgr4port.android mkdir -p files/game
   tar -C ../PGR4-Files -cf - . | adb shell -T 'run-as dev.pgr4port.android tar -xf - -C files/game'
   adb shell am start -n dev.pgr4port.android/.GameActivity
   ```

   Run the commands from the repository root. A readable `game/default.xex` in app-specific external storage is also supported. Some Android builds restrict the app's access to directories created there by the ADB shell.

The ARM64 build runs the base game through the opening movie, profile creation, main menu and into an offline street race on an Adreno 750 with the system Vulkan driver. The touch overlay supports menu navigation, steering, throttle and brake. Some surfaces appear solid black or overexposed during a race. The standalone probe below requires only the Android toolchain and a Vulkan device.

## Performance on OnePlus Ace 5

The system Qualcomm driver and full-resolution render path produced 4.4–5.4 FPS in sampled main-menu windows with the Benchmark APK. Recording around 5,100 draws in one set of samples took about 23 ms per frame. With the SDK's GPU timestamp profiler and separate present thread, profile and menu scenes still took about 196–244 ms of GPU work, most of it drawing. The profiler showed four `SET_BIN_SELECT` commands per frame in those scenes. These are short bring-up samples with different settings and do not establish a sustained race frame rate.

- **Render passes.** Study how PGR4 configures the Xenos tile rectangles and replays draws. Try one full-surface pass only after mapping the base-disc functions and confirming that clears, depth, resolves and queries remain correct. The FH2 hooks are built for a different executable. The pgr4-native D3D hooks target Title Update 2. [FH2's one-pass hook](https://github.com/Gabrick75/fh2-recompiled/blob/ff062497da83f0c9ca8f9da924e90feab55ac416/app/src/fh2_desempenho.cpp), [PGR4's desktop renderer](https://github.com/shaunsingh/pgr4-native)
- **Mobile Vulkan.** NFS Most Wanted reduces tile-memory traffic by moving safe uploads ahead of render passes. FH2 saw gains from vertex reuse and removing small GPU readbacks in its native renderer. Measure those costs in PGR4 before adapting either technique. [NFS Android measurements](https://github.com/victorgbd/NFSMW-Recompiled-Mobile/blob/main/docs/android.md), [FH2 v0.3.0 measurements](https://github.com/Gabrick75/fh2-recompiled/releases/tag/v0.3.0)
- **Driver comparison.** At full render scale and 2× MSAA, the main menu with Single Player selected ran at 7.2–7.3 FPS across three five-second windows on stock Qualcomm and 16.4–16.6 FPS across six windows on Turnip 27.0.0-R2. The introduction and profile loading were excluded. In the Palace Square street race, short samples near the start were 4.4–5.0 FPS on Qualcomm, and the early race on Turnip ran at 14.5–16.7 FPS. These race samples use the same event with different driving and camera positions. Buildings and barriers rendered with their textures on Turnip. [Turnip build tested](https://github.com/StevenMXZ/Adreno-Tools-Drivers/releases/tag/v39), [libadrenotools](https://github.com/bylaws/libadrenotools)

The 50% render scale and disabled MSAA combination briefly reached 7.3 FPS but severely distorted the menu. Disabling MSAA alone also failed to improve the sampled menu frame rate. The temporary settings were removed.

The APK includes the driver loader's native libraries. For a development test, stage the ARM64 Vulkan library in a folder owned by the app and add the following settings to `files/pgr4_recompiled.toml` using `adb shell run-as dev.pgr4port.android`. Restart the app and check the `Vulkan device` line in its log to confirm which driver opened. Remove these settings to select the system driver.

```toml
android_gpu_driver_dir = "/data/user/0/dev.pgr4port.android/files/gpu/turnip-v39"
android_gpu_driver_name = "libvulkan_freedreno.so"
```

The base-disc D3D entry points for the tested executable are `0x8269C6D0` (`BeginTiling`) and `0x8269CB68` (`EndTiling`). The former stores the tile count and the latter calls the matching resolve function. The optional `pgr4_trace_tiling = true` setting logged two full-width tiles covering rows 0–480 and 480–720. The trace records the first 24 multi-tile calls and leaves the guest rendering calls intact.

A local single-pass probe merged those two rectangles, capped MSAA at 1× and relaxed draw predication. The title screen showed heavy corruption. The test device was returned to Turnip with the standard two-tile layout and 2× MSAA.

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
