# pgr4_recompiled - ReXGlue Recompiled Project
#
# SDK boilerplate lives in generated/rexglue.cmake.

cmake_minimum_required(VERSION 3.25)
project(pgr4_recompiled LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(ANDROID)
    if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
        message(FATAL_ERROR "PGR4 Android supports arm64-v8a")
    endif()
    if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/generated/default/sources.cmake")
        message(FATAL_ERROR "Run the host ReXGlue code generator first. See android/README.md.")
    endif()
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
    set(PGR4_ANDROID_COMPILE_JOBS 2 CACHE STRING "Parallel Android compiler processes")
    set_property(GLOBAL APPEND PROPERTY JOB_POOLS
        pgr4_compile=${PGR4_ANDROID_COMPILE_JOBS} pgr4_link=1)
    set(CMAKE_JOB_POOL_COMPILE pgr4_compile)
    set(CMAKE_JOB_POOL_LINK pgr4_link)
    set(REXGLUE_USE_VULKAN ON CACHE BOOL "Android Vulkan backend" FORCE)
    set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "Disable Tracy on Android" FORCE)
    set(REXGLUE_VERSION_OVERRIDE "0.10.0-dev.android" CACHE STRING "Android SDK version")
    if(NOT EXISTS "${REXSDK_DIR}/CMakeLists.txt")
        message(FATAL_ERROR "Set REXSDK_DIR to the Android-capable ReXGlue source tree.")
    endif()
    # The v0.10.0 host emits a CMake file that tries to rerun its desktop
    # generator. Android consumes only the already generated source list.
    add_subdirectory("${REXSDK_DIR}" rexglue-sdk)
    include(generated/default/sources.cmake)
else()
    include(generated/rexglue.cmake)
endif()

# Sources
set(PGR4RECOMPILED_SOURCES
    src/main.cpp
)

if(ANDROID)
    add_library(pgr4_recompiled SHARED ${PGR4RECOMPILED_SOURCES}
        android/app/src/main/cpp/ring_wait.cpp
        android/app/src/main/cpp/touch_pad.cpp
        android/app/src/main/cpp/tiling_trace.cpp)
    set_target_properties(pgr4_recompiled PROPERTIES OUTPUT_NAME main)
    target_link_options(pgr4_recompiled PRIVATE
        "LINKER:-z,max-page-size=16384" "LINKER:-Bsymbolic-functions")
    set_target_properties(SDL3-shared PROPERTIES
        OUTPUT_NAME SDL3 DEBUG_POSTFIX "" RELEASE_POSTFIX "" RELWITHDEBINFO_POSTFIX "")
    set_target_properties(main_hook hook_impl file_redirect_hook gsl_alloc_hook
        PROPERTIES DEBUG_POSTFIX "" LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/driver-hooks")
    add_library(pgr4_probe SHARED android/app/src/main/cpp/vulkan_probe.cpp)
    target_include_directories(pgr4_probe PRIVATE "${REXSDK_DIR}/include")
    target_link_libraries(pgr4_probe PRIVATE rex_android_gpu_driver dl)
    target_link_options(pgr4_probe PRIVATE "LINKER:-z,max-page-size=16384")
elseif(WIN32)
    add_executable(pgr4_recompiled WIN32 ${PGR4RECOMPILED_SOURCES})
else()
    add_executable(pgr4_recompiled ${PGR4RECOMPILED_SOURCES})
endif()

if(ANDROID)
    set(PGR4_GENERATED_DIR "${CMAKE_CURRENT_SOURCE_DIR}/generated/default")
    add_library(pgr4_recompiled_guest OBJECT ${GENERATED_SOURCES})
    target_link_libraries(pgr4_recompiled_guest PRIVATE rex::runtime)
    target_include_directories(pgr4_recompiled_guest PRIVATE "${PGR4_GENERATED_DIR}")
    rexglue_apply_target_settings(pgr4_recompiled_guest)
    target_precompile_headers(pgr4_recompiled_guest PRIVATE
        "${PGR4_GENERATED_DIR}/pgr4_recompiled_pch.h")
    target_compile_options(pgr4_recompiled_guest PRIVATE
        -g0 -ffp-contract=off $<$<CONFIG:Release>:-O2>)

    target_link_libraries(pgr4_recompiled PRIVATE pgr4_recompiled_guest rex::runtime)
    target_include_directories(pgr4_recompiled PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}" "${PGR4_GENERATED_DIR}"
        $<TARGET_PROPERTY:imgui,INTERFACE_INCLUDE_DIRECTORIES>
        $<TARGET_PROPERTY:renderdoc,INTERFACE_INCLUDE_DIRECTORIES>)
    rexglue_configure_target(pgr4_recompiled GPU_PLUGINS xenos)
else()
    rexglue_setup_target(pgr4_recompiled)
endif()
