#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Collection
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Collection

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDefaultCollection
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDefaultCollection
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    hasCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_hasCollection
        (JNIEnv *, jclass, jlong, jstring, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getCollection
        (JNIEnv *, jclass, jlong, jstring, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createCollection
        (JNIEnv *, jclass, jlong, jstring, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    deleteCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_deleteCollection
        (JNIEnv *, jclass, jlong, jstring, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    collectionNames
 * Signature: (JLjava/lang/String;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getCollectionNames
        (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    scopeNames
 * Signature: (J)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getScopeNames
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    isValid
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_isValid
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDatabase
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDatabase
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDocumentCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocumentCount
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getLastSequence
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getLastSequence
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDoc
 * Signature: (JLjava/lang/String;ZJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDoc
        (JNIEnv *, jclass, jlong, jstring, jboolean, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDocBySequence
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocBySequence
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    putDoc
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_putDoc
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createDoc
 * Signature: (JLjava/lang/String;[BI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createDoc
        (JNIEnv *, jclass, jlong, jstring, jbyteArray, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    moveDoc
 * Signature: (JLjava/lang/String;JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_moveDoc
        (JNIEnv *, jclass, jlong, jstring, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    purgeDoc
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_purgeDoc
        (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    setDocExpiration
 * Signature: (JLjava/lang/String;J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_setDocExpiration
        (JNIEnv *, jclass, jlong, jstring, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDocExpiration
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocExpiration
        (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    nextDocExpiration
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_nextDocExpiration
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    purgeExpiredDocs
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_purgeExpiredDocs
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;II[B)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createIndex
        (JNIEnv *, jclass, jlong, jstring, jstring, jint, jint, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    deleteIndex
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_deleteIndex
        (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getIndexesInfo
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getIndexesInfo
        (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif

#endif
