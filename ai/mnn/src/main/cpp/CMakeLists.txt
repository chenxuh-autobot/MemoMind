cmake_minimum_required(VERSION 3.22.1)

project(creative_ai_mnn_bridge)

set(MNN_PREBUILT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/third_party/mnn)
set(MNN_PREBUILT_INCLUDE ${MNN_PREBUILT_ROOT}/include)
set(MNN_PREBUILT_HEADER ${MNN_PREBUILT_INCLUDE}/MNN/Interpreter.hpp)
set(MNN_PREBUILT_LLM_HEADER ${MNN_PREBUILT_INCLUDE}/llm/llm.hpp)
set(MNN_PREBUILT_LIB ${MNN_PREBUILT_ROOT}/lib/${ANDROID_ABI}/libMNN.so)

if(EXISTS ${MNN_PREBUILT_HEADER} AND EXISTS ${MNN_PREBUILT_LIB})
    message(STATUS "creative_ai_mnn_bridge: using real MNN prebuilt ${MNN_PREBUILT_LIB}")
    add_definitions(-DHAS_REAL_MNN=1)
    if(EXISTS ${MNN_PREBUILT_LLM_HEADER})
        message(STATUS "creative_ai_mnn_bridge: llm api headers detected")
        add_definitions(-DHAS_REAL_MNN_LLM=1)
    else()
        message(STATUS "creative_ai_mnn_bridge: llm api headers missing, falling back to graph probe only")
    endif()
    add_library(MNN SHARED IMPORTED)
    set_target_properties(MNN PROPERTIES IMPORTED_LOCATION ${MNN_PREBUILT_LIB})
else()
    message(STATUS "creative_ai_mnn_bridge: using stub MNN bridge")
endif()

add_library(
    creative_ai_mnn_bridge
    SHARED
    native_mnn_bridge.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    creative_ai_mnn_bridge
    ${log-lib}
)

if(TARGET MNN)
    target_include_directories(
        creative_ai_mnn_bridge
        PRIVATE
        ${MNN_PREBUILT_INCLUDE}
    )
    target_link_libraries(
        creative_ai_mnn_bridge
        MNN
    )
endif()
