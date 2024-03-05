/* Header for class com_couchbase_lite_internal_core_C4QueryEnumerator */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator_next
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator_free
(JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
 * Method:    getColumns
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator_getColumns
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator
 * Method:    getMissingColumns
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4QueryEnumerator_getMissingColumns
        (JNIEnv * , jclass, jlong);
#ifdef __cplusplus
}
#endif
#endif
