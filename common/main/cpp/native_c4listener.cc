//
// native_c4listener.cc
//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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
#ifdef COUCHBASE_ENTERPRISE

#include "com_couchbase_lite_internal_core_impl_NativeC4Listener.h"
#include "native_glue.hh"
#include "c4Listener.h"

using namespace litecore;
using namespace litecore::jni;

// Java ConnectionStatus class
static jclass cls_ConnectionStatus;                // global reference
static jmethodID m_ConnectionStatus_init;          // constructor

// Java C4Listener class
static jclass cls_C4Listener;                      // global reference
static jmethodID m_C4Listener_certAuthCallback;    // statusChangedCallback method
static jmethodID m_C4Listener_httpAuthCallback;    // documentEndedCallback method

// Java ArrayList class
static jclass cls_ArrayList;                       // global reference
static jmethodID m_ArrayList_init;                 // constructor
static jmethodID m_ArrayList_add;                  // add

bool litecore::jni::initC4Listener(JNIEnv *env) {
    {
        jclass localClass = env->FindClass("com/couchbase/lite/ConnectionStatus");
        if (!localClass)
            return false;

        cls_ConnectionStatus = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_ConnectionStatus)
            return false;

        m_ConnectionStatus_init = env->GetMethodID(cls_ConnectionStatus, "<init>", "(II)V");
        if (!m_ConnectionStatus_init)
            return false;
    }

    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4Listener");
        if (!localClass)
            return false;

        cls_C4Listener = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_C4Listener)
            return false;

        m_C4Listener_certAuthCallback = env->GetStaticMethodID(
                cls_C4Listener,
                "certAuthCallback",
                "(J[B)Z");

        if (!m_C4Listener_certAuthCallback)
            return false;

        m_C4Listener_httpAuthCallback = env->GetStaticMethodID(
                cls_C4Listener,
                "httpAuthCallback",
                "(JLjava/lang/String;)Z");

        if (!m_C4Listener_httpAuthCallback)
            return false;
    }

    {
        jclass localClass = env->FindClass("java/util/ArrayList");
        if (!localClass)
            return false;

        cls_ArrayList = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (!cls_ArrayList)
            return false;

        m_ArrayList_init = env->GetMethodID(cls_ArrayList, "<init>", "(I)V");
        if (!m_ArrayList_init)
            return false;

        m_ArrayList_add = env->GetMethodID(cls_ArrayList, "add", "(Ljava/lang/Object;)Z");
        if (!m_ArrayList_add)
            return false;
    }

    return true;
}

static bool httpAuthCallback(C4Listener *ignore, C4Slice authHeader, void *context) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    bool res = false;
    if (getEnvStat == JNI_OK) {
        res = env->CallStaticBooleanMethod(cls_C4Listener,
                                           m_C4Listener_httpAuthCallback,
                                           (jlong) context,
                                           toJString(env, authHeader));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            res = env->CallStaticBooleanMethod(cls_C4Listener,
                                               m_C4Listener_httpAuthCallback,
                                               (jlong) context,
                                               toJString(env, authHeader));
            if (gJVM->DetachCurrentThread() != 0)
                C4Warn("doRequestClose(): Failed to detach the current thread from a Java VM");
        } else {
            C4Warn("doRequestClose(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        C4Warn("doClose(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
    return res;
}

static bool certAuthCallback(C4Listener *ignore, C4Slice clientCertData, void *context) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    bool res = false;
    if (getEnvStat == JNI_OK) {
        res = env->CallStaticBooleanMethod(cls_C4Listener,
                                           m_C4Listener_certAuthCallback,
                                           (jlong) context,
                                           toJByteArray(env, clientCertData));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            res = env->CallStaticBooleanMethod(cls_C4Listener,
                                               m_C4Listener_certAuthCallback,
                                               (jlong) context,
                                               toJByteArray(env, clientCertData));
            if (gJVM->DetachCurrentThread() != 0)
                C4Warn("doRequestClose(): Failed to detach the current thread from a Java VM");
        } else {
            C4Warn("doRequestClose(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        C4Warn("doClose(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
    return res;
}

static jobject toConnectionStatus(JNIEnv *env, unsigned connectionCount, unsigned activeConnectionCount) {
    return env->NewObject(
            cls_ConnectionStatus,
            m_ConnectionStatus_init,
            (jint) connectionCount,
            (jint) activeConnectionCount);
}

static jobject toList(JNIEnv *env, FLMutableArray array) {
    int n = FLArray_Count(array);

    jobject result = env->NewObject(cls_ArrayList, m_ArrayList_init, (jint) n);

    for (int i = 0; i < n; i++) {
        auto arrayElem = FLArray_Get(array, i);
        auto str = FLValue_AsString((FLValue) arrayElem);
        jstring jstr = toJString(env, str);
        if (!jstr)
            continue;

        env->CallBooleanMethod(result, m_ArrayList_add, jstr);

        env->DeleteLocalRef(jstr);
    }

    return result;
}

static C4Cert *getCert(JNIEnv *env, jbyteArray cert) {
    jbyte *certData = env->GetByteArrayElements(cert, nullptr);
    size_t certSize = env->GetArrayLength(cert);
    FLSlice certSlice = {certData, (size_t) certSize};

    C4Error error;
    auto c4cert = c4cert_fromData(certSlice, &error);

    env->ReleaseByteArrayElements(cert, certData, 0);

    if (!c4cert) {
        throwError(env, error);
        return nullptr;
    }

    return c4cert;
}

static C4Listener *startListener(
        JNIEnv *env,
        jint port,
        jstring networkInterface,
        jlong context,
        jstring dbPath,
        jboolean allowCreateDBs,
        jboolean allowDeleteDBs,
        jboolean allowPush,
        jboolean allowPull,
        jboolean enableDeltaSync,
        C4TLSConfig *tlsConfig) {

    jstringSlice iFace(env, networkInterface);
    jstringSlice path(env, dbPath);

    C4ListenerConfig config;
    config.port = port;
    config.networkInterface = iFace;
    config.apis = kC4SyncAPI; // forced
    config.tlsConfig = tlsConfig;
    config.httpAuthCallback = httpAuthCallback;
    config.callbackContext = (void *) context;
    config.directory = path;
    config.allowCreateDBs = allowCreateDBs;
    config.allowDeleteDBs = allowDeleteDBs;
    config.allowPush = allowPush;
    config.allowPull = allowPull;
    config.enableDeltaSync = enableDeltaSync;

    C4Error error;

    auto listener = c4listener_start(&config, &error);
    if (!listener) {
        throwError(env, error);
        return nullptr;
    }

    return listener;
}

JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_startHttp(
        JNIEnv *env,
        jclass ignore,
        jint port,
        jstring networkInterface,
        jlong context,
        jstring dbPath,
        jboolean allowCreateDBs,
        jboolean allowDeleteDBs,
        jboolean allowPush,
        jboolean allowPull,
        jboolean enableDeltaSync) {
    return reinterpret_cast<jlong>(startListener(
            env,
            port,
            networkInterface,
            context,
            dbPath,
            allowCreateDBs,
            allowDeleteDBs,
            allowPush,
            allowPull,
            enableDeltaSync,
            nullptr));
}

JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_startTls(
        JNIEnv *env,
        jclass ignore,
        jint port,
        jstring networkInterface,
        jlong context,
        jbyteArray cert,
        jboolean requireClientCerts,
        jbyteArray rootClientCerts,
        jstring dbPath,
        jboolean allowCreateDBs,
        jboolean allowDeleteDBs,
        jboolean allowPush,
        jboolean allowPull,
        jboolean enableDeltaSync) {

    auto c4Cert = getCert(env, cert);
    if (!c4Cert)
        return 0;

    auto c4RootCerts = getCert(env, rootClientCerts);
    if (!c4RootCerts)
        return 0;

    C4TLSConfig tlsConfig;
    tlsConfig.privateKeyRepresentation = kC4PrivateKeyFromCert; // forced
    tlsConfig.key = nullptr;
    tlsConfig.certificate = c4Cert;
    tlsConfig.requireClientCerts = requireClientCerts;
    tlsConfig.rootClientCerts = c4RootCerts;
    tlsConfig.certAuthCallback = certAuthCallback;
    tlsConfig.tlsCallbackContext = reinterpret_cast<void *>(context);

    return reinterpret_cast<jlong>(startListener(
            env,
            port,
            networkInterface,
            context,
            dbPath,
            allowCreateDBs,
            allowDeleteDBs,
            allowPush,
            allowPull,
            enableDeltaSync,
            &tlsConfig));
}

JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_free
        (JNIEnv *env, jclass ignore, jlong c4Listener) {
    c4listener_free(reinterpret_cast<C4Listener *>(c4Listener));
}

JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_shareDb
        (JNIEnv *env, jclass ignore, jlong c4Listener, jstring dbName, jlong c4Database) {
    jstringSlice name(env, dbName);

    C4Error error;

    if (!c4listener_shareDB(
            reinterpret_cast<C4Listener *>(c4Listener),
            name,
            reinterpret_cast<C4Database *>(c4Database),
            &error)) {
        throwError(env, error);
    }
}

JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_unshareDb
        (JNIEnv *env, jclass ignore, jlong c4Listener, jlong c4Database) {

    C4Error error;

    if (!c4listener_unshareDB(
            reinterpret_cast<C4Listener *>(c4Listener),
            reinterpret_cast<C4Database *>(c4Database),
            &error)) {
        throwError(env, error);
    }
}

JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getUrls
        (JNIEnv *env, jclass ignore, jlong c4Listener, jlong c4Database) {

    C4Error error;

    auto urls = c4listener_getURLs(
            reinterpret_cast<C4Listener *>(c4Listener),
            reinterpret_cast<C4Database *>(c4Database),
            kC4SyncAPI, // forced
            &error);

    if (!urls) {
        throwError(env, error);
        return nullptr;
    }

    return toList(env, urls);
}

JNIEXPORT jint
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getPort
        (JNIEnv *env, jclass ignore, jlong c4Listener) {
    return (jint) c4listener_getPort(reinterpret_cast<C4Listener *>(c4Listener));
}

JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getConnectionStatus
        (JNIEnv *env, jclass ignore, jlong c4Listener) {

    unsigned connections;
    unsigned activeConnections;

    c4listener_getConnectionStatus(reinterpret_cast<C4Listener *>(c4Listener), &connections, &activeConnections);

    return toConnectionStatus(env, connections, activeConnections);
}

JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getUriFromPath
        (JNIEnv *env, jclass ignore, jlong c4Listener, jstring path) {
    jstringSlice pathSlice(env, path);
    auto uri = c4db_URINameFromPath(pathSlice);
    jstring jstr = toJString(env, uri);
    c4slice_free(uri);
    return jstr;
}

#endif
