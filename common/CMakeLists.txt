#
# CMakeLists.txt
#
# Copyright (c) 2017 Couchbase, Inc All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.25)

project (LiteCoreJNI)

# This script is only for Android
if (NOT ${CMAKE_SYSTEM_NAME} STREQUAL "Android")
    MESSAGE( FATAL_ERROR "!!!UNEXPECTED SYSTEM (expecting 'Android'): "     ${CMAKE_SYSTEM_NAME} )
endif()

# Handy debugging code
#get_cmake_property(_variableNames VARIABLES)
#list (SORT _variableNames)
#foreach (_variableName ${_variableNames})
#    message(STATUS "${_variableName}=${${_variableName}}")
#endforeach()

message( STATUS "ABI: "                   ${ANDROID_ABI} )
message( STATUS "SOURCE DIR: "            "${PROJECT_SOURCE_DIR}" )
message( STATUS "OUTPUT DIR: "            ${CMAKE_LIBRARY_OUTPUT_DIRECTORY} )
message( STATUS "FLAG: "                  ${WHOLE_LIBRARY_FLAG} )

set(JNI_SRC main/cpp)

set(COMPILE_FLAGS   "${COMPILE_FLAGS} -Wall -Werror")
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

if(BUILD_ENTERPRISE)
    add_definitions(-DCOUCHBASE_ENTERPRISE)
    set(BUILD_ROOT "${PROJECT_SOURCE_DIR}/../../ee/android")
else()
    set(BUILD_ROOT "${PROJECT_SOURCE_DIR}/../../ce/android")
endif()

add_library( LiteCore SHARED IMPORTED )
set_property( TARGET LiteCore PROPERTY
    IMPORTED_LOCATION "${PROJECT_SOURCE_DIR}/../lite-core/android/${ANDROID_ABI}/lib/libLiteCore.so"
)

add_library( LiteCoreJNI SHARED
    ${JNI_SRC}/native_c4.cc
    ${JNI_SRC}/native_c4blobstore.cc
    ${JNI_SRC}/native_c4collection.cc
    ${JNI_SRC}/native_c4database.cc
    ${JNI_SRC}/native_c4document.cc
    ${JNI_SRC}/native_c4index.cc
    ${JNI_SRC}/native_c4listener.cc
    ${JNI_SRC}/native_c4multipeerreplicator.cc
    ${JNI_SRC}/native_c4observer.cc
    ${JNI_SRC}/native_c4prediction.cc
    ${JNI_SRC}/native_c4query.cc
    ${JNI_SRC}/native_c4queryenumerator.cc
    ${JNI_SRC}/native_c4replicator.cc
    ${JNI_SRC}/native_c4replutils.cc
    ${JNI_SRC}/native_c4socket.cc
    ${JNI_SRC}/native_c4testutils.cc
    ${JNI_SRC}/native_fleece.cc
    ${JNI_SRC}/native_flencoder.cc
    ${JNI_SRC}/native_glue.cc
)

target_include_directories( LiteCoreJNI PUBLIC
    ${JNI_SRC}/
    "${PROJECT_SOURCE_DIR}/../lite-core/android/${ANDROID_ABI}/include"
)

set_target_properties(
    LiteCoreJNI PROPERTIES LINK_FLAGS
    "-Wl,-z,max-page-size=16384,--version-script=${BUILD_ROOT}/etc/ld/jni_android.ld"
)

target_link_libraries( LiteCoreJNI
    LiteCore
    "log"
)

