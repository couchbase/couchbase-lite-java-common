/* Header for class com_couchbase_lite_internal_core_impl_NativeC4Document */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4Document
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4Document
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getFromCollection
 * Signature: (JLjava/lang/String;ZZ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getFromCollection
        (JNIEnv *, jclass, jlong, jstring, jboolean, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    createFromSlice
 * Signature: (JLjava/lang/String;JJI)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_createFromSlice
        (JNIEnv *, jclass, jlong, jstring, jlong, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getFlags
 * Signature: (J)I
 */
JNIEXPORT jint
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getFlags
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getRevID
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getRevID
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSequence
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getSequence
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSelectedRevID
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getSelectedRevID
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getRevisionHistory
 * Signature: (JJJ[Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getRevisionHistory
        (JNIEnv *, jclass, jlong, jlong, jlong maxRevs, jobjectArray);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSelectedRevID
 * Signature: (J)J;
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getTimestamp
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSelectedFlags
 * Signature: (J)I
 */
JNIEXPORT jint
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getSelectedFlags
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSelectedSequence
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getSelectedSequence
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSelectedBody2
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getSelectedBody2
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    save
 * Signature: (JI)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_save
        (JNIEnv *, jclass, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_free
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    selectNextLeafRevision
 * Signature: (JZZ)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_selectNextLeafRevision
        (JNIEnv *, jclass, jlong, jboolean, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    resolveConflict
 * Signature: (JLjava/lang/String;Ljava/lang/String;[BI)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_resolveConflict
        (JNIEnv *, jclass, jlong, jstring, jstring, jbyteArray, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    update2
 * Signature: (JJJI)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_update2
        (JNIEnv *, jclass, jlong, jlong, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    bodyAsJSON
 * Signature: (JZ)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_bodyAsJSON
        (JNIEnv *, jclass, jlong, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    dictContainsBlobs
 * Signature: (JJJ)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4Document_dictContainsBlobs
        (JNIEnv *, jclass, jlong, jlong, jlong);

#ifdef __cplusplus
}
#endif
#endif
