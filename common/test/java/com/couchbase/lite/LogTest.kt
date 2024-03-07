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
package com.couchbase.lite

import com.couchbase.lite.internal.core.C4Constants
import com.couchbase.lite.internal.core.C4Log
import com.couchbase.lite.internal.core.C4TestUtils
import com.couchbase.lite.internal.core.CBLVersion
import com.couchbase.lite.internal.core.impl.NativeC4Log
import com.couchbase.lite.internal.logging.Log
import com.couchbase.lite.utils.KotlinHelpers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.nio.charset.StandardCharsets
import java.util.EnumMap
import java.util.Locale
import java.util.UUID
import kotlin.Array


class LogTest : BaseDbTest() {
    private class SingleLineLogger(private val prefix: String?) : Logger {
        private var level: LogLevel? = null
        private var domain: LogDomain? = null
        var message: String? = null
        override fun getLevel(): LogLevel = LogLevel.DEBUG

        override fun log(level: LogLevel, domain: LogDomain, message: String) {
            // ignore extraneous logs
            if ((prefix != null) && (!message.startsWith(Log.LOG_HEADER + prefix))) {
                return
            }

            this.level = level
            this.domain = domain
            this.message = message
        }

        override fun toString() = level.toString() + "/" + domain + ": " + message
    }

    private class TestCustomLogger(private val prefix: String?) : Logger {
        private val lineCounts: MutableMap<LogLevel, Int> = EnumMap(LogLevel::class.java)
        private val buf = StringBuilder()
        private var level: LogLevel? = null
        val content
            get() = buf.toString()

        override fun log(level: LogLevel, domain: LogDomain, message: String) {
            if ((prefix != null) && (!message.startsWith(Log.LOG_HEADER + prefix))) {
                return
            }
            if (level < this.level) {
                return
            }
            lineCounts[level] = getLineCount(level) + 1
            buf.append(message).append("\n    ")
        }

        override fun getLevel() = level!!

        fun getLineCount(level: LogLevel) = lineCounts[level] ?: 0
    }

    private class TestC4Logger(private val domainFilter: String) : C4Log(NativeC4Log()) {
        var minLevel = 0
            private set

        public override fun logInternal(c4Domain: String, c4Level: Int, message: String) {
            if (domainFilter != c4Domain) {
                return
            }
            if (c4Level < minLevel) {
                minLevel = c4Level
            }
        }

        fun reset() {
            minLevel = C4Constants.LogLevel.NONE
        }
    }

    private class TestConsoleLogger : AbstractConsoleLogger(null) {
        private val buf = StringBuilder()
        val content
            get() = buf.toString()

        override fun doLog(level: LogLevel, domain: LogDomain, message: String) {
            buf.append(message)
        }

        fun clearContent() = buf.clear()
    }

    private var scratchDirPath: String? = null

    private val tempDir: File?
        get() {
            val dir = scratchDirPath
            return dir?.let { File(it) }
        }

    private val logFiles: Array<File>
        get() {
            val files = tempDir?.listFiles()
            assertNotNull(files)
            return files!!
        }

    @Before
    fun setUpLogTest() {
        scratchDirPath = getScratchDirectoryPath(getUniqueName("log-dir"))
    }

    @Test
    fun testConsoleLoggerDomains() {
        val consoleLogger = TestConsoleLogger()

        consoleLogger.setDomains()
        for (level in LogLevel.values()) {
            consoleLogger.level = level
            consoleLogger.log(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogger.log(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogger.log(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogger.log(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogger.log(LogLevel.ERROR, LogDomain.DATABASE, "E")
        }
        assertEquals(consoleLogger.content, "")
        consoleLogger.clearContent()

        consoleLogger.setDomains(LogDomain.NETWORK, LogDomain.QUERY)
        for (level in LogLevel.values()) {
            consoleLogger.level = level
            consoleLogger.log(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogger.log(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogger.log(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogger.log(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogger.log(LogLevel.ERROR, LogDomain.DATABASE, "E")
        }
        assertEquals(consoleLogger.content, "")

        consoleLogger.domains = LogDomain.ALL_DOMAINS
        consoleLogger.level = LogLevel.DEBUG
        consoleLogger.log(LogLevel.DEBUG, LogDomain.NETWORK, "N")
        consoleLogger.log(LogLevel.DEBUG, LogDomain.QUERY, "Q")
        consoleLogger.log(LogLevel.DEBUG, LogDomain.DATABASE, "D")
        assertEquals(consoleLogger.content, "NQD")
    }

    @Test
    fun testConsoleLoggerLevel() {
        val consoleLogger = TestConsoleLogger()

        consoleLogger.setDomains(LogDomain.DATABASE)
        for (level in LogLevel.values()) {
            consoleLogger.level = level
            consoleLogger.log(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogger.log(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogger.log(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogger.log(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogger.log(LogLevel.ERROR, LogDomain.DATABASE, "E")
        }

        assertEquals(consoleLogger.content, "DVIWEVIWEIWEWEE")
    }

    @Test
    fun testFileLoggerDefaults() {
        val config = LogFileConfiguration("up/down")
        assertEquals(Defaults.LogFile.MAX_SIZE, config.maxSize)
        assertEquals(Defaults.LogFile.MAX_ROTATE_COUNT, config.maxRotateCount)
        assertEquals(Defaults.LogFile.USE_PLAINTEXT, config.usesPlaintext())
    }

    @Test
    fun testFileLoggingLevels() {
        testWithConfiguration(
            LogLevel.DEBUG,
            LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true).setMaxRotateCount(0)
        ) {
            for (level in LogLevel.values()) {
                Database.log.file.level = level
                Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG")
                Log.i(LogDomain.DATABASE, "$$\$TEST INFO")
                Log.w(LogDomain.DATABASE, "$$\$TEST WARNING")
                Log.e(LogDomain.DATABASE, "$$\$TEST ERROR")
            }

            for (log in logFiles) {
                var lineCount = 0
                BufferedReader(FileReader(log)).use {
                    var l: String?
                    while (it.readLine().also { s -> l = s } != null) {
                        if (l?.contains("$$\$TEST") == true) {
                            lineCount++
                        }
                    }
                }

                val logPath = log.canonicalPath
                when {
                    logPath.contains("verbose") -> assertEquals(0, lineCount)
                    logPath.contains("info") -> assertEquals(3, lineCount)
                    logPath.contains("warning") -> assertEquals(4, lineCount)
                    logPath.contains("error") -> assertEquals(5, lineCount)
                }
            }
        }
    }

    @Test
    fun testFileLoggingDefaultBinaryFormat() {
        testWithConfiguration(LogLevel.INFO, LogFileConfiguration(scratchDirPath!!)) {
            Log.i(LogDomain.DATABASE, "TEST INFO")

            val files = logFiles
            assertTrue(files.isNotEmpty())

            val lastModifiedFile = getMostRecent(files)
            assertNotNull(lastModifiedFile)

            val bytes = ByteArray(4)
            FileInputStream(lastModifiedFile!!).use { inStr -> assertEquals(4, inStr.read(bytes)) }
            assertEquals(bytes[0], 0xCF.toByte())
            assertEquals(bytes[1], 0xB2.toByte())
            assertEquals(bytes[2], 0xAB.toByte())
            assertEquals(bytes[3], 0x1B.toByte())
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

            assertNotNull(files)
            assertEquals(1, files?.size ?: 0)

            val file = getMostRecent(files)
            assertNotNull(file)
            assertTrue(getLogContents(file!!).contains(uuidString))
        }
    }

    @Test
    fun testFileLoggingLogFilename() {
        testWithConfiguration(LogLevel.DEBUG, LogFileConfiguration(scratchDirPath!!)) {
            Log.e(LogDomain.DATABASE, "$$\$TEST MESSAGE")

            val files = logFiles
            assertTrue(files.size >= 4)

            val rex = Regex("cbl_(debug|verbose|info|warning|error)_\\d+\\.cbllog")
            for (file in files) {
                assertTrue(file.name.matches(rex))
            }
        }
    }

    @Test
    fun testFileLoggingMaxSize() {
        val config = LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true).setMaxSize(1024)
        testWithConfiguration(LogLevel.DEBUG, config) {
            // this should create two files, as the 1KB logs + extra header
            writeOneKiloByteOfLog()
            assertEquals(((config.maxRotateCount + 1) * 5), logFiles.size)
        }
    }

    @Test
    fun testFileLoggingDisableLogging() {
        val uuidString = UUID.randomUUID().toString()

        testWithConfiguration(LogLevel.NONE, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            writeAllLogs(uuidString)
            for (log in logFiles) {
                assertFalse(getLogContents(log).contains(uuidString))
            }
        }
    }

    @Test
    fun testFileLoggingReEnableLogging() {
        val uuidString = UUID.randomUUID().toString()

        testWithConfiguration(LogLevel.NONE, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            writeAllLogs(uuidString)

            for (log in logFiles) {
                assertFalse(getLogContents(log).contains(uuidString))
            }

            Database.log.file.level = LogLevel.INFO
            writeAllLogs(uuidString)

            val logFiles = tempDir!!.listFiles()
            assertNotNull(tempDir!!.listFiles())
            for (log in logFiles!!) {
                val fn = log.name.lowercase(Locale.getDefault())
                if (fn.startsWith("cbl_debug_") || fn.startsWith("cbl_verbose_")) {
                    assertFalse(getLogContents(log).contains(uuidString))
                } else {
                    assertTrue(getLogContents(log).contains(uuidString))
                }
            }
        }
    }

    @Test
    fun testFileLoggingHeader() {
        testWithConfiguration(LogLevel.VERBOSE, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            writeOneKiloByteOfLog()
            for (log in logFiles) {
                var logLine: String
                BufferedReader(FileReader(log)).use {
                    logLine = it.readLine()
                    logLine = it.readLine() // skip the LiteCore log line...
                }
                assertNotNull(logLine)
                assertTrue(logLine.contains("CouchbaseLite $PRODUCT"))
                assertTrue(logLine.contains("Core/"))
                assertTrue(logLine.contains(CBLVersion.getSysInfo()))
            }
        }
    }

    @Test
    fun testBasicLogFormatting() {
        val nl = System.lineSeparator()

        val logger = SingleLineLogger("$$\$TEST")
        Database.log.custom = logger

        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG")
        assertEquals(Log.LOG_HEADER + "$$\$TEST DEBUG", logger.message)

        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG", Exception("whoops"))
        var msg = logger.message
        assertNotNull(msg)
        assertTrue(
            msg!!.startsWith(
                Log.LOG_HEADER + "$$\$TEST DEBUG" + nl + "java.lang.Exception: whoops" + System.lineSeparator()
            )
        )

        // test formatting, including argument ordering
        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG %2\$s %1\$d %3$.2f", 1, "arg", 3.0f)
        assertEquals(Log.LOG_HEADER + "$$\$TEST DEBUG arg 1 3.00", logger.message)

        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG %2\$s %1\$d %3$.2f", Exception("whoops"), 1, "arg", 3.0f)
        msg = logger.message
        assertNotNull(msg)
        assertTrue(
            msg!!.startsWith(Log.LOG_HEADER + "$$\$TEST DEBUG arg 1 3.00" + nl + "java.lang.Exception: whoops" + nl)
        )
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
                    assertTrue(getLogContents(log).contains(uuid))
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
            Log.v(LogDomain.DATABASE, message, error, uuid2)
            Log.i(LogDomain.DATABASE, message, error, uuid2)
            Log.w(LogDomain.DATABASE, message, error, uuid2)
            Log.e(LogDomain.DATABASE, message, error, uuid2)

            for (log in logFiles) {
                if (!log.name.contains("verbose")) {
                    val content = getLogContents(log)
                    assertTrue(content.contains(uuid1))
                    assertTrue(content.contains(uuid2))
                }
            }
        }
    }

    @Test
    fun testLogFileConfigurationConstructors() {
        val rotateCount = 4
        val maxSize = 2048L
        val usePlainText = true

        assertThrows(IllegalArgumentException::class.java) {
            KotlinHelpers.createLogFileConfigWithNullConfig()
        }

        assertThrows(IllegalArgumentException::class.java) {
            KotlinHelpers.createLogFileConfigWithNullDir()
        }

        val config = LogFileConfiguration(scratchDirPath!!)
            .setMaxRotateCount(rotateCount)
            .setMaxSize(maxSize)
            .setUsePlaintext(usePlainText)

        assertEquals(config.maxRotateCount, rotateCount)
        assertEquals(config.maxSize, maxSize)
        assertEquals(config.usesPlaintext(), usePlainText)
        assertEquals(config.directory, scratchDirPath)

        val tempDir2 = getScratchDirectoryPath(getUniqueName("logtest2"))
        val newConfig = LogFileConfiguration(tempDir2, config)
        assertEquals(newConfig.maxRotateCount, rotateCount)
        assertEquals(newConfig.maxSize, maxSize)
        assertEquals(newConfig.usesPlaintext(), usePlainText)
        assertEquals(newConfig.directory, tempDir2)
    }

    @Test
    fun testEditReadOnlyLogFileConfiguration() {
        testWithConfiguration(LogLevel.DEBUG, LogFileConfiguration(scratchDirPath!!)) {
            assertThrows(CouchbaseLiteError::class.java) { Database.log.file.config!!.maxSize = 1024 }
            assertThrows(CouchbaseLiteError::class.java) { Database.log.file.config!!.maxRotateCount = 3 }
            assertThrows(CouchbaseLiteError::class.java) { Database.log.file.config!!.setUsePlaintext(true) }
        }
    }

    @Test
    fun testSetNewLogFileConfiguration() {
        val config = LogFileConfiguration(scratchDirPath!!)
        val fileLogger = Database.log.file
        fileLogger.config = config
        assertEquals(fileLogger.config, config)
        fileLogger.config = null
        assertNull(fileLogger.config)
        fileLogger.config = config
        assertEquals(fileLogger.config, config)
        fileLogger.config = LogFileConfiguration("$scratchDirPath/foo")
        assertEquals(fileLogger.config, LogFileConfiguration("$scratchDirPath/foo"))
    }

    @Test
    fun testLogStandardErrorWithFormatting() {
        val nl = System.lineSeparator()

        var prevStdErr: MutableMap<String, String>? = null
        try {
            prevStdErr = Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG %2\$s %1\$d %3$.2f"))

            val logger = SingleLineLogger("$$\$TEST DEBUG")
            Database.log.custom = logger

            // After initLogging, log level is WARN
            Log.w(LogDomain.DATABASE, "FOO", Exception("whoops"), 1, "arg", 3.0f)

            val msg = logger.message
            assertNotNull(msg)
            assertTrue(
                msg!!.startsWith(Log.LOG_HEADER + "$$\$TEST DEBUG arg 1 3.00" + nl + "java.lang.Exception: whoops" + nl)
            )
        } finally {
            Log.setStandardErrorMessages(prevStdErr!!)
        }
    }

    @Test
    fun testLookupStandardMessage() {
        var prevStdErr: MutableMap<String, String>? = null
        try {
            prevStdErr = Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG"))
            assertEquals("$$\$TEST DEBUG", Log.lookupStandardMessage("FOO"))
        } finally {
            Log.setStandardErrorMessages(prevStdErr!!)
        }
    }

    @Test
    fun testFormatStandardMessage() {
        var prevStdErr: MutableMap<String, String>? = null
        try {
            prevStdErr = Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG %2\$s %1\$d %3$.2f"))
            assertEquals("$$\$TEST DEBUG arg 1 3.00", Log.formatStandardMessage("FOO", 1, "arg", 3.0f))
        } finally {
            Log.setStandardErrorMessages(prevStdErr!!)
        }
    }

    // brittle:  will break when the wording of the error message is changed
    @Test
    fun testStandardCBLException() {
        var prevStdErr: MutableMap<String, String>? = null
        try {
            prevStdErr = Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG"))
            val msg = CouchbaseLiteException("FOO", CBLError.Domain.CBLITE, CBLError.Code.UNIMPLEMENTED).message
            assertNotNull(msg)
            assertTrue(msg.contains("$$\$TEST DEBUG"))
        } finally {
            Log.setStandardErrorMessages(prevStdErr!!)
        }
    }

    @Test
    fun testNonStandardCBLException() {
        var prevStdErr: MutableMap<String, String>? = null
        try {
            prevStdErr = Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG"))
            val msg = CouchbaseLiteException("bork", CBLError.Domain.CBLITE, CBLError.Code.UNIMPLEMENTED).message
            assertNotNull(msg)
            assertTrue(msg.contains("bork"))
        } finally {
            Log.setStandardErrorMessages(prevStdErr!!)
        }
    }

    @Ignore("Need a way to coax LiteCore into logging a non-ascii string")
    @Test
    fun testNonASCII() {
        val hebrew = "מזג האוויר נחמד היום" // The weather is nice today.

        val customLogger = TestCustomLogger(null)
        Database.log.custom = customLogger

        val doc = MutableDocument()
        doc.setString("hebrew", hebrew)
        saveDocInCollection(doc)

        // This used to cause LiteCore to log the content of the document...
        QueryBuilder.select(SelectResult.all()).from(DataSource.collection(testCollection)).execute().use {
            assertEquals(1, it.allResults().size)
        }

        assertTrue(customLogger.content.contains("[{\"hebrew\":\"$hebrew\"}]"))
    }

    // Verify that we can set the level for log domains that the platform doesn't recognize.
    // !!! I don't think this test is actually testing anything.
    @Test
    fun testInternalLogging() {
        val c4Domain = "foo"
        val testC4Logger = TestC4Logger(c4Domain)
        val oldLogger = C4Log.LOGGER.getAndSet(testC4Logger)
        try {
            testC4Logger.reset()
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.collection(testCollection))
                .execute()
            val actualMinLevel = testC4Logger.minLevel
            assertTrue(actualMinLevel >= C4TestUtils.getLogLevel(c4Domain))

            testC4Logger.reset()
            testC4Logger.setLevels(actualMinLevel + 1, c4Domain)
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.collection(testCollection))
                .execute()
            // If level > maxLevel, should be no logs
            assertEquals(C4Constants.LogLevel.NONE, testC4Logger.minLevel)

            testC4Logger.reset()
            testC4Logger.setLevels(C4TestUtils.getLogLevel(c4Domain), c4Domain)
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.collection(testCollection))
                .execute()
            assertEquals(actualMinLevel, testC4Logger.minLevel)
        } finally {
            testC4Logger.setLevels(C4TestUtils.getLogLevel(c4Domain), c4Domain)
            C4Log.LOGGER.set(oldLogger)
        }
    }

    private fun testWithConfiguration(level: LogLevel, config: LogFileConfiguration, task: Runnable) {
        val logger = Database.log
        val consoleLogger = logger.console
        val consoleLogLevel = consoleLogger.level
        consoleLogger.level = level

        val fileLogger = logger.file
        val fileLogLevel = fileLogger.level
        fileLogger.config = config
        fileLogger.level = level

        try {
            task.run()
        } finally {
            consoleLogger.level = consoleLogLevel
            fileLogger.level = fileLogLevel
        }
    }

    private fun writeOneKiloByteOfLog() {
        val message = "11223344556677889900" // ~43 bytes
        // 24 * 43 = 1032
        for (i in 0..23) {
            writeAllLogs(message)
        }
    }

    private fun writeAllLogs(message: String) {
        Log.d(LogDomain.DATABASE, message)
        Log.v(LogDomain.DATABASE, message, null)
        Log.i(LogDomain.DATABASE, message)
        Log.w(LogDomain.DATABASE, message)
        Log.e(LogDomain.DATABASE, message)
    }

    private fun getLogContents(log: File): String {
        val b = ByteArray(log.length().toInt())
        val fileInputStream = FileInputStream(log)
        assertEquals(b.size, fileInputStream.read(b))
        return String(b, StandardCharsets.US_ASCII)
    }

    private fun getMostRecent(files: Array<File>?) = files?.maxByOrNull { it.lastModified() }
}