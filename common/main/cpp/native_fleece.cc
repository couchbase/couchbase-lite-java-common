//
// native_fleece.cc
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "native_glue.hh"
#include "com_couchbase_lite_internal_fleece_impl_NativeFleece.h"
#include "com_couchbase_lite_internal_fleece_impl_NativeFLEncoder.h"

using namespace litecore;
using namespace litecore::jni;

extern "C" {
// ----------------------------------------------------------------------------
// NativeFLArray
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    count
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_count(JNIEnv *env, jclass ignore, jlong jarray) {
    return (jlong) FLArray_Count((FLArray) jarray);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    get
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_get(
        JNIEnv *env,
        jclass ignore,
        jlong jarray,
        jlong jindex) {
    return (jlong) FLArray_Get((FLArray) jarray, (uint32_t) jindex);
}

// ----------------------------------------------------------------------------
// FLArrayIterator
// ----------------------------------------------------------------------------
/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    init
 * Signature: (j)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_init(
        JNIEnv *env,
        jclass ignore,
        jlong jarray) {
    auto itr = (FLArrayIterator *) ::malloc(sizeof(FLArrayIterator));
    FLArrayIterator_Begin((FLArray) jarray, itr);
    return (jlong) itr;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    getValueAt
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_getValueAt(
        JNIEnv *env,
        jclass ignore,
        jlong jitr,
        jint offset) {
    return (jlong) FLArrayIterator_GetValueAt((FLArrayIterator *) jitr, (uint32_t) offset);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_next(
        JNIEnv *env,
        jclass ignore,
        jlong jitr) {
    return FLArrayIterator_Next((FLArrayIterator *) jitr) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    getValue
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_getValue(
        JNIEnv *env,
        jclass ignore,
        jlong jitr) {
    return (jlong) FLArrayIterator_GetValue((FLArrayIterator *) jitr);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLArray
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLArray_free(
        JNIEnv *env,
        jclass ignore,
        jlong jitr) {
    ::free((FLArrayIterator *) jitr);
}

// ----------------------------------------------------------------------------
// NativeFLDict
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    count
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_count(
        JNIEnv *env,
        jclass ignore,
        jlong jdict) {
    return (jlong) FLDict_Count((FLDict) jdict);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    getSharedKey
 * Signature: (J[B)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_get(
        JNIEnv *env,
        jclass ignore,
        jlong jdict,
        jbyteArray jkeystring) {
    jbyteArraySlice key(env, jkeystring);
    return (jlong) FLDict_Get((FLDict) jdict, (C4Slice) key);
}

// ----------------------------------------------------------------------------
// FLDictIterator
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    init
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_init(JNIEnv *env, jclass ignore, jlong jdict) {
    auto itr = (FLDictIterator *) ::malloc(sizeof(FLDictIterator));
    FLDictIterator_Begin((FLDict) jdict, itr);
    return (jlong) itr;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    getCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_getCount(JNIEnv *env, jclass ignore, jlong jitr) {
    return (jlong) FLDictIterator_GetCount((FLDictIterator *) jitr);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_next(JNIEnv *env, jclass ignore, jlong jitr) {
    return FLDictIterator_Next((FLDictIterator *) jitr) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    getKey
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_getKey(JNIEnv *env, jclass ignore, jlong jitr) {
    // This is necessary because, when the iterator is exhausted, calling GetKey
    // will fail with a pointer exception.  GetValue returns null instead.
    bool ok = FLDictIterator_GetValue((FLDictIterator *) jitr);
    if (!ok)
        return nullptr;

    FLString s = FLDictIterator_GetKeyString((FLDictIterator *) jitr);
    return toJString(env, s);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    getValue
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_getValue(JNIEnv *env, jclass ignore, jlong jitr) {
    return (jlong) FLDictIterator_GetValue((FLDictIterator *) jitr);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLDict
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLDict_free(JNIEnv *env, jclass ignore, jlong jitr) {
    ::free((FLDictIterator *) jitr);
}

// ----------------------------------------------------------------------------
// FLValue
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    fromData
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_fromData(
        JNIEnv *env,
        jclass ignore,
        jlong ptr,
        jlong size) {
    FLSlice slice{(const void *) ptr, (size_t) size};
    return (jlong) FLValue_FromData(slice, kFLUntrusted);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    fromTrustedData
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_fromTrustedData(
        JNIEnv *env,
        jclass ignore,
        jbyteArray jdata) {
    jbyteArraySlice data(env, jdata, true);
    return (jlong) FLValue_FromData(data, kFLTrusted);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    getType
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_getType(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    return FLValue_GetType((FLValue) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asBool
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asBool(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    return FLValue_AsBool((FLValue) jvalue) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asUnsigned
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asUnsigned(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    return (jlong) FLValue_AsUnsigned((FLValue) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asInt
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asInt(JNIEnv *env, jclass ignore, jlong jvalue) {
    return (jlong) FLValue_AsInt((FLValue) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asFloat
 * Signature: (J)F
 */
JNIEXPORT jfloat JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asFloat(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    return (jfloat) FLValue_AsFloat((FLValue) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asDouble
 * Signature: (J)D
 */
JNIEXPORT jdouble JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asDouble(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    return (jdouble) FLValue_AsDouble((FLValue) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asString
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asString(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    FLString str = FLValue_AsString((FLValue) jvalue);
    return toJString(env, str);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asData
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asData(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    FLSlice bytes = FLValue_AsData((FLValue) jvalue);
    return toJByteArray(env, bytes);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asArray
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asArray(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    return (jlong) FLValue_AsArray((FLValue) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    asDict
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_asDict(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    return (jlong) FLValue_AsDict((FLValue) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    isInteger
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_isInteger(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    return FLValue_IsInteger((FLValue) jvalue) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    isDouble
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_isDouble(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    return FLValue_IsDouble((FLValue) jvalue) ? JNI_TRUE : JNI_FALSE;
}
/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    isUnsigned
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_isUnsigned(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    return FLValue_IsUnsigned((FLValue) jvalue) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    JSON5ToJSON
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_json5toJson(
        JNIEnv *env,
        jclass ignore,
        jstring jjson5) {
    jstringSlice json5(env, jjson5);
    FLError error = kFLNoError;
    FLStringResult json = FLJSON5_ToJSON(json5, nullptr, nullptr, &error);
    if (error != kFLNoError) {
        throwError(env, {FleeceDomain, error});
        return nullptr;
    }
    jstring res = toJString(env, json);
    FLSliceResult_Release(json);
    return res;
}
/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    toString
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_toString(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    FLStringResult str = FLValue_ToString((FLValue) jvalue);
    jstring res = toJString(env, str);
    FLSliceResult_Release(str);
    return res;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    toJSON
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_toJSON(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    FLStringResult str = FLValue_ToJSON((FLValue) jvalue);
    jstring res = toJString(env, str);
    FLSliceResult_Release(str);
    return res;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLValue
 * Method:    toJSON5
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLValue_toJSON5(
        JNIEnv *env,
        jclass ignore,
        jlong jvalue) {
    FLStringResult str = FLValue_ToJSON5((FLValue) jvalue);
    jstring res = toJString(env, str);
    FLSliceResult_Release(str);
    return res;
}

// ----------------------------------------------------------------------------
// NativeFLSliceResult
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLSliceResult
 * Method:    getBuf
 * Signature: (JJ)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLSliceResult_getBuf(
        JNIEnv *env,
        jclass ignore,
        jlong base,
        jlong size) {
    C4Slice s = {(const void *) base, (size_t) size};
    return toJByteArray(env, s);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLSliceResult
 * Method:    release
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLSliceResult_release(
        JNIEnv *env,
        jclass ignore,
        jlong base,
        jlong size) {
    FLSliceResult result = {(const void *) base, (size_t) size};
    FLSliceResult_Release(result);
}
}
