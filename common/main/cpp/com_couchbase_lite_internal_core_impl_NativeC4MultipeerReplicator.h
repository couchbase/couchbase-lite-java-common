/* Header for class com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    create
 * Signature: (JLjava/lang/String;IJ[BJ[Lcom/couchbase/lite/internal/MultipeerReplicationCollection;[B)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_create(
        JNIEnv *,
        jclass,
        jlong,
        jstring,
        jint,
        jlong,
        jbyteArray,
        jlong,
        jobjectArray,
        jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
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

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_free(JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    getId
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_getId(
        JNIEnv *env,
        jclass ignore,
        jlong peer);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    getStatus
 * Signature: (JI)Lcom/couchbase/lite/internal/core/C4PeerSyncStatus;
 */
JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_getStatus(
        JNIEnv *env,
        jclass clazz,
        jlong peer,
        jint protocol);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    getNeighborPeers
 * Signature: (J)[[B
 */
JNIEXPORT jobjectArray
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_getNeighborPeers(
        JNIEnv *env,
        jclass ignore,
        jlong peer);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    getPeerInfo
 * Signature: (J[B)Lcom/couchbase/lite/PeerInfo;
 */
JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_getPeerInfo(
        JNIEnv *env,
        jclass ignore,
        jlong peer,
        jbyteArray jpeerId);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator
 * Method:    setProgressLevel
 * Signature: (JI)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator_setProgressLevel(
        JNIEnv *env,
        jclass ignore,
        jlong peer,
        jint progressLevel);

#ifdef __cplusplus
}
#endif
#endif
