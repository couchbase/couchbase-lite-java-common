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
        if (localClass == nullptr)
            return false;

        cls_C4Replicator = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_C4Replicator == nullptr)
            return false;

        m_C4Replicator_statusChangedCallback = env->GetStaticMethodID(
                cls_C4Replicator,
                "statusChangedCallback",
                "(JLcom/couchbase/lite/internal/core/C4ReplicatorStatus;)V");
        if (m_C4Replicator_statusChangedCallback == nullptr)
            return false;

        m_C4Replicator_documentEndedCallback = env->GetStaticMethodID(
                cls_C4Replicator,
                "documentEndedCallback",
                "(JZ[Lcom/couchbase/lite/internal/core/C4DocumentEnded;)V");
        if (m_C4Replicator_documentEndedCallback == nullptr)
            return false;
    }

    // C4ReplicatorStatus, constructor, and fields
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4ReplicatorStatus");
        if (localClass == nullptr)
            return false;

        cls_C4ReplStatus = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_C4ReplStatus == nullptr)
            return false;

        m_C4ReplStatus_init = env->GetMethodID(cls_C4ReplStatus, "<init>", "(IJJJIII)V");
        if (m_C4ReplStatus_init == nullptr)
            return false;
    }

    // C4DocumentEnded, constructor, and fields
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DocumentEnded");
        if (localClass == nullptr)
            return false;

        cls_C4DocEnded = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_C4DocEnded == nullptr)
            return false;

        m_C4DocEnded_init = env->GetMethodID(
                cls_C4DocEnded,
                "<init>",
                "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJIIIZ)V");
        if (m_C4DocEnded_init == nullptr)
            return false;
    }

    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/ReplicationCollection");
        if (localClass == nullptr)
            return false;

        cls_ReplColl = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_ReplColl == nullptr)
            return false;

        f_ReplColl_token = env->GetFieldID(cls_ReplColl, "token", "J");
        if (f_ReplColl_token == nullptr)
            return false;

        f_ReplColl_scope = env->GetFieldID(cls_ReplColl, "scope", "Ljava/lang/String;");
        if (f_ReplColl_scope == nullptr)
            return false;

        f_ReplColl_name = env->GetFieldID(cls_ReplColl, "name", "Ljava/lang/String;");
        if (f_ReplColl_name == nullptr)
            return false;

        f_ReplColl_options = env->GetFieldID(cls_ReplColl, "options", "[B");
        if (f_ReplColl_options == nullptr)
            return false;

        f_ReplColl_pushFilter = env->GetFieldID(
                cls_ReplColl,
                "c4PushFilter",
                "Lcom/couchbase/lite/internal/ReplicationCollection$C4Filter;");
        if (f_ReplColl_pushFilter == nullptr)
            return false;

        f_ReplColl_pullFilter = env->GetFieldID(
                cls_ReplColl,
                "c4PullFilter",
                "Lcom/couchbase/lite/internal/ReplicationCollection$C4Filter;");
        if (f_ReplColl_pullFilter == nullptr)
            return false;

        m_ReplColl_filterCallback = env->GetStaticMethodID(
                cls_ReplColl,
                "filterCallback",
                "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJZ)Z");
        if (m_ReplColl_filterCallback == nullptr)
            return false;
    }

    jniLog("replicator initialized");
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
            (jint) status.error.domain,
            (jint) status.error.code,
            (jint) status.error.internal_info);
}

static jobject toJavaDocumentEnded(JNIEnv *env, const C4DocumentEnded *document) {
    jstring _scope = toJString(env, document->collectionSpec.scope);
    jstring _name = toJString(env, document->collectionSpec.name);
    jstring _docID = toJString(env, document->docID);
    jstring _revID = toJString(env, document->docID);

    jobject _docEnd = env->NewObject(
            cls_C4DocEnded,
            m_C4DocEnded_init,
            (jlong) document->collectionContext,
            _scope,
            _name,
            _docID,
            _revID,
            (jint) document->flags,
            (jlong) document->sequence,
            (jint) document->error.domain,
            (jint) document->error.code,
            (jint) document->error.internal_info,
            (jboolean) document->errorIsTransient);

    if (_scope != nullptr) env->DeleteLocalRef(_scope);
    if (_name != nullptr) env->DeleteLocalRef(_name);
    if (_docID != nullptr) env->DeleteLocalRef(_docID);
    if (_revID != nullptr) env->DeleteLocalRef(_revID);

    return _docEnd;
}

static jobjectArray toJavaDocumentEndedArray(JNIEnv *env, int arraySize, const C4DocumentEnded *array[]) {
    jobjectArray ds = env->NewObjectArray(arraySize, cls_C4DocEnded, nullptr);
    for (int i = 0; i < arraySize; i++) {
        jobject d = toJavaDocumentEnded(env, array[i]);
        env->SetObjectArrayElement(ds, i, d);
        if (d != nullptr) env->DeleteLocalRef(d);
    }
    return ds;
}

// I am so sorry.  IANAC++P.
// The second and third vectors here, the 4th & 5th arguments,
// are just around to keep the slices they contain from going out of scope.
// All they do is hold on to them, so that the C4ReplicationCollections
// in colls can point to them, until the end of the *caller's* scope.
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
        if (replColl == nullptr) continue;

        jobject jscope = env->GetObjectField(replColl, f_ReplColl_scope);
        auto pScope = std::make_shared<jstringSlice>(env, (jstring) jscope);
        if (jscope != nullptr) env->DeleteLocalRef(jscope);
        collNames.push_back(pScope);
        colls[i].collection.scope = *pScope;

        jobject jname = env->GetObjectField(replColl, f_ReplColl_name);
        auto pName = std::make_shared<jstringSlice>(env, (jstring) jname);
        if (jname != nullptr) env->DeleteLocalRef(jname);
        collNames.push_back(pName);
        colls[i].collection.name = *pName;

        colls[i].push = pushMode;
        colls[i].pull = pullMode;

        jobject joptions = env->GetObjectField(replColl, f_ReplColl_options);
        auto pOptions = std::make_shared<jbyteArraySlice>(env, true, (jbyteArray) joptions);
        collOptions.push_back(pOptions);
        colls[i].optionsDictFleece = *pOptions;

        jobject pushf = env->GetObjectField(replColl, f_ReplColl_pushFilter);
        if (pushf != nullptr) {
            colls[i].pushFilter = &pushFilterFunction;
            env->DeleteLocalRef(pushf);
        }
        jobject pullf = env->GetObjectField(replColl, f_ReplColl_pullFilter);
        if (pullf != nullptr) {
            colls[i].pullFilter = &pullFilterFunction;
            env->DeleteLocalRef(pullf);
        }

        colls[i].callbackContext = (void *) env->GetLongField(replColl, f_ReplColl_token);

        env->DeleteLocalRef(replColl);
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
    jint envState = attachJVM(&env, "statusChanged");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    jobject _status = toJavaReplStatus(env, status);
    env->CallStaticVoidMethod(cls_C4Replicator, m_C4Replicator_statusChangedCallback, (jlong) token, _status);

    if (envState == JNI_EDETACHED) {
        detachJVM("statusChanged");
    } else {
        if (_status != nullptr) env->DeleteLocalRef(_status);
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
static void documentEndedCallback(
        C4Replicator *ignore,
        bool pushing,
        size_t numDocs,
        const C4DocumentEnded *documentEnded[],
        void *token) {
    assert(numDocs < 16384);
    int nDocs = (int) numDocs;

    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "docEnded");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    jobjectArray docs = toJavaDocumentEndedArray(env, nDocs, documentEnded);
    env->CallStaticVoidMethod(cls_C4Replicator, m_C4Replicator_documentEndedCallback, (jlong) token, pushing, docs);

    if (envState == JNI_EDETACHED) {
        detachJVM("docEnded");
    } else {
        if (docs != nullptr) env->DeleteLocalRef(docs);
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
    jint envState = attachJVM(&env, "replicationFilter");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return false;

    jstring _scope = toJString(env, coll.scope);
    jstring _name = toJString(env, coll.name);
    jstring _docID = toJString(env, docID);
    jstring _revID = toJString(env, revID);

    jboolean ok = env->CallStaticBooleanMethod(
            cls_ReplColl,
            m_ReplColl_filterCallback,
            (jlong) token,
            _scope,
            _name,
            _docID,
            _revID,
            flags,
            (jlong) dict,
            isPush);

    if (envState == JNI_EDETACHED) {
        detachJVM("replicationFilter");
    } else {
        if (_scope != nullptr) env->DeleteLocalRef(_scope);
        if (_name != nullptr) env->DeleteLocalRef(_name);
        if (_docID != nullptr) env->DeleteLocalRef(_docID);
        if (_revID != nullptr) env->DeleteLocalRef(_revID);
    }

    return ok != JNI_FALSE;
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
 * Signature: (Ljava/lang/String;[Lcom.couchbase.lite.internal.core.C4ReplicationCollection;JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;IZZZ[BJJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_create(
        JNIEnv *env,
        jclass ignored,
        jstring jid,
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
    jstringSlice id(env, jid);
    jstringSlice scheme(env, jscheme);
    jstringSlice host(env, jhost);
    jstringSlice path(env, jpath);
    jstringSlice remoteDBName(env, jremoteDBName);
    jbyteArraySlice options(env, joptions);

    C4Address c4Address{};
    c4Address.scheme = scheme;
    c4Address.hostname = host;
    c4Address.port = (uint16_t) jport;
    c4Address.path = path;

    C4SocketFactory socketFactory = socket_factory();
    socketFactory.context = (void *) socketFactoryToken;
    socketFactory.framing = (C4SocketFraming) jframing;

    C4ReplicatorParameters params{};
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
    C4Replicator *repl = c4repl_new((C4Database *) jdb, c4Address, remoteDBName, params, id, &error);
    if ((repl == nullptr) && (error.code != 0)) {
        throwError(env, error);
        return 0;
    }

    return (jlong) repl;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    create
 * Signature: (Ljava/lang/String;[Lcom.couchbase.lite.internal.core.C4ReplicationCollection;JJZZZ[BJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_createLocal(
        JNIEnv *env,
        jclass ignored,
        jstring jid,
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
    jstringSlice id(env, jid);
    jbyteArraySlice options(env, joptions);

    C4ReplicatorParameters params{};
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
    C4Replicator *repl = c4repl_newLocal((C4Database *) jdb, (C4Database *) targetDb, params, id, &error);
    if ((repl == nullptr) && (error.code != 0)) {
        throwError(env, error);
        return 0;
    }

    return (jlong) repl;
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    createWithSocket
 * Signature: (Ljava/lang/String;[Lcom.couchbase.lite.internal.core.C4ReplicationCollection;JJ[BJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_createWithSocket(
        JNIEnv *env,
        jclass ignored,
        jstring jid,
        jobjectArray jCollDescs,
        jlong jdb,
        jlong jopenSocket,
        jbyteArray joptions,
        jlong replicatorToken) {
    jstringSlice id(env, jid);
    jbyteArraySlice options(env, joptions);
    auto *db = (C4Database *) jdb;
    auto openSocket = (C4Socket *) jopenSocket;

    C4ReplicatorParameters params{};
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
    C4Replicator *repl = c4repl_newWithSocket(db, openSocket, params, id, &error);
    if ((repl == nullptr) && (error.code != 0)) {
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
    jbyteArraySlice options(env, joptions);
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
    if (!res) {
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
    if (!pending && c4Error.code != 0) {
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
    bool ok = c4repl_setProgressLevel((C4Replicator *) repl, (C4ReplicatorProgressLevel) level, &error);
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
