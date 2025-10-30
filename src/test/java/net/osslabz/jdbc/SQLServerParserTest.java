package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;


/**
 * Tests for Microsoft SQL Server JDBC URL parsing.
 */
class SQLServerParserTest {

    @Test
    void testSimpleSQLServerUrl() {

        String url = "jdbc:sqlserver://localhost:1433;databaseName=mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.SQLSERVER, parsed.databaseProduct());
        assertEquals("jdbc:sqlserver:", parsed.protocol());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(1433, parsed.hosts().get(0).port());
        assertEquals("mydb", parsed.databaseName());
    }


    @Test
    void testSQLServerWithProperties() {

        String url = "jdbc:sqlserver://localhost:1433;databaseName=testdb;encrypt=true;trustServerCertificate=false";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("testdb", parsed.databaseName());
        assertEquals("true", parsed.getPropertyValue("encrypt"));
        assertEquals("false", parsed.getPropertyValue("trustServerCertificate"));
    }


    @Test
    void testSQLServerWithInstanceName() {

        String url = "jdbc:sqlserver://localhost\\SQLEXPRESS:1433;databaseName=mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals("SQLEXPRESS", parsed.hosts().get(0).instanceName());
        assertEquals(1433, parsed.hosts().get(0).port());
        assertEquals("mydb", parsed.databaseName());
    }


    @Test
    void testSQLServerInstanceWithoutPort() {

        String url = "jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals("SQLEXPRESS", parsed.hosts().get(0).instanceName());
        assertEquals("mydb", parsed.databaseName());
    }


    @Test
    void testSQLServerDefaultPort() {

        String url = "jdbc:sqlserver://dbserver;databaseName=production";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("dbserver", parsed.hosts().get(0).hostname());
        assertNull(parsed.hosts().get(0).port());
        assertEquals("production", parsed.databaseName());
    }


    @Test
    void testSQLServerWithDatabase() {

        String url = "jdbc:sqlserver://localhost:1433;database=testdb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("testdb", parsed.databaseName());
    }


    @Test
    void testSQLServerComplexUrl() {

        String url = "jdbc:sqlserver://myserver:1433;databaseName=AdventureWorks;user=sa;password=secret;encrypt=true;trustServerCertificate=true;loginTimeout=30";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("AdventureWorks", parsed.databaseName());
        assertEquals("sa", parsed.getPropertyValue("user"));
        assertEquals("secret", parsed.getPropertyValue("password"));
        assertEquals("true", parsed.getPropertyValue("encrypt"));
        assertEquals("30", parsed.getPropertyValue("loginTimeout"));
    }


    @Test
    void testSQLServerIntegratedSecurity() {

        String url = "jdbc:sqlserver://localhost;databaseName=mydb;integratedSecurity=true";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("mydb", parsed.databaseName());
        assertEquals("true", parsed.getPropertyValue("integratedSecurity"));
    }
}
