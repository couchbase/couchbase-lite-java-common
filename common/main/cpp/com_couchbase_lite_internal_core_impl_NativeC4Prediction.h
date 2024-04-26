/* Header for class com_couchbase_lite_internal_core_impl_NativeC4Prediction */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Prediction
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Prediction
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Prediction
 * Method:    registerModel
 * Signature: (Ljava/lang/String;J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Prediction_registerModel
        (JNIEnv *, jclass, jstring, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Prediction
 * Method:    unregisterModel
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Prediction_unregisterModel
        (JNIEnv *, jclass, jstring);

#ifdef __cplusplus
}
#endif
#endif
