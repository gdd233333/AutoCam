cmake_minimum_required(VERSION 3.22)
project(autocam_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(AUTOCAM_BUILD_JNI "Build libautocam JNI shared library" OFF)
option(AUTOCAM_BUILD_TESTS "Build googletest host tests" OFF)

add_library(autocam_core STATIC src/add.cpp)
target_include_directories(autocam_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
set_target_properties(autocam_core PROPERTIES POSITION_INDEPENDENT_CODE ON)

if(AUTOCAM_BUILD_JNI)
    add_library(autocam SHARED ${CMAKE_CURRENT_SOURCE_DIR}/../../apps/android/app/src/main/cpp/jni_bridge.cpp)
    target_link_libraries(autocam PRIVATE autocam_core)
    target_include_directories(autocam PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)
    if(ANDROID)
        find_library(log-lib log)
        if(log-lib)
            target_link_libraries(autocam PRIVATE ${log-lib})
        endif()
    endif()
endif()

if(AUTOCAM_BUILD_TESTS)
    include(FetchContent)
    FetchContent_Declare(
        googletest
        URL https://github.com/google/googletest/archive/refs/tags/v1.15.2.zip
        DOWNLOAD_EXTRACT_TIMESTAMP TRUE
    )
    set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
    FetchContent_MakeAvailable(googletest)
    enable_testing()
    add_executable(autocam_core_tests tests/add_test.cpp)
    target_link_libraries(autocam_core_tests PRIVATE autocam_core GTest::gtest_main)
    include(GoogleTest)
    gtest_discover_tests(autocam_core_tests)
endif()
