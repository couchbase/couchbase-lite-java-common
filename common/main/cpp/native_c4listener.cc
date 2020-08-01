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

#include "c4Listener.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4Listener.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4KeyPair.h"
#include "native_glue.hh"

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

// Java KeyManager class
static jclass cls_C4KeyPair;                      // global reference
static jmethodID m_C4KeyPair_keyDataCallback;     // get key data
static jmethodID m_C4KeyPair_decryptCallback;     // decrypt using key
static jmethodID m_C4KeyPair_signCallback;        // sign using key
static jmethodID m_C4KeyPair_freeCallback;        // free key
static C4ExternalKeyCallbacks keyCallbacks;

// Initialization
static bool initListenerCallbacks(JNIEnv *env);

static bool initKeyPairCallbacks(JNIEnv *env);

//-------------------------------------------------------------------------
// Package initialization
// ???  This is stuff that is not necessarily going to be used.
//      Perhaps we should lazily find callback methods
//      and explicitly release them, to minimize GlobalRefs?
//-------------------------------------------------------------------------

bool litecore::jni::initC4Listener(JNIEnv *env) {
    return initListenerCallbacks(env) && initKeyPairCallbacks(env);
}

//-------------------------------------------------------------------------
// Callback handlers
//-------------------------------------------------------------------------

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

// The Java method returns a byte array of key data.
// This method copies the data to its destination.
static bool
doKeyDataCallback(JNIEnv *env, void *externalKey, void *output, size_t outputMaxLen, size_t *outputLen) {
    auto key = (jbyteArray) (env->CallStaticObjectMethod(cls_C4KeyPair,
                                                         m_C4KeyPair_keyDataCallback,
                                                         (jlong) externalKey));
    if (!key)
        return false;

    jsize keyDataSize = env->GetArrayLength(key);

    if (keyDataSize > outputMaxLen)
        return false;

    jbyte *keyData = env->GetByteArrayElements(key, nullptr);
    memcpy(output, keyData, keyDataSize);
    env->ReleaseByteArrayElements(key, keyData, 0);

    *outputLen = keyDataSize;

    return true;
}

// See C4ExternalKeyCallbacks in C4Certificate.h
static bool publicKeyDataKeyCallback(void *externalKey, void *output, size_t outputMaxLen, size_t *outputLen) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    bool res = false;
    if (getEnvStat == JNI_OK) {
        res = doKeyDataCallback(env, externalKey, output, outputMaxLen, outputLen);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            res = doKeyDataCallback(env, externalKey, output, outputMaxLen, outputLen);
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

// The Java method takes a byte array of encrypted data and returns a byte array
// containing the decrypted data. This method creates the parameter byte array
// and then copies the result to its destination.
static bool
doDecryptCallback(JNIEnv *env, void *externalKey, C4Slice input, void *output, size_t outputMaxLen, size_t *outputLen) {
    jbyteArray encryptedData = env->NewByteArray(input.size);
    env->SetByteArrayRegion(encryptedData, 0, input.size, (jbyte *) input.buf);

    auto decryptedData = (jbyteArray) (env->CallStaticObjectMethod(cls_C4KeyPair,
                                                                   m_C4KeyPair_decryptCallback,
                                                                   (jlong) externalKey,
                                                                   encryptedData));
    if (!decryptedData)
        return false;

    jsize dataSize = env->GetArrayLength(decryptedData);
    if (dataSize > outputMaxLen)
        return false;

    jbyte *data = env->GetByteArrayElements(decryptedData, nullptr);
    memcpy(output, data, dataSize);
    env->ReleaseByteArrayElements(decryptedData, data, 0);

    *outputLen = dataSize;

    return true;
}

// See C4ExternalKeyCallbacks in C4Certificate.h
static bool decryptKeyCallback(void *externalKey, C4Slice input, void *output, size_t outputMaxLen, size_t *outputLen) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    bool res = false;
    if (getEnvStat == JNI_OK) {
        res = doDecryptCallback(env, externalKey, input, output, outputMaxLen, outputLen);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            res = doDecryptCallback(env, externalKey, input, output, outputMaxLen, outputLen);
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

// The Java method takes a byte array of data and returns a byte array
// containing the signature. This method creates the parameter byte array
// and then copies the result to its destination.
static bool
doSignCallback(JNIEnv *env,
               void *externalKey,
               C4SignatureDigestAlgorithm digestAlgorithm,
               C4Slice inputData,
               void *outSignature) {
    jbyteArray data = env->NewByteArray(inputData.size);
    env->SetByteArrayRegion(data, 0, inputData.size, (jbyte *) inputData.buf);

    auto signature = (jbyteArray) (env->CallStaticObjectMethod(cls_C4KeyPair,
                                                               m_C4KeyPair_signCallback,
                                                               (jlong) externalKey,
                                                               (int) digestAlgorithm,
                                                               data));
    if (!signature)
        return false;

    jsize sigSize = env->GetArrayLength(signature);

    jbyte *sigData = env->GetByteArrayElements(signature, nullptr);
    memcpy(outSignature, sigData, sigSize);
    env->ReleaseByteArrayElements(signature, sigData, 0);

    return true;
}

// See C4ExternalKeyCallbacks in C4Certificate.h
static bool signKeyCallback(
        void *externalKey,
        C4SignatureDigestAlgorithm digestAlgorithm,
        C4Slice inputData,
        void *outSignature) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    bool res = false;
    if (getEnvStat == JNI_OK) {
        res = doSignCallback(env, externalKey, digestAlgorithm, inputData, outSignature);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            res = doSignCallback(env, externalKey, digestAlgorithm, inputData, outSignature);
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

// See C4ExternalKeyCallbacks in C4Certificate.h
static void freeKeyCallback(void *externalKey) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_OK) {
        env->CallStaticVoidMethod(cls_C4KeyPair,
                                  m_C4KeyPair_freeCallback,
                                  (jlong) externalKey);
    } else if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) == 0) {
            env->CallStaticVoidMethod(cls_C4KeyPair,
                                      m_C4KeyPair_freeCallback,
                                      (jlong) externalKey);
            if (gJVM->DetachCurrentThread() != 0)
                C4Warn("doRequestClose(): Failed to detach the current thread from a Java VM");
        } else {
            C4Warn("doRequestClose(): Failed to attaches the current thread to a Java VM");
        }
    } else {
        C4Warn("doClose(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
    }
}

//-------------------------------------------------------------------------
// Utility methods
//-------------------------------------------------------------------------

static bool initListenerCallbacks(JNIEnv *env) {
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

static bool initKeyPairCallbacks(JNIEnv *env) {
    jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4KeyPair");
    if (!localClass)
        return false;

    cls_C4KeyPair = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    if (!cls_C4KeyPair)
        return false;

    m_C4KeyPair_keyDataCallback = env->GetStaticMethodID(cls_C4KeyPair, "getKeyDataCallback", "(J)[B");
    if (!m_C4KeyPair_keyDataCallback)
        return false;

    m_C4KeyPair_signCallback = env->GetStaticMethodID(cls_C4KeyPair, "signCallback", "(JI[B)[B");
    if (!m_C4KeyPair_signCallback)
        return false;

    m_C4KeyPair_decryptCallback = env->GetStaticMethodID(cls_C4KeyPair, "decryptCallback", "(J[B)[B");
    if (!m_C4KeyPair_decryptCallback)
        return false;

    m_C4KeyPair_freeCallback = env->GetStaticMethodID(cls_C4KeyPair, "freeCallback", "(J)V");
    if (!m_C4KeyPair_freeCallback)
        return false;

    keyCallbacks.publicKeyData = &publicKeyDataKeyCallback;
    keyCallbacks.decrypt = &decryptKeyCallback;
    keyCallbacks.sign = &signKeyCallback;
    keyCallbacks.free = &freeKeyCallback;

    return true;
}

static C4Listener *startListener(
        JNIEnv *env,
        jint port,
        jstring networkInterface,
        jint apis,
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
    config.port = (uint16_t) port;
    config.networkInterface = iFace;
    config.apis = (unsigned) apis;
    config.tlsConfig = tlsConfig;
    config.httpAuthCallback = &httpAuthCallback;
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
        auto arrayElem = FLArray_Get(array, (uint32_t) i);
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
    if (!cert)
        return nullptr;

    jsize certSize = env->GetArrayLength(cert);
    if (certSize <= 0)
        return nullptr;

    jbyte *certData = env->GetByteArrayElements(cert, nullptr);
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

static jbyteArray getCertData(JNIEnv *env, C4Cert *cert) {
    if (!cert)
        return nullptr;

    auto certData = c4cert_copyData(cert, false);
    jbyteArray jData = toJByteArray(env, certData);
    c4slice_free(certData);

    return jData;
}

static C4KeyPair *createKeyPair(JNIEnv *env, jbyte algorithm, jint keySizeInBits, jlong context) {
    C4Error error;

    auto keyPair = c4keypair_fromExternal((C4KeyPairAlgorithm) algorithm,
                                          (size_t) keySizeInBits,
                                          (void *) context,
                                          keyCallbacks,
                                          &error);
    if (!keyPair) {
        litecore::jni::throwError(env, error);
        return nullptr;
    }

    return keyPair;
}


extern "C" {

//-------------------------------------------------------------------------
// com.couchbase.lite.internal.core.impl.NativeC4Listener
//-------------------------------------------------------------------------

JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_startHttp(
        JNIEnv *env,
        jclass ignore,
        jint port,
        jstring networkInterface,
        jint apis,
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
            apis,
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
        jint apis,
        jlong context,
        jlong keyPair,
        jbyteArray cert,
        jboolean requireClientCerts,
        jbyteArray rootClientCerts,
        jstring dbPath,
        jboolean allowCreateDBs,
        jboolean allowDeleteDBs,
        jboolean allowPush,
        jboolean allowPull,
        jboolean enableDeltaSync) {
    C4TLSConfig tlsConfig = {};
    tlsConfig.privateKeyRepresentation = kC4PrivateKeyFromKey;
    tlsConfig.key = (C4KeyPair *) keyPair;
    tlsConfig.certificate = getCert(env, cert);

    // Client Cert Authentication:
    tlsConfig.requireClientCerts = requireClientCerts;
    if (requireClientCerts == true) {
        if (rootClientCerts != NULL) {
            tlsConfig.rootClientCerts = getCert(env, rootClientCerts);
        } else {
            tlsConfig.certAuthCallback = &certAuthCallback;
            tlsConfig.tlsCallbackContext = reinterpret_cast<void *>(context);
        }
    }

    return reinterpret_cast<jlong>(startListener(
            env,
            port,
            networkInterface,
            apis,
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
        (JNIEnv *env, jclass ignore, jstring path) {
    jstringSlice pathSlice(env, path);
    auto uri = c4db_URINameFromPath(pathSlice);
    jstring jstr = toJString(env, uri);
    c4slice_free(uri);
    return jstr;
}

//-------------------------------------------------------------------------
// com.couchbase.lite.internal.core.impl.NativeC4KeyPair
//-------------------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_fromExternal
        (JNIEnv *env, jclass ignore, jbyte algorithm, jint keyBits, jlong context) {
    return (jlong) createKeyPair(env, algorithm, keyBits, context);
}

JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_generateSelfSignedCertificate
        (JNIEnv *env, jclass ignore,
         jlong c4KeyPair,
         jbyte algorithm,
         jint keyBits,
         jobjectArray nameComponents,
         jbyte usage,
         jlong validityInSeconds) {
    auto keys = (C4KeyPair *) c4KeyPair;

    int size = env->GetArrayLength(nameComponents);
    auto subjectName = new C4CertNameComponent[size];

    // For retaining jstringSlice objects of cert's attributes and values:
    std::vector<jstringSlice *> attrs;
    for (int i = 0; i < size; ++i) {
        auto component = (jobjectArray) env->GetObjectArrayElement(nameComponents, i);
        auto key = (jstring) env->GetObjectArrayElement(component, 0);
        auto value = (jstring) env->GetObjectArrayElement(component, 1);

        auto keySlice = new jstringSlice(env, key);
        auto valueSlice = new jstringSlice(env, value);

        attrs.push_back(keySlice);
        attrs.push_back(valueSlice);

        subjectName[i] = {*keySlice, *valueSlice};
    }

    C4Error error;
    auto csr = c4cert_createRequest(subjectName, size, usage, keys, &error);
    delete[] subjectName;
    if (!csr) {
        throwError(env, error);
        return nullptr;
    }

    // Release cert's attributes and values:
    for (int i = 0; i < attrs.size(); i++) {
        delete attrs.at(i);
    }

    C4CertIssuerParameters issuerParams = kDefaultCertIssuerParameters;
    if (validityInSeconds > 0) {
        issuerParams.validityInSeconds = validityInSeconds;
    }

    auto cert = c4cert_signRequest(csr, &issuerParams, keys, nullptr, &error);
    c4cert_release(csr);
    if (!cert) {
        throwError(env, error);
        return nullptr;
    }

    auto certData = getCertData(env, cert);
    c4cert_release(cert);

    return certData;
}

JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_free
        (JNIEnv *env, jclass ignore, jlong hdl) {
    c4keypair_release((C4KeyPair *) hdl);
}
}

#endif
