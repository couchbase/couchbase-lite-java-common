//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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

import java.util.Arrays;
import java.util.List;

import com.couchbase.lite.internal.utils.StringUtils;


public class IndexConfiguration extends AbstractIndex {
    private final List<String> expressions;

    IndexConfiguration(IndexType type, String expressions) {
        super(type, QueryLanguage.N1QL);
        this.expressions = Arrays.asList(expressions);
    }

    String getIndexSpec() { return StringUtils.join(",", expressions); }
}
