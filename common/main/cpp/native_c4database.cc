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
#include "native_glue.hh"
#include "com_couchbase_lite_internal_core_impl_NativeC4Database.h"

using namespace litecore;
using namespace litecore::jni;

extern "C" {


// - Lifecycle

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    open
 * Signature: (Ljava/lang/String;Ljava/lang/String;JI[B)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_open(
        JNIEnv *env,
        jclass ignore,
        jstring jparentDir,
        jstring jname,
        jlong jflags,
        jint encryptionAlg,
        jbyteArray encryptionKey) {
    jstringSlice parentDir(env, jparentDir);
    jstringSlice name(env, jname);

    C4DatabaseConfig2 config;
    config.parentDirectory = parentDir;
    config.flags = (C4DatabaseFlags) jflags;
    bool ok = getEncryptionKey(env, encryptionAlg, encryptionKey, &config.encryptionKey);
    if (!ok)
        return 0;

    C4Error error{};
    C4Database *db = c4db_openNamed(name, &config, &error);
    if ((db == nullptr) && (error.code != 0)) {
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
    bool ok = c4db_close((C4Database *) jdb, &error);
    if (!ok && error.code != 0)
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
        jlong jflags,
        jint encryptionAlg,
        jbyteArray encryptionKey) {
    jstringSlice fromPath(env, jfromPath);
    jstringSlice parentDir(env, jparentDir);
    jstringSlice name(env, jname);

    C4DatabaseConfig2 config;
    config.parentDirectory = parentDir;
    config.flags = (C4DatabaseFlags) jflags;
    bool ok = getEncryptionKey(env, encryptionAlg, encryptionKey, &config.encryptionKey);
    if (!ok)
        return;

    C4Error error{};
    ok = c4db_copyNamed(fromPath, name, &config, &error);
    if (!ok && error.code != 0)
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
    bool ok = c4db_delete((C4Database *) jdb, &error);
    if (!ok && error.code != 0)
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
        jstring jparentDir,
        jstring jname) {
    jstringSlice parentDir(env, jparentDir);
    jstringSlice name(env, jname);

    C4Error error{};
    bool ok = c4db_deleteNamed(name, parentDir, &error);
    if (!ok && error.code != 0)
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
    bool ok = c4db_getUUIDs((C4Database *) jdb, &uuid, nullptr, &error);
    if (!ok && error.code != 0)
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
    bool ok = c4db_beginTransaction((C4Database *) jdb, &error);
    if (!ok && error.code != 0)
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
    bool ok = c4db_endTransaction((C4Database *) jdb, jcommit != JNI_FALSE, &error);
    if (!ok && error.code != 0)
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
    bool ok = getEncryptionKey(env, encryptionAlg, encryptionKey, &key);
    if (!ok)
        return;

    C4Error error{};
    ok = c4db_rekey((C4Database *) jdb, &key, &error);
    if (!ok && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    maintenance
 * Signature: (JI)J
 *
 * ??? Does this method ever actually return false without throwing an exception?
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_maintenance(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jint type) {
    C4Error error{};
    bool ok = c4db_maintenance((C4Database *) db, (C4MaintenanceType) type, &error);
    if (!ok && error.code != 0) {
        throwError(env, error);
        return JNI_FALSE;
    }

    return ok ? JNI_TRUE : JNI_FALSE;
}


// - Cookies

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Database
 * Method:    setCookie
 * Signature: (JLjava/lang/String;Ljava/lang/String;Z)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_setCookie(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jstring jurl,
        jstring jcookie,
        jboolean acceptParentDomain) {
    jstringSlice url(env, jurl);
    jstringSlice cookie(env, jcookie);

    C4Address address;
    bool ok = c4address_fromURL(url, &address, nullptr);
    if (!ok) {
        throwError(env, {NetworkDomain, kC4NetErrInvalidURL});
        return;
    }

    C4Error error{};
    ok = c4db_setCookie(
            (C4Database *) jdb,
            cookie,
            address.hostname,
            address.path,
            acceptParentDomain != JNI_FALSE,
            &error);
    if (!ok && error.code != 0)
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
    bool ok = c4address_fromURL(url, &address, nullptr);
    if (!ok) {
        throwError(env, {NetworkDomain, kC4NetErrInvalidURL});
        return nullptr;
    }

    C4Error error{};
    C4StringResult res = c4db_getCookies((C4Database *) jdb, address, &error);
    if (!res) {
        throwError(env, error);
        return nullptr;
    }

    jstring cookies = toJString(env, res);
    c4slice_free(res);
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
 * Method:    docContainsBlobs
 * Signature: (JJJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Database_docContainsBlobs(
        JNIEnv *ignore1,
        jclass ignore2,
        jlong jbodyPtr,
        jlong jbodySize,
        jlong sharedKeys) {
    FLSliceResult body{(const void *) jbodyPtr, (size_t) jbodySize};
    FLDoc doc = FLDoc_FromResultData(body, kFLTrusted, (FLSharedKeys) sharedKeys, kFLSliceNull);
    const auto *const dict = (FLDict) FLDoc_GetRoot(doc);
    bool containsBlobs = c4doc_dictContainsBlobs(dict);
    FLDoc_Release(doc);
    return containsBlobs ? JNI_TRUE : JNI_FALSE;
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
    FLMutableArray scopes = c4db_scopeNames((C4Database *) db, &error);
    if ((scopes == nullptr) && (error.code != 0)) {
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
    return c4db_hasScope((C4Database *) db, scope) ? JNI_TRUE : JNI_FALSE;
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
    FLMutableArray collections = c4db_collectionNames((C4Database *) db, scope, &error);
    if ((collections == nullptr) && (error.code != 0)) {
        throwError(env, error);
        return nullptr;
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
    bool ok = c4db_deleteCollection((C4Database *) db, collSpec, &error);
    if (!ok && error.code != 0)
        throwError(env, error);
}
}
