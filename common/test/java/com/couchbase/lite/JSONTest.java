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

import java.util.Date;

import org.junit.Test;

import com.couchbase.lite.internal.utils.JSONUtils;

import static org.junit.Assert.assertEquals;


@SuppressWarnings("ConstantConditions")
public class JSONTest extends BaseTest {

    // Verify that round trip String -> Date -> String doesn't alter the string (#1611)
    @Test
    public void testJSONDateRoundTrip() {
        final Date now = new Date();
        String dateStr = JSONUtils.toJSONString(now);
        Date date = JSONUtils.toDate(dateStr);
        assertEquals(now.getTime(), date.getTime());
        assertEquals(dateStr, JSONUtils.toJSONString(date));
    }
}
