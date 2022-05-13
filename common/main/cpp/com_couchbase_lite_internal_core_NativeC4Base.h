#include <jni.h>
/* Header for class com_couchbase_lite_internal_core_impl_NativeC4Base */

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Base
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Base
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Base
 * Method:    debug
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Base_debug
  (JNIEnv *, jclass, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Base
 * Method:    setTempDir
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Base_setTempDir
  (JNIEnv *, jclass, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Base
 * Method:    getMessage
 * Signature: (III)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Base_getMessage
  (JNIEnv *, jclass, jint, jint, jint);

#ifdef __cplusplus
}
#endif
#endif
