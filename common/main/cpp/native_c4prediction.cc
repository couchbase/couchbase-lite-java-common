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
#include "com_couchbase_lite_internal_core_impl_NativeC4Prediction.h"

using namespace litecore;
using namespace litecore::jni;


#include <c4PredictiveQuery.h>

static jclass cls_C4Prediction;
static jmethodID m_C4Prediction_prediction;

#ifdef COUCHBASE_ENTERPRISE

bool litecore::jni::initC4Prediction(JNIEnv *env) {
    jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4Prediction");
    if (localClass == nullptr)
        return false;

    cls_C4Prediction = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    if (cls_C4Prediction == nullptr)
        return false;

    m_C4Prediction_prediction = env->GetStaticMethodID(
            cls_C4Prediction,
            "prediction",
            "(JJJ)Lcom/couchbase/lite/internal/fleece/FLSliceResult;");
    if (m_C4Prediction_prediction == nullptr)
        return false;

    jniLog("prediction initialized");
    return true;
}

static C4SliceResult prediction(void *token, FLDict input, C4Database *c4db, C4Error *error) {
    C4SliceResult res = {nullptr, 0};

    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "publicKeyData");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return res;

    // this call returns null when there is no prediction.
    jobject sliceResult = env->CallStaticObjectMethod(
            cls_C4Prediction,
            m_C4Prediction_prediction,
            (jlong) token,
            (jlong) input,
            (jlong) c4db);

    if (sliceResult != nullptr) {
        res = fromJavaFLSliceResult(env, sliceResult);
        unbindJavaFLSliceResult(env, sliceResult);
    }

    if (envState == JNI_EDETACHED) {
        detachJVM("publicKeyDataCallback");
    } else {
        if (sliceResult != nullptr) env->DeleteLocalRef(sliceResult);
    }

    return res;
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