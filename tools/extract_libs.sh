#!/bin/bash
#
# Script for extracting LiteCore native libraries from CBL Java distribution zip files
# (macos and windows) and putting them into the canonical lite-core directory
# for inclusion in the final distribution package.

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
CORE_DIR=$SCRIPT_DIR/../lite-core

function usage() {
    echo "usage: $0 <distribution url> <distribution name> <workspace path>"
    exit 1
}

if [ "$#" -ne 3 ]; then
    usage
fi

DIST_URL="$1"
if [ -z "${DIST_URL}" ]; then
    usage
fi

DIST_FILE="$2"
if [ -z "${DIST_FILE}" ]; then
    usage
fi

WORK_DIR="$3"
if [ -z "${WORK_DIR}" ]; then
    usage
fi

hash curl 2>/dev/null || { echo >&2 "Unable to locate curl, aborting..."; exit 1; }
hash unzip 2>/dev/null || { echo >&2 "Unable to locate zip, aborting..."; exit 1; }

rm -rf "${WORK_DIR}" > /dev/null 2>&1
mkdir -p "${WORK_DIR}"
pushd "${WORK_DIR}" > /dev/null

echo "=== Downloading: ${DIST_URL}/${DIST_FILE}"
curl -f -L "${DIST_URL}/${DIST_FILE}" -o "${DIST_FILE}" || exit 1
unzip "${DIST_FILE}"
rm -rf "${DIST_FILE}"

jar -xf `find . -name 'couchbase-lite-java*.jar' -print` libs

cp -R libs/* "${CORE_DIR}"

popd > /dev/null
rm -rf "${WORK_DIR}"

echo "=== Extraction complete"
find "${CORE_DIR}"

