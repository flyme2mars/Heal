cmake_minimum_required(VERSION 3.22.1)

project("medgemma-native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DGGML_NEON=ON -DGGML_I8MM=ON -DGGML_OPENMP=OFF")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -DGGML_NEON=ON -DGGML_I8MM=ON -DGGML_OPENMP=OFF")

# Target specific ARM architecture features if available
# Gemma 3/MedGemma models benefit heavily from I8MM (Integer Matrix Multiplication)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.4-a+dotprod+i8mm")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.4-a+dotprod+i8mm")

set(LLAMA_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# Add llama.cpp core
add_subdirectory(${LLAMA_SRC_DIR} llama-build)

# Collect all mtmd source files
file(GLOB MTMD_SOURCES 
    "${LLAMA_SRC_DIR}/tools/mtmd/*.cpp"
    "${LLAMA_SRC_DIR}/tools/mtmd/models/*.cpp"
)

# Filter out the CLI/main files if they exist in the glob
list(FILTER MTMD_SOURCES EXCLUDE REGEX "mtmd-cli.cpp$")
list(FILTER MTMD_SOURCES EXCLUDE REGEX "mtmd-debug.cpp$")

# Add our JNI library with all required multimodal sources
add_library(medgemma-native SHARED
        medgemma-jni.cpp
        ${MTMD_SOURCES}
)

target_include_directories(medgemma-native PRIVATE
        ${LLAMA_SRC_DIR}/include
        ${LLAMA_SRC_DIR}/ggml/include
        ${LLAMA_SRC_DIR}/common
        ${LLAMA_SRC_DIR}/tools/mtmd
        ${LLAMA_SRC_DIR}/tools/mtmd/models
        ${LLAMA_SRC_DIR}/vendor
)

# Link against the core llama and system libs
target_link_libraries(medgemma-native
        llama
        android
        log
)
