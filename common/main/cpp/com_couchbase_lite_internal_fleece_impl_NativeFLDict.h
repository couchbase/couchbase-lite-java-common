#include <jni.h>
/* Header for class com_couchbase_lite_internal_fleece_FLDict */

#ifndef _Included_com_couchbase_lite_internal_fleece_impl_NativeFLDict
#define _Included_com_couchbase_lite_internal_fleece_impl_NativeFLDict
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    count
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_count
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    get
 * Signature: (J[B)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_get
  (JNIEnv *, jclass, jlong, jbyteArray);

#ifdef __cplusplus
}
#endif
#endif
