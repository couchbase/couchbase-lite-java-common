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

import com.couchbase.lite.internal.core.C4Constants
import com.couchbase.lite.internal.core.C4Log
import com.couchbase.lite.internal.core.C4TestUtils
import com.couchbase.lite.internal.core.CBLVersion
import com.couchbase.lite.internal.logging.Log
import com.couchbase.lite.internal.logging.LoggersImpl
import com.couchbase.lite.internal.utils.Report
import com.couchbase.lite.logging.BaseLogger
import com.couchbase.lite.logging.Loggers
import com.couchbase.lite.utils.KotlinHelpers
import org.junit.After
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
import java.util.EnumSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.Array


private class SingleLineLogger(private val prefix: String?) : Logger {
    private var level: LogLevel? = null
    private var domain: LogDomain? = null
    private var message: String? = null
    private var latch = CountDownLatch(1)

    override fun getLevel(): LogLevel = LogLevel.DEBUG

    override fun log(level: LogLevel, domain: LogDomain, message: String) {
        // ignore extraneous logs
        if ((prefix != null) && (!message.startsWith(Log.LOG_HEADER + prefix))) {
            return
        }

        this.level = level
        this.domain = domain
        this.message = message
        latch.countDown()
    }

    override fun toString() = level.toString() + "/" + domain + ": " + message

    fun awaitMessage(): String? {
        latch.await(1, TimeUnit.SECONDS)
        latch = CountDownLatch(1)
        return message
    }
}

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

private class TestC4Logger(private val domainFilter: String) : C4Log.NativeImpl {
    var minLevel = 0
        private set

    fun reset() {
        minLevel = C4Constants.LogLevel.NONE
    }

    override fun nLog(domain: String, level: Int, message: String) {
        if (domainFilter != domain) {
            return
        }
        if (level < minLevel) {
            minLevel = level
        }
    }

    override fun nSetLevel(domain: String, level: Int) = Unit
    override fun nSetCallbackLevel(level: Int) = Unit
    override fun nSetBinaryFileLevel(level: Int) = Unit
    override fun nWriteToBinaryFile(
        path: String?,
        level: Int,
        maxRotateCount: Int,
        maxSize: Long,
        usePlaintext: Boolean,
        header: String?
    ) = Unit
}

class LogTest : BaseDbTest() {
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

    @After
    fun tearDownLogTest() = LoggersImpl.initLogging()

    @Test
    fun testC4LogLevel() {
        val mark = "$$$ ${UUID.randomUUID()}"

        val c4Log = LoggersImpl.getLoggers()!!.c4Log
        c4Log.initFileLogger(scratchDirPath, LogLevel.DEBUG, 10, 1024, true, "$$$ TEST")

        for (level in LogLevel.values()) {
            if (level == LogLevel.NONE) {
                continue
            }
            c4Log.setLogLevel(LogDomain.DATABASE, level)

            c4Log.logToCore(LogDomain.DATABASE, LogLevel.DEBUG, mark)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.VERBOSE, mark)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.INFO, mark)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.WARNING, mark)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.ERROR, mark)
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
                logPath.contains("error") -> assertEquals(5, lineCount)
                logPath.contains("warning") -> assertEquals(4, lineCount)
                logPath.contains("info") -> assertEquals(3, lineCount)
                logPath.contains("verbose") -> assertEquals(2, lineCount)
                logPath.contains("debug") -> assertEquals(1, lineCount)
            }
        }
    }

    @Test
    fun testC4MaxFileSize() {
        val c4Log = LoggersImpl.getLoggers()!!.c4Log
        c4Log.initFileLogger(scratchDirPath, LogLevel.DEBUG, 10, 1024, true, "$$$$ TEST")
        c4Log.setLogLevel(LogDomain.DATABASE, LogLevel.DEBUG)

        val message = "11223344556677889900" // ~43 bytes
        // 24 * 43 = 1032
        // ... should cause each level to roll over once
        for (i in 0..23) {
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.DEBUG, message)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.VERBOSE, message)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.INFO, message)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.WARNING, message)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.ERROR, message)
        }

        assertEquals((2 * 5), logFiles.size)
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
        assertEquals("", consoleLogger.content)
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
        assertEquals("", consoleLogger.content)

        consoleLogger.domains = LogDomain.ALL_DOMAINS
        consoleLogger.level = LogLevel.DEBUG
        consoleLogger.log(LogLevel.DEBUG, LogDomain.NETWORK, "N")
        consoleLogger.log(LogLevel.DEBUG, LogDomain.QUERY, "Q")
        consoleLogger.log(LogLevel.DEBUG, LogDomain.DATABASE, "D")
        assertEquals("NQD", consoleLogger.content)
    }

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

        assertEquals("DVIWEVIWEIWEWEE", consoleLogger.content)
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
                    logPath.contains("error") -> assertEquals(5, lineCount)
                    logPath.contains("warning") -> assertEquals(4, lineCount)
                    logPath.contains("info") -> assertEquals(3, lineCount)
                    logPath.contains("debug") -> assertEquals(1, lineCount)
                    logPath.contains("verbose") -> assertEquals(0, lineCount)
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
            assertEquals(0xCF.toByte(), bytes[0])
            assertEquals(0xB2.toByte(), bytes[1])
            assertEquals(0xAB.toByte(), bytes[2])
            assertEquals(0x1B.toByte(), bytes[3])
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
            // This should create two files for each of the 5 levels except verbose (debug, info, warning, error):
            // 1k of logs plus the headers. There should be only one file at the verbose level (just the headers)
            write1KBToLog()
            assertEquals((4 * 2) + 1, logFiles.size)
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
            write1KBToLog()
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
        assertEquals(Log.LOG_HEADER + "$$\$TEST DEBUG", logger.awaitMessage())

        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG", Exception("whoops"))
        var msg = logger.awaitMessage()
        assertNotNull(msg)
        assertTrue(
            msg!!.startsWith(
                Log.LOG_HEADER + "$$\$TEST DEBUG" + nl + "java.lang.Exception: whoops" + System.lineSeparator()
            )
        )

        // test formatting, including argument ordering
        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG %2\$s %1\$d %3$.2f", 1, "arg", 3.0f)
        assertEquals(Log.LOG_HEADER + "$$\$TEST DEBUG arg 1 3.00", logger.awaitMessage())

        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG %2\$s %1\$d %3$.2f", Exception("whoops"), 1, "arg", 3.0f)
        msg = logger.awaitMessage()
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

        assertEquals(rotateCount, config.maxRotateCount)
        assertEquals(maxSize, config.maxSize)
        assertEquals(usePlainText, config.usesPlaintext())
        assertEquals(scratchDirPath, config.directory)

        val tempDir2 = getScratchDirectoryPath(getUniqueName("logtest2"))
        val newConfig = LogFileConfiguration(tempDir2, config)
        assertEquals(rotateCount, newConfig.maxRotateCount)
        assertEquals(maxSize, newConfig.maxSize)
        assertEquals(usePlainText, newConfig.usesPlaintext())
        assertEquals(tempDir2, newConfig.directory)
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
        assertEquals(config, fileLogger.config)
        fileLogger.config = null
        assertNull(fileLogger.config)
        fileLogger.config = config
        assertEquals(config, fileLogger.config)
        fileLogger.config = LogFileConfiguration("$scratchDirPath/foo")
        assertEquals(LogFileConfiguration("$scratchDirPath/foo"), fileLogger.config)
    }

    @Test
    fun testLogStandardErrorWithFormatting() {
        val nl = System.lineSeparator()

        Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG %2\$s %1\$d %3$.2f"))

        val logger = SingleLineLogger("$$\$TEST DEBUG")
        Database.log.custom = logger

        // After initLogging, log level is WARN
        Log.w(LogDomain.DATABASE, "FOO", Exception("whoops"), 1, "arg", 3.0f)

        val msg = logger.awaitMessage()
        assertNotNull(msg)
        assertTrue(
            msg!!.startsWith(Log.LOG_HEADER + "$$\$TEST DEBUG arg 1 3.00" + nl + "java.lang.Exception: whoops" + nl)
        )
    }

    @Test
    fun testLookupStandardMessage() {
        Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG"))
        assertEquals("$$\$TEST DEBUG", Log.lookupStandardMessage("FOO"))
    }

    @Test
    fun testFormatStandardMessage() {
        Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG %2\$s %1\$d %3$.2f"))
        assertEquals("$$\$TEST DEBUG arg 1 3.00", Log.formatStandardMessage("FOO", 1, "arg", 3.0f))
    }

    // brittle:  will break when the wording of the error message is changed
    @Test
    fun testStandardCBLException() {
        Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG"))
        val msg = CouchbaseLiteException("FOO", CBLError.Domain.CBLITE, CBLError.Code.UNIMPLEMENTED).message
        assertNotNull(msg)
        assertTrue(msg.contains("$$\$TEST DEBUG"))
    }

    @Test
    fun testNonStandardCBLException() {
        Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG"))
        val msg = CouchbaseLiteException("bork", CBLError.Domain.CBLITE, CBLError.Code.UNIMPLEMENTED).message
        assertNotNull(msg)
        assertTrue(msg.contains("bork"))
    }

    // Verify that we can handle non-ascii content.
    @Ignore("Need a way to coax LiteCore into logging a non-ascii string")
    @Test
    fun testNonASCII() {
        val customLogger = object : BaseLogger(LogLevel.DEBUG) {
            var text: String = ""

            override fun writeLog(level: LogLevel, domain: LogDomain, message: String) {
                text += "\n $message"
            }
        }

        Loggers.get().customLogger = customLogger

        val hebrew = "מזג האוויר נחמד היום" // The weather is nice today.

        val doc = MutableDocument()
        doc.setString("hebrew", hebrew)
        saveDocInCollection(doc)

        // This used to cause LiteCOre to log the query. It doesn't anymore.
        QueryBuilder.select(SelectResult.all()).from(DataSource.collection(testCollection)).execute().use { rs ->
            assertEquals(rs.allResults().size.toLong(), 1)
        }

        assertTrue(customLogger.text.contains("[{\"hebrew\":\"$hebrew\"}]"))
    }

    // Verify that we can set the level for log domains that the platform doesn't recognize.
    // !!! I don't think this test is actually testing anything.
    @Test
    fun testInternalLogging() {
        val testDb = createDb("base_db")
        Report.log("Created base test DB: $testDb")
        assertNotNull(testDb)
        assertTrue(testDb.isOpen)

        val testCollection = testDb.createCollection(getUniqueName("test_collection"), getUniqueName("test_scope"))
        Report.log("Created base test Collection: $testCollection")
        assertNotNull(testCollection)

        val c4Domain = "foo"
        val testNativeC4Logger = TestC4Logger(c4Domain)
        val testC4Logger = C4Log(testNativeC4Logger)

        LoggersImpl.getLoggers()!!.c4Log = testC4Logger

        testNativeC4Logger.reset()
        QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(testCollection))
            .execute()
        val actualMinLevel = testNativeC4Logger.minLevel
        assertTrue(actualMinLevel >= C4TestUtils.getLogLevel(c4Domain))

        testNativeC4Logger.reset()
        testC4Logger.setLogLevel(c4Domain, actualMinLevel + 1)
        QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(testCollection))
            .execute()
        // If level > maxLevel, should be no logs
        assertEquals(C4Constants.LogLevel.NONE, testNativeC4Logger.minLevel)

        testNativeC4Logger.reset()
        testC4Logger.setLogLevel(c4Domain, C4TestUtils.getLogLevel(c4Domain))
        QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(testCollection))
            .execute()
        assertEquals(actualMinLevel, testNativeC4Logger.minLevel)
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
        val message = "11223344556677889900" // ~43 bytes
        // 24 * 43 = 1032
        for (i in 0..23) {
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
        FileInputStream(log).use { assertEquals(b.size, it.read(b)) }
        return String(b, StandardCharsets.US_ASCII)
    }

    private fun getMostRecent(files: Array<File>?) = files?.maxByOrNull { it.lastModified() }
}