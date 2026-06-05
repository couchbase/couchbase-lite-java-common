//
// Copyright (c) 2022 Couchbase, Inc All rights reserved.
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
@file:Suppress("DEPRECATION")

package com.couchbase.lite

import com.couchbase.lite.internal.core.CBLVersion
import com.couchbase.lite.internal.logging.Log
import com.couchbase.lite.internal.logging.LogSinksImpl
import com.couchbase.lite.logging.BaseLogSink
import com.couchbase.lite.logging.ConsoleLogSink
import com.couchbase.lite.logging.FileLogSink
import com.couchbase.lite.logging.LogSinks
import com.couchbase.lite.utils.KotlinHelpers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.util.Locale
import java.util.UUID
import kotlin.Array


private class TestDeprecatedConsoleLogger : ConsoleLogger() {
    private val buf = StringBuilder()
    val content
        get() = buf.toString()

    override fun shimFactory(level: LogLevel, domain: EnumSet<LogDomain>): ShimLogger {
        return object : ShimLogger(level, domain) {
            override fun doWriteLog(level: LogLevel, domain: LogDomain, message: String) {
                buf.append(message)
            }
        }
    }

    fun clearContent() = buf.clear()
}

class LegacyLogTest : BaseDbTest() {
    private var scratchDirPath: String? = null

    private val tempDir: File?
        get() {
            val dir = scratchDirPath
            return dir?.let { File(it) }
        }

    private val logFiles: Array<File>
        get() = assertNonNull(tempDir?.listFiles())

    @Before
    fun setUpLogTest() {
        scratchDirPath = getScratchDirectoryPath(getUniqueName("log-dir"))
        LogSinksImpl.initLogging()
    }

    @After
    fun tearDownLogTest() = LogSinksImpl.initLogging()

    @Test
    fun testConsoleLoggerLevel() {
        val consoleLogger = TestDeprecatedConsoleLogger()

        consoleLogger.setDomains(LogDomain.DATABASE)
        for (level in LogLevel.values()) {
            if (level == LogLevel.NONE) {
                continue
            }

            consoleLogger.level = level
            consoleLogger.log(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogger.log(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogger.log(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogger.log(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogger.log(LogLevel.ERROR, LogDomain.DATABASE, "E")
        }

        Assert.assertEquals("DVIWEVIWEIWEWEE", consoleLogger.content)
    }

    @Test
    fun testConsoleLoggerDomains() {
        val consoleLogger = TestDeprecatedConsoleLogger()

        consoleLogger.setDomains()
        for (level in LogLevel.values()) {
            if (level == LogLevel.NONE) {
                continue
            }

            consoleLogger.level = level
            consoleLogger.log(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogger.log(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogger.log(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogger.log(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogger.log(LogLevel.ERROR, LogDomain.DATABASE, "E")
        }
        Assert.assertEquals("", consoleLogger.content)
        consoleLogger.clearContent()

        consoleLogger.setDomains(LogDomain.NETWORK, LogDomain.QUERY)
        for (level in LogLevel.values()) {
            if (level == LogLevel.NONE) {
                continue
            }

            consoleLogger.level = level
            consoleLogger.log(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogger.log(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogger.log(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogger.log(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogger.log(LogLevel.ERROR, LogDomain.DATABASE, "E")
        }
        Assert.assertEquals("", consoleLogger.content)

        consoleLogger.domains = LogDomain.ALL_DOMAINS
        consoleLogger.level = LogLevel.DEBUG
        consoleLogger.log(LogLevel.DEBUG, LogDomain.NETWORK, "N")
        consoleLogger.log(LogLevel.DEBUG, LogDomain.QUERY, "Q")
        consoleLogger.log(LogLevel.DEBUG, LogDomain.DATABASE, "D")
        Assert.assertEquals("NQD", consoleLogger.content)
    }

    @Test
    fun testFileLoggerDefaults() {
        val config = LogFileConfiguration("up/down")
        Assert.assertEquals(Defaults.LogFile.MAX_SIZE, config.maxSize)
        Assert.assertEquals(Defaults.LogFile.MAX_ROTATE_COUNT, config.maxRotateCount)
        Assert.assertEquals(Defaults.LogFile.USE_PLAINTEXT, config.usesPlaintext())
    }

    @Test
    fun testFileLoggingLevels() {
        val mark = "$$$$ ${UUID.randomUUID()}"
        testWithConfiguration(
            LogLevel.DEBUG,
            LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true).setMaxRotateCount(0)
        ) {
            for (level in LogLevel.values()) {
                if (level == LogLevel.NONE) {
                    continue
                }
                Database.log.file.level = level

                Log.d(LogDomain.DATABASE, mark)
                Log.i(LogDomain.DATABASE, mark)
                Log.w(LogDomain.DATABASE, mark)
                Log.e(LogDomain.DATABASE, mark)
            }

            for (log in logFiles) {
                var lineCount = 0
                BufferedReader(FileReader(log)).use {
                    while (true) {
                        val l = it.readLine() ?: break
                        if (l.contains(mark)) {
                            lineCount++
                        }
                    }
                }

                val logPath = log.canonicalPath
                when {
                    logPath.contains("error") -> Assert.assertEquals(5, lineCount)
                    logPath.contains("warning") -> Assert.assertEquals(4, lineCount)
                    logPath.contains("info") -> Assert.assertEquals(3, lineCount)
                    logPath.contains("debug") -> Assert.assertEquals(1, lineCount)
                    logPath.contains("verbose") -> Assert.assertEquals(0, lineCount)
                }
            }
        }
    }

    @Test
    fun testFileLoggingDefaultBinaryFormat() {
        testWithConfiguration(LogLevel.INFO, LogFileConfiguration(scratchDirPath!!)) {
            Log.i(LogDomain.DATABASE, "TEST INFO")

            val files = logFiles
            Assert.assertTrue(files.isNotEmpty())

            val lastModifiedFile = getMostRecent(files)
            Assert.assertNotNull(lastModifiedFile)

            val bytes = ByteArray(4)
            FileInputStream(lastModifiedFile!!).use { inStr -> Assert.assertEquals(4, inStr.read(bytes)) }
            Assert.assertEquals(0xCF.toByte(), bytes[0])
            Assert.assertEquals(0xB2.toByte(), bytes[1])
            Assert.assertEquals(0xAB.toByte(), bytes[2])
            Assert.assertEquals(0x1B.toByte(), bytes[3])
        }
    }

    @Test
    fun testFileLoggingUsePlainText() {
        testWithConfiguration(LogLevel.INFO, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            val uuidString = UUID.randomUUID().toString()
            Log.i(LogDomain.DATABASE, uuidString)
            val files = tempDir!!.listFiles { _: File?, name: String ->
                name.lowercase(Locale.getDefault()).startsWith("cbl_info_")
            }

            Assert.assertNotNull(files)
            Assert.assertEquals(1, files?.size ?: 0)

            val file = getMostRecent(files)
            Assert.assertNotNull(file)
            Assert.assertTrue(getLogContents(file!!).contains(uuidString))
        }
    }

    @Test
    fun testFileLoggingLogFilename() {
        testWithConfiguration(LogLevel.DEBUG, LogFileConfiguration(scratchDirPath!!)) {
            Log.e(LogDomain.DATABASE, "$$\$TEST MESSAGE")

            val files = logFiles
            Assert.assertTrue(files.size >= 4)

            val rex = Regex("cbl_(debug|verbose|info|warning|error)_\\d+\\.cbllog")
            for (file in files) {
                Assert.assertTrue(file.name.matches(rex))
            }
        }
    }

    @Test
    fun testFileLoggingMaxSize() {
        val config = LogFileConfiguration(scratchDirPath!!)
            .setUsePlaintext(true)
            .setMaxSize(1024)
            .setMaxRotateCount(10)
        testWithConfiguration(LogLevel.DEBUG, config) {
            // This should create two files for each of the 5 levels except verbose (debug, info, warning, error):
            // 1k of logs plus .5k headers. There should be only one file at the verbose level (just the headers)
            write1KBToLog()
            Assert.assertEquals((4 * 2) + 1, logFiles.size)
        }
    }

    @Test
    fun testFileLoggingDisableLogging() {
        val uuidString = UUID.randomUUID().toString()

        testWithConfiguration(LogLevel.NONE, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            writeAllLogs(uuidString)
            for (log in logFiles) {
                Assert.assertFalse(getLogContents(log).contains(uuidString))
            }
        }
    }

    @Test
    fun testFileLoggingReEnableLogging() {
        val uuidString = UUID.randomUUID().toString()

        testWithConfiguration(LogLevel.NONE, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            writeAllLogs(uuidString)

            for (log in logFiles) {
                Assert.assertFalse(getLogContents(log).contains(uuidString))
            }

            Database.log.file.level = LogLevel.INFO
            writeAllLogs(uuidString)

            val logFiles = tempDir!!.listFiles()
            Assert.assertNotNull(tempDir!!.listFiles())
            for (log in logFiles!!) {
                val fn = log.name.lowercase(Locale.getDefault())
                if (fn.startsWith("cbl_debug_") || fn.startsWith("cbl_verbose_")) {
                    Assert.assertFalse(getLogContents(log).contains(uuidString))
                } else {
                    Assert.assertTrue(getLogContents(log).contains(uuidString))
                }
            }
        }
    }

    @Test
    fun testFileLoggingHeader() {
        testWithConfiguration(LogLevel.VERBOSE, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            write1KBToLog()
            for (log in logFiles) {
                var logLine: String
                BufferedReader(FileReader(log)).use {
                    logLine = it.readLine()
                    logLine = it.readLine() // skip the LiteCore log line...
                }
                Assert.assertNotNull(logLine)
                Assert.assertTrue(logLine.contains("CouchbaseLite $PRODUCT"))
                Assert.assertTrue(logLine.contains("Core/"))
                Assert.assertTrue(logLine.contains(CBLVersion.getSysInfo()))
            }
        }
    }

    @Test
    fun testWriteLogWithError() {
        val message = "test message"
        val uuid = UUID.randomUUID().toString()
        val error = CouchbaseLiteException(uuid)

        testWithConfiguration(LogLevel.DEBUG, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            Log.d(LogDomain.DATABASE, message, error)
            Log.i(LogDomain.DATABASE, message, error)
            Log.w(LogDomain.DATABASE, message, error)
            Log.e(LogDomain.DATABASE, message, error)

            for (log in logFiles) {
                if (!log.name.contains("verbose")) {
                    Assert.assertTrue(getLogContents(log).contains(uuid))
                }
            }
        }
    }

    @Test
    fun testWriteLogWithErrorAndArgs() {
        val uuid1 = UUID.randomUUID().toString()
        val uuid2 = UUID.randomUUID().toString()
        val message = "test message %s"
        val error = CouchbaseLiteException(uuid1)

        testWithConfiguration(LogLevel.DEBUG, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            Log.d(LogDomain.DATABASE, message, error, uuid2)
            Log.i(LogDomain.DATABASE, message, error, uuid2)
            Log.w(LogDomain.DATABASE, message, error, uuid2)
            Log.e(LogDomain.DATABASE, message, error, uuid2)

            for (log in logFiles) {
                if (!log.name.contains("verbose")) {
                    val content = getLogContents(log)
                    Assert.assertTrue(content.contains(uuid1))
                    Assert.assertTrue(content.contains(uuid2))
                }
            }
        }
    }

    @Test
    fun testLogFileConfigurationConstructors() {
        val rotateCount = 4
        val maxSize = 2048L
        val usePlainText = true

        Assert.assertThrows(IllegalArgumentException::class.java) {
            KotlinHelpers.createLogFileConfigWithNullConfig()
        }

        Assert.assertThrows(IllegalArgumentException::class.java) {
            KotlinHelpers.createLogFileConfigWithNullDir()
        }

        val config = LogFileConfiguration(scratchDirPath!!)
            .setMaxRotateCount(rotateCount)
            .setMaxSize(maxSize)
            .setUsePlaintext(usePlainText)

        Assert.assertEquals(rotateCount, config.maxRotateCount)
        Assert.assertEquals(maxSize, config.maxSize)
        Assert.assertEquals(usePlainText, config.usesPlaintext())
        Assert.assertEquals(scratchDirPath, config.directory)

        val tempDir2 = getScratchDirectoryPath(getUniqueName("logtest2"))
        val newConfig = LogFileConfiguration(tempDir2, config)
        Assert.assertEquals(rotateCount, newConfig.maxRotateCount)
        Assert.assertEquals(maxSize, newConfig.maxSize)
        Assert.assertEquals(usePlainText, newConfig.usesPlaintext())
        Assert.assertEquals(tempDir2, newConfig.directory)
    }

    @Test
    fun testEditReadOnlyLogFileConfiguration() {
        testWithConfiguration(LogLevel.DEBUG, LogFileConfiguration(scratchDirPath!!)) {
            Assert.assertThrows(CouchbaseLiteError::class.java) { Database.log.file.config!!.maxSize = 1024 }
            Assert.assertThrows(CouchbaseLiteError::class.java) { Database.log.file.config!!.maxRotateCount = 3 }
            Assert.assertThrows(CouchbaseLiteError::class.java) { Database.log.file.config!!.setUsePlaintext(true) }
        }
    }

    @Test
    fun testSetNewLogFileConfiguration() {
        val config = LogFileConfiguration(scratchDirPath!!)
        val fileLogger = Database.log.file
        fileLogger.config = config
        Assert.assertEquals(config, fileLogger.config)
        fileLogger.config = null
        Assert.assertNull(fileLogger.config)
        fileLogger.config = config
        Assert.assertEquals(config, fileLogger.config)
        fileLogger.config = LogFileConfiguration("$scratchDirPath/legacyLogs")
        Assert.assertEquals(LogFileConfiguration("$scratchDirPath/legacyLogs"), fileLogger.config)
    }

    @Test
    fun testMixLegacyAndNewAPIs1() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            val fileLogger = Database.log.file
            fileLogger.config = LogFileConfiguration(scratchDirPath!!)
            fileLogger.setLevel(LogLevel.VERBOSE)
            LogSinks.get().file = FileLogSink.Builder().setDirectory(scratchDirPath!!).setLevel(LogLevel.ERROR).build()
        }
    }

    @Test
    fun testMixLegacyAndNewAPIs2() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            LogSinks.get().file = FileLogSink.Builder().setDirectory(scratchDirPath!!).setLevel(LogLevel.ERROR).build()
            val fileLogger = Database.log.file
            fileLogger.config = LogFileConfiguration(scratchDirPath!!)
            fileLogger.setLevel(LogLevel.VERBOSE)
        }
    }

    @Test
    fun testMixLegacyAndNewAPIs3() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            Database.log.console.level = LogLevel.VERBOSE
            LogSinks.get().console = ConsoleLogSink(LogLevel.ERROR, LogDomain.ALL)
        }
    }

    @Test
    fun testMixLegacyAndNewAPIs4() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            LogSinks.get().console = ConsoleLogSink(LogLevel.VERBOSE, LogDomain.ALL)
            Database.log.console.level = LogLevel.ERROR
        }
    }

    @Test
    fun testMixLegacyAndNewAPIs5() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            Database.log.custom = object : Logger {
                override fun getLevel() = LogLevel.VERBOSE
                override fun log(level: LogLevel, domain: LogDomain, message: String) {}
            }
            LogSinks.get().custom = object : BaseLogSink(LogLevel.ERROR, LogDomain.ALL) {
                override fun writeLog(level: LogLevel, domain: LogDomain, message: String) {}
            }
        }
    }

    @Test
    fun testMixLegacyAndNewAPIs6() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            LogSinks.get().custom = object : BaseLogSink(LogLevel.ERROR, LogDomain.ALL) {
                override fun writeLog(level: LogLevel, domain: LogDomain, message: String) {}
            }
            Database.log.custom = object : Logger {
                override fun getLevel() = LogLevel.VERBOSE
                override fun log(level: LogLevel, domain: LogDomain, message: String) {}
            }
        }
    }

    @Test
    fun testMixLegacyAndNewAPIs7() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            Database.log.custom = object : Logger {
                override fun getLevel() = LogLevel.VERBOSE
                override fun log(level: LogLevel, domain: LogDomain, message: String) {}
            }
            LogSinks.get().file = FileLogSink.Builder().setDirectory(scratchDirPath!!).setLevel(LogLevel.ERROR).build()
        }
    }

    @Test
    fun testMixLegacyAndNewAPIs8() {
        Assert.assertThrows(CouchbaseLiteError::class.java) {
            LogSinks.get().file = FileLogSink.Builder().setDirectory(scratchDirPath!!).setLevel(LogLevel.ERROR).build()
            Database.log.custom = object : Logger {
                override fun getLevel() = LogLevel.VERBOSE
                override fun log(level: LogLevel, domain: LogDomain, message: String) {}
            }
        }
    }

    private fun testWithConfiguration(level: LogLevel, config: LogFileConfiguration, task: Runnable) {
        val logger = Database.log
        val consoleLogger = logger.console
        consoleLogger.level = level

        val fileLogger = logger.file
        fileLogger.config = config
        fileLogger.level = level

        task.run()
    }

    private fun write1KBToLog() {
        val message = "11223344556677889900" // ~65 bytes including the line headers
        // 16 * 65 ~= 1024.
        for (i in 0..15) {
            writeAllLogs(message)
        }
    }

    private fun writeAllLogs(message: String) {
        Log.d(LogDomain.DATABASE, message)
        Log.i(LogDomain.DATABASE, message)
        Log.w(LogDomain.DATABASE, message)
        Log.e(LogDomain.DATABASE, message)
    }

    private fun getLogContents(log: File): String {
        val b = ByteArray(log.length().toInt())
        FileInputStream(log).use { Assert.assertEquals(b.size, it.read(b)) }
        return String(b, StandardCharsets.US_ASCII)
    }

    private fun getMostRecent(files: Array<File>?) = files?.maxByOrNull { it.lastModified() }
}