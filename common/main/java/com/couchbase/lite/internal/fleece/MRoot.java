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

import com.couchbase.lite.internal.utils.Preconditions;


public final class MRoot extends MCollection {
    public MRoot(@Nullable MContext context, @Nullable FLValue flValue, boolean isMutable) {
        super(new MValue(flValue), null, context, isMutable);
    }

    @Override
    public int count() { return 0; }

    @Override
    public void encodeTo(@NonNull FLEncoder enc) { content().encodeTo(enc); }

    @Override
    public boolean isMutated() { return content().isMutated(); }

    @Nullable
    public Object toJFleece() { return content().toJFleece(this); }

    @NonNull
    private MValue content() { return Preconditions.assertNotNull(getSlot(), "slot"); }
}
