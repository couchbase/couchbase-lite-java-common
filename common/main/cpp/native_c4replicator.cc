//
// native_c4replicator.cc
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
#include <c4Replicator.h>
#include <c4Base.h>
#include <c4Socket.h>
#include "socket_factory.h"
#include "com_couchbase_lite_internal_core_C4Replicator.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4Replicator
// ----------------------------------------------------------------------------

// C4Replicator
static jclass cls_C4Replicator;                         // global reference
static jmethodID m_C4Replicator_statusChangedCallback;  // statusChangedCallback method
static jmethodID m_C4Replicator_documentEndedCallback;  // documentEndedCallback method
static jmethodID m_C4Replicator_validationFunction;     // validationFunction method

// C4ReplicatorStatus
static jclass cls_C4ReplStatus; // global reference
static jmethodID m_C4ReplStatus_init;

//C4DocumentEnded
static jclass cls_C4DocEnded;
static jmethodID m_C4DocEnded_init;

bool litecore::jni::initC4Replicator(JNIEnv *env) {
    // Find `C4Replicator` class and `statusChangedCallback(long, C4ReplicatorStatus )` static method for callback
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4Replicator");
        if (!localClass)
            return false;

        cls_C4Replicator = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4Replicator)
            return false;

        m_C4Replicator_statusChangedCallback = env->GetStaticMethodID(
                cls_C4Replicator,
                "statusChangedCallback",
                 "(JLcom/couchbase/lite/internal/core/C4ReplicatorStatus;)V");
        if (!m_C4Replicator_statusChangedCallback)
            return false;

        m_C4Replicator_documentEndedCallback = env->GetStaticMethodID(
                cls_C4Replicator,
                "documentEndedCallback",
                "(JZ[Lcom/couchbase/lite/internal/core/C4DocumentEnded;)V");
        if (!m_C4Replicator_documentEndedCallback)
            return false;

        m_C4Replicator_validationFunction = env->GetStaticMethodID(
                cls_C4Replicator,
                "validationFunction",
                "(Ljava/lang/String;Ljava/lang/String;IJZLjava/lang/Object;)Z");
        if (!m_C4Replicator_validationFunction)
            return false;
    }

    // C4ReplicatorStatus, constructor, and fields
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4ReplicatorStatus");
        if (!localClass)
            return false;

        cls_C4ReplStatus = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4ReplStatus)
            return false;

        m_C4ReplStatus_init = env->GetMethodID(cls_C4ReplStatus, "<init>", "(IJJJIII)V");
        if (!m_C4ReplStatus_init)
            return false;
    }

    // C4DocumentEnded, constructor, and fields
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DocumentEnded");
        if (!localClass)
            return false;

        cls_C4DocEnded = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4DocEnded)
            return false;

        m_C4DocEnded_init = env->GetMethodID(cls_C4DocEnded, "<init>", "(Ljava/lang/String;Ljava/lang/String;IJIIIZ)V");
        if (!m_C4DocEnded_init)
            return false;
    }
    return true;
}

static jobject toJavaReplStatus(JNIEnv *env, C4ReplicatorStatus status) {
    return env->NewObject(
            cls_C4ReplStatus,
            m_C4ReplStatus_init,
            (jint) status.level,
            (jlong) status.progress.unitsCompleted,
            (jlong) status.progress.unitsTotal,
            (jlong) status.progress.documentCount,
            (int) status.error.domain,
            (int) status.error.code,
            (int) status.error.internal_info);
}

static jobject toJavaDocumentEnded(JNIEnv *env, const C4DocumentEnded *document) {
    jstring docID = toJString(env, document->docID);
    jstring revID = toJString(env, document->docID);

    jobject obj = env->NewObject(
            cls_C4DocEnded,
            m_C4DocEnded_init,
            docID,
            revID,
            (jint) document->flags,
            (jlong) document->sequence,
            (jint) document->error.domain,
            (jint) document->error.code,
            (jint) document->error.internal_info,
            (jboolean) document->errorIsTransient);

    if (docID)
        env->DeleteLocalRef(docID);
    if (docID)
        env->DeleteLocalRef(revID);

    return obj;
}

static jobjectArray toJavaDocumentEndedArray(JNIEnv *env, int arraySize, const C4DocumentEnded *array[]) {
    jobjectArray ds = env->NewObjectArray(arraySize, cls_C4DocEnded, nullptr);
    for (int i = 0; i < arraySize; i++) {
        jobject d = toJavaDocumentEnded(env, array[i]);
        env->SetObjectArrayElement(ds, i, d);
        env->DeleteLocalRef(d);
    }
    return ds;
}

static std::vector<jobject> contexts;

// This method accesses global state: not thread safe
static jobject storeContext(JNIEnv *env, jobject jcontext) {
    if (jcontext == nullptr)
        return nullptr;

    jobject gContext = env->NewGlobalRef(jcontext);
    contexts.push_back(gContext);
    return gContext;
}

// This method accesses global state: not thread safe
static void releaseContext(JNIEnv *env, jobject jcontext) {
    if (jcontext == nullptr)
        return;

    jobject storedContext = nullptr;
    int i = 0;
    for (; i < contexts.size(); i++) {
        jobject c = contexts[i];
        if (env->IsSameObject(c, jcontext)) {
            storedContext = c;
            break;
        }
    }

    if (storedContext != nullptr) {
        env->DeleteGlobalRef(storedContext);
        contexts.erase(contexts.begin() + i);
    }
}

/**
 * Callback a client can register, to get progress information.
 * This will be called on arbitrary background threads and should not block.
 *
 * @param repl
 * @param status
 * @param ctx
 */
static void statusChangedCallback(C4Replicator *repl, C4ReplicatorStatus status, void *ignore) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4Replicator,
                                  m_C4Replicator_statusChangedCallback,
                                  (jlong) repl,
                                  toJavaReplStatus(env, status));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            env->CallStaticVoidMethod(cls_C4Replicator,
                                      m_C4Replicator_statusChangedCallback,
                                      (jlong) repl,
                                      toJavaReplStatus(env, status));
            if (gJVM->DetachCurrentThread() != 0)
                C4Warn("doRequestClose(): Failed to detach the current thread from a Java VM");
        } else {
            C4Warn("doRequestClose(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        C4Warn("doClose(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
}

/**
 * Callback a client can register, to hear about errors replicating individual documents.
 *
 * @param repl
 * @param pushing
 * @param numDocs
 * @param documentEnded
 * @param ctx
 */
static void documentEndedCallback(C4Replicator *repl,
                                  bool pushing,
                                  size_t numDocs,
                                  const C4DocumentEnded *documentEnded[],
                                  void *ctx) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        jobjectArray docs = toJavaDocumentEndedArray(env, numDocs, documentEnded);
        env->CallStaticVoidMethod(cls_C4Replicator,
                                  m_C4Replicator_documentEndedCallback,
                                  (jlong) repl,
                                  pushing,
                                  docs);
        env->DeleteLocalRef(docs);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            jobjectArray docs = toJavaDocumentEndedArray(env, numDocs, documentEnded);
            env->CallStaticVoidMethod(cls_C4Replicator,
                                      m_C4Replicator_documentEndedCallback,
                                      (jlong) repl,
                                      pushing,
                                      docs);
            env->DeleteLocalRef(docs);
            if (gJVM->DetachCurrentThread() != 0) {
                C4Warn("Failed to detach the current thread from a Java VM");
            }
        } else {
            C4Warn("Failed to attach the current thread to a Java VM");
        }
    } else {
        C4Warn("Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
}

static jboolean replicationFilter(C4String docID,
                                  C4String revID,
                                  C4RevisionFlags flags,
                                  FLDict dict,
                                  bool isPush,
                                  void *ctx) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    bool res = false;
    if (getEnvStat == JNI_OK) {
        res = env->CallStaticBooleanMethod(cls_C4Replicator,
                                           m_C4Replicator_validationFunction,
                                           toJString(env, docID),
                                           toJString(env, revID),
                                           flags,
                                           (jlong) dict,
                                           isPush,
                                           (jobject) ctx);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            res = env->CallStaticBooleanMethod(cls_C4Replicator,
                                               m_C4Replicator_validationFunction,
                                               toJString(env, docID),
                                               toJString(env, revID),
                                               flags,
                                               (jlong) dict,
                                               isPush,
                                               (jobject) ctx);
            if (gJVM->DetachCurrentThread() != 0)
                C4Warn("doRequestClose(): Failed to detach the current thread from a Java VM");
        } else {
            C4Warn("doRequestClose(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        C4Warn("doClose(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
    return (jboolean) res;
}

/*
 Callback that can choose to reject an incoming pulled revision by returning false.
        (Note: In the case of an incoming revision, no flags other than 'deletion' and
        'hasAttachments' will be set.)
 *
 * @param docID
 * @param revID
 * @param flags
 * @param dict
 * @param ctx
 */
static bool validationFunction(C4String docID, C4String revID, C4RevisionFlags flags, FLDict dict, void *ctx) {
    return (bool) replicationFilter(docID, revID, flags, dict, false, ctx);
}

/*
 Callback that can stop a local revision from being pushed by returning false.
        (Note: In the case of an incoming revision, no flags other than 'deletion' and
        'hasAttachments' will be set.)
 *
 * @param docID
 * @param revID
 * @param flags
 * @param dict
 * @param ctx
 */
static bool pushFilterFunction(C4String docID, C4String revID, C4RevisionFlags flags, FLDict dict, void *ctx) {
    return (bool) replicationFilter(docID, revID, flags, dict, true, ctx);
}

extern "C" {
/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    create
 * Signature: (JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;JIILjava/lang/Object;ILjava/lang/Object;Lcom/couchbase/lite/internal/core/C4ReplicationFilter;Lcom/couchbase/lite/internal/core/C4ReplicationFilter;[B)J
 *
 * This method accesses global state: not thread safe
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_create(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jstring jscheme,
        jstring jhost,
        jint jport,
        jstring jpath,
        jstring jremoteDBName,
        jint jpush,
        jint jpull,
        jobject jSocketFactoryContext,
        jint jframing,
        jobject jReplicatorContext,
        jobject pushFilter,
        jobject pullFilter,
        jbyteArray joptions) {
    jstringSlice scheme(env, jscheme);
    jstringSlice host(env, jhost);
    jstringSlice path(env, jpath);
    jstringSlice remoteDBName(env, jremoteDBName);
    jbyteArraySlice options(env, joptions, false);

    C4Address c4Address = {};
    c4Address.scheme = scheme;
    c4Address.hostname = host;
    c4Address.port = (uint16_t) jport;
    c4Address.path = path;

    C4SocketFactory socketFactory = {};
    socketFactory = socket_factory();
    socketFactory.context = storeContext(env, jSocketFactoryContext);
    socketFactory.framing = (C4SocketFraming) jframing;

    C4ReplicatorParameters params = {};
    params.push = (C4ReplicatorMode) jpush;
    params.pull = (C4ReplicatorMode) jpull;
    params.optionsDictFleece = options;
    params.onStatusChanged = &statusChangedCallback;
    params.onDocumentsEnded = &documentEndedCallback;
    if (pushFilter != nullptr) params.pushFilter = &pushFilterFunction;
    if (pullFilter != nullptr) params.validationFunc = &validationFunction;
    params.callbackContext = storeContext(env, jReplicatorContext);
    params.socketFactory = &socketFactory;

    C4Error error;
    C4Replicator *repl = c4repl_new((C4Database *) jdb,
                                    c4Address,
                                    remoteDBName,
                                    params,
                                    &error);
    if (!repl) {
        throwError(env, error);
        return 0;
    }

    return (jlong) repl;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    create
 * Signature: (JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;JIILjava/lang/Object;ILjava/lang/Object;Lcom/couchbase/lite/internal/core/C4ReplicationFilter;Lcom/couchbase/lite/internal/core/C4ReplicationFilter;[B)J
 *
 * This method accesses global state: not thread safe
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_createLocal(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jlong jotherLocalDB,
        jint jpush,
        jint jpull,
        jint jframing,
        jobject jReplicatorContext,
        jobject pushFilter,
        jobject pullFilter,
        jbyteArray joptions) {
#ifndef COUCHBASE_ENTERPRISE
    C4Error error = {LiteCoreDomain, kC4ErrorUnimplemented};
    throwError(env, error);
    return 0;
#else
    jbyteArraySlice options(env, joptions, false);

    C4ReplicatorParameters params = {};
    params.push = (C4ReplicatorMode) jpush;
    params.pull = (C4ReplicatorMode) jpull;
    params.optionsDictFleece = options;
    params.onStatusChanged = &statusChangedCallback;
    params.onDocumentsEnded = &documentEndedCallback;
    if (pushFilter != nullptr) params.pushFilter = &pushFilterFunction;
    if (pullFilter != nullptr) params.validationFunc = &validationFunction;
    params.callbackContext = storeContext(env, jReplicatorContext);

    C4Error error;
    C4Replicator *repl = c4repl_newLocal((C4Database *) jdb, (C4Database *) jotherLocalDB, params, &error);
    if (!repl) {
        throwError(env, error);
        return 0;
    }
    return (jlong) repl;
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    createWithSocket
 * Signature: (JJIILjava/lang/Object;[B)J
 *
 * This method accesses global state: not thread safe
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_createWithSocket(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jlong jopenSocket,
        jint jpush,
        jint jpull,
        jobject jReplicatorContext,
        jbyteArray joptions) {
    auto *db = (C4Database *) jdb;
    auto openSocket = (C4Socket *) jopenSocket;
    jbyteArraySlice options(env, joptions, false);

    C4ReplicatorParameters params = {};
    params.push = (C4ReplicatorMode) jpush;
    params.pull = (C4ReplicatorMode) jpull;
    params.optionsDictFleece = options;
    params.onStatusChanged = &statusChangedCallback;
    params.callbackContext = storeContext(env, jReplicatorContext);

    C4Error error;
    C4Replicator *repl = c4repl_newWithSocket(db, openSocket, params, &error);
    if (!repl) {
        throwError(env, error);
        return 0;
    }

    return (jlong) repl;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    free
 * Signature: (JLjava/lang/Object;Ljava/lang/Object;)V
 *
 * This method accesses global state: not thread safe
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_free(
        JNIEnv *env,
        jclass ignore,
        jlong repl,
        jobject replicatorContext,
        jobject socketFactoryContext) {
    releaseContext(env, replicatorContext);
    releaseContext(env, socketFactoryContext);
    c4repl_free((C4Replicator *) repl);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    start
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_start(JNIEnv *env, jclass ignore, jlong repl, jboolean restart) {
    c4repl_start((C4Replicator *) repl, (bool) restart);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    stop
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_stop(JNIEnv *env, jclass ignore, jlong repl) {
    c4repl_stop((C4Replicator *) repl);
}

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_setOptions(JNIEnv *env, jclass clazz, jlong repl, jbyteArray joptions) {
    jbyteArraySlice options(env, joptions, false);
    c4repl_setOptions((C4Replicator *) repl, options);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    getStatus
 * Signature: (J)Lcom/couchbase/lite/internal/core/C4ReplicatorStatus;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_getStatus(JNIEnv *env, jclass ignore, jlong repl) {
    C4ReplicatorStatus status = c4repl_getStatus((C4Replicator *) repl);
    return toJavaReplStatus(env, status);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    getPendingDocIDs
 * Signature: (J)Ljava/lang/Object;
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_getPendingDocIds(JNIEnv *env, jclass ignore, jlong repl) {
    C4Error c4Error = {};

    C4SliceResult res = c4repl_getPendingDocIDs((C4Replicator *) repl, &c4Error);

    if (c4Error.domain != 0 && c4Error.code != 0)
        throwError(env, c4Error);

    auto *sliceResult = (C4SliceResult *) ::malloc(sizeof(C4SliceResult));

    sliceResult->buf = res.buf;
    sliceResult->size = res.size;

    return (jlong) sliceResult;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    isDocumentPending
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_isDocumentPending(JNIEnv *env, jclass ignore, jlong repl, jstring jDocId) {
    jstringSlice docId(env, jDocId);

    C4Error c4Error = {};
    bool pending = c4repl_isDocumentPending((C4Replicator *) repl, docId, &c4Error);

    if (c4Error.domain != 0 && c4Error.code != 0)
        throwError(env, c4Error);

    return (jboolean) pending;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Replicator
 * Method:    setHostReachable
 * Signature: (JZ)Z
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Replicator_setHostReachable(JNIEnv *env, jclass ignore, jlong repl, jboolean reachable) {
    c4repl_setHostReachable((C4Replicator *) repl, (bool) reachable);
}
}