package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


/**
 * Tests for Apache Derby JDBC URL parsing.
 */
class DerbyParserTest {

    @Test
    void testDerbyEmbedded() {

        String url = "jdbc:derby:mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.DERBY, parsed.databaseProduct());
        assertEquals("jdbc:derby:", parsed.protocol());
        assertTrue(parsed.isFileBased());
        assertEquals("mydb", parsed.databaseName());
        assertEquals("EMBEDDED", parsed.getPropertyValue("MODE"));
    }


    @Test
    void testDerbyEmbeddedWithCreate() {

        String url = "jdbc:derby:testdb;create=true";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("testdb", parsed.databaseName());
        assertEquals("true", parsed.getPropertyValue("create"));
        assertEquals("EMBEDDED", parsed.getPropertyValue("MODE"));
    }


    @Test
    void testDerbyEmbeddedPath() {

        String url = "jdbc:derby:/data/databases/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("/data/databases/mydb", parsed.databaseName());
        assertEquals("EMBEDDED", parsed.getPropertyValue("MODE"));
    }


    @Test
    void testDerbyInMemory() {

        String url = "jdbc:derby:memory:testdb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("testdb", parsed.databaseName());
        assertEquals("MEMORY", parsed.getPropertyValue("MODE"));
    }


    @Test
    void testDerbyInMemoryWithCreate() {

        String url = "jdbc:derby:memory:testdb;create=true";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("testdb", parsed.databaseName());
        assertEquals("true", parsed.getPropertyValue("create"));
        assertEquals("MEMORY", parsed.getPropertyValue("MODE"));
    }


    @Test
    void testDerbyNetwork() {

        String url = "jdbc:derby://localhost:1527/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertTrue(parsed.isNetworkBased());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(1527, parsed.hosts().get(0).port());
        assertEquals("mydb", parsed.databaseName());
        assertEquals("NETWORK", parsed.getPropertyValue("MODE"));
    }


    @Test
    void testDerbyNetworkWithProperties() {

        String url = "jdbc:derby://dbserver:1527/production;create=true;user=admin";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("dbserver", parsed.hosts().get(0).hostname());
        assertEquals(1527, parsed.hosts().get(0).port());
        assertEquals("production", parsed.databaseName());
        assertEquals("true", parsed.getPropertyValue("create"));
        assertEquals("admin", parsed.getPropertyValue("user"));
    }


    @Test
    void testDerbyMultipleProperties() {

        String url = "jdbc:derby:mydb;create=true;user=app;password=secret;territory=en_US";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("mydb", parsed.databaseName());
        assertEquals(5, parsed.properties().size()); // 4 from URL + MODE
        assertEquals("true", parsed.getPropertyValue("create"));
        assertEquals("app", parsed.getPropertyValue("user"));
        assertEquals("secret", parsed.getPropertyValue("password"));
        assertEquals("en_US", parsed.getPropertyValue("territory"));
        assertEquals("EMBEDDED", parsed.getPropertyValue("MODE"));
    }


    @Test
    void testDerbyShutdown() {

        String url = "jdbc:derby:mydb;shutdown=true";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("mydb", parsed.databaseName());
        assertEquals("true", parsed.getPropertyValue("shutdown"));
    }
}
