/* Header for class com_couchbase_lite_internal_core_C4FullTextMatch */
// This code is used only by tests

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_C4FullTextMatch
#define _Included_com_couchbase_lite_internal_core_C4FullTextMatch
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    dataSource
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4FullTextMatch_dataSource
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    property
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4FullTextMatch_property
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    term
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4FullTextMatch_term
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    start
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4FullTextMatch_start
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    length
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4FullTextMatch_length
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    getFullTextMatchCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4FullTextMatch_getFullTextMatchCount(
        JNIEnv *,
        jclass,
        jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    getFullTextMatch
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4FullTextMatch_getFullTextMatch(
        JNIEnv *,
        jclass,
        jlong,
        jint);

#ifdef __cplusplus
}
#endif
#endif
