/* Header for class com_couchbase_lite_internal_core_C4Socket */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_C4Socket
#define _Included_com_couchbase_lite_internal_core_C4Socket
#ifdef __cplusplus
extern "C" {
#endif

#undef com_couchbase_lite_internal_core_C4Socket_WEB_SOCKET_CLIENT_FRAMING
#define com_couchbase_lite_internal_core_C4Socket_WEB_SOCKET_CLIENT_FRAMING 0L
#undef com_couchbase_lite_internal_core_C4Socket_NO_FRAMING
#define com_couchbase_lite_internal_core_C4Socket_NO_FRAMING 1L
#undef com_couchbase_lite_internal_core_C4Socket_WEB_SOCKET_SERVER_FRAMING
#define com_couchbase_lite_internal_core_C4Socket_WEB_SOCKET_SERVER_FRAMING 2L

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    fromNative
 * Signature: (JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;I)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_fromNative
        (JNIEnv *, jclass, jlong, jstring, jstring, jint, jstring, jint);

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    opened
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_opened
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    gotPeerCertificate
 * Signature: (J[BLjava.lang.String;)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_gotPeerCertificate
        (JNIEnv *, jclass, jlong, jbyteArray, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    gotHTTPResponse
 * Signature: (JI[B)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_gotHTTPResponse
        (JNIEnv *, jclass, jlong, jint, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    completedWrite
 * Signature: (JJ)V
 */
JNIEXPORT void

JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_completedWrite
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    received
 * Signature: (J[B)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_received
        (JNIEnv *, jclass, jlong, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    closeRequested
 * Signature: (JILjava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_closeRequested
        (JNIEnv *, jclass, jlong, jint, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_C4Socket
 * Method:    closed
 * Signature: (JIILjava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Socket_closed
        (JNIEnv *, jclass, jlong, jint, jint, jstring);

#ifdef __cplusplus
}
#endif
#endif
