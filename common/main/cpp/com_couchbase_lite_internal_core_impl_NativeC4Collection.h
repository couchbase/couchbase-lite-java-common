/* Header for class com_couchbase_lite_internal_core_impl_NativeC4Collection */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Collection
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Collection

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDefaultCollection
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDefaultCollection
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getCollection
        (JNIEnv *, jclass, jlong, jstring, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createCollection
        (JNIEnv *, jclass, jlong, jstring, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    isValid
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_isValid
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_free
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDocumentCount
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocumentCount
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    setDocExpiration
 * Signature: (JLjava/lang/String;J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_setDocExpiration
        (JNIEnv *, jclass, jlong, jstring, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDocExpiration
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocExpiration
        (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    purgeDoc
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_purgeDoc
        (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getIndexesInfo
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getIndexesInfo
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createValueIndex
 * Signature: (JLjava/lang/String;ILjava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createValueIndex
        (JNIEnv *, jclass, jlong, jstring, jint, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createFullTextIndex
 * Signature: (JLjava/lang/String;ILjava/lang/String;Ljava/lang/String;B)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createFullTextIndex
        (JNIEnv *, jclass, jlong, jstring, jint, jstring, jstring, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createPredictiveIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createPredictiveIndex
        (JNIEnv *, jclass, jlong, jstring, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createVectoreIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;JIJIJJJJJB)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createVectorIndex
        (JNIEnv *, jclass,
         jlong,
         jstring,
         jstring,
         jlong,
         jint,
         jlong,
         jint,
         jlong,
         jlong,
         jlong,
         jlong,
         jlong,
         jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    deleteIndex
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_deleteIndex
        (JNIEnv *, jclass, jlong, jstring);

#ifdef __cplusplus
}
#endif

#endif
