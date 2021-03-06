/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_couchbase_lite_internal_fleece_FLEncoder */

#ifndef _Included_com_couchbase_lite_internal_fleece_FLEncoder
#define _Included_com_couchbase_lite_internal_fleece_FLEncoder
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    newFleeceEncoder
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_newFleeceEncoder
  (JNIEnv *, jclass);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_free
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    reset
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_reset
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeNull
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_writeNull
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeBool
 * Signature: (JZ)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_writeBool
  (JNIEnv *, jclass, jlong, jboolean);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeInt
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_writeInt
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeFloat
 * Signature: (JF)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_writeFloat
  (JNIEnv *, jclass, jlong, jfloat);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeDouble
 * Signature: (JD)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_writeDouble
  (JNIEnv *, jclass, jlong, jdouble);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeString
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_writeString
  (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeData
 * Signature: (J[B)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_writeData
  (JNIEnv *, jclass, jlong, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeValue
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_writeValue
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    beginArray
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_beginArray
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    endArray
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_endArray
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    beginDict
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_beginDict
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    endDict
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_endDict
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeKey
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_writeKey
  (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    finish
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_finish
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    finish2
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLEncoder_finish2
  (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
