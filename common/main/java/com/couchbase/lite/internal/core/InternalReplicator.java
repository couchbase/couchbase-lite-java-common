//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.core;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;


@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class InternalReplicator {
    private C4Replicator c4Replicator;

    protected void setC4Replicator(@NonNull C4Replicator c4Repl) { c4Replicator = c4Repl; }

    @Nullable
    protected C4Replicator getC4Replicator() { return c4Replicator; }
}
