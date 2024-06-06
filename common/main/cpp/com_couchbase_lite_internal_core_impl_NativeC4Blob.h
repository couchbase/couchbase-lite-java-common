/* Header for class com_couchbase_lite_internal_core_impl_NativeC4Blob */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Blob
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Blob
#ifdef __cplusplus
extern "C" {
#endif

//// BlobKey
/*
* Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
* Method:    fromString
* Signature: (Ljava/lang/String;)J
*/
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_fromString
        (JNIEnv *, jclass, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    toString
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_toString
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_free
        (JNIEnv *, jclass, jlong);


//// BlobStore
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    getBlobStore
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_getBlobStore
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    getSize
 * Signature: (JJ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_getSize
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    getContents
 * Signature: (JJ)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_getContents
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    getFilePath
 * Signature: (JJ)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_getFilePath
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    create
 * Signature: (J[B)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_create
        (JNIEnv *, jclass, jlong, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    delete
 * Signature: (JJ)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_delete
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    openReadStream
 * Signature: (JJ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_openReadStream
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    openWriteStream
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_openWriteStream
        (JNIEnv *, jclass, jlong);

//// BlobReadStream
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    read
 * Signature: (J[BIJ)I
 */
JNIEXPORT jint
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_read
        (JNIEnv *, jclass, jlong, jbyteArray, jint, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    getLength
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_getLength
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    seek
 * Signature: (JJ)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_seek
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_closeReadStream
        (JNIEnv *, jclass, jlong);

//// BlobWriteStream
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    write
 * Signature: (J[BI)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_write
        (JNIEnv *, jclass, jlong, jbyteArray, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    computeBlobKey
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_computeBlobKey
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    install
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_install
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    closeWriteStream
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_closeWriteStream
        (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
