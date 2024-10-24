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

static void createIndex(
        JNIEnv *env,
        jlong coll,
        C4IndexType type,
        jstring jName,
        C4QueryLanguage language,
        jstring jqueryExpressions,
        C4IndexOptions const &options) {
    jstringSlice name(env, jName);
    jstringSlice queryExpressions(env, jqueryExpressions);

    C4Error error{};
    bool ok = c4coll_createIndex(
            (C4Collection *) coll,
            name,
            queryExpressions,
            language,
            type,
            &options,
            &error);

    if (!ok && (error.code != 0))
        throwError(env, error);
}

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
    if ((coll == nullptr) && (error.code != 0)) {
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
    if ((coll == nullptr) && (error.code != 0)) {

        // Ignore LiteCore's annoying "not found" error
        if ((error.domain == LiteCoreDomain) && (error.code == kC4ErrorNotFound)) {
            return (jlong) coll;
        }

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
    if ((coll == nullptr) && (error.code != 0)) {
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
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocumentCount(
        JNIEnv *env,
        jclass ignore,
        jlong coll) {
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
    if (!ok && (error.code != 0))
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
    // -1 is C4Timestamp.Error
    if ((exp == -1) && (error.code != 0)) {
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
    bool ok = c4coll_purgeDoc((C4Collection *) coll, docId, &error);
    if (!ok && (error.code != 0))
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
    if (!data && (error.code != 0)) {
        throwError(env, error);
        return 0;
    }
    return (jlong) FLValue_FromData({data.buf, data.size}, kFLTrusted);
}


/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createValueIndex
 * Signature: (JLjava/lang/String;ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createValueIndex(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jName,
        jint qLanguage,
        jstring jqueryExpressions) {
    C4IndexOptions options = {};
    createIndex(env, coll, kC4ValueIndex, jName, (C4QueryLanguage) qLanguage, jqueryExpressions, options);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createArrayIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createArrayIndex(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jName,
        jstring jpath,
        jstring jqueryExpressions) {
    jstringSlice path(env, jpath);

    C4IndexOptions options = {};
    options.unnestPath = path.c_str();

    createIndex(env, coll, kC4ArrayIndex, jName, kC4N1QLQuery, jqueryExpressions, options);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createFullTextIndex
 * Signature: (JLjava/lang/String;ILjava/lang/String;Ljava/lang/String;B)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createFullTextIndex(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jName,
        jint qLanguage,
        jstring jqueryExpressions,
        jstring jlanguage,
        jboolean ignoreDiacritics) {
    C4IndexOptions options = {};

    jstringSlice language(env, jlanguage);

    options.language = language.c_str();
    options.ignoreDiacritics = ignoreDiacritics == JNI_TRUE;

    createIndex(env, coll, kC4FullTextIndex, jName, (C4QueryLanguage) qLanguage, jqueryExpressions, options);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createPredictiveIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createPredictiveIndex(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jName,
        jstring jqueryExpressions) {
    C4IndexOptions options = {};
    createIndex(env, coll, kC4PredictiveIndex, jName, kC4JSONQuery, jqueryExpressions, options);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createVectorIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;JIJIJJJJJB)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createVectorIndex(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jName,
        jstring jqueryExpressions,
        jlong dimensions,
        jint metric,
        jlong centroids,
        jint encoding,
        jlong subquantizers,
        jlong bits,
        jlong minTrainingSize,
        jlong maxTrainingSize,
        jlong numProbes,
        jboolean isLazy) {
#ifdef COUCHBASE_ENTERPRISE
    C4IndexOptions options = {};

    options.vector = {};
    options.vector.dimensions = (unsigned) dimensions;
    options.vector.metric = (C4VectorMetricType) metric;
    options.vector.clustering = {kC4VectorClusteringFlat, (unsigned) centroids, 0, 0};
    options.vector.encoding = {(C4VectorEncodingType) encoding, (unsigned) subquantizers, (unsigned) bits};
    options.vector.minTrainingSize = (unsigned) minTrainingSize;
    options.vector.maxTrainingSize = (unsigned) maxTrainingSize;
    options.vector.numProbes = (unsigned) numProbes;
    options.vector.lazy = isLazy == JNI_TRUE;

    createIndex(env, coll, kC4VectorIndex, jName, kC4N1QLQuery, jqueryExpressions, options);
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getIndex
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getIndex(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jName) {
    jstringSlice name(env, jName);

    C4Error error{};
    C4Index *idx = c4coll_getIndex((C4Collection *) coll, name, &error);
    if (idx != nullptr)
        return (jlong) idx;

    // no error code; no error
    if (error.code == 0)
        return 0;

    // If the index was not found, just return null
    if ((error.domain == LiteCoreDomain) && (error.code == kC4ErrorMissingIndex))
        return 0;

    throwError(env, error);
    return 0;
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
    bool ok = c4coll_deleteIndex((C4Collection *) coll, name, &error);
    if (!ok && (error.code != 0))
        throwError(env, error);
}
}
