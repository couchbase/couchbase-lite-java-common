/* Header for class com_couchbase_lite_internal_core_impl_NativeC4Listener */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Listener
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Listener
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    startHttp
 * Signature: (ILjava/lang/String;JZZZZ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_startHttp
        (JNIEnv *, jclass, jint, jstring, jlong, jboolean, jboolean, jboolean, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    startTls
 * Signature: (ILjava/lang/String;JJ[BZ[BZZZZ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_startTls
        (JNIEnv *, jclass, jint, jstring, jlong, jlong, jbyteArray, jboolean, jbyteArray, jboolean, jboolean, jboolean, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_free
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    shareCollection
 * Signature: (JLjava/lang/String;J[J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_shareDbCollections
        (JNIEnv *, jclass, jlong, jstring, jlong, jlongArray);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    getUrls
 * Signature: (JJ)Ljava/util/List;
 */
JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getUrls
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    getPort
 * Signature: (J)I
 */
JNIEXPORT jint
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getPort
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    getConnectionStatus
 * Signature: (J)Lcom/couchbase/lite/ConnectionStatus;
 */
JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getConnectionStatus
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Listener
 * Method:    getUriFromPath
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Listener_getUriFromPath
        (JNIEnv *, jclass, jstring);

#ifdef __cplusplus
}
#endif
#endif
