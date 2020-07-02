//
// native_c4listener.cc
//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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
#ifdef COUCHBASE_ENTERPRISE

#include "com_couchbase_lite_internal_core_impl_NativeC4KeyPair.h"
#include "native_glue.hh"
#include "c4Certificate.h"

using namespace litecore;
using namespace litecore::jni;

static C4ExternalKeyCallbacks keyCallbacks;

bool litecore::jni::initC4KeyPair(JNIEnv *) {

}

JNIEXPORT jlong
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_fromExternal
        (JNIEnv *env, jclass ignore, jbyte algorithm, jint keySizeInBits, jlong context) {

    C4Error error;

    auto keyPair = c4keypair_fromExternal((C4KeyPairAlgorithm) algorithm,
                                          (size_t) keySizeInBits,
                                          (void *) context,
                                          keyCallbacks,
                                          &error);
    if (!keyPair) {
        throwError(env, error);
        return -1;
    }

    return (jlong) keyPair;
}

JNIEXPORT void
JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4KeyPair_free
        (JNIEnv *env, jclass ignore, jlong hdl) {
    c4keypair_release((C4KeyPair *) hdl);
}

#endif
