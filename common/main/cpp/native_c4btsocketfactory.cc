#if defined(COUCHBASE_ENTERPRISE) && defined(__ANDROID__)

#include "c4Socket.h"
#include "c4Socket.hh"
#include "c4SocketTypes.h"
#include "native_glue.hh"
#include "native_bluetoothpeer_internal.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4BTSocketFactory.h"

#include <string>

using namespace litecore;
using namespace litecore::jni;

// ─────────────────────────────────────────────────────────────────────────────
// Java class / method cache
// ─────────────────────────────────────────────────────────────────────────────

static jclass    cls_C4BTSocketFactory;
static jmethodID m_open;
static jmethodID m_write;
static jmethodID m_completedReceive;
static jmethodID m_close;
static jmethodID m_dispose;
static jmethodID m_attached;

bool litecore::jni::initC4BTSocketFactory(JNIEnv* env) {
    jclass localCls = env->FindClass(
            "com/couchbase/lite/internal/p2p/ble/BluetoothSocketFactory");
    if (!localCls) return false;

    cls_C4BTSocketFactory = reinterpret_cast<jclass>(env->NewGlobalRef(localCls));
    env->DeleteLocalRef(localCls);
    if (!cls_C4BTSocketFactory) return false;

    // static void open(long c4socketPeer, long factoryToken, String peerID)
    m_open = env->GetStaticMethodID(cls_C4BTSocketFactory,
                                    "open", "(JJLjava/lang/String;)V");
    if (!m_open) return false;

    // static void write(long c4socketPeer, byte[] data)
    m_write = env->GetStaticMethodID(cls_C4BTSocketFactory,
                                     "write", "(J[B)V");
    if (!m_write) return false;

    // static void completedReceive(long c4socketPeer, long byteCount)
    m_completedReceive = env->GetStaticMethodID(cls_C4BTSocketFactory,
                                                "completedReceive", "(JJ)V");
    if (!m_completedReceive) return false;

    // static void close(long c4socketPeer)
    m_close = env->GetStaticMethodID(cls_C4BTSocketFactory,
                                     "close", "(J)V");
    if (!m_close) return false;

    // static void dispose(long c4socketPeer)
    m_dispose = env->GetStaticMethodID(cls_C4BTSocketFactory,
                                       "dispose", "(J)V");
    if (!m_dispose) return false;

    // static void attached(long c4socketPeer, long btSocketHandle, String peerID)
    m_attached = env->GetStaticMethodID(cls_C4BTSocketFactory,
                                        "attached", "(JJLjava/lang/String;)V");
    if (!m_attached) return false;

    jniLog("C4BTSocketFactory: callbacks initialized");
    return true;
}

// ─────────────────────────────────────────────────────────────────────────────
// C4SocketFactory callback implementations
// ─────────────────────────────────────────────────────────────────────────────

static void btOpen(C4Socket*       socket,
                   const C4Address* addr,
                   C4Slice          options,
                   void*            context) {
    JNIEnv* env = nullptr;
    jint envState = attachJVM(&env, "btOpen");
    if (envState != JNI_OK && envState != JNI_EDETACHED) return;

    c4socket_retain(socket);

    // addr->hostname carries the CBL peer-ID / BT MAC address as a C4Slice.
    // toJString(env, C4Slice) is the correct helper (same as native_c4socket.cc).
    jstring jPeerID = toJString(env, addr->hostname);

    env->CallStaticVoidMethod(
            cls_C4BTSocketFactory, m_open,
            (jlong) socket,
            (jlong) context,    // factoryToken = context set at registration
            jPeerID);

    if (envState == JNI_EDETACHED) {
        detachJVM("btOpen");
    } else {
        if (jPeerID) env->DeleteLocalRef(jPeerID);
    }
}

static void btWrite(C4Socket* socket, C4SliceResult allocatedData) {
    JNIEnv* env = nullptr;
    jint envState = attachJVM(&env, "btWrite");
    if (envState != JNI_OK && envState != JNI_EDETACHED) {
        c4slice_free(allocatedData);
        return;
    }

    jbyteArray jData = toJByteArray(env, allocatedData);
    c4slice_free(allocatedData);

    env->CallStaticVoidMethod(cls_C4BTSocketFactory, m_write, (jlong) socket, jData);

    if (envState == JNI_EDETACHED) {
        detachJVM("btWrite");
    } else {
        if (jData) env->DeleteLocalRef(jData);
    }
}

static void btCompletedReceive(C4Socket* socket, size_t byteCount) {
    JNIEnv* env = nullptr;
    jint envState = attachJVM(&env, "btCompletedReceive");
    if (envState != JNI_OK && envState != JNI_EDETACHED) return;

    env->CallStaticVoidMethod(cls_C4BTSocketFactory, m_completedReceive,
                              (jlong) socket, (jlong) byteCount);

    if (envState == JNI_EDETACHED) detachJVM("btCompletedReceive");
}

static void btClose(C4Socket* socket) {
    JNIEnv* env = nullptr;
    jint envState = attachJVM(&env, "btClose");
    if (envState != JNI_OK && envState != JNI_EDETACHED) return;

    env->CallStaticVoidMethod(cls_C4BTSocketFactory, m_close, (jlong) socket);

    if (envState == JNI_EDETACHED) detachJVM("btClose");
}

static void btDispose(C4Socket* socket) {
    auto* ctx = static_cast<BTNativeHandle*>(socket->getNativeHandle());
    if (ctx) {
        socket->setNativeHandle(nullptr);
        delete ctx;
    }

    JNIEnv* env = nullptr;
    jint envState = attachJVM(&env, "btDispose");
    if (envState != JNI_OK && envState != JNI_EDETACHED) return;

    env->CallStaticVoidMethod(cls_C4BTSocketFactory, m_dispose, (jlong) socket);

    if (envState == JNI_EDETACHED) detachJVM("btDispose");
}

/**
 * btAttached – called by LiteCore after c4socket_fromNative creates the
 * peripheral (incoming) C4Socket.
 *
 * Retrieves the BTNativeHandle stored by fromNative via setNativeHandle(),
 * forwards {btSocketHandle, peerID} to Java, then frees the handle.
 */
static void btAttached(C4Socket* socket) {
    auto* ctx = static_cast<BTNativeHandle*>(socket->getNativeHandle());
    if (!ctx) return;

    // Clear immediately so btDispose won't double-free.
    socket->setNativeHandle(nullptr);

    JNIEnv* env = nullptr;
    jint envState = attachJVM(&env, "btAttached");
    if (envState != JNI_OK && envState != JNI_EDETACHED) {
        delete ctx;
        return;
    }

    jstring jPeerID = env->NewStringUTF((ctx->peerID).c_str());

    env->CallStaticVoidMethod(
            cls_C4BTSocketFactory, m_attached,
            (jlong) socket,
            ctx->btSocketHandle,
            jPeerID);

    delete ctx;

    if (envState == JNI_EDETACHED) {
        detachJVM("btAttached");
    } else {
        if (jPeerID) env->DeleteLocalRef(jPeerID);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The factory struct
// ─────────────────────────────────────────────────────────────────────────────
namespace litecore::jni {
    const C4SocketFactory kBTSocketFactory{
            .framing         = kC4WebSocketClientFraming,
            .context         = nullptr,          // filled in at registration time
            .open            = btOpen,
            .write           = btWrite,
            .completedReceive = btCompletedReceive,
            .close           = btClose,
            .dispose         = btDispose,
            .attached        = btAttached,
    };
}
#endif // COUCHBASE_ENTERPRISE && __ANDROID__