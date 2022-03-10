#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

function usage() {
    echo "usage: -e CE|EE [-o <path>]"
    echo "  -e|--edition      LiteCore edition, CE or EE."
    echo "  -o|--output-path  The output path to write the result to"
    echo
   exit 1
}

shopt -s nocasematch
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
        -e|--edition)
        EDITION="$2"
        shift
        shift
        ;;
        -o|--out-path)
        OUTPUT_PATH="$2"
        shift
        shift
        ;;
        *)
        echo >&2 "Unrecognized option $key, aborting..."
        usage
        exit 1
        ;;
    esac
done

if [ "${EDITION}" != "CE" -a "${EDITION}" != "EE" ]; then
   echo >&2 "Unrecognized edition option '${EDITION}'. Aborting..."
   usage
fi

hash git 2>/dev/null || { echo >&2 "Unable to locate git, aborting..."; exit 1; }
hash shasum 2>/dev/null || { echo >&2 "Unable to locate shasum, aborting..."; exit 1; }

pushd $SCRIPT_DIR/../../core > /dev/null
ARTIFACT_ID=`git rev-parse HEAD`
ARTIFACT_ID=${ARTIFACT_ID:0:40}
popd > /dev/null

if [[ "${EDITION}" == "EE" ]]; then
    pushd "${SCRIPT_DIR}/../../couchbase-lite-core-EE" > /dev/null
    EE_SHA=`git rev-parse HEAD`
    popd > /dev/null
    EE_SHA="${EE_SHA:0:40}"
    ARTIFACT_ID=`echo -n "${ARTIFACT_ID}${EE_SHA}" | shasum -a 1`
    ARTIFACT_ID="${ARTIFACT_ID:0:40}"
fi

if [ -z "${OUTPUT_PATH}" ]; then
   echo "${ARTIFACT_ID}"
else
   echo "${ARTIFACT_ID}" > "${OUTPUT_PATH}"
fi

