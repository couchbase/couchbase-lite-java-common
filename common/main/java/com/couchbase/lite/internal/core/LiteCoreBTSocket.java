package com.couchbase.lite.internal.core;

import android.Manifest;
import android.bluetooth.BluetoothSocket;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Constants.ErrorDomain;
import com.couchbase.lite.internal.core.C4Constants.NetworkError;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.p2p.ble.BleService;
import com.couchbase.lite.internal.sockets.CloseStatus;
import com.couchbase.lite.internal.sockets.SocketFromCore;

@SuppressWarnings({"checkstyle:CheckNullabilityAnnotations", "PMD.TooManyFields"})
public final class LiteCoreBTSocket implements SocketFromCore {

    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;
    private static final int READ_BUFFER_SIZE       = 64 * 1024;
    private static final int MAX_RECEIVED_BYTES_PENDING = 256 * 1024;

    private static final Map<Long, LiteCoreBTSocket> BOUND_BT_SOCKETS = new ConcurrentHashMap<>();

    @Nullable
    static LiteCoreBTSocket getBoundSocket(long c4socketPeer) {
        return BOUND_BT_SOCKETS.get(c4socketPeer);
    }

    static void unbindSocket(long c4socketPeer) {
        BOUND_BT_SOCKETS.remove(c4socketPeer);
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Outgoing (central / browsing) side.
     * Called from {@link C4BTSocketFactory#open}.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @NonNull
    public static LiteCoreBTSocket openOutgoing(
            @NonNull C4Socket c4socket,
            @NonNull String peerID,
            @NonNull BleService bleService) {
        final LiteCoreBTSocket socket = new LiteCoreBTSocket(peerID, bleService, c4socket);
        c4socket.init(socket);
        socket.open();
        return socket;
    }

    /**
     * Incoming (peripheral / publishing) side.
     * Called from {@link C4BTSocketFactory#attached}.
     */
    @NonNull
    public static LiteCoreBTSocket attachIncoming(
            @NonNull C4Socket c4socket,
            @NonNull BluetoothSocket btSocket,
            @NonNull String peerID) {
        final LiteCoreBTSocket socket = new LiteCoreBTSocket(peerID, null, c4socket);
        c4socket.init(socket);
        socket.setChannel(btSocket);
        socket.notifyConnected();
        return socket;
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    @NonNull
    private final String peerID;
    @Nullable
    private final BleService bleService;
    @NonNull
    private final C4Socket c4socket;

    @NonNull
    private final ExecutorService socketQueue;

    @NonNull
    private final ExecutorService readExecutor;

    @Nullable
    private BluetoothSocket btSocket;
    @Nullable
    private InputStream inputStream;
    @Nullable
    private OutputStream outputStream;

    @NonNull
    private final Deque<byte[]> pendingWrites = new ArrayDeque<>();

    private long receivedBytesPending;
    private volatile boolean connecting;
    private volatile boolean closing;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    private LiteCoreBTSocket(@NonNull String peerID,
                             @Nullable BleService bleService,
                             @NonNull C4Socket c4socket) {
        this.peerID    = peerID;
        this.bleService = bleService;
        this.c4socket  = c4socket;
        // Now peerID is guaranteed assigned — safe to use in lambda
        this.socketQueue = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "litecore-btsocket-" + peerID);
            t.setDaemon(true);
            return t;
        });
        this.readExecutor = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "litecore-btsocket-reader-" + peerID);
            t.setDaemon(true);
            return t;
        });

        BOUND_BT_SOCKETS.put(getPeerPtr(c4socket), this);
    }

    // Retrieve the native peer pointer stored in C4Socket
    private static long getPeerPtr(@NonNull C4Socket socket) {
        // C4Peer.getPeer() returns the native pointer
        return socket.withPeerOrDefault(0L, ptr -> ptr);
    }

    // -------------------------------------------------------------------------
    // Outgoing connection
    // -------------------------------------------------------------------------

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void open() {
        connecting = true;
        Log.d(LOG_DOMAIN, "BTSocket %s: open – initiating L2CAP connect", this);

        final boolean initiated = bleService.openPeerSocket(
                peerID,
                btSocket -> this.onChannelOpened(btSocket),
                (addr, err) -> this.closeWithError(
                        ErrorDomain.NETWORK, NetworkError.BROKEN_PIPE,
                        err != null ? err.getMessage() : "L2CAP channel closed"));

        if (!initiated) {
            Log.w(LOG_DOMAIN, "BTSocket %s: peer not found for L2CAP open", this);
            socketQueue.execute(() ->
                    closeWithError(ErrorDomain.NETWORK, NetworkError.HOST_DOWN,
                            "Bluetooth peer not found: " + peerID));
        }
    }

    /** Called by BleService / CblBleDevice once the BluetoothSocket is connected. */
    public void onChannelOpened(@NonNull BluetoothSocket socket) {
        socketQueue.execute(() -> {
            if (!connecting) {
                try { socket.close(); } catch (IOException ignore) {}
                return;
            }
            connecting = false;
            setChannel(socket);
            notifyConnected();
        });
    }

    private void setChannel(@NonNull BluetoothSocket socket) {
        btSocket = socket;
        try {
            inputStream  = socket.getInputStream();
            outputStream = socket.getOutputStream();
        } catch (IOException e) {
            Log.w(LOG_DOMAIN, "BTSocket %s: failed to get streams", this, e);
            closeWithError(ErrorDomain.NETWORK, NetworkError.BROKEN_PIPE, e.getMessage());
            return;
        }
        readExecutor.execute(this::readLoop);
        Log.d(LOG_DOMAIN, "BTSocket %s: channel ready, read loop started", this);
    }

    private void notifyConnected() {
        Log.i(LOG_DOMAIN, "BTSocket %s: CONNECTED", this);
        synchronized (c4socket.getLock()) {
            c4socket.ackOpenToCore(101, null);  // HTTP 101 Switching Protocols
        }
    }

    // -------------------------------------------------------------------------
    // SocketFromCore – LiteCore → socket callbacks
    // -------------------------------------------------------------------------

    /** LiteCore requests open (no-op for BT; already handled in factory open callback). */
    @Override
    public void coreRequestsOpen() {
        Log.d(LOG_DOMAIN, "^BTSocket %s: coreRequestsOpen (redundant, ignoring)", this);
    }

    /** LiteCore wants to send data over the wire. */
    @Override
    public void coreWrites(@NonNull byte[] data) {
        Log.d(LOG_DOMAIN, "^BTSocket %s: coreWrites %d bytes", this, data.length);
        socketQueue.execute(() -> {
            pendingWrites.addLast(data);
            doWrite();
        });
    }

    /** LiteCore finished processing byteCount bytes we delivered. */
    @Override
    public void coreAcksWrite(long byteCount) {
        Log.d(LOG_DOMAIN, "^BTSocket %s: coreAcksWrite %d", this, byteCount);
        socketQueue.execute(() -> {
            final boolean wasThrottled = isReadThrottled();
            receivedBytesPending -= byteCount;
            if (wasThrottled && !isReadThrottled()) {
                readExecutor.execute(this::readLoop);
            }
        });
    }

    /** LiteCore requests close (byte-stream framing). */
    @Override
    public void coreClosed() {
        Log.i(LOG_DOMAIN, "^BTSocket %s: coreClosed", this);
        socketQueue.execute(() -> closeWithError(0, 0, null));
    }

    /** LiteCore requests WebSocket-level close (message framing). */
    @Override
    public void coreRequestsClose(@NonNull CloseStatus status) {
        Log.i(LOG_DOMAIN, "^BTSocket %s: coreRequestsClose %d/%d", this, status.domain, status.code);
        socketQueue.execute(() -> closeWithError(status.domain, status.code, status.message));
    }

    private void postToSocketQueue(@NonNull Runnable r) {
        if (!socketQueue.isShutdown()) { socketQueue.execute(r); }
    }

    @SuppressWarnings("PMD.CloseResource")
    private void readLoop() {
        final InputStream in = inputStream;
        if (in == null) { return; }
        final byte[] buf = new byte[READ_BUFFER_SIZE];

        try {
            while (!closing) {
                if (isReadThrottled()) { return; } // coreAcksWrite will re-submit

                final int n;
                try { n = in.read(buf); }
                catch (IOException e) {
                    if (!closing) {
                        postToSocketQueue(() ->
                                closeWithError(ErrorDomain.NETWORK, NetworkError.CONNECTION_RESET, e.getMessage()));
                    }
                    return;
                }

                if (n < 0) {
                    postToSocketQueue(() -> closeWithError(0, 0, null));
                    return;
                }
                if (n == 0) { continue; }

                final byte[] frame = new byte[n];
                System.arraycopy(buf, 0, frame, 0, n);

                postToSocketQueue(() -> {
                    receivedBytesPending += frame.length;
                    Log.d(LOG_DOMAIN, "BTSocket %s: <<< %d bytes [%d pending]",
                            this, frame.length, receivedBytesPending);
                    synchronized (c4socket.getLock()) {
                        c4socket.writeToCore(frame);
                    }
                });
            }
        } catch (Exception e) {
            if (!closing) {
                postToSocketQueue(() ->
                        closeWithError(ErrorDomain.NETWORK, NetworkError.CONNECTION_RESET, e.getMessage()));
            }
        }
    }

    @SuppressWarnings("PMD.CloseResource")
    private void doWrite() {
        final OutputStream out = outputStream;
        if (out == null) { return; }

        while (!pendingWrites.isEmpty()) {
            final byte[] data = pendingWrites.pollFirst();
            if (data == null) { break; }
            try {
                out.write(data);
                out.flush();
                Log.d(LOG_DOMAIN, "BTSocket %s: >>> %d bytes", this, data.length);
                synchronized (c4socket.getLock()) {
                    c4socket.ackWriteToCore(data.length);
                }
            } catch (IOException e) {
                Log.w(LOG_DOMAIN, "BTSocket %s: write error", this, e);
                closeWithError(ErrorDomain.NETWORK, NetworkError.BROKEN_PIPE, e.getMessage());
                return;
            }
        }
    }

    @SuppressWarnings("PMD.CloseResource")
    private void closeWithError(int domain, int code, @Nullable String message) {
        if (closing) { return; }
        closing = true;

        if (domain == 0) { Log.i(LOG_DOMAIN, "BTSocket %s: CLOSED", this); }
        else { Log.w(LOG_DOMAIN, "BTSocket %s: CLOSED err %d/%d '%s'", this, domain, code, message); }

        disconnect();

        final CloseStatus status = new CloseStatus(domain, code, message);
        synchronized (c4socket.getLock()) {
            c4socket.closeCore(status);
        }
    }

    @SuppressWarnings("PMD.CloseResource")
    private void disconnect() {
        connecting = false;
        final InputStream in   = inputStream;
        final OutputStream out = outputStream;
        final BluetoothSocket s = btSocket;

        inputStream  = null;
        outputStream = null;
        btSocket     = null;

        if (in  != null) { try { in.close();  } catch (IOException ignore) {} }
        if (out != null) { try { out.close(); } catch (IOException ignore) {} }
        if (s   != null) { try { s.close();   } catch (IOException ignore) {} }

        readExecutor.shutdownNow();
        socketQueue.shutdown();
    }

    private boolean isReadThrottled() {
        return receivedBytesPending >= MAX_RECEIVED_BYTES_PENDING;
    }

    @Override
    @NonNull
    public String toString() { return "BTSocket[" + peerID + "]"; }
}
