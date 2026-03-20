#if defined(COUCHBASE_ENTERPRISE) && defined(__ANDROID__)

#ifndef ANDROID_NATIVE_BLUETOOTHPEER_INTERNAL_H
#define ANDROID_NATIVE_BLUETOOTHPEER_INTERNAL_H

#include "c4PeerDiscovery.hh"
#include "c4Error.h"

class BluetoothPeer final : public C4Peer {
    using C4Peer::C4Peer;
public:
    void resolvingUrl(std::string s, C4Error err) { resolvedURL(std::move(s), err); }
};

#endif //ANDROID_NATIVE_BLUETOOTHPEER_INTERNAL_H
#endif
