#include "generated/default/pgr4_recompiled_pch.h"

#include <atomic>
#include <iterator>
#include <string>

#include <fmt/format.h>
#include <rex/cvar.h>
#include <rex/hook.h>
#include <rex/logging.h>

REXCVAR_DEFINE_BOOL(pgr4_trace_tiling, false, "PGR4",
                    "Log the first 24 multi-tile BeginTiling calls on the base disc");

REX_EXTERN(__imp__sub_8269C6D0);

// Base-disc D3D BeginTiling. r5 is the count and r6 points to the rectangle array.
REX_HOOK_RAW(sub_8269C6D0) {
  if (REXCVAR_GET(pgr4_trace_tiling)) {
    const uint32_t count = ctx.r5.u32;
    const uint32_t rectangles = ctx.r6.u32;
    static std::atomic<uint32_t> calls{0};
    if (count >= 2 && count <= 16 && rectangles &&
        calls.fetch_add(1, std::memory_order_relaxed) < 24) {
      std::string bounds;
      for (uint32_t i = 0; i < count; ++i) {
        const uint32_t address = rectangles + i * 16;
        fmt::format_to(std::back_inserter(bounds), " ({},{} {}x{})", REX_LOAD_U32(address),
                       REX_LOAD_U32(address + 4), REX_LOAD_U32(address + 8),
                       REX_LOAD_U32(address + 12));
      }
      REXLOG_INFO("[pgr4] BeginTiling {} rectangles (left,top right,bottom):{}", count, bounds);
    }
  }
  __imp__sub_8269C6D0(ctx, base);
}
