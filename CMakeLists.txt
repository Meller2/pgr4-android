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
    set_property(GLOBAL APPEND PROPERTY JOB_POOLS pgr4_compile=2 pgr4_link=1)
    set(CMAKE_JOB_POOL_COMPILE pgr4_compile)
    set(CMAKE_JOB_POOL_LINK pgr4_link)
    set(REXGLUE_USE_VULKAN ON CACHE BOOL "Android Vulkan backend" FORCE)
    set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "Disable Tracy on Android" FORCE)
    set(REXGLUE_VERSION_OVERRIDE "0.10.0-dev.android" CACHE STRING "Android SDK version")
    set(REXGLUE_RECOMP_DEBUG_INFO "none" CACHE STRING "Guest debug information")
endif()

include(generated/rexglue.cmake)

# Sources
set(PGR4RECOMPILED_SOURCES
    src/main.cpp
)

if(ANDROID)
    add_library(pgr4_recompiled SHARED ${PGR4RECOMPILED_SOURCES})
    set_target_properties(pgr4_recompiled PROPERTIES OUTPUT_NAME main)
    target_link_options(pgr4_recompiled PRIVATE
        "LINKER:-z,max-page-size=16384" "LINKER:-Bsymbolic-functions")
    set_target_properties(SDL3-shared PROPERTIES
        OUTPUT_NAME SDL3 DEBUG_POSTFIX "" RELEASE_POSTFIX "" RELWITHDEBINFO_POSTFIX "")
elseif(WIN32)
    add_executable(pgr4_recompiled WIN32 ${PGR4RECOMPILED_SOURCES})
else()
    add_executable(pgr4_recompiled ${PGR4RECOMPILED_SOURCES})
endif()

if(ANDROID)
    rexglue_setup_target(pgr4_recompiled GPU_PLUGINS xenos)
    target_include_directories(pgr4_recompiled PRIVATE
        $<TARGET_PROPERTY:imgui,INTERFACE_INCLUDE_DIRECTORIES>
        $<TARGET_PROPERTY:renderdoc,INTERFACE_INCLUDE_DIRECTORIES>)
else()
    rexglue_setup_target(pgr4_recompiled)
endif()
