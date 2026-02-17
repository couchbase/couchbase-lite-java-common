#ifndef COUCHBASE_LITE_JAVA_EE_ROOT_COM_COUCHBASE_LITE_INTERNAL_CORE_IMPL_NATIVEBLUETOOTHPEER_H
#define COUCHBASE_LITE_JAVA_EE_ROOT_COM_COUCHBASE_LITE_INTERNAL_CORE_IMPL_NATIVEBLUETOOTHPEER_H

#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C++" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeBluetoothPeer
 * Method:    getId
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_getId(
        JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeBluetoothPeer
 * Method:    isOnline
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_isOnline(
        JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeBluetoothPeer
 * Method:    getAllMetadata
 * Signature: (J)Ljava/util/Map;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_getAllMetadata(
        JNIEnv *, jclass, jlong);


/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeBluetoothPeer
 * Method:    setMetadata
 * Signature: (JLjava/util/Map;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_setMetadata(
        JNIEnv *, jclass, jlong, jobject);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeBluetoothPeer
 * Method:    monitorMetadata
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_monitorMetadata(
        JNIEnv *, jclass, jlong, jboolean);

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeBluetoothPeer_resolvedURL(
        JNIEnv *env, jclass clazz, jlong peerPtr, jstring jurl);

#ifdef __cplusplus
}
#endif

#endif //COUCHBASE_LITE_JAVA_EE_ROOT_COM_COUCHBASE_LITE_INTERNAL_CORE_IMPL_NATIVEBLUETOOTHPEER_H
