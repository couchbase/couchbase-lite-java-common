package com.couchbase.lite;

import java.util.Locale;

import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.internal.utils.Report;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertNotNull;



public class BaseCollectionTest extends BaseDbTest {
    protected Collection testCollection;
    protected Scope testScope;

    @Before
    public final void setUpBaseCollectionTest(){
        testScope = baseTestDb.getDefaultScope();
        testCollection = testScope.getDefaultCollection();
        Report.log(LogLevel.INFO, "Created base test Collection: " + testCollection);
        assertNotNull(testCollection);
    }

    @After
    public final void tearDownBaseCollectionTest() {
        try { testScope.deleteCollection(testCollection); }
        catch (CouchbaseLiteException ignore) { }
        Report.log(LogLevel.INFO, "Deleted testCollection: " + testCollection);
    }

    protected final Document createSingleDocInCollectionWithId(String docID) throws CouchbaseLiteException {
        final long n = testCollection.getCount();

        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("key", 1);
        Document savedDoc = saveDocInBaseCollectionTest(doc);

        assertEquals(n + 1, testCollection.getCount());
        assertEquals(1, savedDoc.getSequence());

        return savedDoc;
    }

    protected final Document saveDocInBaseCollectionTest(MutableDocument doc) throws CouchbaseLiteException {
        testCollection.save(doc);

        Document savedDoc = testCollection.getDocument(doc.getId());
        assertNotNull(savedDoc);
        assertEquals(doc.getId(), savedDoc.getId());

        return savedDoc;
    }

    protected final void createDocsInBaseCollectionTest(int n) throws CouchbaseLiteException {
        for (int i = 0; i < n; i++) {
            MutableDocument doc = new MutableDocument(String.format(Locale.US, "doc_%03d", i));
            doc.setValue("key", i);
            saveDocInBaseCollectionTest(doc);
        }
        assertEquals(n, testCollection.getCount());
    }
}

