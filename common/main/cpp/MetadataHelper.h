#if defined(COUCHBASE_ENTERPRISE) && defined(__ANDROID__)
#include <jni.h>

#include "c4PeerDiscovery.hh"

#ifndef COUCHBASE_LITE_JAVA_EE_ROOT_METADATAHELPER_H
#define COUCHBASE_LITE_JAVA_EE_ROOT_METADATAHELPER_H

namespace litecore::jni {
    C4Peer::Metadata javaMapToMetadata(JNIEnv* env, jobject map);
    jobject metadataToJavaMap(JNIEnv* env, const C4Peer::Metadata& metadata);
}

#endif //COUCHBASE_LITE_JAVA_EE_ROOT_METADATAHELPER_H
#endif