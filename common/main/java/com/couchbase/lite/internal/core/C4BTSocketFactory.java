package com.couchbase.lite.internal.core;

import android.Manifest;
import android.bluetooth.BluetoothSocket;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4BTSocketFactory;
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.p2p.ble.BleService;
import com.couchbase.lite.internal.sockets.CloseStatus;
import com.couchbase.lite.internal.sockets.MessageFraming;

@SuppressWarnings({"checkstyle:CheckNullabilityAnnotations", "PMD.UnusedPrivateMethod"})
public final class C4BTSocketFactory {
    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

    private  C4BTSocketFactory() {
        // Do Nothing
    }

    // -------------------------------------------------------------------------
    // NativeImpl interface  (implemented by NativeC4BTSocketFactory)
    // -------------------------------------------------------------------------

    public interface NativeImpl {
        /** Register the BT socket factory with LiteCore. Returns a token for unregistering. */
        long nRegisterFactory();
        /** Create a C4Socket wrapping an already-connected native BT handle (peripheral side). */
        long nFromNative(long token, String scheme, String host, long port, String path, int framing);
    }

    // -------------------------------------------------------------------------
    // Token → BleService binding  (one per registered factory instance)
    // -------------------------------------------------------------------------

    public static class FactoryBinding<T> extends TaggedWeakPeerBinding<T> {
        @Override
        protected void preBind(long key, @NonNull T obj) {}
        @Override
        protected void preGetBinding(long key) {}
    }

    private static final FactoryBinding<BleService> BOUND_SERVICES = new FactoryBinding<>();

    // -------------------------------------------------------------------------
    // Singleton factory token (returned by nRegisterFactory)
    // -------------------------------------------------------------------------

    private static final NativeImpl NATIVE_IMPL = new NativeC4BTSocketFactory();
    private static volatile long sFactoryToken;

    /**
     * Register the BT C4SocketFactory with LiteCore and bind the given BleService
     * so it can be retrieved by the native callbacks.
     * Call once at startup (idempotent).
     *
     * @param bleService the BleService to use for outgoing L2CAP connections
     * @return the factory token (also stored statically)
     */
    public static long register(@NonNull BleService bleService) {
        if (sFactoryToken == 0L) {
            sFactoryToken = NATIVE_IMPL.nRegisterFactory();
            Log.i(LOG_DOMAIN, "C4BTSocketFactory registered, token=%d", sFactoryToken);
        }
        BOUND_SERVICES.bind(sFactoryToken, bleService);
        return sFactoryToken;
    }

    public static void triggerIncoming(@NonNull String peerAddr, long btSocketHandle) {
        if (sFactoryToken == 0L) {
            Log.w(LOG_DOMAIN, "triggerIncoming: factory not registered yet");
            return;
        }
        NATIVE_IMPL.nFromNative(
                sFactoryToken, "blip+bt", peerAddr, 0, "/",
                MessageFraming.getC4Framing(MessageFraming.CLIENT_FRAMING));
    }

    @Nullable
    private static BleService getBleService(long token) {
        return BOUND_SERVICES.getBinding(token);
    }

    // -------------------------------------------------------------------------
    // JNI callbacks from native BTSocketFactory
    // These are called by the C++ JNI layer (native_c4btsocketfactory.cpp).
    // Method signatures must NOT change.
    // -------------------------------------------------------------------------

    /**
     * Factory callback: open.
     * Called by LiteCore when the replicator wants to open a BT socket to peerID.
     *
     * @param c4socketPeer native C4Socket pointer
     * @param factoryToken the token passed to nRegisterFactory (= context)
     * @param peerID       Bluetooth device address of the remote peer
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    static void open(long c4socketPeer, long factoryToken, @NonNull String peerID) {
        Log.d(LOG_DOMAIN, "^BTSocketFactory.open peer=%s c4socket=0x%x", peerID, c4socketPeer);
        final BleService bleService = getBleService(factoryToken);
        if (bleService == null) {
            Log.w(LOG_DOMAIN, "BTSocketFactory.open: no BleService for token %d", factoryToken);
            return;
        }

        // Create the C4Socket Java wrapper
        final C4Socket c4socket = C4Socket.BOUND_SOCKETS.getBinding(c4socketPeer);
        if (c4socket == null) {
            Log.w(LOG_DOMAIN, "BTSocketFactory.open: no C4Socket for 0x%x", c4socketPeer);
            return;
        }
        LiteCoreBTSocket.openOutgoing(c4socket, peerID, bleService);
    }

    /**
     * Factory callback: write.
     * Called by LiteCore when it has data to send over the BT socket.
     */
    static void write(long c4socketPeer, @NonNull byte[] data) {
        Log.d(LOG_DOMAIN, "^BTSocketFactory.write c4socket=0x%x len=%d", c4socketPeer, data.length);
        final LiteCoreBTSocket btSocket = LiteCoreBTSocket.getBoundSocket(c4socketPeer);
        if (btSocket != null) {
            btSocket.coreWrites(data);
        }
    }

    /**
     * Factory callback: completedReceive.
     * Called by LiteCore after it has processed n bytes that we delivered.
     */
    static void completedReceive(long c4socketPeer, long byteCount) {
        Log.d(LOG_DOMAIN, "^BTSocketFactory.completedReceive c4socket=0x%x n=%d", c4socketPeer, byteCount);
        final LiteCoreBTSocket btSocket = LiteCoreBTSocket.getBoundSocket(c4socketPeer);
        if (btSocket != null) {
            btSocket.coreAcksWrite(byteCount);
        }
    }

    /**
     * Factory callback: close.
     * Called by LiteCore (byte-stream framing) to request socket teardown.
     */
    static void close(long c4socketPeer) {
        Log.d(LOG_DOMAIN, "^BTSocketFactory.close c4socket=0x%x", c4socketPeer);
        final LiteCoreBTSocket btSocket = LiteCoreBTSocket.getBoundSocket(c4socketPeer);
        if (btSocket != null) {
            btSocket.coreClosed();
        }
    }

    /**
     * Factory callback: requestClose.
     * Called by LiteCore (message framing) to send a close message to the peer.
     */
    static void requestClose(long c4socketPeer, int status, @Nullable String message) {
        Log.d(
                LOG_DOMAIN, "^BTSocketFactory.requestClose c4socket=0x%x status=%d msg=%s",
                c4socketPeer,
                status,
                message
        );
        final LiteCoreBTSocket btSocket = LiteCoreBTSocket.getBoundSocket(c4socketPeer);
        if (btSocket != null) {
            btSocket.coreRequestsClose(new CloseStatus(C4Constants.ErrorDomain.WEB_SOCKET, status, message));
        }
    }

    /**
     * Factory callback: dispose.
     * Called by LiteCore when the C4Socket is being freed.  Remove our binding.
     */
    static void dispose(long c4socketPeer) {
        Log.d(LOG_DOMAIN, "^BTSocketFactory.dispose c4socket=0x%x", c4socketPeer);
        LiteCoreBTSocket.unbindSocket(c4socketPeer);
    }

    /**
     * Factory callback: attached.
     * Called when a C4Socket is created via c4socket_fromNative (peripheral / incoming side).
     *
     * @param c4socketPeer native C4Socket pointer
     * @param btSocketHandle Java handle of the BluetoothSocket (cast to long by native)
     * @param peerID         CBL peer device ID string
     */
    static void attached(long c4socketPeer, long btSocketHandle, @NonNull String peerID) {
        Log.d(LOG_DOMAIN, "^BTSocketFactory.attached c4socket=0x%x peer=%s", c4socketPeer, peerID);
        final BluetoothSocket btSocket = SOCKET_HANDLES.remove(btSocketHandle);
        if (btSocket == null) {
            Log.w(LOG_DOMAIN, "BTSocketFactory.attached: no BluetoothSocket for handle %d", btSocketHandle);
            return;
        }
        final C4Socket c4socket = C4Socket.BOUND_SOCKETS.getBinding(c4socketPeer);
        if (c4socket == null) {
            Log.w(LOG_DOMAIN, "BTSocketFactory.attached: no C4Socket for peer 0x%x", c4socketPeer);
            return;
        }
        LiteCoreBTSocket.attachIncoming(c4socket, btSocket, peerID);
    }

    @Nullable
    public static byte[] buildUpgradeHeaders() {
        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.beginDict(3);
            enc.writeKey("Connection");
            enc.writeString("Upgrade");
            enc.writeKey("Upgrade");
            enc.writeString("websocket");
            enc.writeKey("Sec-WebSocket-Protocol");
            enc.writeString("BLIP_3+CBMobile_4");
            enc.endDict();
            return enc.finish();
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // BluetoothSocket hand-off table
    // When the peripheral (server) side accepts an L2CAP connection, the
    // BluetoothSocket is stored here keyed by a handle; the handle is passed
    // to the native layer which then calls attached() with it.
    // -------------------------------------------------------------------------

    private static final Map<Long, BluetoothSocket> SOCKET_HANDLES = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_HANDLE = new AtomicLong(1L);

    /**
     * Register a BluetoothSocket for the peripheral (incoming) path.
     * Returns a long handle to be passed to nAttachIncoming.
     */
    public static long registerIncomingSocket(@NonNull BluetoothSocket socket) {
        final long handle = NEXT_HANDLE.getAndIncrement();
        SOCKET_HANDLES.put(handle, socket);
        return handle;
    }
}
