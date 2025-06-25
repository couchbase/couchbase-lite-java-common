package com.couchbase.lite.logging

import com.couchbase.lite.BaseDbTest
import com.couchbase.lite.CBLError
import com.couchbase.lite.CouchbaseLiteException
import com.couchbase.lite.Defaults
import com.couchbase.lite.LogDomain
import com.couchbase.lite.LogLevel
import com.couchbase.lite.getC4Db
import com.couchbase.lite.internal.QueryLanguage
import com.couchbase.lite.internal.core.C4Log
import com.couchbase.lite.internal.core.C4Query
import com.couchbase.lite.internal.core.C4TestUtils
import com.couchbase.lite.internal.core.CBLVersion
import com.couchbase.lite.internal.logging.Log
import com.couchbase.lite.internal.logging.LogSinksImpl
import com.couchbase.lite.internal.logging.writeToLog

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

private class ConsoleLogSinkDelegate : ConsoleLogSink.Delegate {
    private val buf = StringBuilder()
    val content
        get() = buf.toString()

    override fun writeLog(level: LogLevel, domain: LogDomain, message: String) {
        buf.append(message)
    }
}

private class SingleLineLogSink(private val prefix: String? = null) : BaseLogSink(LogLevel.DEBUG) {
    private var level: LogLevel? = null
    private var domain: LogDomain? = null
    private var message: String? = null
    private var latch = CountDownLatch(1)

    override fun writeLog(level: LogLevel, domain: LogDomain, message: String) {
        // ignore extraneous logs
        if ((prefix != null) && (!message.startsWith(prefix))) {
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
        get() = assertNonNull(tempDir?.listFiles())

    @Before
    fun setUpLogTest() {
        scratchDirPath = getScratchDirectoryPath(getUniqueName("log-dir"))
    }

    @After
    fun tearDownLogTest() = LogSinksImpl.initLogging()

    @Test
    fun testC4LogLevel() {
        val mark = "$$$ ${UUID.randomUUID()}"

        val c4Log = assertNonNull(LogSinksImpl.getLogSinks()).c4Log
        c4Log.initFileLogging(scratchDirPath, LogLevel.DEBUG, 10, 1024, true, "$$$ TEST")

        for (level in LogLevel.entries) {
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
        val c4Log = assertNonNull(LogSinksImpl.getLogSinks()).c4Log
        c4Log.initFileLogging(scratchDirPath, LogLevel.DEBUG, 10, 1024, true, "$$$$ TEST")
        c4Log.setLogLevel(LogDomain.DATABASE, LogLevel.DEBUG)

        val message = "11223344556677889900" // ~43 bytes
        // 24 * 43 = 1032
        for (i in 0..23) {
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.DEBUG, message)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.VERBOSE, message)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.INFO, message)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.WARNING, message)
            c4Log.logToCore(LogDomain.DATABASE, LogLevel.ERROR, message)
        }

        // each level should have rolled over once
        // and there may have been a few extra things logged as well.
        val n = logFiles.size
        Assert.assertTrue((n >= (2 * 5)) && (n < (3 * 5)))
    }

    @Test
    fun testConsoleLoggerLevel() {
        var i = 0
        for (level in LogLevel.entries) {
            if (level == LogLevel.NONE) {
                continue
            }

            val delegate = ConsoleLogSinkDelegate()
            val consoleLogSink = ConsoleLogSink(level, LogDomain.ALL, delegate)
            consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogSink.writeToLog(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogSink.writeToLog(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogSink.writeToLog(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogSink.writeToLog(LogLevel.ERROR, LogDomain.DATABASE, "E")

            Assert.assertEquals("DVIWE".substring(i++), delegate.content)
        }
    }

    @Test
    fun testConsoleLoggerDomains() {

        for (level in LogLevel.entries) {
            if (level == LogLevel.NONE) {
                continue
            }

            val delegate = ConsoleLogSinkDelegate()
            val consoleLogSink = ConsoleLogSink(level, emptySet(), delegate)
            consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogSink.writeToLog(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogSink.writeToLog(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogSink.writeToLog(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogSink.writeToLog(LogLevel.ERROR, LogDomain.DATABASE, "E")

            Assert.assertEquals("", delegate.content)
        }

        for (level in LogLevel.entries) {
            if (level == LogLevel.NONE) {
                continue
            }

            val delegate = ConsoleLogSinkDelegate()
            val consoleLogSink = ConsoleLogSink(level, setOf(LogDomain.NETWORK, LogDomain.QUERY), delegate)
            consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogSink.writeToLog(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogSink.writeToLog(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogSink.writeToLog(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogSink.writeToLog(LogLevel.ERROR, LogDomain.DATABASE, "E")

            Assert.assertEquals("", delegate.content)
        }

        var i = 0
        for (level in LogLevel.entries) {
            if (level == LogLevel.NONE) {
                continue
            }

            val delegate = ConsoleLogSinkDelegate()
            val consoleLogSink = ConsoleLogSink(level, null, delegate)
            consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.DATABASE, "D")
            consoleLogSink.writeToLog(LogLevel.VERBOSE, LogDomain.DATABASE, "V")
            consoleLogSink.writeToLog(LogLevel.INFO, LogDomain.DATABASE, "I")
            consoleLogSink.writeToLog(LogLevel.WARNING, LogDomain.DATABASE, "W")
            consoleLogSink.writeToLog(LogLevel.ERROR, LogDomain.DATABASE, "E")

            Assert.assertEquals("DVIWE".substring(i++), delegate.content)
        }

        val delegate = ConsoleLogSinkDelegate()
        val consoleLogSink = ConsoleLogSink(LogLevel.DEBUG, LogDomain.ALL, delegate)
        consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.NETWORK, "N")
        consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.QUERY, "Q")
        consoleLogSink.writeToLog(LogLevel.DEBUG, LogDomain.DATABASE, "D")
        Assert.assertEquals("NQD", delegate.content)
    }

    @Test
    fun testFileLoggerDefaults() {
        val sink = FileLogSink.Builder().setDirectory(scratchDirPath!!).build()
        Assert.assertEquals(Defaults.FileLogSink.MAX_SIZE, sink.maxFileSize)
        Assert.assertEquals(Defaults.FileLogSink.MAX_KEPT_FILES, sink.maxKeptFiles)
        Assert.assertEquals(Defaults.FileLogSink.USE_PLAINTEXT, sink.isPlainText)
    }

    @Test
    fun testFileLoggingLevels() {
        val builder = FileLogSink.Builder().setDirectory(scratchDirPath!!).setPlainText(true)
        val mark = "$$$$ ${UUID.randomUUID()}"
        for (level in LogLevel.entries) {
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
            FileLogSink.Builder()
                .setDirectory(scratchDirPath!!)
                .setPlainText(true)
                .setMaxFileSize(1024)
                .setMaxKeptFiles(10)
        ) {
            // This should create two files for each of the 5 levels except verbose (debug, info, warning, error):
            // 1k of logs plus .5k headers. There should be only one file at the verbose level (just the headers)
            write1KBToLog()
            Assert.assertEquals((4 * 2) + 1, logFiles.size)
        }
    }

    @Test
    fun testFileLoggingMaxKeptFiles() {
        testWithConfiguration(
            LogLevel.DEBUG,
            FileLogSink.Builder()
                .setDirectory(scratchDirPath!!)
                .setPlainText(true)
                .setMaxFileSize(1024)
                .setMaxKeptFiles(3)
        ) {
            // This should create several files for each of the 5 levels except verbose (debug, info, warning, error):
            // 1k of logs plus .5k headers. Although lots of files are created they should be trimmed to only 3
            // at each level.  There should be only one file at the verbose level (just the headers)
            for (i in 0..20) {
                write1KBToLog()
            }
            Assert.assertEquals((4 * 3) + 1, logFiles.size)
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

        val sink = SingleLineLogSink(Log.LOG_HEADER + "$$\$TEST")
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

        val logger = SingleLineLogSink(Log.LOG_HEADER + "$$\$TEST DEBUG")
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

    // There was a bug that caused an attempt to log a message from within the
    // LiteCore logging callback to hang the callback thread
    //
    // If there is a file logger, when the platform logs a message it will forwarded
    // to LiteCore.  That, in turn, will call c4log_getDomain.  If c4log_getDomain
    // attempts to re-acquire the same non-reentrant lock that the thread already holds,
    // it will deadlock
    //
    // Since CBL-6800, this test must test only that the platform cannot generate recursive
    // log from the LiteCore callback thread.  A an attempt to log from some other thread
    // during the callback, will be blocked until the callback returns but cannot, directly,
    // deadlock.
    @Ignore("CBL-6787 still not fixed in LiteCore")
    @Test
    fun testRecursiveLogging() {
        val logSinks = assertNonNull(LogSinksImpl.getLogSinks())
        logSinks.file = FileLogSink.Builder().setDirectory(scratchDirPath!!).build()

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(1)

        var success: Boolean? = null

        assertNonNull(logSinks.c4Log).setCallbackInstrumentation(object : C4Log.Instrumentation {
            override fun onLogToCore(domain: LogDomain, level: LogLevel, message: String): Boolean {
                success = false
                return false // do not allow a recursive call!!  It will deadlock!
            }

            override fun onCallback(c4Domain: String?, c4Level: Int, message: String?): Boolean {
                startLatch.countDown()

                // Try to generate a recursive call to LiteCore logging
                Log.e(LogDomain.DATABASE, "BANG!!!")

                // This happens after the call logToCore returns: if Instrumentation.onLogToCore
                // was called, success will be false.  If it was not called, success will be null
                success = success ?: true
                finishLatch.countDown()
                return true
            }
        })

        // This will cause a LiteCore log callback.
        // The instrumentation (above) will attempt to log a message
        // from the callback thread: a recursive call on the same thread.
        // If that call makes it to the instrumentation's onLogToCore method
        // "success" will be false and the test will fail.
        C4TestUtils.forceLog("DB", 3, "BOOM!!!")

        // Wait for the callback to start the thread
        Assert.assertTrue(startLatch.await(10, TimeUnit.SECONDS))

        // Now wait for the callback, including the recursive call, to complete
        val done = finishLatch.await(30, TimeUnit.SECONDS)

        // clean up
        logSinks.c4Log.setCallbackInstrumentation(null)

        Assert.assertTrue(done && (success ?: false))
    }

    // There may have been a bug caused when the destructor for a subclass of LiteCore's Logging
    // class was called from the LiteCore log callback thread. This appeared to happen when, during
    // the callback, the thread attached the JVM (in the JNI code) and attempted to allocate a Thread object.
    // This might cause a garbage collection and the garbage collector appeared to wait for the
    // finalizer/Cleaners to run, freeing the LiteCore native peers (totally a guess). If the destructor
    // for one of the peers attempted to seize the same lock that was already held by LiteCore during
    // the callback, it would deadlock.
    //
    // A C4Query's native peer is a subclass of LiteCore's Logging class.
    // Closing it will release the native peer and cause its destructor to be run
    //
    // This test verifies that the destructor will run to completion, even while a callback is taking place.
    // This is a stronger guarantee than the re-entrant case.  This guarantees that running the destructor
    // cannot cause a deadlock even if the thread calling it blocks the LiteCore logging callback thread.
    @Test
    fun testDeleteLiteCoreLoggerInCallback() {
        val query = C4Query.create(testDatabase.getC4Db, QueryLanguage.N1QL, "SELECT 1 FROM _")

        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(1)

        val testThread = Thread {
            startLatch.countDown()
            query.close()
            finishLatch.countDown()
        }

        val logSinks = assertNonNull(LogSinksImpl.getLogSinks())
        assertNonNull(logSinks.c4Log).setCallbackInstrumentation(object : C4Log.Instrumentation {
            override fun onLogToCore(domain: LogDomain, level: LogLevel, message: String) = true
            override fun onCallback(c4Domain: String?, c4Level: Int, message: String?): Boolean {
                testThread.start()

                // don't return from the callback until the query.close() has run
                finishLatch.await(30, TimeUnit.SECONDS)
                return true
            }
        })

        // This is the thread on which the callback will run
        // it will hang awaiting the finishLatch
        Thread { C4TestUtils.forceLog("DB", 3, "BOOM!!!") }.start()

        // Wait for the callback to start the thread
        Assert.assertTrue(startLatch.await(10, TimeUnit.SECONDS))

        // now wait for the query.close() to complete
        // it should be done very quickly
        val success = finishLatch.await(2, TimeUnit.SECONDS)

        // Clean up
        logSinks.c4Log.setCallbackInstrumentation(null)
        finishLatch.countDown()

        Assert.assertTrue(success)
    }

    @Test
    fun testNonASCII() {
        val prefix = "HEBREW: "
        val hebrew = "מזג האוויר נחמד היום" // The weather is nice today.

        val sink = SingleLineLogSink(prefix)
        LogSinks.get().custom = sink

        C4TestUtils.forceLog("DB", 3, "$prefix $hebrew")

        val msg = sink.awaitMessage()
        Assert.assertTrue(msg?.contains(hebrew) ?: false)
    }

    private fun testWithConfiguration(level: LogLevel, builder: FileLogSink.Builder, task: Runnable) {
        val sinks = LogSinks.get()
        sinks.console = ConsoleLogSink(level, LogDomain.ALL)
        sinks.file = builder.setLevel(level).build()
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
