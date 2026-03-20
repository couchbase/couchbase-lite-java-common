#include <jni.h>

#ifndef COUCHBASE_LITE_JAVA_EE_ROOT_COM_COUCHBASE_LITE_INTERNAL_CORE_IMPL_NATIVEC4PEERDISCOVERYPROVIDER_H
#define COUCHBASE_LITE_JAVA_EE_ROOT_COM_COUCHBASE_LITE_INTERNAL_CORE_IMPL_NATIVEC4PEERDISCOVERYPROVIDER_H

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_create(
        JNIEnv *, jclass, jlong, jstring);


JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_free(
        JNIEnv *, jclass, jlong);

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_peerDiscovered(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId, jobject metadata);

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_peerLost(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId);


JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_onIncomingConnection(
        JNIEnv *env, jclass thiz, jlong providerPtr, jbyteArray peerId, jlong socketPtr);

JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_addPeer(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId);

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_removePeer(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId);

JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_peerWithID(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId);

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_statusChanged(
        JNIEnv *env, jclass thiz, jlong providerPtr, jint mode, jboolean online,
jint errorDomain, jint errorCode);

JNIEXPORT jlongArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_peersWithProvider(
        JNIEnv *env, jclass thiz, jlong providerPtr);

JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_serviceUuidFromPeerGroup(
        JNIEnv* env, jclass, jstring peerGroup);

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_createIncomingSocket(
        JNIEnv* env, jclass,
        jlong providerPtr,
        jlong peerPtr,
        jlong btSocketHandle,
        jstring jUrl);

JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_getSocketFactory(
        JNIEnv* env, jclass,
        jlong providerPtr);

#ifdef __cplusplus
}
#endif

#endif //COUCHBASE_LITE_JAVA_EE_ROOT_COM_COUCHBASE_LITE_INTERNAL_CORE_IMPL_NATIVEC4PEERDISCOVERYPROVIDER_H