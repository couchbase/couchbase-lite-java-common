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
import org.junit.Assert
import org.junit.Test


const val CONFIG_FACTORY_TEST_STRING = "midway down the midway"

class CommonConfigFactoryTest : BaseTest() {
    @Test
    fun testFullTextIndexConfigurationFactory() {
        val config = FullTextIndexConfigurationFactory.newConfig(CONFIG_FACTORY_TEST_STRING)
        Assert.assertEquals(1, config.expressions.size)
        Assert.assertEquals(CONFIG_FACTORY_TEST_STRING, config.expressions[0])
    }

    @Test
    fun testFullTextIndexConfigurationFactoryWithProps() {
        val config = FullTextIndexConfigurationFactory.newConfig(
            CONFIG_FACTORY_TEST_STRING,
            language = "fr",
            ignoreAccents = true
        )
        Assert.assertEquals(1, config.expressions.size)
        Assert.assertEquals(CONFIG_FACTORY_TEST_STRING, config.expressions[0])
        Assert.assertEquals(true, config.isIgnoringAccents)
        Assert.assertEquals("fr", config.language)
    }

    @Test
    fun testFullTextIndexConfigurationFactoryNullExp() {
        Assert.assertThrows(IllegalArgumentException::class.java) { FullTextIndexConfigurationFactory.newConfig() }
    }

    @Test
    fun testFullTextIndexConfigurationFactoryCopy() {
        val config1 = FullTextIndexConfigurationFactory.newConfig(CONFIG_FACTORY_TEST_STRING)
        val config2 = config1.newConfig()
        Assert.assertNotEquals(config1, config2)
        Assert.assertEquals(1, config2.expressions.size)
        Assert.assertEquals(CONFIG_FACTORY_TEST_STRING, config2.expressions[0])
    }

    @Test
    fun testValueIndexConfigurationFactory() {
        val config = ValueIndexConfigurationFactory.newConfig(CONFIG_FACTORY_TEST_STRING)
        Assert.assertEquals(1, config.expressions.size)
        Assert.assertEquals(CONFIG_FACTORY_TEST_STRING, config.expressions[0])
    }

    @Test
    fun testValueIndexConfigurationFactoryNullExp() {
        Assert.assertThrows(IllegalArgumentException::class.java) { ValueIndexConfigurationFactory.newConfig() }
    }

    @Test
    fun testValueIndexConfigurationFactoryCopy() {
        val config1 = ValueIndexConfigurationFactory.newConfig(CONFIG_FACTORY_TEST_STRING)
        val config2 = config1.newConfig()
        Assert.assertNotEquals(config1, config2)
        Assert.assertEquals(1, config2.expressions.size)
        Assert.assertEquals(CONFIG_FACTORY_TEST_STRING, config2.expressions[0])
    }

//    @Test
//    fun testLogFileConfigurationFactory() {
//        val config = LogFileConfigurationFactory.newConfig(directory = CONFIG_FACTORY_TEST_STRING, maxSize = 4096L)
//        Assert.assertEquals(CONFIG_FACTORY_TEST_STRING, config.directory)
//        Assert.assertEquals(4096L, config.maxSize)
//    }
//
//    @Test
//    fun testLogFileConfigurationFactoryNullDir() {
//        Assert.assertThrows(IllegalArgumentException::class.java) { LogFileConfigurationFactory.newConfig() }
//    }
//
//    @Test
//    fun
//            testLogFileConfigurationFactoryCopy() {
//        val config1 = LogFileConfigurationFactory.newConfig(directory = CONFIG_FACTORY_TEST_STRING, maxSize = 4096L)
//        val config2 = config1.newConfig(maxSize = 1024L)
//        Assert.assertNotEquals(config1, config2)
//        Assert.assertEquals(CONFIG_FACTORY_TEST_STRING, config2.directory)
//        Assert.assertEquals(1024L, config2.maxSize)
//    }

    @Test
    fun testFileLogSinkFactory() {
        val dir = getScratchDirectoryPath(getUniqueName("sink-dir"))
        FileLogSinkFactory.install(directory = dir, maxFileSize = 4096L)
        LogSinks.get().file?.let {
            Assert.assertEquals(dir, it.directory)
            Assert.assertEquals(4096L, it.maxFileSize)
        }
    }

    @Test
    fun testFileLogSinkFactoryNullDir() {
        Assert.assertThrows(IllegalArgumentException::class.java) { FileLogSinkFactory.install() }
    }

    @Test
    fun testFileLogSinkCopy() {
        val dir1 = getScratchDirectoryPath(getUniqueName("sink-dir1"))
        FileLogSinkFactory.install(directory = dir1, maxFileSize = 4096L)
        val sink = LogSinks.get().file
        sink?.let {
            Assert.assertEquals(dir1, it.directory)
            Assert.assertEquals(4096L, it.maxFileSize)
        }

        val dir2 = getScratchDirectoryPath(getUniqueName("sink-dir2"))
        sink.install(directory = dir2, maxFileSize = 8192L)
        LogSinks.get().file?.let {
            Assert.assertNotEquals(sink, it)
            Assert.assertEquals(dir2, it.directory)
            Assert.assertEquals(8192L, it.maxFileSize)
        }
    }
}

