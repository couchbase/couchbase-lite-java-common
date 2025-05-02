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
package com.couchbase.lite.internal.core;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;


public class C4NestedQueryTest extends C4QueryBaseTest {
    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Before
    public final void setUpC4NestedQueryTest() throws LiteCoreException, IOException {
        loadJsonAsset("nested.json");
    }

    // - DB Query ANY nested
    @Test
    public void testDBQueryANYNested() throws LiteCoreException {
        compile("['ANY', 'Shape', ['.', 'shapes'], ['=', ['?', 'Shape', 'color'], 'red']]");
        Assert.assertEquals(Arrays.asList("0000001", "0000003"), run());
    }
}
