package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


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

        assertEquals(":memory:", parsed.databaseName());
        assertEquals("MEMORY", parsed.getPropertyValue("MODE"));
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
}
