//
// native_c4fulltextmatch.cc
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
#include "com_couchbase_lite_internal_core_C4FullTextMatch.h"

using namespace litecore;
using namespace litecore::jni;

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4FullTextMatch
// ----------------------------------------------------------------------------

extern "C" {

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    dataSource
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4FullTextMatch_dataSource(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
    if (!handle) return 0L;
    return (jlong) ((C4FullTextMatch *) handle)->dataSource;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    property
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4FullTextMatch_property(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
    if (!handle) return 0L;
    return (jlong) ((C4FullTextMatch *) handle)->property;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    term
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4FullTextMatch_term(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
    if (!handle) return 0L;
    return (jlong) ((C4FullTextMatch *) handle)->term;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    start
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4FullTextMatch_start(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
    if (!handle) return 0L;
    return (jlong) ((C4FullTextMatch *) handle)->start;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    length
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4FullTextMatch_length(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
    if (!handle) return 0L;
    return (jlong) ((C4FullTextMatch *) handle)->length;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch
 * Method:    getFullTextMatchCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4FullTextMatch_getFullTextMatchCount(
        JNIEnv *env,
        jclass ignore,
        jlong handle) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return 0L;

    return (jlong) e->fullTextMatchCount;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4FullTextMatch_getFullTextMatch
 * Method:    getFullTextMatch
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4FullTextMatch_getFullTextMatch(
        JNIEnv *env,
        jclass ignore,
        jlong handle,
        jint jidx) {
    auto e = (C4QueryEnumerator *) handle;
    if (e == nullptr)
        return 0L;

    return (jlong) &(e->fullTextMatches[(int) jidx]);
}
}