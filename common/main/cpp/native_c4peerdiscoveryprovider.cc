#if defined(COUCHBASE_ENTERPRISE) && defined(__ANDROID__)

#include "c4PeerDiscovery.hh"
#include "c4PeerSyncTypes.h"
#include "native_glue.hh"
#include "socket_factory.h"
#include "MetadataHelper.h"
#include "TLSCodec.hh"
#include "c4Socket.hh"
#include "c4Error.h"
#include "native_bluetoothpeer_internal.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4BTSocketFactory.h"
#include "com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider.h"
#include <android/api-level.h>

using namespace litecore;
using namespace litecore::jni;
using namespace litecore::p2p;
using namespace std;

namespace litecore::jni {
    static void assertAndroidLevel() {
        constexpr int MIN_LEVEL = __ANDROID_API_Q__;
        if (android_get_device_api_level() < MIN_LEVEL) {
            C4Error::raise(LiteCoreDomain, kC4ErrorUnsupported,
                           "Bluetooth peer discovery requires Android API %d or higher",
                           MIN_LEVEL);
        }
    }

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
                : C4PeerDiscoveryProvider(discovery, kPeerSyncProtocol_BluetoothLE, peerGroupID)
                , _socketFactory(litecore::jni::kBTSocketFactory){
            JNIEnv* env = nullptr;
            jint envState = attachJVM(&env, "initBleProvider");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED)) return;

            jstring jPeerGroup = UTF8ToJstring(env, peerGroupID.data(), peerGroupID.size());

            auto providerPtr = reinterpret_cast<jlong>(this);
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
            _socketFactory.context = reinterpret_cast<void *>(_contextToken);

            if (envState == JNI_EDETACHED) detachJVM("initBleProvider");
            if (jPeerGroup) env->DeleteLocalRef(jPeerGroup);
        }

        void startBrowsing() override {
            assertAndroidLevel();
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "startBrowsing");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;
            jniLog("Start Browsing");

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

        void startPublishing(std::string_view displayName, uint16_t port,
                             C4Peer::Metadata const& metadata) override {
            assertAndroidLevel();
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "startPublishing");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            jstring jDisplayName = UTF8ToJstring(env, displayName.data(), displayName.size());
            jniLog("Start Publishing");
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

        void monitorMetadata(C4Peer* peer, bool start) override {
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "monitorMetadata");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            jbyteArray peerId = toJByteArray(env, (const uint8_t*)peer->id.data(), peer->id.size());
            auto peerPtr = reinterpret_cast<jlong>(peer);


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

        void resolveURL(C4Peer* peer) override {
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "resolveURL");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            auto peerPtr = reinterpret_cast<jlong>(peer);

            env->CallStaticVoidMethod(
                    cls_C4PeerDiscoveryProvider,
                    m_C4PeerDiscoveryProvider_resolveURL,
                    _contextToken,
                    peerPtr);

            if (envState == JNI_EDETACHED) {
                detachJVM("resolveURL");
            }
        }

        void updateMetadata(C4Peer::Metadata const& metadata) override {
            JNIEnv *env = nullptr;
            jint envState = attachJVM(&env, "updateMetadata");
            if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                return;

            jniLog("Update Metadata");
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

        void shutdown(std::function<void()> onComplete) override {
            stopBrowsing();
            stopPublishing();
            onComplete();
        }

        void stop(Mode mode) override {
            if (mode == C4PeerDiscovery::Mode::browse) {
                stopBrowsing();
            }
            else if (mode == C4PeerDiscovery::Mode::publish) {
                stopPublishing();
            }
        }

        fleece::Ref<C4Peer> addDiscoveredPeer(C4Peer* peer, bool moreComing = false) {
            return addPeer(peer, moreComing);
        }

        void removeDiscoveredPeer(const string &id, bool moreComing = false) {
            removePeer(id, moreComing);
        }

        void statusStateChange(Mode m, Status s) {
            statusChanged(m, s);
        }

        std::optional<C4SocketFactory> getSocketFactory() const override {
            return net::wrapSocketFactoryInTLS(_socketFactory);
        }

        C4SocketFactory& unwrappedSocketFactory() { return _socketFactory; }

        bool notifyIncomingConn(C4Peer* peer, C4Socket* socket) {
            return notifyIncomingConnection(peer, socket);
        }

        fleece::Ref<C4Socket> createIncomingSockWithTLS(C4SocketFactory& factory, void* ctx, C4Address address) {
            return createIncomingSocketWithTLS(factory, ctx, address);
        }



    private:
        jlong _contextToken{};
        C4SocketFactory _socketFactory{};
    };

    // Factory function for creating BLE provider
    static std::unique_ptr<C4PeerDiscoveryProvider, C4PeerDiscovery::ProviderDeleter>
    createBLEProvider(C4PeerDiscovery& discovery,
                      std::string_view peerGroupID) {
        auto* provider = new C4BLEProvider(discovery, peerGroupID);
        return {provider, [](C4PeerDiscoveryProvider* ptr) {
            delete ptr;
        }};
    }

    // Register the BLE provider
    static bool registerBleProvider() {
        C4PeerDiscovery::registerProvider(kPeerSyncProtocol_BluetoothLE, &createBLEProvider);
        return true;
    }

    static bool bleProviderRegistered = registerBleProvider();
}


#ifdef __cplusplus
extern "C" {
#endif

//-------------------------------------------------------------------------
// com.couchbase.lite.internal.core.impl.NativeC4MultipeerReplicator
//-------------------------------------------------------------------------


JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_serviceUuidFromPeerGroup(
        JNIEnv* env, jclass, jstring peerGroup) {
    std::string pg = JstringToUTF8(env, peerGroup);
    auto uuid = litecore::p2p::btle::ServiceUUIDFromPeerGroup(pg);
    C4Slice s = {&uuid, sizeof(uuid)};
    return toJByteArray(env, s);
}

JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_addPeer(
        JNIEnv *env, jclass thiz, jlong providerPtr, jstring peerId) {
    auto* provider = reinterpret_cast<C4BLEProvider*>(providerPtr);
    if (!provider || !peerId) { return 0; }

    std::string id = JstringToUTF8(env, peerId);
    if (id.empty()) { return 0; }

    auto created = fleece::make_retained<BluetoothPeer>(provider, id);
    fleece::Ref<C4Peer> peer = provider->addDiscoveredPeer(created.get());

    return (jlong) reinterpret_cast<uintptr_t>(std::move(peer).detach());
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

    fleece::Retained<C4Peer> peer = provider->discovery().peerWithID(id);

    if (!peer) {
        auto created = fleece::make_retained<BluetoothPeer>(provider, id);
        peer = provider->addDiscoveredPeer(created.get());
    }
    peer->setMetadata(std::move(peerMetadata));
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

JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_createIncomingSocket(
        JNIEnv* env, jclass,
        jlong providerPtr,
        jlong btSocketHandle,
        jstring jUrl)
{
    auto* provider = reinterpret_cast<C4BLEProvider*>(providerPtr);
    if (!provider) return;

    // Parse URL → C4Address
    jstringSlice urlSlice(env, jUrl);
    C4Address address{};
    if (!c4address_fromURL(urlSlice, &address, nullptr)) {
        C4Warn("nCreateIncomingSocket: failed to parse URL");
        return;
    }

    auto* ctx = new BTNativeHandle {
            btSocketHandle,
            fleece::alloc_slice(urlSlice)
    };

    // Use the raw BT socket factory, not getSocketFactory() which wraps in TLS.
    // createIncomingSockWithTLS will wrap it in TLS itself.
    fleece::Ref<C4Socket> socket = provider->createIncomingSockWithTLS(
            provider->unwrappedSocketFactory(),
            reinterpret_cast<void*>(ctx),
            address);

    if (!socket) {
        C4Warn("nCreateIncomingSocket: createIncomingSocketWithTLS failed");
        delete ctx;
        return;
    }

    if (!provider->notifyIncomingConn(nullptr, socket)) {
        socket->getFactory().close(socket);
    }
}

JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4PeerDiscoveryProvider_getSocketFactory(
        JNIEnv* env, jclass,
        jlong providerPtr)
{
    auto* provider = reinterpret_cast<C4BLEProvider*>(providerPtr);
    if (!provider) return 0L;

    std::optional<C4SocketFactory> factory = provider->getSocketFactory();

    if (!factory.has_value()) return 0L;

    auto* factoryPtr = new C4SocketFactory(factory.value());
    return reinterpret_cast<jlong>(factoryPtr);
}


#ifdef __cplusplus
}
#endif
#endif // COUCHBASE_ENTERPRISE && __ANDROID__
