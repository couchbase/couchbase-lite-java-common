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
#include "com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver.h"

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
        if (localClass == nullptr)
            return false;

        cls_C4CollObs = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_C4CollObs == nullptr)
            return false;

        m_C4CollObs_callback = env->GetStaticMethodID(cls_C4CollObs, "callback", "(J)V");
        if (m_C4CollObs_callback == nullptr)
            return false;
    }

    // C4DocumentObserver.callback
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DocumentObserver");
        if (localClass == nullptr)
            return false;

        cls_C4DocObs = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_C4DocObs == nullptr)
            return false;

        m_C4DocObs_callback = env->GetStaticMethodID(cls_C4DocObs, "callback", "(JJLjava/lang/String;)V");
        if (m_C4DocObs_callback == nullptr)
            return false;
    }

    // C4DocumentChange.create
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DocumentChange");
        if (localClass == nullptr)
            return false;

        cls_C4DocChange = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_C4DocChange == nullptr)
            return false;

        m_C4DocChange_create = env->GetStaticMethodID(
                cls_C4DocChange,
                "createC4DocumentChange",
                "(Ljava/lang/String;Ljava/lang/String;JZ)Lcom/couchbase/lite/internal/core/C4DocumentChange;");
        if (m_C4DocChange_create == nullptr)
            return false;
    }

    // C4QueryObserver.onQueryChanged
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4QueryObserver");
        if (localClass == nullptr)
            return false;

        cls_C4QueryObs = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_C4QueryObs == nullptr)
            return false;

        m_C4QueryObs_callback = env->GetStaticMethodID(cls_C4QueryObs, "onQueryChanged", "(JJIILjava/lang/String;)V");
        if (m_C4QueryObs_callback == nullptr)
            return false;
    }

    jniLog("observers initialized");
    return true;
}

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
// ----------------------------------------------------------------------------

/**
 * Callback method from LiteCore C4CollectionObserver
 * @param ignore ignored
 * @param context the token bound to the java C4CollectionObserver instance.
 */
static void c4CollectionObsCallback(C4CollectionObserver *ignore, void *context) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "collectionObserver");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    env->CallStaticVoidMethod(cls_C4CollObs, m_C4CollObs_callback, (jlong) context);

    if (envState == JNI_EDETACHED)
        detachJVM("collectionObserver");
}

/**
 * Callback method from LiteCore C4DocumentObserver
 * @param ign1 ref for the C4Document observer: ignored
 * @param ign2 ref for the C4Collection: ignored
 * @param docID the id of the changed document
 * @param seq the doc sequence number: ignored
 * @param context the token bound to the observer instance.
 */
static void c4DocObsCallback(
        C4DocumentObserver *ign1,
        C4Collection *ign2,
        C4Slice docID,
        C4SequenceNumber seq,
        void *context) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "docObserver");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;


    jstring _docID = toJString(env, docID);
    env->CallStaticVoidMethod(cls_C4DocObs, m_C4DocObs_callback, (jlong) context, (jlong) seq, _docID);

    if (envState == JNI_EDETACHED) {
        detachJVM("docObserver");
    } else {
        if (_docID != nullptr) env->DeleteLocalRef(_docID);
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
        jstring _docId = toJString(env, changes[i].docID);
        jstring _revId = toJString(env, changes[i].revID);

        jobject obj = env->CallStaticObjectMethod(
                cls_C4DocChange,
                m_C4DocChange_create,
                _docId,
                _revId,
                (jlong) changes[i].sequence,
                external ? JNI_TRUE : JNI_FALSE);

        if (_docId != nullptr) env->DeleteLocalRef(_docId);
        if (_revId != nullptr) env->DeleteLocalRef(_revId);

        if (obj != nullptr) {
            env->SetObjectArrayElement(array, (jsize) i, obj);
            env->DeleteLocalRef(obj);
        }
    }

    return array;
}

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
// ----------------------------------------------------------------------------

static void
doC4QueryObserverCallback(JNIEnv *env, C4QueryObserver *observer, void *ctx) {
    C4Error error{};
    C4QueryEnumerator *results = c4queryobs_getEnumerator(observer, false, &error);

    jstring errMsg = nullptr;
    if (error.code != 0) {
        C4SliceResult msgSlice = c4error_getMessage(error);
        errMsg = toJString(env, msgSlice);
        c4slice_free(msgSlice);
    }

    env->CallStaticVoidMethod(
            cls_C4QueryObs,
            m_C4QueryObs_callback,
            (jlong) ctx,
            (jlong) results,
            (jint) error.domain,
            (jint) error.code,
            errMsg);

    if (errMsg != nullptr) env->DeleteLocalRef(errMsg);
}

/**
 * Callback method from LiteCore C4QueryObserverCallback
 * @param observer
 * @param ctx
 */
static void
c4QueryObserverCallback(C4QueryObserver *observer, C4Query *ignore, void *ctx) {
    if (!observer) return;

    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "queryObserver");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    doC4QueryObserverCallback(env, observer, ctx);

    if (envState == JNI_EDETACHED)
        detachJVM("queryObserver");
}

#ifdef __cplusplus
extern "C" {
#endif

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
 * Method:    create
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver_create(
        JNIEnv *env,
        jclass ignore,
        jlong token,
        jlong coll) {
    C4Error error{};
    auto obs = c4dbobs_createOnCollection(
            (C4Collection *) coll,
            c4CollectionObsCallback,
            (void *) token,
            &error);
    if ((obs == nullptr) && (error.code != 0)) {
        throwError(env, error);
        return 0;
    }

    return (jlong) obs;
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

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver
 * Method:    create
 * Signature: (JJLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver_create(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jlong token,
        jstring jdocID) {
    jstringSlice docID(env, jdocID);

    C4Error error{};
    auto obs = c4docobs_createWithCollection(
            (C4Collection *) coll,
            docID,
            c4DocObsCallback,
            (void *) token,
            &error);
    if ((obs == nullptr) && (error.code != 0)) {
        throwError(env, error);
        return 0;
    }

    return (jlong) obs;
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

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
 * Method:    create
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_create(
        JNIEnv *env,
        jclass clazz,
        jlong jquery,
        jlong token) {
    return (jlong) c4queryobs_create((C4Query *) jquery, c4QueryObserverCallback, (void *) token);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
 * Method:    enable
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_enable(
        JNIEnv *env,
        jclass clazz,
        jlong handle) {
    c4queryobs_setEnabled((C4QueryObserver *) handle, true);
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
    if (handle != 0) {
        auto obs = (C4QueryObserver *) handle;
        c4queryobs_setEnabled(obs, false);
        c4queryobs_free(obs);
    }
}
#ifdef __cplusplus
}
#endif
