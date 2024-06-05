/* Header for several fleece classes */

#include <jni.h>

#ifndef _Included_com_couchbase_lite_internal_fleece_impl_NativeFleece
#define _Included_com_couchbase_lite_internal_fleece_impl_NativeFleece
#ifdef __cplusplus
extern "C" {
#endif

// ----------------------------------------------------------------------------
// NativeFLArray
// ----------------------------------------------------------------------------
/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    count
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_count
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    get
 * Signature: (JJ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_get
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    init
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_init
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    getValueAt
 * Signature: (JI)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_getValueAt
        (JNIEnv *, jclass, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_next
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    getValue
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_getValue
        (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_free
        (JNIEnv *, jclass, jlong);

// ----------------------------------------------------------------------------
// NativeFLDict
// ----------------------------------------------------------------------------
/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    count
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_count
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    get
 * Signature: (J[B)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_get
        (JNIEnv * , jclass, jlong, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    init
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_init
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    getCount
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_getCount
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_next
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    getKey
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_getKey
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    getValue
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_getValue
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_free
        (JNIEnv *, jclass, jlong);

// ----------------------------------------------------------------------------
// FLValue
// ----------------------------------------------------------------------------
/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    fromTrustedData
 * Signature: ([B)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_fromTrustedData
        (JNIEnv * , jclass, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    fromData
 * Signature: (JJ)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_fromData
        (JNIEnv * , jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    getType
 * Signature: (J)I
 */
JNIEXPORT jint
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_getType
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    isInteger
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_isInteger
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    isUnsigned
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_isUnsigned
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    isDouble
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_isDouble
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    toString
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_toString
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    toJSON
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_toJSON
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    toJSON5
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_toJSON5
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asData
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asData
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asBool
 * Signature: (J)Z
 */
JNIEXPORT jboolean
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asBool
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asUnsigned
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asUnsigned
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asInt
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asInt
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asFloat
 * Signature: (J)F
 */
JNIEXPORT jfloat
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asFloat
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asDouble
 * Signature: (J)D
 */
JNIEXPORT jdouble
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asDouble
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asString
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asString
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asArray
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asArray
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asDict
 * Signature: (J)J
 */
JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asDict
        (JNIEnv * , jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    json5toJson
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_json5toJson
        (JNIEnv * , jclass, jstring);

// ----------------------------------------------------------------------------
// NativeFLSliceResult
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLSliceResult
 * Method:    getBuf
 * Signature: (JJ)[B
 */
JNIEXPORT jbyteArray
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLSliceResult_getBuf
        (JNIEnv * , jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLSliceResult
 * Method:    release
 * Signature: (JJ)V
 */
JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLSliceResult_release(
        JNIEnv *, jclass, jlong, jlong);

#ifdef __cplusplus
}
#endif
#endif
