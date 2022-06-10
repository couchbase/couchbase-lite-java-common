//
// native_c4database.cc
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
#include <errno.h>
#include "c4.h"
#include "c4Document+Fleece.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4Database.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

extern "C" {


// - Lifecycle

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    open
 * Signature: (Ljava/lang/String;Ljava/lang/String;II[B)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_open(
        JNIEnv *env,
        jclass ignore,
        jstring jparentDir,
        jstring jname,
        jint jflags,
        jint encryptionAlg,
        jbyteArray encryptionKey) {
    jstringSlice name(env, jname);

    jstringSlice parentDir(env, jparentDir);

    C4DatabaseConfig2 config;
    config.parentDirectory = parentDir;
    config.flags = (C4DatabaseFlags) jflags;
    if (!getEncryptionKey(env, encryptionAlg, encryptionKey, &config.encryptionKey))
        return 0;

    C4Error error{};
    C4Database *db = c4db_openNamed(name, &config, &error);
    if (!db && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    return (jlong) db;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_close(JNIEnv *env, jclass ignore, jlong jdb) {
    C4Error error{};
    bool res = c4db_close((C4Database *) jdb, &error);
    if (!res && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_free(JNIEnv *env, jclass ignore, jlong jdb) {
    c4db_release((C4Database *) jdb);
}


// - File System

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getPath
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getPath(JNIEnv *env, jclass ignore, jlong jdb) {
    C4SliceResult slice = c4db_getPath((C4Database *) jdb);
    jstring ret = toJString(env, slice);
    c4slice_free(slice);
    return ret;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    copy
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II[B)Z
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_copy(
        JNIEnv *env,
        jclass ignore,
        jstring jfromPath,
        jstring jparentDir,
        jstring jname,
        jint jflags,
        jint encryptionAlg,
        jbyteArray encryptionKey) {
    jstringSlice fromPath(env, jfromPath);
    jstringSlice name(env, jname);

    jstringSlice parentDir(env, jparentDir);

    C4DatabaseConfig2 config;
    config.parentDirectory = parentDir;
    config.flags = (C4DatabaseFlags) jflags;
    if (!getEncryptionKey(env, encryptionAlg, encryptionKey, &config.encryptionKey))
        return;

    C4Error error{};
    bool res = c4db_copyNamed(fromPath, name, &config, &error);
    if (!res && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    delete
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_delete(JNIEnv *env, jclass ignore, jlong jdb) {
    C4Error error{};
    bool res = c4db_delete((C4Database *) jdb, &error);
    if (!res && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    deleteAtPath
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_deleteNamed(
        JNIEnv *env,
        jclass ignore,
        jstring name,
        jstring dir) {
    jstringSlice dbName(env, name);
    jstringSlice inDirectory(env, dir);

    C4Error error{};
    bool res = c4db_deleteNamed(dbName, inDirectory, &error);
    if (!res && error.code != 0)
        throwError(env, error);
}


// - UUID

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getPublicUUID
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getPublicUUID(JNIEnv *env, jclass ignore, jlong jdb) {
    C4UUID uuid;

    C4Error error{};
    bool res = c4db_getUUIDs((C4Database *) jdb, &uuid, nullptr, &error);
    if (!res && error.code != 0)
        throwError(env, error);

    C4Slice s = {&uuid, sizeof(uuid)};
    return toJByteArray(env, s);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getPrivateUUID
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getPrivateUUID(JNIEnv *env, jclass ignore, jlong jdb) {
    C4UUID uuid;

    C4Error error{};
    bool res = c4db_getUUIDs((C4Database *) jdb, nullptr, &uuid, &error);
    if (!res && error.code != 0)
        throwError(env, error);

    C4Slice s = {&uuid, sizeof(uuid)};
    return toJByteArray(env, s);
}


// - Transactions

/*
* Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
* Method:    beginTransaction
* Signature: (J)V
*/
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_beginTransaction(JNIEnv *env, jclass ignore, jlong jdb) {
    C4Error error{};
    bool res = c4db_beginTransaction((C4Database *) jdb, &error);
    if (!res && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    endTransaction
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_endTransaction(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jboolean jcommit) {
    C4Error error{};
    bool res = c4db_endTransaction((C4Database *) jdb, jcommit, &error);
    if (!res && error.code != 0)
        throwError(env, error);
}


// - Maintenance

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    rekey
 * Signature: (JI[B)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_rekey(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jint encryptionAlg,
        jbyteArray encryptionKey) {
    C4EncryptionKey key;
    if (!getEncryptionKey(env, encryptionAlg, encryptionKey, &key))
        return;

    C4Error error{};
    bool res = c4db_rekey((C4Database *) jdb, &key, &error);
    if (!res && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    maintenance
 * Signature: (JI)J
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_maintenance(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jint type) {
    C4Error error{};
    bool res = c4db_maintenance((C4Database *) db, (C4MaintenanceType) type, &error);
    if (!res && error.code != 0)
        throwError(env, error);

    return (jboolean) res;
}


// - Cookies

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    setCookie
 * Signature: (JLjava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_setCookie(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jstring jurl,
        jstring jcookie) {
    jstringSlice url(env, jurl);
    jstringSlice cookie(env, jcookie);

    C4Address address;
    if (!c4address_fromURL(url, &address, nullptr)) {
        throwError(env, {NetworkDomain, kC4NetErrInvalidURL});
        return;
    }

    C4Error error{};
    bool res = c4db_setCookie((C4Database *) jdb, cookie, address.hostname, address.path, &error);
    if (!res && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getCookies
 * Signature: (JLjava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getCookies(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jstring jurl) {
    jstringSlice url(env, jurl);

    C4Address address;
    if (!c4address_fromURL(url, &address, nullptr)) {
        throwError(env, {NetworkDomain, kC4NetErrInvalidURL});
        return nullptr;
    }

    C4Error error{};
    C4StringResult result = c4db_getCookies((C4Database *) jdb, address, &error);
    if (!result && error.code != 0) {
        throwError(env, error);
        return nullptr;
    }

    jstring cookies = toJString(env, result);
    c4slice_free(result);
    return cookies;
}


// - Utilities

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getSharedFleeceEncoder
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getSharedFleeceEncoder(
        JNIEnv *env,
        jclass ignore,
        jlong db) {
    return (jlong) c4db_getSharedFleeceEncoder((C4Database *) db);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getFLSharedKeys
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getFLSharedKeys(JNIEnv *env, jclass ignore, jlong db) {
    return (jlong) c4db_getFLSharedKeys((C4Database *) db);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    encodeJSON
 * Signature: (J[B)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_encodeJSON(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jbyteArray jbody) {
    jbyteArraySlice body(env, jbody, false);

    C4Error error{};
    C4SliceResult res = c4db_encodeJSON((C4Database *) db, (C4Slice) body, &error);
    if (!res && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    return (jlong) copyToHeap(res);
}

// - Scopes and Collections

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getScopes
 * Signature: (J)Ljava/util.List;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getScopeNames(
        JNIEnv *env,
        jclass ignore,
        jlong db) {
    C4Error error{};
    auto scopes = c4db_scopeNames((C4Database *) db, &error);
    if (!scopes && error.code != 0) {
        throwError(env, error);
        return nullptr;
    }

    jobject scopeSet = toStringSet(env, scopes);
    FLMutableArray_Release(scopes);
    return scopeSet;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    hasScope
 * Signature: (JLjava/lang/String;)J;
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_hasScope(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jstring jscope) {
    jstringSlice scope(env, jscope);
    return c4db_hasScope((C4Database *) db, scope);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    collectionNames
 * Signature: (JLjava/lang/String;)Ljava/util.Set;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getCollectionNames(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jstring jscope) {
    jstringSlice scope(env, jscope);

    C4Error error{};
    auto collections = c4db_collectionNames((C4Database *) db, scope, &error);
    if (!collections && error.code != 0) {
        throwError(env, error);
        return 0;
    }

    jobject collectionsSet = toStringSet(env, collections);
    FLMutableArray_Release(collections);
    return collectionsSet;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    deleteCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_deleteCollection(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jstring jscope,
        jstring jcollection) {
    jstringSlice scope(env, jscope);
    jstringSlice collection(env, jcollection);
    C4CollectionSpec collSpec = {collection, scope};

    C4Error error{};
    bool res = c4db_deleteCollection((C4Database *) db, collSpec, &error);
    if (!res && error.code != 0)
        throwError(env, error);
}

// - Testing

/*
 * Class:     com_couchbase_lite_internal_core_C4Database
 * Method:    getLastSequence
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getLastSequence(JNIEnv *env, jclass ignore, jlong jdb) {
    return (jlong) c4db_getLastSequence((C4Database *) jdb);
}

// !!! DEPRECATED
// Delete these methods when the corresponding Java methods proxy to the default collection

// - Documents

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getDocumentCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getDocumentCount(JNIEnv *env, jclass ignore, jlong jdb) {
    return (jlong) c4db_getDocumentCount((C4Database *) jdb);
}

// document get/create methods are in native_c4document

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    setDocumentExpiration
 * Signature: (JLjava/lang/String;J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_setDocumentExpiration(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jstring jdocID,
        jlong jtimestamp) {
    jstringSlice docID(env, jdocID);
    C4Error error{};
    if (!c4doc_setExpiration((C4Database *) jdb, docID, jtimestamp, &error))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getDocumentExpiration
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getDocumentExpiration(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jstring jdocID) {
    jstringSlice docID(env, jdocID);
    C4Error error{};
    jlong exp = c4doc_getExpiration((C4Database *) jdb, docID, &error);
    if (exp < 0) {
        throwError(env, error);
        return 0;
    }
    return exp;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    purgeDoc
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_purgeDoc(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jstring jdocID) {
    jstringSlice docID(env, jdocID);
    C4Error error{};
    if (!c4db_purgeDoc((C4Database *) jdb, docID, &error))
        throwError(env, error);
}


///// Indexes

/*
 * Class:     Java_com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    getIndexesInfoForDb
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_getIndexesInfo(JNIEnv *env, jclass ignore, jlong jdb) {
    C4SliceResult data = c4db_getIndexesInfo((C4Database *) jdb, nullptr);
    return (jlong) FLValue_FromData({data.buf, data.size}, kFLTrusted);
}

/*
 * Class:     Java_com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    createIndexForDb
 * Signature: (JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;Z)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_createIndex(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jstring jname,
        jstring jqueryExpressions,
        jint queryLanguage,
        jint indexType,
        jstring jlanguage,
        jboolean ignoreDiacritics) {
    jstringSlice name(env, jname);
    jstringSlice queryExpressions(env, jqueryExpressions);
    jstringSlice language(env, jlanguage);

    C4IndexOptions options = {};
    options.language = language.c_str();
    options.ignoreDiacritics = (bool) ignoreDiacritics;

    C4Error error{};
    bool res = c4db_createIndex2(
            (C4Database *) db,
            name,
            (C4Slice) queryExpressions,
            (C4QueryLanguage) queryLanguage,
            (C4IndexType) indexType,
            &options,
            &error);
    if (!res)
        throwError(env, error);
}

/*
 * Class:     Java_com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    deleteIndexForDb
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_deleteIndex(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jstring jname) {
    jstringSlice name(env, jname);
    C4Error error{};
    bool res = c4db_deleteIndex((C4Database *) jdb, name, &error);
    if (!res)
        throwError(env, error);
}
// end deprecation
}
