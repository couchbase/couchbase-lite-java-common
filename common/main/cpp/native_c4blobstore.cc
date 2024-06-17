//
// native_c4blobstore.cc
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
#include "com_couchbase_lite_internal_core_impl_NativeC4Blob.h"

using namespace litecore;
using namespace litecore::jni;

extern "C" {

//// BlobKey
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    getBlobStore
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_getBlobStore(JNIEnv *env, jclass ignore, jlong jdb) {
    C4Error error{};
    C4BlobStore *store = c4db_getBlobStore((C4Database *) jdb, &error);
    if (store == nullptr) {
        throwError(env, error);
        return 0;
    }
    return (jlong) store;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    fromString
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_fromString(JNIEnv *env, jclass ignore, jstring jstr) {
    jstringSlice str(env, jstr);
    auto pBlobKey = (C4BlobKey *) ::malloc(sizeof(C4BlobKey));
    bool ok = c4blob_keyFromString(str, pBlobKey);
    if (!ok) {
        ::free(pBlobKey);
        throwError(env, {LiteCoreDomain, 0});
        return 0L;
    }
    return (jlong) pBlobKey;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    toString
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_toString(JNIEnv *env, jclass ignore, jlong jblobkey) {
    auto pBlobKey = (C4BlobKey *) jblobkey;
    C4StringResult result = c4blob_keyToString(*pBlobKey);
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_free(JNIEnv *env, jclass ignore, jlong jblobkey) {
    ::free((C4BlobKey *) jblobkey);
}

//// BlobStore
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    getSize
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_getSize(
        JNIEnv *env,
        jclass ignore,
        jlong jblobstore,
        jlong jblobkey) {
    auto pBlobKey = (C4BlobKey *) jblobkey;
    return (jlong) c4blob_getSize((C4BlobStore *) jblobstore, *pBlobKey);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    getContents
 * Signature: (JJ)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_getContents(
        JNIEnv *env,
        jclass ignore,
        jlong jblobstore,
        jlong jblobkey) {
    auto pBlobKey = (C4BlobKey *) jblobkey;

    C4Error error{};
    C4SliceResult res = c4blob_getContents((C4BlobStore *) jblobstore, *pBlobKey, &error);
    if (error.domain != 0 && error.code != 0) {
        throwError(env, error);
        return nullptr;
    }

    jbyteArray content = toJByteArray(env, res);

    FLSliceResult_Release(res);

    return content;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    getFilePath
 * Signature: (JJ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_getFilePath(
        JNIEnv *env,
        jclass ignore,
        jlong jblobstore,
        jlong jblobkey) {
    auto pBlobKey = (C4BlobKey *) jblobkey;

    C4Error error{};
    C4StringResult res = c4blob_getFilePath((C4BlobStore *) jblobstore, *pBlobKey, &error);
    if (error.domain != 0 && error.code != 0) {
        throwError(env, error);
        return nullptr;
    }

    jstring ret = toJString(env, res);
    c4slice_free(res);
    return ret;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    create
 * Signature: (J[B)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_create(
        JNIEnv *env,
        jclass ignore,
        jlong jblobstore,
        jbyteArray jcontents) {
    jbyteArraySlice ccontents(env, jcontents);

    C4BlobKey blobKey;
    C4Error error{};
    bool ok = c4blob_create((C4BlobStore *) jblobstore, ccontents, nullptr, &blobKey, &error);
    if (!ok) {
        throwError(env, error);
        return 0;
    }

    auto pBlobKey = (C4BlobKey *) ::malloc(sizeof(C4BlobKey));
    *pBlobKey = blobKey;
    return (jlong) pBlobKey;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    delete
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_delete(
        JNIEnv *env,
        jclass ignore,
        jlong jblobstore,
        jlong jblobkey) {
    auto pBlobKey = (C4BlobKey *) jblobkey;
    C4Error error{};
    bool ok = c4blob_delete((C4BlobStore *) jblobstore, *pBlobKey, &error);
    if (!ok)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    openReadStream
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_openReadStream(
        JNIEnv *env,
        jclass ignore,
        jlong jblobstore,
        jlong jblobkey) {
    auto pBlobKey = (C4BlobKey *) jblobkey;
    C4Error error{};
    C4ReadStream *stream = c4blob_openReadStream((C4BlobStore *) jblobstore, *pBlobKey, &error);
    if (stream == nullptr) {
        throwError(env, error);
        return 0;
    }
    return (jlong) stream;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    openWriteStream
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_openWriteStream(JNIEnv *env, jclass ignore, jlong jblobstore) {
    C4Error error{};
    C4WriteStream *stream = c4blob_openWriteStream((C4BlobStore *) jblobstore, &error);
    if (stream == nullptr) {
        throwError(env, error);
        return 0;
    }
    return (jlong) stream;
}

//// BlobReadStream
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    read
 * Signature: (J[BIJ)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_read(
        JNIEnv *env,
        jclass ignore,
        jlong jstream,
        jbyteArray buffer,
        jint offset,
        jlong jsize) {
    C4Error error{};

    int bufSize = env->GetArrayLength(buffer);
    if (offset + jsize > bufSize) {
        throwError(env, error);
        return -1;
    }

    jbyte *buff = env->GetByteArrayElements(buffer, nullptr);

    size_t read = c4stream_read((C4ReadStream *) jstream, buff + offset, (size_t) jsize, &error);

    env->ReleaseByteArrayElements(buffer, buff, 0);

    return read;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    getLength
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_getLength(JNIEnv *env, jclass ignore, jlong jstream) {
    C4Error error{};
    int64_t length = c4stream_getLength((C4ReadStream *) jstream, &error);
    if (length == -1) {
        throwError(env, error);
        return 0;
    }
    return (jlong) length;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    seek
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_seek(
        JNIEnv *env,
        jclass ignore,
        jlong jstream,
        jlong jposition) {
    C4Error error{};
    bool ok = c4stream_seek((C4ReadStream *) jstream, (uint64_t) jposition, &error);
    if (!ok)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_closeReadStream(
        JNIEnv *env,
        jclass ignore,
        jlong jstream) {
    c4stream_close((C4ReadStream *) jstream);
}

//// BlobWriteStream
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    write
 * Signature: (J[BI)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_write(
        JNIEnv *env,
        jclass ignore,
        jlong jstream,
        jbyteArray jbytes,
        jint jsize) {
    jbyteArraySlice bytes(env, false, jbytes, (size_t) jsize, true);
    auto slice = (C4Slice) bytes;
    C4Error error{};
    bool ok = c4stream_write((C4WriteStream *) jstream, slice.buf, slice.size, &error);
    if (!ok)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    computeBlobKey
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_computeBlobKey(JNIEnv *env, jclass ignore, jlong jstream) {
    auto blobKey = (C4BlobKey *) ::malloc(sizeof(C4BlobKey));
    *blobKey = c4stream_computeBlobKey((C4WriteStream *) jstream);
    return (jlong) blobKey;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    install
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_install(JNIEnv *env, jclass ignore, jlong jstream) {
    C4Error error{};
    bool ok = c4stream_install((C4WriteStream *) jstream, nullptr, &error);
    if (!ok)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Blob
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Blob_closeWriteStream(JNIEnv *env, jclass ignore, jlong jstream) {
    c4stream_closeWriter((C4WriteStream *) jstream);
}
}
