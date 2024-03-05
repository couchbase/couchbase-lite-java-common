/* Header for class com_couchbase_lite_internal_core_impl_NativeC4Query */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Query
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Query

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    init
 * Signature: (JILjava/lang/String;)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Query_createQuery
        (JNIEnv *, jclass, jlong, jint, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    setParameters
 * Signature: (JJJ)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Query_setParameters
        (JNIEnv *, jclass, jlong, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Query_free
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    explain
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Query_explain
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    columnCount
 * Signature: (J)I
 */
JNIEXPORT jint
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Query_columnCount
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    columnCount
 * Signature: (JI)Ljava/lang/String
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Query_columnName
        (JNIEnv *, jclass, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    run
 * Signature: (JJJ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Query_run
        (JNIEnv *, jclass, jlong, jlong, jlong);

#ifdef __cplusplus
}
#endif

#endif
