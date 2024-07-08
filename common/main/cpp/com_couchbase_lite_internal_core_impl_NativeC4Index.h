/* Header for class com_couchbase_lite_internal_core_impl_NativeC4Index */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Index
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Index
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Index
 * Method:    beginUpdate
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Index_beginUpdate(
        JNIEnv *,
        jclass,
        jlong,
        jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Index
 * Method:    releaseIndex
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Index_releaseIndex(
        JNIEnv *,
        jclass,
        jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    count
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_count(
        JNIEnv *,
        jclass,
        jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    valueAt
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_valueAt(
        JNIEnv *,
        jclass,
        jlong,
        jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    setVectorAt
 * Signature: (JJ[F)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_setVectorAt(
        JNIEnv *,
        jclass,
        jlong,
        jlong,
        jfloatArray);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    skipVectorAt
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_skipVectorAt(
        JNIEnv *,
        jclass,
        jlong,
        jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    finish
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_finish(
        JNIEnv *,
        jclass,
        jlong);
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4IndexUpdater_close(
        JNIEnv *env,
        jclass,
        jlong);
#ifdef __cplusplus
}
#endif
#endif
