/* Header for class com_couchbase_lite_internal_core_impl_NativeC4 */

#include <jni.h>

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
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_setenv
        (JNIEnv *, jclass, jstring, jstring, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    getBuildInfo
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_getBuildInfo
        (JNIEnv *, jclass);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    getVersion
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_getVersion
        (JNIEnv *, jclass);
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    debug
 * Signature: (Z)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_debug
        (JNIEnv *, jclass, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    setTempDir
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_setTempDir
        (JNIEnv *, jclass, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    setExtPath
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_setExtPath
        (JNIEnv *, jclass, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    getMessage
 * Signature: (III)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4_getMessage
        (JNIEnv *, jclass, jint, jint, jint);

#ifdef __cplusplus
}
#endif
#endif
