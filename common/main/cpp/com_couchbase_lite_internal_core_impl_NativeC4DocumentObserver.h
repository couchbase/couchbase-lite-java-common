#include <jni.h>
/* Header for class com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver */

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver
 * Method:    create
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver_create
    (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4DocumentObserver_free
    (JNIEnv*, jclass, jlong);

#ifdef __cplusplus
}
#endif

#endif