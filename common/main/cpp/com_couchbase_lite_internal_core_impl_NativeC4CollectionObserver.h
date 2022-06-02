#include <jni.h>
/* Header for class com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver */

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
 * Method:    create
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver_create
    (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
 * Method:    getChanges
 * Signature: (JI)[Lcom/couchbase/lite/internal/core/C4CollectionChange;
 */
JNIEXPORT jobjectArray JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver_getChanges
        (JNIEnv *, jclass, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionObserver_free
    (JNIEnv*, jclass, jlong);

#ifdef __cplusplus
}
#endif

#endif
