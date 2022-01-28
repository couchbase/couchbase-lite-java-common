#!/bin/bash
#
# Download and extract source for Linux and macOS
#
#

# Exit with error if any of the arguments is empty
function usage() {
    echo "Usage: $0 <latest builds> <source> <version> <build number>"
    exit 1
}

if [ "$#" -ne 4 ]; then
    usage
fi

LATEST_BUILDS="$1"
if [ -z "${LATEST_BUILDS}" ]; then
    usage
fi

SOURCE="$2"
if [ -z "${SOURCE}" ]; then
    usage
fi

VERSION="$3"
if [ -z "${VERSION}" ]; then
    usage
fi

BUILD_NUMBER="$4"
if [ -z "${BUILD_NUMBER}" ]; then
    usage
fi

echo "======== Linux: Download source: ${SOURCE}"
curl -LO "${LATEST_BUILDS}/couchbase-lite-java/${VERSION}/${BUILD_NUMBER}/${SOURCE}"

echo "======== Linux: Extract source"
tar xzf ${SOURCE}
rm *-source.tar.gz
