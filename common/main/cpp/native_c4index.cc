//
// native_c4index.cc
//
// Copyright (c) 2024 Couchbase, Inc All rights reserved.
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

using namespace litecore;
using namespace litecore::jni;

extern "C" {

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4Index
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Index
 * Method:    beginUpdate
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Index_beginUpdate(
        JNIEnv *env,
        jclass ignore,
        jlong handle,
        jlong limit) {
#ifndef COUCHBASE_ENTERPRISE
    return 0;
#else
    C4Error error{};
    C4IndexUpdater *updater = c4index_beginUpdate((C4Index *) handle, (size_t) limit, &error);
    if (updater == nullptr) {
        if (error.code != 0)
            throwError(env, error);

        return 0;
    }

    return (jlong) updater;
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Index
 * Method:    releaseIndex
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Index_releaseIndex(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
#ifdef COUCHBASE_ENTERPRISE
    auto idx = (C4Index *) handle;
    if (idx != nullptr)
        c4index_release(idx);
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    count
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_count(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
#ifndef COUCHBASE_ENTERPRISE
    return 0;
#else
    return c4indexupdater_count((C4IndexUpdater *) handle);
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    valueAt
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_valueAt(
        JNIEnv *env,
        jclass ignore,
        jlong handle,
        jlong index) {
#ifndef COUCHBASE_ENTERPRISE
    return 0;
#else
    return (jlong) c4indexupdater_valueAt((C4IndexUpdater *) handle, (size_t) index);
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    setVectorAt
 * Signature: (JJ[F)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_setVectorAt(
        JNIEnv *env,
        jclass ignore,
        jlong handle,
        jlong index,
        jfloatArray jvalues) {
#ifdef COUCHBASE_ENTERPRISE
    jsize len = 0;
    jfloat *val = nullptr;

    if (jvalues != nullptr) {
        len = env->GetArrayLength(jvalues);
        val = env->GetFloatArrayElements(jvalues, nullptr);
    }

    C4Error error{};
    bool ok = c4indexupdater_setVectorAt((C4IndexUpdater *) handle, (size_t) index, val, len, &error);

    if (val != nullptr)
        env->ReleaseFloatArrayElements(jvalues, val, 0);

    if (!ok && error.code != 0)
        throwError(env, error);
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    skipVectorAt
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_skipVectorAt(
        JNIEnv *env,
        jclass ignore,
        jlong handle,
        jlong index) {
#ifndef COUCHBASE_ENTERPRISE
    return JNI_FALSE;
#else
    return (c4indexupdater_skipVectorAt((C4IndexUpdater *) handle, (size_t) index) ? JNI_TRUE : JNI_FALSE);
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    finish
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_finish(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
#ifdef COUCHBASE_ENTERPRISE
    C4Error error{};
    bool ok = c4indexupdater_finish((C4IndexUpdater *) handle, &error);
    if (!ok && error.code != 0) {
        throwError(env, error);
        return;
    }
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_close(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
#ifdef COUCHBASE_ENTERPRISE
    c4indexupdater_release((C4IndexUpdater *) handle);
#endif
}
}