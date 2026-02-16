cmake_minimum_required(VERSION 3.22.1)

project(quickjs_jni)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DCONFIG_BIGNUM -DCONFIG_VERSION=\\\"2024-01-13\\\"")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")

# QuickJS source files
set(QUICKJS_SOURCES
    ${CMAKE_SOURCE_DIR}/quickjs/quickjs.c
    ${CMAKE_SOURCE_DIR}/quickjs/libbf.c
    ${CMAKE_SOURCE_DIR}/quickjs/libregexp.c
    ${CMAKE_SOURCE_DIR}/quickjs/libunicode.c
    ${CMAKE_SOURCE_DIR}/quickjs/cutils.c
    ${CMAKE_SOURCE_DIR}/quickjs/quickjs-libc.c
)

# JNI wrapper
add_library(quickjs_jni SHARED
    quickjs_jni.cpp
    ${QUICKJS_SOURCES}
)

target_include_directories(quickjs_jni PRIVATE
    ${CMAKE_SOURCE_DIR}/quickjs
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(quickjs_jni
    ${log-lib}
    ${android-lib}
    m
)
