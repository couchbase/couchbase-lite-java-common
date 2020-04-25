#!/bin/bash -e
#
# Script for extracting LiteCore native libraries from CBL Java distribution zip files
# (macos and windows) and putting them into the canonical lite-core directory
# for inclusion in the final distribution package.
#

function usage() {
    echo "Usage: $0 <platform specific distribution zip> <workspace path>"
    exit 1
}

if [ "$#" -ne 2 ]; then
    usage
fi

DIST_ZIP="$1"
if [ -z "${DIST_ZIP}" ]; then
    usage
fi

WORKSPACE="$2"
if [ -z "$WORKSPACE" ]; then
    usage
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
hash curl 2>/dev/null || { echo >&2 "Unable to locate curl, aborting..."; exit 1; }
hash unzip 2>/dev/null || { echo >&2 "Unable to locate tar, aborting..."; exit 1; }

OUTPUT_DIR=$SCRIPT_DIR/../lite-core
mkdir -p $OUTPUT_DIR

ZIP_DIR="${WORKSPACE}/extracted"
rm -rf "${ZIP_DIR}"
unzip "${DIST_ZIP}" -d "${ZIP_DIR}"

pushd "${ZIP_DIR}" > /dev/null
jar -xf `find . -name 'couchbase-lite-java*.jar' -print` libs
popd > /dev/null

cp -R "${ZIP_DIR}/libs/"* "${OUTPUT_DIR}/"

rm -rf "${ZIP_DIR}"
