/* Header for class com_couchbase_lite_internal_core_impl_NativeCallback */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeCallback
#define _Included_com_couchbase_lite_internal_core_impl_NativeCallback

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeCallback_nativeRunCallback
        (JNIEnv *, jclass, jlong functionToken);

#ifdef __cplusplus
}
#endif

#endif