package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the main JdbcUrlParser facade.
 */
class JdbcUrlParserTest {

    @Test
    void testDetectDatabaseProduct() {

        assertEquals(DatabaseProduct.MYSQL, JdbcUrlParser.detectDatabaseProduct("jdbc:mysql://localhost/db"));
        assertEquals(DatabaseProduct.POSTGRESQL, JdbcUrlParser.detectDatabaseProduct("jdbc:postgresql://localhost/db"));
        assertEquals(
                DatabaseProduct.ORACLE, JdbcUrlParser.detectDatabaseProduct("jdbc:oracle:thin:@localhost:1521:ORCL"));
        assertEquals(
                DatabaseProduct.SQLSERVER,
                JdbcUrlParser.detectDatabaseProduct("jdbc:sqlserver://localhost;databaseName=db"));
        assertEquals(DatabaseProduct.H2, JdbcUrlParser.detectDatabaseProduct("jdbc:h2:mem:test"));
        assertEquals(DatabaseProduct.HSQLDB, JdbcUrlParser.detectDatabaseProduct("jdbc:hsqldb:file:test"));
        assertEquals(DatabaseProduct.DERBY, JdbcUrlParser.detectDatabaseProduct("jdbc:derby:mydb"));
        assertEquals(DatabaseProduct.SQLITE, JdbcUrlParser.detectDatabaseProduct("jdbc:sqlite:test.db"));
    }

    @Test
    void testIsJdbcUrl() {

        assertTrue(JdbcUrlParser.isJdbcUrl("jdbc:mysql://localhost/db"));
        assertTrue(JdbcUrlParser.isJdbcUrl("JDBC:MYSQL://localhost/db"));
        assertFalse(JdbcUrlParser.isJdbcUrl("mysql://localhost/db"));
        assertFalse(JdbcUrlParser.isJdbcUrl(null));
        assertFalse(JdbcUrlParser.isJdbcUrl(""));
    }

    @Test
    void testTryParseValid() {

        JdbcUrl result = JdbcUrlParser.tryParse("jdbc:mysql://localhost/db");
        assertNotNull(result);
        assertEquals(DatabaseProduct.MYSQL, result.databaseProduct());
    }

    @Test
    void testTryParseInvalid() {

        JdbcUrl result = JdbcUrlParser.tryParse("invalid url");
        assertNull(result);
    }

    @Test
    void testTryParseNull() {

        JdbcUrl result = JdbcUrlParser.tryParse(null);
        assertNull(result);
    }

    @Test
    void testParseNullThrowsException() {

        assertThrows(IllegalArgumentException.class, () -> JdbcUrlParser.parse(null));
    }

    @Test
    void testParseBlankThrowsException() {

        assertThrows(IllegalArgumentException.class, () -> JdbcUrlParser.parse("  "));
    }

    @Test
    void testParseNonJdbcUrlThrowsException() {

        assertThrows(JdbcUrlParseException.class, () -> JdbcUrlParser.parse("mysql://localhost/db"));
    }

    @Test
    void testParseUnknownDatabaseThrowsException() {

        assertThrows(JdbcUrlParseException.class, () -> JdbcUrlParser.parse("jdbc:unknown://localhost/db"));
    }

    @Test
    void testJdbcUrlEquality() {

        String url = "jdbc:mysql://localhost:3306/mydb";
        JdbcUrl parsed1 = JdbcUrlParser.parse(url);
        JdbcUrl parsed2 = JdbcUrlParser.parse(url);

        assertEquals(parsed1, parsed2);
        assertEquals(parsed1.hashCode(), parsed2.hashCode());
    }

    @Test
    void testJdbcUrlToString() {

        String url = "jdbc:postgresql://localhost:5432/testdb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);
        String toString = parsed.toString();

        assertTrue(toString.contains("POSTGRESQL"));
        assertTrue(toString.contains("jdbc:postgresql:"));
    }

    @Test
    void testOriginalUrlPreserved() {

        String url = "jdbc:mysql://localhost:3306/mydb?useSSL=true";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(url, parsed.originalUrl());
    }

    @Test
    void testIsNetworkBased() {

        JdbcUrl mysql = JdbcUrlParser.parse("jdbc:mysql://localhost/db");
        assertTrue(mysql.isNetworkBased());
        assertFalse(mysql.isFileBased());

        JdbcUrl h2 = JdbcUrlParser.parse("jdbc:h2:mem:test");
        assertFalse(h2.isNetworkBased());
        assertTrue(h2.isFileBased());
    }

    @Test
    void testGetPrimaryHost() {

        JdbcUrl mysql = JdbcUrlParser.parse("jdbc:mysql://host1:3306,host2:3307/db");
        assertEquals("host1", mysql.getPrimaryHost().hostname());
        assertEquals(3306, mysql.getPrimaryHost().port());

        JdbcUrl sqlite = JdbcUrlParser.parse("jdbc:sqlite:test.db");
        assertNull(sqlite.getPrimaryHost());
    }

    @Test
    void parsesUppercaseUrlUnderTurkishDefaultLocale() {

        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            JdbcUrl parsed = JdbcUrlParser.parse("JDBC:SQLITE:test.db");

            assertEquals(DatabaseProduct.SQLITE, parsed.databaseProduct());
            assertEquals("test.db", parsed.databaseName());
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    // Parse errors end up in application logs, and URLs carry passwords.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "jdbc:mysql://db:password=S3Cx/shop",
                "jdbc:mysql://db/shop?=password=S3Cx",
                "jdbc:mariadb://address=(host=db)(type=password=S3Cx)/x",
                "jdbc:mariadb:password=S3Cx://db/x",
                "jdbc:postgresql://db:password=S3Cx/x",
                "jdbc:oracle:thin:@db:1521:ORCL:password=S3Cx",
                "jdbc:oracle:password=S3Cx:@db:1521:ORCL",
                "jdbc:sqlserver://db\\password=S3Cx;databaseName=x",
                "jdbc:sqlserver://db;databaseName=x;password=S3Cx;passwordS3Cx",
                "jdbc:h2:mem:x;password=S3Cx;S3Cx",
                "jdbc:h2:tcp://db:password=S3Cx/x",
                "jdbc:hsqldb:hsql://[::1]password=S3Cx/x",
                "jdbc:hsqldb:hsql://db:password=S3Cx/x",
                "jdbc:derby:db;user=a;passwordS3Cx",
                "jdbc:derby:password=S3Cx:x",
                "jdbc:derby://db:password=S3Cx/x",
                " jdbc:sqlite:t.db?password=S3Cx"
            })
    void keepsUrlTextOutOfParseErrorMessage(String url) {

        JdbcUrlParseException e = assertThrows(JdbcUrlParseException.class, () -> JdbcUrlParser.parse(url));

        assertFalse(e.getMessage().toLowerCase(Locale.ROOT).contains("s3cx"), e.getMessage());
        assertEquals(url, e.getJdbcUrl());
    }

    @Test
    void keepsUrlOutOfDebugLog() {

        Logger logger = (Logger) LoggerFactory.getLogger(JdbcUrlParser.class);
        Level level = logger.getLevel();
        boolean additive = logger.isAdditive();
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        try {
            JdbcUrlParser.parse("jdbc:postgresql://db/x?password=S3Cx");
            JdbcUrlParser.tryParse("jdbc:postgresql://db:S3Cx/x");
        } finally {
            logger.detachAppender(events);
            logger.setLevel(level);
            logger.setAdditive(additive);
        }

        assertFalse(events.list.isEmpty());
        events.list.forEach(event -> {
            assertFalse(event.getFormattedMessage().contains("S3Cx"), event.getFormattedMessage());
            for (IThrowableProxy cause = event.getThrowableProxy(); cause != null; cause = cause.getCause()) {
                assertFalse(String.valueOf(cause.getMessage()).contains("S3Cx"), cause.getMessage());
            }
        });
    }
}
