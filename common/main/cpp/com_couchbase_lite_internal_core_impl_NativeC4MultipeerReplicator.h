/* Header for class com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    create
 * Signature: (JLjava/lang/String;J[BJ[Lcom.couchbase.lite.internal.core.MultipeerCollectionConfiguration;[B)J
  */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_create(
        JNIEnv *,
        jclass,
        jlong,
        jstring,
        jlong,
        jbyteArray,
        jlong,
        jobjectArray,
        jbyteArray);

/*
 * Class:     Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    start
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_start(JNIEnv *, jclass, jlong);


/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    stop
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_stop(JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
