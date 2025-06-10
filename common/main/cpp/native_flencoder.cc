//
// native_flencoder.cc
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
#include "com_couchbase_lite_internal_fleece_impl_NativeFLEncoder.h"

using namespace litecore;
using namespace litecore::jni;

extern "C" {
// ----------------------------------------------------------------------------
// FLEncoder
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    init
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_newFleeceEncoder(JNIEnv *env, jclass ignore) {
    return (jlong) FLEncoder_New();
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_free(JNIEnv *env, jclass ignore, jlong jenc) {
    FLEncoder_Free((FLEncoder) jenc);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeNull
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeNull(JNIEnv *env, jclass ignore, jlong jenc) {
    return FLEncoder_WriteNull((FLEncoder) jenc) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeBool
 * Signature: (JZ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeBool(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jboolean jvalue) {
    return FLEncoder_WriteBool((FLEncoder) jenc, (bool) jvalue) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeInt
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeInt(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jlong jvalue) {
    return FLEncoder_WriteInt((FLEncoder) jenc, (int64_t) jvalue) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeFloat
 * Signature: (JF)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeFloat(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jfloat jvalue) {
    return FLEncoder_WriteFloat((FLEncoder) jenc, (float) jvalue) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeDouble
 * Signature: (JD)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeDouble(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jdouble jvalue) {
    return FLEncoder_WriteDouble((FLEncoder) jenc, (double) jvalue) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeString
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeString(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jstring jvalue) {
    jstringSlice value(env, jvalue);
    return FLEncoder_WriteString((FLEncoder) jenc, value) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeStringChars
 * Signature: (J[C)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeStringChars(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jcharArray jvalue) {
    jstringSlice value(env, jvalue);
    return FLEncoder_WriteString((FLEncoder) jenc, value) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeData
 * Signature: (J[B)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeData(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jbyteArray jvalue) {
    jbyteArraySlice value(env, jvalue);
    return FLEncoder_WriteData((FLEncoder) jenc, value) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeValue
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeValue(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jlong jvalue) {
    return FLEncoder_WriteValue((FLEncoder) jenc, (FLValue) jvalue) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    beginArray
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_beginArray(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jlong jreserve) {
    return FLEncoder_BeginArray((FLEncoder) jenc, (size_t) jreserve) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    endArray
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_endArray(JNIEnv *env, jclass ignore, jlong jenc) {
    return FLEncoder_EndArray((FLEncoder) jenc) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    beginDict
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_beginDict(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jlong jreserve) {
    return FLEncoder_BeginDict((FLEncoder) jenc, (size_t) jreserve) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    endDict
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_endDict(JNIEnv *env, jclass ignore, jlong jenc) {
    return FLEncoder_EndDict((FLEncoder) jenc) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    writeKey
 * Signature: (J[B)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_writeKey(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jstring jkey) {
    if (jkey == nullptr)
        return JNI_FALSE;
    jstringSlice key(env, jkey);
    return FLEncoder_WriteKey((FLEncoder) jenc, key) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    finish
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_finish(JNIEnv *env, jclass ignore, jlong jenc) {
    FLError error = kFLNoError;
    FLSliceResult result = FLEncoder_Finish((FLEncoder) jenc, &error);
    if (error != kFLNoError) {
        throwError(env, {FleeceDomain, error}, FLEncoder_GetErrorMessage((FLEncoder) jenc));
        return nullptr;
    }

    jbyteArray res = toJByteArray(env, (C4Slice) result);
    FLSliceResult_Release(result);
    return res;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    finish2
 * Signature: (J)Lcom/couchbase/lite/internal/fleece/FLSliceResult
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_finish2(JNIEnv *env, jclass ignore, jlong jenc) {
    FLError error = kFLNoError;
    FLSliceResult res = FLEncoder_Finish((FLEncoder) jenc, &error);
    if (error != kFLNoError) {
        throwError(env, {FleeceDomain, error}, FLEncoder_GetErrorMessage((FLEncoder) jenc));
        return nullptr;
    }

    return toJavaFLSliceResult(env, res);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    reset
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_reset(
        JNIEnv *env,
        jclass ignore,
        jlong jenc) {
    FLEncoder_Reset((FLEncoder) jenc);
}

// ----------------------------------------------------------------------------
// JsonEncoder
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    newJSONEncoder
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_newJSONEncoder(
        JNIEnv *env,
        jclass ignore) {
    return (jlong) FLEncoder_NewWithOptions(kFLEncodeJSON, 0, false);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_impl_NativeFLEncoder
 * Method:    finishJSON
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_fleece_impl_NativeFLEncoder_finishJSON(
        JNIEnv *env,
        jclass ignore,
        jlong jenc) {
    FLError error = kFLNoError;
    FLSliceResult result = FLEncoder_Finish((FLEncoder) jenc, &error);
    if (error != kFLNoError) {
        throwError(env, {FleeceDomain, error}, FLEncoder_GetErrorMessage((FLEncoder) jenc));
        return nullptr;
    }

    jstring json = toJString(env, result);

    FLSliceResult_Release(result);

    if (json == nullptr)
        throwError(env, {LiteCoreDomain, kC4ErrorCorruptData});

    return json;
}
}
