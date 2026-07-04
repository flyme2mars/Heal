cmake_minimum_required(VERSION 3.22.1)

project("medgemma-native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# Android arm64: KleidiAI + I8MM dotprod (llama.android reference)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "Enable KleidiAI" FORCE)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.4-a+dotprod+i8mm")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.4-a+dotprod+i8mm")
endif()

set(GGML_VULKAN OFF CACHE BOOL "Disable Vulkan" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "Disable OpenCL" FORCE)
set(GGML_OPENMP ON CACHE BOOL "Enable OpenMP for CPU matmul" FORCE)
set(GGML_I8MM ON CACHE BOOL "Enable I8MM" FORCE)
# REPACK duplicates the model in RAM (~2.3GB extra) and slows decode on mobile
set(GGML_CPU_REPACK OFF CACHE BOOL "Disable runtime weight repack" FORCE)

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "Skip common lib (not needed)" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "Skip CLI tools" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Skip examples" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Skip tests" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "Build mtmd lib" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "Disable ffmpeg video on Android" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "No OpenSSL on Android" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Shared libs" FORCE)

add_subdirectory(${LLAMA_SRC_DIR} llama-build)

add_library(medgemma-native SHARED medgemma-jni.cpp)

target_include_directories(medgemma-native PRIVATE
        ${LLAMA_SRC_DIR}/include
        ${LLAMA_SRC_DIR}/ggml/include
        ${LLAMA_SRC_DIR}/common
        ${LLAMA_SRC_DIR}/tools/mtmd
        ${LLAMA_SRC_DIR}/vendor
)

target_link_libraries(medgemma-native
        mtmd
        llama
        android
        log
)