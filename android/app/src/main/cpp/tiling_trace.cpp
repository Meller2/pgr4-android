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
REXCVAR_DEFINE_BOOL(pgr4_single_tile, false, "PGR4",
                    "Merge the 1280x480 + 1280x240 two-tile scene into a single 1280x720 tile");

REX_EXTERN(__imp__sub_8269C6D0);

// Base-disc D3D BeginTiling. r5 is the count and r6 points to the rectangle array.
REX_HOOK_RAW(sub_8269C6D0) {
  const uint32_t count = ctx.r5.u32;
  const uint32_t rectangles = ctx.r6.u32;
  if (REXCVAR_GET(pgr4_trace_tiling)) {
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
  if (REXCVAR_GET(pgr4_single_tile) && count == 2 && rectangles) {
    const uint32_t x1_0 = REX_LOAD_U32(rectangles + 0);
    const uint32_t y1_0 = REX_LOAD_U32(rectangles + 4);
    const uint32_t x2_0 = REX_LOAD_U32(rectangles + 8);
    const uint32_t y2_0 = REX_LOAD_U32(rectangles + 12);
    const uint32_t x1_1 = REX_LOAD_U32(rectangles + 16);
    const uint32_t y1_1 = REX_LOAD_U32(rectangles + 20);
    const uint32_t x2_1 = REX_LOAD_U32(rectangles + 24);
    const uint32_t y2_1 = REX_LOAD_U32(rectangles + 28);
    if (x1_0 == 0 && y1_0 == 0 && x2_0 == 1280 && y2_0 == 480 && x1_1 == 0 && y1_1 == 480 &&
        x2_1 == 1280 && y2_1 == 720) {
      REX_STORE_U32(rectangles + 12, 720u);
      ctx.r5.u64 = 1;
      __imp__sub_8269C6D0(ctx, base);
      REX_STORE_U32(rectangles + 12, 480u);
      return;
    }
  }
  __imp__sub_8269C6D0(ctx, base);
}
