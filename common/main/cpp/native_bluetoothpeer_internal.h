#if defined(COUCHBASE_ENTERPRISE) && defined(__ANDROID__)

#ifndef ANDROID_NATIVE_BLUETOOTHPEER_INTERNAL_H
#define ANDROID_NATIVE_BLUETOOTHPEER_INTERNAL_H

#include "c4PeerDiscovery.hh"
#include "c4Error.h"
#include "c4Socket.h"
#include "fleece/slice.hh"

namespace litecore::jni {
    /// The BT C4SocketFactory — defined in native_c4btsocketfactory.cc
    extern const C4SocketFactory kBTSocketFactory;
}

struct BTNativeHandle {
    jlong       btSocketHandle;   // key into Java's sSocketHandles map
    fleece::alloc_slice peerID;           // BT MAC address / CBL peer ID
};


class BluetoothPeer final : public C4Peer {
    using C4Peer::C4Peer;
public:
    void resolvingUrl(std::string s, C4Error err) { resolvedURL(std::move(s), err); }
};

#endif //ANDROID_NATIVE_BLUETOOTHPEER_INTERNAL_H
#endif
