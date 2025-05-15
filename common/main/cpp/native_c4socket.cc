//
// native_c4socket.cc
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

#include "c4Base.h"
#include "native_glue.hh"
#include "socket_factory.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4Socket.h"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// Callback method IDs to C4Socket
// ----------------------------------------------------------------------------
// C4Socket
static jclass cls_C4Socket;                   // global reference to C4Socket
static jmethodID m_C4Socket_open;             // callback method for C4Socket.open(...)
static jmethodID m_C4Socket_write;            // callback method for C4Socket.write(...)
static jmethodID m_C4Socket_completedReceive; // callback method for C4Socket.completedReceive(...)
static jmethodID m_C4Socket_requestClose;     // callback method for C4Socket.requestClose(...)
static jmethodID m_C4Socket_close;            // callback method for C4Socket.close(...)

bool litecore::jni::initC4Socket(JNIEnv *env) {
    // Find C4Socket class and static methods for callback
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4Socket");
        if (localClass == nullptr)
            return false;

        cls_C4Socket = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_C4Socket == nullptr)
            return false;

        m_C4Socket_open = env->GetStaticMethodID(
                cls_C4Socket,
                "open",
                "(JJLjava/lang/String;Ljava/lang/String;ILjava/lang/String;[B)V");
        if (m_C4Socket_open == nullptr)
            return false;

        m_C4Socket_write = env->GetStaticMethodID(cls_C4Socket, "write", "(J[B)V");
        if (m_C4Socket_write == nullptr)
            return false;

        m_C4Socket_completedReceive = env->GetStaticMethodID(cls_C4Socket, "completedReceive", "(JJ)V");
        if (m_C4Socket_completedReceive == nullptr)
            return false;

        m_C4Socket_close = env->GetStaticMethodID(cls_C4Socket, "close", "(J)V");
        if (m_C4Socket_close == nullptr)
            return false;

        m_C4Socket_requestClose = env->GetStaticMethodID(cls_C4Socket, "requestClose", "(JILjava/lang/String;)V");
        if (m_C4Socket_requestClose == nullptr)
            return false;
    }

    jniLog("sockets initialized");
    return true;
}

// ----------------------------------------------------------------------------
// C4SocketFactory implementation
// ----------------------------------------------------------------------------

static void socket_open(C4Socket *socket, const C4Address *addr, C4Slice options, void *token) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "socketOpen");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    jstring _scheme = toJString(env, addr->scheme);
    jstring _host = toJString(env, addr->hostname);
    jstring _path = toJString(env, addr->path);
    jbyteArray _options = toJByteArray(env, options);

    // This is here because we always release the socket when we close it
    // (see Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_closed)
    // Even though this socket is already retained, we neeed to match retains with releases
    c4socket_retain(socket);

    env->CallStaticVoidMethod(
            cls_C4Socket,
            m_C4Socket_open,
            (jlong) socket,
            (jlong) token,
            _scheme,
            _host,
            addr->port,
            _path,
            _options);

    if (envState == JNI_EDETACHED) {
        detachJVM("socketOpen");
    } else {
        if (_scheme != nullptr) env->DeleteLocalRef(_scheme);
        if (_host != nullptr) env->DeleteLocalRef(_host);
        if (_path != nullptr) env->DeleteLocalRef(_path);
        if (_options != nullptr) env->DeleteLocalRef(_options);
    }
}

static void do_socket_write(JNIEnv *env, C4Socket *socket, C4SliceResult data) {
    jbyteArray _data = toJByteArray(env, data);
    c4slice_free(data);
    env->CallStaticVoidMethod(cls_C4Socket, m_C4Socket_write, (jlong) socket, _data);
    if (_data != nullptr) env->DeleteLocalRef(_data);
}

static void socket_write(C4Socket *socket, C4SliceResult allocatedData) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "socketWrite");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    do_socket_write(env, socket, allocatedData);

    if (envState == JNI_EDETACHED)
        detachJVM("socketWrite");
}

static void socket_completedReceive(C4Socket *socket, size_t byteCount) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "socketCompletedReceive");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    env->CallStaticVoidMethod(cls_C4Socket, m_C4Socket_completedReceive, (jlong) socket, (jlong) byteCount);

    if (envState == JNI_EDETACHED)
        detachJVM("socketCompletedReceive");
}

static void socket_requestClose(C4Socket *socket, int status, C4String messageSlice) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "socketRequestClose");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    jstring _message = toJString(env, messageSlice);
    env->CallStaticVoidMethod(cls_C4Socket, m_C4Socket_requestClose, (jlong) socket, (jint) status, _message);

    if (envState == JNI_EDETACHED) {
        detachJVM("socketRequestClose");
    } else {
        if (_message != nullptr) env->DeleteLocalRef(_message);
    }
}

static void socket_close(C4Socket *socket) {
    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "socketClose");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    env->CallStaticVoidMethod(cls_C4Socket, m_C4Socket_close, (jlong) socket);

    if (envState == JNI_EDETACHED)
        detachJVM("socketCompletedReceive");
}

static const C4SocketFactory kSocketFactory{
        kC4NoFraming,               // framing
        nullptr,                    // context
        &socket_open,               // open
        &socket_write,              // write
        &socket_completedReceive,   // completedReceive
        &socket_close,              // close
        &socket_requestClose,       // requestClose
        nullptr,                    // dispose
};

const C4SocketFactory socket_factory() { return kSocketFactory; }

extern "C" {

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4Socket
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    fromNative
 * Signature: (JLjava/lang/String;ILjava/lang/String;I)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_fromNative(
        JNIEnv *env,
        jclass ignore,
        jlong jcontext,
        jstring jscheme,
        jstring jhost,
        jint jport,
        jstring jpath,
        jint jframing) {
    void *context = (void *) jcontext;

    jstringSlice scheme(env, jscheme);
    jstringSlice host(env, jhost);
    jstringSlice path(env, jpath);

    C4Address c4Address = {};
    c4Address.scheme = scheme;
    c4Address.hostname = host;
    c4Address.port = jport;
    c4Address.path = path;

    C4SocketFactory socketFactory = socket_factory();
    socketFactory.framing = (C4SocketFraming) jframing;
    socketFactory.context = context;

    C4Socket *c4socket = c4socket_fromNative(socketFactory, context, &c4Address);

    // Unlike most ref-counted objects, the C4Socket is not
    // retained when it is created.  We are obliged to retain it immediately
    c4socket_retain(c4socket);

    return (jlong) c4socket;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    opened
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_opened(JNIEnv *env, jclass ignore, jlong jsocket) {
    auto *socket = (C4Socket *) jsocket;
    c4socket_opened(socket);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    gotHTTPResponse
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_gotHTTPResponse(
        JNIEnv *env,
        jclass ignore,
        jlong socket,
        jint httpStatus,
        jbyteArray jresponseHeadersFleece) {
    jbyteArraySlice responseHeadersFleece(env, jresponseHeadersFleece);
    c4socket_gotHTTPResponse((C4Socket *) socket, httpStatus, responseHeadersFleece);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    completedWrite
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_completedWrite(
        JNIEnv *env,
        jclass ignore,
        jlong jSocket,
        jlong jByteCount) {
    auto *socket = (C4Socket *) jSocket;
    auto byteCount = (size_t) jByteCount;
    c4socket_completedWrite(socket, byteCount);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    received
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_received(
        JNIEnv *env,
        jclass ignore,
        jlong jSocket,
        jbyteArray jdata) {
    auto socket = (C4Socket *) jSocket;
    jbyteArraySlice data(env, jdata);
    c4socket_received(socket, data);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    closeRequested
 * Signature: (JILjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_closeRequested(
        JNIEnv *env,
        jclass ignore,
        jlong jSocket,
        jint status,
        jstring jmessage) {
    auto socket = (C4Socket *) jSocket;
    jstringSlice message(env, jmessage);
    c4socket_closeRequested(socket, (int) status, message);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    closed
 * Signature: (JIILjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_closed(
        JNIEnv *env,
        jclass ignore,
        jlong jSocket,
        jint domain,
        jint code,
        jstring jmessage) {
    auto socket = (C4Socket *) jSocket;
    jstringSlice message(env, jmessage);
    C4Error error = c4error_make((C4ErrorDomain) domain, code, message);
    c4socket_closed(socket, error);
    c4socket_release(socket);
}
}