/* Header for class com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
 * Method:    create
 * Signature: (JJ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver_create
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
 * Method:    getChanges
 * Signature: (JI)[Lcom/couchbase/lite/internal/core/C4DocumentChange;
 */
JNIEXPORT jobjectArray
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver_getChanges
        (JNIEnv *, jclass, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver_free
        (JNIEnv*, jclass, jlong);

#ifdef __cplusplus
}
#endif

#endif
