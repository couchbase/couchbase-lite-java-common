//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
#include "c4DatabaseTypes.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4Collection.h"

using namespace litecore;
using namespace litecore::jni;

extern "C" {

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createCollection(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jstring jscope,
        jstring jcollection) {
    jstringSlice scope(env, jscope);
    jstringSlice collection(env, jcollection);
    C4CollectionSpec collSpec = {collection, scope};

    C4Error error{};
    C4Collection *coll = c4db_createCollection((C4Database *) db, collSpec, &error);
    if (!coll && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    c4coll_retain(coll);

    return (jlong) coll;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getCollection(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jstring jscope,
        jstring jcollection) {
    jstringSlice scope(env, jscope);
    jstringSlice collection(env, jcollection);
    C4CollectionSpec collSpec = {collection, scope};

    C4Error error{};
    C4Collection *coll = c4db_getCollection((C4Database *) db, collSpec, &error);
    if (!coll && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    c4coll_retain(coll);
    return (jlong) coll;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDefaultCollection
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDefaultCollection(
        JNIEnv *env,
        jclass ignore,
        jlong db) {
    C4Error error{};
    C4Collection *coll = c4db_getDefaultCollection((C4Database *) db, &error);
    if (!coll && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    c4coll_retain(coll);
    return (jlong) coll;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    isValid
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_isValid(
        JNIEnv *env,
        jclass ignore,
        jlong coll) {
    return (jboolean) c4coll_isValid((C4Collection *) coll);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_free(
        JNIEnv *env,
        jclass ignore,
        jlong coll) {
    c4coll_release((C4Collection *) coll);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDocumentCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocumentCount(JNIEnv *env, jclass ignore, jlong coll) {
    return (jlong) c4coll_getDocumentCount((C4Collection *) coll);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    setDocExpiration
 * Signature: (JLjava/lang/String;J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_setDocExpiration(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jDocId,
        jlong timestamp) {
    jstringSlice docId(env, jDocId);

    C4Error error{};
    bool ok = c4coll_setDocExpiration((C4Collection *) coll, docId, timestamp, &error);
    if (!ok && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDocExpiration
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocExpiration(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jDocId) {
    jstringSlice docID(env, jDocId);

    C4Error error{};
    C4Timestamp exp = c4coll_getDocExpiration((C4Collection *) coll, docID, &error);
    if (!exp && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    return (jlong) exp;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    purgeDoc
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_purgeDoc(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jDocId) {
    jstringSlice docId(env, jDocId);

    C4Error error{};
    bool purged = c4coll_purgeDoc((C4Collection *) coll, docId, &error);
    if (!purged && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getIndexesInfo
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getIndexesInfo(
        JNIEnv *env,
        jclass ignore,
        jlong coll) {
    C4Error error{};
    C4SliceResult data = c4coll_getIndexesInfo((C4Collection *) coll, &error);
    if (!data && error.code != 0) {
        throwError(env, error);
        return 0;
    }
    return (jlong) FLValue_FromData({data.buf, data.size}, kFLTrusted);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;II[B)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createIndex(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jName,
        jstring jqueryExpressions,
        jint queryLanguage,
        jint indexType,
        jstring jlanguage,
        jboolean ignoreDiacritics) {
    jstringSlice name(env, jName);
    jstringSlice queryExpressions(env, jqueryExpressions);
    jstringSlice language(env, jlanguage);

    C4IndexOptions options = {};
    options.language = language.c_str();
    options.ignoreDiacritics = ignoreDiacritics == JNI_TRUE;

    C4Error error{};
    bool res = c4coll_createIndex(
            (C4Collection *) coll,
            name,
            (C4Slice) queryExpressions,
            (C4QueryLanguage) queryLanguage,
            (C4IndexType) indexType,
            &options,
            &error);
    if (!res && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    deleteIndex
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_deleteIndex(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jName) {
    jstringSlice name(env, jName);

    C4Error error{};
    bool res = c4coll_deleteIndex((C4Collection *) coll, name, &error);
    if (!res && error.code != 0)
        throwError(env, error);
}
}