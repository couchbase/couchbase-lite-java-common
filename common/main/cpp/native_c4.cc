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
#ifdef __ANDROID__

#include <android/log.h>

#elif defined(__linux__) || defined(__APPLE__)
#include <sys/time.h>
#endif

#include <unordered_set>
#include "native_glue.hh"
#include "com_couchbase_lite_internal_core_impl_NativeC4.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4Log.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4Key.h"

using namespace litecore;
using namespace litecore::jni;

//-------------------------------------------------------------------------
// Package initialization
//-------------------------------------------------------------------------

static jclass cls_C4Log;
static jmethodID m_C4Log_logCallback;

static void logCallback(C4LogDomain domain, C4LogLevel level, const char *fmt, va_list ignore);

bool litecore::jni::initC4Logging(JNIEnv *env) {
    jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4Log");
    if (localClass == nullptr)
        return false;

    cls_C4Log = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    if (cls_C4Log == nullptr)
        return false;

    m_C4Log_logCallback = env->GetStaticMethodID(
            cls_C4Log,
            "logCallback",
            "(Ljava/lang/String;ILjava/lang/String;)V");

    if (m_C4Log_logCallback == nullptr)
        return false;

    c4log_writeToCallback((C4LogLevel) kC4LogDebug, logCallback, true);

    jniLog("logging initialized");
    return true;
}

//-------------------------------------------------------------------------
// Pre-initialization Logging
//-------------------------------------------------------------------------

// Before our logger is set up, or when something in the logging system fails,
// log to stderr, or on Android to __android_log_write.
// ??? Need to do something better for web service and Windows logging
void vLogError(const char *fmt, va_list args) {
#if defined(__ANDROID__)
    __android_log_vprint(ANDROID_LOG_ERROR, "LiteCore/JNI", fmt, args);
#else
#if defined(__linux__) || defined(__APPLE__)
    struct timeval tv;
    gettimeofday(&tv, NULL);

    struct tm tm;
    localtime_r(&tv.tv_sec, &tm);

    char timestamp[100];
    strftime(timestamp, sizeof(timestamp), "%T", &tm);

    fprintf(stderr, "%s.%03u ", timestamp, tv.tv_usec / 1000);
#endif

    fprintf(stderr, "E/LiteCore/JNI: ");
    vfprintf(stderr, fmt, args);
    fputc('\n', stderr);
#endif
}

void litecore::jni::jniLog(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vLogError(fmt, args);
    va_end(args);
}


//-------------------------------------------------------------------------
// Logging Callback
//-------------------------------------------------------------------------

static inline bool detach(jint envState) {
    return (envState == JNI_EDETACHED) && (JNI_OK == detachJVM("log"));
}

static void logCallback(C4LogDomain domain, C4LogLevel level, const char *fmt, va_list ignore) {
    if ((cls_C4Log == nullptr) || (m_C4Log_logCallback == nullptr)) {
        jniLog("logger: Not initialized");
        return;
    }

    JNIEnv *env = nullptr;
    jint envState = attachJVM(&env, "logger");
    if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
        return;

    if (env->ExceptionCheck() != JNI_FALSE) {
        jniLog("logger: Exception outstanding");
        detach(envState);
        return;
    }

    jstring message = UTF8ToJstring(env, fmt, strlen(fmt));
    if (message == nullptr) {
        jniLog("logger: Failed encoding message");
        detach(envState);
        return;
    }

    const char *domainNameRaw = c4log_getDomainName(domain);
    jstring domainName = UTF8ToJstring(env, domainNameRaw, strlen(domainNameRaw));
    if (domainName == nullptr) domainName = env->NewStringUTF("???");

    env->CallStaticVoidMethod(cls_C4Log, m_C4Log_logCallback, domainName, (jint) level, message);

    if (!detach(envState)) {
        env->DeleteLocalRef(message);
        env->DeleteLocalRef(domainName);
    }
}

extern "C" {
// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4
// ----------------------------------------------------------------------------
/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    setenv
 * Signature: (Ljava/lang/String;Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4_setenv(
        JNIEnv *env,
        jclass ignore,
        jstring jname,
        jstring jvalue,
        jint overwrite) {
    jstringSlice name(env, jname);
    jstringSlice value(env, jvalue);

#ifdef _MSC_VER
    _putenv_s(name.c_str(), value.c_str());
#else
    setenv(name.c_str(), value.c_str(), overwrite);
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    getBuildInfo
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4_getBuildInfo(JNIEnv *env, jclass ignore) {
    C4StringResult result = c4_getBuildInfo();
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    getVersion
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4_getVersion(JNIEnv *env, jclass ignore) {
    C4StringResult result = c4_getVersion();
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    debug
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4_debug(JNIEnv *env, jclass ignore, jboolean debugging) {
    c4log_enableFatalExceptionBacktrace();
    if (debugging)
        c4log_warnOnErrors(true);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    getMessage
 * Signature: (III)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4_getMessage(
        JNIEnv *env,
        jclass ignore,
        jint jdomain,
        jint jcode,
        jint jinfo) {
    C4Error c4err = {(C4ErrorDomain) jdomain, (int) jcode, (unsigned) jinfo};
    C4StringResult msg = c4error_getMessage(c4err);
    jstring result = toJString(env, msg);
    c4slice_free(msg);
    return result;
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    setTempDir
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4_setTempDir(JNIEnv *env, jclass ignore, jstring jtempDir) {
    jstringSlice tempDir(env, jtempDir);
    C4Error error{};
    bool ok = c4_setTempDir(tempDir, &error);
    if (!ok && error.code != 0)
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4
 * Method:    enableExtension
 * Signature: (Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4_enableExtension(
        JNIEnv *env,
        jclass ignore,
        jstring jname,
        jstring jpath) {
    jstringSlice name(env, jname);
    jstringSlice path(env, jpath);
    C4Error error{};
    bool ok = c4_enableExtension(name, path, &error);
    if (!ok && error.code != 0)
        throwError(env, error);
}


// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4Log
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_impl_C4Log
 * Method:    setLevel
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Log_setLevel(
        JNIEnv *env,
        jclass ignore,
        jstring jdomain,
        jint jlevel) {
    jstringSlice domain(env, jdomain);
    C4LogDomain logDomain = c4log_getDomain(domain.c_str(), true);
    if (logDomain != nullptr)
        c4log_setLevel(logDomain, (C4LogLevel) jlevel);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Log_log
 * Method:    log
 * Signature: (Ljava/lang/String;I;Ljava/lang/String)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Log_log(
        JNIEnv *env,
        jclass ignore,
        jstring jdomain,
        jint jlevel,
        jstring jmessage) {
    jstringSlice domain(env, jdomain);
    jstringSlice message(env, jmessage);
    C4LogDomain logDomain = c4log_getDomain(domain.c_str(), true);
    if (logDomain != nullptr)
        c4slog(logDomain, (C4LogLevel) jlevel, message);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Log
 * Method:    setBinaryFileLevel
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Log_setBinaryFileLevel(
        JNIEnv *env,
        jclass ignore,
        jint level) {
    c4log_setBinaryFileLevel((C4LogLevel) level);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Log
 * Method:    writeToBinaryFile
 * Signature: (Ljava/lang/String;IIJZLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Log_writeToBinaryFile(
        JNIEnv *env,
        jclass ignore,
        jstring jpath,
        jint jlevel,
        jint jmaxrotatecount,
        jlong jmaxsize,
        jboolean juseplaintext,
        jstring jheader) {
    jstringSlice path(env, jpath);
    jstringSlice header(env, jheader);
    C4LogFileOptions options{
            (C4LogLevel) jlevel,
            path,
            jmaxsize,
            jmaxrotatecount,
            (bool) juseplaintext,
            header
    };

    C4Error error{};
    bool ok = c4log_writeToBinaryFile(options, &error);
    if (!ok && (error.code != 0))
        throwError(env, error);
}

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Log
 * Method:    setCallbackLevel
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Log_setCallbackLevel(
        JNIEnv *env,
        jclass clazz,
        jint jlevel) {
    c4log_setCallbackLevel((C4LogLevel) jlevel);
}

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_impl_NativeC4Key
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_impl_NativeC4Key
 * Method:    pbkdf2
 * Signature: (Ljava/lang/String;[BII)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4Key_pbkdf2(JNIEnv *env, jclass ignore, jstring password) {
    jstringSlice pwd(env, password);

    C4EncryptionKey key;
    bool ok = c4key_setPasswordSHA1(&key, pwd, kC4EncryptionAES256);
    if (!ok) return nullptr;

    int keyLen = sizeof(key.bytes);
    jbyteArray result = env->NewByteArray(keyLen);
    env->SetByteArrayRegion(result, 0, keyLen, (jbyte *) &key.bytes);

    return result;
}
}
