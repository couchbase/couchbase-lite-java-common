//
// native_c4document.cc
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
#include <algorithm>
#include <vector>
#include "c4Base.h"
#include "native_glue.hh"
#include "com_couchbase_lite_internal_core_impl_NativeC4Document.h"

using namespace litecore;
using namespace litecore::jni;

extern "C" {

// - Collection Constructors:

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getFromCollection
 * Signature: (JLjava/lang/String;ZZ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getFromCollection(
        JNIEnv *env,
        jclass ignore,
        jlong coll,
        jstring jDocId,
        jboolean mustExist,
        jboolean allRevs) {
    jstringSlice docId(env, jDocId);

    C4Error error{};
    C4Document *doc = c4coll_getDoc(
            (C4Collection *) coll,
            docId,
            mustExist == JNI_TRUE,
            (allRevs == JNI_TRUE) ? kDocGetAll : kDocGetCurrentRev,
            &error);

    if ((doc == nullptr) && (error.code != 0)) {

        // Ignore LiteCore's annoying "not found" error
        if ((error.domain == LiteCoreDomain) && (error.code == kC4ErrorNotFound)) {
            return (jlong) doc;
        }

        throwError(env, error);
        return 0;
    }

    return (jlong) doc;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    createFromSLice
 * Signature: (JLjava/lang/String;JJI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_createFromSlice(
        JNIEnv *env,
        jclass ignore,
        jlong jcollection,
        jstring jdocID,
        jlong jbodyPtr,
        jlong jbodySize,
        jint flags) {
    jstringSlice docID(env, jdocID);
    C4Slice body{(const void *) jbodyPtr, (size_t) jbodySize};
    C4Error error{};
    C4Document *doc = c4coll_createDoc((C4Collection *) jcollection, docID, body, (unsigned) flags, &error);
    if (doc == nullptr) {
        throwError(env, error);
        return 0;
    }

    return (jlong) doc;
}

// - Properties

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getFlags
 * Signature: (J)I
 *
 * This is a uint-32.  It should be a jlong.
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getFlags(
        JNIEnv *ignore1,
        jclass ignore2,
        jlong jdoc) {
    auto doc = (C4Document *) jdoc;
    return (jint) doc->flags;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getRevID
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getRevID(
        JNIEnv *env,
        jclass ignore,
        jlong jdoc) {
    auto doc = (C4Document *) jdoc;
    return toJString(env, doc->revID);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSequence
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getSequence(
        JNIEnv *ignore1,
        jclass ignore2,
        jlong jdoc) {
    auto doc = (C4Document *) jdoc;
    return (jlong) doc->sequence;
}


// - Revisions

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSelectedFlags
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getSelectedFlags(
        JNIEnv *ignore1,
        jclass ignore2,
        jlong jdoc) {
    auto doc = (C4Document *) jdoc;
    return doc->selectedRev.flags;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSelectedRevID
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getSelectedRevID(
        JNIEnv *env,
        jclass ignore,
        jlong jdoc) {
    auto doc = (C4Document *) jdoc;
    return toJString(env, doc->selectedRev.revID);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getRevisionHistory
 * Signature: (JJJ[Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getRevisionHistory(
        JNIEnv *env,
        jclass ignore,
        jlong jcoll,
        jlong jdoc,
        jlong maxRevs,
        jobjectArray jBackToRevs) {
    C4String *backToRevs = nullptr;
    unsigned int nBackToRevs = 0;

    // if jBackToRevs is non-null, a Java String[], convert it to a C array of C4Slice:
    if (jBackToRevs != nullptr) {
        jsize n = env->GetArrayLength(jBackToRevs);
        std::vector<C4Slice> b2r(n);
        std::vector<jstringSlice *> b2rAlloc;
        for (jsize i = 0; i < n; i++) {
            auto js = (jstring) env->GetObjectArrayElement(jBackToRevs, i);
            auto *item = new jstringSlice(env, js);
            b2rAlloc.push_back(item); // so its memory won't be freed at the end of the block
            b2r[i] = *item;
        }

        backToRevs = b2r.data();
        nBackToRevs = b2r.size();
    }

    auto *doc = (C4Document *) jdoc;

    C4Error error{};
    C4Document* allDoc = c4coll_getDoc((C4Collection*) jcoll, doc->docID, false, kDocGetAll, &error);
    if ((allDoc == nullptr) && (error.code != 0)) {
        throwError(env, error);
        return nullptr;
    }

    FLSliceResult revHistory = c4doc_getRevisionHistory(allDoc, (unsigned int) maxRevs, backToRevs, nBackToRevs);

    jstring res = toJString(env, revHistory);
    FLSliceResult_Release(revHistory);

    return res;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSelectedRevID
 * Signature: (J)J;
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getTimestamp(
        JNIEnv *ignore1,
        jclass ignore2,
        jlong jdoc) {
    auto doc = (C4Document *) jdoc;
    FLHeapSlice revId = doc->selectedRev.revID;
    return (jlong) c4rev_getTimestamp(revId);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSelectedSequence
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getSelectedSequence(
        JNIEnv *ignore1,
        jclass ignore2,
        jlong jdoc) {
    auto doc = (C4Document *) jdoc;
    return (jlong) doc->selectedRev.sequence;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    getSelectedBody2
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_getSelectedBody2(
        JNIEnv *ignore1,
        jclass ignore2,
        jlong jdoc) {
    C4Slice body = c4doc_getRevisionBody((C4Document *) jdoc);
    if (body.size == 0)
        return (jlong) nullptr;

    FLValue data = FLValue_FromData(body, kFLTrusted);

    return (jlong) FLValue_AsDict(data);
}

// - Conflict resolution

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    selectNextLeafRevision
 * Signature: (JZZ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_selectNextLeafRevision(
        JNIEnv *env,
        jclass ignore,
        jlong jdoc,
        jboolean jincludeDeleted,
        jboolean jwithBody) {
    C4Error error{};
    bool ok = c4doc_selectNextLeafRevision((C4Document *) jdoc, jincludeDeleted, jwithBody, &error);
    if (!ok)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    resolveConflict
 * Signature: (JLjava/lang/String;Ljava/lang/String;[BI)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_resolveConflict(
        JNIEnv *env,
        jclass ignore,
        jlong jdoc,
        jstring jWinningRevID,
        jstring jLosingRevID,
        jbyteArray jMergedBody,
        jint jMergedFlags) {
    jstringSlice winningRevID(env, jWinningRevID);
    jstringSlice losingRevID(env, jLosingRevID);
    jbyteArraySlice mergedBody(env, jMergedBody);
    auto revisionFlag = (C4RevisionFlags) jMergedFlags;
    C4Error error{};
    bool ok = c4doc_resolveConflict((C4Document *) jdoc, winningRevID, losingRevID, mergedBody, revisionFlag, &error);
    if (!ok)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    update2
 * Signature: (JJJI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_update2(
        JNIEnv *env,
        jclass ignore,
        jlong jdoc,
        jlong jbodyPtr,
        jlong jbodySize,
        jint flags) {
    auto doc = (C4Document *) jdoc;
    if (doc == nullptr) {
        throwError(env, {LiteCoreDomain, kC4ErrorAssertionFailed});
        return 0;
    }

    C4Slice body{(const void *) jbodyPtr, (size_t) jbodySize};

    C4Error error{};
    C4Document *newDoc = c4doc_update((C4Document *) jdoc, body, (unsigned) flags, &error);
    if (newDoc == nullptr) {
        throwError(env, error);
        return 0;
    }

    return (jlong) newDoc;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    save
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_save(
        JNIEnv *env,
        jclass ignore,
        jlong jdoc,
        jint maxRevTreeDepth) {
    C4Error error{};
    bool ok = c4doc_save((C4Document *) jdoc, (uint16_t) maxRevTreeDepth, &error);
    if (!ok)
        throwError(env, error);
}

// - Fleece

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    bodyAsJSON
 * Signature: (JZ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_bodyAsJSON(
        JNIEnv *env,
        jclass ignore,
        jlong jdoc,
        jboolean canonical) {
    C4Error error{};
    C4StringResult res = c4doc_bodyAsJSON((C4Document *) jdoc, canonical, &error);
    if (!res) {
        throwError(env, error);
        return nullptr;
    }

    jstring jstr = toJString(env, res);

    c4slice_free(res);

    if (jstr == nullptr) {
        throwError(env, {LiteCoreDomain, kC4ErrorCorruptData});
        return nullptr;
    }

    return jstr;
}

// - Lifecycle

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Document
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Document_free(
        JNIEnv *ignore1,
        jclass ignore2,
        jlong jdoc) {
    c4doc_release((C4Document *) jdoc);
}

}
