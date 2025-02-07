//
// native_glue.cc
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

#include <queue>
#include <new>
#include <codecvt>
#include <locale>
#include <assert.h>
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;
using namespace std;

// Java ArrayList class
static jclass cls_ArrayList;                      // global class reference
static jmethodID m_ArrayList_init;                // constructor
static jmethodID m_ArrayList_add;                 // add

// Java HashSet class
static jclass cls_HashSet;                        // global class reference
static jmethodID m_HashSet_init;                  // constructor
static jmethodID m_HashSet_add;                   // add

// Java FLSliceResult class
static jclass cls_FLSliceResult;                  // global class reference
static jmethodID m_FLSliceResult_createSliceResult; // static constructor
static jmethodID m_FLSliceResult_getBase;         // get slice base
static jmethodID m_FLSliceResult_getSize;         // get slice size
static jmethodID m_FLSliceResult_unbind;          // mark the Java slice as invalid

// Java LiteCoreException class
static jclass cls_LiteCoreException;              // global reference
static jmethodID m_LiteCoreException_throw;       // static throw


static bool initC4Glue(JNIEnv *env) {
    {
        jclass localClass = env->FindClass("java/util/ArrayList");
        if (localClass == nullptr)
            return false;

        cls_ArrayList = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_ArrayList == nullptr)
            return false;

        m_ArrayList_init = env->GetMethodID(cls_ArrayList, "<init>", "(I)V");
        if (m_ArrayList_init == nullptr)
            return false;

        m_ArrayList_add = env->GetMethodID(cls_ArrayList, "add", "(Ljava/lang/Object;)Z");
        if (m_ArrayList_add == nullptr)
            return false;
    }
    {
        jclass localClass = env->FindClass("java/util/HashSet");
        if (localClass == nullptr)
            return false;

        cls_HashSet = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_HashSet == nullptr)
            return false;

        m_HashSet_init = env->GetMethodID(cls_HashSet, "<init>", "(I)V");
        if (m_HashSet_init == nullptr)
            return false;

        m_HashSet_add = env->GetMethodID(cls_HashSet, "add", "(Ljava/lang/Object;)Z");
        if (m_HashSet_add == nullptr)
            return false;
    }
    {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/fleece/FLSliceResult");
        if (localClass == nullptr)
            return false;

        cls_FLSliceResult = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_FLSliceResult == nullptr)
            return false;

        m_FLSliceResult_createSliceResult = env->GetStaticMethodID(
                cls_FLSliceResult,
                "create",
                "(JJ)Lcom/couchbase/lite/internal/fleece/FLSliceResult;");
        if (m_FLSliceResult_createSliceResult == nullptr)
            return false;

        m_FLSliceResult_getBase = env->GetMethodID(cls_FLSliceResult, "getBase", "()J");
        if (m_FLSliceResult_getBase == nullptr)
            return false;

        m_FLSliceResult_getSize = env->GetMethodID(cls_FLSliceResult, "getSize", "()J");
        if (m_FLSliceResult_getSize == nullptr)
            return false;

        m_FLSliceResult_unbind = env->GetMethodID(cls_FLSliceResult, "unbind", "()V");
        if (m_FLSliceResult_unbind == nullptr)
            return false;
    }
    {
        jclass localClass = env->FindClass("com/couchbase/lite/LiteCoreException");
        if (localClass == nullptr)
            return false;

        cls_LiteCoreException = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_LiteCoreException == nullptr)
            return false;

        m_LiteCoreException_throw = env->GetStaticMethodID(
                cls_LiteCoreException,
                "throwException",
                "(IILjava/lang/String;)V");
        if (m_LiteCoreException_throw == nullptr)
            return false;
    }

    jniLog("glue initialized");
    return true;
}

/*
 * Will be called by JNI when the library is loaded
 *
 * NOTE:
 *  Resources allocated here are never explicitly released.
 *  We rely on system to free all global refs when it goes away,
 *  the pairing function JNI_OnUnload() will never get called at all.
 */
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *jvm, void *ignore) {
    JNIEnv *env;
    if ((jvm->GetEnv((void **) &env, JNI_VERSION_1_6) == JNI_OK)
        && initC4Logging(env)
        && initC4Glue(env)
        && initC4Observer(env)
        && initC4Replicator(env)
        #ifdef COUCHBASE_ENTERPRISE
        && initC4Listener(env)
        && initC4Prediction(env)
        #endif
        && initC4Socket(env)) {
        assert(gJVM == nullptr);
        gJVM = jvm;

        return JNI_VERSION_1_6;
    }

    return JNI_ERR;
}

namespace litecore::jni {

    // ----------------------------------------------------------------------------
    // JVM
    // ----------------------------------------------------------------------------
    JavaVM *gJVM;

    int attachCurrentThread(JNIEnv **env) {
#ifdef JNI_VERSION_1_8
        return gJVM->AttachCurrentThread(reinterpret_cast<void **>(env), NULL);
#else
        return gJVM->AttachCurrentThread(env, nullptr);
#endif
    }

    // ----------------------------------------------------------------------------
    // Exception Handling
    // ----------------------------------------------------------------------------

    void throwError(JNIEnv *env, C4Error error, jstring msg) {
        if (env->ExceptionOccurred())
            return;

        env->CallStaticVoidMethod(
                cls_LiteCoreException,
                m_LiteCoreException_throw,
                (jint) error.domain,
                (jint) error.code,
                msg);

        if (msg != nullptr)env->DeleteLocalRef(msg);
    }

    void throwError(JNIEnv *env, C4Error error) {
        C4SliceResult msgSlice = c4error_getMessage(error);
        jstring msg = toJString(env, msgSlice);
        c4slice_free(msgSlice);
        throwError(env, error, msg);
    }

    void throwError(JNIEnv *env, C4Error error, char const *message) {
        if (message != nullptr) {
            jstring msg = UTF8ToJstring(env, message, sizeof(message));
            if (msg != nullptr) {
                throwError(env, error, msg);
                return;
            }
        }
        throwError(env, error);
    }

    // ----------------------------------------------------------------------------
    // String Conversions
    // ----------------------------------------------------------------------------

    /**
     * Java uses Modified-UTF-8, not UTF-8: Attempting to decode a real UTF-8 string will cause a failure that
     * looks like:
     *   art/runtime/check_jni.cc:65] JNI DETECTED ERROR IN APPLICATION: input is not valid Modified UTF-8: illegal start byte ...
     *   art/runtime/check_jni.cc:65]     string: ...
     *   art/runtime/check_jni.cc:65]     in call to NewStringUTF
     *  See:
     *    https://stackoverflow.com/questions/35519823/jni-detected-error-in-application-input-is-not-valid-modified-utf-8-illegal-st
     *  The strategy here is to use standard C functions to convert the UTF-8 directly to UTF-16,
     *  which Java handles nicely. These functions are derived from this code
     *      (https://github.com/incanus/android-jni/blob/master/app/src/main/jni/JNI.cpp#L57-L86):
     *  ??? Creating the wstring_convert is expensive.  It would be nice to create one
     *      and to re-use it.  It is *NOT*, however, threadsafe.
     *  ??? On failure, just return a nullptr.
     */
    jstring UTF8ToJstring(JNIEnv *env, const char *s, size_t size) {
        std::u16string ustr;
        try {
#ifdef _MSC_VER
            auto tmpstr = std::wstring_convert<std::codecvt_utf8_utf16<int16_t>, int16_t>().from_bytes(s, s + size);
    ustr = reinterpret_cast<const char16_t *>(tmpstr.data());
#else
            ustr = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().from_bytes(s, s + size);
#endif
        }
        catch (const std::bad_alloc &x) {
            jniLog("Failed allocating space to convert string: %s", x.what());
            return nullptr;
        }
        catch (const std::exception &x) {
            // sadly, we better not try to log the actual string...
            jniLog("Failed to convert string from UTF-8 to UTF-16: %s", x.what());
            return nullptr;
        }

        jstring jstr = env->NewString(reinterpret_cast<const jchar *>(ustr.c_str()), ustr.size());
        if (jstr == nullptr) {
            C4Error error = {LiteCoreDomain, kC4ErrorMemoryError, 0};
            throwError(env, error);
            return nullptr;
        }

        return jstr;
    }

    std::string JcharsToUTF8(const jchar *chars, jsize len) {
        std::string str;

        if (chars == nullptr) {
            str = std::string();
        } else {
            try {
#ifdef _MSC_VER
                str = std::wstring_convert<std::codecvt_utf8_utf16<int16_t>, int16_t>()
                            .to_bytes(reinterpret_cast<const int16_t *>(chars),
                                      reinterpret_cast<const int16_t *>(chars + len));
#else
                str = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>()
                        .to_bytes(reinterpret_cast<const char16_t *>(chars),
                                  reinterpret_cast<const char16_t *>(chars + len));
#endif

            }
            catch (const std::exception &x) {
                str = std::string();
            }
        }

        return str;
    }

    jstring UTF8ToJstring(JNIEnv *env, const char *s) {
        return (s == nullptr) ? nullptr : UTF8ToJstring(env, s, strlen(s));
    }

    // ??? Callers can't handle exceptions so we just ignore errors and return an empty string.
    std::string JstringToUTF8(JNIEnv *env, jstring jstr) {
        jsize len = env->GetStringLength(jstr);
        if (len <= 0)
            return std::string();

        const jchar *chars = env->GetStringChars(jstr, nullptr);
        std::string ret = JcharsToUTF8(chars, len);
        env->ReleaseStringChars(jstr, chars);
        return ret;
    }

    // ??? Callers can't handle exceptions so we just ignore errors and return an empty string.
    std::string JcharArrayToUTF8(JNIEnv *env, jcharArray jcharArray) {
        jsize len = env->GetArrayLength(jcharArray);
        if (len <= 0)
            return std::string();

        jchar *chars = env->GetCharArrayElements(jcharArray, nullptr);
        std::string ret = JcharsToUTF8(chars, len);
        env->ReleaseCharArrayElements(jcharArray, chars, JNI_ABORT);
        return ret;
    }

    // ----------------------------------------------------------------------------
    // jstringSlice
    // ----------------------------------------------------------------------------

    jstringSlice::jstringSlice(JNIEnv *env, jstring js) {
        assert(env != nullptr);
        if (js == nullptr) {
            _slice = kFLSliceNull;
        } else {
            _str = JstringToUTF8(env, js);
            _slice = FLStr(_str.c_str());
        }
    }

    jstringSlice::jstringSlice(JNIEnv *env, jcharArray jchars) {
        assert(env != nullptr);
        if (jchars == nullptr) {
            _slice = kFLSliceNull;
        } else {
            _str = JcharArrayToUTF8(env, jchars);
            _slice = FLStr(_str.c_str());
        }
    }

    const char *jstringSlice::c_str() {
        return (const char *) _slice.buf;
    }

    // ----------------------------------------------------------------------------
    // jbyteArraySlice
    // ----------------------------------------------------------------------------

    // ATTN: In critical, cannot call any other JNI methods.
    // http://docs.oracle.com/javase/6/docs/technotes/guides/jni/spec/functions.html
    // Just don't set it true.
    jbyteArraySlice::jbyteArraySlice(JNIEnv *env, jbyteArray jbytes, bool critical)
            : jbyteArraySlice(env, false, jbytes, critical) {}

    jbyteArraySlice::jbyteArraySlice(JNIEnv *env, bool delRef, jbyteArray jbytes, bool critical)
            : jbyteArraySlice(
            env,
            delRef,
            jbytes,
            (size_t) (!jbytes ? 0 : env->GetArrayLength(jbytes)),
            critical) {}

    jbyteArraySlice::jbyteArraySlice(JNIEnv *env, bool delRef, jbyteArray jbytes, size_t length, bool critical)
            : _env(env),
              _jbytes(jbytes),
              _critical(critical),
              _delRef(delRef) {
        if ((jbytes == nullptr) || (length <= 0)) {
            _slice = kFLSliceNull;
            return;
        }

        void *data;
        if (critical) {
            data = env->GetPrimitiveArrayCritical(jbytes, nullptr);
        } else {
            data = env->GetByteArrayElements(jbytes, nullptr);
        }

        _slice = {data, length};
    }

    jbyteArraySlice::~jbyteArraySlice() {
        if (_slice.buf != nullptr) {
            if (_critical) {
                _env->ReleasePrimitiveArrayCritical(_jbytes, (void *) _slice.buf, JNI_ABORT);
            } else {
                _env->ReleaseByteArrayElements(_jbytes, (jbyte *) _slice.buf, JNI_ABORT);
            }
        }
        if (_jbytes && _delRef) _env->DeleteLocalRef(_jbytes);
    }

    FLSliceResult jbyteArraySlice::copy(JNIEnv *env, jbyteArray jbytes) {
        jbyteArraySlice bytes(env, jbytes, true);
        return FLSlice_Copy(bytes);
    }

    // ----------------------------------------------------------------------------
    // Other Glue
    // ----------------------------------------------------------------------------

    jstring toJString(JNIEnv *env, C4Slice s) {
        if (s.buf == nullptr)
            return nullptr;
        return UTF8ToJstring(env, (char *) s.buf, s.size);
    }

    jstring toJString(JNIEnv *env, C4SliceResult s) {
        return toJString(env, (C4Slice) s);
    }

    // NOTE: this creates a *copy* of the passed slice
    jbyteArray toJByteArray(JNIEnv *env, C4Slice s) {
        if (s.buf == nullptr)
            return nullptr;
        jbyteArray array = env->NewByteArray((jsize) s.size);
        if (array != nullptr)
            env->SetByteArrayRegion(array, 0, (jsize) s.size, (const jbyte *) s.buf);
        return array;
    }

    jbyteArray toJByteArray(JNIEnv *env, C4SliceResult s) {
        return toJByteArray(env, (C4Slice) s);
    }

    jobject toJavaFLSliceResult(JNIEnv *const env, const FLSliceResult &sr) {
        return env->CallStaticObjectMethod(
                cls_FLSliceResult,
                m_FLSliceResult_createSliceResult,
                (jlong) sr.buf,
                (jlong) sr.size);
    }

    FLSliceResult fromJavaFLSliceResult(JNIEnv *const env, jobject jsr) {
        C4SliceResult sliceResult{
                (const void *) env->CallLongMethod(jsr, m_FLSliceResult_getBase),
                (size_t) env->CallLongMethod(jsr, m_FLSliceResult_getSize)
        };
        return sliceResult;
    }

    void unbindJavaFLSliceResult(JNIEnv *const env, jobject jsr) { env->CallVoidMethod(jsr, m_FLSliceResult_unbind); }

    jobject toStringList(JNIEnv *env, const FLMutableArray array) {
        uint32_t n = FLArray_Count(array);
        jobject result = env->NewObject(cls_ArrayList, m_ArrayList_init, (jint) n);

        if (array == nullptr)
            return result;

        for (int i = 0; i < n; i++) {
            FLValue arrayElem = FLArray_Get(array, (uint32_t) i);
            if (arrayElem == nullptr) continue;

            FLSlice str = FLValue_AsString(arrayElem);
            if (!str) continue;

            jstring jstr = toJString(env, str);
            if (!jstr) continue;

            env->CallBooleanMethod(result, m_ArrayList_add, jstr);

            env->DeleteLocalRef(jstr);
        }

        return result;
    }

    jobject toStringSet(JNIEnv *env, const FLMutableArray array) {
        uint32_t n = FLArray_Count(array);
        jobject result = env->NewObject(cls_HashSet, m_HashSet_init, (jint) n);

        if (!array)
            return result;

        for (int i = 0; i < n; i++) {
            FLValue arrayElem = FLArray_Get(array, (uint32_t) i);
            if (arrayElem == nullptr) continue;

            FLSlice str = FLValue_AsString(arrayElem);
            if (!str) continue;

            jstring jstr = toJString(env, str);
            if (!jstr) continue;

            env->CallBooleanMethod(result, m_HashSet_add, jstr);

            env->DeleteLocalRef(jstr);
        }

        return result;
    }

    bool getEncryptionKey(JNIEnv *env, jint keyAlg, jbyteArray jKeyBytes, C4EncryptionKey *outKey) {
        outKey->algorithm = (C4EncryptionAlgorithm) keyAlg;
        if (keyAlg == kC4EncryptionNone)
            return true;

        jbyteArraySlice keyBytes(env, jKeyBytes);
        FLSlice keySlice = keyBytes;
        if ((!keySlice) || (keySlice.size > sizeof(outKey->bytes))) {
            throwError(env, C4Error{LiteCoreDomain, kC4ErrorCrypto});
            return false;
        }

        memset(outKey->bytes, 0, sizeof(outKey->bytes));
        memcpy(outKey->bytes, keySlice.buf, keySlice.size);

        return true;
    }
}
