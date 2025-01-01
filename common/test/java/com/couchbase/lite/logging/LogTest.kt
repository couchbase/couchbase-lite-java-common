package com.couchbase.lite.logging

import com.couchbase.lite.BaseDbTest
import com.couchbase.lite.CBLError
import com.couchbase.lite.CouchbaseLiteException
import com.couchbase.lite.DataSource
import com.couchbase.lite.Defaults
import com.couchbase.lite.LogDomain
import com.couchbase.lite.LogLevel
import com.couchbase.lite.Meta
import com.couchbase.lite.MutableDocument
import com.couchbase.lite.QueryBuilder
import com.couchbase.lite.SelectResult
import com.couchbase.lite.internal.core.C4Constants
import com.couchbase.lite.internal.core.C4Log
import com.couchbase.lite.internal.core.C4TestUtils
import com.couchbase.lite.internal.core.CBLVersion
import com.couchbase.lite.internal.logging.Log
import com.couchbase.lite.internal.logging.LogSinksImpl
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

// !!! This won't work when the ConsoleLogSink turns final
private class TestConsoleLogSink(level: LogLevel, domains: Set<LogDomain>) : ConsoleLogSink(level, domains) {
    private val buf = StringBuilder()
    val content
        get() = buf.toString()

    fun writeToLog(level: LogLevel, domain: LogDomain, message: String) = log(level, domain, message)

    override fun writeLog(level: LogLevel, domain: LogDomain, message: String) {
        buf.append(message)
    }
}

private class SingleLineLogSink(private val prefix: String?) : BaseLogSink(LogLevel.DEBUG, LogDomain.ALL) {
    private var level: LogLevel? = null
    private var domain: LogDomain? = null
    private var message: String? = null
    private var latch = CountDownLatch(1)

    override fun writeLog(level: LogLevel, domain: LogDomain, message: String) {
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
            Assert.assertNotNull(files)
            return files!!
        }

    @Before
    fun setUpLogTest() {
        scratchDirPath = getScratchDirectoryPath(getUniqueName("log-dir"))
    }

    @After
    fun tearDownLogTest() = LogSinksImpl.initLogging()

    @Test
    fun testC4LogLevel() {
        val mark = "$$$ ${UUID.randomUUID()}"

        val c4Log = LogSinksImpl.getLogSinks().c4Log
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
                logPath.contains("error") -> Assert.assertEquals(5, lineCount)
                logPath.contains("warning") -> Assert.assertEquals(4, lineCount)
                logPath.contains("info") -> Assert.assertEquals(3, lineCount)
                logPath.contains("verbose") -> Assert.assertEquals(2, lineCount)
                logPath.contains("debug") -> Assert.assertEquals(1, lineCount)
            }
        }
    }

    @Test
    fun testC4MaxFileSize() {
        val c4Log = LogSinksImpl.getLogSinks().c4Log
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

        Assert.assertEquals((2 * 5), logFiles.size)
    }

    @Test
    fun testConsoleLoggerLevel() {
        var i = 0
        for (level in LogLevel.values()) {
            if (level == LogLevel.NONE) {
                continue
            }

            val consoleLogSink = TestConsoleLogSink(level, LogDomain.ALL)
            consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogSink.writeToLog(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogSink.writeToLog(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogSink.writeToLog(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogSink.writeToLog(LogLevel.ERROR, LogDomain.DATABASE, "E")

            Assert.assertEquals("DVIWE".substring(i++), consoleLogSink.content)
        }
    }

    @Test
    fun testConsoleLoggerDomains() {
        for (level in LogLevel.values()) {
            if (level == LogLevel.NONE) {
                continue
            }

            val consoleLogSink = TestConsoleLogSink(level, emptySet())
            consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogSink.writeToLog(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogSink.writeToLog(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogSink.writeToLog(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogSink.writeToLog(LogLevel.ERROR, LogDomain.DATABASE, "E")

            Assert.assertEquals("", consoleLogSink.content)
        }

        for (level in LogLevel.values()) {
            if (level == LogLevel.NONE) {
                continue
            }

            val consoleLogSink = TestConsoleLogSink(level, setOf(LogDomain.NETWORK, LogDomain.QUERY))
            consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogSink.writeToLog(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogSink.writeToLog(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogSink.writeToLog(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogSink.writeToLog(LogLevel.ERROR, LogDomain.DATABASE, "E")

            Assert.assertEquals("", consoleLogSink.content)
        }

        val consoleLogSink = TestConsoleLogSink(LogLevel.DEBUG, LogDomain.ALL)
        consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.NETWORK, "N")
        consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.QUERY, "Q")
        consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.DATABASE, "D")
        Assert.assertEquals("NQD", consoleLogSink.content)
    }

    @Test
    fun testFileLoggerDefaults() {
        val sink = FileLogSink.Builder().setDirectory(scratchDirPath!!).build()
        Assert.assertEquals(Defaults.LogFile.MAX_SIZE, sink.maxFileSize)
        Assert.assertEquals(Defaults.LogFile.MAX_ROTATE_COUNT, sink.maxKeptFiles)
        Assert.assertEquals(Defaults.LogFile.USE_PLAINTEXT, sink.isPlainText)
    }

    @Test
    fun testFileLoggingLevels() {
        val builder = FileLogSink.Builder().setDirectory(scratchDirPath!!).setPlainText(true)
        val mark = "$$$$ ${UUID.randomUUID()}"
        for (level in LogLevel.values()) {
            if (level == LogLevel.NONE) {
                continue
            }

            LogSinks.get().file = builder.setLevel(level).build()
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

    @Test
    fun testFileLoggingLogFilename() {
        testWithConfiguration(LogLevel.DEBUG, FileLogSink.Builder().setDirectory(scratchDirPath!!)) {
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
    fun testFileLoggingDefaultBinaryFormat() {
        testWithConfiguration(LogLevel.INFO, FileLogSink.Builder().setDirectory(scratchDirPath!!)) {
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
        testWithConfiguration(LogLevel.INFO, FileLogSink.Builder().setDirectory(scratchDirPath!!).setPlainText(true)) {
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
    fun testFileLoggingMaxSize() {
        testWithConfiguration(
            LogLevel.DEBUG,
            FileLogSink.Builder().setDirectory(scratchDirPath!!).setPlainText(true).setMaxFileSize(1024)
        ) {
            // This should create two files for each of the 5 levels except verbose (debug, info, warning, error):
            // 1k of logs plus the headers. There should be only one file at the verbose level (just the headers)
            write1KBToLog()
            Assert.assertEquals((4 * 2) + 1, logFiles.size)
        }
    }

    @Test
    fun testFileLoggingDisableLogging() {
        val uuidString = UUID.randomUUID().toString()

        testWithConfiguration(LogLevel.NONE, FileLogSink.Builder().setDirectory(scratchDirPath!!).setPlainText(true)) {
            writeAllLogs(uuidString)
            for (log in logFiles) {
                Assert.assertFalse(getLogContents(log).contains(uuidString))
            }
        }
    }

    @Test
    fun testFileLoggingReEnableLogging() {
        val uuidString = UUID.randomUUID().toString()

        testWithConfiguration(LogLevel.NONE, FileLogSink.Builder().setDirectory(scratchDirPath!!).setPlainText(true)) {
            writeAllLogs(uuidString)

            for (log in logFiles) {
                Assert.assertFalse(getLogContents(log).contains(uuidString))
            }
        }

        testWithConfiguration(LogLevel.INFO, FileLogSink.Builder().setDirectory(scratchDirPath!!).setPlainText(true)) {
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
        testWithConfiguration(
            LogLevel.VERBOSE,
            FileLogSink.Builder().setDirectory(scratchDirPath!!).setPlainText(true)
        ) {
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
    fun testBasicLogFormatting() {
        val nl = System.lineSeparator()

        val sink = SingleLineLogSink("$$\$TEST")
        LogSinks.get().custom = sink

        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG")
        Assert.assertEquals(Log.LOG_HEADER + "$$\$TEST DEBUG", sink.awaitMessage())

        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG", Exception("whoops"))
        var msg = sink.awaitMessage()
        Assert.assertNotNull(msg)
        Assert.assertTrue(
            msg!!.startsWith(
                Log.LOG_HEADER + "$$\$TEST DEBUG" + nl + "java.lang.Exception: whoops" + System.lineSeparator()
            )
        )

        // test formatting, including argument ordering
        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG %2\$s %1\$d %3$.2f", 1, "arg", 3.0f)
        Assert.assertEquals(Log.LOG_HEADER + "$$\$TEST DEBUG arg 1 3.00", sink.awaitMessage())

        Log.d(LogDomain.DATABASE, "$$\$TEST DEBUG %2\$s %1\$d %3$.2f", Exception("whoops"), 1, "arg", 3.0f)
        msg = sink.awaitMessage()
        Assert.assertNotNull(msg)
        Assert.assertTrue(
            msg!!.startsWith(Log.LOG_HEADER + "$$\$TEST DEBUG arg 1 3.00" + nl + "java.lang.Exception: whoops" + nl)
        )
    }

    @Test
    fun testWriteLogWithError() {
        val message = "test message"
        val uuid = UUID.randomUUID().toString()
        val error = CouchbaseLiteException(uuid)

        testWithConfiguration(LogLevel.DEBUG, FileLogSink.Builder().setDirectory(scratchDirPath!!).setPlainText(true)) {
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

        testWithConfiguration(LogLevel.DEBUG, FileLogSink.Builder().setDirectory(scratchDirPath!!).setPlainText(true)) {
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
    fun testLogStandardErrorWithFormatting() {
        val nl = System.lineSeparator()

        Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG %2\$s %1\$d %3$.2f"))

        val logger = SingleLineLogSink("$$\$TEST DEBUG")
        LogSinks.get().custom = logger

        // After initLogging, log level is WARN
        Log.w(LogDomain.DATABASE, "FOO", Exception("whoops"), 1, "arg", 3.0f)

        val msg = logger.awaitMessage()
        Assert.assertNotNull(msg)
        Assert.assertTrue(
            msg!!.startsWith(Log.LOG_HEADER + "$$\$TEST DEBUG arg 1 3.00" + nl + "java.lang.Exception: whoops" + nl)
        )
    }

    @Test
    fun testLookupStandardMessage() {
        Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG"))
        Assert.assertEquals("$$\$TEST DEBUG", Log.lookupStandardMessage("FOO"))
    }

    @Test
    fun testFormatStandardMessage() {
        Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG %2\$s %1\$d %3$.2f"))
        Assert.assertEquals("$$\$TEST DEBUG arg 1 3.00", Log.formatStandardMessage("FOO", 1, "arg", 3.0f))
    }

    // brittle:  will break when the wording of the error message is changed
    @Test
    fun testStandardCBLException() {
        Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG"))
        val msg = CouchbaseLiteException("FOO", CBLError.Domain.CBLITE, CBLError.Code.UNIMPLEMENTED).message
        Assert.assertNotNull(msg)
        Assert.assertTrue(msg.contains("$$\$TEST DEBUG"))
    }

    @Test
    fun testNonStandardCBLException() {
        Log.setStandardErrorMessages(mapOf("FOO" to "$$\$TEST DEBUG"))
        val msg = CouchbaseLiteException("bork", CBLError.Domain.CBLITE, CBLError.Code.UNIMPLEMENTED).message
        Assert.assertNotNull(msg)
        Assert.assertTrue(msg.contains("bork"))
    }

    // Verify that we can handle non-ascii content.
    @Ignore("Need a way to coax LiteCore into logging a non-ascii string")
    @Test
    fun testNonASCII() {
        val hebrew = "מזג האוויר נחמד היום" // The weather is nice today.

        val customLogger = object : BaseLogSink(LogLevel.DEBUG, LogDomain.ALL) {
            var text: String = ""

            override fun writeLog(level: LogLevel, domain: LogDomain, message: String) {
                text += "\n $message"
            }
        }

        LogSinks.get().custom = customLogger


        val doc = MutableDocument()
        doc.setString("hebrew", hebrew)
        testCollection.save(doc)

        // This used to cause LiteCore to log the content of the document.  It doesn't anymore.
        QueryBuilder.select(SelectResult.all()).from(DataSource.collection(testCollection)).execute().use {
            Assert.assertEquals(1, it.allResults().size)
        }

        Assert.assertTrue(customLogger.text.contains("[{\"hebrew\":\"$hebrew\"}]"))
    }

    // Verify that we can set the level for log domains that the platform doesn't recognize.
    // !!! I don't think this test is actually testing anything.
    @Test
    fun testInternalLogging() {
        val c4Domain = "foo"

        val testNativeC4Logger = TestC4Logger(c4Domain)
        val testC4Logger = C4Log(testNativeC4Logger)

        LogSinksImpl.getLogSinks().c4Log = testC4Logger

        testNativeC4Logger.reset()
        QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(testCollection))
            .execute()
        val actualMinLevel = testNativeC4Logger.minLevel
        Assert.assertTrue(actualMinLevel >= C4TestUtils.getLogLevel(c4Domain))

        testNativeC4Logger.reset()
        testC4Logger.setLogLevel(c4Domain, actualMinLevel + 1)
        QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(testCollection))
            .execute()
        // If level > maxLevel, should be no logs
        Assert.assertEquals(C4Constants.LogLevel.NONE, testNativeC4Logger.minLevel)

        testNativeC4Logger.reset()
        testC4Logger.setLogLevel(c4Domain, C4TestUtils.getLogLevel(c4Domain))
        QueryBuilder.select(SelectResult.expression(Meta.id))
            .from(DataSource.collection(testCollection))
            .execute()
        Assert.assertEquals(actualMinLevel, testNativeC4Logger.minLevel)
    }

    private fun testWithConfiguration(level: LogLevel, builder: FileLogSink.Builder, task: Runnable) {
        val sinks = LogSinks.get()
        sinks.console = ConsoleLogSink(level, LogDomain.ALL)
        sinks.file = builder.setLevel(level).build()
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
        FileInputStream(log).use { Assert.assertEquals(b.size, it.read(b)) }
        return String(b, StandardCharsets.US_ASCII)
    }

    private fun getMostRecent(files: Array<File>?) = files?.maxByOrNull { it.lastModified() }
}