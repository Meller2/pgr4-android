#include <jni.h>

#include <rex/input/sdl/sdl_input_driver.h>

extern "C" JNIEXPORT void JNICALL Java_dev_pgr4port_android_TouchPad_nativeSetState(
    JNIEnv*, jclass, jint buttons, jint left_x, jint brake, jint throttle) {
  rex_sdl_set_touch_gamepad_state(static_cast<uint16_t>(buttons),
                                 static_cast<int16_t>(left_x), 0, 0, 0,
                                 static_cast<uint8_t>(brake),
                                 static_cast<uint8_t>(throttle));
}
