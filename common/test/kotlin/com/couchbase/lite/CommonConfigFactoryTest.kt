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
package com.couchbase.lite

import com.couchbase.lite.logging.LogSinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test


const val CONFIG_FACTORY_TEST_STRING = "midway down the midway"

class CommonConfigFactoryTest : BaseTest() {
    @Test
    fun testFullTextIndexConfigurationFactory() {
        val config = FullTextIndexConfigurationFactory.newConfig(CONFIG_FACTORY_TEST_STRING)
        assertEquals(1, config.expressions.size)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config.expressions[0])
    }

    @Test
    fun testFullTextIndexConfigurationFactoryWithProps() {
        val config = FullTextIndexConfigurationFactory.newConfig(
            CONFIG_FACTORY_TEST_STRING,
            language = "fr",
            ignoreAccents = true
        )
        assertEquals(1, config.expressions.size)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config.expressions[0])
        assertEquals(true, config.isIgnoringAccents)
        assertEquals("fr", config.language)
    }

    @Test
    fun testFullTextIndexConfigurationFactoryNullExp() {
        assertThrows(IllegalArgumentException::class.java) { FullTextIndexConfigurationFactory.newConfig() }
    }

    @Test
    fun testFullTextIndexConfigurationFactoryCopy() {
        val config1 = FullTextIndexConfigurationFactory.newConfig(CONFIG_FACTORY_TEST_STRING)
        val config2 = config1.newConfig()
        assertNotEquals(config1, config2)
        assertEquals(1, config2.expressions.size)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config2.expressions[0])
    }

    @Test
    fun testValueIndexConfigurationFactory() {
        val config = ValueIndexConfigurationFactory.newConfig(CONFIG_FACTORY_TEST_STRING)
        assertEquals(1, config.expressions.size)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config.expressions[0])
    }

    @Test
    fun testValueIndexConfigurationFactoryNullExp() {
        assertThrows(IllegalArgumentException::class.java) { ValueIndexConfigurationFactory.newConfig() }
    }

    @Test
    fun testValueIndexConfigurationFactoryCopy() {
        val config1 = ValueIndexConfigurationFactory.newConfig(CONFIG_FACTORY_TEST_STRING)
        val config2 = config1.newConfig()
        assertNotEquals(config1, config2)
        assertEquals(1, config2.expressions.size)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config2.expressions[0])
    }

    @Test
    fun testFileLogSinkFactory() {
        val dir = getScratchDirectoryPath(getUniqueName("sink-dir"))
        FileLogSinkFactory.install(directory = dir, maxFileSize = 4096L)
        LogSinks.get().file?.let {
            assertEquals(dir, it.directory)
            assertEquals(4096L, it.maxFileSize)
        }
    }

    @Test
    fun testFileLogSinkactoryNullDir() {
        assertThrows(IllegalArgumentException::class.java) { FileLogSinkFactory.install() }
    }

    @Test
    fun testFileLogSinkCopy() {
        val dir1 = getScratchDirectoryPath(getUniqueName("sink-dir1"))
        FileLogSinkFactory.install(directory = dir1, maxFileSize = 4096L)
        val sink = LogSinks.get().file
        sink?.let {
            assertEquals(dir1, it.directory)
            assertEquals(4096L, it.maxFileSize)
        }

        val dir2 = getScratchDirectoryPath(getUniqueName("sink-dir2"))
        sink.install(directory = dir2, maxFileSize = 8192L)
        LogSinks.get().file?.let {
            assertNotEquals(sink, it)
            assertEquals(dir2, it.directory)
            assertEquals(8192L, it.maxFileSize)
        }
    }
}
