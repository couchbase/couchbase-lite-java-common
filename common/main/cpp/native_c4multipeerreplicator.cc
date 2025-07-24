//
// native_c4multipeerreplicator.cc
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
#if defined(COUCHBASE_ENTERPRISE) && defined(__ANDROID__)

#include <vector>
#include "c4PeerSync.h"
#include "c4PeerDiscovery.hh"
#include "native_glue.hh"
#include "native_c4replutils.hh"
#include "socket_factory.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator.h"

using namespace litecore;
using namespace litecore::jni;
using namespace std;

namespace litecore::jni {
    //-------------------------------------------------------------------------
    // com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
    //
    // I am appalled at the amount of copypasta code in this file.
    // I apologize to anyone that has to maintain it.
    // With a little less time pressure, I might have been able to
    // figure out how to build an abstraction that allowed sharing
    // most of the code here and in native_c4replicator.cc.
    // It is what it is.
    //-------------------------------------------------------------------------

    // C4MultipeerReplicator
    static jclass cls_C4MultipeerReplicator;               // global reference
    static jmethodID m_C4MultipeerReplicator_createPeerInfo;
    static jmethodID m_C4MultipeerReplicator_onSyncStatusChanged;
    static jmethodID m_C4MultipeerReplicator_onAuthenticate;
    static jmethodID m_C4MultipeerReplicator_onPeerDiscovered;
    static jmethodID m_C4MultipeerReplicator_onReplicatorStatusChanged;
    static jmethodID m_C4MultipeerReplicator_onDocumentEnded;

    // ReplicationCollection
    static jclass cls_MultipeerReplColl;                             // global reference
    static jfieldID f_MultipeerReplColl_token;
    static jfieldID f_MultipeerReplColl_scope;
    static jfieldID f_MultipeerReplColl_name;
    static jfieldID f_MultipeerReplColl_options;
    static jfieldID f_MultipeerReplColl_pushFilter;
    static jfieldID f_MultipeerReplColl_pullFilter;
    static jmethodID m_MultipeerReplColl_filterCallback;             // validationFunction method

    static bool pullFilterCallback(
            C4PeerSync *,
            const C4PeerID *,
            C4CollectionSpec,
            C4String,
            C4String,
            C4RevisionFlags,
            FLDict,
            void *);

    static bool pushFilterCallback(
            C4PeerSync *,
            const C4PeerID *,
            C4CollectionSpec,
            C4String,
            C4String,
            C4RevisionFlags,
            FLDict,
            void *);

    //-------------------------------------------------------------------------
    // Package initialization
    // ???  This is stuff that is not necessarily going to be used.
    //      Perhaps we should lazily find callback methods
    //      and explicitly release them, to minimize GlobalRefs?
    //-------------------------------------------------------------------------

    bool initC4MultipeerReplicator(JNIEnv *env) {
        // C4MultipeerReplicator callbacks
        {
            jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4MultipeerReplicator");
            if (localClass == nullptr)
                return false;

            cls_C4MultipeerReplicator = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
            if (cls_C4MultipeerReplicator == nullptr)
                return false;

            m_C4MultipeerReplicator_createPeerInfo = env->GetStaticMethodID(
                    cls_C4MultipeerReplicator,
                    "createPeerInfo",
                    "([B[BZ[[BLcom/couchbase/lite/internal/core/C4ReplicatorStatus;)Lcom/couchbase/lite/PeerInfo;");

            if (m_C4MultipeerReplicator_createPeerInfo == nullptr)
                return false;

            m_C4MultipeerReplicator_onSyncStatusChanged = env->GetStaticMethodID(
                    cls_C4MultipeerReplicator,
                    "onSyncStatusChanged",
                    "(JZIJ)V");

            if (m_C4MultipeerReplicator_onSyncStatusChanged == nullptr)
                return false;

            m_C4MultipeerReplicator_onAuthenticate = env->GetStaticMethodID(
                    cls_C4MultipeerReplicator,
                    "onAuthenticate",
                    "(J[B[B)Z");

            if (m_C4MultipeerReplicator_onAuthenticate == nullptr)
                return false;

            m_C4MultipeerReplicator_onPeerDiscovered = env->GetStaticMethodID(
                    cls_C4MultipeerReplicator,
                    "onPeerDiscovered",
                    "(J[BZ)V");

            if (m_C4MultipeerReplicator_onPeerDiscovered == nullptr)
                return false;

            m_C4MultipeerReplicator_onReplicatorStatusChanged = env->GetStaticMethodID(
                    cls_C4MultipeerReplicator,
                    "onReplicatorStatusChanged",
                    "(J[BZLcom/couchbase/lite/internal/core/C4ReplicatorStatus;)V");

            if (m_C4MultipeerReplicator_onReplicatorStatusChanged == nullptr)
                return false;

            m_C4MultipeerReplicator_onDocumentEnded = env->GetStaticMethodID(
                    cls_C4MultipeerReplicator,
                    "onDocumentEnded",
                    "(J[BZ[Lcom/couchbase/lite/internal/core/C4DocumentEnded;)V");

            if (m_C4MultipeerReplicator_onDocumentEnded == nullptr)
                return false;
        }

        {
            jclass localClass = env->FindClass("com/couchbase/lite/internal/MultipeerReplicationCollection");
            if (localClass == nullptr)
                return false;

            cls_MultipeerReplColl = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
            if (cls_MultipeerReplColl == nullptr)
                return false;

            f_MultipeerReplColl_token = env->GetFieldID(cls_MultipeerReplColl, "token", "J");
            if (f_MultipeerReplColl_token == nullptr)
                return false;

            f_MultipeerReplColl_scope = env->GetFieldID(cls_MultipeerReplColl, "scope", "Ljava/lang/String;");
            if (f_MultipeerReplColl_scope == nullptr)
                return false;

            f_MultipeerReplColl_name = env->GetFieldID(cls_MultipeerReplColl, "name", "Ljava/lang/String;");
            if (f_MultipeerReplColl_name == nullptr)
                return false;

            f_MultipeerReplColl_options = env->GetFieldID(cls_MultipeerReplColl, "options", "[B");
            if (f_MultipeerReplColl_options == nullptr)
                return false;

            f_MultipeerReplColl_pushFilter = env->GetFieldID(
                    cls_MultipeerReplColl,
                    "c4PushFilter",
                    "Lcom/couchbase/lite/internal/MultipeerReplicationCollection$C4Filter;");
            if (f_MultipeerReplColl_pushFilter == nullptr)
                return false;

            f_MultipeerReplColl_pullFilter = env->GetFieldID(
                    cls_MultipeerReplColl,
                    "c4PullFilter",
                    "Lcom/couchbase/lite/internal/MultipeerReplicationCollection$C4Filter;");
            if (f_MultipeerReplColl_pullFilter == nullptr)
                return false;

            m_MultipeerReplColl_filterCallback = env->GetStaticMethodID(
                    cls_MultipeerReplColl,
                    "filterCallback",
                    "(J[BLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJZ)Z");
            if (m_MultipeerReplColl_filterCallback == nullptr)
                return false;
        }

        auto factory = socket_factory();

        // DO NOT change this unless you change the corresponding entry in MultipeerSocketFactory.java
        constexpr size_t MP_SOCKET_FACTORY_TAG = 0x4D505250; // MPRP
        factory.context = (void *)MP_SOCKET_FACTORY_TAG;
        C4PeerDiscovery::setDefaultSocketFactory(factory);

        jniLog("multipeer replicator initialized");
        return true;
    }

    //-------------------------------------------------------------------------
    // Utility methods
    //-------------------------------------------------------------------------
    static jobjectArray fromC4PeerID(JNIEnv *env, C4PeerID *peerIds, size_t count) {
        jobjectArray neighborIds = env->NewObjectArray((jsize) count, env->FindClass("[B"), nullptr);
        for (size_t i = 0; i < count; i++) {
            jbyteArray neighborId = toJByteArray(env, peerIds[i].bytes, 32);
            env->SetObjectArrayElement(neighborIds, (jsize) i, neighborId);
            env->DeleteLocalRef(neighborId);
        }
        return neighborIds;
    }

    // The comment over in native_c4replicator.cc applies here as well.
    // I'm even sorrier that I have to duplicate this mess.
    static int fromJavaReplColls(
            JNIEnv *env,
            jobjectArray jColls,
            std::vector<C4PeerSyncCollection> &colls,
            std::vector<std::shared_ptr<jstringSlice>> &collNames,
            std::vector<std::shared_ptr<jbyteArraySlice>> &collOptions,
            bool pushMode,
            bool pullMode) {
        int nColls = env->GetArrayLength(jColls);
        colls.resize(nColls);

        for (jsize i = 0; i < nColls; i++) {
            jobject replColl = env->GetObjectArrayElement(jColls, i);
            if (replColl == nullptr) continue;

            jobject jscope = env->GetObjectField(replColl, f_MultipeerReplColl_scope);
            auto pScope = std::make_shared<jstringSlice>(env, (jstring) jscope);
            if (jscope != nullptr) env->DeleteLocalRef(jscope);
            collNames.push_back(pScope);
            colls[i].collection.scope = *pScope;

            jobject jname = env->GetObjectField(replColl, f_MultipeerReplColl_name);
            auto pName = std::make_shared<jstringSlice>(env, (jstring) jname);
            if (jname != nullptr) env->DeleteLocalRef(jname);
            collNames.push_back(pName);
            colls[i].collection.name = *pName;

            colls[i].pushEnabled = pushMode;
            colls[i].pullEnabled = pullMode;

            jobject joptions = env->GetObjectField(replColl, f_MultipeerReplColl_options);
            auto pOptions = std::make_shared<jbyteArraySlice>(env, true, (jbyteArray) joptions);
            collOptions.push_back(pOptions);
            colls[i].optionsDictFleece = *pOptions;

            jobject pushf = env->GetObjectField(replColl, f_MultipeerReplColl_pushFilter);
            if (pushf != nullptr) {
                colls[i].pushFilter = &pushFilterCallback;
                env->DeleteLocalRef(pushf);
            }
            jobject pullf = env->GetObjectField(replColl, f_MultipeerReplColl_pullFilter);
            if (pullf != nullptr) {
                colls[i].pullFilter = &pullFilterCallback;
                env->DeleteLocalRef(pullf);
            }

            colls[i].callbackContext = (void *) env->GetLongField(replColl, f_MultipeerReplColl_token);

            env->DeleteLocalRef(replColl);
        }

        return nColls;
    }

    //-------------------------------------------------------------------------
    // Callbacks
    //-------------------------------------------------------------------------

    static void statusChangedCallback(C4PeerSync *ignored, bool started, C4Error error, void *context) {
        JNIEnv *env = nullptr;
        jint envState = attachJVM(&env, "p2pStatusChanged");
        if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
            return;

        env->CallStaticVoidMethod(
                cls_C4MultipeerReplicator,
                m_C4MultipeerReplicator_onSyncStatusChanged,
                (jlong) context,
                started ? JNI_TRUE : JNI_FALSE,
                (jint) error.domain,
                (jlong) error.code);

        if (envState == JNI_EDETACHED)
            detachJVM("p2pStatusChanged");
    }

    static bool authenticateCallback(C4PeerSync *ignored, const C4PeerID *peerId, C4Cert *certChain, void *context) {
        JNIEnv *env = nullptr;
        jint envState = attachJVM(&env, "p2pAuthenticate");
        if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
            return false;

        C4SliceResult rawCertChain = c4cert_copyChainData(certChain);
        jbyteArray _certs = toJByteArray(env, rawCertChain);
        c4slice_free(rawCertChain);

        jbyteArray _peerId = toJByteArray(env, peerId->bytes, 32);
        jboolean ok = env->CallStaticBooleanMethod(
                cls_C4MultipeerReplicator,
                m_C4MultipeerReplicator_onAuthenticate,
                (jlong) context,
                _peerId,
                _certs);

        if (envState == JNI_EDETACHED) {
            detachJVM("p2pAuthenticate");
        } else {
            if (_peerId != nullptr) env->DeleteLocalRef(_peerId);
            if (_certs != nullptr) env->DeleteLocalRef(_certs);
        }

        return ok != JNI_FALSE;
    }

    static void peerDiscoveredCallback(C4PeerSync *ignored, const C4PeerID *peerId, bool online, void *context) {
        JNIEnv *env = nullptr;
        jint envState = attachJVM(&env, "p2pPeerDiscovered");
        if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
            return;

        jbyteArray _peerId = toJByteArray(env, peerId->bytes, 32);
        env->CallStaticVoidMethod(
                cls_C4MultipeerReplicator,
                m_C4MultipeerReplicator_onPeerDiscovered,
                (jlong) context,
                _peerId,
                online ? JNI_TRUE : JNI_FALSE);

        if (envState == JNI_EDETACHED) {
            detachJVM("p2pPeerDiscovered");
        } else {
            if (_peerId != nullptr) env->DeleteLocalRef(_peerId);
        }
    }

    static void replicatorStatusChangedCallback(
            C4PeerSync *ignored,
            const C4PeerID *peerId,
            bool outbound,
            const C4ReplicatorStatus *status,
            void *context) {
        JNIEnv *env = nullptr;
        jint envState = attachJVM(&env, "p2pReplStatusChanged");
        if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
            return;

        jbyteArray _peerId = toJByteArray(env, peerId->bytes, 32);
        jobject _status = toJavaReplStatus(env, *status);
        env->CallStaticVoidMethod(
                cls_C4MultipeerReplicator,
                m_C4MultipeerReplicator_onReplicatorStatusChanged,
                (jlong) context,
                _peerId,
                outbound ? JNI_TRUE : JNI_FALSE,
                _status);

        if (envState == JNI_EDETACHED) {
            detachJVM("p2pReplStatusChanged");
        } else {
            if (_peerId != nullptr) env->DeleteLocalRef(_peerId);
            if (_status != nullptr) env->DeleteLocalRef(_status);
        }
    }

    static void documentEndedCallback(
            C4PeerSync *ignored,
            const C4PeerID *peerId,
            bool pushing,
            size_t numDocs,
            const C4DocumentEnded *documentEnded[],
            void *context) {
        assert(numDocs < 16384);
        int nDocs = (int) numDocs;

        JNIEnv *env = nullptr;
        jint envState = attachJVM(&env, "docEnded");
        if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
            return;

        jbyteArray _peerId = toJByteArray(env, peerId->bytes, 32);
        jobjectArray _docs = toJavaDocumentEndedArray(env, nDocs, documentEnded);
        env->CallStaticVoidMethod(
                cls_C4MultipeerReplicator,
                m_C4MultipeerReplicator_onDocumentEnded,
                (jlong) context,
                _peerId,
                pushing ? JNI_TRUE : JNI_FALSE,
                _docs);

        if (envState == JNI_EDETACHED) {
            detachJVM("docEnded");
        } else {
            if (_peerId != nullptr) env->DeleteLocalRef(_peerId);
            if (_docs != nullptr) env->DeleteLocalRef(_docs);
        }
    }

    static C4PeerSyncCallbacks getPeerSyncCallbacks(long token) {
        return {
                &statusChangedCallback,
                &authenticateCallback,
                &peerDiscoveredCallback,
                &replicatorStatusChangedCallback,
                &documentEndedCallback,
                nullptr,
                nullptr,
                nullptr,
                (void *) token
        };
    }

    static bool replicationFilter(
            void *token,
            C4PeerSync *sync,
            const C4PeerID *peerId,
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

        jbyteArray _peerId = toJByteArray(env, peerId->bytes, 32);
        jstring _scope = toJString(env, coll.scope);
        jstring _name = toJString(env, coll.name);
        jstring _docID = toJString(env, docID);
        jstring _revID = toJString(env, revID);

        jboolean ok = env->CallStaticBooleanMethod(
                cls_MultipeerReplColl,
                m_MultipeerReplColl_filterCallback,
                (jlong) token,
                _peerId,
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

    static bool pushFilterCallback(
            C4PeerSync *sync,
            const C4PeerID *peerId,
            C4CollectionSpec coll,
            C4String docID,
            C4String revID,
            C4RevisionFlags flags,
            FLDict body,
            void *token) {
        return replicationFilter(token, sync, peerId, coll, docID, revID, flags, body, true);
    }

    static bool pullFilterCallback(
            C4PeerSync *sync,
            const C4PeerID *peerId,
            C4CollectionSpec coll,
            C4String docID,
            C4String revID,
            C4RevisionFlags flags,
            FLDict body,
            void *token) {
        return replicationFilter(token, sync, peerId, coll, docID, revID, flags, body, false);
    }
}

#ifdef __cplusplus
extern "C" {
#endif

//-------------------------------------------------------------------------
// com.couchbase.lite.internal.core.impl.NativeC4MultipeerReplicator
//-------------------------------------------------------------------------

/*
 * Class:     Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    create
 * Signature: (JLjava/lang/String;J[BJ[Lcom.couchbase.lite.internal.core.MultipeerCollectionConfiguration;[B)J
  */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_create(
        JNIEnv *env,
        jclass ignore,
        jlong token,
        jstring jgroupId,
        jlong keyPair,
        jbyteArray cert,
        jlong c4db,
        jobjectArray jCollDescs,
        jbyteArray joptions) {
    jstringSlice groupId(env, jgroupId);
    jbyteArraySlice options(env, joptions);

    C4PeerSyncParameters params{};

    // Peer Group ID:
    params.peerGroupID = groupId;

    // Protocols:
    C4String protocols[] = {kPeerSyncProtocol_DNS_SD};
    params.protocols = protocols;
    params.protocolsCount = sizeof(protocols) / sizeof(protocols[0]);

    // Identity:
    bool failed;
    params.tlsCert = toC4Cert(env, cert, failed);
    if (failed) {
        C4Error error = {LiteCoreDomain, kC4ErrorInvalidParameter};
        throwError(env, error);
        return 0;
    }
    params.tlsKeyPair = (keyPair == 0L) ? nullptr : (C4KeyPair *) keyPair;

    // Database and Collections:
    params.database = (C4Database *) c4db;

    std::vector<C4PeerSyncCollection> collectionDescs;
    std::vector<std::shared_ptr<jstringSlice>> collectionNames;
    std::vector<std::shared_ptr<jbyteArraySlice>> collectionOptions;
    int nColls = fromJavaReplColls(
            env,
            jCollDescs,
            collectionDescs,
            collectionNames,
            collectionOptions,
            true,
            true);


    if (nColls < 0) {
        C4Error error = {LiteCoreDomain, kC4ErrorInvalidParameter};
        throwError(env, error);
        return 0;
    }
    params.collectionCount = nColls;
    params.collections = collectionDescs.data();

    // Replicator Options:
    params.optionsDictFleece = options;

    // Callbacks:
    params.callbacks = getPeerSyncCallbacks((long) token);

    C4Error error{};
    C4PeerSync *mpRepl = c4peersync_new(&params, &error);
    if ((mpRepl == nullptr) && (error.code != 0)) {
        throwError(env, error);
        return 0;
    }

    return (jlong) mpRepl;
}

/*
 * Class:     Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    start
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_start(
        JNIEnv *env,
        jclass ignore,
        jlong peer) {
    c4peersync_start((C4PeerSync *) peer);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    stop
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_stop(
        JNIEnv *env,
        jclass ignore,
        jlong peer) {
    c4peersync_stop((C4PeerSync *) peer);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_free(
        JNIEnv *env,
        jclass ignore,
        jlong peer) {
    c4peersync_free((C4PeerSync *) peer);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    stop
 * Signature: (J)B[
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_getId(
        JNIEnv *env,
        jclass ignore,
        jlong peer) {
    C4PeerID peerId = c4peersync_getMyID((C4PeerSync *) peer);
    return toJByteArray(env, peerId.bytes, 32);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    stop
 * Signature: (J)B[[
 */
JNIEXPORT jobjectArray
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_getNeighborPeers(
        JNIEnv *env,
        jclass ignore,
        jlong peer) {
    size_t count = 0;
    C4PeerID *peers = c4peersync_getOnlinePeers((C4PeerSync *) peer, &count);

    jobjectArray neighborIds = fromC4PeerID(env, peers, count);

    free(peers);

    return neighborIds;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    stop
 * Signature: (J)Lcom/couchbase/lite/internal/core/C4PeerInfo;
 */
JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_getPeerInfo(
        JNIEnv *env,
        jclass ignore,
        jlong peer,
        jbyteArray jpeerId) {
    jbyteArraySlice peerIdArray(env, jpeerId);
    FLSlice peerIdSlice = peerIdArray;
    C4PeerID peerId;
    memcpy(peerId.bytes, peerIdSlice.buf, sizeof(peerId.bytes));

    C4PeerInfo *info = c4peersync_getPeerInfo((C4PeerSync *) peer, peerId);
    if(info == nullptr) {
        return nullptr;
    }

    jbyteArray certChain = nullptr;
    if(info->certificate) {
        C4SliceResult rawCertChain = c4cert_copyChainData(info->certificate);
        certChain = toJByteArray(env, rawCertChain);
        c4slice_free(rawCertChain);
    }

    jobject replStatus = toJavaReplStatus(env, info->replicatorStatus);
    jobjectArray neighborIds = fromC4PeerID(env, info->neighbors, info->neighborCount);

    jobject peerInfo = env->CallStaticObjectMethod(
            cls_C4MultipeerReplicator,
            m_C4MultipeerReplicator_createPeerInfo,
            jpeerId,
            certChain,
            info->online ? JNI_TRUE : JNI_FALSE,
            neighborIds,
            replStatus);

    c4peerinfo_free(info);

    return peerInfo;
}

JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_setProgressLevel(
        JNIEnv* env,
        jclass ignore,
        jlong peer,
        jint progressLevel) {
    c4peersync_setProgressLevel((C4PeerSync *)peer, (C4ReplicatorProgressLevel)progressLevel);
}

#ifdef __cplusplus
}
#endif
#endif
