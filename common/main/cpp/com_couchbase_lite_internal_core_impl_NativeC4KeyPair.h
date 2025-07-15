/* Header for class com_couchbase_lite_internal_core_impl_NativeC4KeyPair */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4KeyPair
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4KeyPair
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4KeyPair
 * Method:    generateSelfSignedCertificate
 * Signature: (JBI[[Ljava/lang/String;BJ)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_generateSelfSignedCertificate
        (JNIEnv *, jclass, jlong, jbyte, jint, jobjectArray, jbyte, jlong);
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_generateCertificate
        (JNIEnv *, jclass, jlong, jlong, jbyte, jint, jobjectArray, jbyte, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4KeyPair
 * Method:    fromExternal
 * Signature: (BIJ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_fromExternal
        (JNIEnv *, jclass, jbyte, jint, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4KeyPair
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_free
        (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
