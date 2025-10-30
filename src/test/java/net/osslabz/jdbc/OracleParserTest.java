package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


/**
 * Tests for Oracle JDBC URL parsing.
 */
class OracleParserTest {

    @Test
    void testOracleThinWithSID() {

        String url = "jdbc:oracle:thin:@localhost:1521:ORCL";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.ORACLE, parsed.databaseProduct());
        assertEquals("jdbc:oracle:", parsed.protocol());
        assertEquals(1, parsed.hosts().size());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(1521, parsed.hosts().get(0).port());
        assertEquals("ORCL", parsed.databaseName());
        assertEquals("ORCL", parsed.getPropertyValue("SID"));
    }


    @Test
    void testOracleThinWithServiceName() {

        String url = "jdbc:oracle:thin:@//localhost:1521/XEPDB1";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.ORACLE, parsed.databaseProduct());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(1521, parsed.hosts().get(0).port());
        assertEquals("XEPDB1", parsed.databaseName());
        assertEquals("XEPDB1", parsed.getPropertyValue("SERVICE_NAME"));
    }


    @Test
    void testOracleWithServiceNameNoPort() {

        String url = "jdbc:oracle:thin:@dbserver.example.com:1522/myservice";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("dbserver.example.com", parsed.hosts().get(0).hostname());
        assertEquals(1522, parsed.hosts().get(0).port());
        assertEquals("myservice", parsed.databaseName());
    }


    @Test
    void testOracleDescriptorFormat() {

        String url = "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=localhost)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=myservice)))";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.ORACLE, parsed.databaseProduct());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(1521, parsed.hosts().get(0).port());
        assertEquals("myservice", parsed.databaseName());
        assertTrue(parsed.properties().containsKey("DESCRIPTOR"));
    }


    @Test
    void testOracleDescriptorWithSID() {

        String url = "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=dbhost)(PORT=1521))(CONNECT_DATA=(SID=ORCL)))";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("dbhost", parsed.hosts().get(0).hostname());
        assertEquals(1521, parsed.hosts().get(0).port());
        assertEquals("ORCL", parsed.databaseName());
        assertEquals("ORCL", parsed.getPropertyValue("SID"));
    }


    @Test
    void testOracleDescriptorCaseInsensitive() {

        String url = "jdbc:oracle:thin:@(description=(address=(protocol=TCP)(host=myhost)(port=1522))(connect_data=(service_name=PROD)))";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("myhost", parsed.hosts().get(0).hostname());
        assertEquals(1522, parsed.hosts().get(0).port());
        assertEquals("PROD", parsed.databaseName());
    }


    @Test
    void testOracleOCIDriver() {

        String url = "jdbc:oracle:oci:@//localhost:1521/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.ORACLE, parsed.databaseProduct());
        assertEquals("mydb", parsed.databaseName());
    }
}
