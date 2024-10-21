/* Header for class com_couchbase_lite_internal_core_C4TestUtils */
// This code is used only by tests

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_C4TestUtils
#define _Included_com_couchbase_lite_internal_core_C4TestUtils
#ifdef __cplusplus
extern "C" {
#endif

// C4FullTextMatch

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    dataSource
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_dataSource
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    property
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_property
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    term
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_term
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    start
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_start
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    length
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_length
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getFullTextMatchCount
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_getFullTextMatchCount
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getFullTextMatch
 * Signature: (JI)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_getFullTextMatch
        (JNIEnv *, jclass, jlong, jint);

// C4DocEnumerator

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    enumerateAllDocs
 * Signature: (JI)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_enumerateAllDocs
        (JNIEnv *, jclass, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_next
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getDocument
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_getDocument
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_free
        (JNIEnv *, jclass, jlong);

// C4Blob

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getBlobLength
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_getBlobLength
        (JNIEnv *, jclass, jlong);

// C4BlobStore

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    openStore
 * Signature: (Ljava/lang/String;J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_openStore
        (JNIEnv *, jclass, jstring, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    deleteStore
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_deleteStore
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    freeStore
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_freeStore
        (JNIEnv *, jclass, jlong);

// C4Database

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getPrivateUUID
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_getPrivateUUID
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    encodeJSON
 * Signature: (J[B)Lcom/couchbase/lite/internal/fleece/FLSliceResult;
 */
JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_encodeJSON
        (JNIEnv *, jclass, jlong, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getFlags
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_getFlags(
        JNIEnv *, jclass, jlong);

// C4Document

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getDocID
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_getDocID
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    put
 * Signature: (J[BLjava/lang/String;IZZ[Ljava/lang/String;ZII)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_put
        (JNIEnv *, jclass, jlong, jbyteArray, jstring, jint, jboolean, jboolean, jobjectArray, jboolean, jint, jint);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    put2
 * Signature: (JJLjava/lang/String;IZZ[Ljava/lang/String;ZII)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_put2
        (JNIEnv *, jclass, jlong, jlong, jlong, jstring, jint, jboolean, jboolean, jobjectArray, jboolean, jint, jint);

// C4Key

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    deriveKeyFromPassword
 * Signature: (Ljava/lang/String;)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_deriveKeyFromPassword
        (JNIEnv *, jclass, jstring);

// C4Log

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getLogLevel
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint
JNICALL Java_com_couchbase_lite_internal_core_C4TestUtils_getLevel
        (JNIEnv *, jclass, jstring);

// C4Collection

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    isIndexTrained
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_isIndexTrained(
        JNIEnv *,
        jclass,
        jlong,
        jstring);

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getIndexOptions
 * Signature: (J)Lcom/couchbase/lite/internal/core/C4IndexOptions;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_getIndexOptions(JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
