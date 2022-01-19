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

cmake_minimum_required(VERSION 3.11)

project (LiteCoreJNI)

set(LITE_CORE_LIB ${PROJECT_SOURCE_DIR}/../lite-core/android/${ANDROID_ABI}/libLiteCore.so)
set(LITE_CORE_SRC ${PROJECT_SOURCE_DIR}/../../core)
set(JNI_DIR main/cpp)

# This script is only for Android
if (NOT ${CMAKE_SYSTEM_NAME} STREQUAL "Android")
    MESSAGE( FATAL_ERROR "!!!UNEXPECTED SYSTEM (expecting 'Android'): "     ${CMAKE_SYSTEM_NAME} )
endif()

MESSAGE( STATUS "ABI: "                   ${ANDROID_ABI} )
MESSAGE( STATUS "OUTPUT DIR: "            ${CMAKE_LIBRARY_OUTPUT_DIRECTORY} )

set(COMPILE_FLAGS   "${COMPILE_FLAGS}   -Wall -Werror")
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

if(BUILD_ENTERPRISE)
    add_definitions(-DCOUCHBASE_ENTERPRISE)
endif()

add_library( LiteCoreJNI SHARED
    ${JNI_DIR}/native_c4.cc
    ${JNI_DIR}/native_c4base.cc
    ${JNI_DIR}/native_c4blobstore.cc
    ${JNI_DIR}/native_c4database.cc
    ${JNI_DIR}/native_c4docenumerator.cc
    ${JNI_DIR}/native_c4document.cc
    ${JNI_DIR}/native_c4fulltextmatch.cc
    ${JNI_DIR}/native_c4listener.cc
    ${JNI_DIR}/native_c4observer.cc
    ${JNI_DIR}/native_c4prediction.cc
    ${JNI_DIR}/native_c4query.cc
    ${JNI_DIR}/native_c4queryenumerator.cc
    ${JNI_DIR}/native_c4rawdocument.cc
    ${JNI_DIR}/native_c4replicator.cc
    ${JNI_DIR}/native_c4socket.cc
    ${JNI_DIR}/native_fleece.cc
    ${JNI_DIR}/native_flencoder.cc
    ${JNI_DIR}/native_glue.cc
)

target_include_directories( LiteCoreJNI PUBLIC
    ${JNI_DIR}/
    ${LITE_CORE_SRC}/C/                   # include c4Private.h for debugging
    ${LITE_CORE_SRC}/C/include/
    ${LITE_CORE_SRC}/vendor/fleece/API/)

target_link_libraries( LiteCoreJNI
    ${WHOLE_LIBRARY_FLAG}
    ${LITE_CORE_LIB}
    ${NO_WHOLE_LIBRARY_FLAG}
    "log")


