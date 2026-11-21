#include <jni.h>
#include <dlfcn.h>

#include <atomic>

#include <rex/input/sdl/sdl_input_driver.h>

extern "C" JNIEXPORT void JNICALL Java_dev_pgr4port_android_TouchPad_nativeSetState(
    JNIEnv*, jclass, jint buttons, jint left_x, jint left_y, jint brake, jint throttle) {
  rex_sdl_set_touch_gamepad_state(static_cast<uint16_t>(buttons),
                                 static_cast<int16_t>(left_x),
                                 static_cast<int16_t>(left_y), 0, 0,
                                 static_cast<uint8_t>(brake),
                                 static_cast<uint8_t>(throttle));
}

extern "C" JNIEXPORT jfloat JNICALL Java_dev_pgr4port_android_PerformanceHud_nativeGuestFps(
    JNIEnv*, jclass) {
  static std::atomic<const std::atomic<float>*> cached_fps{nullptr};
  const auto* fps = cached_fps.load(std::memory_order_acquire);
  if (!fps) {
    // The SDK loads the Xenos plugin after the activity starts. Never load it here.
    void* plugin = dlopen("librexgpu-xenos.so", RTLD_NOW | RTLD_NOLOAD);
    if (!plugin) {
      return -1.0f;
    }
    fps = static_cast<const std::atomic<float>*>(dlsym(plugin, "rex_fps_guest"));
    if (!fps) {
      dlclose(plugin);
      return -1.0f;
    }
    // Keep the plugin referenced once the symbol is cached.
    cached_fps.store(fps, std::memory_order_release);
  }
  return fps->load(std::memory_order_relaxed);
}
