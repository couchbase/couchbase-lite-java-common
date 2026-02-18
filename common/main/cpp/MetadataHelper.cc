#if defined(COUCHBASE_ENTERPRISE) && defined(__ANDROID__)
#include <jni.h>
#include "c4PeerDiscovery.hh"
#include "native_glue.hh"

namespace litecore::jni {
// Helper to convert Java Map to Metadata
    C4Peer::Metadata javaMapToMetadata(JNIEnv* env, jobject map) {
        C4Peer::Metadata metadata;

        if (!map) return metadata;

        jclass mapClass = env->GetObjectClass(map);
        jmethodID entrySetMethod = env->GetMethodID(mapClass, "entrySet", "()Ljava/util/Set;");
        jobject entrySet = env->CallObjectMethod(map, entrySetMethod);

        jclass setClass = env->GetObjectClass(entrySet);
        jmethodID toArrayMethod = env->GetMethodID(setClass, "toArray", "()[Ljava/lang/Object;");
        jobjectArray entries = (jobjectArray)env->CallObjectMethod(entrySet, toArrayMethod);

        jsize entryCount = env->GetArrayLength(entries);

        jclass entryClass = env->FindClass("java/util/Map$Entry");
        jmethodID getKeyMethod = env->GetMethodID(entryClass, "getKey", "()Ljava/lang/Object;");
        jmethodID getValueMethod = env->GetMethodID(entryClass, "getValue", "()Ljava/lang/Object;");

        for (jsize i = 0; i < entryCount; i++) {
            jobject entry = env->GetObjectArrayElement(entries, i);
            jstring jKey = (jstring)env->CallObjectMethod(entry, getKeyMethod);
            jbyteArray jValue = (jbyteArray)env->CallObjectMethod(entry, getValueMethod);

            std::string key = JstringToUTF8(env, jKey);
            jbyteArraySlice value(env, jValue);

            metadata[std::string(key)] = fleece::alloc_slice(value);

            env->DeleteLocalRef(entry);
            env->DeleteLocalRef(jKey);
            env->DeleteLocalRef(jValue);
        }

        env->DeleteLocalRef(entrySet);
        env->DeleteLocalRef(entries);

        return metadata;
    }


    jobject metadataToJavaMap(JNIEnv* env, const C4Peer::Metadata& metadata) {
        // Create HashMap
        jclass hashMapClass = env->FindClass("java/util/HashMap");
        jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "(I)V");
        jobject hashMap = env->NewObject(hashMapClass, hashMapInit);

        // Put method for HashMap
        jmethodID hashMapPut = env->GetMethodID(
                hashMapClass, "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // Convert each key-value pair
        for (const auto& [key, value] : metadata) {
            // Convert key to Java String
            jstring jKey = UTF8ToJstring(env, key.c_str(), key.size());

            // Convert value (fleece::alloc_slice) to Java byte[]
            jbyteArray jValue = toJByteArray(env, value);

            // Put in map
            env->CallObjectMethod(hashMap, hashMapPut, jKey, jValue);

            // Clean up local references
            env->DeleteLocalRef(jKey);
            env->DeleteLocalRef(jValue);
        }

        env->DeleteLocalRef(hashMapClass);
        return hashMap;
    }
}
#endif