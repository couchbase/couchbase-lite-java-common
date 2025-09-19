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

#include <vector>
#include <memory>
#include "native_glue.hh"
#include "c4Base.h"
#include "Defer.hh"
#include "com_couchbase_lite_internal_core_impl_NativeC4Listener.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4KeyPair.h"

using namespace litecore;
using namespace litecore::jni;

// Java ConnectionStatus class
static jclass cls_ConnectionStatus;                // global reference
static jmethodID m_ConnectionStatus_init;          // constructor

// Java C4Listener class
static jclass cls_C4Listener;                      // global reference
static jmethodID m_C4Listener_certAuthCallback;    // TLS authentication callback
static jmethodID m_C4Listener_httpAuthCallback;    // HTTP authentication callback

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
    jint envState = attachJVM(&env, "httpAuth");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return false;

    jstring _header = toJString(env, authHeader);
    jboolean ok = env->CallStaticBooleanMethod(cls_C4Listener, m_C4Listener_httpAuthCallback, (jlong) context, _header);
    if (envState == JNI_EDETACHED) {
        detachJVM("httpAuthCallback");
    } else {
        if (_header != nullptr) env->DeleteLocalRef(_header);
    }

    return ok != JNI_FALSE;
}

static bool certAuthCallback(C4Listener *ignore, C4Slice clientCertData, void *context) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "certAuth");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return false;

    jbyteArray _data = toJByteArray(env, clientCertData);
    jboolean ok = env->CallStaticBooleanMethod(cls_C4Listener, m_C4Listener_certAuthCallback, (jlong) context, _data);

    if (envState == JNI_EDETACHED) {
        detachJVM("certAuthCallback");
    } else {
        if (_data != nullptr) env->DeleteLocalRef(_data);
    }

    return ok != JNI_FALSE;
}

// The Java method returns a byte array of key data.
// This method copies the data to its destination.
static bool doKeyDataCallback(JNIEnv *env, void *extKey, size_t outMaxLen, void *output, size_t *outLen) {
    auto key = (jbyteArray) (env->CallStaticObjectMethod(
            cls_C4KeyPair,
            m_C4KeyPair_keyDataCallback,
            (jlong) extKey));
    if (key == nullptr) {
        C4Warn("doKeyDataCallback: Failed to get key data from Java");
        return false;
    }

    jsize keyDataSize = env->GetArrayLength(key);
    if (keyDataSize > outMaxLen) {
        C4Warn("doKeyDataCallback: data is too big");
        env->DeleteLocalRef(key);
        return false;
    }

    jbyte *keyData = env->GetByteArrayElements(key, nullptr);
    memcpy(output, keyData, keyDataSize);
    env->ReleaseByteArrayElements(key, keyData, 0);
    env->DeleteLocalRef(key);

    *outLen = keyDataSize;

    return true;
}

// See C4ExternalKeyCallbacks in C4Certificate.h
static bool publicKeyDataCallback(void *externalKey, void *output, size_t outputMaxLen, size_t *outputLen) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "publicKeyData");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return false;

    jboolean ok = doKeyDataCallback(env, externalKey, outputMaxLen, output, outputLen);

    if (envState == JNI_EDETACHED)
        detachJVM("publicKeyDataCallback");

    return ok != JNI_FALSE;
}

// The Java method takes a byte array of encrypted data and returns a byte array
// containing the decrypted data. This method creates the parameter byte array
// and then copies the result to its destination.
static bool doDecryptCallback(
        JNIEnv *env,
        void *extKey,
        C4Slice input,
        size_t outMaxLen,
        void *output,
        size_t *outLen) {
    assert(input.size < 16384);
    int n = (int) input.size;

    jbyteArray encData = env->NewByteArray(n);
    if (encData == nullptr) {
        C4Warn("doDecryptCallback: Failed to allocate byte array");
        return false;
    }
    env->SetByteArrayRegion(encData, 0, n, (jbyte *) input.buf);

    auto decData = (jbyteArray)
            (env->CallStaticObjectMethod(cls_C4KeyPair, m_C4KeyPair_decryptCallback, (jlong) extKey, encData));
    env->DeleteLocalRef(encData);
    if (decData == nullptr) {
        C4Warn("doDecryptCallback: Failed to get decrypted data from Java");
        return false;
    }

    jsize dataSize = env->GetArrayLength(decData);
    if (dataSize > outMaxLen) {
        C4Warn("doDecryptCallback: data is too big");
        env->DeleteLocalRef(decData);
        return false;
    }

    jbyte *data = env->GetByteArrayElements(decData, nullptr);
    memcpy(output, data, dataSize);
    env->ReleaseByteArrayElements(decData, data, 0);
    env->DeleteLocalRef(decData);

    *outLen = dataSize;

    return true;
}

// See C4ExternalKeyCallbacks in C4Certificate.h
static bool decryptKeyCallback(void *externalKey, C4Slice input, void *output, size_t outputMaxLen, size_t *outputLen) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "decryptKey");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return false;

    jboolean ok = doDecryptCallback(env, externalKey, input, outputMaxLen, output, outputLen);

    if (envState == JNI_EDETACHED)
        detachJVM("decryptKeyCallback");

    return ok != JNI_FALSE;
}

// The Java method takes a byte array of data and returns a byte array
// containing the signature. This method creates the parameter byte array
// and then copies the result to its destination.
static bool doSignCallback(JNIEnv *env, void *extKey, C4SignatureDigestAlgorithm alg, C4Slice inData, void *outSig) {
    assert(inData.size < 16384);
    int n = (int) inData.size;

    jbyteArray data = env->NewByteArray(n);
    if (data == nullptr) {
        C4Warn("doSignCallback: Failed to allocate byte array");
        return false;
    }
    env->SetByteArrayRegion(data, 0, n, (jbyte *) inData.buf);

    auto sig = (jbyteArray)
            (env->CallStaticObjectMethod(cls_C4KeyPair, m_C4KeyPair_signCallback, (jlong) extKey, (jint) alg, data));
    env->DeleteLocalRef(data);
    if (sig == nullptr) {
        C4Warn("doDecryptCallback: Failed to get signing data from Java");
        return false;
    }

    jsize sigSize = env->GetArrayLength(sig);
    // The signature is the same size as the key.
    // This check happens in Java

    jbyte *sigData = env->GetByteArrayElements(sig, nullptr);
    memcpy(outSig, sigData, sigSize);
    env->ReleaseByteArrayElements(sig, sigData, 0);
    env->DeleteLocalRef(sig);

    return true;
}

// See C4ExternalKeyCallbacks in C4Certificate.h
static bool signKeyCallback(
        void *externalKey,
        C4SignatureDigestAlgorithm digestAlgorithm,
        C4Slice inputData,
        void *outSignature) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "signKey");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return false;

    jboolean ok = doSignCallback(env, externalKey, digestAlgorithm, inputData, outSignature);
    if (envState == JNI_EDETACHED)
        detachJVM("signKey");

    return ok != JNI_FALSE;
}

// See C4ExternalKeyCallbacks in C4Certificate.h
static void freeKeyCallback(void *externalKey) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "freeKey");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    env->CallStaticVoidMethod(cls_C4KeyPair, m_C4KeyPair_freeCallback, (jlong) externalKey);

    if (envState == JNI_EDETACHED)
        detachJVM("freeKey");
}

//-------------------------------------------------------------------------
// Utility methods
//-------------------------------------------------------------------------

static bool initListenerCallbacks(JNIEnv *env) {
    {
        jclass localClass = env->FindClass("com/couchbase/lite/ConnectionStatus");
        if (localClass == nullptr)
            return false;

        cls_ConnectionStatus = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_ConnectionStatus == nullptr)
            return false;

        m_ConnectionStatus_init = env->GetMethodID(cls_ConnectionStatus, "<init>", "(II)V");
        if (m_ConnectionStatus_init == nullptr)
            return false;
    }

    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4Listener");
        if (localClass == nullptr)
            return false;

        cls_C4Listener = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_C4Listener == nullptr)
            return false;

        m_C4Listener_certAuthCallback = env->GetStaticMethodID(
                cls_C4Listener,
                "certAuthCallback",
                "(J[B)Z");

        if (m_C4Listener_certAuthCallback == nullptr)
            return false;

        m_C4Listener_httpAuthCallback = env->GetStaticMethodID(
                cls_C4Listener,
                "httpAuthCallback",
                "(JLjava/lang/String;)Z");

        if (m_C4Listener_httpAuthCallback == nullptr)
            return false;
    }

    jniLog("listener initialized");
    return true;
}

static bool initKeyPairCallbacks(JNIEnv *env) {
    jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4KeyPair");
    if (localClass == nullptr)
        return false;

    cls_C4KeyPair = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    if (cls_C4KeyPair == nullptr)
        return false;

    m_C4KeyPair_keyDataCallback = env->GetStaticMethodID(cls_C4KeyPair, "getKeyDataCallback", "(J)[B");
    if (m_C4KeyPair_keyDataCallback == nullptr)
        return false;

    m_C4KeyPair_signCallback = env->GetStaticMethodID(cls_C4KeyPair, "signCallback", "(JI[B)[B");
    if (m_C4KeyPair_signCallback == nullptr)
        return false;

    m_C4KeyPair_decryptCallback = env->GetStaticMethodID(cls_C4KeyPair, "decryptCallback", "(J[B)[B");
    if (m_C4KeyPair_decryptCallback == nullptr)
        return false;

    m_C4KeyPair_freeCallback = env->GetStaticMethodID(cls_C4KeyPair, "freeCallback", "(J)V");
    if (m_C4KeyPair_freeCallback == nullptr)
        return false;

    keyCallbacks.publicKeyData = &publicKeyDataCallback;
    keyCallbacks.decrypt = &decryptKeyCallback;
    keyCallbacks.sign = &signKeyCallback;
    keyCallbacks.free = &freeKeyCallback;

    jniLog("keypair initialized");
    return true;
}

static C4Listener *startListener(
        JNIEnv *env,
        jint port,
        jstring networkInterface,
        jlong context,
        jboolean allowPush,
        jboolean allowPull,
        jboolean enableDeltaSync,
        jboolean requirePasswordAuth,
        C4TLSConfig *tlsConfig) {
    jstringSlice iFace(env, networkInterface);

    C4ListenerConfig config{};
    config.port = (uint16_t) port;
    config.networkInterface = iFace;
    config.tlsConfig = tlsConfig;
    config.allowPush = allowPush;
    config.allowPull = allowPull;
    config.enableDeltaSync = enableDeltaSync;

    // !!! config.apis = kC4SyncAPI; // needed when using a 3.2 LiteCore

    if (requirePasswordAuth) {
        config.httpAuthCallback = &httpAuthCallback;
        config.callbackContext = (void *) context;
    }

    C4Error error{};
    C4Listener *listener = c4listener_start(&config, &error);
    if ((listener == nullptr) && (error.code != 0)) {
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

static C4KeyPair *createKeyPair(JNIEnv *env, jbyte algorithm, jint keySizeInBits, jlong context) {
    C4Error error{};
    C4KeyPair* keyPair = c4keypair_fromExternal(
            (C4KeyPairAlgorithm) algorithm,
            (size_t) keySizeInBits,
            (void *) context,
            keyCallbacks,
            &error);
    if (keyPair == nullptr) {
        throwError(env, error);
        return nullptr;
    }

    return keyPair;
}


extern "C" {

//-------------------------------------------------------------------------
// com.couchbase.lite.internal.core.impl.NativeC4Listener
//-------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    startHttp
 * Signature: (ILjava/lang/String;JZZZZ)J
  */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_startHttp(
        JNIEnv *env,
        jclass ignore,
        jint port,
        jstring networkInterface,
        jlong context,
        jboolean allowPush,
        jboolean allowPull,
        jboolean enableDeltaSync,
        jboolean requirePasswordAuth) {

    return reinterpret_cast<jlong>(startListener(
            env,
            port,
            networkInterface,
            context,
            allowPush,
            allowPull,
            enableDeltaSync,
            requirePasswordAuth,
            nullptr));
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    startTls
 * Signature: (ILjava/lang/String;JJ[BZ[BZZZZ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_startTls(
        JNIEnv *env,
        jclass ignore,
        jint port,
        jstring networkInterface,
        jlong context,
        jlong keyPair,
        jbyteArray cert,
        jboolean requireClientCerts,
        jbyteArray rootClientCerts,
        jboolean allowPush,
        jboolean allowPull,
        jboolean enableDeltaSync,
        jboolean requirePasswordAuth) {
    C4TLSConfig tlsConfig{};
    tlsConfig.privateKeyRepresentation = kC4PrivateKeyFromKey;
    tlsConfig.key = (C4KeyPair *) keyPair;

    bool failed;
    tlsConfig.certificate = toC4Cert(env, cert, failed);
    if (failed)
        return 0;

    // Client Cert Authentication:
    tlsConfig.requireClientCerts = requireClientCerts;
    if (requireClientCerts != JNI_FALSE) {
        if (rootClientCerts == nullptr) {
            tlsConfig.certAuthCallback = &certAuthCallback;
            tlsConfig.tlsCallbackContext = reinterpret_cast<void *>(context);
        } else {
            tlsConfig.rootClientCerts = toC4Cert(env, rootClientCerts, failed);
            if (failed) {
                c4cert_release(tlsConfig.certificate);
                return 0;
            }
        }
    }

    C4Listener *listener = startListener(
            env,
            port,
            networkInterface,
            context,
            allowPush,
            allowPull,
            enableDeltaSync,
            requirePasswordAuth,
            &tlsConfig);

    c4cert_release(tlsConfig.certificate);
    c4cert_release(tlsConfig.rootClientCerts);

    return (jlong) listener;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_free(
        JNIEnv *env,
        jclass ignore,
        jlong c4Listener) {
    c4listener_free(reinterpret_cast<C4Listener *>(c4Listener));
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    shareCollection
 * Signature: (JLjava/lang/String;J[J)V
 *
 *
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_shareDbCollections(
        JNIEnv *env,
        jclass ignore,
        jlong c4Listener,
        jstring dbName,
        jlong c4db,
        jlongArray c4Collections) {
    jstringSlice name(env, dbName);

    C4Error error1{};
    bool ok = c4listener_shareDB(
            reinterpret_cast<C4Listener *>(c4Listener),
            name,
            reinterpret_cast<C4Database *>(c4db),
            &error1);
    if (!ok && error1.code != 0) {
        throwError(env, error1);
        return;
    }

    jsize n = env->GetArrayLength(c4Collections);
    jlong *colls = env->GetLongArrayElements(c4Collections, nullptr);
    for (int i = 0; i < n; i++) {
        C4Error error2{};
        ok = c4listener_shareCollection(
                reinterpret_cast<C4Listener *>(c4Listener),
                name,
                reinterpret_cast<C4Collection *>(colls[i]),
                &error2);
        if (!ok && error2.code != 0) {
            throwError(env, error2);
            break;
        }
    }
    env->ReleaseLongArrayElements(c4Collections, colls, 0);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    getUrls
 * Signature: (JJ)Ljava/util/List;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getUrls(
        JNIEnv *env,
        jclass ignore,
        jlong c4Listener,
        jlong c4Database) {
    C4Error error{};
    FLMutableArray urls = c4listener_getURLs(
            reinterpret_cast<C4Listener *>(c4Listener),
            reinterpret_cast<C4Database *>(c4Database),
            // !!! kC4SyncAPI, // needed when using a 3.2 LiteCore
            &error);

    if (urls == nullptr) {
        throwError(env, error);
        return nullptr;
    }

    jobject urLList = toStringList(env, urls);

    FLMutableArray_Release(urls);

    return urLList;
}

JNIEXPORT jint JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getPort(
        JNIEnv *env,
        jclass ignore,
        jlong c4Listener) {
    return (jint) c4listener_getPort(reinterpret_cast<C4Listener *>(c4Listener));
}

JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getConnectionStatus(
        JNIEnv *env,
        jclass ignore,
        jlong c4Listener) {
    unsigned connections;
    unsigned activeConnections;

    c4listener_getConnectionStatus(reinterpret_cast<C4Listener *>(c4Listener), &connections, &activeConnections);

    return toConnectionStatus(env, connections, activeConnections);
}

JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getUriFromPath(
        JNIEnv *env,
        jclass ignore,
        jstring path) {
    jstringSlice pathSlice(env, path);
    FLSliceResult uri = c4db_URINameFromPath(pathSlice);
    jstring jstr = toJString(env, uri);
    FLSliceResult_Release(uri);
    return jstr;
}

//-------------------------------------------------------------------------
// com.couchbase.lite.internal.core.impl.NativeC4KeyPair
//-------------------------------------------------------------------------

JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_fromExternal(
        JNIEnv *env,
        jclass ignore,
        jbyte algorithm,
        jint keyBits,
        jlong context) {
    return (jlong) createKeyPair(env, algorithm, keyBits, context);
}

static jbyteArray generateCertificate(
        JNIEnv *env,
        jlong c4KeyPair,
        jbyteArray caKey,
        jbyteArray caCertificate,
        jobjectArray nameComponents,
        jbyte usage,
        jlong validityInSeconds) {
    auto keys = (C4KeyPair *) c4KeyPair;
    jbyteArraySlice caKeySlice(env, caKey);
    jbyteArraySlice caCertSlice(env, caCertificate);

    int size = env->GetArrayLength(nameComponents);
    std::vector<C4CertNameComponent> subjectNames;
    subjectNames.reserve(size);

    // For retaining jstringSlice objects of cert's attributes and values:
    std::vector<std::unique_ptr<jstringSlice>> attrs;
    for (int i = 0; i < size; ++i) {
        auto component = (jobjectArray) env->GetObjectArrayElement(nameComponents, i);
        if (component == nullptr) continue;

        auto key = (jstring) env->GetObjectArrayElement(component, 0);
        attrs.emplace_back(std::make_unique<jstringSlice>(env, key));
        auto* keySlice = attrs[attrs.size() - 1].get();
        if (key != nullptr) env->DeleteLocalRef(key);

        auto value = (jstring) env->GetObjectArrayElement(component, 1);
        attrs.emplace_back(std::make_unique<jstringSlice>(env, value));
        auto* valueSlice = attrs[attrs.size() - 1].get();
        if (value != nullptr) env->DeleteLocalRef(value);

        env->DeleteLocalRef(component);

        subjectNames.push_back(C4CertNameComponent { *keySlice, *valueSlice });
    }

    C4Error error{};
    C4Cert *csr = c4cert_createRequest(subjectNames.data(), subjectNames.size(), usage, keys, &error);
    DEFER { c4cert_release(csr); };

    if (csr == nullptr) {
        throwError(env, error);
        return nullptr;
    }

    C4CertIssuerParameters issuerParams = kDefaultCertIssuerParameters;
    if (validityInSeconds > 0)
        issuerParams.validityInSeconds = validityInSeconds;

    C4KeyPair* issuerKey = keys;
    C4Cert* issuerCert = nullptr;
    if(caKey != nullptr && caCertificate != nullptr) {
        issuerKey = c4keypair_fromPrivateKeyData(caKeySlice, kC4SliceNull, &error);
        if(!issuerKey) {
            throwError(env, error);
            return nullptr;
        }

        issuerCert = c4cert_fromData(caCertSlice, &error);
        if(!issuerCert) {
            throwError(env, error);
            return nullptr;
        }
    }

    C4Cert *cert = c4cert_signRequest(csr, &issuerParams, issuerKey, issuerCert, &error);
    DEFER {
        // Release issuerKey only if it's not the `c4KeyPair` passed to this function.
        if (issuerKey != keys) {
            c4keypair_release(issuerKey);
        }
        c4cert_release(issuerCert);
        c4cert_release(cert);
    };
    
    if (cert == nullptr) {
        throwError(env, error);
        return nullptr;
    }

    jbyteArray certData = fromC4Cert(env, cert);
    return certData;
}

JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_generateSelfSignedCertificate(
        JNIEnv *env,
        jclass ignore,
        jlong c4KeyPair,
        jbyte algorithm,
        jint keyBits,
        jobjectArray nameComponents,
        jbyte usage,
        jlong validityInSeconds) {
    return generateCertificate(env, c4KeyPair, nullptr, nullptr, nameComponents, usage, validityInSeconds);
}

JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_generateCASignedCertificate(
        JNIEnv *env,
        jclass ignore,
        jlong c4KeyPair,
        jbyteArray caKey,
        jbyteArray caCertificate,
        jobjectArray nameComponents,
        jbyte usage,
        jlong validityInSeconds) {
    return generateCertificate(env, c4KeyPair, caKey, caCertificate, nameComponents, usage, validityInSeconds);
}

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_free(JNIEnv *env, jclass ignore, jlong hdl) {
    c4keypair_release((C4KeyPair *) hdl);
}
}

#endif
