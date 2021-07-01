package com.couchbase.lite.internal.core;

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.couchbase.lite.internal.utils.MathUtils;


/**
 * This class provides a way for native objects to reference their
 * Java peers.  The <code>reserveKey()</code> method creates a unique token
 * that the native code can redeem, using <code>getObjFromContext</code> for
 * the Java object that is its peer.
 * Note that the token is a 31 bit integer (a positive int) so that it is
 * relatively immune to sign extension.
 * The internal map holds only a weak reference to the Java object.
 * If nobody in java-land cares about the peer-pair anymore, calls to
 * <code>getObjFromContext</code> will return null.
 * While it would be possible to accomplish something similar, perhaps by
 * passing the actual java reference to the native object, such an implementation
 * would require the native code to manage LocalRefs.... with the distinct
 * possibility of running out.
 * <p>
 * !!! There should be a nanny thread cleaning out all the ref -> null
 *
 * @param <T> The type of the Java peer.
 */
public class NativeContext<T> {
    @GuardedBy("this")
    @NonNull
    private final Map<Integer, WeakReference<T>> contexts = new HashMap<>();

    /**
     * Reserve a token.
     * Sometimes the object to be put into the map needs to know
     * its own token.  Pre-reserving it makes it possible to make it final.
     *
     * @return a unique positive int key.
     */
    public synchronized int reserveKey() {
        int key;

        do { key = MathUtils.RANDOM.get().nextInt(Integer.MAX_VALUE); }
        while (contexts.containsKey(key));
        contexts.put(key, null);

        return key;
    }

    /**
     * Bind an object to a token.
     *
     * @param key a previously reserved token
     * @param obj the object to be bound to the token.
     */
    public synchronized void bind(int key, @NonNull T obj) {
        if (!contexts.containsKey(key)) { throw new IllegalStateException("attempt to use un-reserved key"); }
        if (contexts.get(key) != null) { throw new IllegalStateException("attempt to rebind key"); }
        contexts.put(key, new WeakReference<>(obj));
    }

    /**
     * Remove the binding for a key
     * Re-entrant.
     *
     * @param key the key to be unbound.
     */
    public synchronized void unbind(int key) { contexts.remove(key); }

    /**
     * Get the object bound to the passed token.
     * Returns null if no object is bound to the key.
     * For legacy reasons, core holds these "contexts" as (void *), so they are longs.
     *
     * @param context a token created by <code>reserveKey()</code>
     * @return the bound object, or null if none exists.
     */
    @Nullable
    public synchronized T getObjFromContext(long context) {
        if ((context < 0) || (context > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("Context out of bounds: " + context);
        }

        final Integer key = (int) context;

        final WeakReference<T> ref = contexts.get(key);
        if (ref == null) { return null; }

        final T obj = ref.get();
        if (obj == null) {
            // clean up dead objects...
            contexts.remove(key);
            return null;
        }

        return obj;
    }

    @VisibleForTesting
    synchronized int size() { return contexts.size(); }

    @VisibleForTesting
    synchronized void clear() { contexts.clear(); }

    @NonNull
    @VisibleForTesting
    synchronized Set<Integer> keySet() { return contexts.keySet(); }
}
