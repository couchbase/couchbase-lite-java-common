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
#include <memory>
#include <vector>
#include "c4Base.h"
#include "native_glue.hh"
#include "socket_factory.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4Replicator.h"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4Replicator
// ----------------------------------------------------------------------------

// C4Replicator
static jclass cls_C4Replicator;                         // global reference
static jmethodID m_C4Replicator_statusChangedCallback;  // statusChangedCallback method
static jmethodID m_C4Replicator_documentEndedCallback;  // documentEndedCallback method

// C4ReplicatorStatus
static jclass cls_C4ReplStatus; // global reference
static jmethodID m_C4ReplStatus_init;

// C4DocumentEnded
static jclass cls_C4DocEnded;
static jmethodID m_C4DocEnded_init;

// ReplicationCollection
static jclass cls_ReplColl;                             // global reference
static jfieldID f_ReplColl_token;
static jfieldID f_ReplColl_scope;
static jfieldID f_ReplColl_name;
static jfieldID f_ReplColl_options;
static jfieldID f_ReplColl_pushFilter;
static jfieldID f_ReplColl_pullFilter;
static jmethodID m_ReplColl_filterCallback;             // validationFunction method
static bool pullFilterFunction(C4CollectionSpec, C4String, C4String, C4RevisionFlags, FLDict, void *);

static bool pushFilterFunction(C4CollectionSpec, C4String, C4String, C4RevisionFlags, FLDict, void *);

bool litecore::jni::initC4Replicator(JNIEnv *env) {
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

        m_C4DocEnded_init = env->GetMethodID(
                cls_C4DocEnded,
                "<init>",
                "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJIIIZ)V");
        if (!m_C4DocEnded_init)
            return false;
    }

    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/ReplicationCollection");
        if (!localClass)
            return false;

        cls_ReplColl = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_ReplColl)
            return false;

        f_ReplColl_token = env->GetFieldID(cls_ReplColl, "token", "J");
        if (!f_ReplColl_token)
            return false;

        f_ReplColl_scope = env->GetFieldID(cls_ReplColl, "scope", "Ljava/lang/String;");
        if (!f_ReplColl_scope)
            return false;

        f_ReplColl_name = env->GetFieldID(cls_ReplColl, "name", "Ljava/lang/String;");
        if (!f_ReplColl_name)
            return false;

        f_ReplColl_options = env->GetFieldID(cls_ReplColl, "options", "[B");
        if (!f_ReplColl_options)
            return false;

        f_ReplColl_pushFilter = env->GetFieldID(
                cls_ReplColl,
                "c4PushFilter",
                "Lcom/couchbase/lite/internal/ReplicationCollection$C4Filter;");
        if (!f_ReplColl_pushFilter)
            return false;

        f_ReplColl_pullFilter = env->GetFieldID(
                cls_ReplColl,
                "c4PushFilter",
                "Lcom/couchbase/lite/internal/ReplicationCollection$C4Filter;");
        if (!f_ReplColl_pullFilter)
            return false;

        m_ReplColl_filterCallback = env->GetStaticMethodID(
                cls_ReplColl,
                "filterCallback",
                "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJZ)Z");
        if (!m_ReplColl_filterCallback)
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
    jstring scope = toJString(env, document->collectionSpec.scope);
    jstring name = toJString(env, document->collectionSpec.name);
    jstring docID = toJString(env, document->docID);
    jstring revID = toJString(env, document->docID);
    return env->NewObject(
            cls_C4DocEnded,
            m_C4DocEnded_init,
            (jlong) document->collectionContext,
            scope,
            name,
            docID,
            revID,
            (jint) document->flags,
            (jlong) document->sequence,
            (jint) document->error.domain,
            (jint) document->error.code,
            (jint) document->error.internal_info,
            (jboolean) document->errorIsTransient);
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

// I am so sorry.  IANAC++P.
// The second vector, here (the 4th argument), is just around to
// keep the jstringSlices from going out of scope.  All it does
// is hold on to them, so that the C4ReplicationCollections in
// colls can point to them, until the end of the caller's scope.
static int fromJavaReplColls(
        JNIEnv *env,
        jobjectArray jColls,
        std::vector<C4ReplicationCollection> &colls,
        std::vector<std::shared_ptr<jstringSlice>> &collNames,
        std::vector<std::shared_ptr<jbyteArraySlice>> &collOptions,
        C4ReplicatorMode pushMode,
        C4ReplicatorMode pullMode) {
    int nColls = env->GetArrayLength(jColls);
    colls.resize(nColls);

    for (jsize i = 0; i < nColls; i++) {
        jobject replColl = env->GetObjectArrayElement(jColls, i);

        jobject jscope = env->GetObjectField(replColl, f_ReplColl_scope);
        auto pScope = std::make_shared<jstringSlice>(env, (jstring) jscope);
        collNames.push_back(pScope);
        colls[i].collection.scope = *pScope;

        jobject jname = env->GetObjectField(replColl, f_ReplColl_name);
        auto pName = std::make_shared<jstringSlice>(env, (jstring) jname);
        collNames.push_back(pName);
        colls[i].collection.name = *pName;

        colls[i].push = pushMode;
        colls[i].pull = pullMode;

        jobject joptions = env->GetObjectField(replColl, f_ReplColl_options);
        auto pOptions = std::make_shared<jbyteArraySlice>(env, (jbyteArray) joptions, false);
        collOptions.push_back(pOptions);
        colls[i].optionsDictFleece = *pOptions;

        if (env->GetObjectField(replColl, f_ReplColl_pushFilter) != nullptr)
            colls[i].pushFilter = &pushFilterFunction;
        if (env->GetObjectField(replColl, f_ReplColl_pullFilter) != nullptr)
            colls[i].pullFilter = &pullFilterFunction;

        colls[i].callbackContext = (void *) env->GetLongField(replColl, f_ReplColl_token);
    }

    return nColls;
}

/**
 * Callback a client can register, to get progress information.
 * This will be called on arbitrary background threads and should not block.
 *
 * @param ignored
 * @param status
 * @param token
 */
static void statusChangedCallback(C4Replicator *ignored, C4ReplicatorStatus status, void *token) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4Replicator,
                                  m_C4Replicator_statusChangedCallback,
                                  (jlong) token,
                                  toJavaReplStatus(env, status));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            env->CallStaticVoidMethod(cls_C4Replicator,
                                      m_C4Replicator_statusChangedCallback,
                                      (jlong) token,
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
 * @param ignored
 * @param pushing
 * @param numDocs
 * @param documentEnded
 * @param token
 */
static void documentEndedCallback(C4Replicator *ignored,
                                  bool pushing,
                                  size_t numDocs,
                                  const C4DocumentEnded *documentEnded[],
                                  void *token) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        jobjectArray docs = toJavaDocumentEndedArray(env, numDocs, documentEnded);
        env->CallStaticVoidMethod(cls_C4Replicator,
                                  m_C4Replicator_documentEndedCallback,
                                  (jlong) token,
                                  pushing,
                                  docs);
        env->DeleteLocalRef(docs);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            jobjectArray docs = toJavaDocumentEndedArray(env, numDocs, documentEnded);
            env->CallStaticVoidMethod(cls_C4Replicator,
                                      m_C4Replicator_documentEndedCallback,
                                      (jlong) token,
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

static jboolean replicationFilter(
        void *token,
        C4CollectionSpec coll,
        C4String docID,
        C4String revID,
        C4RevisionFlags flags,
        FLDict dict,
        bool isPush) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    bool res = false;
    if (getEnvStat == JNI_OK) {
        res = env->CallStaticBooleanMethod(cls_ReplColl,
                                           m_ReplColl_filterCallback,
                                           (jlong) token,
                                           toJString(env, coll.scope),
                                           toJString(env, coll.name),
                                           toJString(env, docID),
                                           toJString(env, revID),
                                           flags,
                                           (jlong) dict,
                                           isPush);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            res = env->CallStaticBooleanMethod(cls_ReplColl,
                                               m_ReplColl_filterCallback,
                                               (jlong) token,
                                               toJString(env, coll.scope),
                                               toJString(env, coll.name),
                                               toJString(env, docID),
                                               toJString(env, revID),
                                               flags,
                                               (jlong) dict,
                                               isPush);
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

/**
 * Callback that can choose to reject an incoming pulled revision by returning false.
 * (Note: In the case of an incoming revision, no flags other than 'deletion' and
 * 'hasAttachments' will be set.)
 *
 * @param coll
 * @param token
 * @param docID
 * @param revID
 * @param flags
 * @param dict
 */
static bool pullFilterFunction(
        C4CollectionSpec coll,
        C4String docID,
        C4String revID,
        C4RevisionFlags flags,
        FLDict dict,
        void *token) {
    return replicationFilter(token, coll, docID, revID, flags, dict, false) == JNI_TRUE;
}

/**
 * Callback that can stop a local revision from being pushed by returning false.
 * (Note: In the case of an incoming revision, no flags other than 'deletion' and
 * 'hasAttachments' will be set.)
 *
 * @param coll
 * @param token
 * @param docID
 * @param revID
 * @param flags
 * @param dict
 * @param token
 */
static bool pushFilterFunction(
        C4CollectionSpec coll,
        C4String docID,
        C4String revID,
        C4RevisionFlags flags,
        FLDict dict,
        void *token) {
    return (bool) replicationFilter(token, coll, docID, revID, flags, dict, true) == JNI_TRUE;
}


extern "C" {
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    create
 * Signature: ([Lcom.couchbase.lite.internal.core.C4ReplicationCollection;JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;IZZZ[BJJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_create(
        JNIEnv *env,
        jclass ignored,
        jobjectArray jCollDescs,
        jlong jdb,
        jstring jscheme,
        jstring jhost,
        jint jport,
        jstring jpath,
        jstring jremoteDBName,
        jint jframing,
        jboolean push,
        jboolean pull,
        jboolean continuous,
        jbyteArray joptions,
        jlong replicatorToken,
        jlong socketFactoryToken) {
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

    C4SocketFactory socketFactory = socket_factory();
    socketFactory.context = (void *) socketFactoryToken;
    socketFactory.framing = (C4SocketFraming) jframing;

    C4ReplicatorParameters params = {};
    params.optionsDictFleece = options;
    params.onStatusChanged = &statusChangedCallback;
    params.onDocumentsEnded = &documentEndedCallback;
    params.callbackContext = (void *) replicatorToken;
    params.socketFactory = &socketFactory;

    C4ReplicatorMode mode = (continuous) ? kC4Continuous : kC4OneShot;

    std::vector<C4ReplicationCollection> collectionDescs;
    std::vector<std::shared_ptr<jstringSlice>> collectionNames;
    std::vector<std::shared_ptr<jbyteArraySlice>> collectionOptions;
    int nColls = fromJavaReplColls(
            env,
            jCollDescs,
            collectionDescs,
            collectionNames,
            collectionOptions,
            (push == JNI_TRUE) ? mode : kC4Disabled,
            (pull == JNI_TRUE) ? mode : kC4Disabled);
    if (nColls < 0) {
        C4Error error = {LiteCoreDomain, kC4ErrorInvalidParameter};
        throwError(env, error);
        return 0;
    }
    params.collectionCount = nColls;
    params.collections = collectionDescs.data();

    C4Error error{};
    C4Replicator *repl = c4repl_new((C4Database *) jdb, c4Address, remoteDBName, params, &error);
    if (!repl && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    return (jlong) repl;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    create
 * Signature: ([Lcom.couchbase.lite.internal.core.C4ReplicationCollection;JJZZZ[BJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_createLocal(
        JNIEnv *env,
        jclass ignored,
        jobjectArray jCollDescs,
        jlong jdb,
        jlong targetDb,
        jboolean push,
        jboolean pull,
        jboolean continuous,
        jbyteArray joptions,
        jlong replicatorToken) {
#ifndef COUCHBASE_ENTERPRISE
    C4Error error = {LiteCoreDomain, kC4ErrorUnimplemented};
    throwError(env, error);
    return 0;
#else
    jbyteArraySlice options(env, joptions, false);

    C4ReplicatorParameters params = {};
    params.optionsDictFleece = options;
    params.onStatusChanged = &statusChangedCallback;
    params.onDocumentsEnded = &documentEndedCallback;
    params.callbackContext = (void *) replicatorToken;

    C4ReplicatorMode mode = (continuous) ? kC4Continuous : kC4OneShot;

    std::vector<C4ReplicationCollection> collectionDescs;
    std::vector<std::shared_ptr<jstringSlice>> collectionNames;
    std::vector<std::shared_ptr<jbyteArraySlice>> collectionOptions;
    int nColls = fromJavaReplColls(
            env,
            jCollDescs,
            collectionDescs,
            collectionNames,
            collectionOptions,
            (push == JNI_TRUE) ? mode : kC4Disabled,
            (pull == JNI_TRUE) ? mode : kC4Disabled);
    if (nColls < 0) {
        C4Error error = {LiteCoreDomain, kC4ErrorInvalidParameter};
        throwError(env, error);
        return 0;
    }
    params.collectionCount = nColls;
    params.collections = collectionDescs.data();

    C4Error error{};
    C4Replicator *repl = c4repl_newLocal((C4Database *) jdb, (C4Database *) targetDb, params, &error);
    if (!repl && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    return (jlong) repl;
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    createWithSocket
 * Signature: ([Lcom.couchbase.lite.internal.core.C4ReplicationCollection;JJ[BJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_createWithSocket(
        JNIEnv *env,
        jclass ignored,
        jobjectArray jCollDescs,
        jlong jdb,
        jlong jopenSocket,
        jbyteArray joptions,
        jlong replicatorToken) {
    jbyteArraySlice options(env, joptions, false);
    auto *db = (C4Database *) jdb;
    auto openSocket = (C4Socket *) jopenSocket;

    C4ReplicatorParameters params = {};
    params.optionsDictFleece = options;
    params.onStatusChanged = &statusChangedCallback;
    params.callbackContext = (void *) replicatorToken;

    std::vector<C4ReplicationCollection> collectionDescs;
    std::vector<std::shared_ptr<jstringSlice>> collectionNames;
    std::vector<std::shared_ptr<jbyteArraySlice>> collectionOptions;
    int nColls = fromJavaReplColls(
            env,
            jCollDescs,
            collectionDescs,
            collectionNames,
            collectionOptions,
            kC4Passive,
            kC4Passive);
    if (nColls < 0) {
        C4Error error = {LiteCoreDomain, kC4ErrorInvalidParameter};
        throwError(env, error);
        return 0;
    }
    params.collectionCount = nColls;
    params.collections = collectionDescs.data();

    C4Error error{};
    C4Replicator *repl = c4repl_newWithSocket(db, openSocket, params, &error);
    if (!repl && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    return (jlong) repl;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_free(
        JNIEnv *env,
        jclass ignored,
        jlong repl) {
    c4repl_free((C4Replicator *) repl);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    start
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_start(
        JNIEnv *env,
        jclass ignored,
        jlong repl,
        jboolean restart) {
    c4repl_start((C4Replicator *) repl, (bool) restart);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    stop
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_stop(
        JNIEnv *env,
        jclass ignored,
        jlong repl) {
    c4repl_stop((C4Replicator *) repl);
}

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_setOptions(
        JNIEnv *env,
        jclass ignored,
        jlong repl,
        jbyteArray joptions) {
    jbyteArraySlice options(env, joptions, false);
    c4repl_setOptions((C4Replicator *) repl, options);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    getStatus
 * Signature: (J)Lcom/couchbase/lite/internal/core/C4ReplicatorStatus;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_getStatus(
        JNIEnv *env,
        jclass ignored,
        jlong repl) {
    C4ReplicatorStatus status = c4repl_getStatus((C4Replicator *) repl);
    return toJavaReplStatus(env, status);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    getPendingDocIDs
 * Signature: (JLjava/lang/String;Ljava/lang/String;)Lcom/couchbase/lite/internal/fleece/FLSliceResult;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_getPendingDocIds(
        JNIEnv *env,
        jclass ignored,
        jlong repl,
        jstring jscope,
        jstring jcollection) {
    jstringSlice scope(env, jscope);
    jstringSlice collection(env, jcollection);
    C4CollectionSpec collSpec = {collection, scope};

    C4Error error{};

    C4SliceResult res = c4repl_getPendingDocIDs((C4Replicator *) repl, collSpec, &error);
    if (error.domain != 0 && error.code != 0) {
        throwError(env, error);
        return nullptr;
    }

    return toJavaFLSliceResult(env, res);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    isDocumentPending
 * Signature: (JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_isDocumentPending(
        JNIEnv *env,
        jclass ignored,
        jlong repl,
        jstring jDocId,
        jstring jscope,
        jstring jcollection) {
    jstringSlice docId(env, jDocId);
    jstringSlice scope(env, jscope);
    jstringSlice collection(env, jcollection);
    C4CollectionSpec collSpec = {collection, scope};

    C4Error c4Error{};
    bool pending = c4repl_isDocumentPending((C4Replicator *) repl, docId, collSpec, &c4Error);
    if (c4Error.domain != 0 && c4Error.code != 0) {
        throwError(env, c4Error);
        return false;
    }

    return (jboolean) pending;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    setProgressLevel
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_setProgressLevel(
        JNIEnv *env,
        jclass ignored,
        jlong repl,
        jint level) {
    C4Error error{};
    auto ok = c4repl_setProgressLevel((C4Replicator *) repl, (C4ReplicatorProgressLevel) level, &error);
    if (!ok && error.code != 0) {
        throwError(env, error);
    }
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    setHostReachable
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_setHostReachable(
        JNIEnv *env,
        jclass ignored,
        jlong repl,
        jboolean reachable) {
    c4repl_setHostReachable((C4Replicator *) repl, (bool) reachable);
}
}