cmake_minimum_required(VERSION 3.22.1)
project(budsia_audio LANGUAGES CXX)

find_package(oboe REQUIRED CONFIG)

add_library(
    budsia_audio
    SHARED
    JniBridge.cpp
    NativeAudioEngine.cpp
)

target_include_directories(
    budsia_audio
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_features(budsia_audio PRIVATE cxx_std_20)

target_compile_options(
    budsia_audio
    PRIVATE
    -Wall
    -Wextra
    -Wpedantic
)

target_link_libraries(
    budsia_audio
    PRIVATE
    oboe::oboe
    android
    log
)
