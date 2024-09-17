//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.internal.core.peers;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.couchbase.lite.CouchbaseLiteError;
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
 * <code>getBinding</code> will return null.
 * While it would be possible to accomplish something similar, perhaps by
 * passing the actual java reference to the native object, such an implementation
 * would require the native code to manage LocalRefs.... with the distinct
 * possibility of running out.
 * <p>
 *
 * @param <T> The type of the Java peer.
 */
// ??? There should be a nanny thread cleaning out all the ref -> null
public class TaggedWeakPeerBinding<T> extends WeakPeerBinding<T> {

    /**
     * Reserve a token.
     * Sometimes the object to be put into the map needs to know
     * its own token.  Pre-reserving it makes it possible to make it final.
     *
     * Don't leak keys.  If code throws an exception between this call
     * and when you actually bind the key, it will be lost.
     *
     * @return a unique value 3 <= key < Integer.MAX_VALUE - 1.
     */
    public synchronized long reserveKey() {
        long key;

        int i = 0;
        do {
            // Reasonable response to an unreasonable condition. h/t Pasin.
            if (i++ > 13) { throw new CouchbaseLiteError("No free binding tags"); }
            key = MathUtils.RANDOM.get().nextInt(Integer.MAX_VALUE - 5) + 4;
        }
        while (exists(key));
        super.set(key, null);

        return key;
    }

    /**
     * Bind an object to a token.
     *
     * @param key a previously reserved token
     * @param obj the object to be bound to the token.
     */
    @GuardedBy("this")
    @Override
    protected void preBind(long key, @NonNull T obj) {
        if (!exists(key)) { throw new CouchbaseLiteError("attempt to use un-reserved key"); }
    }

    /**
     * Get the object bound to the passed token.
     * Returns null if no object is bound to the key.
     * For legacy reasons, core holds these "contexts" as (void *), so they are longs.
     *
     * @param key a token created by <code>reserveKey()</code>
     * @return the bound object, or null if none exists.
     */
    @GuardedBy("this")
    @Override
    protected void preGetBinding(long key) {
        if ((key < 3) || (key >= Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("Key out of bounds: " + key);
        }
    }
}
