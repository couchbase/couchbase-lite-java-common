package com.couchbase.lite.internal.core;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteCoreException;

import static org.junit.Assert.assertEquals;


public class C4PathsQueryTest extends C4QueryBaseTest {
    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Before
    public final void setUpC4PathsQueryTest() throws CouchbaseLiteException { importJSONLinesSafely("paths.json"); }

    // - DB Query ANY w/paths
    @Test
    public void testDBQueryANYwPaths() throws LiteCoreException {
        // For https://github.com/couchbase/couchbase-lite-core/issues/238
        compile(json5("['ANY','path',['.paths'],['=',['?path','city'],'San Jose']]"));
        assertEquals(Arrays.asList("0000001"), run());

        compile(json5("['ANY','path',['.paths'],['=',['?path.city'],'San Jose']]"));
        assertEquals(Arrays.asList("0000001"), run());

        compile(json5("['ANY','path',['.paths'],['=',['?path','city'],'Palo Alto']]"));
        assertEquals(Arrays.asList("0000001", "0000002"), run());
    }
}
