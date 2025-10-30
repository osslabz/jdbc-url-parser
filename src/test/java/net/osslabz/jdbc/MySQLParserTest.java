package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


/**
 * Tests for MySQL and MariaDB JDBC URL parsing.
 */
class MySQLParserTest {

    @Test
    void testSimpleMySQLUrl() {

        String url = "jdbc:mysql://localhost:3306/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.MYSQL, parsed.databaseProduct());
        assertEquals("jdbc:mysql:", parsed.protocol());
        assertEquals(1, parsed.hosts().size());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(3306, parsed.hosts().get(0).port());
        assertEquals("mydb", parsed.databaseName());
        assertTrue(parsed.properties().isEmpty());
    }


    @Test
    void testMySQLWithProperties() {

        String url = "jdbc:mysql://localhost:3306/mydb?useSSL=true&serverTimezone=UTC";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.MYSQL, parsed.databaseProduct());
        assertEquals("mydb", parsed.databaseName());
        assertEquals(2, parsed.properties().size());
        assertEquals("true", parsed.getPropertyValue("useSSL"));
        assertEquals("UTC", parsed.getPropertyValue("serverTimezone"));
    }


    @Test
    void testMySQLMultiHost() {

        String url = "jdbc:mysql://host1:3306,host2:3307/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.MYSQL, parsed.databaseProduct());
        assertEquals(2, parsed.hosts().size());
        assertEquals("host1", parsed.hosts().get(0).hostname());
        assertEquals(3306, parsed.hosts().get(0).port());
        assertEquals("host2", parsed.hosts().get(1).hostname());
        assertEquals(3307, parsed.hosts().get(1).port());
        assertEquals("mydb", parsed.databaseName());
    }


    @Test
    void testMySQLWithoutPort() {

        String url = "jdbc:mysql://localhost/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertNull(parsed.hosts().get(0).port());
    }


    @Test
    void testMariaDBUrl() {

        String url = "jdbc:mariadb://db.example.com:3307/production";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.MARIADB, parsed.databaseProduct());
        assertEquals("jdbc:mariadb:", parsed.protocol());
        assertEquals("db.example.com", parsed.hosts().get(0).hostname());
        assertEquals(3307, parsed.hosts().get(0).port());
        assertEquals("production", parsed.databaseName());
    }


    @Test
    void testMariaDBWithProperties() {

        String url = "jdbc:mariadb://localhost:3306/testdb?user=root&password=secret";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.MARIADB, parsed.databaseProduct());
        assertEquals("root", parsed.getPropertyValue("user"));
        assertEquals("secret", parsed.getPropertyValue("password"));
    }


    @Test
    void testMySQLEmptyDatabase() {

        String url = "jdbc:mysql://localhost:3306/";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("", parsed.databaseName());
    }


    @Test
    void testMySQLComplexUrl() {

        String url = "jdbc:mysql://primary:3306,replica1:3306,replica2:3306/mydb?useSSL=true&rewriteBatchedStatements=true";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(3, parsed.hosts().size());
        assertEquals("primary", parsed.hosts().get(0).hostname());
        assertEquals("replica1", parsed.hosts().get(1).hostname());
        assertEquals("replica2", parsed.hosts().get(2).hostname());
        assertEquals("true", parsed.getPropertyValue("useSSL"));
        assertEquals("true", parsed.getPropertyValue("rewriteBatchedStatements"));
    }
}
