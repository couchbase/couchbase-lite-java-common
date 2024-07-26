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
package com.couchbase.lite;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.DbContext;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLSlice;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MContext;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.logging.Log;
import com.couchbase.lite.internal.utils.Internal;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * This class exists to provide access to package visible symbols, to MValue
 */
@Internal("This class is not part of the public API")
public abstract class MValueConverter {
    public static final class JavaValue {
        public final boolean cacheIt;
        @Nullable
        public final Object nVal;

        public JavaValue(boolean cacheIt, @Nullable Object nVal) {
            this.cacheIt = cacheIt;
            this.nVal = nVal;
        }
    }

    protected MValueConverter() { }

    //-------------------------------------------------------------------------
    // Protected methods
    //-------------------------------------------------------------------------
    @NonNull
    protected JavaValue toJava(@NonNull MValue val, @Nullable MCollection parent) {
        final FLValue value = Preconditions.assertNotNull(val.getFLValue(), "value");
        switch (value.getType()) {
            case FLSlice.ValueType.DICT:
                return mValueToDictionary(val, Preconditions.assertNotNull(parent, "parent"));
            case FLSlice.ValueType.ARRAY:
                return new JavaValue(
                    true,
                    ((parent == null) || !parent.isMutable())
                        ? new Array(val, parent)
                        : new MutableArray(val, parent));
            case FLSlice.ValueType.DATA:
                return new JavaValue(false, new Blob("application/octet-stream", value.asData()));
            default:
                return new JavaValue(false, value.asJava());
        }
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    @NonNull
    private JavaValue mValueToDictionary(@NonNull MValue mv, @NonNull MCollection parent) {
        final MContext ctxt = parent.getContext();
        if (!(ctxt instanceof DbContext)) { throw new CouchbaseLiteError("Context is not DbContext: " + ctxt); }
        final DbContext context = (DbContext) ctxt;

        final FLDict flDict = Preconditions.assertNotNull(mv.getFLValue(), "MValue").asFLDict();

        final FLValue flType = flDict.get(Blob.META_PROP_TYPE);
        final String type = (flType == null) ? null : flType.asString();
        if (Blob.TYPE_BLOB.equals(type) || ((type == null) && isOldAttachment(flDict))) {
            return new JavaValue(
                true,
                new Blob(Preconditions.assertNotNull(context.getDatabase(), "database"), flDict.asDict()));
        }

        return new JavaValue(
            true,
            (parent.isMutable()) ? new MutableDictionary(mv, parent) : new Dictionary(mv, parent));
    }

    // At some point in the past, attachments were dictionaries in a top-level
    // element named "_attachments". Those dictionaries contained at least the
    // properties listed here.
    // Unfortunately, at this point, we don't know the name of the parent element.
    // Heuristically, we just look for the properties and cross our fingers.
    private boolean isOldAttachment(@NonNull FLDict flDict) {
        final boolean ret = (flDict.get(Blob.PROP_DIGEST) != null)
            && (flDict.get(Blob.PROP_LENGTH) != null)
            && (flDict.get(Blob.PROP_STUB) != null)
            && (flDict.get(Blob.PROP_REVPOS) != null);
        if (ret) { Log.i(LogDomain.DATABASE, "Old style blob: " + flDict.asDict()); }
        return ret;
    }
}
