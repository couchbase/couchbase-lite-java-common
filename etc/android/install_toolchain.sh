#!/bin/bash
#
# Install the Android toolchain
# Don't execute this file, source it
# This script expects to inherit the variable BIN_DIR,
# the name of a directory into which it can install tools

# These versions must match the versions in lib/build.gradle
NINJA_VERSION="1.10.2"
CMAKE_VERSION='3.23.0'
BUILD_TOOLS_VERSION='32.0.0'
NDK_VERSION='23.1.7779620'

echo "======== Install Toolchain"

cbdep install -d "${BIN_DIR}" ninja ${NINJA_VERSION}
NINJA_DIR=`echo "${BIN_DIR}"/ninja-*`
export PATH="${NINJA_DIR}/bin:${PATH}"

cbdep install -d "${BIN_DIR}" cmake ${CMAKE_VERSION}
CMAKE_DIR=`echo "${BIN_DIR}"/cmake-*`
export PATH="${CMAKE_DIR}/bin:${PATH}"

yes | ${SDK_MGR} --licenses > /dev/null 2>&1
${SDK_MGR} --install "build-tools;${BUILD_TOOLS_VERSION}"

