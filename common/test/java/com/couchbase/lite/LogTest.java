package com.couchbase.lite;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.FlakyTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class LogTest extends BaseDbTest {
    @FunctionalInterface
    public interface LogHandler {
        void accept(@NonNull LogLevel level, @NonNull LogDomain domain, @Nullable String message);
    }

    private static class TestLogger extends CustomLogger {
        private final String prefix;
        private final LogHandler handler;

        TestLogger(@Nullable String prefix, @NonNull LogHandler handler) { this(LogLevel.DEBUG, prefix, handler); }

        TestLogger(@NonNull LogLevel level, @Nullable String prefix, @NonNull LogHandler handler) {
            super(level);
            this.prefix = prefix;
            this.handler = handler;
        }

        @Override
        public void writeLog(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
            System.out.println("### " + domain + "/" + level + ": " + message);
            // ignore extraneous logs
            if ((prefix != null) && !message.startsWith(Log.LOG_HEADER + prefix)) { return; }
            handler.accept(level, domain, message);
        }
    }

    private static class LogListener implements C4Log.RawLogListener {
        private final String domainFilter;
        private int minLevel;

        public LogListener(String domainFilter) { this.domainFilter = domainFilter; }

        @Override
        public void accept(@NonNull String domain, int level, @Nullable String message) {
            if (!domainFilter.equals(domain)) { return; }
            if (level < minLevel) { minLevel = level; }
        }

        public int getMinLevel() { return minLevel; }

        public void reset() { minLevel = C4Constants.LogLevel.NONE; }
    }


    private String scratchDirPath;

    @Before
    public final void setUpLogTest() {
        scratchDirPath = getScratchDirectoryPath(getUniqueName("log_test"));
    }

    @Test
    public void testURLEndpointURIWithBadScheme() throws URISyntaxException {
        final String uri = "http://4.4.4.4:4444";
        try {
            new URLEndpoint(new URI(uri));
            fail("URLEndpoint should not accept URI with http scheme");
        }
        catch (IllegalArgumentException e) { assertTrue(e.getMessage().contains(uri)); }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testURLEndpointURIWithCredentials() throws URISyntaxException {
        new URLEndpoint(new URI("http://user:pwd@4.4.4.4:4444"));
    }

    @Test
    public void testBasicLogFormatting() {
        final String nl = System.lineSeparator();

        final StringBuilder buf = new StringBuilder();
        final LogHandler handler = (l, d, m) -> {
            buf.setLength(0);
            buf.append(m);
        };

        TestLogger logger = new TestLogger("$$$TEST", handler);
        CouchbaseLite.getLoggers().setCustomLogger(logger);

        Log.d(LogDomain.DATABASE, "$$$TEST DEBUG");
        assertEquals(Log.LOG_HEADER + "$$$TEST DEBUG", buf.toString());

        Log.d(LogDomain.DATABASE, "$$$TEST DEBUG", new Exception("whoops"));
        assertTrue(buf.toString().startsWith(
            Log.LOG_HEADER + "$$$TEST DEBUG" + nl + "java.lang.Exception: whoops" + nl));

        // test formatting, including argument ordering
        Log.d(LogDomain.DATABASE, "$$$TEST DEBUG %2$s %1$d %3$.2f", 1, "arg", 3.0F);
        assertEquals(Log.LOG_HEADER + "$$$TEST DEBUG arg 1 3.00", buf.toString());

        // the whole megillah
        Log.d(LogDomain.DATABASE, "$$$TEST DEBUG %2$s %1$d %3$.2f", new Exception("whoops"), 1, "arg", 3.0F);
        assertTrue(buf.toString().startsWith(
            Log.LOG_HEADER + "$$$TEST DEBUG arg 1 3.00" + nl + "java.lang.Exception: whoops" + nl));
    }

    @Test
    public void testLookupStandardMessage() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("$$$TAG", "$$$TEST DEBUG");
        try {
            Log.initLogging(stdErr);
            assertEquals("$$$TEST DEBUG", Log.lookupStandardMessage("$$$TAG"));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test
    public void testFormatStandardMessage() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("$$$TAG", "$$$TEST DEBUG %2$s %1$d %3$.2f");
        try {
            Log.initLogging(stdErr);
            assertEquals("$$$TEST DEBUG arg 1 3.00", Log.formatStandardMessage("$$$TAG", 1, "arg", 3.0F));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test
    public void testStandardCBLException() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("$$$TAG", "$$$TEST");
        try {
            Log.initLogging(stdErr);
            CouchbaseLiteException e
                = new CouchbaseLiteException("$$$TAG", CBLError.Domain.CBLITE, CBLError.Code.UNIMPLEMENTED);
            String msg = e.getMessage();
            assertTrue(msg.startsWith("$$$TEST"));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test
    public void testNonStandardCBLException() {
        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("$$$TAG", "$$$TEST");
        try {
            Log.initLogging(stdErr);
            CouchbaseLiteException e
                = new CouchbaseLiteException("FOO", CBLError.Domain.CBLITE, CBLError.Code.UNIMPLEMENTED);
            String msg = e.getMessage();
            assertNotNull(msg);
            assertTrue(msg.startsWith("FOO"));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test
    public void testLogStandardErrorWithFormatting() {
        String nl = System.lineSeparator();

        Map<String, String> stdErr = new HashMap<>();
        stdErr.put("$$$TAG", "$$$TEST DEBUG %2$s %1$d %3$.2f");
        try {
            Log.initLogging(stdErr);

            final StringBuilder buf = new StringBuilder();
            final LogHandler handler = (l, d, m) -> {
                buf.setLength(0);
                buf.append(m);
            };

            TestLogger logger = new TestLogger("$$$TEST", handler);
            CouchbaseLite.getLoggers().setCustomLogger(logger);

            Log.d(LogDomain.DATABASE, "$$$TAG", new Exception("whoops"), 1, "arg", 3.0F);

            assertTrue(buf.toString().startsWith(
                Log.LOG_HEADER + "$$$TEST DEBUG arg 1 3.00" + nl + "java.lang.Exception: whoops" + nl));
        }
        finally {
            reloadStandardErrorMessages();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFileLoggerBuilderArgNullDirFile() { new FileLogger.Builder((File) null); }

    @Test(expected = IllegalArgumentException.class)
    public void testFileLoggerBuilderArgNullDir() { new FileLogger.Builder((String) null); }

    @Test(expected = IllegalArgumentException.class)
    public void testFileLoggerBuilderArgNullLogger() { new FileLogger.Builder((FileLogger) null); }

    @Test(expected = IllegalArgumentException.class)
    public void testFileLoggerBuilderArgNullLevel() { new FileLogger.Builder((FileLogger) null); }

    @Test(expected = IllegalArgumentException.class)
    public void testFileLoggerBuilderArgBadDir() { new FileLogger.Builder("/foo"); }

    @Test(expected = IllegalArgumentException.class)
    public void testFileLoggerBuilderArgFileMaxTooSmall() { new FileLogger.Builder("").setMaxFileSize(72); }

    @Test(expected = IllegalArgumentException.class)
    public void testFileLoggerBuilderArgFileRotateTooSmall() { new FileLogger.Builder("").setMaxRotateCount(-1); }

    @Test
    public void testFileLoggingLevels() throws IOException {
        final Loggers loggers = CouchbaseLite.getLoggers();
        loggers.setFileLogger(
            new FileLogger.Builder(scratchDirPath)
                .setUsePlaintext(true)
                .setMaxRotateCount(0)
                .build());

        for (LogLevel level: LogLevel.values()) {
            CouchbaseLite.getLoggers().setFileLogger(
                new FileLogger.Builder(loggers.getFileLogger())
                    .setLevel(level)
                    .build());
            writeAllLogs("$$$TEST");
        }

        File[] logs = getAllLogFiles();
        assertTrue(logs.length > 0);

        for (File log: logs) {
            try (BufferedReader fin = new BufferedReader(new FileReader(log))) {
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
        }
    }

    @Test
    public void testFileLoggingBinary() throws Exception {
        CouchbaseLite.getLoggers().setFileLogger(
            new FileLogger.Builder(scratchDirPath)
                .setLevel(LogLevel.INFO)
                .build());

        Log.i(LogDomain.DATABASE, "TEST INFO");

        final File[] infoLog = getLogFiles((dir, name) -> name.toLowerCase().startsWith("cbl_info"));
        assertEquals(1, infoLog.length);

        final byte[] bytes = new byte[4];
        int n;
        try (InputStream is = new FileInputStream(infoLog[0])) { n = is.read(bytes); }

        assertEquals(4, n);
        assertEquals(bytes[0], (byte) 0xCF);
        assertEquals(bytes[1], (byte) 0xB2);
        assertEquals(bytes[2], (byte) 0xAB);
        assertEquals(bytes[3], (byte) 0x1B);
    }

    @Test
    public void testFileLoggingPlainText() throws IOException {
        CouchbaseLite.getLoggers().setFileLogger(
            new FileLogger.Builder(scratchDirPath)
                .setLevel(LogLevel.INFO)
                .setUsePlaintext(true)
                .build());

        final String uuidString = UUID.randomUUID().toString();
        Log.i(LogDomain.DATABASE, uuidString);

        final File[] infoLog = getLogFiles((dir, name) -> name.toLowerCase().startsWith("cbl_info"));
        assertEquals(1, infoLog.length);

        assertTrue(getLogContents(infoLog[0]).contains(uuidString));
    }

    @Test
    public void testFileLoggingMaxSize() {
        CouchbaseLite.getLoggers().setFileLogger(
            new FileLogger.Builder(scratchDirPath)
                .setLevel(LogLevel.DEBUG)
                .setUsePlaintext(true)
                .setMaxFileSize(1024)
                .setMaxRotateCount(1)
                .build());

        logOneKB();

        assertEquals((1 + 1) * 5, getAllLogFiles().length);
    }

    @Test
    public void testFileLoggingDisableLogging() throws Exception {
        CouchbaseLite.getLoggers().setFileLogger(
            new FileLogger.Builder(scratchDirPath)
                .setLevel(LogLevel.NONE)
                .setUsePlaintext(true)
                .setMaxFileSize(1024)
                .setMaxRotateCount(1)
                .build());


        String uuidString = UUID.randomUUID().toString();
        writeAllLogs(uuidString);

        File[] logs = getAllLogFiles();
        assertTrue(logs.length > 0);

        for (File log: logs) { assertFalse(getLogContents(log).contains(uuidString)); }
    }

    @Test
    public void testFileLoggingReEnableLogging() throws Exception {
        testFileLoggingDisableLogging();

        CouchbaseLite.getLoggers().setFileLogger(
            new FileLogger.Builder(scratchDirPath)
                .setLevel(LogLevel.DEBUG)
                .setUsePlaintext(true)
                .build());

        String uuidString = UUID.randomUUID().toString();
        writeAllLogs(uuidString);

        File[] logs = getLogFiles();
        for (File log: logs) { assertTrue(getLogContents(log).contains(uuidString)); }
    }

    @Test
    public void testFileLoggingHeader() throws Exception {
        CouchbaseLite.getLoggers().setFileLogger(
            new FileLogger.Builder(scratchDirPath)
                .setLevel(LogLevel.NONE)
                .setUsePlaintext(true)
                .build());

        logOneKB();

        File[] logs = getLogFiles();

        for (File log: logs) {
            try (BufferedReader fin = new BufferedReader(new FileReader(log))) {
                String firstLine = fin.readLine();
                assertNotNull(firstLine);
                assertTrue(firstLine.contains("CouchbaseLite " + PlatformBaseTest.PRODUCT));
                assertTrue(firstLine.contains("Core/"));
                assertTrue(firstLine.contains(CBLVersion.getSysInfo()));
            }
        }
    }

    @FlakyTest
    @Test
    public void testWriteFileLogWithError() throws Exception {
        CouchbaseLite.getLoggers().setFileLogger(
            new FileLogger.Builder(scratchDirPath)
                .setLevel(LogLevel.DEBUG)
                .setUsePlaintext(true)
                .build());

        String uuid = UUID.randomUUID().toString();
        CouchbaseLiteException error = new CouchbaseLiteException(uuid);

        Log.d(LogDomain.DATABASE, "$$$TEST", error);
        Log.v(LogDomain.DATABASE, "$$$TEST", error);
        Log.i(LogDomain.DATABASE, "$$$TEST", error);
        Log.w(LogDomain.DATABASE, "$$$TEST", error);
        Log.e(LogDomain.DATABASE, "$$$TEST", error);

        for (File log: getLogFiles()) { assertTrue(getLogContents(log).contains(uuid)); }
    }

    @Test
    public void testWriteFileLogWithErrorAndArgs() throws Exception {
        CouchbaseLite.getLoggers().setFileLogger(
            new FileLogger.Builder(scratchDirPath)
                .setLevel(LogLevel.DEBUG)
                .setUsePlaintext(true)
                .build());

        String errTag = UUID.randomUUID().toString();
        String argTag = UUID.randomUUID().toString();
        String message = "test message %s";
        CouchbaseLiteException error = new CouchbaseLiteException(errTag);

        Log.d(LogDomain.DATABASE, message, error, argTag);
        Log.v(LogDomain.DATABASE, message, error, argTag);
        Log.i(LogDomain.DATABASE, message, error, argTag);
        Log.w(LogDomain.DATABASE, message, error, argTag);
        Log.e(LogDomain.DATABASE, message, error, argTag);

        File[] logs = getLogFiles();

        for (File log: logs) {
            String content = getLogContents(log);
            assertTrue(content.contains(errTag));
            assertTrue(content.contains(argTag));
        }
    }

    // This should be a LiteCore unit test...
    @Test
    public void testFileLoggingLogFilename() {
        CouchbaseLite.getLoggers().setFileLogger(
            new FileLogger.Builder(scratchDirPath)
                .build());

        Log.e(LogDomain.DATABASE, "$$$TEST");

        File[] logs = getAllLogFiles();
        assertTrue(logs.length >= 4);

        String filenameRegex = "cbl_(debug|verbose|info|warning|error)_\\d+\\.cbllog";
        for (File file: logs) { assertTrue(file.getName().matches(filenameRegex)); }
    }

    @Test
    public void testCustomLoggingLevels() {
        final Map<LogLevel, Integer> counts = new HashMap<>();
        final LogHandler counter = (l, d, m) -> counts.put(l, asInt(counts.get(l)) + 1);

        for (LogLevel level: LogLevel.values()) {
            CouchbaseLite.getLoggers().setCustomLogger(new TestLogger(level, "$$$TEST", counter));
            writeAllLogs("$$$TEST");
        }

        assertEquals(2, asInt(counts.get(LogLevel.VERBOSE)));
        assertEquals(3, asInt(counts.get(LogLevel.INFO)));
        assertEquals(4, asInt(counts.get(LogLevel.WARNING)));
        assertEquals(5, asInt(counts.get(LogLevel.ERROR)));
    }

    @Test
    public void testEnableAndDisableCustomLogging() {
        int[] lines = {0};
        final LogHandler counter = (l, d, m) -> lines[0]++;

        CouchbaseLite.getLoggers().setCustomLogger(new TestLogger(LogLevel.NONE, "$$$TEST", counter));
        writeAllLogs("$$$TEST");
        assertEquals(0, lines[0]);

        CouchbaseLite.getLoggers().setCustomLogger(new TestLogger(LogLevel.VERBOSE, "$$$TEST", counter));
        writeAllLogs("$$$TEST");
        assertEquals(4, lines[0]);
    }

    @Test
    public void testNonASCII() {
        String message = "$$$TEST מזג האוויר נחמד היום"; // The weather is nice today.

        final Map<LogLevel, StringBuffer> content = new HashMap<>();
        final LogHandler contentBuilder = (l, d, m) -> {
            StringBuffer buf = content.get(l);
            if (buf == null) {
                buf = new StringBuffer();
                content.put(l, buf);
            }
            buf.append(m);
        };

        TestLogger customLogger = new TestLogger(LogLevel.DEBUG, "$$$TEST", contentBuilder);
        CouchbaseLite.getLoggers().setCustomLogger(customLogger);

        writeAllLogs(message);

        assertEquals(5, content.size());
        for (StringBuffer buf: content.values()) { assertTrue(buf.toString().contains(message)); }
    }

    // Verify that we can set the level for log domains that the platform doesn't recognize.
    @Test
    public void testInternalLogging() throws CouchbaseLiteException {
        final String c4Domain = "foo";

        final LogListener rawLogListener = new LogListener(c4Domain);
        C4Log.registerRawListener(rawLogListener);
        int originalLogLevel = C4Log.getLevelForDomain(c4Domain);
        try {
            rawLogListener.reset();
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(baseTestDb))
                .execute();
            int actualMinLevel = rawLogListener.getMinLevel();
            assertTrue(actualMinLevel >= originalLogLevel);

            C4Log.setLevelForDomain(c4Domain, actualMinLevel + 1);
            rawLogListener.reset();
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(baseTestDb))
                .execute();
            // If level > maxLevel, should be no logs
            assertEquals(C4Constants.LogLevel.NONE, rawLogListener.getMinLevel());

            rawLogListener.reset();
            C4Log.setLevelForDomain(c4Domain, originalLogLevel);
            QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(baseTestDb))
                .execute();
            assertEquals(actualMinLevel, rawLogListener.getMinLevel());
        }
        finally {
            C4Log.registerRawListener(null);
            C4Log.setLevelForDomain(c4Domain, originalLogLevel);
        }
    }

    private int asInt(@Nullable Integer n) { return (n == null) ? 0 : n; }

    private void logOneKB() {
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
        final byte[] buf = new byte[(int) log.length()];
        final int n;
        try (FileInputStream in = new FileInputStream(log)) { n = in.read(buf); }
        assertEquals(buf.length, n);
        return new String(buf, StandardCharsets.US_ASCII);
    }

    private File[] getAllLogFiles() { return getLogFiles(null); }

    private File[] getLogFiles() { return getLogFiles((dir, name) -> name.toLowerCase().startsWith("cbl_")); }

    private File[] getLogFiles(FilenameFilter filter) {
        File[] files = new File(scratchDirPath).listFiles(filter);
        assertNotNull(files);
        assertTrue(files.length > 0);
        return files;
    }
}
