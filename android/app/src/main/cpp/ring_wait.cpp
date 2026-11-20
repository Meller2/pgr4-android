#include "generated/default/pgr4_recompiled_pch.h"

#include <atomic>
#include <dlfcn.h>
#include <linux/futex.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

#include <rex/hook.h>

REX_EXTERN(__imp__sub_82697970);

namespace {

struct ReadPointerSignal {
  std::atomic<uint32_t>* sequence = nullptr;
  std::atomic<uint32_t>* waiters = nullptr;
};

ReadPointerSignal FindReadPointerSignal() {
  void* plugin = dlopen("librexgpu-xenos.so", RTLD_NOW | RTLD_NOLOAD);
  if (!plugin) {
    return {};
  }
  // Keep the plugin referenced for the lifetime of these pointers.
  return {static_cast<std::atomic<uint32_t>*>(dlsym(plugin, "rex_cp_rptr_seq")),
          static_cast<std::atomic<uint32_t>*>(dlsym(plugin, "rex_cp_rptr_waiters"))};
}

}  // namespace

REX_HOOK_RAW(sub_82697970) {
  // Base-disc ring space loop in sub_82698B80. Other callers retain their timing.
  if (ctx.lr == 0x82698C10) {
    static const ReadPointerSignal signal = FindReadPointerSignal();
    if (signal.sequence && signal.waiters) {
      const uint32_t state = ctx.r3.u32;
      const uint32_t device = REX_LOAD_U32(state);
      // If the guest has already flagged the device, let its error path run immediately.
      if (!(REX_LOAD_U8(device + 10941) & 2)) {
        const uint32_t pointer_address = REX_LOAD_U32(device + 10896);
        const uint32_t previous = REX_LOAD_U32(state + 8);
        signal.waiters->fetch_add(1, std::memory_order_seq_cst);
        const uint32_t sequence = signal.sequence->load(std::memory_order_seq_cst);
        if (REX_LOAD_U32(pointer_address) == previous) {
          // A write between the check and the syscall changes the futex value.
          // The short timeout also covers ring stalls and interrupts.
          const timespec limit{0, 1'000'000};
          syscall(SYS_futex, signal.sequence, FUTEX_WAIT_PRIVATE, sequence, &limit, nullptr, 0);
        }
        signal.waiters->fetch_sub(1, std::memory_order_seq_cst);
      }
    }
  }
  __imp__sub_82697970(ctx, base);
}
