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
#include "c4Base.h"
#include "native_glue.hh"
#include "com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator.h"

using namespace litecore;
using namespace litecore::jni;

extern "C" {

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator_next(
        JNIEnv *env,
        jclass ignore,
        jlong peer) {
    auto e = (C4QueryEnumerator *) peer;
    if (e == nullptr)
        return false;

    C4Error error{};
    bool ok = c4queryenum_next(e, &error);
    if (!ok && (error.code != 0)) {
        throwError(env, error);
        return false;
    }

    return ok ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator_free(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return;

    c4queryenum_release(e);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
 * Method:    getColumns
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator_getColumns(
        JNIEnv *env,
        jclass ignore,
        jlong peer) {
    auto e = (C4QueryEnumerator *) peer;
    if (e == nullptr)
        return 0L;

    return (jlong) &(e->columns);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
 * Method:    getMissingColumns
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator_getMissingColumns(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return 0L;

    return (jlong) e->missingColumns;
}
}