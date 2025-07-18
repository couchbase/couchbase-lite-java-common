//
// native_glue.hh
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

#ifndef native_glue_hpp
#define native_glue_hpp

#include <jni.h>
#include <cerrno>
#include <string>
#include "c4.h"
#include "fleece/Fleece.h"
#include "fleece/FLExpert.h"
#include "fleece/FLValue.h"


// For printing jstringSlices
// like this: logError("A jstringSlice: %.*s", SPLAT(aJStringSlice));
#define SPLAT(S)    (int)(S).size, (char *)(S).buf

namespace litecore::jni {

    // Soft limit of number of local JNI refs to use. Even using PushLocalFrame(), you may not get as
    // many refs as you asked for. At least, that's what happens on Android: the new frame won't have
    // more than 512 refs available. So 200 is being conservative.
    static const jsize MaxLocalRefsToUse = 200;

    bool initC4Logging(JNIEnv *env);           // Implemented in native_c4.cc
    bool initC4Observer(JNIEnv *);             // Implemented in native_c4observer.cc
    bool initC4Replicator(JNIEnv *);           // Implemented in native_c4replicator.cc
    bool initC4Socket(JNIEnv *);               // Implemented in native_c4socket.cc

#ifdef COUCHBASE_ENTERPRISE

    bool initC4Prediction(JNIEnv *);           // Implemented in native_c4prediction.cc
    bool initC4Listener(JNIEnv *);             // Implemented in native_c4listener.cc

#ifdef __ANDROID__

    bool initC4MultipeerReplicator(JNIEnv *);  // Implemented in native_c4multipeerreplicator.cc
#endif
#endif

    // lightweight logging: defined in native_c4.cc
    void jniLog(const char *fmt, ...);

    int attachJVM(JNIEnv **env, const char *caller);

    int detachJVM(const char *caller);

    // Sets a Java exception based on the LiteCore error.
    void throwError(JNIEnv *env, C4Error);

    void throwError(JNIEnv *env, C4Error, const char *msg);

    std::string JstringToUTF8(JNIEnv *env, jstring jstr);

    jstring UTF8ToJstring(JNIEnv *env, const char *s);

    jstring UTF8ToJstring(JNIEnv *env, const char *s, size_t size);

    std::string JcharsToUTF8(const jchar *jchars, jsize len);

    // Creates a temporary slice value from a Java String object
    class jstringSlice {
    public:
        jstringSlice(JNIEnv *env, jstring js);

        jstringSlice(JNIEnv *env, jcharArray jchars);

        jstringSlice(jstringSlice &&s) noexcept
                : _str(std::move(s._str)), _slice(s._slice) { s._slice = kFLSliceNull; }

        operator FLSlice() { return _slice; }

        const char *c_str();

    private:
        std::string _str;
        FLSlice _slice;
    };

    // Creates a temporary slice value from a Java byte[], attempting to avoid copying
    class jbyteArraySlice {
        // Warning: If `critical` is true, you cannot make any further JNI calls (except other
        // critical accesses) until this object goes out of scope or is deleted.
        // That includes any attempt to log anything.
    public:
        jbyteArraySlice(JNIEnv *env, jbyteArray jbytes, bool critical = false);

        jbyteArraySlice(JNIEnv *env, bool delRef, jbyteArray jbytes, bool critical = false);

        jbyteArraySlice(JNIEnv *env, bool delRef, jbyteArray jbytes, size_t length, bool critical = false);

        ~jbyteArraySlice();

        jbyteArraySlice(jbyteArraySlice &&s) noexcept // move constructor
                : _slice(s._slice), _env(s._env), _delRef(s._delRef), _jbytes(s._jbytes),
                  _critical(s._critical) { s._slice = kFLSliceNull; }

        operator FLSlice() { return _slice; }

        // Copies a Java byte[] to FLSliceResult
        static FLSliceResult copy(JNIEnv *env, jbyteArray jbytes);

    private:
        FLSlice _slice;
        JNIEnv *_env;
        jbyteArray _jbytes;
        bool _critical;
        bool _delRef;
    };

    // Creates a Java String from the contents of a C4Slice.

    jstring toJString(JNIEnv *, C4Slice);

    jstring toJString(JNIEnv *, C4SliceResult);

    // Creates a Java byte[] from the contents of a C4Slice.
    jbyteArray toJByteArray(JNIEnv *env, const uint8_t *bytes, const size_t);

    jbyteArray toJByteArray(JNIEnv *, C4Slice);

    jbyteArray toJByteArray(JNIEnv *, C4SliceResult);

    // Copy a FLMutableArray of strings to a Java ArrayList<String>
    jobject toStringList(JNIEnv *env, FLMutableArray array);

    // Copy a FLMutableArray of strings to a Java HashSet<String>
    jobject toStringSet(JNIEnv *env, FLMutableArray array);

    // Copy a native FLSliceResult to a Java FLSliceResult
    jobject toJavaFLSliceResult(JNIEnv *const, const FLSliceResult &);

    // Copy a Java FLSliceResult to a native FLSliceResult
    FLSliceResult fromJavaFLSliceResult(JNIEnv *const, jobject);

    void unbindJavaFLSliceResult(JNIEnv *const, jobject jsr);

    // Copies an encryption key to a C4EncryptionKey. Returns false on exception.
    bool getEncryptionKey(
            JNIEnv *env,
            jint keyAlg,
            jbyteArray jKeyBytes,
            C4EncryptionKey *outKey);

#ifdef COUCHBASE_ENTERPRISE
    C4Cert *toC4Cert(JNIEnv *, jbyteArray, bool &);
    jbyteArray fromC4Cert(JNIEnv *, C4Cert *);
#endif // COUCHBASE_ENTERPRISE
} // namespace litecore::jni

#endif /* native_glue_hpp */
