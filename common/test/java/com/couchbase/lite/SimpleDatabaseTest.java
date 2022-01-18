//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;


public class SimpleDatabaseTest extends BaseTest {
    @Test
    public void testCreateConfiguration() {
        // Default:
        DatabaseConfiguration config1 = new DatabaseConfiguration();
        assertNotNull(config1.getDirectory());
        assertFalse(config1.getDirectory().isEmpty());

        // Custom
        DatabaseConfiguration config2 = new DatabaseConfiguration();
        String dbDir = getScratchDirectoryPath(getUniqueName("tmp"));
        config2.setDirectory(dbDir);
        assertEquals(dbDir, config2.getDirectory());
    }

    @Test
    public void testGetSetConfiguration() throws CouchbaseLiteException {
        final DatabaseConfiguration config
            = new DatabaseConfiguration().setDirectory(getScratchDirectoryPath(getUniqueName("get-set-config-dir")));

        final Database db = createDb("get_set_config_db", config);
        try {
            final DatabaseConfiguration newConfig = db.getConfig();
            assertNotNull(newConfig);
            assertEquals(config.getDirectory(), newConfig.getDirectory());
        }
        finally { deleteDb(db); }
    }

    @Test
    public void testConfigurationIsCopiedWhenGetSet() throws CouchbaseLiteException {
        final DatabaseConfiguration config
            = new DatabaseConfiguration().setDirectory(getScratchDirectoryPath(getUniqueName("copy-config-dir")));

        final Database db = createDb("config_copied_db", config);
        try {
            assertNotNull(db.getConfig());
            assertNotSame(db.getConfig(), config);
        }
        finally { deleteDb(db); }
    }

    @Test
    public void testDatabaseConfigurationDefaultDirectory() throws CouchbaseLiteException, IOException {
        final String expectedPath = CouchbaseLiteInternal.getDefaultDbDir().getAbsolutePath();

        final DatabaseConfiguration config = new DatabaseConfiguration();
        assertEquals(config.getDirectory(), expectedPath);

        Database db = createDb("default_dir_db", config);
        try { assertTrue(new File(db.getPath()).getCanonicalPath().contains(expectedPath)); }
        finally { db.delete(); }
    }

    @Test
    public void testCreateWithDefaultConfiguration() throws CouchbaseLiteException {
        Database db = createDb("default_config_db");
        try {
            assertNotNull(db);
            assertEquals(0, db.getCount());
        }
        finally { deleteDb(db); }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithEmptyDBNames() throws CouchbaseLiteException { new Database(""); }

    @Test
    public void testCreateWithSpecialCharacterDBNames() throws CouchbaseLiteException {
        Database db = new Database(LEGAL_FILE_NAME_CHARS);
        try { assertEquals(LEGAL_FILE_NAME_CHARS, db.getName()); }
        finally { deleteDb(db); }
    }

    @Test
    public void testCreateWithCustomDirectory() throws CouchbaseLiteException, IOException {
        final File dir = new File(getScratchDirectoryPath(getUniqueName("create-custom-dir")));

        final String dbName = getUniqueName("create_custom_db");

        // create db with custom directory
        DatabaseConfiguration config = new DatabaseConfiguration().setDirectory(dir.getCanonicalPath());
        Database db = new Database(dbName, config);

        try {
            assertNotNull(db);
            assertTrue(Database.exists(dbName, dir));

            assertEquals(dbName, db.getName());

            final String path = new File(db.getPath()).getCanonicalPath();
            assertTrue(path.endsWith(C4Database.DB_EXTENSION));
            assertTrue(path.contains(dir.getPath()));

            assertEquals(0, db.getCount());
        }
        finally {
            deleteDb(db);
        }
    }
}
