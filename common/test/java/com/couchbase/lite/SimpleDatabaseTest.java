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

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.core.C4TestUtils;


public class SimpleDatabaseTest extends BaseTest {
    @Test
    public void testCreateConfiguration() {
        // Default:
        DatabaseConfiguration config1 = new DatabaseConfiguration();
        Assert.assertNotNull(config1.getDirectory());
        Assert.assertFalse(config1.getDirectory().isEmpty());

        // Custom
        DatabaseConfiguration config2 = new DatabaseConfiguration();
        String dbDir = getScratchDirectoryPath(getUniqueName("tmp"));
        config2.setDirectory(dbDir);
        Assert.assertEquals(dbDir, config2.getDirectory());
    }

    @Test
    public void testFullSync() {
        DatabaseConfiguration config = new DatabaseConfiguration();

        // # 1.2 TestSQLiteFullSyncDefault
        Assert.assertEquals(Defaults.Database.FULL_SYNC, config.isFullSync());

        config = new DatabaseConfiguration();

        // # 1.3-4 TestSetGetFullSync
        config.setFullSync(true);
        Assert.assertTrue(config.isFullSync());
        // # 1.5-6 TestSetGetFullSync
        config.setFullSync(false);
        Assert.assertFalse(config.isFullSync());
    }

    /**
     * Steps
     * 1. Create a DatabaseConfiguration object.
     * 2. Get and check that the value of the mmapEnabled property is true.
     * 3. Set the mmapEnabled property to false and verify that the value is false.
     * 4. Set the mmapEnabled property to true, and verify that the mmap value is true.
     */
    @Test
    public void testMMapConfig() {
        DatabaseConfiguration config = new DatabaseConfiguration();

        Assert.assertEquals(Defaults.Database.MMAP_ENABLED, config.isMMapEnabled());

        config.setMMapEnabled(false);
        Assert.assertFalse(config.isMMapEnabled());

        config.setMMapEnabled(true);
        Assert.assertTrue(config.isMMapEnabled());
    }

    @Test
    public void testGetSetConfiguration() {
        final DatabaseConfiguration config
            = new DatabaseConfiguration().setDirectory(getScratchDirectoryPath(getUniqueName("get-set-config-dir")));

        final Database db = createDb("get_set_config_db", config);
        try {
            final DatabaseConfiguration newConfig = db.getConfig();
            Assert.assertNotNull(newConfig);
            Assert.assertEquals(config.getDirectory(), newConfig.getDirectory());
        }
        finally { eraseDb(db); }
    }

    @Test
    public void testConfigurationIsCopiedWhenGetSet() {
        final DatabaseConfiguration config
            = new DatabaseConfiguration().setDirectory(getScratchDirectoryPath(getUniqueName("copy-config-dir")));

        final Database db = createDb("config_copied_db", config);
        try {
            Assert.assertNotNull(db.getConfig());
            Assert.assertNotSame(db.getConfig(), config);
        }
        finally { eraseDb(db); }
    }

    @Test
    public void testDatabaseConfigurationDefaultDirectory() throws CouchbaseLiteException, IOException {
        final String expectedPath = CouchbaseLiteInternal.getDefaultDbDir().getAbsolutePath();

        final DatabaseConfiguration config = new DatabaseConfiguration();
        Assert.assertEquals(config.getDirectory(), expectedPath);

        Database db = createDb("default_dir_db", config);
        try { Assert.assertTrue(new File(db.getPath()).getCanonicalPath().contains(expectedPath)); }
        finally { db.delete(); }
    }


    @SuppressWarnings("deprecation")
    @Test
    public void testCreateWithDefaultConfiguration() {
        Database db = createDb("default_config_db");
        try {
            Assert.assertNotNull(db);
            Assert.assertEquals(0, db.getCount());
        }
        finally { eraseDb(db); }
    }

    @Test
    public void testDBWithFullSync() throws CouchbaseLiteException, LiteCoreException {
        final DatabaseConfiguration config = new DatabaseConfiguration();

        Database db = new Database(getUniqueName("full_sync_db_1"), config.setFullSync(true));
        try {
            Assert.assertTrue(db.getConfig().isFullSync());
            Assert.assertEquals(
                C4Constants.DatabaseFlags.DISC_FULL_SYNC,
                C4TestUtils.getFlags(db.getOpenC4Database()) & C4Constants.DatabaseFlags.DISC_FULL_SYNC);
        }
        finally { eraseDb(db); }

        db = new Database(getUniqueName("full_sync_db_2"), config.setFullSync(false));
        try {
            Assert.assertFalse(db.getConfig().isFullSync());
            Assert.assertEquals(
                0,
                C4TestUtils.getFlags(db.getOpenC4Database()) & C4Constants.DatabaseFlags.DISC_FULL_SYNC);
        }
        finally { eraseDb(db); }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithEmptyDBNames() throws CouchbaseLiteException { new Database(""); }

    @Test
    public void testCreateWithSpecialCharacterDBNames() throws CouchbaseLiteException {
        Database db = new Database(LEGAL_FILE_NAME_CHARS);
        try { Assert.assertEquals(LEGAL_FILE_NAME_CHARS, db.getName()); }
        finally { eraseDb(db); }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testCreateWithCustomDirectory() throws CouchbaseLiteException, IOException {
        final File dir = new File(getScratchDirectoryPath(getUniqueName("create-custom-dir")));

        final String dbName = getUniqueName("create_custom_db");

        // create db with custom directory
        DatabaseConfiguration config = new DatabaseConfiguration().setDirectory(dir.getCanonicalPath());
        Database db = new Database(dbName, config);

        try {
            Assert.assertNotNull(db);
            Assert.assertTrue(Database.exists(dbName, dir));

            Assert.assertEquals(dbName, db.getName());

            final String path = new File(db.getPath()).getCanonicalPath();
            Assert.assertTrue(path.endsWith(C4Database.DB_EXTENSION));
            Assert.assertTrue(path.contains(dir.getPath()));

            Assert.assertEquals(0, db.getCount());
        }
        finally {
            eraseDb(db);
        }
    }
}
