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

set(LITE_CORE_ROOT ${PROJECT_SOURCE_DIR}/../../core)

find_package(Java COMPONENTS Development)
include (UseJava)

# This script is only for Android
if (NOT ${CMAKE_SYSTEM_NAME} STREQUAL "Android")
    MESSAGE( FATAL_ERROR "!!!UNEXPECTED SYSTEM (expecting 'Android': "     ${CMAKE_SYSTEM_NAME} )
endif()

MESSAGE( STATUS "JAVA FOUND: "            ${Java_FOUND} )
MESSAGE( STATUS "JAVA INCLUDE DIRS: "     ${Java_INCLUDE_DIRS} )
MESSAGE( STATUS "JAVA INCLUDE PATH: "     ${JAVA_INCLUDE_PATH} )
MESSAGE( STATUS "JAVA INCLUDE PATH2: "    ${JAVA_INCLUDE_PATH2} )
MESSAGE( STATUS "CORE ROOT: " ${LITE_CORE_ROOT} )
MESSAGE( STATUS "OUTPUT DIR: " ${CMAKE_LIBRARY_OUTPUT_DIRECTORY} )

set(COMPILE_FLAGS   "${COMPILE_FLAGS}   -Wall -Werror")
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

#
# JNI for litecore - shared library including litecore static library
#
include_directories(
    ${JNI_OUTPUT_DIR}
    ${LITE_CORE_ROOT}/C/                                  # include c4Private.h for debugging
    ${LITE_CORE_ROOT}/C/include/
    ${LITE_CORE_ROOT}/LiteCore/Storage/
    ${LITE_CORE_ROOT}/LiteCore/Support/
    ${LITE_CORE_ROOT}/vendor/fleece/Fleece/
    ${LITE_CORE_ROOT}/vendor/fleece/Fleece/Mutable
    ${LITE_CORE_ROOT}/vendor/fleece/API
    ${LITE_CORE_ROOT}/vendor/fleece/Fleece/Core
    ${LITE_CORE_ROOT}/vendor/fleece/Fleece/Mutable
    ${LITE_CORE_ROOT}/vendor/fleece/Fleece/Support
    ${LITE_CORE_ROOT}/vendor/SQLiteCpp/include
    ${LITE_CORE_ROOT}/vendor/SQLiteCpp/sqlite3
    ${LITE_CORE_ROOT}/vendor/sqlite3-unicodesn
    ${LITE_CORE_ROOT}/vendor/mbedtls/include
    ${LITE_CORE_ROOT}/vendor/mbedtls/crypto/include)

if(BUILD_ENTERPRISE)
    add_definitions(-DCOUCHBASE_ENTERPRISE      # Tells LiteCore it's an EE build
                    -DSQLITE_HAS_CODEC)         # Enables SQLite encryption extension (SEE)
endif()

aux_source_directory(./main/cpp JNI_SRC)
add_library(LiteCoreJNI SHARED ${JNI_SRC})

add_subdirectory(${LITE_CORE_ROOT} ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})

target_link_libraries(  LiteCoreJNI
                        ${WHOLE_LIBRARY_FLAG}
                        LiteCoreStatic
                        LiteCoreREST_Static
                        FleeceStatic
                        ${NO_WHOLE_LIBRARY_FLAG}
                        LiteCoreWebSocket
                        SQLite3_UnicodeSN
                        BLIPStatic
                        mbedcrypto
                        mbedtls
                        mbedx509
                        "atomic"
                        "log"
                        zlibstatic)

