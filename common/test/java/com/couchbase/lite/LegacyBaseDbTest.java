

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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.JSONUtils;
import com.couchbase.lite.internal.utils.PlatformUtils;
import com.couchbase.lite.internal.utils.Report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public abstract class LegacyBaseDbTest extends BaseTest {
    @FunctionalInterface
    public interface DocValidator extends Fn.ConsumerThrows<Document, CouchbaseLiteException> { }

    public static final String TEST_DATE = "2019-02-21T05:37:22.014Z";
    public static final String BLOB_CONTENT = "Knox on fox in socks in box. Socks on Knox and Knox in box.";

    public static C4Database getC4Db(@NonNull Database db) { return db.getOpenC4Database(); }

    protected static <T extends Comparable<T>> void assertContents(List<T> l1, T... contents) {
        List<T> l2 = Arrays.asList(contents);
        assertTrue(l1.containsAll(l2) && l2.containsAll(l1));
    }

    // Attempt to standardize doc ids.
    public static String docId(int i) { return "doc" + i; }

    // There are tests that depend on dictionary ordering of documents:
    // This version of docId will sort doc-001 before doc-009 and before doc-010.
    public static String jsonDocId(int i) { return String.format(Locale.ENGLISH, "doc-%03d", i); }


    protected Database baseTestDb;

    @Before
    public final void setUpBaseDbTest() throws CouchbaseLiteException {
        baseTestDb = createDb("base_db");
        Report.log("Created base test DB: " + baseTestDb);
        assertNotNull(baseTestDb);
        synchronized (baseTestDb.getDbLock()) { assertTrue(baseTestDb.isOpenLocked()); }
    }

    @After
    public final void tearDownBaseDbTest() {
        eraseDb(baseTestDb);
        Report.log("Deleted baseTestDb: " + baseTestDb);
    }

    protected final Document createSingleDocInBaseTestDb(String docID) throws CouchbaseLiteException {
        final long n = baseTestDb.getCount();

        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("key", 1);
        Document savedDoc = saveDocInBaseTestDb(doc);

        assertEquals(n + 1, baseTestDb.getCount());
        assertEquals(1, savedDoc.getSequence());

        return savedDoc;
    }

    protected final void createDocsInDb(int first, int count, Database db) throws CouchbaseLiteException {
        db.inBatch(() -> {
            for (int i = first; i < first + count; i++) {
                final MutableDocument doc = new MutableDocument(docId(i));
                doc.setNumber("count", i);
                doc.setString("inverse", "minus-" + i);
                db.save(doc);
            }
        });
    }

    protected final Document saveDocInBaseTestDb(MutableDocument doc) throws CouchbaseLiteException {
        baseTestDb.save(doc);

        Document savedDoc = baseTestDb.getDocument(doc.getId());
        assertNotNull(savedDoc);
        assertEquals(doc.getId(), savedDoc.getId());

        return savedDoc;
    }

    protected final Document saveDocInBaseTestDb(MutableDocument doc, DocValidator validator)
        throws CouchbaseLiteException {
        validator.accept(doc);

        Document savedDoc = saveDocInBaseTestDb(doc);
        validator.accept(doc);
        validator.accept(savedDoc);

        return savedDoc;
    }

    // used from other package's tests
    protected final void populateData(MutableDocument doc) {
        doc.setValue("true", true);
        doc.setValue("false", false);
        doc.setValue("string", "string");
        doc.setValue("zero", 0);
        doc.setValue("one", 1);
        doc.setValue("minus_one", -1);
        doc.setValue("one_dot_one", 1.1);
        doc.setValue("date", JSONUtils.toDate(TEST_DATE));
        doc.setValue("null", null);

        // Dictionary:
        MutableDictionary dict = new MutableDictionary();
        dict.setValue("street", "1 Main street");
        dict.setValue("city", "Mountain View");
        dict.setValue("state", "CA");
        doc.setValue("dict", dict);

        // Array:
        MutableArray array = new MutableArray();
        array.addValue("650-123-0001");
        array.addValue("650-123-0002");
        doc.setValue("array", array);

        // Blob:
        doc.setValue("blob", new Blob("text/plain", BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)));
    }

    // used from other package's tests
    protected final void populateDataByTypedSetter(MutableDocument doc) {
        doc.setBoolean("true", true);
        doc.setBoolean("false", false);
        doc.setString("string", "string");
        doc.setNumber("zero", 0);
        doc.setInt("one", 1);
        doc.setLong("minus_one", -1);
        doc.setDouble("one_dot_one", 1.1);
        doc.setDate("date", JSONUtils.toDate(TEST_DATE));
        doc.setString("null", null);

        // Dictionary:
        MutableDictionary dict = new MutableDictionary();
        dict.setString("street", "1 Main street");
        dict.setString("city", "Mountain View");
        dict.setString("state", "CA");
        doc.setDictionary("dict", dict);

        // Array:
        MutableArray array = new MutableArray();
        array.addString("650-123-0001");
        array.addString("650-123-0002");
        doc.setArray("array", array);

        // Blob:
        doc.setValue("blob", new Blob("text/plain", BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)));
    }

    protected final String readJSONResource(String name) throws IOException {
        StringBuilder buf = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(PlatformUtils.getAsset(name)))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.trim().isEmpty()) { buf.append(line); }
            }
        }
        return buf.toString();
    }

    @NonNull
    protected final MutableArray makeArray() {
        // A small array
        final MutableArray simpleArray = new MutableArray();
        simpleArray.addInt(54);
        simpleArray.addString("Joplin");

        // A small dictionary
        final MutableDictionary simpleDict = new MutableDictionary();
        simpleDict.setInt("sdict-1", 58);
        simpleDict.setString("sdict-2", "Winehouse");

        final MutableArray array = new MutableArray();
        array.addValue(null);

        array.addBoolean(true);
        array.addBoolean(false);

        array.addInt(0);
        array.addInt(Integer.MIN_VALUE);
        array.addInt(Integer.MAX_VALUE);

        array.addLong(0L);
        array.addLong(Long.MIN_VALUE);
        array.addLong(Long.MAX_VALUE);

        array.addFloat(0.0F);
        array.addFloat(Float.MIN_VALUE);
        array.addFloat(Float.MAX_VALUE);

        array.addDouble(0.0);
        array.addDouble(Double.MIN_VALUE);
        array.addDouble(Double.MAX_VALUE);

        array.addNumber(null);
        array.addNumber(0);
        array.addNumber(Float.MIN_VALUE);
        array.addNumber(Long.MIN_VALUE);

        array.addString(null);
        array.addString("Harry");

        array.addDate(null);
        array.addDate(JSONUtils.toDate(TEST_DATE));

        array.addArray(null);
        array.addArray(simpleArray);

        array.addDictionary(null);
        array.addDictionary(simpleDict);

        return array;
    }

    protected final void verifyArray(JSONArray jArray) throws JSONException {
        assertEquals(27, jArray.length());

        assertEquals(JSONObject.NULL, jArray.get(0));

        assertEquals(true, jArray.get(1));
        assertEquals(false, jArray.get(2));

        assertEquals(0, jArray.get(3));
        assertEquals(Integer.MIN_VALUE, jArray.get(4));
        assertEquals(Integer.MAX_VALUE, jArray.get(5));

        assertEquals(0, jArray.get(6));
        assertEquals(Long.MIN_VALUE, jArray.get(7));
        assertEquals(Long.MAX_VALUE, jArray.get(8));

        assertEquals(0.0F, jArray.getDouble(9), 0.001);
        assertEquals(Float.MIN_VALUE, (float) jArray.getDouble(10), 0.001);
        assertEquals(Float.MAX_VALUE, (float) jArray.getDouble(11), 100.0);

        assertEquals(0.0, jArray.getDouble(12), 0.001);
        assertEquals(Double.MIN_VALUE, jArray.getDouble(13), 0.001);
        assertEquals(Double.MAX_VALUE, jArray.getDouble(14), 1);

        assertEquals(JSONObject.NULL, jArray.get(15));
        assertEquals(0, jArray.getLong(16));
        assertEquals(Float.MIN_VALUE, jArray.getDouble(17), 0.001);
        assertEquals(Long.MIN_VALUE, jArray.getLong(18), 0.001);

        assertEquals(JSONObject.NULL, jArray.get(19));
        assertEquals("Harry", jArray.get(20));

        assertEquals(JSONObject.NULL, jArray.get(21));
        assertEquals(TEST_DATE, jArray.get(22));

        assertEquals(JSONObject.NULL, jArray.get(23));
        assertEquals(JSONArray.class, jArray.get(24).getClass());

        assertEquals(JSONObject.NULL, jArray.get(25));
        assertEquals(JSONObject.class, jArray.get(26).getClass());

        assertEquals(JSONObject.NULL, jArray.get(25));
        assertEquals(JSONObject.class, jArray.get(26).getClass());
    }

    @SuppressWarnings("UnnecessaryBoxing")
    protected final void verifyArray(@Nullable ArrayInterface array) {
        assertNotNull(array);

        assertEquals(27, array.count());

        //#0 array.addValue(null);
        assertNull(array.getValue(0));
        assertFalse(array.getBoolean(0));
        assertEquals(0, array.getInt(0));
        assertEquals(0L, array.getLong(0));
        assertEquals(0.0F, array.getFloat(0), 0.001F);
        assertEquals(0.0, array.getDouble(0), 0.001);
        assertNull(array.getNumber(0));
        assertNull(array.getString(0));
        assertNull(array.getDate(0));
        assertNull(array.getBlob(0));
        assertNull(array.getArray(0));
        assertNull(array.getDictionary(0));

        //#1 array.addBoolean(true);
        assertEquals(Boolean.TRUE, array.getValue(1));
        assertTrue(array.getBoolean(1));
        assertEquals(1, array.getInt(1));
        assertEquals(1L, array.getLong(1));
        assertEquals(1.0F, array.getFloat(1), 0.001F);
        assertEquals(1.0, array.getDouble(1), 0.001);
        assertEquals(1, array.getNumber(1));
        assertNull(array.getString(1));
        assertNull(array.getDate(1));
        assertNull(array.getBlob(1));
        assertNull(array.getArray(1));
        assertNull(array.getDictionary(1));

        //#2 array.addBoolean(false);
        assertEquals(Boolean.FALSE, array.getValue(2));
        assertFalse(array.getBoolean(2));
        assertEquals(0, array.getInt(2));
        assertEquals(0L, array.getLong(2));
        assertEquals(0.0F, array.getFloat(2), 0.001F);
        assertEquals(0.0, array.getDouble(2), 0.001);
        assertEquals(0, array.getNumber(2));
        assertNull(array.getString(2));
        assertNull(array.getDate(2));
        assertNull(array.getBlob(2));
        assertNull(array.getArray(2));
        assertNull(array.getDictionary(2));

        //#3 array.addInt(0);
        assertEquals(0L, array.getValue(3));
        assertFalse(array.getBoolean(3));
        assertEquals(0, array.getInt(3));
        assertEquals(0L, array.getLong(3));
        assertEquals(0.0F, array.getFloat(3), 0.001F);
        assertEquals(0.0, array.getDouble(3), 0.001);
        assertEquals(0L, array.getNumber(3));
        assertNull(array.getString(3));
        assertNull(array.getDate(3));
        assertNull(array.getBlob(3));
        assertNull(array.getArray(3));
        assertNull(array.getDictionary(3));

        //#4 array.addInt(Integer.MIN_VALUE);
        assertEquals(Long.valueOf(Integer.MIN_VALUE), array.getValue(4));
        assertTrue(array.getBoolean(4));
        assertEquals(Integer.MIN_VALUE, array.getInt(4));
        assertEquals((long) Integer.MIN_VALUE, array.getLong(4));
        assertEquals((float) Integer.MIN_VALUE, array.getFloat(4), 0.001F);
        assertEquals((double) Integer.MIN_VALUE, array.getDouble(4), 0.001);
        assertEquals(Long.valueOf(Integer.MIN_VALUE), array.getNumber(4));
        assertNull(array.getString(4));
        assertNull(array.getDate(4));
        assertNull(array.getBlob(4));
        assertNull(array.getArray(4));
        assertNull(array.getDictionary(4));

        //#5 array.addInt(Integer.MAX_VALUE);
        assertEquals(Long.valueOf(Integer.MAX_VALUE), array.getValue(5));
        assertTrue(array.getBoolean(5));
        assertEquals(Integer.MAX_VALUE, array.getInt(5));
        assertEquals((long) Integer.MAX_VALUE, array.getLong(5));
        assertEquals((float) Integer.MAX_VALUE, array.getFloat(5), 100.0F);
        assertEquals((double) Integer.MAX_VALUE, array.getDouble(5), 100.0);
        assertEquals(Long.valueOf(Integer.MAX_VALUE), array.getNumber(5));
        assertNull(array.getString(5));
        assertNull(array.getDate(5));
        assertNull(array.getBlob(5));
        assertNull(array.getArray(5));
        assertNull(array.getDictionary(5));

        //#6 array.addLong(0L);
        assertEquals(Long.valueOf(0L), array.getValue(6));
        assertFalse(array.getBoolean(6));
        assertEquals(0, array.getInt(6));
        assertEquals(0L, array.getLong(6));
        assertEquals(0.0F, array.getFloat(6), 0.001F);
        assertEquals(0.0, array.getDouble(6), 0.001);
        assertEquals(Long.valueOf(0L), array.getNumber(6));
        assertNull(array.getString(6));
        assertNull(array.getDate(6));
        assertNull(array.getBlob(6));
        assertNull(array.getArray(6));
        assertNull(array.getDictionary(6));

        //#7 array.addLong(Long.MIN_VALUE);
        assertEquals(Long.valueOf(Long.MIN_VALUE), array.getValue(7));
        assertFalse(array.getBoolean(7));
        assertEquals(Long.valueOf(Long.MIN_VALUE).intValue(), array.getInt(7));
        assertEquals(Long.MIN_VALUE, array.getLong(7));
        assertEquals((float) Long.MIN_VALUE, array.getFloat(7), 0.001F);
        assertEquals((double) Long.MIN_VALUE, array.getDouble(7), 0.001);
        assertEquals(Long.valueOf(Long.MIN_VALUE), array.getNumber(7));
        assertNull(array.getString(7));
        assertNull(array.getDate(7));
        assertNull(array.getBlob(7));
        assertNull(array.getArray(7));
        assertNull(array.getDictionary(7));

        //#8 array.addLong(Long.MAX_VALUE);
        assertEquals(Long.valueOf(Long.MAX_VALUE), array.getValue(8));
        assertTrue(array.getBoolean(8));
        assertEquals(Long.valueOf(Long.MAX_VALUE).intValue(), array.getInt(8));
        assertEquals((long) Long.MAX_VALUE, array.getLong(8));
        assertEquals((float) Long.MAX_VALUE, array.getFloat(8), 100.0F);
        assertEquals((double) Long.MAX_VALUE, array.getDouble(8), 100.0);
        assertEquals(Long.valueOf(Long.MAX_VALUE), array.getNumber(8));
        assertNull(array.getString(8));
        assertNull(array.getDate(8));
        assertNull(array.getBlob(8));
        assertNull(array.getArray(8));
        assertNull(array.getDictionary(8));

        //#9 array.addFloat(0.0F);
        assertEquals(Float.valueOf(0.0F), array.getValue(9));
        assertFalse(array.getBoolean(9));
        assertEquals(0, array.getInt(9));
        assertEquals(0L, array.getLong(9));
        assertEquals(0.0F, array.getFloat(9), 0.001F);
        assertEquals(0.0, array.getDouble(9), 0.001);
        assertEquals(Float.valueOf(0.0F), array.getNumber(9));
        assertNull(array.getString(9));
        assertNull(array.getDate(9));
        assertNull(array.getBlob(9));
        assertNull(array.getArray(9));
        assertNull(array.getDictionary(9));

        //#10 array.addFloat(Float.MIN_VALUE);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(array.getValue(10)));
        assertFalse(array.getBoolean(10));
        assertEquals(Float.valueOf(Float.MIN_VALUE).intValue(), array.getInt(10), 0.001F);
        assertEquals(Float.valueOf(Float.MIN_VALUE).longValue(), array.getLong(10), 0.001F);
        assertEquals(Float.MIN_VALUE, array.getFloat(10), 0.001F);
        assertEquals((double) Float.MIN_VALUE, array.getDouble(10), 0.001);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(array.getNumber(10)));
        assertNull(array.getString(10));
        assertNull(array.getDate(10));
        assertNull(array.getBlob(10));
        assertNull(array.getArray(10));
        assertNull(array.getDictionary(10));

        //#11 array.addFloat(Float.MAX_VALUE);
        assertEquals(Float.valueOf(Float.MAX_VALUE), demoteToFloat(array.getValue(11)));
        assertTrue(array.getBoolean(11));
        // ??? Fails: assertEquals(Float.valueOf(Float.MAX_VALUE).intValue(), array.getInt(11));
        // ??? Fails: assertEquals(Float.valueOf(Float.MAX_VALUE).longValue(), array.getLong(11));
        assertEquals(Float.MAX_VALUE, array.getFloat(11), 100.0F);
        assertEquals((double) Float.MAX_VALUE, demoteToFloat(array.getDouble(11)), 100.0);
        assertEquals(Float.valueOf(Float.MAX_VALUE), demoteToFloat(array.getNumber(11)));
        assertNull(array.getString(11));
        assertNull(array.getDate(11));
        assertNull(array.getBlob(11));
        assertNull(array.getArray(11));
        assertNull(array.getDictionary(11));

        //#12 array.addDouble(0.0);
        assertEquals(Float.valueOf(0), array.getValue(12));
        assertFalse(array.getBoolean(12));
        assertEquals(0, array.getInt(12));
        assertEquals(0L, array.getLong(12));
        assertEquals(0.0F, array.getFloat(12), 0.001F);
        assertEquals(0.0, array.getDouble(12), 0.001);
        assertEquals(Float.valueOf(0), array.getNumber(12));
        assertNull(array.getString(12));
        assertNull(array.getDate(12));
        assertNull(array.getBlob(12));
        assertNull(array.getArray(12));
        assertNull(array.getDictionary(12));

        //#13 array.addDouble(Double.MIN_VALUE);
        assertEquals(Double.valueOf(Double.MIN_VALUE), array.getValue(13));
        assertFalse(array.getBoolean(13));
        assertEquals(Double.valueOf(Double.MIN_VALUE).intValue(), array.getInt(13));
        assertEquals(Double.valueOf(Double.MIN_VALUE).longValue(), array.getLong(13));
        assertEquals(Double.valueOf(Double.MIN_VALUE).floatValue(), array.getFloat(13), 0.001F);
        assertEquals(Double.MIN_VALUE, array.getDouble(13), 0.001);
        assertEquals(Double.valueOf(Double.MIN_VALUE), array.getNumber(13));
        assertNull(array.getString(13));
        assertNull(array.getDate(13));
        assertNull(array.getBlob(13));
        assertNull(array.getArray(13));
        assertNull(array.getDictionary(13));

        //#14 array.addDouble(Double.MAX_VALUE);
        assertEquals(Double.valueOf(Double.MAX_VALUE), array.getValue(14));
        assertTrue(array.getBoolean(14));
        // ??? Fails: assertEquals(Double.valueOf(Double.MAX_VALUE).intValue(), array.getInt(14));
        // ??? Fails: assertEquals(Double.valueOf(Double.MAX_VALUE).longValue(), array.getLong(14));
        assertEquals(Double.valueOf(Double.MAX_VALUE).floatValue(), array.getFloat(14), 100.0F);
        assertEquals(Double.MAX_VALUE, array.getDouble(14), 100.0);
        assertEquals(Double.valueOf(Double.MAX_VALUE), array.getNumber(14));
        assertNull(array.getString(14));
        assertNull(array.getDate(14));
        assertNull(array.getBlob(14));
        assertNull(array.getArray(14));
        assertNull(array.getDictionary(14));

        //#15 array.addNumber(null);
        assertNull(array.getValue(15));
        assertFalse(array.getBoolean(15));
        assertEquals(0, array.getInt(15));
        assertEquals(0L, array.getLong(15));
        assertEquals(0.0F, array.getFloat(15), 0.001F);
        assertEquals(0.0, array.getDouble(15), 0.001);
        assertNull(array.getNumber(15));
        assertNull(array.getString(15));
        assertNull(array.getDate(15));
        assertNull(array.getBlob(15));
        assertNull(array.getArray(15));
        assertNull(array.getDictionary(15));

        //#16 array.addNumber(0);
        assertEquals(0L, array.getValue(16));
        assertFalse(array.getBoolean(16));
        assertEquals(0, array.getInt(16));
        assertEquals(0L, array.getLong(16));
        assertEquals(0.0F, array.getFloat(16), 0.001F);
        assertEquals(0.0, array.getDouble(16), 0.001);
        assertEquals(0L, array.getNumber(16));
        assertNull(array.getString(16));
        assertNull(array.getDate(16));
        assertNull(array.getBlob(16));
        assertNull(array.getArray(16));
        assertNull(array.getDictionary(16));

        //#17 array.addNumber(Float.MIN_VALUE);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(array.getValue(17)));
        assertFalse(array.getBoolean(17));
        assertEquals(Float.valueOf(Float.MIN_VALUE).intValue(), array.getInt(17), 0.001F);
        assertEquals(Float.valueOf(Float.MIN_VALUE).longValue(), array.getLong(17), 0.001F);
        assertEquals(Float.MIN_VALUE, array.getFloat(17), 0.001F);
        assertEquals((double) Float.MIN_VALUE, array.getDouble(17), 0.001);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(array.getNumber(17)));
        assertNull(array.getString(17));
        assertNull(array.getDate(17));
        assertNull(array.getBlob(17));
        assertNull(array.getArray(17));
        assertNull(array.getDictionary(17));

        //#18 array.addNumber(Long.MIN_VALUE);
        assertEquals(Long.valueOf(Long.MIN_VALUE), array.getValue(18));
        assertFalse(array.getBoolean(18));
        assertEquals(Long.valueOf(Long.MIN_VALUE).intValue(), array.getInt(18));
        assertEquals(Long.MIN_VALUE, array.getLong(18));
        assertEquals((float) Long.MIN_VALUE, array.getFloat(18), 0.001F);
        assertEquals((double) Long.MIN_VALUE, array.getDouble(18), 0.001);
        assertEquals(Long.valueOf(Long.MIN_VALUE), array.getNumber(18));
        assertNull(array.getString(18));
        assertNull(array.getDate(18));
        assertNull(array.getBlob(18));
        assertNull(array.getArray(18));
        assertNull(array.getDictionary(18));

        //#19 array.addString(null);
        assertNull(array.getValue(19));
        assertFalse(array.getBoolean(19));
        assertEquals(0, array.getInt(19));
        assertEquals(0L, array.getLong(19));
        assertEquals(0.0F, array.getFloat(19), 0.001F);
        assertEquals(0.0, array.getDouble(19), 0.001);
        assertNull(array.getNumber(19));
        assertNull(array.getString(19));
        assertNull(array.getDate(19));
        assertNull(array.getBlob(19));
        assertNull(array.getArray(19));
        assertNull(array.getDictionary(19));

        //#20 array.addString("Quatro");
        assertEquals("Harry", array.getValue(20));
        assertTrue(array.getBoolean(20));
        assertEquals(0, array.getInt(20));
        assertEquals(0, array.getLong(20));
        assertEquals(0.0F, array.getFloat(20), 0.001F);
        assertEquals(0.0, array.getDouble(20), 0.001);
        assertNull(array.getNumber(20));
        assertEquals("Harry", array.getString(20));
        assertNull(array.getDate(20));
        assertNull(array.getBlob(20));
        assertNull(array.getArray(20));
        assertNull(array.getDictionary(20));

        //#21 array.addDate(null);
        assertNull(array.getValue(21));
        assertFalse(array.getBoolean(21));
        assertEquals(0, array.getInt(21));
        assertEquals(0L, array.getLong(21));
        assertEquals(0.0F, array.getFloat(21), 0.001F);
        assertEquals(0.0, array.getDouble(21), 0.001);
        assertNull(array.getNumber(21));
        assertNull(array.getString(21));
        assertNull(array.getDate(21));
        assertNull(array.getBlob(21));
        assertNull(array.getArray(21));
        assertNull(array.getDictionary(21));

        //#22 array.addDate(JSONUtils.toDate(TEST_DATE));
        assertEquals(TEST_DATE, array.getValue(22));
        assertTrue(array.getBoolean(22));
        assertEquals(0, array.getInt(22));
        assertEquals(0L, array.getLong(22));
        assertEquals(0.0F, array.getFloat(22), 0.001F);
        assertEquals(0.0, array.getDouble(22), 0.001);
        assertNull(array.getNumber(22));
        assertEquals(TEST_DATE, array.getString(22));
        assertEquals(JSONUtils.toDate(TEST_DATE), array.getDate(22));
        assertNull(array.getBlob(22));
        assertNull(array.getArray(22));
        assertNull(array.getDictionary(22));

        //#23 array.addArray(null);
        assertNull(array.getValue(23));
        assertFalse(array.getBoolean(23));
        assertEquals(0, array.getInt(23));
        assertEquals(0L, array.getLong(23));
        assertEquals(0.0F, array.getFloat(23), 0.001F);
        assertEquals(0.0, array.getDouble(23), 0.001);
        assertNull(array.getNumber(23));
        assertNull(array.getString(23));
        assertNull(array.getDate(23));
        assertNull(array.getBlob(23));
        assertNull(array.getArray(23));
        assertNull(array.getDictionary(23));

        //#24 array.addArray(simpleArray);
        assertTrue(array.getValue(24) instanceof Array);
        assertTrue(array.getBoolean(24));
        assertEquals(0, array.getInt(24));
        assertEquals(0L, array.getLong(24));
        assertEquals(0.0F, array.getFloat(24), 0.001F);
        assertEquals(0.0, array.getDouble(24), 0.001);
        assertNull(array.getNumber(24));
        assertNull(array.getString(24));
        assertNull(array.getDate(24));
        assertNull(array.getBlob(24));
        assertTrue(array.getArray(24) instanceof Array);
        assertNull(array.getDictionary(24));

        //#25 array.addDictionary(null);
        assertNull(array.getValue(25));
        assertFalse(array.getBoolean(25));
        assertEquals(0, array.getInt(25));
        assertEquals(0L, array.getLong(25));
        assertEquals(0.0F, array.getFloat(25), 0.001F);
        assertEquals(0.0, array.getDouble(25), 0.001);
        assertNull(array.getNumber(25));
        assertNull(array.getString(25));
        assertNull(array.getDate(25));
        assertNull(array.getBlob(25));
        assertNull(array.getArray(25));
        assertNull(array.getDictionary(25));

        //#26 array.addDictionary(simpleDict);
        assertTrue(array.getValue(26) instanceof Dictionary);
        assertTrue(array.getBoolean(26));
        assertEquals(0, array.getInt(26));
        assertEquals(0L, array.getLong(26));
        assertEquals(0.0F, array.getFloat(26), 0.001F);
        assertEquals(0.0, array.getDouble(26), 0.001);
        assertNull(array.getNumber(26));
        assertNull(array.getString(26));
        assertNull(array.getDate(26));
        assertNull(array.getBlob(26));
        assertNull(array.getArray(26));
        assertTrue(array.getDictionary(26) instanceof Dictionary);
    }

    @NonNull
    protected final MutableDictionary makeDict() {
        // A small array
        final MutableArray simpleArray = new MutableArray();
        simpleArray.addInt(54);
        simpleArray.addString("Joplin");

        // A small dictionary
        final MutableDictionary simpleDict = new MutableDictionary();
        simpleDict.setInt("sdict.1", 58);
        simpleDict.setString("sdict.2", "Winehouse");

        // Dictionary:
        final MutableDictionary dict = new MutableDictionary();
        dict.setValue("dict-1", null);

        dict.setBoolean("dict-2", true);
        dict.setBoolean("dict-3", false);

        dict.setInt("dict-4", 0);
        dict.setInt("dict-5", Integer.MIN_VALUE);
        dict.setInt("dict-6", Integer.MAX_VALUE);

        dict.setLong("dict-7", 0L);
        dict.setLong("dict-8", Long.MIN_VALUE);
        dict.setLong("dict-9", Long.MAX_VALUE);

        dict.setFloat("dict-10", 0.0F);
        dict.setFloat("dict-11", Float.MIN_VALUE);
        dict.setFloat("dict-12", Float.MAX_VALUE);

        dict.setDouble("dict-13", 0.0);
        dict.setDouble("dict-14", Double.MIN_VALUE);
        dict.setDouble("dict-15", Double.MAX_VALUE);

        dict.setNumber("dict-16", null);
        dict.setNumber("dict-17", 0);
        dict.setNumber("dict-18", Float.MIN_VALUE);
        dict.setNumber("dict-19", Long.MIN_VALUE);

        dict.setString("dict-20", null);
        dict.setString("dict-21", "Quatro");

        dict.setDate("dict-22", null);
        dict.setDate("dict-23", JSONUtils.toDate(TEST_DATE));

        dict.setArray("dict-24", null);
        dict.setArray("dict-25", simpleArray);

        dict.setDictionary("dict-26", null);
        dict.setDictionary("dict-27", simpleDict);

        return dict;
    }

    protected final void verifyDict(JSONObject jObj) throws JSONException {
        assertEquals(27, jObj.length());

        assertEquals(JSONObject.NULL, jObj.get("dict-1"));

        assertEquals(true, jObj.get("dict-2"));
        assertEquals(false, jObj.get("dict-3"));

        assertEquals(0, jObj.get("dict-4"));
        assertEquals(Integer.MIN_VALUE, jObj.get("dict-5"));
        assertEquals(Integer.MAX_VALUE, jObj.get("dict-6"));

        assertEquals(0, jObj.get("dict-7"));
        assertEquals(Long.MIN_VALUE, jObj.get("dict-8"));
        assertEquals(Long.MAX_VALUE, jObj.get("dict-9"));

        assertEquals(0.0F, jObj.getDouble("dict-10"), 0.001);
        assertEquals(Float.MIN_VALUE, (float) jObj.getDouble("dict-11"), 0.001);
        assertEquals(Float.MAX_VALUE, (float) jObj.getDouble("dict-12"), 100.0);

        assertEquals(0.0, jObj.getDouble("dict-13"), 0.001);
        assertEquals(Double.MIN_VALUE, jObj.getDouble("dict-14"), 0.001);
        assertEquals(Double.MAX_VALUE, jObj.getDouble("dict-15"), 1);

        assertEquals(JSONObject.NULL, jObj.get("dict-16"));
        assertEquals(0, jObj.getLong("dict-17"));
        assertEquals(Float.MIN_VALUE, jObj.getDouble("dict-18"), 0.001);
        assertEquals(Long.MIN_VALUE, jObj.getLong("dict-19"), 0.001);

        assertEquals(JSONObject.NULL, jObj.get("dict-20"));
        assertEquals("Quatro", jObj.get("dict-21"));

        assertEquals(JSONObject.NULL, jObj.get("dict-22"));
        assertEquals(TEST_DATE, jObj.get("dict-23"));

        assertEquals(JSONObject.NULL, jObj.get("dict-24"));
        assertEquals(JSONArray.class, jObj.get("dict-25").getClass());

        assertEquals(JSONObject.NULL, jObj.get("dict-26"));
        assertEquals(JSONObject.class, jObj.get("dict-27").getClass());
    }


    @SuppressWarnings("UnnecessaryBoxing")
    protected final void verifyDict(@Nullable DictionaryInterface dict) {
        assertNotNull(dict);

        assertEquals(27, dict.count());

        //#0 dict.setValue(null);
        assertNull(dict.getValue("dict-1"));
        assertFalse(dict.getBoolean("dict-1"));
        assertEquals(0, dict.getInt("dict-1"));
        assertEquals(0L, dict.getLong("dict-1"));
        assertEquals(0.0F, dict.getFloat("dict-1"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-1"), 0.001);
        assertNull(dict.getNumber("dict-1"));
        assertNull(dict.getString("dict-1"));
        assertNull(dict.getDate("dict-1"));
        assertNull(dict.getBlob("dict-1"));
        assertNull(dict.getArray("dict-1"));
        assertNull(dict.getDictionary("dict-1"));

        //#1 dict.setBoolean(true);
        assertEquals(Boolean.TRUE, dict.getValue("dict-2"));
        assertTrue(dict.getBoolean("dict-2"));
        assertEquals(1, dict.getInt("dict-2"));
        assertEquals(1L, dict.getLong("dict-2"));
        assertEquals(1.0F, dict.getFloat("dict-2"), 0.001F);
        assertEquals(1.0, dict.getDouble("dict-2"), 0.001);
        assertEquals(1, dict.getNumber("dict-2"));
        assertNull(dict.getString("dict-2"));
        assertNull(dict.getDate("dict-2"));
        assertNull(dict.getBlob("dict-2"));
        assertNull(dict.getArray("dict-2"));
        assertNull(dict.getDictionary("dict-2"));

        //#2 dict.setBoolean(false);
        assertEquals(Boolean.FALSE, dict.getValue("dict-3"));
        assertFalse(dict.getBoolean("dict-3"));
        assertEquals(0, dict.getInt("dict-3"));
        assertEquals(0L, dict.getLong("dict-3"));
        assertEquals(0.0F, dict.getFloat("dict-3"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-3"), 0.001);
        assertEquals(0, dict.getNumber("dict-3"));
        assertNull(dict.getString("dict-3"));
        assertNull(dict.getDate("dict-3"));
        assertNull(dict.getBlob("dict-3"));
        assertNull(dict.getArray("dict-3"));
        assertNull(dict.getDictionary("dict-3"));

        //#3 dict.setInt(0);
        assertEquals(0L, dict.getValue("dict-4"));
        assertFalse(dict.getBoolean("dict-4"));
        assertEquals(0, dict.getInt("dict-4"));
        assertEquals(0L, dict.getLong("dict-4"));
        assertEquals(0.0F, dict.getFloat("dict-4"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-4"), 0.001);
        assertEquals(0L, dict.getNumber("dict-4"));
        assertNull(dict.getString("dict-4"));
        assertNull(dict.getDate("dict-4"));
        assertNull(dict.getBlob("dict-4"));
        assertNull(dict.getArray("dict-4"));
        assertNull(dict.getDictionary("dict-4"));

        //#4 dict.setInt(Integer.MIN_VALUE);
        assertEquals(Long.valueOf(Integer.MIN_VALUE), dict.getValue("dict-5"));
        assertTrue(dict.getBoolean("dict-5"));
        assertEquals(Integer.MIN_VALUE, dict.getInt("dict-5"));
        assertEquals((long) Integer.MIN_VALUE, dict.getLong("dict-5"));
        assertEquals((float) Integer.MIN_VALUE, dict.getFloat("dict-5"), 0.001F);
        assertEquals((double) Integer.MIN_VALUE, dict.getDouble("dict-5"), 0.001);
        assertEquals(Long.valueOf(Integer.MIN_VALUE), dict.getNumber("dict-5"));
        assertNull(dict.getString("dict-5"));
        assertNull(dict.getDate("dict-5"));
        assertNull(dict.getBlob("dict-5"));
        assertNull(dict.getArray("dict-5"));
        assertNull(dict.getDictionary("dict-5"));

        //#5 dict.setInt(Integer.MAX_VALUE);
        assertEquals(Long.valueOf(Integer.MAX_VALUE), dict.getValue("dict-6"));
        assertTrue(dict.getBoolean("dict-6"));
        assertEquals(Integer.MAX_VALUE, dict.getInt("dict-6"));
        assertEquals((long) Integer.MAX_VALUE, dict.getLong("dict-6"));
        assertEquals((float) Integer.MAX_VALUE, dict.getFloat("dict-6"), 100.0F);
        assertEquals((double) Integer.MAX_VALUE, dict.getDouble("dict-6"), 100.0);
        assertEquals(Long.valueOf(Integer.MAX_VALUE), dict.getNumber("dict-6"));
        assertNull(dict.getString("dict-6"));
        assertNull(dict.getDate("dict-6"));
        assertNull(dict.getBlob("dict-6"));
        assertNull(dict.getArray("dict-6"));
        assertNull(dict.getDictionary("dict-6"));

        //#6 dict.setLong(0L);
        assertEquals(Long.valueOf(0L), dict.getValue("dict-7"));
        assertFalse(dict.getBoolean("dict-7"));
        assertEquals(0, dict.getInt("dict-7"));
        assertEquals(0L, dict.getLong("dict-7"));
        assertEquals(0.0F, dict.getFloat("dict-7"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-7"), 0.001);
        assertEquals(Long.valueOf(0L), dict.getNumber("dict-7"));
        assertNull(dict.getString("dict-7"));
        assertNull(dict.getDate("dict-7"));
        assertNull(dict.getBlob("dict-7"));
        assertNull(dict.getArray("dict-7"));
        assertNull(dict.getDictionary("dict-7"));

        //#7 dict.setLong(Long.MIN_VALUE);
        assertEquals(Long.valueOf(Long.MIN_VALUE), dict.getValue("dict-8"));
        assertFalse(dict.getBoolean("dict-8"));
        assertEquals(Long.valueOf(Long.MIN_VALUE).intValue(), dict.getInt("dict-8"));
        assertEquals(Long.MIN_VALUE, dict.getLong("dict-8"));
        assertEquals((float) Long.MIN_VALUE, dict.getFloat("dict-8"), 0.001F);
        assertEquals((double) Long.MIN_VALUE, dict.getDouble("dict-8"), 0.001);
        assertEquals(Long.valueOf(Long.MIN_VALUE), dict.getNumber("dict-8"));
        assertNull(dict.getString("dict-8"));
        assertNull(dict.getDate("dict-8"));
        assertNull(dict.getBlob("dict-8"));
        assertNull(dict.getArray("dict-8"));
        assertNull(dict.getDictionary("dict-8"));

        //#8 dict.setLong(Long.MAX_VALUE);
        assertEquals(Long.valueOf(Long.MAX_VALUE), dict.getValue("dict-9"));
        assertTrue(dict.getBoolean("dict-9"));
        assertEquals(Long.valueOf(Long.MAX_VALUE).intValue(), dict.getInt("dict-9"));
        assertEquals((long) Long.MAX_VALUE, dict.getLong("dict-9"));
        assertEquals((float) Long.MAX_VALUE, dict.getFloat("dict-9"), 100.0F);
        assertEquals((double) Long.MAX_VALUE, dict.getDouble("dict-9"), 100.0);
        assertEquals(Long.valueOf(Long.MAX_VALUE), dict.getNumber("dict-9"));
        assertNull(dict.getString("dict-9"));
        assertNull(dict.getDate("dict-9"));
        assertNull(dict.getBlob("dict-9"));
        assertNull(dict.getArray("dict-9"));
        assertNull(dict.getDictionary("dict-9"));

        //#9 dict.setFloat(0.0F);
        assertEquals(Float.valueOf(0.0F), dict.getValue("dict-10"));
        assertFalse(dict.getBoolean("dict-10"));
        assertEquals(0, dict.getInt("dict-10"));
        assertEquals(0L, dict.getLong("dict-10"));
        assertEquals(0.0F, dict.getFloat("dict-10"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-10"), 0.001);
        assertEquals(Float.valueOf(0.0F), dict.getNumber("dict-10"));
        assertNull(dict.getString("dict-10"));
        assertNull(dict.getDate("dict-10"));
        assertNull(dict.getBlob("dict-10"));
        assertNull(dict.getArray("dict-10"));
        assertNull(dict.getDictionary("dict-10"));

        //#10 dict.setFloat(Float.MIN_VALUE);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(dict.getValue("dict-11")));
        assertFalse(dict.getBoolean("dict-11"));
        assertEquals(Float.valueOf(Float.MIN_VALUE).intValue(), dict.getInt("dict-11"), 0.001F);
        assertEquals(Float.valueOf(Float.MIN_VALUE).longValue(), dict.getLong("dict-11"), 0.001F);
        assertEquals(Float.MIN_VALUE, dict.getFloat("dict-11"), 0.001F);
        assertEquals((double) Float.MIN_VALUE, dict.getDouble("dict-11"), 0.001);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(dict.getNumber("dict-11")));
        assertNull(dict.getString("dict-11"));
        assertNull(dict.getDate("dict-11"));
        assertNull(dict.getBlob("dict-11"));
        assertNull(dict.getArray("dict-11"));
        assertNull(dict.getDictionary("dict-11"));

        //#11 dict.setFloat(Float.MAX_VALUE);
        assertEquals(Float.valueOf(Float.MAX_VALUE), demoteToFloat(dict.getValue("dict-12")));
        assertTrue(dict.getBoolean("dict-12"));
        // ??? Fails: assertEquals(Float.valueOf(Float.MAX_VALUE).intValue(), dict.getInt("dict-12"));
        // ??? Fails: assertEquals(Float.valueOf(Float.MAX_VALUE).longValue(), dict.getLong("dict-12"));
        assertEquals(Float.MAX_VALUE, dict.getFloat("dict-12"), 1.0E32);
        assertEquals((double) Float.MAX_VALUE, dict.getDouble("dict-12"), 1.0E32);
        assertEquals(Float.valueOf(Float.MAX_VALUE), demoteToFloat(dict.getNumber("dict-12")));
        assertNull(dict.getString("dict-12"));
        assertNull(dict.getDate("dict-12"));
        assertNull(dict.getBlob("dict-12"));
        assertNull(dict.getArray("dict-12"));
        assertNull(dict.getDictionary("dict-12"));

        //#12 dict.setDouble(0.0);
        assertEquals(Float.valueOf(0), dict.getValue("dict-13"));
        assertFalse(dict.getBoolean("dict-13"));
        assertEquals(0, dict.getInt("dict-13"));
        assertEquals(0L, dict.getLong("dict-13"));
        assertEquals(0.0F, dict.getFloat("dict-13"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-13"), 0.001);
        assertEquals(Float.valueOf(0), dict.getNumber("dict-13"));
        assertNull(dict.getString("dict-13"));
        assertNull(dict.getDate("dict-13"));
        assertNull(dict.getBlob("dict-13"));
        assertNull(dict.getArray("dict-13"));
        assertNull(dict.getDictionary("dict-13"));

        //#13 dict.setDouble(Double.MIN_VALUE);
        assertEquals(Double.valueOf(Double.MIN_VALUE), dict.getValue("dict-14"));
        assertFalse(dict.getBoolean("dict-14"));
        assertEquals(Double.valueOf(Double.MIN_VALUE).intValue(), dict.getInt("dict-14"));
        assertEquals(Double.valueOf(Double.MIN_VALUE).longValue(), dict.getLong("dict-14"));
        assertEquals(Double.valueOf(Double.MIN_VALUE).floatValue(), dict.getFloat("dict-14"), 0.001F);
        assertEquals(Double.MIN_VALUE, dict.getDouble("dict-14"), 0.001);
        assertEquals(Double.valueOf(Double.MIN_VALUE), dict.getNumber("dict-14"));
        assertNull(dict.getString("dict-14"));
        assertNull(dict.getDate("dict-14"));
        assertNull(dict.getBlob("dict-14"));
        assertNull(dict.getArray("dict-14"));
        assertNull(dict.getDictionary("dict-14"));

        //#14 dict.setDouble(Double.MAX_VALUE);
        assertEquals(Double.valueOf(Double.MAX_VALUE), dict.getValue("dict-15"));
        assertTrue(dict.getBoolean("dict-15"));
        // ??? Fails: assertEquals(Double.valueOf(Double.MAX_VALUE).intValue(), dict.getInt("dict-15"));
        // ??? Fails: assertEquals(Double.valueOf(Double.MAX_VALUE).longValue(), dict.getLong("dict-15"));
        assertEquals(Double.valueOf(Double.MAX_VALUE).floatValue(), dict.getFloat("dict-15"), 100.0F);
        assertEquals(Double.MAX_VALUE, dict.getDouble("dict-15"), 100.0);
        assertEquals(Double.valueOf(Double.MAX_VALUE), dict.getNumber("dict-15"));
        assertNull(dict.getString("dict-15"));
        assertNull(dict.getDate("dict-15"));
        assertNull(dict.getBlob("dict-15"));
        assertNull(dict.getArray("dict-15"));
        assertNull(dict.getDictionary("dict-15"));

        //#15 dict.setNumber(null);
        assertNull(dict.getValue("dict-16"));
        assertFalse(dict.getBoolean("dict-16"));
        assertEquals(0, dict.getInt("dict-16"));
        assertEquals(0L, dict.getLong("dict-16"));
        assertEquals(0.0F, dict.getFloat("dict-16"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-16"), 0.001);
        assertNull(dict.getNumber("dict-16"));
        assertNull(dict.getString("dict-16"));
        assertNull(dict.getDate("dict-16"));
        assertNull(dict.getBlob("dict-16"));
        assertNull(dict.getArray("dict-16"));
        assertNull(dict.getDictionary("dict-16"));

        //#16 dict.setNumber(0);
        assertEquals(0L, dict.getValue("dict-17"));
        assertFalse(dict.getBoolean("dict-17"));
        assertEquals(0, dict.getInt("dict-17"));
        assertEquals(0L, dict.getLong("dict-17"));
        assertEquals(0.0F, dict.getFloat("dict-17"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-17"), 0.001);
        assertEquals(0L, dict.getNumber("dict-17"));
        assertNull(dict.getString("dict-17"));
        assertNull(dict.getDate("dict-17"));
        assertNull(dict.getBlob("dict-17"));
        assertNull(dict.getArray("dict-17"));
        assertNull(dict.getDictionary("dict-17"));

        //#17 dict.setNumber(Float.MIN_VALUE);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(dict.getValue("dict-18")));
        assertFalse(dict.getBoolean("dict-18"));
        assertEquals(Float.valueOf(Float.MIN_VALUE).intValue(), dict.getInt("dict-18"));
        assertEquals(Float.valueOf(Float.MIN_VALUE).longValue(), dict.getLong("dict-18"));
        assertEquals(Float.MIN_VALUE, dict.getFloat("dict-18"), 0.001F);
        assertEquals((double) Float.MIN_VALUE, dict.getDouble("dict-18"), 0.001);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(dict.getNumber("dict-18")));
        assertNull(dict.getString("dict-18"));
        assertNull(dict.getDate("dict-18"));
        assertNull(dict.getBlob("dict-18"));
        assertNull(dict.getArray("dict-18"));
        assertNull(dict.getDictionary("dict-18"));

        //#18 dict.setNumber(Long.MIN_VALUE);
        assertEquals(Long.valueOf(Long.MIN_VALUE), dict.getValue("dict-19"));
        assertFalse(dict.getBoolean("dict-19"));
        assertEquals(Long.valueOf(Long.MIN_VALUE).intValue(), dict.getInt("dict-19"));
        assertEquals(Long.MIN_VALUE, dict.getLong("dict-19"));
        assertEquals((float) Long.MIN_VALUE, dict.getFloat("dict-19"), 0.001F);
        assertEquals((double) Long.MIN_VALUE, dict.getDouble("dict-19"), 0.001);
        assertEquals(Long.valueOf(Long.MIN_VALUE), dict.getNumber("dict-19"));
        assertNull(dict.getString("dict-19"));
        assertNull(dict.getDate("dict-19"));
        assertNull(dict.getBlob("dict-19"));
        assertNull(dict.getArray("dict-19"));
        assertNull(dict.getDictionary("dict-19"));

        //#19 dict.setString(null);
        assertNull(dict.getValue("dict-20"));
        assertFalse(dict.getBoolean("dict-20"));
        assertEquals(0, dict.getInt("dict-20"));
        assertEquals(0L, dict.getLong("dict-20"));
        assertEquals(0.0F, dict.getFloat("dict-20"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-20"), 0.001);
        assertNull(dict.getNumber("dict-20"));
        assertNull(dict.getString("dict-20"));
        assertNull(dict.getDate("dict-20"));
        assertNull(dict.getBlob("dict-20"));
        assertNull(dict.getArray("dict-20"));
        assertNull(dict.getDictionary("dict-20"));

        //#20 dict.setString("Quatro");
        assertEquals("Quatro", dict.getValue("dict-21"));
        assertTrue(dict.getBoolean("dict-21"));
        assertEquals(0, dict.getInt("dict-21"));
        assertEquals(0, dict.getLong("dict-21"));
        assertEquals(0.0F, dict.getFloat("dict-21"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-21"), 0.001);
        assertNull(dict.getNumber("dict-21"));
        assertEquals("Quatro", dict.getString("dict-21"));
        assertNull(dict.getDate("dict-21"));
        assertNull(dict.getBlob("dict-21"));
        assertNull(dict.getArray("dict-21"));
        assertNull(dict.getDictionary("dict-21"));

        //#21 dict.setDate(null);
        assertNull(dict.getValue("dict-22"));
        assertFalse(dict.getBoolean("dict-22"));
        assertEquals(0, dict.getInt("dict-22"));
        assertEquals(0L, dict.getLong("dict-22"));
        assertEquals(0.0F, dict.getFloat("dict-22"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-22"), 0.001);
        assertNull(dict.getNumber("dict-22"));
        assertNull(dict.getString("dict-22"));
        assertNull(dict.getDate("dict-22"));
        assertNull(dict.getBlob("dict-22"));
        assertNull(dict.getArray("dict-22"));
        assertNull(dict.getDictionary("dict-22"));

        //#22 dict.setDate(JSONUtils.toDate(TEST_DATE));
        assertEquals(TEST_DATE, dict.getValue("dict-23"));
        assertTrue(dict.getBoolean("dict-23"));
        assertEquals(0, dict.getInt("dict-23"));
        assertEquals(0L, dict.getLong("dict-23"));
        assertEquals(0.0F, dict.getFloat("dict-23"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-23"), 0.001);
        assertNull(dict.getNumber("dict-23"));
        assertEquals(TEST_DATE, dict.getString("dict-23"));
        assertEquals(JSONUtils.toDate(TEST_DATE), dict.getDate("dict-23"));
        assertNull(dict.getBlob("dict-23"));
        assertNull(dict.getArray("dict-23"));
        assertNull(dict.getDictionary("dict-23"));

        //#23 dict.setArray(null);
        assertNull(dict.getValue("dict-24"));
        assertFalse(dict.getBoolean("dict-24"));
        assertEquals(0, dict.getInt("dict-24"));
        assertEquals(0L, dict.getLong("dict-24"));
        assertEquals(0.0F, dict.getFloat("dict-24"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-24"), 0.001);
        assertNull(dict.getNumber("dict-24"));
        assertNull(dict.getString("dict-24"));
        assertNull(dict.getDate("dict-24"));
        assertNull(dict.getBlob("dict-24"));
        assertNull(dict.getArray("dict-24"));
        assertNull(dict.getDictionary("dict-24"));

        //#24 dict.setArray(simpleArray);
        assertTrue(dict.getValue("dict-25") instanceof Array);
        assertTrue(dict.getBoolean("dict-25"));
        assertEquals(0, dict.getInt("dict-25"));
        assertEquals(0L, dict.getLong("dict-25"));
        assertEquals(0.0F, dict.getFloat("dict-25"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-25"), 0.001);
        assertNull(dict.getNumber("dict-25"));
        assertNull(dict.getString("dict-25"));
        assertNull(dict.getDate("dict-25"));
        assertNull(dict.getBlob("dict-25"));
        assertTrue(dict.getArray("dict-25") instanceof Array);
        assertNull(dict.getDictionary("dict-25"));

        //#25 dict.setDictionary(null);
        assertNull(dict.getValue("dict-26"));
        assertFalse(dict.getBoolean("dict-26"));
        assertEquals(0, dict.getInt("dict-26"));
        assertEquals(0L, dict.getLong("dict-26"));
        assertEquals(0.0F, dict.getFloat("dict-26"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-26"), 0.001);
        assertNull(dict.getNumber("dict-26"));
        assertNull(dict.getString("dict-26"));
        assertNull(dict.getDate("dict-26"));
        assertNull(dict.getBlob("dict-26"));
        assertNull(dict.getArray("dict-26"));
        assertNull(dict.getDictionary("dict-26"));

        //#26 dict.setDictionary(simpleDict);
        assertTrue(dict.getValue("dict-27") instanceof Dictionary);
        assertTrue(dict.getBoolean("dict-27"));
        assertEquals(0, dict.getInt("dict-27"));
        assertEquals(0L, dict.getLong("dict-27"));
        assertEquals(0.0F, dict.getFloat("dict-27"), 0.001F);
        assertEquals(0.0, dict.getDouble("dict-27"), 0.001);
        assertNull(dict.getNumber("dict-27"));
        assertNull(dict.getString("dict-27"));
        assertNull(dict.getDate("dict-27"));
        assertNull(dict.getBlob("dict-27"));
        assertNull(dict.getArray("dict-27"));
        assertTrue(dict.getDictionary("dict-27") instanceof Dictionary);
    }

    protected final Blob makeBlob() { return new Blob("text/plain", BLOB_CONTENT.getBytes(StandardCharsets.UTF_8)); }

    protected final void verifyBlob(JSONObject jBlob) throws JSONException {
        assertEquals(4, jBlob.length());
        assertEquals(Blob.TYPE_BLOB, jBlob.get(Blob.META_PROP_TYPE));
        assertEquals("sha1-C+QguVamTgLjyDQ9RzRtyCv6x60=", jBlob.get(Blob.PROP_DIGEST));
        assertEquals(59, jBlob.getLong(Blob.PROP_LENGTH));
        assertEquals("text/plain", jBlob.get(Blob.PROP_CONTENT_TYPE));
    }

    protected final void verifyBlob(@Nullable Blob blob) {
        assertNotNull(blob);
        assertEquals("sha1-C+QguVamTgLjyDQ9RzRtyCv6x60=", blob.digest());
        assertEquals(59, blob.length());
        assertEquals("text/plain", blob.getContentType());
        assertEquals(BLOB_CONTENT, new String(blob.getContent()));
    }

    protected final MutableDocument makeDocument() {
        // Dictionary:
        final MutableDocument mDoc = new MutableDocument();
        mDoc.setValue("doc-1", null);

        mDoc.setBoolean("doc-2", true);
        mDoc.setBoolean("doc-3", false);

        mDoc.setInt("doc-4", 0);
        mDoc.setInt("doc-5", Integer.MIN_VALUE);
        mDoc.setInt("doc-6", Integer.MAX_VALUE);

        mDoc.setLong("doc-7", 0L);
        mDoc.setLong("doc-8", Long.MIN_VALUE);
        mDoc.setLong("doc-9", Long.MAX_VALUE);

        mDoc.setFloat("doc-10", 0.0F);
        mDoc.setFloat("doc-11", Float.MIN_VALUE);
        mDoc.setFloat("doc-12", Float.MAX_VALUE);

        mDoc.setDouble("doc-13", 0.0);
        mDoc.setDouble("doc-14", Double.MIN_VALUE);
        mDoc.setDouble("doc-15", Double.MAX_VALUE);

        mDoc.setNumber("doc-16", null);
        mDoc.setNumber("doc-17", 0);
        mDoc.setNumber("doc-18", Float.MIN_VALUE);
        mDoc.setNumber("doc-19", Long.MIN_VALUE);

        mDoc.setString("doc-20", null);
        mDoc.setString("doc-21", "Jett");

        mDoc.setDate("doc-22", null);
        mDoc.setDate("doc-23", JSONUtils.toDate(TEST_DATE));

        mDoc.setArray("doc-24", null);
        mDoc.setArray("doc-25", makeArray());

        mDoc.setDictionary("doc-26", null);
        mDoc.setDictionary("doc-27", makeDict());

        mDoc.setBlob("doc-28", null);
        mDoc.setBlob("doc-29", makeBlob());

        return mDoc;
    }

    protected final void verifyDocument(JSONObject jObj) throws JSONException {
        assertEquals(29, jObj.length());

        assertEquals(JSONObject.NULL, jObj.get("doc-1"));

        assertEquals(true, jObj.get("doc-2"));
        assertEquals(false, jObj.get("doc-3"));

        assertEquals(0, jObj.get("doc-4"));
        assertEquals(Integer.MIN_VALUE, jObj.get("doc-5"));
        assertEquals(Integer.MAX_VALUE, jObj.get("doc-6"));

        assertEquals(0, jObj.get("doc-7"));
        assertEquals(Long.MIN_VALUE, jObj.get("doc-8"));
        assertEquals(Long.MAX_VALUE, jObj.get("doc-9"));

        assertEquals(0.0F, jObj.getDouble("doc-10"), 0.001);
        assertEquals(Float.MIN_VALUE, (float) jObj.getDouble("doc-11"), 0.001);
        assertEquals(Float.MAX_VALUE, (float) jObj.getDouble("doc-12"), 100.0);

        assertEquals(0.0, jObj.getDouble("doc-13"), 0.001);
        assertEquals(Double.MIN_VALUE, jObj.getDouble("doc-14"), 0.001);
        assertEquals(Double.MAX_VALUE, jObj.getDouble("doc-15"), 1);

        assertEquals(JSONObject.NULL, jObj.get("doc-16"));
        assertEquals(0, jObj.getLong("doc-17"));
        assertEquals(Float.MIN_VALUE, jObj.getDouble("doc-18"), 0.001);
        assertEquals(Long.MIN_VALUE, jObj.getLong("doc-19"), 0.001);

        assertEquals(JSONObject.NULL, jObj.get("doc-20"));
        assertEquals("Jett", jObj.get("doc-21"));

        assertEquals(JSONObject.NULL, jObj.get("doc-22"));
        assertEquals(TEST_DATE, jObj.get("doc-23"));

        assertEquals(JSONObject.NULL, jObj.get("doc-24"));
        verifyArray((JSONArray) jObj.get("doc-25"));

        assertEquals(JSONObject.NULL, jObj.get("doc-26"));
        verifyDict((JSONObject) jObj.get("doc-27"));

        assertEquals(JSONObject.NULL, jObj.get("doc-28"));
        verifyBlob(jObj.getJSONObject("doc-29"));
    }

    @SuppressWarnings("UnnecessaryBoxing")
    protected final void verifyDocument(DictionaryInterface doc) {
        assertEquals(29, doc.count());

        //#0 doc.setValue(null);
        assertNull(doc.getValue("doc-1"));
        assertFalse(doc.getBoolean("doc-1"));
        assertEquals(0, doc.getInt("doc-1"));
        assertEquals(0L, doc.getLong("doc-1"));
        assertEquals(0.0F, doc.getFloat("doc-1"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-1"), 0.001);
        assertNull(doc.getNumber("doc-1"));
        assertNull(doc.getString("doc-1"));
        assertNull(doc.getDate("doc-1"));
        assertNull(doc.getBlob("doc-1"));
        assertNull(doc.getArray("doc-1"));
        assertNull(doc.getDictionary("doc-1"));

        //#1 doc.setBoolean(true);
        assertEquals(Boolean.TRUE, doc.getValue("doc-2"));
        assertTrue(doc.getBoolean("doc-2"));
        assertEquals(1, doc.getInt("doc-2"));
        assertEquals(1L, doc.getLong("doc-2"));
        assertEquals(1.0F, doc.getFloat("doc-2"), 0.001F);
        assertEquals(1.0, doc.getDouble("doc-2"), 0.001);
        assertEquals(1, doc.getNumber("doc-2"));
        assertNull(doc.getString("doc-2"));
        assertNull(doc.getDate("doc-2"));
        assertNull(doc.getBlob("doc-2"));
        assertNull(doc.getArray("doc-2"));
        assertNull(doc.getDictionary("doc-2"));

        //#2 doc.setBoolean(false);
        assertEquals(Boolean.FALSE, doc.getValue("doc-3"));
        assertFalse(doc.getBoolean("doc-3"));
        assertEquals(0, doc.getInt("doc-3"));
        assertEquals(0L, doc.getLong("doc-3"));
        assertEquals(0.0F, doc.getFloat("doc-3"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-3"), 0.001);
        assertEquals(0, doc.getNumber("doc-3"));
        assertNull(doc.getString("doc-3"));
        assertNull(doc.getDate("doc-3"));
        assertNull(doc.getBlob("doc-3"));
        assertNull(doc.getArray("doc-3"));
        assertNull(doc.getDictionary("doc-3"));

        //#3 doc.setInt(0);
        assertEquals(0L, doc.getValue("doc-4"));
        assertFalse(doc.getBoolean("doc-4"));
        assertEquals(0, doc.getInt("doc-4"));
        assertEquals(0L, doc.getLong("doc-4"));
        assertEquals(0.0F, doc.getFloat("doc-4"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-4"), 0.001);
        assertEquals(0L, doc.getNumber("doc-4"));
        assertNull(doc.getString("doc-4"));
        assertNull(doc.getDate("doc-4"));
        assertNull(doc.getBlob("doc-4"));
        assertNull(doc.getArray("doc-4"));
        assertNull(doc.getDictionary("doc-4"));

        //#4 doc.setInt(Integer.MIN_VALUE);
        assertEquals(Long.valueOf(Integer.MIN_VALUE), doc.getValue("doc-5"));
        assertTrue(doc.getBoolean("doc-5"));
        assertEquals(Integer.MIN_VALUE, doc.getInt("doc-5"));
        assertEquals((long) Integer.MIN_VALUE, doc.getLong("doc-5"));
        assertEquals((float) Integer.MIN_VALUE, doc.getFloat("doc-5"), 0.001F);
        assertEquals((double) Integer.MIN_VALUE, doc.getDouble("doc-5"), 0.001);
        assertEquals(Long.valueOf(Integer.MIN_VALUE), doc.getNumber("doc-5"));
        assertNull(doc.getString("doc-5"));
        assertNull(doc.getDate("doc-5"));
        assertNull(doc.getBlob("doc-5"));
        assertNull(doc.getArray("doc-5"));
        assertNull(doc.getDictionary("doc-5"));

        //#5 doc.setInt(Integer.MAX_VALUE);
        assertEquals(Long.valueOf(Integer.MAX_VALUE), doc.getValue("doc-6"));
        assertTrue(doc.getBoolean("doc-6"));
        assertEquals(Integer.MAX_VALUE, doc.getInt("doc-6"));
        assertEquals((long) Integer.MAX_VALUE, doc.getLong("doc-6"));
        assertEquals((float) Integer.MAX_VALUE, doc.getFloat("doc-6"), 100.0F);
        assertEquals((double) Integer.MAX_VALUE, doc.getDouble("doc-6"), 100.0);
        assertEquals(Long.valueOf(Integer.MAX_VALUE), doc.getNumber("doc-6"));
        assertNull(doc.getString("doc-6"));
        assertNull(doc.getDate("doc-6"));
        assertNull(doc.getBlob("doc-6"));
        assertNull(doc.getArray("doc-6"));
        assertNull(doc.getDictionary("doc-6"));

        //#6 doc.setLong(0L);
        assertEquals(Long.valueOf(0L), doc.getValue("doc-7"));
        assertFalse(doc.getBoolean("doc-7"));
        assertEquals(0, doc.getInt("doc-7"));
        assertEquals(0L, doc.getLong("doc-7"));
        assertEquals(0.0F, doc.getFloat("doc-7"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-7"), 0.001);
        assertEquals(Long.valueOf(0L), doc.getNumber("doc-7"));
        assertNull(doc.getString("doc-7"));
        assertNull(doc.getDate("doc-7"));
        assertNull(doc.getBlob("doc-7"));
        assertNull(doc.getArray("doc-7"));
        assertNull(doc.getDictionary("doc-7"));

        //#7 doc.setLong(Long.MIN_VALUE);
        assertEquals(Long.valueOf(Long.MIN_VALUE), doc.getValue("doc-8"));
        // ??? Value differs for Documents and Results: assertTrue(doc.getBoolean("doc-8"));
        assertEquals(Long.valueOf(Long.MIN_VALUE).intValue(), doc.getInt("doc-8"));
        assertEquals(Long.MIN_VALUE, doc.getLong("doc-8"));
        assertEquals((float) Long.MIN_VALUE, doc.getFloat("doc-8"), 0.001F);
        assertEquals((double) Long.MIN_VALUE, doc.getDouble("doc-8"), 0.001);
        assertEquals(Long.valueOf(Long.MIN_VALUE), doc.getNumber("doc-8"));
        assertNull(doc.getString("doc-8"));
        assertNull(doc.getDate("doc-8"));
        assertNull(doc.getBlob("doc-8"));
        assertNull(doc.getArray("doc-8"));
        assertNull(doc.getDictionary("doc-8"));

        //#8 doc.setLong(Long.MAX_VALUE);
        assertEquals(Long.valueOf(Long.MAX_VALUE), doc.getValue("doc-9"));
        assertTrue(doc.getBoolean("doc-9"));
        assertEquals(Long.valueOf(Long.MAX_VALUE).intValue(), doc.getInt("doc-9"));
        assertEquals((long) Long.MAX_VALUE, doc.getLong("doc-9"));
        assertEquals((float) Long.MAX_VALUE, doc.getFloat("doc-9"), 100.0F);
        assertEquals((double) Long.MAX_VALUE, doc.getDouble("doc-9"), 100.0);
        assertEquals(Long.valueOf(Long.MAX_VALUE), doc.getNumber("doc-9"));
        assertNull(doc.getString("doc-9"));
        assertNull(doc.getDate("doc-9"));
        assertNull(doc.getBlob("doc-9"));
        assertNull(doc.getArray("doc-9"));
        assertNull(doc.getDictionary("doc-9"));

        //#9 doc.setFloat(0.0F);
        assertEquals(Float.valueOf(0.0F), doc.getValue("doc-10"));
        assertFalse(doc.getBoolean("doc-10"));
        assertEquals(0, doc.getInt("doc-10"));
        assertEquals(0L, doc.getLong("doc-10"));
        assertEquals(0.0F, doc.getFloat("doc-10"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-10"), 0.001);
        assertEquals(Float.valueOf(0.0F), doc.getNumber("doc-10"));
        assertNull(doc.getString("doc-10"));
        assertNull(doc.getDate("doc-10"));
        assertNull(doc.getBlob("doc-10"));
        assertNull(doc.getArray("doc-10"));
        assertNull(doc.getDictionary("doc-10"));

        //#10 doc.setFloat(Float.MIN_VALUE);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(doc.getValue("doc-11")));
        assertFalse(doc.getBoolean("doc-11"));
        assertEquals(Float.valueOf(Float.MIN_VALUE).intValue(), doc.getInt("doc-11"), 0.001F);
        assertEquals(Float.valueOf(Float.MIN_VALUE).longValue(), doc.getLong("doc-11"), 0.001F);
        assertEquals(Float.MIN_VALUE, doc.getFloat("doc-11"), 0.001F);
        assertEquals((double) Float.MIN_VALUE, doc.getDouble("doc-11"), 0.001);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(doc.getValue("doc-11")));
        assertNull(doc.getString("doc-11"));
        assertNull(doc.getDate("doc-11"));
        assertNull(doc.getBlob("doc-11"));
        assertNull(doc.getArray("doc-11"));
        assertNull(doc.getDictionary("doc-11"));

        //#11 doc.setFloat(Float.MAX_VALUE);
        assertEquals(Float.valueOf(Float.MAX_VALUE), demoteToFloat(doc.getValue("doc-12")));
        assertTrue(doc.getBoolean("doc-12"));
        // ??? Fails: assertEquals(Float.valueOf(Float.MAX_VALUE).intValue(), doc.getInt("doc-12"));
        // ??? Fails: assertEquals(Float.valueOf(Float.MAX_VALUE).longValue(), doc.getLong("doc-12"));
        assertEquals(Float.MAX_VALUE, doc.getFloat("doc-12"), 1.0E32);
        assertEquals((double) Float.MAX_VALUE, doc.getDouble("doc-12"), 1.0E32);
        assertEquals(Float.valueOf(Float.MAX_VALUE), demoteToFloat(doc.getNumber("doc-12")));
        assertNull(doc.getString("doc-12"));
        assertNull(doc.getDate("doc-12"));
        assertNull(doc.getBlob("doc-12"));
        assertNull(doc.getArray("doc-12"));
        assertNull(doc.getDictionary("doc-12"));

        //#12 doc.setDouble(0.0);
        assertEquals(Float.valueOf(0), doc.getValue("doc-13"));
        assertFalse(doc.getBoolean("doc-13"));
        assertEquals(0, doc.getInt("doc-13"));
        assertEquals(0L, doc.getLong("doc-13"));
        assertEquals(0.0F, doc.getFloat("doc-13"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-13"), 0.001);
        assertEquals(Float.valueOf(0), doc.getNumber("doc-13"));
        assertNull(doc.getString("doc-13"));
        assertNull(doc.getDate("doc-13"));
        assertNull(doc.getBlob("doc-13"));
        assertNull(doc.getArray("doc-13"));
        assertNull(doc.getDictionary("doc-13"));

        //#13 doc.setDouble(Double.MIN_VALUE);
        assertEquals(Double.valueOf(Double.MIN_VALUE), doc.getValue("doc-14"));
        assertFalse(doc.getBoolean("doc-14"));
        assertEquals(Double.valueOf(Double.MIN_VALUE).intValue(), doc.getInt("doc-14"));
        assertEquals(Double.valueOf(Double.MIN_VALUE).longValue(), doc.getLong("doc-14"));
        assertEquals(Double.valueOf(Double.MIN_VALUE).floatValue(), doc.getFloat("doc-14"), 0.001F);
        assertEquals(Double.MIN_VALUE, doc.getDouble("doc-14"), 0.001);
        assertEquals(Double.valueOf(Double.MIN_VALUE), doc.getNumber("doc-14"));
        assertNull(doc.getString("doc-14"));
        assertNull(doc.getDate("doc-14"));
        assertNull(doc.getBlob("doc-14"));
        assertNull(doc.getArray("doc-14"));
        assertNull(doc.getDictionary("doc-14"));

        //#14 doc.setDouble(Double.MAX_VALUE);
        assertEquals(Double.valueOf(Double.MAX_VALUE), doc.getValue("doc-15"));
        // ??? Fails: assertEquals(Double.valueOf(Double.MAX_VALUE).intValue(), doc.getInt("doc-15"));
        // ??? Fails: assertEquals(Double.valueOf(Double.MAX_VALUE).longValue(), doc.getLong("doc-15"));
        assertEquals(Double.valueOf(Double.MAX_VALUE).floatValue(), doc.getFloat("doc-15"), 100.0F);
        assertEquals(Double.MAX_VALUE, doc.getDouble("doc-15"), 100.0);
        assertEquals(Double.valueOf(Double.MAX_VALUE), doc.getNumber("doc-15"));
        assertNull(doc.getString("doc-15"));
        assertNull(doc.getDate("doc-15"));
        assertNull(doc.getBlob("doc-15"));
        assertNull(doc.getArray("doc-15"));
        assertNull(doc.getDictionary("doc-15"));

        //#15 doc.setNumber(null);
        assertNull(doc.getValue("doc-16"));
        assertFalse(doc.getBoolean("doc-16"));
        assertEquals(0, doc.getInt("doc-16"));
        assertEquals(0L, doc.getLong("doc-16"));
        assertEquals(0.0F, doc.getFloat("doc-16"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-16"), 0.001);
        assertNull(doc.getNumber("doc-16"));
        assertNull(doc.getString("doc-16"));
        assertNull(doc.getDate("doc-16"));
        assertNull(doc.getBlob("doc-16"));
        assertNull(doc.getArray("doc-16"));
        assertNull(doc.getDictionary("doc-16"));

        //#16 doc.setNumber(0);
        assertEquals(0L, doc.getValue("doc-17"));
        assertFalse(doc.getBoolean("doc-17"));
        assertEquals(0, doc.getInt("doc-17"));
        assertEquals(0L, doc.getLong("doc-17"));
        assertEquals(0.0F, doc.getFloat("doc-17"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-17"), 0.001);
        assertEquals(0L, doc.getNumber("doc-17"));
        assertNull(doc.getString("doc-17"));
        assertNull(doc.getDate("doc-17"));
        assertNull(doc.getBlob("doc-17"));
        assertNull(doc.getArray("doc-17"));
        assertNull(doc.getDictionary("doc-17"));

        //#17 doc.setNumber(Float.MIN_VALUE);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(doc.getValue("doc-18")));
        assertFalse(doc.getBoolean("doc-18"));
        assertEquals(Float.valueOf(Float.MIN_VALUE).intValue(), doc.getInt("doc-18"));
        assertEquals(Float.valueOf(Float.MIN_VALUE).longValue(), doc.getLong("doc-18"));
        assertEquals(Float.MIN_VALUE, doc.getFloat("doc-18"), 0.001F);
        assertEquals((double) Float.MIN_VALUE, doc.getDouble("doc-18"), 0.001);
        assertEquals(Float.valueOf(Float.MIN_VALUE), demoteToFloat(doc.getNumber("doc-18")));
        assertNull(doc.getString("doc-18"));
        assertNull(doc.getDate("doc-18"));
        assertNull(doc.getBlob("doc-18"));
        assertNull(doc.getArray("doc-18"));
        assertNull(doc.getDictionary("doc-18"));

        //#18 doc.setNumber(Long.MIN_VALUE);
        assertEquals(Long.valueOf(Long.MIN_VALUE), doc.getValue("doc-19"));
        // ??? Value differs for Documents and Results: assertTrue(doc.getBoolean("doc-19"));
        assertEquals(Long.valueOf(Long.MIN_VALUE).intValue(), doc.getInt("doc-19"));
        assertEquals(Long.MIN_VALUE, doc.getLong("doc-19"));
        assertEquals((float) Long.MIN_VALUE, doc.getFloat("doc-19"), 0.001F);
        assertEquals((double) Long.MIN_VALUE, doc.getDouble("doc-19"), 0.001);
        assertEquals(Long.valueOf(Long.MIN_VALUE), doc.getNumber("doc-19"));
        assertNull(doc.getString("doc-19"));
        assertNull(doc.getDate("doc-19"));
        assertNull(doc.getBlob("doc-19"));
        assertNull(doc.getArray("doc-19"));
        assertNull(doc.getDictionary("doc-19"));

        //#19 doc.setString(null);
        assertNull(doc.getValue("doc-20"));
        assertFalse(doc.getBoolean("doc-20"));
        assertEquals(0, doc.getInt("doc-20"));
        assertEquals(0L, doc.getLong("doc-20"));
        assertEquals(0.0F, doc.getFloat("doc-20"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-20"), 0.001);
        assertNull(doc.getNumber("doc-20"));
        assertNull(doc.getString("doc-20"));
        assertNull(doc.getDate("doc-20"));
        assertNull(doc.getBlob("doc-20"));
        assertNull(doc.getArray("doc-20"));
        assertNull(doc.getDictionary("doc-20"));

        //#20 doc.setString("Quatro");
        assertEquals("Jett", doc.getValue("doc-21"));
        assertTrue(doc.getBoolean("doc-21"));
        assertEquals(0, doc.getInt("doc-21"));
        assertEquals(0, doc.getLong("doc-21"));
        assertEquals(0.0F, doc.getFloat("doc-21"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-21"), 0.001);
        assertNull(doc.getNumber("doc-21"));
        assertEquals("Jett", doc.getString("doc-21"));
        assertNull(doc.getDate("doc-21"));
        assertNull(doc.getBlob("doc-21"));
        assertNull(doc.getArray("doc-21"));
        assertNull(doc.getDictionary("doc-21"));

        //#21 doc.setDate(null);
        assertNull(doc.getValue("doc-22"));
        assertFalse(doc.getBoolean("doc-22"));
        assertEquals(0, doc.getInt("doc-22"));
        assertEquals(0L, doc.getLong("doc-22"));
        assertEquals(0.0F, doc.getFloat("doc-22"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-22"), 0.001);
        assertNull(doc.getNumber("doc-22"));
        assertNull(doc.getString("doc-22"));
        assertNull(doc.getDate("doc-22"));
        assertNull(doc.getBlob("doc-22"));
        assertNull(doc.getArray("doc-22"));
        assertNull(doc.getDictionary("doc-22"));

        //#22 doc.setDate(JSONUtils.toDate(TEST_DATE));
        assertEquals(TEST_DATE, doc.getValue("doc-23"));
        assertTrue(doc.getBoolean("doc-23"));
        assertEquals(0, doc.getInt("doc-23"));
        assertEquals(0L, doc.getLong("doc-23"));
        assertEquals(0.0F, doc.getFloat("doc-23"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-23"), 0.001);
        assertNull(doc.getNumber("doc-23"));
        assertEquals(TEST_DATE, doc.getString("doc-23"));
        assertEquals(JSONUtils.toDate(TEST_DATE), doc.getDate("doc-23"));
        assertNull(doc.getBlob("doc-23"));
        assertNull(doc.getArray("doc-23"));
        assertNull(doc.getDictionary("doc-23"));

        //#23 doc.setArray(null);
        assertNull(doc.getValue("doc-24"));
        assertFalse(doc.getBoolean("doc-24"));
        assertEquals(0, doc.getInt("doc-24"));
        assertEquals(0L, doc.getLong("doc-24"));
        assertEquals(0.0F, doc.getFloat("doc-24"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-24"), 0.001);
        assertNull(doc.getNumber("doc-24"));
        assertNull(doc.getString("doc-24"));
        assertNull(doc.getDate("doc-24"));
        assertNull(doc.getBlob("doc-24"));
        assertNull(doc.getArray("doc-24"));
        assertNull(doc.getDictionary("doc-24"));

        //#24 doc.setDictionary(null);
        verifyArray(doc.getArray("doc-25"));

        //#25 doc.setDictionary(null);
        assertNull(doc.getValue("doc-26"));
        assertFalse(doc.getBoolean("doc-26"));
        assertEquals(0, doc.getInt("doc-26"));
        assertEquals(0L, doc.getLong("doc-26"));
        assertEquals(0.0F, doc.getFloat("doc-26"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-26"), 0.001);
        assertNull(doc.getNumber("doc-26"));
        assertNull(doc.getString("doc-26"));
        assertNull(doc.getDate("doc-26"));
        assertNull(doc.getBlob("doc-26"));
        assertNull(doc.getArray("doc-26"));
        assertNull(doc.getDictionary("doc-26"));

        //#26 doc.setDictionary(simpleDict);
        verifyDict(doc.getDictionary("doc-27"));

        //#27 doc.setDictionary(null);
        assertNull(doc.getValue("doc-28"));
        assertFalse(doc.getBoolean("doc-28"));
        assertEquals(0, doc.getInt("doc-28"));
        assertEquals(0L, doc.getLong("doc-28"));
        assertEquals(0.0F, doc.getFloat("doc-28"), 0.001F);
        assertEquals(0.0, doc.getDouble("doc-28"), 0.001);
        assertNull(doc.getNumber("doc-28"));
        assertNull(doc.getString("doc-28"));
        assertNull(doc.getDate("doc-28"));
        assertNull(doc.getBlob("doc-28"));
        assertNull(doc.getArray("doc-28"));
        assertNull(doc.getDictionary("doc-28"));

        verifyBlob(doc.getBlob("doc-29"));
    }

    protected Database openDatabase() throws CouchbaseLiteException {
        return verifyOrDeleteDb(createDb(getUniqueName("test_db")));
    }

    protected void reopenBaseTestDb() throws CouchbaseLiteException { baseTestDb = reopenDb(baseTestDb); }

    protected void recreateBastTestDb() throws CouchbaseLiteException { baseTestDb = recreateDb(baseTestDb); }

    protected Database duplicateBaseTestDb() throws CouchbaseLiteException {
        return verifyOrDeleteDb(duplicateDb(baseTestDb));
    }

    // Some JSON encoding will promote a Float to a Double.
    protected final Float demoteToFloat(Object val) {
        if (val instanceof Float) { return (Float) val; }
        if (val instanceof Double) { return ((Double) val).floatValue(); }
        throw new IllegalArgumentException("expected a floating point value");
    }

    private Database verifyOrDeleteDb(Database db) {
        try {
            assertNotNull(db);
            assertTrue(new File(db.getPath()).getCanonicalPath().endsWith(C4Database.DB_EXTENSION));

            return db;
        }
        catch (IOException e) {
            eraseDb(db);
            throw new AssertionError("Unable to get db path", e);
        }
        catch (AssertionError e) {
            eraseDb(db);
            throw e;
        }
    }
}
