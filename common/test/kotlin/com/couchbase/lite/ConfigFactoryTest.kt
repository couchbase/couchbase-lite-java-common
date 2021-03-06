//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://info.couchbase.com/rs/302-GJY-034/images/2017-10-30_License_Agreement.pdf
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test


const val CONFIG_FACTORY_TEST_STRING = "midway down the midway"

class ConfigFactoryTest : BaseTest() {
    @Test
    fun testFullTextIndexConfigurationFactory() {
        val config = FullTextIndexConfigurationFactory.create(CONFIG_FACTORY_TEST_STRING)
        assertEquals(1, config.expressions.size)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config.expressions[0])
    }

    @Test(expected = IllegalStateException::class)
    fun testFullTextIndexConfigurationFactoryNullExp() {
        FullTextIndexConfigurationFactory.create()
    }

    @Test
    fun testFullTextIndexConfigurationFactoryCopy() {
        val config1 = FullTextIndexConfigurationFactory.create(CONFIG_FACTORY_TEST_STRING)
        val config2 = config1.create()
        assertNotEquals(config1, config2)
        assertEquals(1, config2.expressions.size)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config2.expressions[0])
    }

    @Test
    fun testValueIndexConfigurationFactory() {
        val config = ValueIndexConfigurationFactory.create(CONFIG_FACTORY_TEST_STRING)
        assertEquals(1, config.expressions.size)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config.expressions[0])
    }

    @Test(expected = IllegalStateException::class)
    fun testValueIndexConfigurationFactoryNullExp() {
        ValueIndexConfigurationFactory.create()
    }

    @Test
    fun testValueIndexConfigurationFactoryCopy() {
        val config1 = ValueIndexConfigurationFactory.create(CONFIG_FACTORY_TEST_STRING)
        val config2 = config1.create()
        assertNotEquals(config1, config2)
        assertEquals(1, config2.expressions.size)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config2.expressions[0])
    }

    @Test
    fun testLogFileConfigurationFactory() {
        val config = LogFileConfigurationFactory.create(directory = CONFIG_FACTORY_TEST_STRING, maxSize = 4096L)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config.directory)
        assertEquals(4096L, config.maxSize)
    }

    @Test(expected = IllegalStateException::class)
    fun testLogFileConfigurationFactoryNullDir() {
        LogFileConfigurationFactory.create()
    }

    @Test
    fun testLogFileConfigurationFactoryCopy() {
        val config1 = LogFileConfigurationFactory.create(directory = CONFIG_FACTORY_TEST_STRING, maxSize = 4096L)
        val config2 = config1.create(maxSize = 1024L)
        assertNotEquals(config1, config2)
        assertEquals(CONFIG_FACTORY_TEST_STRING, config2.directory)
        assertEquals(1024L, config2.maxSize)
    }
}