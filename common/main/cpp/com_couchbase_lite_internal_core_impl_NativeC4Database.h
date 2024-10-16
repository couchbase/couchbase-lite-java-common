/* Header for class com_couchbase_lite_internal_core_C4Database */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Database
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Database
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    open
 * Signature: (Ljava/lang/String;Ljava/lang/String;II[B)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_open
        (JNIEnv *, jclass, jstring, jstring, jlong, jint, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_free
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    copy
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II[B)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_copy
        (JNIEnv *, jclass, jstring, jstring, jstring, jlong, jint, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_close
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    delete
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_delete
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    deleteNamed
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_deleteNamed
        (JNIEnv *, jclass, jstring, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    rekey
 * Signature: (JI[B)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_rekey
        (JNIEnv *, jclass, jlong, jint, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getPath
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getPath
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getPublicUUID
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getPublicUUID
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    beginTransaction
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_beginTransaction
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    endTransaction
 * Signature: (JZ)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_endTransaction
        (JNIEnv *, jclass, jlong, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    setCookie
 * Signature: (JLjava/lang/String;Ljava/lang/String;Z)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_setCookie
        (JNIEnv *, jclass, jlong, jstring, jstring, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getCookies
 * Signature: (JLjava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getCookies
        (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getSharedFleeceEncoder
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getSharedFleeceEncoder
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getFLSharedKeys
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getFLSharedKeys
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    docContainsBlobs
 * Signature: (JJJ)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_docContainsBlobs
        (JNIEnv *, jclass, jlong, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    maintenance
 * Signature: (JI)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_maintenance
        (JNIEnv *, jclass, jlong, jint);


///// Collections

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getScopes
 * Signature: (J)Ljava/util.Set;
 */
JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getScopeNames
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    hasScope
 * Signature: (JLjava/lang/String;)Z;
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_hasScope
        (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    collectionNames
 * Signature: (JLjava/lang/String;)Ljava/util.Set;
 */
JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getCollectionNames
        (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    deleteCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Database_deleteCollection
        (JNIEnv *, jclass, jlong, jstring, jstring);

#ifdef __cplusplus
}
#endif
#endif
