package net.osslabz.jdbc;

import static net.osslabz.jdbc.ParsedUrls.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
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

        assertParsed(
                "jdbc:hsqldb:mem:testdb?shutdown=true",
                List.of(),
                "testdb?shutdown=true",
                props("MODE", derived("MEMORY")));
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

    static Stream<Arguments> driverReadings() {

        return Stream.of(
                Arguments.of(
                        "jdbc:hsqldb:mem:testdb;shutdown=true",
                        List.of(),
                        "testdb",
                        props("shutdown", path("true"), "MODE", derived("MEMORY"))),
                Arguments.of("jdbc:hsqldb:mem:x;", List.of(), "x", props("MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:TestDb?user=ZUSR&password=S3Cx",
                        List.of(),
                        "testdb",
                        props("user", query("ZUSR"), "password", query("S3Cx"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:file:/tmp/x?user=ZUSR&password=S3Cx;shutdown=true",
                        List.of(),
                        "/tmp/x",
                        props(
                                "user",
                                query("ZUSR"),
                                "password",
                                query("S3Cx"),
                                "shutdown",
                                path("true"),
                                "MODE",
                                derived("FILE"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:TestDb?password=a&user=b",
                        List.of(),
                        "testdb?password=a&user=b",
                        props("MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:x&password=S3Cx",
                        List.of(),
                        "x",
                        props("password", query("S3Cx"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:x?user=a?user=b",
                        List.of(),
                        "x",
                        props("user", query("a?user=b"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:TestDb;USER=ZUSR",
                        List.of(),
                        "testdb",
                        props("USER", path("ZUSR"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:TestDb; shutdown = true ;a=b",
                        List.of(),
                        "testdb",
                        props("shutdown", path("true"), "a", path("b"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:TestDb;a=1;a=2",
                        List.of(),
                        "testdb",
                        props("a", path("2"), "MODE", derived("MEMORY"))),
                Arguments.of("jdbc:hsqldb:mem:TestDb;novalue", List.of(), "testdb", props("MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:TestDb;sql.syntax_pgs=true&x=y",
                        List.of(),
                        "testdb",
                        props("sql.syntax_pgs", path("true&x=y"), "MODE", derived("MEMORY"))),
                Arguments.of("jdbc:hsqldb:mem:x;MODE=MySQL", List.of(), "x", props("MODE", path("MySQL"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:x;mode=Oracle",
                        List.of(),
                        "x",
                        props("mode", path("Oracle"), "MODE", derived("MEMORY"))),
                Arguments.of("jdbc:hsqldb:.", List.of(), ".", props("MODE", derived("MEMORY"))),
                Arguments.of("jdbc:hsqldb:Db", List.of(), "Db", props("MODE", derived("FILE"))),
                Arguments.of("jdbc:hsqldb:file:~/Db", List.of(), "~/Db", props("MODE", derived("FILE"))),
                Arguments.of("jdbc:hsqldb:res:org/x/Db", List.of(), "/org/x/Db", props("MODE", derived("RESOURCE"))),
                Arguments.of("jdbc:hsqldb:MEM:x", List.of(), "x", props("MODE", derived("MEMORY"))),
                Arguments.of("jdbc:HSQLDB:mem:x", List.of(), "x", props("MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql://DB/Xdb", List.of(host("db", 9001)), "xdb", props("MODE", derived("HSQL"))),
                Arguments.of("jdbc:hsqldb:HSQL://db/x", List.of(host("db", 9001)), "x", props("MODE", derived("HSQL"))),
                Arguments.of("jdbc:hsqldb:hsql://db", List.of(host("db", 9001)), "", props("MODE", derived("HSQL"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql://db/a/B", List.of(host("db", 9001)), "B", props("MODE", derived("HSQL"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql:///x", List.of(host("localhost", 9001)), "x", props("MODE", derived("HSQL"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql://db/Xdb;ifexists=true",
                        List.of(host("db", 9001)),
                        "xdb",
                        props("ifexists", path("true"), "MODE", derived("HSQL"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql://db/Xdb?user=ZUSR&password=S3Cx",
                        List.of(host("db", 9001)),
                        "xdb",
                        props("user", query("ZUSR"), "password", query("S3Cx"), "MODE", derived("HSQL"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql://db/x?shutdown=true",
                        List.of(host("db", 9001)),
                        "x?shutdown=true",
                        props("MODE", derived("HSQL"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql://db/x;MODE=Oracle",
                        List.of(host("db", 9001)),
                        "x",
                        props("MODE", path("Oracle"))),
                Arguments.of(
                        "jdbc:hsqldb:hsqls://db:555/x", List.of(host("db", 555)), "x", props("MODE", derived("HSQLS"))),
                Arguments.of(
                        "jdbc:hsqldb:https://db:8443/x",
                        List.of(host("db", 8443)),
                        "x",
                        props("MODE", derived("HTTPS"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql://[::1]/x",
                        List.of(host("[::1]", 9001)),
                        "x",
                        props("MODE", derived("HSQL"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:x?USER=a&PASSWORD=b",
                        List.of(),
                        "x",
                        props("USER", query("a"), "PASSWORD", query("b"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql://db/x?USER=a",
                        List.of(host("db", 9001)),
                        "x",
                        props("USER", query("a"), "MODE", derived("HSQL"))),
                Arguments.of("jdbc:hsqldb:file:x?USER=a", List.of(), "x?USER=a", props("MODE", derived("FILE"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:x?user=a;user=b",
                        List.of(),
                        "x",
                        props("user", query("a"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:mem:?user=a", List.of(), "", props("user", query("a"), "MODE", derived("MEMORY"))),
                Arguments.of("jdbc:hsqldb:mem:x?uſer=a", List.of(), "x?uſer=a", props("MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql://;", List.of(host("localhost", 9001)), "", props("MODE", derived("HSQL"))),
                Arguments.of(
                        "jdbc:hsqldb:hsql://[::ABCD]:7/x",
                        List.of(host("[::abcd]", 7)),
                        "x",
                        props("MODE", derived("HSQL"))));
    }

    @ParameterizedTest
    @MethodSource("driverReadings")
    void readsUrlAsDriverDoes(String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<String> driverRejections() {

        return Stream.of(
                "jdbc:hsqldb:mem:",
                "jdbc:hsqldb:",
                "jdbc:hsqldb:mem:;shutdown=true",
                "jdbc:hsqldb:alias:x",
                "jdbc:hsqldb:hsql://a,b/x",
                "jdbc:hsqldb:hsql://ZUSR:S3Cx@db/x",
                "jdbc:hsqldb:hsql://db:abc/x",
                "jdbc:hsqldb:hsql://db:/x",
                "jdbc:hsqldb:hsql://a:9001,b:9002/x",
                "jdbc:hsqldb:hsql://[::1",
                "jdbc:hsqldb:hsql://",
                "jdbc:hsqldb:res:",
                "jdbc:hsqldb:hsql://db:99999/x",
                "jdbc:hsqldb:hsql://:9001/x",
                "jdbc:hsqldb:hsql://[::1]x/y",
                "jdbc:hsqldb:hsql://[]/x",
                "jdbc:hsqldb:hsql://db?user=a/x");
    }

    @ParameterizedTest
    @MethodSource("driverRejections")
    void rejectsUrlDriverRejects(String url) {

        assertRejected(url);
    }

    @Test
    void lowercasesMemoryNameUnderTurkishLocale() {

        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertEquals("title", JdbcUrlParser.parse("jdbc:hsqldb:mem:TITLE").databaseName());
        } finally {
            Locale.setDefault(original);
        }
    }
}
