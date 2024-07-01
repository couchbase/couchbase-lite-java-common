//
// native_c4testutils.cc
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
#include <vector>

#include "native_glue.hh"
#include "com_couchbase_lite_internal_core_C4TestUtils.h"

using namespace litecore;
using namespace litecore::jni;

extern "C" {

// C4DocEnumerator

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    enumerateAllDocs
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_enumerateAllDocs(
        JNIEnv *env,
        jclass ignore,
        jlong jcollection,
        jint jflags) {
    const C4EnumeratorOptions options = {C4EnumeratorFlags(jflags)};
    C4Error error{};
    C4DocEnumerator *e = c4coll_enumerateAllDocs((C4Collection *) jcollection, &options, &error);
    if (e == nullptr) {
        throwError(env, error);
        return 0;
    }
    return (jlong) e;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_next(JNIEnv *env, jclass ignore, jlong handle) {
    C4Error error{};
    bool ok = c4enum_next((C4DocEnumerator *) handle, &error);
    if (!ok && error.code != 0) {
        throwError(env, error);
        return false;
    }
    return (jboolean) ok;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getDocument
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_getDocument(JNIEnv *env, jclass ignore, jlong handle) {
    C4Error error{};
    C4Document *doc = c4enum_getDocument((C4DocEnumerator *) handle, &error);
    if (doc == nullptr) {
        throwError(env, error);
        return 0;
    }
    return (jlong) doc;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_free(JNIEnv *env, jclass ignore, jlong handle) {
    c4enum_free((C4DocEnumerator *) handle);
}

// C4BlobStore

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    openStore
 * Signature: (Ljava/lang/String;J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_openStore(
        JNIEnv *env,
        jclass ignore,
        jstring jdirpath,
        jlong jflags) {
    jstringSlice dirPath(env, jdirpath);
    C4Error error{};
    // TODO: Need to work for encryption
    C4BlobStore *store = c4blob_openStore(dirPath, (C4DatabaseFlags) jflags, nullptr, &error);
    if (store == nullptr) {
        throwError(env, error);
        return 0;
    }
    return (jlong) store;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    deleteStore
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_deleteStore(JNIEnv *env, jclass ignore, jlong jblobstore) {
    C4Error error{};
    bool ok = c4blob_deleteStore((C4BlobStore *) jblobstore, &error);
    if (!ok)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    freeStore
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_freeStore(JNIEnv *env, jclass ignore, jlong jblobstore) {
    c4blob_freeStore((C4BlobStore *) jblobstore);
}

// C4Database

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getPrivateUUID
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_getPrivateUUID(JNIEnv *env, jclass ignore, jlong jdb) {
    C4UUID uuid;

    C4Error error{};
    bool ok = c4db_getUUIDs((C4Database *) jdb, nullptr, &uuid, &error);
    if (!ok && error.code != 0)
        throwError(env, error);

    C4Slice s = {&uuid, sizeof(uuid)};
    return toJByteArray(env, s);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    encodeJSON
 * Signature: (J[B)Lcom/couchbase/lite/internal/fleece/FLSliceResult;
 */
JNIEXPORT jobject JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_encodeJSON(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jbyteArray jbody) {
    jbyteArraySlice body(env, jbody);

    C4Error error{};
    C4SliceResult res = c4db_encodeJSON((C4Database *) db, (C4Slice) body, &error);
    if (error.domain != 0 && error.code != 0) {
        throwError(env, error);
        return nullptr;
    }

    return toJavaFLSliceResult(env, res);
}

// C4Document

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getDocID
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_getDocID(JNIEnv *env, jclass ignore, jlong jdoc) {
    auto doc = (C4Document *) jdoc;
    return toJString(env, doc->docID);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    put
 * Signature: (J[BLjava/lang/String;IZZ[Ljava/lang/String;ZII)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_put(
        JNIEnv *env,
        jclass ignore,
        jlong jcollection,
        jbyteArray jbody,
        jstring jdocID,
        jint revFlags,
        jboolean existingRevision,
        jboolean allowConflict,
        jobjectArray jhistory,
        jboolean save,
        jint maxRevTreeDepth,
        jint remoteDBID) {

    auto collection = (C4Collection *) jcollection;
    jstringSlice docID(env, jdocID);
    jbyteArraySlice body(env, jbody);

    C4DocPutRequest rq{};
    rq.body = body;                          // Revision's body
    rq.docID = docID;                        // Document ID
    rq.revFlags = revFlags;                  // Revision flags (deletion, attachments, keepBody)
    rq.existingRevision = existingRevision;  // Is this an already-existing rev coming from replication?
    rq.allowConflict = allowConflict;        // OK to create a conflict, i.e. can parent be non-leaf?
    rq.history = nullptr;                    // Array of ancestor revision IDs
    rq.historyCount = 0;                     // Size of history[] array
    rq.save = save;                          // Save the document after inserting the revision?
    rq.maxRevTreeDepth = maxRevTreeDepth;    // Max depth of revision tree to save (or 0 for default)
    rq.remoteDBID = (C4RemoteID) remoteDBID; // Identifier of remote db this rev's from (or 0 if local)

    // history
    // Convert jhistory, a Java String[], to a C array of C4Slice:
    jsize n = env->GetArrayLength(jhistory);
    if (env->EnsureLocalCapacity(std::min(n + 1, MaxLocalRefsToUse)) < 0)
        return -1;
    std::vector<C4Slice> history(n);
    std::vector<jstringSlice *> historyAlloc;
    for (jsize i = 0; i < n; i++) {
        auto js = (jstring) env->GetObjectArrayElement(jhistory, i);
        auto *item = new jstringSlice(env, js);
        historyAlloc.push_back(item); // so its memory won't be freed
        history[i] = *item;
    }
    rq.history = history.data();
    rq.historyCount = history.size();

    size_t commonAncestorIndex;
    C4Error error{};
    C4Document *doc = c4coll_putDoc(collection, &rq, &commonAncestorIndex, &error);

    // release memory
    for (jsize i = 0; i < n; i++)
        delete historyAlloc.at(i);

    if (doc == nullptr) {
        throwError(env, error);
        return 0;
    }

    return (jlong) doc;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    put2
 * Signature: (JJJLjava/lang/String;IZZ[Ljava/lang/String;ZII)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_put2(
        JNIEnv *env,
        jclass ignore,
        jlong jcollection,
        jlong jbodyPtr,
        jlong jbodySize,
        jstring jdocID,
        jint revFlags,
        jboolean existingRevision,
        jboolean allowConflict,
        jobjectArray jhistory,
        jboolean save,
        jint maxRevTreeDepth,
        jint remoteDBID) {
    auto collection = (C4Collection *) jcollection;
    jstringSlice docID(env, jdocID);

    // Parameters for adding a revision using c4coll_put.
    C4DocPutRequest rq{};
    rq.body.buf = (const void *) jbodyPtr;   // Revision's body
    rq.body.size = (size_t) jbodySize;       // Revision's body
    rq.docID = docID;                        // Document ID
    rq.revFlags = revFlags;                  // Revision flags (deletion, attachments, keepBody)
    rq.existingRevision = existingRevision;  // Is this an already-existing rev coming from replication?
    rq.allowConflict = allowConflict;        // OK to create a conflict, i.e. can parent be non-leaf?
    rq.history = nullptr;                    // Array of ancestor revision IDs
    rq.historyCount = 0;                     // Size of history[] array
    rq.save = save;                          // Save the document after inserting the revision?
    rq.maxRevTreeDepth = maxRevTreeDepth;    // Max depth of revision tree to save (or 0 for default)
    rq.remoteDBID = (C4RemoteID) remoteDBID; // Identifier of remote db this rev's from (or 0 if local)

    // history
    // Convert jhistory, a Java String[], to a C array of C4Slice:
    jsize n = env->GetArrayLength(jhistory);
    if (env->EnsureLocalCapacity(std::min(n + 1, MaxLocalRefsToUse)) < 0)
        return -1;
    std::vector<C4Slice> history(n);
    std::vector<jstringSlice *> historyAlloc;
    if (n > 0) {
        for (jsize i = 0; i < n; i++) {
            auto js = (jstring) env->GetObjectArrayElement(jhistory, i);
            auto *item = new jstringSlice(env, js);
            historyAlloc.push_back(item); // so its memory won't be freed
            history[i] = *item;
        }
        rq.history = history.data();
        rq.historyCount = history.size();
    }

    size_t commonAncestorIndex;
    C4Error error{};
    C4Document *doc = c4coll_putDoc(collection, &rq, &commonAncestorIndex, &error);

    // release memory
    for (jsize i = 0; i < n; i++)
        delete historyAlloc.at(i);

    if (doc == nullptr) {
        throwError(env, error);
        return 0;
    }

    return (jlong) doc;
}

// C4Key

/*
 * Class:     Java_com_couchbase_lite_internal_core_C4TestUtils
 * Method:    deriveKeyFromPassword
 * Signature: (Ljava/lang/String;I)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_deriveKeyFromPassword(JNIEnv *env, jclass ignore, jstring password) {
    jstringSlice pwd(env, password);

    C4EncryptionKey key;
    bool ok = c4key_setPassword(&key, pwd, kC4EncryptionAES256);
    if (!ok)
        return nullptr;

    int keyLen = sizeof(key.bytes);
    jbyteArray result = env->NewByteArray(keyLen);
    env->SetByteArrayRegion(result, 0, keyLen, (jbyte *) &key.bytes);

    return result;
}

// C4Log

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getLevel
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_getLevel(JNIEnv *env, jclass ignore, jstring jdomain) {
    jstringSlice domain(env, jdomain);
    C4LogDomain logDomain = c4log_getDomain(domain.c_str(), false);
    return (!logDomain) ? -1 : (jint) c4log_getLevel(logDomain);
}

// C4Collection

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    isIndexTrained
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_isIndexTrained(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jname) {
    jstringSlice name(env, jname);

    C4Error error{};
    bool ok = c4coll_isIndexTrained((C4Collection *) coll, name, &error);
    if (error.domain != 0 && error.code != 0) {
        throwError(env, error);
        return JNI_FALSE;
    }

    return ok ? JNI_TRUE : JNI_FALSE;
}
}
