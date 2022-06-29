/* Header for class com_couchbase_lite_internal_core_impl_NativeC4Replicator */
#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Replicator
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Replicator

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    create
 * Signature: ([Lcom.couchbase.lite.internal.core.C4ReplicationCollection;JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;IZ[BJJ)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_create
  (JNIEnv *, jclass, jobjectArray, jlong, jstring, jstring, jint, jstring, jstring, jint, jboolean, jbyteArray, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    createLocal
 * Signature: ([Lcom.couchbase.lite.internal.core.C4ReplicationCollection;JJZ[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_createLocal
  (JNIEnv *, jclass, jobjectArray, jlong, jlong, jboolean, jbyteArray, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    createWithSocket
 * Signature: ([Lcom.couchbase.lite.internal.core.C4ReplicationCollection;JJZ[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_createWithSocket
  (JNIEnv *, jclass, jobjectArray, jlong, jlong, jboolean, jbyteArray, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_free
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    start
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_start
  (JNIEnv *, jclass, jlong, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    stop
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_stop
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    setOptions
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_setOptions
  (JNIEnv *, jclass, jlong, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    getStatus
 * Signature: (J)Lcom/couchbase/lite/internal/core/C4ReplicatorStatus;
 */
JNIEXPORT jobject JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_getStatus
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    getPendingDocIds
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_getPendingDocIds
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    isDocumentPending
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_isDocumentPending
  (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    setProgressLevel
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_setProgressLevel
  (JNIEnv *, jclass, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    setHostReachable
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_setHostReachable
  (JNIEnv *, jclass, jlong, jboolean);


/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    create
 * Signature: (JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;III[BZZJJ)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_createDeprecated
        (JNIEnv *, jclass, jlong, jstring, jstring, jint, jstring, jstring, jint, jint, jint, jbyteArray, jboolean, jboolean, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    createLocal
 * Signature: (JJII[BZZJ)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_createLocalDeprecated
        (JNIEnv *, jclass, jlong, jlong, jint, jint, jbyteArray, jboolean , jboolean, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Replicator
 * Method:    createWithSocket
 * Signature: (JJII[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Replicator_createWithSocketDeprecated
        (JNIEnv *, jclass, jlong, jlong, jint, jint, jbyteArray, jlong);

#ifdef __cplusplus
}
#endif
#endif
