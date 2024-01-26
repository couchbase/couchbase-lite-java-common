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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.internal.utils.StringUtils;


public abstract class IndexConfiguration extends AbstractIndex {
    @NonNull
    private final List<String> expressions;

    IndexConfiguration(@NonNull String... expressions) { this(Arrays.asList(expressions)); }

    IndexConfiguration(@NonNull List<String> expressions) {
        this.expressions = Preconditions.assertNotEmpty(
            Fn.filterToList(expressions, i -> (i != null)),
            "expression list");
    }

    @NonNull
    public List<String> getExpressions() { return new ArrayList<>(expressions); }

    @NonNull
    String getIndexSpec() { return StringUtils.join(",", expressions); }
}
