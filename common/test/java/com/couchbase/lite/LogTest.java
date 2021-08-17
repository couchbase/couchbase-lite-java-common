package com.couchbase.lite;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.FlakyTest;
import com.couchbase.lite.internal.utils.Fn;

import static com.couchbase.lite.internal.utils.TestUtils.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class LogTest extends BaseDbTest {
    private static class SingleLineLogger implements Logger {
        private final String prefix;
        private LogLevel level;
        private LogDomain domain;
        private String message;

        SingleLineLogger(@Nullable String prefix) { this.prefix = prefix; }

        @NonNull
        @Override
        public LogLevel getLevel() { return LogLevel.DEBUG; }

        @Override
        public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            // ignore extraneous logs
            if ((prefix != null) && !message.startsWith(Log.LOG_HEADER + prefix)) { return; }

            this.level = level;
            this.domain = domain;
            this.message = message;
        }

        @NonNull
        @Override
        public String toString() { return level + "/" + domain + ": " + message; }
    }

    private static class LogTestLogger implements Logger {
        private final String prefix;
        private final Map<LogLevel, Integer> lineCounts = new HashMap<>();
        private final StringBuilder content = new StringBuilder();
        private LogLevel level;

        LogTestLogger(@Nullable String prefix) { this.prefix = prefix; }

        @Override
        public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            if ((prefix != null) && !message.startsWith(Log.LOG_HEADER + prefix)) { return; }
            if (level.compareTo(this.level) < 0) { return; }
            lineCounts.put(level, getLineCount(level) + 1);
            content.append(message);
        }

        @NonNull
        @Override
        public LogLevel getLevel() { return level; }

        void setLevel(LogLevel level) { this.level = level; }

        int getLineCount() {
            int total = 0;
            for (LogLevel level: LogLevel.values()) { total += getLineCount(level); }
            return total;
        }

        int getLineCount(LogLevel level) {
            Integer levelCount = lineCounts.get(level);
            return (levelCount == null) ? 0 : levelCount;
        }

        String getContent() { return content.toString(); }
    }

    private static class RawLogListener implements Fn.Consumer<C4Log.RawLog> {
        private final String domainFilter;
        private int minLevel;

        public RawLogListener(String domainFilter) { this.domainFilter = domainFilter; }

        @Override
        public void accept(C4Log.RawLog log) {
            if (!domainFilter.equals(log.domain)) { return; }
            if (log.level < minLevel) { minLevel = log.level; }
        }

        public int getMinLevel() { return minLevel; }

        public void reset() { minLevel = C4Constants.LogLevel.NONE; }
    }

    @AfterClass
    public static void tearDownLogTestClass() { Database.log.reset(); }


    private String scratchDirPath;

    @Before
    public final void setUpLogTest() {
        scratchDirPath = getScratchDirectoryPath(getUniqueName("log-dir"));
        Database.log.reset();
    }

    @Test
    public void testCustomLoggingLevels() {
        LogTestLogger customLogger = new LogTestLogger("$$$TEST ");
        Database.log.setCustom(customLogger);

        for (LogLevel level: LogLevel.values()) {
            customLogger.setLevel(level);
            Log.d(LogDomain.DATABASE, "$$$TEST DEBUG");
            Log.v(LogDomain.DATABASE, "$$$TEST VERBOSE");
            Log.i(LogDomain.DATABASE, "$$$TEST INFO");
            Log.w(LogDomain.DATABASE, "$$$TEST WARNING");
            Log.e(LogDomain.DATABASE, "$$$TEST ERROR");
        }

        assertEquals(2, customLogger.getLineCount(LogLevel.VERBOSE));
        assertEquals(3, customLogger.getLineCount(LogLevel.INFO));
        assertEquals(4, customLogger.getLineCount(LogLevel.WARNING));
        assertEquals(5, customLogger.getLineCount(LogLevel.ERROR));
    }

    @Test
    public void testEnableAndDisableCustomLogging() {
        LogTestLogger customLogger = new LogTestLogger("$$$TEST ");
        Database.log.setCustom(customLogger);

        customLogger.setLevel(LogLevel.NONE);
        Log.d(LogDomain.DATABASE, "$$$TEST DEBUG");
        Log.v(LogDomain.DATABASE, "$$$TEST VERBOSE");
        Log.i(LogDomain.DATABASE, "$$$TEST INFO");
        Log.w(LogDomain.DATABASE, "$$$TEST WARNING");
        Log.e(LogDomain.DATABASE, "$$$TEST ERROR");
        assertEquals(0, customLogger.getLineCount());

        customLogger.setLevel(LogLevel.VERBOSE);
        Log.d(LogDomain.DATABASE, "$$$TEST DEBUG");
        Log.v(LogDomain.DATABASE, "$$$TEST VERBOSE");
        Log.i(LogDomain.DATABASE, "$$$TEST INFO");
        Log.w(LogDomain.DATABASE, "$$$TEST WARNING");
        Log.e(LogDomain.DATABASE, "$$$TEST ERROR");
        assertEquals(4, customLogger.getLineCount());
    }

    @Test
    public void testLogArgs() throws URISyntaxException {
        final String uri = "http://4.4.4.4:4444";
        IllegalArgumentException err = null;
        try { new URLEndpoint(new URI(uri)); }
        catch (IllegalArgumentException e) { err = e; }
        assertNotNull(err);
        assertTrue(err.getMessage().contains(uri));
    }

    @Test
    public void testFileLoggingLevels() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath)
            .setUsePlaintext(true)
            .setMaxRotateCount(0);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                for (LogLevel level: LogLevel.values()) {
                    Database.log.getFile().setLevel(level);
                    Log.d(LogDomain.DATABASE, "$$$TEST DEBUG");
                    Log.v(LogDomain.DATABASE, "$$$TEST VERBOSE");
                    Log.i(LogDomain.DATABASE, "$$$TEST INFO");
                    Log.w(LogDomain.DATABASE, "$$$TEST WARNING");
                    Log.e(LogDomain.DATABASE, "$$$TEST ERROR");
                }

                for (File log: getLogFiles()) {
                    BufferedReader fin = new BufferedReader(new FileReader(log));
                    int lineCount = 0;
                    String l;
                    while ((l = fin.readLine()) != null) {
                        if (l.contains("$$$TEST")) { lineCount++; }
                    }

                    String logPath = log.getCanonicalPath();
                    if (logPath.contains("verbose")) { assertEquals(2, lineCount); }
                    else if (logPath.contains("info")) { assertEquals(3, lineCount); }
                    else if (logPath.contains("warning")) { assertEquals(4, lineCount); }
                    else if (logPath.contains("error")) { assertEquals(5, lineCount); }
                }
            });
    }

    @Test
    public void testFileLoggingDefaultBinaryFormat() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath);

        testWithConfiguration(
            LogLevel.INFO,
            config,
            () -> {
                Log.i(LogDomain.DATABASE, "TEST INFO");

                File[] files = getLogFiles();
                assertTrue(files.length > 0);

                File lastModifiedFile = getMostRecent(files);

                byte[] bytes = new byte[4];
                InputStream is = new FileInputStream(lastModifiedFile);
                assertEquals(4, is.read(bytes));

                assertEquals(bytes[0], (byte) 0xCF);
                assertEquals(bytes[1], (byte) 0xB2);
                assertEquals(bytes[2], (byte) 0xAB);
                assertEquals(bytes[3], (byte) 0x1B);
            });
    }

    @Test
    public void testFileLoggingUsePlainText() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.INFO,
            config,
            () -> {
                String uuidString = UUID.randomUUID().toString();
                Log.i(LogDomain.DATABASE, uuidString);

                File[] files = getTempDir().listFiles((dir, name) -> name.toLowerCase().startsWith("cbl_info_"));
                assertNotNull(files);
                assertEquals(1, files.length);

                assertTrue(getLogContents(getMostRecent(files)).contains(uuidString));
            });
    }

    @Test
    public void testFileLoggingLogFilename() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                Log.e(LogDomain.DATABASE, "$$$TEST MESSAGE");

                File[] files = getLogFiles();
                assertTrue(files.length >= 4);

                String filenameRegex = "cbl_(debug|verbose|info|warning|error)_\\d+\\.cbllog";
                for (File file: files) { assertTrue(file.getName().matches(filenameRegex)); }
            });
    }

    @Test
    public void testFileLoggingMaxSize() throws Exception {
        final LogFileConfiguration config = new LogFileConfiguration(scratchDirPath)
            .setUsePlaintext(true)
            .setMaxSize(1024);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                // this should create two files, as the 1KB logs + extra header
                writeOneKiloByteOfLog();
                assertEquals((config.getMaxRotateCount() + 1) * 5, getLogFiles().length);
            });
    }

    @Test
    public void testFileLoggingDisableLogging() throws Exception {
        String uuidString = UUID.randomUUID().toString();

        final LogFileConfiguration config = new LogFileConfiguration(scratchDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.NONE,
            config,
            () -> {
                writeAllLogs(uuidString);
                for (File log: getLogFiles()) { assertFalse(getLogContents(log).contains(uuidString)); }
            });
    }

    @Test
    public void testFileLoggingReEnableLogging() throws Exception {
        String uuidString = UUID.randomUUID().toString();

        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.NONE,
            config,
            () -> {
                writeAllLogs(uuidString);

                for (File log: getLogFiles()) { assertFalse(getLogContents(log).contains(uuidString)); }

                Database.log.getFile().setLevel(LogLevel.VERBOSE);
                writeAllLogs(uuidString);

                File[] filesExceptDebug
                    = getTempDir().listFiles((ign, name) -> !name.toLowerCase().startsWith("cbl_debug_"));
                assertNotNull(filesExceptDebug);

                for (File log: filesExceptDebug) { assertTrue(getLogContents(log).contains(uuidString)); }
            });
    }

    @Test
    public void testFileLoggingHeader() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.VERBOSE,
            config,
            () -> {
                writeOneKiloByteOfLog();
                for (File log: getLogFiles()) {
                    BufferedReader fin = new BufferedReader(new FileReader(log));
                    String firstLine = fin.readLine();

                    assertNotNull(firstLine);
                    assertTrue(firstLine.contains("CouchbaseLite " + PlatformBaseTest.PRODUCT));
                    assertTrue(firstLine.contains("Core/"));
                    assertTrue(firstLine.contains(CBLVersion.getSysInfo()));
                }
            });
    }

    @Test
    public void testBasicLogFormatting() {
        String nl = System.lineSeparator();

        SingleLineLogger logger = new SingleLineLogger("$$$TEST");
        Database.log.setCustom(logger);

        Log.d(LogDomain.DATABASE, "$$$TEST DEBUG");
        assertEquals(Log.LOG_HEADER + "$$$TEST DEBUG", logger.message);

        Log.d(LogDomain.DATABASE, "$$$TEST DEBUG", new Exception("whoops"));
        String msg = logger.message;
        assertNotNull(msg);
        assertTrue(msg.startsWith(
            Log.LOG_HEADER + "$$$TEST DEBUG" + nl + "java.lang.Exception: whoops" + System.lineSeparator()));

        // test formatting, including argument ordering
        Log.d(LogDomain.DATABASE, "$$$TEST DEBUG %2$s %1$d %3$.2f", 1, "arg", 3.0F);
        assertEquals(Log.LOG_HEADER + "$$$TEST DEBUG arg 1 3.00", logger.message);

        Log.d(LogDomain.DATABASE, "$$$TEST DEBUG %2$s %1$d %3$.2f", new Exception("whoops"), 1, "arg", 3.0F);
        msg = logger.message;
        assertNotNull(msg);
        assertTrue(msg.startsWith(
            Log.LOG_HEADER + "$$$TEST DEBUG arg 1 3.00" + nl + "java.lang.Exception: whoops" + nl));
    }

    @Test
    public void testWriteLogWithError() throws Exception {
        String message = "test message";
        String uuid = UUID.randomUUID().toString();
        CouchbaseLiteException error = new CouchbaseLiteException(uuid);

        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                Log.d(LogDomain.DATABASE, message, error);
                Log.v(LogDomain.DATABASE, message, error);
                Log.i(LogDomain.DATABASE, message, error);
                Log.w(LogDomain.DATABASE, message, error);
                Log.e(LogDomain.DATABASE, message, error);

                for (File log: getLogFiles()) { assertTrue(getLogContents(log).contains(uuid)); }
            });
    }

    @Test
    public void testWriteLogWithErrorAndArgs() throws Exception {
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String message = "test message %s";
        CouchbaseLiteException error = new CouchbaseLiteException(uuid1);

        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath).setUsePlaintext(true);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                Log.d(LogDomain.DATABASE, message, error, uuid2);
                Log.v(LogDomain.DATABASE, message, error, uuid2);
                Log.i(LogDomain.DATABASE, message, error, uuid2);
                Log.w(LogDomain.DATABASE, message, error, uuid2);
                Log.e(LogDomain.DATABASE, message, error, uuid2);

                for (File log: getLogFiles()) {
                    String content = getLogContents(log);
                    assertTrue(content.contains(uuid1));
                    assertTrue(content.contains(uuid2));
                }
            });
    }

    @Test
    public void testLogFileConfigurationConstructors() {
        int rotateCount = 4;
        long maxSize = 2048;
        boolean usePlainText = true;

        assertThrows(IllegalArgumentException.class, () -> new LogFileConfiguration((String) null));

        assertThrows(IllegalArgumentException.class, () -> new LogFileConfiguration((LogFileConfiguration) null));

        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath)
            .setMaxRotateCount(rotateCount)
            .setMaxSize(maxSize)
            .setUsePlaintext(usePlainText);
        assertEquals(config.getMaxRotateCount(), rotateCount);
        assertEquals(config.getMaxSize(), maxSize);
        assertEquals(config.usesPlaintext(), usePlainText);
        assertEquals(config.getDirectory(), scratchDirPath);

        final String tempDir2 = getScratchDirectoryPath(getUniqueName("logtest2"));
        LogFileConfiguration newConfig = new LogFileConfiguration(tempDir2, config);
        assertEquals(newConfig.getMaxRotateCount(), rotateCount);
        assertEquals(newConfig.getMaxSize(), maxSize);
        assertEquals(newConfig.usesPlaintext(), usePlainText);
        assertEquals(newConfig.getDirectory(), tempDir2);
    }

    @Test
    public void testEditReadOnlyLogFileConfiguration() throws Exception {
        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath);

        testWithConfiguration(
            LogLevel.DEBUG,
            config,
            () -> {
                assertThrows(IllegalStateException.class, () -> Database.log.getFile().getConfig().setMaxSize(1024));

                assertThrows(
                    IllegalStateException.class,
                    () -> Database.log.getFile().getConfig().setMaxRotateCount(3));

                assertThrows(
                    IllegalStateException.class,
                    () -> Database.log.getFile().getConfig().setUsePlaintext(true));
            });
    }

    @Test
    public void testSetNewLogFileConfiguration() {
        LogFileConfiguration config = new LogFileConfiguration(scratchDirPath);

        final FileLogger fileLogger = Database.log.getFile();
        fileLogger.setConfig(config);
        assertEquals(fileLogger.getConfig(), config);
        fileLogger.setConfig(config);
        assertEquals(fileLogger.getConfig(), config);
        fileLogger.setConfig(null);
        assertNull(fileLogger.getConfig());
        fileLogger.setConfig(null);
        assertNull(fileLogger.getConfig());
        fileLogger.setConfig(config);
        assertEquals(fileLogger.getConfig(), config);
        fileLogger.setConfig(new LogFileConfiguration(scratchDirPath + "/foo"));
        assertEquals(fileLogger.getConfig(), new LogFileConfiguration(scratchDirPath + "/foo"));
    }

    @FlakyTest(log = {"Linux: 21/06/11"})
    @Test
    public void testNonASCII() throws CouchbaseLiteException {
        String hebrew = "מזג האוויר נחמד היום"; // The weather is nice today.

        LogTestLogger customLogger = new LogTestLogger(null);
        customLogger.setLevel(LogLevel.VERBOSE);
        Database.log.setCustom(customLogger);

        // hack: The console logger sets the C4 callback level
        Database.log.getConsole().setLevel(LogLevel.VERBOSE);

        MutableDocument doc = new MutableDocument();
        doc.setString("hebrew", hebrew);
        saveDocInBaseTestDb(doc);

        Query query = QueryBuilder.select(SelectResult.all()).from(DataSource.database(baseTestDb));
        try (ResultSet rs = query.execute()) { assertEquals(rs.allResults().size(), 1); }

        assertTrue(customLogger.getContent().contains("[{\"hebrew\":\"" + hebrew + "\"}]"));
    }

    @Test
    public void testLogStandardErrorWithFormatting() {
        String nl = System.lineSeparator();

        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("FOO", "$$$TEST DEBUG %2$s %1$d %3$.2f");
        try {
            Log.initLogging(stdErr);

            SingleLineLogger logger = new SingleLineLogger("$$$TEST DEBUG");
            Database.log.setCustom(logger);

            Log.d(LogDomain.DATABASE, "FOO", new Exception("whoops"), 1, "arg", 3.0F);
            String msg = logger.message;
            assertNotNull(msg);
            assertTrue(msg.startsWith(
                Log.LOG_HEADER + "$$$TEST DEBUG arg 1 3.00" + nl + "java.lang.Exception: whoops" + nl));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test
    public void testLookupStandardMessage() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("FOO", "$$$TEST DEBUG");
        try {
            Log.initLogging(stdErr);
            assertEquals("$$$TEST DEBUG", Log.lookupStandardMessage("FOO"));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test
    public void testFormatStandardMessage() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("FOO", "$$$TEST DEBUG %2$s %1$d %3$.2f");
        try {
            Log.initLogging(stdErr);
            assertEquals("$$$TEST DEBUG arg 1 3.00", Log.formatStandardMessage("FOO", 1, "arg", 3.0F));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    // brittle:  will break when the wording of the error message is changed
    @Test
    public void testStandardCBLException() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("FOO", "$$$TEST DEBUG");
        try {
            Log.initLogging(stdErr);
            CouchbaseLiteException e = new CouchbaseLiteException(
                "FOO",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNIMPLEMENTED);
            String msg = e.getMessage();
            assertNotNull(msg);
            assertTrue(msg.startsWith("$$$TEST DEBUG"));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test
    public void testNonStandardCBLException() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("FOO", "$$$TEST DEBUG");
        try {
            Log.initLogging(stdErr);
            CouchbaseLiteException e = new CouchbaseLiteException(
                "bork",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNIMPLEMENTED);
            String msg = e.getMessage();
            assertNotNull(msg);
            assertTrue(msg.startsWith("bork"));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    // Verify that we can set the level for log domains that the platform doesn't recognize.
    @Test
    public void testInternalLogging() throws CouchbaseLiteException {
        final String c4Domain = "foo";

        final RawLogListener rawLogListener = new RawLogListener(c4Domain);
        C4Log.registerListener(rawLogListener);
        int originalLogLevel = C4Log.getLevel(c4Domain);
        try {
            rawLogListener.reset();
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(baseTestDb))
                .execute();
            int actualMinLevel = rawLogListener.getMinLevel();
            assertTrue(actualMinLevel >= originalLogLevel);

            C4Log.setLevels(actualMinLevel + 1, c4Domain);
            rawLogListener.reset();
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(baseTestDb))
                .execute();
            // If level > maxLevel, should be no logs
            assertEquals(C4Constants.LogLevel.NONE, rawLogListener.getMinLevel());

            rawLogListener.reset();
            C4Log.setLevels(originalLogLevel, c4Domain);
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(baseTestDb))
                .execute();
            assertEquals(actualMinLevel, rawLogListener.getMinLevel());
        }
        finally {
            C4Log.registerListener(null);
            C4Log.setLevels(originalLogLevel, c4Domain);
        }
    }

    private void testWithConfiguration(LogLevel level, LogFileConfiguration config, Fn.TaskThrows<Exception> task)
        throws Exception {
        final com.couchbase.lite.Log logger = Database.log;

        final ConsoleLogger consoleLogger = logger.getConsole();
        consoleLogger.setLevel(level);
        final LogLevel consoleLogLevel = consoleLogger.getLevel();

        final FileLogger fileLogger = logger.getFile();
        fileLogger.setConfig(config);
        fileLogger.setLevel(level);
        final LogLevel fileLogLevel = fileLogger.getLevel();

        try { task.run(); }
        finally {
            consoleLogger.setLevel(consoleLogLevel);
            fileLogger.setLevel(fileLogLevel);
        }
    }

    private void writeOneKiloByteOfLog() {
        String message = "11223344556677889900"; // ~43 bytes
        // 24 * 43 = 1032
        for (int i = 0; i < 24; i++) { writeAllLogs(message); }
    }

    private void writeAllLogs(String message) {
        Log.d(LogDomain.DATABASE, message);
        Log.v(LogDomain.DATABASE, message);
        Log.i(LogDomain.DATABASE, message);
        Log.w(LogDomain.DATABASE, message);
        Log.e(LogDomain.DATABASE, message);
    }

    @NonNull
    private String getLogContents(File log) throws IOException {
        byte[] b = new byte[(int) log.length()];
        FileInputStream fileInputStream = new FileInputStream(log);
        assertEquals(b.length, fileInputStream.read(b));
        return new String(b, StandardCharsets.US_ASCII);
    }

    @NonNull
    private File getMostRecent(@NonNull File[] files) {
        File lastModifiedFile = files[0];
        for (File log: files) {
            if (log.lastModified() > lastModifiedFile.lastModified()) { lastModifiedFile = log; }
        }
        return lastModifiedFile;
    }

    @NonNull
    private File[] getLogFiles() {
        File[] files = getTempDir().listFiles();
        assertNotNull(files);
        return files;
    }

    @Nullable
    private File getTempDir() { return (scratchDirPath == null) ? null : new File(scratchDirPath); }
}
