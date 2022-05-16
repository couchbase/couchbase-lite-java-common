#include <jni.h>
/* Header for class com_couchbase_lite_internal_core_impl_NativeC4 */

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    setenv
 * Signature: (Ljava/lang/String;Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_setenv
  (JNIEnv *, jclass, jstring, jstring, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    getenv
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_getenv
  (JNIEnv *, jclass, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    getBuildInfo
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_getBuildInfo
  (JNIEnv *, jclass);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    getVersion
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_getVersion
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif
