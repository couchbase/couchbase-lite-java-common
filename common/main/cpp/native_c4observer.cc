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
#include "native_glue.hh"
#include "com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4QueryObserver.h"
#include "com_couchbase_lite_internal_core_C4DocumentObserver.h"

using namespace litecore;
using namespace litecore::jni;

// -------------------------------------------------------------------------------------------------
// Observer callbacks
// -------------------------------------------------------------------------------------------------

// C4CollectionObserver
static jclass cls_C4CollObs;              // global class reference
static jmethodID m_C4CollObs_callback;    // collection observer callback

// C4DocumentObserver
static jclass cls_C4DocObs;               // global class reference
static jmethodID m_C4DocObs_callback;     // document observer callback

// C4DocumentChange
static jclass cls_C4DocChange;            // global class reference
static jmethodID m_C4DocChange_create;    // C4DocumentChange static constructor

// C4QueryObserver
static jclass cls_C4QueryObs;             // global class reference
static jmethodID m_C4QueryObs_callback;   // query observer callback

bool litecore::jni::initC4Observer(JNIEnv *env) {
    // C4CollectionObserver.callback
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4CollectionObserver");
        if (!localClass)
            return false;

        cls_C4CollObs = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4CollObs)
            return false;

        m_C4CollObs_callback = env->GetStaticMethodID(cls_C4CollObs, "callback", "(J)V");
        if (!m_C4CollObs_callback)
            return false;
    }

    // C4DocumentObserver.callback
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DocumentObserver");
        if (!localClass)
            return false;

        cls_C4DocObs = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4DocObs)
            return false;

        m_C4DocObs_callback = env->GetStaticMethodID(cls_C4DocObs, "callback", "(JLjava/lang/String;)V");
        if (!m_C4DocObs_callback)
            return false;
    }

    // C4DocumentChange.create
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DocumentChange");
        if (!localClass)
            return false;

        cls_C4DocChange = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4DocChange)
            return false;

        m_C4DocChange_create = env->GetStaticMethodID(
                cls_C4DocChange,
                "createC4DocumentChange",
                "(Ljava/lang/String;Ljava/lang/String;JZ)Lcom/couchbase/lite/internal/core/C4DocumentChange;");
        if (!m_C4DocChange_create)
            return false;
    }

    // C4QueryObserver.onQueryChanged
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4QueryObserver");
        if (!localClass)
            return false;

        cls_C4QueryObs = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4QueryObs)
            return false;

        m_C4QueryObs_callback = env->GetStaticMethodID(cls_C4QueryObs, "onQueryChanged", "(J)V");
        if (!m_C4QueryObs_callback)
            return false;
    }

    return true;
}

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
// ----------------------------------------------------------------------------

/**
 * Callback method from LiteCore C4CollectionObserver
 * @param observer reference to the C4CollectionObserver
 * @param ignore "context". Ignored.
 */
static void
c4CollectionObsCallback(C4CollectionObserver *observer, void *ignore) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4CollObs, m_C4CollObs_callback, (jlong) observer);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            env->CallStaticVoidMethod(cls_C4CollObs, m_C4CollObs_callback, (jlong) observer);
            gJVM->DetachCurrentThread();
        }
    }
}

/**
 * Callback method from LiteCore C4DocumentObserver
 * @param docID
 * @param seq
 */
static void
c4DocObsCallback(C4DocumentObserver *obs, C4Collection *ign1, C4Slice docID, C4SequenceNumber ign2, void *ign3) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4DocObs, m_C4DocObs_callback, (jlong) obs, toJString(env, docID));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            env->CallStaticVoidMethod(cls_C4DocObs, m_C4DocObs_callback, (jlong) obs, toJString(env, docID));
            gJVM->DetachCurrentThread();
        }
    }
}

/**
 * Convert a C array of C4CollectionChanges (C4DatabaseChanges) to a Java array of C4DocumentChanges.
 *
 * @param env Java env
 * @param changes the C array of C4CollectionChanges
 * @param nChanges the C array size
 * @param external Change came from elsewhere (replication)
 * @return
 */
static jobjectArray
c4DocChangesToJavaArray(JNIEnv *env, C4CollectionChange changes[], uint32_t nChanges, bool external) {
    jobjectArray array = env->NewObjectArray((jsize) nChanges, cls_C4DocChange, nullptr);
    for (size_t i = 0; i < nChanges; i++) {
        jobject obj = env->CallStaticObjectMethod(
                cls_C4DocChange,
                m_C4DocChange_create,
                toJString(env, changes[i].docID),
                toJString(env, changes[i].revID),
                (jlong) changes[i].sequence,
                (jboolean) external);
        if (obj != nullptr)
            env->SetObjectArrayElement(array, (jsize) i, obj);
    }

    return array;
}

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_C4QueryObserver
// ----------------------------------------------------------------------------

/**
 * Callback method from LiteCore C4QueryObserverCallback
 * @param
 * @param ctx
 */
static void
c4QueryObserverCallback(C4QueryObserver *ignore1, C4Query *ignore2, void *ctx) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4QueryObs, m_C4QueryObs_callback, (jlong) ctx);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            env->CallStaticVoidMethod(cls_C4QueryObs, m_C4QueryObs_callback, (jlong) ctx);
            gJVM->DetachCurrentThread();
        }
    }
}

extern "C" {

/*
 * Collection observer
 */

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
 * Method:    create
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver_create(
        JNIEnv *env,
        jclass ignore,
        jlong coll) {

    C4Error error{};
    auto res = (jlong) c4dbobs_createOnCollection(
            (C4Collection *) coll,
            c4CollectionObsCallback,
            (void *)
                    0L,
            &error);
    if (!res && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    return res;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4DatabaseObserver
 * Method:    getChanges
 * Signature: (JI)[Lcom/couchbase/lite/internal/core/C4DocumentChange;
 */
JNIEXPORT jobjectArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver_getChanges(
        JNIEnv *env,
        jclass ignore,
        jlong observer,
        jint maxChanges) {
    auto *c4changes = new C4CollectionChange[maxChanges];

    auto observation = c4dbobs_getChanges((C4CollectionObserver *) observer, c4changes, (uint32_t) maxChanges);

    auto changes = c4DocChangesToJavaArray(env, c4changes, observation.numChanges, observation.external);

    c4dbobs_releaseChanges(c4changes, observation.numChanges);

    return changes;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver_free(
        JNIEnv *,
        jclass,
        jlong observer) {
    if (observer != 0)
        c4dbobs_free((C4CollectionObserver *) observer);
}

/*
 * Collection document observer
 */

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver
 * Method:    create
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver_create(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jdocID) {
    jstringSlice docID(env, jdocID);

    C4Error error{};
    auto res = (jlong) c4docobs_createWithCollection(
            (C4Collection *) coll,
            docID,
            c4DocObsCallback,
            (void *) 0L,
            &error);
    if (!res && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    return res;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver_free(
        JNIEnv *env,
        jclass ignore,
        jlong observer) {
    if (observer != 0)
        c4docobs_free((C4DocumentObserver *) observer);
}

/*
 * Query observer
 */

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
 * Method:    create
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_create(
        JNIEnv *env,
        jclass clazz,
        jlong token,
        jlong jquery) {
    return (jlong) c4queryobs_create((C4Query *) jquery, c4QueryObserverCallback, (void *) token);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
 * Method:    setEnabled
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_setEnabled(
        JNIEnv *env,
        jclass clazz,
        jlong handle,
        jboolean enabled) {
    c4queryobs_setEnabled((C4QueryObserver *) handle, (bool) enabled);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
 * Method:    getEnumerator
 * Signature: (JZ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_getEnumerator(
        JNIEnv *env, jclass clazz,
        jlong handle,
        jboolean forget) {
    C4Error error{};
    C4QueryEnumerator *results = c4queryobs_getEnumerator((C4QueryObserver *) handle, (bool) forget, &error);
    if (!results) {
        throwError(env, error);
        return 0;
    }
    return (jlong) results;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_free(
        JNIEnv *env,
        jclass clazz,
        jlong handle) {
    c4queryobs_setEnabled((C4QueryObserver *) handle, false);
    c4queryobs_free((C4QueryObserver *) handle);
}
}
