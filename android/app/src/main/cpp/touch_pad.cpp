#include <jni.h>
#include <dlfcn.h>

#include <atomic>

#include <rex/input/sdl/sdl_input_driver.h>

extern "C" JNIEXPORT void JNICALL Java_dev_pgr4port_android_TouchPad_nativeSetState(
    JNIEnv*, jclass, jint buttons, jint left_x, jint brake, jint throttle) {
  rex_sdl_set_touch_gamepad_state(static_cast<uint16_t>(buttons),
                                 static_cast<int16_t>(left_x), 0, 0, 0,
                                 static_cast<uint8_t>(brake),
                                 static_cast<uint8_t>(throttle));
}

extern "C" JNIEXPORT jfloat JNICALL Java_dev_pgr4port_android_PerformanceHud_nativeGuestFps(
    JNIEnv*, jclass) {
  // The SDK loads the Xenos plugin after the activity starts. Never load it here.
  void* plugin = dlopen("librexgpu-xenos.so", RTLD_NOW | RTLD_NOLOAD);
  if (!plugin) {
    return -1.0f;
  }
  auto* fps = static_cast<const std::atomic<float>*>(dlsym(plugin, "rex_fps_guest"));
  float result = fps ? fps->load(std::memory_order_relaxed) : -1.0f;
  dlclose(plugin);
  return result;
}
