/* Header for class com_couchbase_lite_internal_core_NativeC4QueryObserver */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_NativeC4QueryObserver
 * Method:    create
 * Signature: (JJ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_create
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_NativeC4QueryObserver
 * Method:    enable
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_enable
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_NativeC4QueryObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_free
        (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
