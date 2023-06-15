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
import com.couchbase.lite.internal.support.Log
import com.couchbase.lite.utils.KotlinHelpers
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.Array


class LogTest : BaseDbTest() {
    private class SingleLineLogger(private val prefix: String?) : Logger {
        private var level: LogLevel? = null
        private var domain: LogDomain? = null
        var message: String? = null
        override fun getLevel(): LogLevel = LogLevel.DEBUG

        override fun log(level: LogLevel, domain: LogDomain, message: String) {
            // ignore extraneous logs
            if ((prefix != null) && (!message.startsWith(Log.LOG_HEADER + prefix))) { return }

            this.level = level
            this.domain = domain
            this.message = message
        }

        override fun toString() = level.toString() + "/" + domain + ": " + message
    }

    private class LogTestLogger constructor(private val prefix: String?) : Logger {
        private val lineCounts: MutableMap<LogLevel, Int> = EnumMap(LogLevel::class.java)
        private val content = StringBuilder()
        private var level: LogLevel? = null
        val lineCount: Int
            get() {
                var total = 0
                for (level in LogLevel.values()) { total += getLineCount(level) }
                return total
            }

        override fun log(level: LogLevel, domain: LogDomain, message: String) {
            if ((prefix != null) && (!message.startsWith(Log.LOG_HEADER + prefix))) { return }
            if (level < this.level) { return }
            lineCounts[level] = getLineCount(level) + 1
            content.append(message)
        }

        override fun getLevel() = level!!

        fun setLevel(level: LogLevel?) { this.level = level }

        fun getLineCount(level: LogLevel) = lineCounts[level] ?: 0

        fun getContent() = content.toString()
    }

    private class TestLogger(private val domainFilter: String) : C4Log(NativeC4Log()) {
        var minLevel = 0
            private set

        public override fun logInternal(c4Domain: String, c4Level: Int, message: String) {
            if (domainFilter != c4Domain) { return }
            if (c4Level < minLevel) { minLevel = c4Level }
        }

        fun reset() { minLevel = C4Constants.LogLevel.NONE }
    }

    companion object {
        @JvmStatic
        @AfterClass
        fun tearDownLogTestClass() = Database.log.reset()
    }

    private var scratchDirPath: String? = null

    private val logFiles: Array<File>
        get() {
            val files = tempDir?.listFiles()
            assertNotNull(files)
            return files!!
        }

    private val tempDir: File?
        get() {
            val dir = scratchDirPath
            return dir?.let { File(it) }
        }

    @Before
    fun setUpLogTest() {
        scratchDirPath = getScratchDirectoryPath(getUniqueName("log-dir"))
        Database.log.reset()
    }

    @After
    fun tearDownLogTest() {
        Database.log.custom = null
        reloadStandardErrorMessages()
    }

    @Test
    fun testFileLoggerDefaults() {
        val config = LogFileConfiguration("up/down")
        assertEquals(Defaults.LogFile.MAX_SIZE, config.maxSize)
        assertEquals(Defaults.LogFile.MAX_ROTATE_COUNT, config.maxRotateCount)
        assertEquals(Defaults.LogFile.USE_PLAIN_TEXT, config.usesPlaintext())
    }

    @Test
    fun testCustomLoggingLevels() {
        val customLogger = LogTestLogger("$$\$TEST ")
        Database.log.custom = customLogger
        for (level in LogLevel.values()) {
            customLogger.setLevel(level)
            Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG")
            Log.i(LogDomain.DATABASE, "$$\$TEST INFO")
            Log.w(LogDomain.DATABASE, "$$\$TEST WARNING")
            Log.e(LogDomain.DATABASE, "$$\$TEST ERROR")
        }

        assertEquals(0, customLogger.getLineCount(LogLevel.VERBOSE).toLong())
        assertEquals(3, customLogger.getLineCount(LogLevel.INFO).toLong())
        assertEquals(4, customLogger.getLineCount(LogLevel.WARNING).toLong())
        assertEquals(5, customLogger.getLineCount(LogLevel.ERROR).toLong())
    }

    @Test
    fun testEnableAndDisableCustomLogging() {
        val customLogger = LogTestLogger("$$\$TEST ")
        Database.log.custom = customLogger

        customLogger.setLevel(LogLevel.NONE)
        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG")
        Log.i(LogDomain.DATABASE, "$$\$TEST INFO")
        Log.w(LogDomain.DATABASE, "$$\$TEST WARNING")
        Log.e(LogDomain.DATABASE, "$$\$TEST ERROR")
        assertEquals(0, customLogger.lineCount.toLong())

        customLogger.setLevel(LogLevel.VERBOSE)
        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG")
        Log.i(LogDomain.DATABASE, "$$\$TEST INFO")
        Log.w(LogDomain.DATABASE, "$$\$TEST WARNING")
        Log.e(LogDomain.DATABASE, "$$\$TEST ERROR")
        assertEquals(3, customLogger.lineCount.toLong())
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
                        if (l?.contains("$$\$TEST") == true) { lineCount++ }
                    }
                }

                val logPath = log.canonicalPath
                if (logPath.contains("verbose")) {
                    assertEquals(0, lineCount.toLong())
                } else if (logPath.contains("info")) {
                    assertEquals(3, lineCount.toLong())
                } else if (logPath.contains("warning")) {
                    assertEquals(4, lineCount.toLong())
                } else if (logPath.contains("error")) {
                    assertEquals(5, lineCount.toLong())
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

            val bytes = ByteArray(4)
            val `is`: InputStream = FileInputStream(lastModifiedFile)
            assertEquals(4, `is`.read(bytes).toLong())

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
            assertEquals(1, files?.size?.toLong() ?: 0)

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
            for (file in files) { assertTrue(file.name.matches(rex)) }
        }
    }

    @Test
    fun testFileLoggingMaxSize() {
        val config = LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true).setMaxSize(1024)
        testWithConfiguration(LogLevel.DEBUG, config) {
            // this should create two files, as the 1KB logs + extra header
            writeOneKiloByteOfLog()
            assertEquals(((config.maxRotateCount + 1) * 5).toLong(), logFiles.size.toLong())
        }
    }

    @Test
    fun testFileLoggingDisableLogging() {
        val uuidString = UUID.randomUUID().toString()

        testWithConfiguration(LogLevel.NONE, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            writeAllLogs(uuidString)
            for (log in logFiles) { assertFalse(getLogContents(log).contains(uuidString)) }
        }
    }

    @Test
    fun testFileLoggingReEnableLogging() {
        val uuidString = UUID.randomUUID().toString()

        testWithConfiguration(LogLevel.NONE, LogFileConfiguration(scratchDirPath!!).setUsePlaintext(true)) {
            writeAllLogs(uuidString)

            for (log in logFiles) { assertFalse(getLogContents(log).contains(uuidString)) }

            Database.log.file.level = LogLevel.INFO
            writeAllLogs(uuidString)

            val logFiles = tempDir!!.listFiles()
            assertNotNull(tempDir!!.listFiles())
            for (log in logFiles!!) {
                val fn = log.name.lowercase(Locale.getDefault())
                if (fn.startsWith("cbl_debug_") || fn.startsWith("cbl_verbose_")) {
                    assertFalse(getLogContents(log).contains(uuidString))
                }
                else {
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
                var firstLine: String
                BufferedReader(FileReader(log)).use { firstLine = it.readLine() }
                assertNotNull(firstLine)
                assertTrue(firstLine.contains("CouchbaseLite $PRODUCT"))
                assertTrue(firstLine.contains("Core/"))
                assertTrue(firstLine.contains(CBLVersion.getSysInfo()))
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
                if (!log.name.contains("verbose")) { assertTrue(getLogContents(log).contains(uuid)) }
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
            Log.testLog(LogLevel.VERBOSE, LogDomain.DATABASE, message, error, uuid2)
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
        val maxSize: Long = 2048
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

        assertEquals(config.maxRotateCount.toLong(), rotateCount.toLong())
        assertEquals(config.maxSize, maxSize)
        assertEquals(config.usesPlaintext(), usePlainText)
        assertEquals(config.directory, scratchDirPath)

        val tempDir2 = getScratchDirectoryPath(getUniqueName("logtest2"))
        val newConfig = LogFileConfiguration(tempDir2, config)
        assertEquals(newConfig.maxRotateCount.toLong(), rotateCount.toLong())
        assertEquals(newConfig.maxSize, maxSize)
        assertEquals(newConfig.usesPlaintext(), usePlainText)
        assertEquals(newConfig.directory, tempDir2)
    }

    @Test
    fun testEditReadOnlyLogFileConfiguration() {
        testWithConfiguration(LogLevel.DEBUG, LogFileConfiguration(scratchDirPath!!)) {
            assertThrows(IllegalStateException::class.java) { Database.log.file.config!!.maxSize = 1024 }
            assertThrows(IllegalStateException::class.java) { Database.log.file.config!!.maxRotateCount = 3 }
            assertThrows(IllegalStateException::class.java) { Database.log.file.config!!.setUsePlaintext(true) }
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
    fun testNonASCII() {
        val hebrew = "מזג האוויר נחמד היום" // The weather is nice today.

        val customLogger = LogTestLogger(null)
        customLogger.setLevel(LogLevel.VERBOSE)

        Database.log.custom = customLogger

        // hack: The console logger sets the C4 callback level
        Database.log.console.level = LogLevel.VERBOSE

        val doc = MutableDocument()
        doc.setString("hebrew", hebrew)
        saveDocInCollection(doc)

        val query: Query = QueryBuilder.select(SelectResult.all()).from(DataSource.collection(testCollection))
        query.execute().use { rs -> assertEquals(rs.allResults().size.toLong(), 1) }

        assertTrue(customLogger.getContent().contains("[{\"hebrew\":\"$hebrew\"}]"))
    }

    @Test
    fun testLogStandardErrorWithFormatting() {
        val nl = System.lineSeparator()

        val stdErr: MutableMap<String, String> = HashMap()
        stdErr["FOO"] = "$$\$TEST DEBUG %2\$s %1\$d %3$.2f"

        Log.initLogging(stdErr)
        val logger = SingleLineLogger("$$\$TEST DEBUG")
        Database.log.custom = logger

        // After initLogging, log level is WARN
        Log.w(LogDomain.DATABASE, "FOO", Exception("whoops"), 1, "arg", 3.0f)

        val msg = logger.message
        assertNotNull(msg)
        assertTrue(
            msg!!.startsWith(Log.LOG_HEADER + "$$\$TEST DEBUG arg 1 3.00" + nl + "java.lang.Exception: whoops" + nl)
        )
    }

    @Test
    fun testLookupStandardMessage() {
        val stdErr: MutableMap<String, String> = HashMap()
        stdErr["FOO"] = "$$\$TEST DEBUG"
        Log.initLogging(stdErr)
        assertEquals("$$\$TEST DEBUG", Log.lookupStandardMessage("FOO"))
    }

    @Test
    fun testFormatStandardMessage() {
        val stdErr: MutableMap<String, String> = HashMap()
        stdErr["FOO"] = "$$\$TEST DEBUG %2\$s %1\$d %3$.2f"
        Log.initLogging(stdErr)
        assertEquals("$$\$TEST DEBUG arg 1 3.00", Log.formatStandardMessage("FOO", 1, "arg", 3.0f))
    }

    // brittle:  will break when the wording of the error message is changed
    @Test
    fun testStandardCBLException() {
        Log.initLogging(mapOf("FOO" to "$$\$TEST DEBUG"))
        val msg = CouchbaseLiteException("FOO", CBLError.Domain.CBLITE, CBLError.Code.UNIMPLEMENTED).message
        assertNotNull(msg)
        assertTrue(msg.startsWith("$$\$TEST DEBUG"))
    }

    @Test
    fun testNonStandardCBLException() {
        Log.initLogging(mapOf("FOO" to "$$\$TEST DEBUG"))
        val msg = CouchbaseLiteException("bork", CBLError.Domain.CBLITE, CBLError.Code.UNIMPLEMENTED).message
        assertNotNull(msg)
        assertTrue(msg.startsWith("bork"))
    }

    // Verify that we can set the level for log domains that the platform doesn't recognize.
    // !!! I don't think this test is acutally testing anything.
    @Test
    fun testInternalLogging() {
        val c4Domain = "foo"
        val testLogger = TestLogger(c4Domain)
        val oldLogger = C4Log.LOGGER.getAndSet(testLogger)
        try {
            testLogger.reset()
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.collection(testCollection))
                .execute()
            val actualMinLevel = testLogger.minLevel
            assertTrue(actualMinLevel >= C4TestUtils.getLogLevel(c4Domain))

            testLogger.reset()
            testLogger.setLevels(actualMinLevel + 1, c4Domain)
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.collection(testCollection))
                .execute()
            // If level > maxLevel, should be no logs
            assertEquals(C4Constants.LogLevel.NONE.toLong(), testLogger.minLevel.toLong())

            testLogger.reset()
            testLogger.setLevels(C4TestUtils.getLogLevel(c4Domain), c4Domain)
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.collection(testCollection))
                .execute()
            assertEquals(actualMinLevel.toLong(), testLogger.minLevel.toLong())
        } finally {
            testLogger.setLevels(C4TestUtils.getLogLevel(c4Domain), c4Domain)
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
        for (i in 0..23) {  writeAllLogs(message) }
    }

    private fun writeAllLogs(message: String) {
        Log.d(LogDomain.DATABASE, message)
        Log.testLog(LogLevel.VERBOSE, LogDomain.DATABASE, message)
        Log.i(LogDomain.DATABASE, message)
        Log.w(LogDomain.DATABASE, message)
        Log.e(LogDomain.DATABASE, message)
    }

    private fun getLogContents(log: File): String {
        val b = ByteArray(log.length().toInt())
        val fileInputStream = FileInputStream(log)
        assertEquals(b.size.toLong(), fileInputStream.read(b).toLong())
        return String(b, StandardCharsets.US_ASCII)
    }

    private fun getMostRecent(files: Array<File>?) = files?.maxByOrNull { it.lastModified() }
}