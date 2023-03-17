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

/*
 * Class:     com_couchbase_lite_internal_core_C4TestUtils
 * Method:    getPrivateUUID
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_C4TestUtils_getPrivateUUID(JNIEnv *env, jclass ignore, jlong jdb) {
    C4UUID uuid;

    C4Error error{};
    bool res = c4db_getUUIDs((C4Database *) jdb, nullptr, &uuid, &error);
    if (!res && error.code != 0)
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
    jbyteArraySlice body(env, jbody, false);

    C4Error error{};
    C4SliceResult res = c4db_encodeJSON((C4Database *) db, (C4Slice) body, &error);
    if (!res && error.code != 0) {
        throwError(env, error);
        return nullptr;
    }

    return toJavaFLSliceResult(env, res);
}

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
    if (!e) {
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
    bool res = c4enum_next((C4DocEnumerator *) handle, &error);
    if (!res && error.code != 0) {
        throwError(env, error);
        return false;
    }
    return (jboolean) res;
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
    if (!doc) {
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
    jbyteArraySlice body(env, jbody, false);

    C4DocPutRequest rq{};
    rq.body = body;                         ///< Revision's body
    rq.docID = docID;                       ///< Document ID
    rq.revFlags = revFlags;                 ///< Revision flags (deletion, attachments, keepBody)
    rq.existingRevision = existingRevision; ///< Is this an already-existing rev coming from replication?
    rq.allowConflict = allowConflict;       ///< OK to create a conflict, i.e. can parent be non-leaf?
    rq.history = nullptr;                   ///< Array of ancestor revision IDs
    rq.historyCount = 0;                    ///< Size of history[] array
    rq.save = save;                         ///< Save the document after inserting the revision?
    rq.maxRevTreeDepth = maxRevTreeDepth;   ///< Max depth of revision tree to save (or 0 for default)
    rq.remoteDBID = (C4RemoteID) remoteDBID; ///< Identifier of remote db this rev's from (or 0 if local)

    // history
    // Convert jhistory, a Java String[], to a C array of C4Slice:
    jsize n = env->GetArrayLength(jhistory);
    if (env->EnsureLocalCapacity(std::min(n + 1, MaxLocalRefsToUse)) < 0)
        return -1;
    std::vector<C4Slice> history(n);
    std::vector<jstringSlice *> historyAlloc;
    for (jsize i = 0; i < n; i++) {
        auto js = (jstring) env->GetObjectArrayElement(jhistory, i);
        auto item = new jstringSlice(env, js);
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

    if (!doc) {
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

    // Parameters for adding a revision using c4doc_put.
    C4DocPutRequest rq{};
    rq.body.buf = (const void *) jbodyPtr;  ///< Revision's body
    rq.body.size = (size_t) jbodySize;      ///< Revision's body
    rq.docID = docID;                       ///< Document ID
    rq.revFlags = revFlags;                 ///< Revision flags (deletion, attachments, keepBody)
    rq.existingRevision = existingRevision; ///< Is this an already-existing rev coming from replication?
    rq.allowConflict = allowConflict;       ///< OK to create a conflict, i.e. can parent be non-leaf?
    rq.history = nullptr;                   ///< Array of ancestor revision IDs
    rq.historyCount = 0;                    ///< Size of history[] array
    rq.save = save;                         ///< Save the document after inserting the revision?
    rq.maxRevTreeDepth = maxRevTreeDepth;   ///< Max depth of revision tree to save (or 0 for default)
    rq.remoteDBID = (C4RemoteID) remoteDBID; ///< Identifier of remote db this rev's from (or 0 if local)

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
            auto item = new jstringSlice(env, js);
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

    if (!doc) {
        throwError(env, error);
        return 0;
    }

    return (jlong) doc;
}
}
