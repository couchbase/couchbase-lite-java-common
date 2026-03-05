#if defined(COUCHBASE_ENTERPRISE) && defined(__ANDROID__)
#include "com_couchbase_lite_internal_core_impl_NativeBluetoothPeer.h"
#include "native_glue.hh"
#include "c4PeerDiscovery.hh"
#include "c4Error.h"
#include "fleece/RefCounted.hh"
#include "fleece/FLExpert.h"
#include "MetadataHelper.h"
#include "native_bluetoothpeer_internal.h"

using namespace litecore;
using namespace litecore::jni;

// Helper to convert C4Peer* from jlong
static C4Peer* getPeer(jlong peerPtr) {
    return reinterpret_cast<C4Peer*>(peerPtr);
}

extern "C++" {
    JNIEXPORT jstring JNICALL
    Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_getId(
            JNIEnv *env, jclass clazz, jlong peerPtr) {

        C4Peer* peer = getPeer(peerPtr);
        if (!peer) return nullptr;

        return UTF8ToJstring(env, peer->id.c_str());
    }

    JNIEXPORT jboolean JNICALL
    Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_isOnline(
            JNIEnv *env, jclass clazz, jlong peerPtr) {

        C4Peer* peer = getPeer(peerPtr);
        if (!peer) return JNI_FALSE;

        return peer->online() ? JNI_TRUE : JNI_FALSE;
    }


    JNIEXPORT jobject JNICALL
    Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_getAllMetadata(
            JNIEnv *env, jclass clazz, jlong peerPtr) {

        C4Peer* peer = getPeer(peerPtr);
        if (!peer) return nullptr;

        C4Peer::Metadata metadata = peer->getAllMetadata();
        return metadataToJavaMap(env, metadata);
    }

    JNIEXPORT void JNICALL
    Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_setMetadata(
            JNIEnv *env, jclass clazz, jlong peerPtr, jobject metadata) {

        C4Peer* peer = getPeer(peerPtr);
        if (!peer) return;

        C4Peer::Metadata nativeMetadata = javaMapToMetadata(env, metadata);
        peer->setMetadata(nativeMetadata);
    }

    JNIEXPORT void JNICALL
    Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_monitorMetadata(
            JNIEnv *env, jclass clazz, jlong peerPtr, jboolean enable) {

        C4Peer *peer = getPeer(peerPtr);
        if (!peer) return;

        peer->monitorMetadata(enable != JNI_FALSE);
    }

    JNIEXPORT void JNICALL
    Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_resolvedURL(
            JNIEnv *env, jclass clazz, jlong peerPtr, jstring jurl) {
        std::string url = JstringToUTF8(env, jurl);

        BluetoothPeer *peer = static_cast<BluetoothPeer*>(getPeer(peerPtr));
        if (!peer) return;

        peer->resolvingUrl(url, kC4NoError);
    }

    JNIEXPORT void JNICALL
    Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_release(
            JNIEnv *env, jclass clazz, jlong peerPtr) {
        fleece::release(reinterpret_cast<const fleece::RefCounted*>(peerPtr));
    }
}
#endif