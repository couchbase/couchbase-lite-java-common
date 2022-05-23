#include <jni.h>
/* Header for class com_couchbase_lite_internal_fleece_FLArray */

#ifndef _Included_com_couchbase_lite_internal_fleece_impl_NativeFLArray
#define _Included_com_couchbase_lite_internal_fleece_impl_NativeFLArray
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    count
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_count
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    get
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_get
  (JNIEnv *, jclass, jlong, jlong);

#ifdef __cplusplus
}
#endif
#endif
