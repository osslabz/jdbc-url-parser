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
 * Tests for HSQLDB (HyperSQL) JDBC URL parsing.
 */
class HSQLDBParserTest {

    @Test
    void testHSQLDBInMemory() {

        String url = "jdbc:hsqldb:mem:testdb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.HSQLDB, parsed.databaseProduct());
        assertEquals("jdbc:hsqldb:", parsed.protocol());
        assertTrue(parsed.isFileBased());
        assertEquals("testdb", parsed.databaseName());
        assertEquals("MEMORY", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testHSQLDBFile() {

        String url = "jdbc:hsqldb:file:/opt/db/testdb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("/opt/db/testdb", parsed.databaseName());
        assertEquals("FILE", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testHSQLDBImpliedFile() {

        String url = "jdbc:hsqldb:/data/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("/data/mydb", parsed.databaseName());
        assertEquals("FILE", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testHSQLDBResource() {

        String url = "jdbc:hsqldb:res:/org/mydatabase/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("/org/mydatabase/mydb", parsed.databaseName());
        assertEquals("RESOURCE", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testHSQLDBServer() {

        String url = "jdbc:hsqldb:hsql://localhost:9001/testdb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertTrue(parsed.isNetworkBased());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(9001, parsed.hosts().get(0).port());
        assertEquals("testdb", parsed.databaseName());
        assertEquals("HSQL", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testHSQLDBServerSSL() {

        String url = "jdbc:hsqldb:hsqls://dbserver:9002/production";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertTrue(parsed.isNetworkBased());
        assertEquals("dbserver", parsed.hosts().get(0).hostname());
        assertEquals(9002, parsed.hosts().get(0).port());
        assertEquals("production", parsed.databaseName());
        assertEquals("HSQLS", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testHSQLDBHTTP() {

        String url = "jdbc:hsqldb:http://localhost:8080/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertTrue(parsed.isNetworkBased());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(8080, parsed.hosts().get(0).port());
        assertEquals("mydb", parsed.databaseName());
        assertEquals("HTTP", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testHSQLDBHTTPS() {

        String url = "jdbc:hsqldb:https://secure.example.com:8443/securedb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertTrue(parsed.isNetworkBased());
        assertEquals("secure.example.com", parsed.hosts().get(0).hostname());
        assertEquals(8443, parsed.hosts().get(0).port());
        assertEquals("securedb", parsed.databaseName());
        assertEquals("HTTPS", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testHSQLDBFileWithProperties() {

        String url = "jdbc:hsqldb:file:testdb;shutdown=true;hsqldb.tx=mvcc";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("testdb", parsed.databaseName());
        assertEquals("true", parsed.getPropertyValue("shutdown"));
        assertEquals("mvcc", parsed.getPropertyValue("hsqldb.tx"));
    }

    @Test
    void testHSQLDBInMemoryWithProperties() {

        String url = "jdbc:hsqldb:mem:testdb?shutdown=true";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("testdb", parsed.databaseName());
        assertEquals("true", parsed.getPropertyValue("shutdown"));
    }

    static Stream<Arguments> defaultPortCases() {

        return Stream.of(
                Arguments.of("jdbc:hsqldb:hsql://db/x", List.of(host("db", 9001)), "x", props("MODE", derived("HSQL"))),
                Arguments.of(
                        "jdbc:hsqldb:hsqls://db/x", List.of(host("db", 554)), "x", props("MODE", derived("HSQLS"))),
                Arguments.of("jdbc:hsqldb:http://db/x", List.of(host("db", 80)), "x", props("MODE", derived("HTTP"))),
                Arguments.of(
                        "jdbc:hsqldb:https://db/x", List.of(host("db", 443)), "x", props("MODE", derived("HTTPS"))));
    }

    @ParameterizedTest
    @MethodSource("defaultPortCases")
    void defaultPortIsReported(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }
}
