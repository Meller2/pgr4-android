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

## Quick race on Android

`quick-race.cmd` restarts the installed game on an unlocked Android device connected over ADB. It skips the intro and selects Arcade Mode, Palace Square Street Race, Ford Sierra Cosworth RS500, and Silver difficulty. It stops sending input as soon as the race HUD appears so you can drive or start a benchmark.

On Windows, double-click `quick-race.cmd` in this directory. The launcher uses Python 3.10+, Pillow 9.1+, and `adb` on PATH. Install Pillow with `py -3 -m pip install Pillow` if needed. From the repository root, you can also run `py -3 android/tools/quick_race.py --serial DEVICE_SERIAL` to target a specific phone. Omit `--serial` if only one device is attached. The route assumes the base-disc build with the English menu, the default menu selections, and the touch controls enabled.

## Performance on OnePlus Ace 5

The in-game monitor shows guest FPS, average frame time and Android thermal status (0–6) at the top of the screen. Tap the monitor to collapse it to a small `FPS` button. Tap again to expand it. The setting is saved and updates stop when the monitor is collapsed or the game is in the background.

For a quick comparison at the start of the race, run this from the repository root with the phone unlocked:

```bat
android\quick-race.cmd --benchmark out\palace-turnip-baseline
```

Use a new output directory for each run. The script waits for the race HUD, saves a screenshot, samples for 15 seconds without sending driving input, and stops the game even if the route times out. The output contains the installed build details, app config, Android thermal status, and game log with the actual Vulkan driver. Compare only complete FPS windows between `hud` and `end` in `run.txt`. For a diagnostic run, set `gpu_cronometro = true` in the app config to log GPU timestamps by type of work. Repeat with the profiler off for the FPS comparison. Use a sustained driving run to validate the 30 FPS target.

The system Qualcomm driver and full-resolution render path produced 4.4–5.4 FPS in sampled main-menu windows with the Benchmark APK. Recording around 5,100 draws in one set of samples took about 23 ms per frame. With the SDK's GPU timestamp profiler and separate present thread, profile and menu scenes still took about 196–244 ms of GPU work, most of it drawing. The profiler showed four `SET_BIN_SELECT` commands per frame in those scenes. These are short bring-up samples with different settings and do not establish a sustained race frame rate.

- **Render passes.** Study how PGR4 configures the Xenos tile rectangles and replays draws. Try one full-surface pass only after mapping the base-disc functions and confirming that clears, depth, resolves and queries remain correct. The FH2 hooks are built for a different executable. The pgr4-native D3D hooks target Title Update 2. [FH2's one-pass hook](https://github.com/Gabrick75/fh2-recompiled/blob/ff062497da83f0c9ca8f9da924e90feab55ac416/app/src/fh2_desempenho.cpp), [PGR4's desktop renderer](https://github.com/shaunsingh/pgr4-native)
- **Mobile Vulkan.** NFS Most Wanted reduces tile-memory traffic by moving safe uploads ahead of render passes. FH2 saw gains from vertex reuse and removing small GPU readbacks in its native renderer. Measure those costs in PGR4 before adapting either technique. [NFS Android measurements](https://github.com/victorgbd/NFSMW-Recompiled-Mobile/blob/main/docs/android.md), [FH2 v0.3.0 measurements](https://github.com/Gabrick75/fh2-recompiled/releases/tag/v0.3.0)
- **Driver comparison.** At full render scale and 2× MSAA, the main menu with Single Player selected ran at 7.2–7.3 FPS across three five-second windows on stock Qualcomm and 16.4–16.6 FPS across six windows on Turnip 27.0.0-R2. The introduction and profile loading were excluded. In the Palace Square street race, short samples near the start were 4.4–5.0 FPS on Qualcomm, and the early race on Turnip ran at 14.5–16.7 FPS. These race samples use the same event with different driving and camera positions. Buildings and barriers rendered with their textures on Turnip. [Turnip build tested](https://github.com/StevenMXZ/Adreno-Tools-Drivers/releases/tag/v39), [libadrenotools](https://github.com/bylaws/libadrenotools)

On November 20, 2026, the stationary Palace Square benchmark on Ace 5 used the landscape fix (`e78c507`), the native libraries from the prior Benchmark APK, Turnip v39 (Mesa `2648b2059f`), full render scale and 2× MSAA. Two complete five-second windows after the HUD ran at 15.6 and 16.4 FPS (64.3 and 61.0 ms per frame, with worst intervals of 83.2 and 71.5 ms). The command processor used 40.0 and 37.2 ms of CPU time per frame. The HUD capture showed textured buildings, barriers and road. Android reported thermal status 1 before and after the run. This is a baseline for short comparisons at the starting grid.

An Ace 5 CPU profile at the same starting grid put 26.5% of process CPU samples in two guest functions that poll the Xenos ring. The Android hook lets the polling thread sleep for up to 1 ms, or until the SDK publishes a new read pointer. It applies to the ring-space loop in `sub_82698B80` and runs the original guest checks after waking. In two one-second samples, the main guest thread used 82–83% of a CPU core before the change and 31–33% after it. The complete frame window after the HUD was 15.3 FPS before and 15.5 FPS after, with Turnip v39 and the same render settings. Thermal status went from 1 to 2 in both runs. The race screenshot showed the same textured surfaces. This reduces CPU work spent waiting; it does not establish a frame-rate or sustained thermal gain.

The 50% render scale and disabled MSAA combination briefly reached 7.3 FPS but severely distorted the menu. Disabling MSAA alone also failed to improve the sampled menu frame rate. The temporary settings were removed.

The APK includes the driver loader's native libraries. For a development test, stage the ARM64 Vulkan library in a folder owned by the app and add the following settings to `files/pgr4_recompiled.toml` using `adb shell run-as dev.pgr4port.android`. Restart the app and check the `Vulkan device` line in its log to confirm which driver opened. Remove these settings to select the system driver.

```toml
android_gpu_driver_dir = "/data/user/0/dev.pgr4port.android/files/gpu/turnip-v39"
android_gpu_driver_name = "libvulkan_freedreno.so"
```

The base-disc D3D entry points for the tested executable are `0x8269C6D0` (`BeginTiling`) and `0x8269CB68` (`EndTiling`). The former stores the tile count and the latter calls the matching resolve function. The optional `pgr4_trace_tiling = true` setting logged two full-width tiles covering rows 0–480 and 480–720. The trace records the first 24 multi-tile calls and leaves the guest rendering calls intact.

For GPU diagnosis, apply the optional [Xenos bin timing patch](patches/0003-vulkan-profile-xenos-bins.patch) from the FH2 repository root and rebuild. With `gpu_cronometro = true`, the SDK logs the GPU draw time per `SET_BIN_SELECT` mask. On Ace 5 at the Palace Square starting grid with Turnip v39, native render scale and 2× MSAA, complete windows after the HUD measured 62.7–63.9 ms of GPU work per frame. Draws accounted for 54.8–55.9 ms, split between mask `0x80000003` (27.4–28.1 ms), `0xC` (14.2–14.4 ms) and `0xFFFFFFFF` (13.2–13.5 ms). The masks describe selection state and do not by themselves identify a specific draw pass. The stage profiler does not measure Android composition. A separate timestamp run at the same grid showed about 99.9% device GPU activity in KGSL samples.

The optional [PM4 replay probe](patches/0004-profile-pgr4-pm4-bin-replay.patch) compares draw packet addresses and contents across the two tile masks. Enable `pgr4_profile_bin_replay = true` to sample one frame every 24 swaps. Eight frames after the Palace Square HUD on Ace 5 showed 3,019–3,464 shared color/depth packets per sampled frame: every packet under mask `0x80000003` was also under `0xC`, which had one additional packet. This identifies duplicated command packets. It does not show that both passes cover the same pixels or use identical render state. Reusing geometry work requires accounting for tile offsets, depth, resolves and sample coverage.

Removing the SDK's forced 4× anisotropic filtering (`anisotropic_override = -1`, preserving the guest's choices) gave 15.5, 15.5 and 17.0 FPS in complete windows after the HUD. Two windows overlapped the 15.5–15.7 FPS control and one was faster. Both runs started and ended at thermal status 1. This did not establish a consistent gain; the default sampler settings were restored.

A local single-pass probe merged those two rectangles, capped MSAA at 1× and relaxed draw predication. The title screen showed heavy corruption. The test device was returned to Turnip with the standard two-tile layout and 2× MSAA.

Follow-up measurements on Ace 5 (Turnip v39, 1280×720) showed:

- **Paired draw state.** Extending the PM4 replay probe to hash VS/PS shaders, shader constants, fetch constants and render state confirmed that 100% of paired predicated draws across `0x80000003` and `0xC` (2,995–3,440 per frame) share identical shader inputs and render state, differing only in `PA_SC_WINDOW_OFFSET` and `PA_SC_WINDOW_SCISSOR`.
- **Hoisted shared-memory uploads.** Render-pass diagnostics showed ~1,131 mid-pass `VkRenderPass` breaks per frame (488 in tile 0, 664 in `0xFFFFFFFF`), almost all caused by `VulkanSharedMemory::UploadRanges` ending the active render pass to copy dirty guest pages. Apply [0005-vulkan-hoist-shared-memory-uploads.patch](patches/0005-vulkan-hoist-shared-memory-uploads.patch) (which hoists safe uploads ahead of `CmdVkBeginRendering` / `CmdVkBeginRenderPass` using per-page read-generation tracking, and tightens `renderArea` to drawn scissor bounds). On Ace 5 with 2× MSAA, mid-pass barrier breaks dropped from 1,131 to 74–83 per frame, GPU time dropped from 60.6–64.2 ms to 47.9–50.2 ms, and clean Palace Square FPS rose from 15.6–16.4 to 19.0–19.1 FPS (52.3–52.6 ms average, thermal 0→0) with clean image output.
- **Scoped single-tile 1280×720 + resolve shortcut.** Enabling `pgr4_single_tile = true` (which merges only exact `(0,0,1280,480) + (0,480,1280,720)` `BeginTiling` calls without globally relaxing predication) together with `gpu_sin_msaa = true` (avoiding the 2× MSAA EDRAM color/depth overlap at base `0` vs `960`) eliminated tile 1 (`0xC`: 0 draws), reaching 27.3–28.8 FPS clean and 29.1–29.4 FPS with `gpu_atalho_resolve = true` (22.4 resolves/frame shortcutted, 0 misses). With `vsync = false` (removing the 30 FPS guest VBlank wait), stationary Palace Square reached **30.1–31.7 FPS** (31.5–33.3 ms average, worst 42.7 ms, thermal 1→1) with clean visuals.
- **Stage 1 & Stage 2 Vulkan optimizations.** Apply [0006-vulkan-stage1-stage2-optimizations.patch](patches/0006-vulkan-stage1-stage2-optimizations.patch) on top of SDK patches `0001`, `0003`, `0004`, and `0005` (`0005` requires `--ignore-whitespace` on LF checkouts):
  - **Stage 1** adds background worker preloading for Vulkan graphics pipelines (zero main-thread creations across all 485 pipelines during the Palace Square run), dynamic UBO/constant caching, texture descriptor caching, recent sampler caching, and dirty-range merging. On Ace 5 (`out\palace-stage1-fps`, thermal 0→0): **29.6–32.6 FPS** (30.7–33.8 ms average). Diagnostic pass (`out\palace-stage1-diag`): GPU 30.9–33.5 ms, CPU command processor 24.1–26.7 ms, `end_bar=72.1–79.7`, `atalho=22.8–23.0` hits / 0 misses, `cargas de textura=1.5–1.6 ms`. An X-Ray trace (`out\palace-stage1-xray`) mapped all 5,115 draws per frame (cubemap, 4 shadow cascades, 320×180 reflection pass, main 720p scene, post-FX/bloom, HUD/swap).
  - **Stage 2** adds unified pre-render-pass hoisting for safe texture loads and image layout barriers (`DeferredCommandBuffer::pre_rp_streams_`), extended `Atalho` format/pitch matching (`xenos::GetStorageColorFormat`, `GetPitchTiles()`) with Direct-to-Texture `Atalho` writes, `gpu_sin_msaa` 1× MSAA capping on the `pitch == 320` reflection surface, `vulkan_discard_overwrite_transfers`, and `gpu_cronometro`-gated CPU `IssueDraw` stage timing. On Ace 5 (`out\palace-stage2-fps`, Turnip v39, 1280×720, thermal 0→0): **31.4–32.9 FPS** (30.4–31.9 ms average, worst 40.0–46.8 ms) with clean visuals and **30.0** resolved shortcut textures/frame (0 misses). Diagnostic breakdown (`out\palace-stage2-diag`, thermal 0→0): GPU **30.0–31.5 ms** (`desenho 23.6–24.9 ms`, `transferencias 3.0–3.1 ms`, `atalho 2.0 ms`, `cargas de textura 0.2–0.3 ms`; `begin A=6.5–6.6`, `end_bar=61.7–65.0`), CPU command processor **26.8–28.0 ms** (`bind=6.44–6.57 ms`, `vb_emit=5.31–5.54 ms`, `prim=3.01–3.33 ms`, `sh_samp=1.96–2.03 ms`, `req_tex=1.82–1.94 ms`, `rt_upd=1.50–1.56 ms`, `dyn_sys=0.83–0.86 ms`, `pipe=0.64–0.68 ms`).
  - **Stage 3** ([0007-vulkan-stage3-optimizations.patch](patches/0007-vulkan-stage3-optimizations.patch), applied via `py -3 android/tools/sdk_patches.py --apply`) makes the texture and dynamic-constant descriptor caches persistent across frames (invalidated safely on `VulkanTextureCache::image_view_destroy_generation()`), batches per-draw vertex buffer `SharedMemory::RequestRanges` calls with a single-range fast path, caches `CmdVkBindIndexBuffer` and `use_dynamic_rendering_`, copies contiguous float constants in runs, caps 4× MSAA on the `pitch == 280` car cubemap when `RB_COLOR_MASK != 0`, and fixes `is_single_rect_draw` + `can_discard_rt` for pre-transformed clears and scratch post-FX/mip passes (`pitch <= 320`). On Ace 5 (`out\palace-stage3-fps`, Turnip v39, 1280×720, thermal 1→1): **31.9–33.7 FPS** (29.7–31.4 ms average, CPU command processor **22.5–23.7 ms**) with clean visuals. Diagnostic breakdown (`out\palace-stage3-diag`, thermal 1→1): GPU **28.9–30.7 ms** (`desenho 23.2–24.8 ms`, `transferencias 2.6–2.8 ms` at `28.8–32.0` calls, `atalho 1.9–2.0 ms`, `dump+copia 0.5 ms`), CPU `bind` cut in half to **3.26–3.49 ms** with **`tex_writes = 0.0`**.

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
