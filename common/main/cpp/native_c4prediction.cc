//
// native_c4prediction.cc
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
#include "com_couchbase_lite_internal_core_NativeC4Prediction.h"

using namespace litecore;
using namespace litecore::jni;


#include <c4PredictiveQuery.h>

static jclass cls_C4Prediction;
static jmethodID m_C4Prediction_prediction;

#ifdef COUCHBASE_ENTERPRISE

bool litecore::jni::initC4Prediction(JNIEnv *env) {
    jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4Prediction");
    if (!localClass)
        return false;

    cls_C4Prediction = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    if (!cls_C4Prediction)
        return false;

    m_C4Prediction_prediction = env->GetStaticMethodID(cls_C4Prediction, "prediction", "(JJJ)J");
    if (!m_C4Prediction_prediction)
        return false;

    return true;
}

static C4SliceResult prediction(void *token, FLDict input, C4Database *c4db, C4Error *error) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED)
        attachCurrentThread(&env);

    // this call returns null when there is no prediction.
    jobject sliceResult = env->CallStaticObjectMethod(
            cls_C4Prediction,
            m_C4Prediction_prediction,
            (jlong) token,
            (jlong) input,
            (jlong) c4db);

    // if the call returned a nullptr, just give the caller an empty result.
    FLSliceResult result;
    if (!sliceResult)
        result = {nullptr, 0};
    else
        result = fromJavaFLSliceResult(env, sliceResult);

    if (getEnvStat == JNI_EDETACHED)
        gJVM->DetachCurrentThread();

    return result;
}

#endif


extern "C" {
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Prediction_registerModel(
        JNIEnv *env,
        jclass ignore,
        jstring jname,
        jlong token) {
#ifdef COUCHBASE_ENTERPRISE
    jstringSlice name(env, jname);

    C4PredictiveModel predModel = {
            (void *) token,   // .context
            &prediction,      // .prediction
            nullptr};         // .unregistered

    c4pred_registerModel(name.c_str(), predModel);
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Prediction
 * Method:    unregisterModel
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Prediction_unregisterModel(
        JNIEnv *env,
        jclass ignore,
        jstring jname) {
#ifdef COUCHBASE_ENTERPRISE
    jstringSlice name(env, jname);
    c4pred_unregisterModel(name.c_str());
#endif
}
}