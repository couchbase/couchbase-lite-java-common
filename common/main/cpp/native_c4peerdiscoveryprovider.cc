#if defined(COUCHBASE_ENTERPRISE) && defined(__ANDROID__)

#include "c4PeerDiscovery.hh"
#include "c4PeerSyncTypes.h"
#include "native_glue.hh"
#include "socket_factory.h"
#include "MetadataHelper.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider.h"

using namespace litecore;
using namespace litecore::jni;
using namespace litecore::p2p;

namespace litecore::jni {
    static jclass cls_C4PeerDiscoveryProvider;

    static jmethodID m_C4PeerDiscoveryProvider_startBrowsing;
    static jmethodID m_C4PeerDiscoveryProvider_stopBrowsing;
    static jmethodID m_C4PeerDiscoveryProvider_startPublishing;
    static jmethodID m_C4PeerDiscoveryProvider_stopPublishing;
    static jmethodID m_C4PeerDiscoveryProvider_resolveURL;
    static jmethodID m_C4PeerDiscoveryProvider_updateMetadata;
    static jmethodID m_C4PeerDiscoveryProvider_startMetadataMonitoring;
    static jmethodID m_C4PeerDiscoveryProvider_stopMetadataMonitoring;
    static jmethodID m_C4PeerDiscoveryProvider_initBleProvider;

    bool initC4PeerDiscoveryProvider(JNIEnv *env) {
        jclass localClass = env->FindClass("com/couchbase/lite/internal/core/BluetoothProvider");
        if (localClass == nullptr) return false;

        cls_C4PeerDiscoveryProvider = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        if (cls_C4PeerDiscoveryProvider == nullptr) return false;

        m_C4PeerDiscoveryProvider_startBrowsing = env->GetStaticMethodID(
                cls_C4PeerDiscoveryProvider,
                "startBrowsing",
                "(J)V");

        m_C4PeerDiscoveryProvider_stopBrowsing = env->GetStaticMethodID(
                cls_C4PeerDiscoveryProvider,
                "stopBrowsing",
                "(J)V");

        m_C4PeerDiscoveryProvider_startPublishing = env->GetStaticMethodID(
                cls_C4PeerDiscoveryProvider,
                "startPublishing",
                "(JLjava/lang/String;ILjava/util/Map;)V");

        m_C4PeerDiscoveryProvider_stopPublishing = env->GetStaticMethodID(
                cls_C4PeerDiscoveryProvider,
                "stopPublishing",
                "(J)V");

        m_C4PeerDiscoveryProvider_resolveURL = env->GetStaticMethodID(
                cls_C4PeerDiscoveryProvider,
                "resolveURL",
                "(JJ)V");

        m_C4PeerDiscoveryProvider_updateMetadata = env->GetStaticMethodID(
                cls_C4PeerDiscoveryProvider,
                "updateMetadata",
                "(JLjava/util/Map;)V");

        m_C4PeerDiscoveryProvider_startMetadataMonitoring = env->GetStaticMethodID(
                cls_C4PeerDiscoveryProvider,
                "startMetadataMonitoring",
                "(JJ)V");

        m_C4PeerDiscoveryProvider_stopMetadataMonitoring = env->GetStaticMethodID(
                cls_C4PeerDiscoveryProvider,
                "stopMetadataMonitoring",
                "(JJ)V");

        m_C4PeerDiscoveryProvider_initBleProvider = env->GetStaticMethodID(
                cls_C4PeerDiscoveryProvider,
                "initBleProvider",
                "(JLjava/lang/String;)J"
                );

        return (m_C4PeerDiscoveryProvider_startBrowsing != nullptr)
               && (m_C4PeerDiscoveryProvider_stopBrowsing != nullptr)
               && (m_C4PeerDiscoveryProvider_startPublishing != nullptr)
               && (m_C4PeerDiscoveryProvider_stopPublishing != nullptr)
               && (m_C4PeerDiscoveryProvider_resolveURL != nullptr)
               && (m_C4PeerDiscoveryProvider_updateMetadata != nullptr)
               && (m_C4PeerDiscoveryProvider_initBleProvider != nullptr);
    }



    class C4BLEProvider : public C4PeerDiscoveryProvider {
    public:
        C4BLEProvider(C4PeerDiscovery& discovery, std::string_view peerGroupID)
                : C4PeerDiscoveryProvider(discovery, kPeerSyncProtocol_BluetoothLE, peerGroupID) {
            std::string pg(peerGroupID);

            JNIEnv* env = nullptr;
            jint envState = attachJVM(&env, "initBleProvider");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED)) return;

            jstring jPeerGroup = UTF8ToJstring(env, pg.data(), pg.size());

            jlong providerPtr = reinterpret_cast<jlong>(this);
            jlong token = env->CallStaticLongMethod(
                    cls_C4PeerDiscoveryProvider,
                    m_C4PeerDiscoveryProvider_initBleProvider,
                    providerPtr,
                    jPeerGroup);

            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                token = 0;
            }

            _contextToken = token;

            if (envState == JNI_EDETACHED) detachJVM("initBleProvider");
            if (jPeerGroup) env->DeleteLocalRef(jPeerGroup);
        }

        virtual void startBrowsing() override {
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "startBrowsing");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            // Call Java BLE service to start scanning
            env->CallStaticVoidMethod(
                    cls_C4PeerDiscoveryProvider,
                    m_C4PeerDiscoveryProvider_startBrowsing,
                    _contextToken);
            if (envState == JNI_EDETACHED) {
                detachJVM("startBrowsing");
            }
        }

        virtual void stopBrowsing() {
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "stopBrowsing");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            env->CallStaticVoidMethod(
                    cls_C4PeerDiscoveryProvider,
                    m_C4PeerDiscoveryProvider_stopBrowsing,
                    _contextToken);

            if (envState == JNI_EDETACHED) {
                detachJVM("stopBrowsing");
            }
        }

        virtual void startPublishing(std::string_view displayName, uint16_t port,
                                     C4Peer::Metadata const& metadata) override {
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "startPublishing");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            jstring jDisplayName = UTF8ToJstring(env, displayName.data(), displayName.size());
            jobject jMetadata = metadataToJavaMap(env, metadata);

            env->CallStaticVoidMethod(
                    cls_C4PeerDiscoveryProvider,
                    m_C4PeerDiscoveryProvider_startPublishing,
                    _contextToken,
                    jDisplayName,
                    (jint)port,
                    jMetadata);
            if (envState == JNI_EDETACHED) {
                detachJVM("startPublishing");
            } else {
                if (jDisplayName != nullptr) env->DeleteLocalRef(jDisplayName);
                if (jMetadata != nullptr) env->DeleteLocalRef(jMetadata);
            }
        }

        virtual void stopPublishing() {
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "stopPublishing");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            env->CallStaticVoidMethod(
                    cls_C4PeerDiscoveryProvider,
                    m_C4PeerDiscoveryProvider_stopPublishing,
                    _contextToken);

            if (envState == JNI_EDETACHED) {
                detachJVM("stopPublishing");
            }
        }

        virtual void monitorMetadata(C4Peer* peer, bool start) override {
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "monitorMetadata");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            jbyteArray peerId = toJByteArray(env, (const uint8_t*)peer->id.data(), peer->id.size());
            jlong peerPtr = reinterpret_cast<jlong>(peer);


            if (start) {
                // Start monitoring metadata characteristic
                env->CallStaticVoidMethod(
                        cls_C4PeerDiscoveryProvider,
                        m_C4PeerDiscoveryProvider_startMetadataMonitoring,
                        _contextToken,
                        peerPtr);
            } else {
                // Stop monitoring metadata characteristic
                env->CallStaticVoidMethod(
                        cls_C4PeerDiscoveryProvider,
                        m_C4PeerDiscoveryProvider_stopMetadataMonitoring,
                        _contextToken,
                        peerPtr);
            }

            if (envState == JNI_EDETACHED) {
                detachJVM("monitorMetadata");
            } else {
                if (peerId != nullptr) env->DeleteLocalRef(peerId);
            }
        }

        virtual void resolveURL(C4Peer* peer) override {
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "resolveURL");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            jlong peerPtr = reinterpret_cast<jlong>(peer);

            env->CallStaticVoidMethod(
                    cls_C4PeerDiscoveryProvider,
                    m_C4PeerDiscoveryProvider_resolveURL,
                    _contextToken,
                    peerPtr);

            if (envState == JNI_EDETACHED) {
                detachJVM("resolveURL");
            }
        }

        virtual void updateMetadata(C4Peer::Metadata const& metadata) override {
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "updateMetadata");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            jobject jMetadata = metadataToJavaMap(env, metadata);

            env->CallStaticVoidMethod(
                    cls_C4PeerDiscoveryProvider,
                    m_C4PeerDiscoveryProvider_updateMetadata,
                    _contextToken,
                    jMetadata);

            if (envState == JNI_EDETACHED) {
                detachJVM("updateMetadata");
            } else {
                if (jMetadata != nullptr) env->DeleteLocalRef(jMetadata);
            }
        }

        virtual void shutdown(std::function<void()> onComplete) override {
            stopBrowsing();
            stopPublishing();
            onComplete();
        }

        virtual void stop(Mode mode) override {
            if (mode == C4PeerDiscovery::Mode::browse) {
                stopBrowsing();
            }
            else if (mode == C4PeerDiscovery::Mode::publish) {
                stopPublishing();
            }
        }

        void addDiscoveredPeer(C4Peer* peer, bool moreComing = false) {
            addPeer(peer, moreComing);
        }

        void removeDiscoveredPeer(std::string id, bool moreComing = false) {
            removePeer(id, moreComing);
        }

        void statusStateChange(Mode m, Status s) {
            statusChanged(m, s);
        }

        void setContextToken(jlong token) {
            _contextToken = token;
        }

    private:
        jlong _contextToken{};
    };

    // Factory function for creating BLE provider
    static std::unique_ptr<C4PeerDiscoveryProvider, C4PeerDiscovery::ProviderDeleter>
    createBLEProvider(C4PeerDiscovery& discovery,
                      std::string_view peerGroupID) {
        C4BLEProvider* provider = new C4BLEProvider(discovery, peerGroupID);
        return C4PeerDiscovery::ProviderRef(provider, [](C4PeerDiscoveryProvider* ptr) {
            delete ptr;
        });
    }

    // Register the BLE provider
    static bool registerBleProvider() {
        C4PeerDiscovery::registerProvider(kPeerSyncProtocol_BluetoothLE, &createBLEProvider);
        return true;
    }

    static bool bleProviderRegistered = registerBleProvider();
}

namespace litecore::p2p {

    // BleP2pConstants
    static jclass cls_BleP2pConstants;

    static jclass jUuidClass;
    static jmethodID uuidFromString;
    static jfieldID PORT_CHAR_FIELD, META_CHAR_FIELD, PEER_GROUP_NS_FIELD;


    void setUuidConstant(JNIEnv* env, jclass cls, const char* uuidStr) {
        jstring str = env->NewStringUTF(uuidStr);
        jobject uuidObj = env->CallStaticObjectMethod(jUuidClass, uuidFromString, str);

        if (strcmp(uuidStr, litecore::p2p::btle::kPortCharacteristicID) == 0)
            env->SetStaticObjectField(cls, PORT_CHAR_FIELD, uuidObj);
        else if (strcmp(uuidStr, litecore::p2p::btle::kMetadataCharacteristicID) == 0)
            env->SetStaticObjectField(cls, META_CHAR_FIELD, uuidObj);
        else if (strcmp(uuidStr, litecore::p2p::btle::kPeerGroupUUIDNamespace) == 0)
            env->SetStaticObjectField(cls, PEER_GROUP_NS_FIELD, uuidObj);
    }
}


#ifdef __cplusplus
extern "C++" {
#endif

//-------------------------------------------------------------------------
// com.couchbase.lite.internal.core.impl.NativeC4MultipeerReplicator
//-------------------------------------------------------------------------


JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_serviceUuidFromPeerGroup(
        JNIEnv* env, jclass, jstring peerGroup) {
    std::string pg = JstringToUTF8(env, peerGroup);
    auto uuid = litecore::p2p::btle::ServiceUUIDFromPeerGroup(pg);
    C4Slice s = {&uuid, sizeof(uuid)};
    return toJString(env, s);
}

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_addPeer(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId) {
    // Get the provider instance from the pointer
    auto* provider = (C4BLEProvider*)providerPtr;
    if (!provider) return;

    const char* peerIdStr = env->GetStringUTFChars(peerId, nullptr);
    if (!peerIdStr) return;
    std::string id(peerIdStr);
    env->ReleaseStringUTFChars(peerId, peerIdStr);


    auto* peer = new C4Peer(provider, id);
    provider->addDiscoveredPeer(peer);
}

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_removePeer(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId) {
    // Get the provider instance from the pointer
    auto* provider = (C4BLEProvider*)providerPtr;
    if (!provider) return;

    // Convert peerId from Java string to C++ string
    const char* peerIdStr = env->GetStringUTFChars(peerId, nullptr);
    if (!peerIdStr) return;
    std::string id(peerIdStr);
    env->ReleaseStringUTFChars(peerId, peerIdStr);

    provider->removeDiscoveredPeer(id);
}

JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_peerWithID(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId) {

    auto *provider = reinterpret_cast<C4BLEProvider *>(providerPtr);
    if (!provider || !peerId) { return 0; }

    std::string peerIdStr = JstringToUTF8(env, peerId);
    if (peerIdStr.empty()) { return 0; }

    C4PeerDiscovery &discovery = provider->discovery();
    fleece::Retained<C4Peer> peer = discovery.peerWithID(peerIdStr);
    if (!peer) { return 0; }

    C4Peer *rawPeer = std::move(peer).detach();

    return static_cast<jlong>(reinterpret_cast<uintptr_t>(rawPeer));
}

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_peerDiscovered(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId, jobject metadata) {

    auto* provider = (C4BLEProvider*)providerPtr;
    if (!provider) return;

    const char* peerIdStr = env->GetStringUTFChars(peerId, nullptr);
    if (!peerIdStr) return;
    std::string id(peerIdStr);
    env->ReleaseStringUTFChars(peerId, peerIdStr);

    // Use the utility function
    C4Peer::Metadata peerMetadata = javaMapToMetadata(env, metadata);

    auto* peer = new C4Peer(provider, id, peerMetadata);
    provider->addDiscoveredPeer(peer);
}

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_peerLost(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId) {

    auto provider = (C4BLEProvider*)providerPtr;
    if (!provider) return;

    // Convert peerId from Java string to C++ string
    const char* peerIdStr = env->GetStringUTFChars(peerId, nullptr);
    if (!peerIdStr) return;
    std::string id(peerIdStr);
    env->ReleaseStringUTFChars(peerId, peerIdStr);

    // Remove the peer from the discovery system
    // This will trigger notifications to observers about the peer going offline
    provider->removeDiscoveredPeer(id);
}

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_statusChanged(
        JNIEnv *env, jclass thiz, jlong providerPtr, jint mode, jboolean online,
        jint errorDomain, jint errorCode) {

    // Get provider from binding
    auto* provider = (C4BLEProvider*)providerPtr;
    if (!provider) return;

    // Convert parameters
    C4PeerDiscoveryProvider::Mode nativeMode =
            (mode == 1) ? C4PeerDiscoveryProvider::Mode::publish
                        : C4PeerDiscoveryProvider::Mode::browse;

    C4PeerDiscoveryProvider::Status status{};
    status.online = (online != JNI_FALSE);
    status.error.domain = (C4ErrorDomain) errorDomain;
    status.error.code = errorCode;

    provider->statusStateChange(nativeMode, status);
}

JNIEXPORT jlongArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_peersWithProvider(
        JNIEnv *env, jclass thiz, jlong providerPtr) {

    // Get provider from binding
    auto* provider = (C4BLEProvider*)providerPtr;
    if (!provider) return nullptr;
    std::vector<fleece::Ref<C4Peer>> peers = provider->discovery().peersWithProvider(provider);
    jlongArray arr = env->NewLongArray((jsize) peers.size());
    if (!arr) return nullptr;

    std::vector<jlong> handles;
    handles.reserve(peers.size());

    for (auto& p : peers) {
        handles.push_back(reinterpret_cast<jlong>(p.get()));
    }

    env->SetLongArrayRegion(arr, 0, (jsize)handles.size(), handles.data());
    return arr;
}

#ifdef __cplusplus
}
#endif
#endif // COUCHBASE_ENTERPRISE && __ANDROID__