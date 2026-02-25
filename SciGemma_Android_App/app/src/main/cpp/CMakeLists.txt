cmake_minimum_required(VERSION 3.22)
project(llama_android)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# ---- llama.cpp build options ----
# Disable features not needed on Android
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
set(GGML_METAL           OFF CACHE BOOL "" FORCE)  # iOS only
set(GGML_CUDA            OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# Add llama.cpp as a static library (git submodule).
# If this fails, run inside SciGemma_Android_App/:
#   git submodule add https://github.com/ggerganov/llama.cpp app/src/main/cpp/llama.cpp
#   git submodule update --init --recursive
if(NOT EXISTS "${CMAKE_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp submodule is empty or missing.\n"
        "Run inside SciGemma_Android_App/:\n"
        "  git submodule add https://github.com/ggerganov/llama.cpp app/src/main/cpp/llama.cpp\n"
        "  git submodule update --init --recursive"
    )
endif()
add_subdirectory(${CMAKE_SOURCE_DIR}/llama.cpp ${CMAKE_BINARY_DIR}/llama_build)

# ---- JNI bridge shared library ----
add_library(llama_bridge SHARED llama_bridge.cpp)

target_link_libraries(llama_bridge
    llama
    android
    log)

target_include_directories(llama_bridge PRIVATE
    ${CMAKE_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_SOURCE_DIR}/llama.cpp/ggml/include
    ${CMAKE_SOURCE_DIR}/llama.cpp)
