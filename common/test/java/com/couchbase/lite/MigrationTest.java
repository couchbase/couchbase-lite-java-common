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
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.PlatformUtils;
import com.couchbase.lite.internal.utils.ZipUtils;


public class MigrationTest extends BaseTest {
    private static final String DB_NAME = "android-sqlite";
    private static final String TEST_KEY = "key";


    private File dbDir;
    private Database migrationTestDb;

    @Before
    public final void setUpMigrationTest() {
        dbDir = new File(CouchbaseLiteInternal.getDefaultDbDir(), getUniqueName("migration-test-dir"));
    }

    @After
    public final void tearDownMigrationTest() {
        eraseDb(migrationTestDb);
        FileUtils.eraseFileOrDir(dbDir);
    }

    // TODO: 1.x DB's attachment is not automatically detected as blob
    // https://github.com/couchbase/couchbase-lite-android/issues/1237
    @Test
    public void testOpenExistingDBv1x() throws Exception {
        ZipUtils.unzip(PlatformUtils.getAsset("android140-sqlite.cblite2.zip"), dbDir);

        migrationTestDb = openDatabase();
        Collection migrationTestCollection = migrationTestDb.getDefaultCollection();
        Assert.assertEquals(2, migrationTestCollection.getCount());
        for (int i = 1; i <= 2; i++) {
            Document doc = migrationTestCollection.getDocument("doc" + i);
            Assert.assertNotNull(doc);
            Assert.assertEquals(String.valueOf(i), doc.getString(TEST_KEY));

            Dictionary attachments = doc.getDictionary("_attachments");
            Assert.assertNotNull(attachments);
            String key = "attach" + i;

            Blob blob = attachments.getBlob(key);
            Assert.assertNotNull(blob);
            byte[] attach = String.format(Locale.ENGLISH, "attach%d", i).getBytes(StandardCharsets.UTF_8);
            Assert.assertArrayEquals(attach, blob.getContent());
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1237
    @Test
    public void testOpenExistingDBv1xNoAttachment() throws Exception {
        ZipUtils.unzip(PlatformUtils.getAsset("android140-sqlite-noattachment.cblite2.zip"), dbDir);

        migrationTestDb = openDatabase();
        Collection migrationTestCollection = migrationTestDb.getDefaultCollection();
        Assert.assertEquals(2, migrationTestCollection.getCount());
        for (int i = 1; i <= 2; i++) {
            Document doc = migrationTestCollection.getDocument("doc" + i);
            Assert.assertNotNull(doc);
            Assert.assertEquals(String.valueOf(i), doc.getString(TEST_KEY));
        }
    }

    @Test
    public void testOpenExistingDB() throws Exception {
        ZipUtils.unzip(PlatformUtils.getAsset("android200-sqlite.cblite2.zip"), dbDir);

        migrationTestDb = openDatabase();
        Collection migrationTestCollection = migrationTestDb.getDefaultCollection();
        Assert.assertEquals(2, migrationTestCollection.getCount());

        for (int i = 1; i <= 2; i++) {
            Document doc = migrationTestCollection.getDocument("doc" + i);
            Assert.assertNotNull(doc);
            Assert.assertEquals(String.valueOf(i), doc.getString(TEST_KEY));
            Blob blob = doc.getBlob("attach" + i);
            Assert.assertNotNull(blob);
            byte[] attach = String.format(Locale.ENGLISH, "attach%d", i).getBytes(StandardCharsets.UTF_8);
            Assert.assertArrayEquals(attach, blob.getContent());
        }
    }

    private Database openDatabase() throws IOException, CouchbaseLiteException {
        final DatabaseConfiguration config = new DatabaseConfiguration();
        config.setDirectory(dbDir.getCanonicalPath());
        return new Database(DB_NAME, config);
    }
}
