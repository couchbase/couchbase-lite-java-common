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
#include "com_couchbase_lite_internal_core_impl_NativeC4Collection.h"
#include "c4DatabaseTypes.h"
#include "c4Collection.h"

extern "C" {

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDefaultCollection
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDefaultCollection
        (JNIEnv *env, jclass ignore, jlong db) {
    // C4Collection* c4db_getDefaultCollection(C4Database *db)
    return 0x8BADF00D;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getCollection
        (JNIEnv *env, jclass ignore, jlong db, jstring scope, jstring collection) {
    // C4Collection* C4NULLABLE c4db_getCollection(C4Database *db, C4CollectionSpec spec)
    return 0x8BADF00D;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createCollection
 * Signature: (JLjava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createCollection
        (JNIEnv *, jclass, jlong, jstring, jstring) {
    // C4Collection* C4NULLABLE c4db_createCollection(C4Database *db, C4CollectionSpec spec, C4Error* C4NULLABLE outError)
    return 0x8BADF00D;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    isValid
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_isValid
        (JNIEnv *, jclass, jlong) {
    return JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDatabase
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDatabase
        (JNIEnv *, jclass, jlong) {
    return 0L;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDocumentCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocumentCount
        (JNIEnv *, jclass, jlong) {
    return 0L;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getLastSequence
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getLastSequence
        (JNIEnv *env, jclass ingnore, jlong collection) {
    return (jlong) c4coll_getLastSequence((C4Collection *) collection);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDoc
 * Signature: (JLjava/lang/String;Z)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDoc
        (JNIEnv *, jclass, jlong, jstring, jboolean) {
    return 0L;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDocBySequence
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocBySequence
        (JNIEnv *, jclass, jlong, jlong) {
    return 0L;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    putDoc
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_putDoc
        (JNIEnv *, jclass, jlong, jlong) {
    return 0L;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createDoc
 * Signature: (JLjava/lang/String;[BI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createDoc
        (JNIEnv *, jclass, jlong, jstring, jbyteArray, jint) {
    return 0L;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    moveDoc
 * Signature: (JLjava/lang/String;JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_moveDoc
        (JNIEnv *, jclass, jlong, jstring, jlong, jstring) {
    return JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    purgeDoc
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_purgeDoc
        (JNIEnv *, jclass, jlong, jstring) {
    return JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    deleteDoc
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_deleteDoc
        (JNIEnv *, jclass, jlong, jstring) {
    return JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    setDocExpiration
 * Signature: (JLjava/lang/String;J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_setDocExpiration
        (JNIEnv *, jclass, jlong, jstring, jlong) {
    return JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getDocExpiration
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getDocExpiration
        (JNIEnv *, jclass, jlong, jstring) {
    return 0L;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    nextDocExpiration
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_nextDocExpiration
        (JNIEnv *, jclass, jlong) {
    return 0L;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    purgeExpiredDocs
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_purgeExpiredDocs
        (JNIEnv *, jclass, jlong) {
    return 0L;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    getIndexesInfo
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_getIndexesInfo
        (JNIEnv *, jclass, jlong) {
    // c4coll_getIndexesInfo
    return 0L;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    createIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;II[B)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_createIndex
        (JNIEnv *, jclass, jlong, jstring, jstring, jint, jint, jstring, jboolean) {
    // c4coll_createIndex
    return JNI_FALSE;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Collection
 * Method:    deleteIndex
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Collection_deleteIndex
        (JNIEnv *, jclass, jlong, jstring) {
    // c4coll_deleteIndex
    return JNI_FALSE;
}
}