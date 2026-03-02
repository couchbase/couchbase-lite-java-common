
# P2P Bluetooth LE Synchronization Documentation

## 1. Overview

This document provides a detailed explanation of the P2P synchronization feature using Bluetooth Low Energy (BLE). The feature allows Couchbase Lite enabled devices to discover each other and synchronize data directly without the need for a central server.

## 2. Architecture

The P2P synchronization feature is built on a hybrid architecture that leverages both Java and C++ code.

*   **Java Layer**: The Java layer is responsible for all platform-specific Bluetooth LE operations. This includes scanning for nearby devices, advertising the device's presence, and managing Bluetooth connections. By delegating these tasks to the Java layer, we ensure that the application can take advantage of the latest Android Bluetooth APIs and best practices.

*   **C++ Layer**: The C++ layer contains the core logic for peer discovery and data synchronization. It is responsible for managing the state of discovered peers, handling metadata exchange, and orchestrating the overall synchronization process. The C++ layer communicates with the Java layer through the Java Native Interface (JNI).

This separation of concerns allows for a clean and maintainable codebase. The C++ layer remains platform-agnostic, while the Java layer handles the intricacies of the Android Bluetooth stack.

## 3. C++ Class Descriptions

### 3.1. `C4BLEProvider`

The `C4BLEProvider` class is the heart of the peer discovery process. It is a C++ class that runs in the native layer and is responsible for the following:

*   **Starting and Stopping Peer Discovery**: The `C4BLEProvider` can be instructed to start or stop scanning for nearby peers. When scanning is active, the provider will notify the application of any discovered peers.
*   **Publishing the Device's Presence**: The `C4BLEProvider` can also be used to advertise the device's presence to other peers. This allows other devices to discover and connect to it.
*   **Managing Discovered Peers**: The `C4BLEProvider` maintains a list of all discovered peers and their current status (online or offline).
*   **Forwarding Calls to the Java Layer**: The `C4BLEProvider` uses JNI to forward calls to the Java layer for all Bluetooth-specific operations.

### 3.2. `C4Peer`

The `C4Peer` class represents a discovered peer in the network. It is a C++ class that contains the following information about a peer:

*   **Peer ID**: A unique identifier for the peer.
*   **Online Status**: A boolean flag that indicates whether the peer is currently online and reachable.
*   **Metadata**: A collection of key-value pairs that can be used to store additional information about the peer.

The `C4Peer` class provides methods for getting and setting the peer's metadata, as well as for monitoring changes to the metadata.

### 3.3. `MetadataHelper`

The `MetadataHelper` is a C++ utility class that provides functions for converting between Java `Map` objects and the `C4Peer::Metadata` type. This is necessary because the metadata is exchanged between the Java and C++ layers as part of the peer discovery and synchronization process.

## 4. Java Class Descriptions

### 4.1. `BluetoothProvider`

The `BluetoothProvider` class is the main entry point for the Java-side of the P2P implementation. It acts as a bridge between the native C++ code and the Java-based `BleService`. It is responsible for:

*   Creating and managing `BleService` instances.
*   Forwarding calls from the native layer to the `BleService` for starting/stopping browsing and publishing.
*   Handling callbacks from the `BleService` and forwarding them to the native layer.

### 4.2. `BluetoothPeer`

The `BluetoothPeer` class is the Java representation of a discovered peer. It mirrors the C++ `C4Peer` class and provides a high-level API for interacting with a peer. It is responsible for:

*   Storing the peer's ID, online status, and metadata.
*   Providing methods for monitoring metadata changes and resolving the peer's URL.

### 4.3. `BleService`

The `BleService` class is the core of the Java-side implementation. It manages all Bluetooth LE operations, including:

*   **Scanning**: It starts and stops scanning for nearby devices that are advertising the Couchbase Lite P2P service.
*   **Device Management**: It maintains a list of discovered devices (`CblBleDevice`) and manages their connection state.
*   **Publishing**: It coordinates with the `BlePublisher` to advertise the device's presence.
*   **L2CAP Connections**: It initiates L2CAP connections to discovered peers.

### 4.4. `BlePublisher`

The `BlePublisher` class is responsible for advertising the device's presence to other peers. It handles the complexities of BLE advertising, including:

*   Starting and stopping advertising with the appropriate settings.
*   Managing the advertising lifecycle and handling failures.
*   Working in conjunction with the `BleGattServer` to host the Couchbase Lite service.

### 4.5. `CblBleDevice`

The `CblBleDevice` class represents a remote Bluetooth LE device that has been discovered by the `BleService`. It is responsible for:

*   Managing the GATT connection to the remote device.
*   Discovering the Couchbase Lite P2P service and its characteristics.
*   Reading the peer's ID, port, and metadata from the GATT characteristics.
*   Initiating an L2CAP connection to the remote device.

### 4.6. `BleGattServer`

The `BleGattServer` class is responsible for creating and managing the GATT server that hosts the Couchbase Lite P2P service. Its key responsibilities include:

*   Creating the GATT service with the required characteristics (ID, port, metadata).
*   Handling read requests for the characteristics from remote devices.
*   Optionally starting an L2CAP server to listen for incoming connections.

### 4.7. `BleL2CAPConnection`

The `BleL2CAPConnection` class is a wrapper around a BluetoothSocket that provides a simple interface for sending and receiving data over an L2CAP connection. It handles:

*   Reading data from the socket in a background thread.
*   Writing data to the socket asynchronously.
*   Notifying a listener of incoming data and connection closure.

### 4.8. `BleP2pConstants`

This class defines the UUIDs for the Couchbase Lite P2P service and its characteristics, as well as other constants used by the BLE implementation.

### 4.9. Listeners and Handles

*   **`BlePublisherListener`**: An interface for receiving lifecycle events from the `BlePublisher`.
*   **`BlePublisherHandle`**: A handle that allows for stopping an active advertising session.
*   **`BleGattInboundListener`**: An interface for receiving events related to inbound L2CAP connections.

## 5. Flows

### 5.1. Peer Discovery

The peer discovery process is initiated by the application, which calls the `startBrowsing()` method on the `C4BLEProvider` object. The `C4BLEProvider` then forwards this call to the Java layer, which begins scanning for nearby Bluetooth LE devices.

When a new device is discovered, the Java layer notifies the `C4BLEProvider`, which creates a new `C4Peer` object to represent the device. The `C4BLEProvider` then adds the new `C4Peer` to its list of discovered peers and notifies the application.

### 5.2. Publishing

To make itself discoverable to other devices, the application calls the `startPublishing()` method on the `C4BLEProvider` object. The `C4BLEProvider` then forwards this call to the Java layer, which begins advertising the device's presence.

The advertisement packet contains a service UUID that is specific to the application, as well as the device's display name and other relevant information.

### 5.3. Metadata Exchange

The P2P synchronization feature allows for the exchange of metadata between peers. This metadata can be used to store any application-specific information, such as the user's name or the device's capabilities.

The metadata is exchanged as part of the peer discovery process. When a device discovers a new peer, it can request the peer's metadata. The metadata is then sent over the Bluetooth connection and stored in the `C4Peer` object.

The application can also monitor changes to a peer's metadata. When the metadata changes, the `C4BLEProvider` will notify the application, which can then take appropriate action.
