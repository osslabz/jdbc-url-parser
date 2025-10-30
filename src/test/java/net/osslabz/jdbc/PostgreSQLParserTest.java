package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;


/**
 * Tests for PostgreSQL JDBC URL parsing.
 */
class PostgreSQLParserTest {

    @Test
    void testSimplePostgreSQLUrl() {

        String url = "jdbc:postgresql://localhost:5432/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.POSTGRESQL, parsed.databaseProduct());
        assertEquals("jdbc:postgresql:", parsed.protocol());
        assertEquals(1, parsed.hosts().size());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(5432, parsed.hosts().get(0).port());
        assertEquals("mydb", parsed.databaseName());
    }


    @Test
    void testPostgreSQLWithProperties() {

        String url = "jdbc:postgresql://localhost:5432/testdb?ssl=true&sslmode=require";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("testdb", parsed.databaseName());
        assertEquals("true", parsed.getPropertyValue("ssl"));
        assertEquals("require", parsed.getPropertyValue("sslmode"));
    }


    @Test
    void testPostgreSQLWithoutPort() {

        String url = "jdbc:postgresql://dbserver/production";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("dbserver", parsed.hosts().get(0).hostname());
        assertNull(parsed.hosts().get(0).port());
        assertEquals("production", parsed.databaseName());
    }


    @Test
    void testPostgreSQLMultiHost() {

        String url = "jdbc:postgresql://host1:5432,host2:5433/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(2, parsed.hosts().size());
        assertEquals("host1", parsed.hosts().get(0).hostname());
        assertEquals(5432, parsed.hosts().get(0).port());
        assertEquals("host2", parsed.hosts().get(1).hostname());
        assertEquals(5433, parsed.hosts().get(1).port());
    }


    @Test
    void testPostgreSQLWithSchema() {

        String url = "jdbc:postgresql://localhost/mydb?currentSchema=public";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("mydb", parsed.databaseName());
        assertEquals("public", parsed.getPropertyValue("currentSchema"));
    }


    @Test
    void testPostgreSQLComplexProperties() {

        String url = "jdbc:postgresql://localhost:5432/testdb?user=postgres&password=secret&ssl=true&sslmode=verify-full&sslcert=/path/to/cert";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(5, parsed.properties().size());
        assertEquals("postgres", parsed.getPropertyValue("user"));
        assertEquals("secret", parsed.getPropertyValue("password"));
        assertEquals("true", parsed.getPropertyValue("ssl"));
        assertEquals("verify-full", parsed.getPropertyValue("sslmode"));
        assertEquals("/path/to/cert", parsed.getPropertyValue("sslcert"));
    }


    @Test
    void testPostgreSQLIPv6() {

        String url = "jdbc:postgresql://[::1]:5432/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("[::1]", parsed.hosts().get(0).hostname());
        assertEquals(5432, parsed.hosts().get(0).port());
    }
}
