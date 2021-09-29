//
// native_c4observer.cc
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
#include "com_couchbase_lite_internal_core_C4DatabaseObserver.h"
#include "com_couchbase_lite_internal_core_C4DocumentObserver.h"
#include "com_couchbase_lite_internal_core_NativeC4QueryObserver.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

// -------------------------------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4DatabaseObserver and com_couchbase_lite_internal_core_C4DocumentObserver
// -------------------------------------------------------------------------------------------------

// C4DatabaseObserver
static jclass cls_C4DBObs;           // global reference
static jmethodID m_C4DBObs_callback; // callback method

// C4DatabaseChange
static jclass cls_C4DBChange; // global reference
static jmethodID m_C4DBChange_create;

// C4DocumentObserver
static jclass cls_C4DocObs;           // global reference
static jmethodID m_C4DocObs_callback; // callback method

bool litecore::jni::initC4Observer(JNIEnv *env) {
    // Find `C4DatabaseObserver` class and `callback(long)` static method for callback
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DatabaseObserver");
        if (!localClass)
            return false;

        cls_C4DBObs = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4DBObs)
            return false;

        m_C4DBObs_callback = env->GetStaticMethodID(cls_C4DBObs, "callback", "(J)V");
        if (!m_C4DBObs_callback)
            return false;
    }

    // Find `C4DocumentObserver.callback()` method id for callback
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DocumentObserver");
        if (!localClass)
            return false;

        cls_C4DocObs = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4DocObs)
            return false;

        m_C4DocObs_callback = env->GetStaticMethodID(cls_C4DocObs, "callback", "(JLjava/lang/String;J)V");
        if (!m_C4DocObs_callback)
            return false;
    }

    // C4DatabaseChange constructor
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DatabaseChange");
        if (!localClass)
            return false;

        cls_C4DBChange = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4DBChange)
            return false;

        m_C4DBChange_create = env->GetStaticMethodID(
                cls_C4DBChange,
                "createC4DatabaseChange",
                "(Ljava/lang/String;Ljava/lang/String;JZ)Lcom/couchbase/lite/internal/core/C4DatabaseChange;");
        if (!m_C4DBChange_create)
            return false;
    }
    return true;
}

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4DatabaseObserver
// ----------------------------------------------------------------------------

/**
 * Callback method from LiteCore C4DatabaseObserver
 * @param obs
 * @param ctx
 */
static void c4DBObsCallback(C4DatabaseObserver *obs, void *ignore) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4DBObs, m_C4DBObs_callback, (jlong) obs);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            env->CallStaticVoidMethod(cls_C4DBObs, m_C4DBObs_callback, (jlong) obs);
            gJVM->DetachCurrentThread();
        }
    }
}

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4DocumentObserver
// ----------------------------------------------------------------------------

/**
 * Callback method from LiteCore C4DatabaseObserver
 * @param obs
 * @param ctx
 */
static void
c4DocObsCallback(C4DocumentObserver *obs, C4Slice docID, C4SequenceNumber seq, void *ignore) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(
                cls_C4DocObs,
                m_C4DocObs_callback,
                (jlong) obs,
                toJString(env, docID),
                (jlong) seq);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            env->CallStaticVoidMethod(
                    cls_C4DocObs,
                    m_C4DocObs_callback,
                    (jlong) obs,
                    toJString(env, docID),
                    (jlong) seq);
            gJVM->DetachCurrentThread();
        }
    }
}

extern "C" {

/*
 * Class:     com_couchbase_lite_internal_core_C4DatabaseObserver
 * Method:    create
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4DatabaseObserver_create(JNIEnv *, jclass, jlong db) {
    return (jlong) c4dbobs_create((C4Database *) db, c4DBObsCallback, nullptr);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4DatabaseObserver
 * Method:    getChanges
 * Signature: (JI)[Lcom/couchbase/lite/internal/core/C4DatabaseChange;
 */
JNIEXPORT jobjectArray JNICALL
Java_com_couchbase_lite_internal_core_C4DatabaseObserver_getChanges(
        JNIEnv *env,
        jclass ignore,
        jlong observer,
        jint maxChanges) {
    auto *c4changes = new C4DatabaseChange[maxChanges];
    bool external = false;
    uint32_t nChanges = c4dbobs_getChanges(
            (C4DatabaseObserver *) observer,
            c4changes,
            (uint32_t) maxChanges,
            &external);

    jobjectArray array = env->NewObjectArray(nChanges, cls_C4DBChange, nullptr);
    for (size_t i = 0; i < nChanges; i++) {
        jobject obj = env->CallStaticObjectMethod(
                cls_C4DBChange,
                m_C4DBChange_create,
                toJString(env, c4changes[i].docID),
                toJString(env, c4changes[i].revID),
                (jlong) c4changes[i].sequence,
                (jboolean) external);
        if (obj != NULL)
            env->SetObjectArrayElement(array, i, obj);
    }
    c4dbobs_releaseChanges(c4changes, nChanges);
    return array;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4DatabaseObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4DatabaseObserver_free(JNIEnv *env, jclass ignore, jlong observer) {
    if (observer != 0)
        c4dbobs_free((C4DatabaseObserver *) observer);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4DocumentObserver
 * Method:    create
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4DocumentObserver_create(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jstring jdocID) {
    jstringSlice docID(env, jdocID);
    return (jlong) c4docobs_create((C4Database *) jdb, docID, c4DocObsCallback, nullptr);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4DocumentObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4DocumentObserver_free(JNIEnv *env, jclass ignore, jlong obs) {
    if (obs != 0)
        c4docobs_free((C4DocumentObserver *) obs);
}
}
/*
 * Query observer
 */
extern "C"
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_NativeC4QueryObserver_create(
        JNIEnv *env, jclass clazz,
        jlong c4_query) {
    // TODO: implement create()
}
extern "C"
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_NativeC4QueryObserver_setEnabled(
        JNIEnv *env, jclass clazz,
        jlong handle,
        jboolean enabled) {
    // TODO: implement setEnabled()
}
extern "C"
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_NativeC4QueryObserver_free(
        JNIEnv *env, jclass clazz,
        jlong observer) {
    // TODO: implement free()
}
extern "C"
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_NativeC4QueryObserver_getEnumerator(
        JNIEnv *env, jclass clazz,
        jlong observer,
        jboolean forget) {
    // TODO: implement getEnumerator()
}