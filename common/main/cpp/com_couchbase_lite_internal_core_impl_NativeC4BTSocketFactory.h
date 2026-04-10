#ifndef ANDROID_COM_COUCHBASE_LITE_INTERNAL_CORE_IMPL_NATIVEC4BTSOCKETFACTORY_H
#define ANDROID_COM_COUCHBASE_LITE_INTERNAL_CORE_IMPL_NATIVEC4BTSOCKETFACTORY_H
#include <jni.h>

#ifdef __cplusplus
extern "C++" {
#endif

JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4BTSocketFactory_registerBTSocketFactory(
        JNIEnv*, jclass);

JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4BTSocketFactory_fromNative(
        JNIEnv*, jclass, jlong, jstring, jstring, jlong, jstring, jint);

#ifdef __cplusplus
}
#endif

#endif //ANDROID_COM_COUCHBASE_LITE_INTERNAL_CORE_IMPL_NATIVEC4BTSOCKETFACTORY_H
