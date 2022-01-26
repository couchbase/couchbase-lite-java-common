//
// native_c4queryenumerator.cc
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
#include <c4.h>
#include <c4Base.h>
#include "com_couchbase_lite_internal_core_C4QueryEnumerator.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

extern "C" {

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4QueryEnumerator
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_next(JNIEnv *env, jclass ignore, jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return false;
    C4Error error = {};
    jboolean result = c4queryenum_next(e, &error);
    if (!result) {
// NOTE: Please keep following line of code for a while.
// At end of iteration, proactively free the enumerator:
// c4queryenum_free((C4QueryEnumerator *) handle);
        if (error.code != 0)
            throwError(env, error);
    }
    return result;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    getRowCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_getRowCount(JNIEnv *env, jclass ignore, jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return 0L;
    C4Error error = {};
    int64_t res = c4queryenum_getRowCount(e, &error);
    if (res == -1)
        throwError(env, error);
    return (jlong) res;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    seek
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_seek(JNIEnv *env, jclass ignore, jlong handle, jlong rowIndex) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return false;
    C4Error error = {};
    jboolean result = c4queryenum_seek(e, (uint64_t) rowIndex, &error);
    if (!result)
        throwError(env, error);
    return result;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    refresh
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_refresh(JNIEnv *env, jclass ignore, jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return 0L;
    C4Error error = {};
    C4QueryEnumerator *result = c4queryenum_refresh(e, &error);
    // NOTE: if result is null, it indicates no update. it is not error.
    if (error.code != 0)
        throwError(env, error);
    return (jlong) result;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_close__J(JNIEnv *env, jclass ignore, jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return;
    c4queryenum_close(e);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    retain
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_retain(JNIEnv *env, jclass ignore, jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return;
    c4queryenum_retain(e);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    release
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_release(JNIEnv *env, jclass ignore, jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return;
    c4queryenum_release(e);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    getColumns
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_getColumns(JNIEnv *env, jclass ignore, jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return 0L;
    return (jlong) &(e->columns);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    getMissingColumns
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_getMissingColumns(JNIEnv *env, jclass ignore, jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return 0L;
    return (jlong) e->missingColumns;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    getFullTextMatchCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_getFullTextMatchCount
        (JNIEnv *env, jclass ignore, jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return 0L;
    return (jlong) e->fullTextMatchCount;
}
/*
 * Class:     com_couchbase_lite_internal_core_C4QueryEnumerator
 * Method:    getFullTextMatch
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4QueryEnumerator_getFullTextMatch
        (JNIEnv *env, jclass ignore, jlong handle, jint jidx) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return 0L;
    return (jlong) &(e->fullTextMatches[(int) jidx]);
}
}