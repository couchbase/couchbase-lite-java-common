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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.couchbase.lite.BaseTest;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.utils.Report;


public class EncodingTest extends BaseTest {
    // https://github.com/couchbase/couchbase-lite-android/issues/1453
    @Test
    public void testFLEncode() throws LiteCoreException {
        testRoundTrip(42L);
        testRoundTrip(Long.MIN_VALUE);
        testRoundTrip("Fleece");
        testRoundTrip("Goodbye cruel world".toCharArray(), "Goodbye cruel world");
        Map<String, Object> map = new HashMap<>();
        map.put("foo", "bar");
        testRoundTrip(map);
        testRoundTrip(Arrays.asList((Object) "foo", "bar"));
        testRoundTrip(true);
        testRoundTrip(3.14F);
        testRoundTrip(Math.PI);
    }

    @Test
    public void testFLEncodeUTF8() throws LiteCoreException {
        testRoundTrip("Goodbye cruel world"); // one byte utf-8 chars
        testRoundTrip("Goodbye cruel £ world"); // a two byte utf-8 chars
        testRoundTrip("Goodbye cruel ᘺ world"); // a three byte utf-8 char
        testRoundTrip("Hello \uD83D\uDE3A World"); // a four byte utf-8 char: 😺
        testRoundTrip("Goodbye cruel \uD83D world", ""); // cheshire cat: half missing.
        testRoundTrip("Goodbye cruel \uD83D\uC03A world", ""); // a bad cat
    }

    @Test
    public void testFLEncodeBadUTF8() throws LiteCoreException {
        skipTestWhen("WINDOWS");
        testRoundTrip("Goodbye cruel \uD83D\uDE3A\uDE3A world", ""); // a cat and a half
    }

    // Oddly windows seems to parse this differently...
    @Test
    public void testFLEncodeUTF8Win() throws LiteCoreException {
        skipTestUnless("WINDOWS");
        testRoundTrip("Goodbye cruel \uD83D\uDE3A\uDE3A world"); // a cat and a half
    }

    // These tests are built on the following fleece encoding.  Start at the end.
    // 0000: 44                                [byte 0: 44: high order 4: this is a string; low order 4: 4 bytes long]
    // 0001:     f0 9f 98 BA 00: "😺"          [bytes 1-4, cat; byte 5, 0: pad to align on even byte]
    // 0006: 80 03             : &"😺" (@0000) [byte 0, 80: this is a pointer; byte 1, 03: 3 2-byte units ago]
    @Test
    public void testUTF8Slices() {
        // https://github.com/couchbase/couchbase-lite-android/issues/1742
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0xBA, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            "\uD83D\uDE3A");

        // same as above, but byte 3 of the character is not legal
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xF0, (byte) 0x9F, (byte) 0x41, (byte) 0xBA, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);

        // two 2-byte characters
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xC2, (byte) 0xA3, (byte) 0xC2, (byte) 0xA5, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            "£¥");

        // two 2-byte characters, 2nd byte of 2nd char is not legal
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xC2, (byte) 0xA3, (byte) 0xC2, (byte) 0x41, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);

        // a three byte character and a one byte character
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xE1, (byte) 0x98, (byte) 0xBA, (byte) 0x41, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            "ᘺA");

        // a three byte character and a continuation byte
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xE1, (byte) 0x98, (byte) 0xBA, (byte) 0xBA, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);

        // a three byte character with an illegal 2nd byte
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xE1, (byte) 0x98, (byte) 0x41, (byte) 0x41, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);

        // four single byte characters
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            "AAAA");

        // four single byte characters one is a continuation character
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0x41, (byte) 0x98, (byte) 0x41, (byte) 0x41, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);

        // four single byte characters, byte 4 is illegal anywhere
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0xC0, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);
    }

    private void testRoundTrip(Object item) throws LiteCoreException { testRoundTrip(item, item); }

    private void testRoundTrip(Object item, Object expected) throws LiteCoreException {
        try (FLEncoder encoder = FLEncoder.getManagedEncoder()) {
            Assert.assertTrue(encoder.writeValue(item));

            final FLValue flValue;
            try (FLSliceResult slice = encoder.finish2()) {
                flValue = FLValue.fromData(slice);
                Assert.assertNotNull(flValue);

                Object obj = toObject(flValue);
                Report.log("ROUND TRIP SLICE: '%s'; FROM: '%s'; EXPECTING: '%s'", obj, item, expected);
                Assert.assertEquals(expected, obj);
            }
        }
    }

    private void testSlice(byte[] utf8Slice, String expected) {
        FLValue flValue = FLValue.fromData(utf8Slice);
        Object obj = toObject(flValue);
        Report.log("DECODE SLICE: '%s'; EXPECTED: '%s'", obj, expected);
        Assert.assertEquals(expected, obj);
    }

    @Nullable
    public static Object toObject(@NonNull FLValue flValue) { return flValue.toJava(); }
}
