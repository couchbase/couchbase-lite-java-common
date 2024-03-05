/* Header for class com_couchbase_lite_internal_core_impl_NativeC4Key */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Key
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Key
#ifdef __cplusplus
extern "C" {
#endif

#undef com_couchbase_lite_internal_core_C4Key_DEFAULT_PBKDF2_KEY_ROUNDS
#define com_couchbase_lite_internal_core_C4Key_DEFAULT_PBKDF2_KEY_ROUNDS 64000L

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Key
 * Method:    pbkdf2
 * Signature: (Ljava/lang/String;)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Key_pbkdf2
        (JNIEnv *, jclass, jstring);

#ifdef __cplusplus
}
#endif
#endif
