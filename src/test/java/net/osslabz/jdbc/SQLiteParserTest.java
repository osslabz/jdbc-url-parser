package net.osslabz.jdbc;

import static net.osslabz.jdbc.ParsedUrls.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for SQLite JDBC URL parsing.
 */
class SQLiteParserTest {

    @Test
    void testSQLiteSimpleFile() {

        String url = "jdbc:sqlite:test.db";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.SQLITE, parsed.databaseProduct());
        assertEquals("jdbc:sqlite:", parsed.protocol());
        assertTrue(parsed.isFileBased());
        assertEquals("test.db", parsed.databaseName());
        assertEquals("FILE", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testSQLiteAbsolutePath() {

        String url = "jdbc:sqlite:/data/databases/mydb.db";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("/data/databases/mydb.db", parsed.databaseName());
        assertEquals("FILE", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testSQLiteWindowsPath() {

        String url = "jdbc:sqlite:C:\\databases\\test.db";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("C:\\databases\\test.db", parsed.databaseName());
        assertEquals("FILE", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testSQLiteInMemory() {

        String url = "jdbc:sqlite::memory:";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(":memory:", parsed.databaseName());
        assertEquals("MEMORY", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testSQLiteInMemoryAlternate() {

        String url = "jdbc:sqlite:memory:";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("memory:", parsed.databaseName());
        assertEquals("FILE", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testSQLiteWithProperties() {

        String url = "jdbc:sqlite:test.db?foreign_keys=true&journal_mode=WAL";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("test.db", parsed.databaseName());
        assertEquals("true", parsed.getPropertyValue("foreign_keys"));
        assertEquals("WAL", parsed.getPropertyValue("journal_mode"));
    }

    @Test
    void testSQLiteRelativePath() {

        String url = "jdbc:sqlite:../data/mydb.db";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("../data/mydb.db", parsed.databaseName());
    }

    @Test
    void testSQLiteEmptyPath() {

        String url = "jdbc:sqlite:";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("", parsed.databaseName());
    }

    @Test
    void testSQLiteComplexPath() {

        String url = "jdbc:sqlite:/var/lib/app/data/application.db?cache=shared&mode=ro";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("/var/lib/app/data/application.db", parsed.databaseName());
        assertEquals("shared", parsed.getPropertyValue("cache"));
        assertEquals("ro", parsed.getPropertyValue("mode"));
    }

    static Stream<Arguments> driverReadings() {

        return Stream.of(
                Arguments.of(
                        "jdbc:sqlite::memory:?foreign_keys=true",
                        ":memory:",
                        props("foreign_keys", query("true"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:sqlite:?foreign_keys=true",
                        "",
                        props("foreign_keys", query("true"), "MODE", derived("FILE"))),
                Arguments.of("jdbc:sqlite:x.db?", "x.db", props("MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:file::memory:?cache=shared",
                        "file::memory:",
                        props("cache", query("shared"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:file:test.db?mode=ro",
                        "file:test.db",
                        props("mode", query("ro"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:t.db?foreign_keys=true&foreign_keys=false",
                        "t.db",
                        props("foreign_keys", query("false"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:t.db?Foreign_Keys=true",
                        "t.db",
                        props("Foreign_Keys", query("true"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:t.db? foreign_keys = true & busy_timeout=9 ",
                        "t.db",
                        props("foreign_keys", query("true"), "busy_timeout", query("9"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:t.db?&&foreign_keys=true&",
                        "t.db",
                        props("foreign_keys", query("true"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:t.db?foreign_keys=tr%75e",
                        "t.db", props("foreign_keys", query("tr%75e"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:t.db?journal_mode=WAL#x",
                        "t.db",
                        props("journal_mode", query("WAL#x"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:/data/x.db?password=a;instanceName=S3Cx",
                        "/data/x.db",
                        props("password", query("a;instanceName=S3Cx"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:x.db?password=a=b",
                        "x.db",
                        props("password", query("a=b"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:C:\\data\\x.db?foreign_keys=true",
                        "C:\\data\\x.db",
                        props("foreign_keys", query("true"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite://ZUSR:S3Cx@/data/x.db", "//ZUSR:S3Cx@/data/x.db", props("MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite::resource:org/x/db.sqlite",
                        ":resource:org/x/db.sqlite",
                        props("MODE", derived("FILE"))),
                Arguments.of("jdbc:sqlite:t.db ", "t.db", props("MODE", derived("FILE"))),
                Arguments.of("jdbc:sqlite::memory: ", ":memory:", props("MODE", derived("MEMORY"))),
                Arguments.of("Jdbc:SQLite:t.db", "t.db", props("MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:t.db?a=1&flag&b=2",
                        "t.db",
                        props("a", query("1"), "flag", query(""), "b", query("2"), "MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:sqlite:t.db?a=1& &b=2",
                        "t.db",
                        props("a", query("1"), "b", query("2"), "MODE", derived("FILE"))),
                Arguments.of("jdbc:sqlite::memory:?MODE=FILE", ":memory:", props("MODE", query("FILE"))),
                Arguments.of("jdbc:sqlite:t.db?mode=x", "t.db", props("mode", query("x"), "MODE", derived("FILE"))));
    }

    @ParameterizedTest
    @MethodSource("driverReadings")
    void readsUrlAsDriverDoes(String url, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, List.of(), databaseName, properties);
    }

    // The driver reads the URL as given and rejects leading whitespace, the prefix check runs before its trim.
    static Stream<String> driverRejections() {

        return Stream.of("jdbc:sqlite", " jdbc:sqlite:t.db", "jdbc:sqlit:t.db");
    }

    @ParameterizedTest
    @MethodSource("driverRejections")
    void rejectsUrlDriverRejects(String url) {

        assertRejected(url);
    }
}
