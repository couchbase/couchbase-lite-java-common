//
// native_c4query.cc
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

#include "c4Base.h"
#include "native_glue.hh"
#include "com_couchbase_lite_internal_core_impl_NativeC4Query.h"

using namespace litecore;
using namespace litecore::jni;

extern "C" {

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    init
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Query_createQuery(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jint lang,
        jstring jexpr) {
    jstringSlice expr(env, jexpr);
    int errorLoc = -1;
    C4Error error{};

    C4Query *query = c4query_new2(
            (C4Database *) db,
            (C4QueryLanguage) lang,
            expr,
            &errorLoc,
            &error);

    // ??? Should put errorLoc into the exception message.
    if (query == nullptr) {
        throwError(env, error);
        return 0;
    }

    return (jlong) query;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    setParameters
 * Signature: (JJJ)V;
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Query_setParameters(
        JNIEnv *env,
        jclass ignore,
        jlong jquery,
        jlong jparamPtr,
        jlong jparamSize) {
    C4String s {(const void *) jparamPtr, (size_t) jparamSize};
    c4query_setParameters((C4Query *) jquery, s);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    explain
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Query_explain(JNIEnv *env, jclass ignore, jlong jquery) {
    C4StringResult result = c4query_explain((C4Query *) jquery);
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    run
 * Signature: (JJJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Query_run(
        JNIEnv *env,
        jclass ignore,
        jlong jquery,
        jlong jparamPtr,
        jlong jparamSize) {
    C4Error error{};
    C4Slice params {(const void *) jparamPtr, (size_t) jparamSize};
    C4QueryEnumerator *e = c4query_run((C4Query *) jquery, params, &error);
    if (e == nullptr) {
        throwError(env, error);
        return 0;
    }
    return (jlong) e;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    columnCount
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Query_columnCount(JNIEnv *env, jclass ignore, jlong jquery) {
    return c4query_columnCount((C4Query *) jquery);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    columnName
 * Signature: (JI)Ljava/lang/String;
 *
 * ??? Check this to see how expensive this is...
 * Might want to replace it with a function that creates
 * the entire map of column names to indices in one fell swoop...
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Query_columnName(
        JNIEnv *env,
        jclass ignore,
        jlong jquery,
        jint colIdx) {
    return toJString(env, c4query_columnTitle((C4Query *) jquery, colIdx));
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Query
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Query_free(JNIEnv *env, jclass ignore, jlong jquery) {
    c4query_release((C4Query *) jquery);
}
}
