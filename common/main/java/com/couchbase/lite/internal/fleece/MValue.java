//
// Copyright (c) 2020 Couchbase, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal.fleece;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.BaseMValue;
import com.couchbase.lite.Blob;
import com.couchbase.lite.CouchbaseLiteError;
import com.couchbase.lite.internal.DbContext;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The Mutable Fleece implementations are different across the various platforms. Here's what
 * I can figure out (though I really have only rumors to go by).  I think that Jens did two
 * implementations, the second of which was an attempt to make life easier for the platforms.
 * Word has it that it did not succeed in doing that.  iOS still uses the first
 * implementation. Java tried to use the first implementation but, because of a problem with
 * running out of LocalRefs, the original developer for this platform (Java/Android) chose,
 * more or less, to port that first implementation into Java. I think that .NET did something
 * similar. As I understand it both Jim and Sandy tried to update .NET to use Jens' second
 * implementation somewhere in the 2.7 time-frame. They had, at most, partial success.
 * <p>
 * In 9/2020 (CBL-246), I tried to convert this code to use LiteCore's MutableFleece package
 * (that's Jens' second implementations). Both Jim and Jens warned me, without specifics,
 * that doing so might be more trouble than it was worth. Although the LiteCore
 * implementation of Mutable Fleece is relatively clear, this existing Java code is just
 * plain bizarre. It seems to work, though. I have seen very few problems that could be traced
 * to it. I've cleaned it up a bit but other than that, I'm leaving it alone.  I suggest
 * you do the same, unless something changes to make the benefit side of the C/B fraction
 * more interesting.
 * <p>
 * The regrettable upside-down dependency on BaseMValue provides access to package
 * visible symbols in com.couchbase.lite.
 * <p>
 * It worries me that this isn't thread safe... but, as I say, it hasn't been a significant issue.
 * <p>
 * 3/2024 (CBL-5486): I've seen a problem!
 * If the parent, the object holding the Fleece reference, is closed, the Fleece object backing
 * all the contained objects, is freed.
 * <p>
 * There are more notes on my attempts to tame this code in &lt;root&gt;/docs/FixFleece.md
 */
public class MValue extends BaseMValue implements FleeceEncodable {

    //-------------------------------------------------------------------------
    // Static members
    //-------------------------------------------------------------------------

    static final MValue EMPTY = new MValue(null, null) {
        @Override
        public boolean isEmpty() { return true; }
    };

    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    @Nullable
    private FLValue flValue;
    @Nullable
    private Object cachedValue;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public MValue(@Nullable Object obj) { this(obj, null); }

    MValue(@Nullable FLValue val) { this(null, val); }

    private MValue(@Nullable Object obj, @Nullable FLValue val) {
        cachedValue = obj;
        flValue = val;
    }

    //-------------------------------------------------------------------------
    // Public methods
    //-------------------------------------------------------------------------

    @Nullable
    public FLValue getFLValue() { return flValue; }

    public boolean isEmpty() { return false; }

    public boolean isMutated() { return flValue == null; }

    public void mutate() {
        Preconditions.assertNotNull(cachedValue, "Native object");
        flValue = null;
    }

    @Override
    public void encodeTo(@NonNull FLEncoder enc) {
        if (isEmpty()) { throw new CouchbaseLiteError("MValue is empty."); }

        if (flValue != null) { enc.writeValue(flValue); }
        else if (cachedValue != null) { enc.writeValue(cachedValue); }
        else { enc.writeNull(); }
    }

    @Nullable
    public Object toJFleece(@Nullable MCollection parent) {
        if ((cachedValue != null) || (flValue == null)) { return cachedValue; }

        switch (flValue.getType()) {
            case FLValue.DICT:
                cachedValue = toDictionary(parent);
                return cachedValue;
            case FLValue.ARRAY:
                cachedValue = getArray(this, parent);
                return cachedValue;
            case FLValue.DATA:
                return new Blob("application/octet-stream", flValue.asByteArray());
            default:
                return flValue.toJava();
        }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    @NonNull
    private Object toDictionary(@Nullable MCollection parent) {
        final FLDict flDict = Preconditions.assertNotNull(flValue, "MValue").asFLDict();

        final FLValue flType = flDict.get(META_PROP_TYPE);
        final String type = (flType == null) ? null : flType.asString();

        if (TYPE_BLOB.equals(type) || isOldAttachment(type, flDict)) {
            final MContext ctxt = Preconditions.assertNotNull(parent, "parent").getContext();
            if (!(ctxt instanceof DbContext)) { throw new CouchbaseLiteError("Context is not DbContext: " + ctxt); }
            return getBlob((DbContext) ctxt, flDict);
        }

        return getDictionary(this, parent);
    }

    // This is a really, really ugly hack.
    // It is necessary because blobs are stored in two different ways in a document.
    // LiteCore store them as dictionaries with the meta-type "blob" ("@type": "blob")
    // at the path at which they were inserted into the document.
    // The SG stores them as dictionaries at the top level of the document in a dictionary
    // at the key "_attachments".  We, apparently,support clients use of either method of
    // managing their blobs.  Feh.  There is an extended discussion in <root>/docs/FixFleece.md
    private boolean isOldAttachment(@Nullable String type, @NonNull FLDict flDict) {
        return (type == null)
            && (flDict.get(PROP_DIGEST) != null)
            && (flDict.get(PROP_LENGTH) != null)
            && (flDict.get(PROP_STUB) != null)
            && (flDict.get(PROP_REVPOS) != null);
    }
}
