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
#include "com_couchbase_lite_internal_core_C4Listener.h"
#include "native_glue.hh"
#include "c4Listener.h"

using namespace litecore;
using namespace litecore::jni;

// C4Listener
static jclass cls_C4Listener;                      // global reference
static jmethodID m_C4Listener_certAuthCallback;    // statusChangedCallback method
static jmethodID m_C4Listener_httpAuthCallback;    // documentEndedCallback method

// ConnectionStatus
static jclass cls_ConnectionStatus;          // global reference
static jmethodID m_ConnectionStatus_init;    // constructor

bool litecore::jni::initC4Listener(JNIEnv *env) {
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
                "(JLjava/lang/Object;J)Z");

        if (!m_C4Listener_certAuthCallback)
            return false;

        m_C4Listener_httpAuthCallback = env->GetStaticMethodID(
                cls_C4Listener,
                "httpAuthCallback",
                "(JLjava/lang/Object;J)Z");

        if (!m_C4Listener_httpAuthCallback)
            return false;
    }

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
    return true;
}

static bool certAuthCallback(C4Listener *listener, C4Slice clientCertData, void *context) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    bool res = false;
    if (getEnvStat == JNI_OK) {
        res = env->CallStaticBooleanMethod(cls_C4Listener,
                                           m_C4Listener_certAuthCallback,
                                           (jlong) listener,
                                           NULL,
                                           (jlong) context);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            res = env->CallStaticBooleanMethod(cls_C4Listener,
                                               m_C4Listener_certAuthCallback,
                                               (jlong) listener,
                                               NULL,
                                               (jlong) context);
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

static bool httpAuthCallback(C4Listener *listener, C4Slice authHeader, void *context) {
    JNIEnv *env = NULL;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    bool res = false;
    if (getEnvStat == JNI_OK) {
        res = env->CallStaticBooleanMethod(cls_C4Listener,
                                           m_C4Listener_httpAuthCallback,
                                           (jlong) listener,
                                           NULL,
                                           reinterpret_cast<jlong>(context));
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            res = env->CallStaticBooleanMethod(cls_C4Listener,
                                               m_C4Listener_httpAuthCallback,
                                               (jlong) listener,
                                               NULL,
                                               reinterpret_cast<jlong>(context));
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

JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4Listener_startHttp(
        JNIEnv *env,
        jclass clazz,
        jint port,
        jstring networkInterface,
        int apis,
        jlong context,
        jstring dbPath,
        jboolean allowCreateDBs,
        jboolean allowDeleteDBs) {

    FLSlice hack; // Temporary cookie for the compiler...

    C4Error error;

    C4ListenerConfig config;
    config.port = port;
    config.networkInterface = hack; //networkInterface;
    config.apis = apis;
    config.tlsConfig = nullptr;
    config.httpAuthCallback = httpAuthCallback;
    config.callbackContext = (void *) context;
    config.directory = hack; // dbPath;
    config.allowCreateDBs = allowCreateDBs;
    config.allowDeleteDBs = allowDeleteDBs;

    auto listener = c4listener_start(&config, &error);
    if (!listener) {
        throwError(env, error);
        return 0;
    }

    return reinterpret_cast<jlong>(listener);
}

JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_C4Listener_free(JNIEnv *env, jclass clazz, jlong c4Listener) {
    c4listener_free(reinterpret_cast<C4Listener *>(c4Listener));
}

JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_C4Listener_shareDb
        (JNIEnv *env, jclass clazz, jlong c4Listener, jstring dbName, jlong c4Database) {
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
JNICALL Java_com_couchbase_lite_internal_core_C4Listener_unshareDb
        (JNIEnv *env, jclass clazz, jlong c4Listener, jlong c4Database) {

    C4Error error;

    if (!c4listener_unshareDB(
            reinterpret_cast<C4Listener *>(c4Listener),
            reinterpret_cast<C4Database *>(c4Database),
            &error)) {
        throwError(env, error);
    }
}

JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4Listener_getUrls
        (JNIEnv *env, jclass clazz, jlong c4Listener, jlong c4Database, jint api) {
    C4Error error;

    auto urls = c4listener_getURLs(
            reinterpret_cast<C4Listener *>(c4Listener),
            reinterpret_cast<C4Database *>(c4Database),
            api,
            &error);

    if (!urls) {
        throwError(env, error);
        return 0;
    }

    // do something sensible with the return

    return 0;
}

JNIEXPORT jint
JNICALL Java_com_couchbase_lite_internal_core_C4Listener_getPort
        (JNIEnv *env, jclass clazz, jlong c4Listener) {
    return (jint) c4listener_getPort(reinterpret_cast<C4Listener *>(c4Listener));
}

JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_C4Listener_getConnectionStatus
        (JNIEnv *env, jclass clazz, jlong c4Listener) {

    unsigned connections;
    unsigned activeConnections;

    c4listener_getConnectionStatus(reinterpret_cast<C4Listener *>(c4Listener), &connections, &activeConnections);

    return toConnectionStatus(env, connections, activeConnections);
}

JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_C4Listener_getUriFromPath
        (JNIEnv *env, jclass clazz, jlong c4Listener, jstring path) {
    jstringSlice pathSlice(env, path);
    auto uri = c4db_URINameFromPath(pathSlice);
    jstring jstr = toJString(env, uri);
    c4slice_free(uri);
    return jstr;
}
