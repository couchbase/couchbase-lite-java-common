/* Header for class com_couchbase_lite_internal_fleece_impl_NativeFLEncoder */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
#define _Included_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    newFleeceEncoder
 * Signature: ()J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_newFleeceEncoder
        (JNIEnv *, jclass);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_free
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    reset
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_reset
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeNull
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeNull
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeBool
 * Signature: (JZ)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeBool
        (JNIEnv * , jclass, jlong, jboolean);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeInt
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeInt
        (JNIEnv * , jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeFloat
 * Signature: (JF)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeFloat
        (JNIEnv * , jclass, jlong, jfloat);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeDouble
 * Signature: (JD)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeDouble
        (JNIEnv * , jclass, jlong, jdouble);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeString
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeString
        (JNIEnv * , jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeStringChars
 * Signature: (J[C)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeStringChars
        (JNIEnv * , jclass, jlong, jcharArray);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeData
 * Signature: (J[B)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeData
        (JNIEnv * , jclass, jlong, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeValue
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeValue
        (JNIEnv * , jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    beginArray
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_beginArray
        (JNIEnv * , jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    endArray
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_endArray
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    beginDict
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_beginDict
        (JNIEnv * , jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    endDict
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_endDict
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeKey
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeKey
        (JNIEnv * , jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    finish
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_finish
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    finish2
 * Signature: (J)Lcom/couchbase/lite/internal/fleece/FLSliceResult
 */
JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_finish2
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    finish3
 * Signature: (J)Lcom/couchbase/lite/internal/fleece/FLSliceResult
 */
JNIEXPORT jobject
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_finish3
        (JNIEnv * , jclass, jlong);

// ----------------------------------------------------------------------------
// JsonEncoder
// ----------------------------------------------------------------------------
/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    newJSONEncoder
 * Signature: ()J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_newJSONEncoder
        (JNIEnv * , jclass);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    finishJSON
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_finishJSON
        (JNIEnv * , jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
