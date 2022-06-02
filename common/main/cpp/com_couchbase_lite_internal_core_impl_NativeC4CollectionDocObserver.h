#include <jni.h>
/* Header for class com_couchbase_lite_internal_core_impl_NativeC4CollectionDocObserver */

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4CollectionDocObserver
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4CollectionDocObserver

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionDocObserver
 * Method:    create
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionDocObserver_create
    (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4CollectionDocObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4CollectionDocObserver_free
    (JNIEnv*, jclass, jlong);

#ifdef __cplusplus
}
#endif

#endif
