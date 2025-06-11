//
// native_c4replutils.cc
//
// Copyright (c) 2025 Couchbase, Inc All rights reserved.
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

#include <vector>
//#include "c4PeerSync.h"
#include "c4PeerSyncTypes.h"
#include "native_glue.hh"
#include "native_c4replutils.hh"
#include "com_couchbase_lite_internal_core_impl_NativeC4MultipeerReplicator.h"

using namespace litecore;
using namespace litecore::jni;

namespace litecore::jni {
    //-------------------------------------------------------------------------
    // A little bit of code common to c4Replicator and c4MultipeerReplicator
    //-------------------------------------------------------------------------

    // C4ReplicatorStatus
    static jclass cls_C4ReplStatus; // global reference
    static jmethodID m_C4ReplStatus_init;

    // C4DocumentEnded
    static jclass cls_C4DocEnded;
    static jmethodID m_C4DocEnded_init;

    bool initC4ReplicatorUtils(JNIEnv *env) {
        // C4ReplicatorStatus, constructor, and fields
        {
            jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4ReplicatorStatus");
            if (localClass == nullptr)
                return false;

            cls_C4ReplStatus = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
            if (cls_C4ReplStatus == nullptr)
                return false;

            m_C4ReplStatus_init = env->GetMethodID(cls_C4ReplStatus, "<init>", "(IJJJIII)V");
            if (m_C4ReplStatus_init == nullptr)
                return false;
        }

        // C4DocumentEnded, constructor, and fields
        {
            jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4DocumentEnded");
            if (localClass == nullptr)
                return false;

            cls_C4DocEnded = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
            if (cls_C4DocEnded == nullptr)
                return false;

            m_C4DocEnded_init = env->GetMethodID(
                    cls_C4DocEnded,
                    "<init>",
                    "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJIIIZ)V");
            if (m_C4DocEnded_init == nullptr)
                return false;
        }

        jniLog("replicator utils initialized");

        return true;
    }

    jobject toJavaReplStatus(JNIEnv *env, C4ReplicatorStatus status) {
        return env->NewObject(
                cls_C4ReplStatus,
                m_C4ReplStatus_init,
                (jint) status.level,
                (jlong) status.progress.unitsCompleted,
                (jlong) status.progress.unitsTotal,
                (jlong) status.progress.documentCount,
                (jint) status.error.domain,
                (jint) status.error.code,
                (jint) status.error.internal_info);
    }

    jobject toJavaDocumentEnded(JNIEnv *env, const C4DocumentEnded *document) {
        jstring _scope = toJString(env, document->collectionSpec.scope);
        jstring _name = toJString(env, document->collectionSpec.name);
        jstring _docID = toJString(env, document->docID);
        jstring _revID = toJString(env, document->revID);

        jobject _docEnd = env->NewObject(
                cls_C4DocEnded,
                m_C4DocEnded_init,
                (jlong) document->collectionContext,
                _scope,
                _name,
                _docID,
                _revID,
                (jint) document->flags,
                (jlong) document->sequence,
                (jint) document->error.domain,
                (jint) document->error.code,
                (jint) document->error.internal_info,
                document->errorIsTransient ? JNI_TRUE : JNI_FALSE);

        if (_scope != nullptr) env->DeleteLocalRef(_scope);
        if (_name != nullptr) env->DeleteLocalRef(_name);
        if (_docID != nullptr) env->DeleteLocalRef(_docID);
        if (_revID != nullptr) env->DeleteLocalRef(_revID);

        return _docEnd;
    }

    jobjectArray toJavaDocumentEndedArray(JNIEnv *env, int arraySize, const C4DocumentEnded *array[]) {
        jobjectArray ds = env->NewObjectArray(arraySize, cls_C4DocEnded, nullptr);
        for (int i = 0; i < arraySize; i++) {
            jobject d = toJavaDocumentEnded(env, array[i]);
            env->SetObjectArrayElement(ds, i, d);
            if (d != nullptr) env->DeleteLocalRef(d);
        }
        return ds;
    }
}
