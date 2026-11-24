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

    static Stream<Arguments> defaultPortCases() {

        return Stream.of(
                Arguments.of("jdbc:derby://db/x", List.of(host("db", 1527)), "x", props("MODE", derived("NETWORK"))));
    }

    @ParameterizedTest
    @MethodSource("defaultPortCases")
    void defaultPortIsReported(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<Arguments> embeddedDriverReadings() {

        return Stream.of(
                Arguments.of("jdbc:derby:db;", List.of(), "db", props("MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:memory:x;create=true",
                        List.of(),
                        "x",
                        props("create", path("true"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:derby:directory:/data/db", List.of(), "/data/db", props("MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:classpath:/org/x/db",
                        List.of(),
                        "classpath:/org/x/db",
                        props("MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:jar:(/data/a.jar)db",
                        List.of(),
                        "jar:(/data/a.jar)db",
                        props("MODE", derived("EMBEDDED"))),
                Arguments.of("jdbc:derby:C:\\data\\db", List.of(), "C:\\data\\db", props("MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:;databaseName=x;create=true",
                        List.of(),
                        "x",
                        props("databaseName", path("x"), "create", path("true"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:db;databaseName=y",
                        List.of(),
                        "db",
                        props("databaseName", path("y"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby: ;databaseName=x",
                        List.of(),
                        "",
                        props("databaseName", path("x"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:;create=true",
                        List.of(),
                        "",
                        props("create", path("true"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby: db ;create=true",
                        List.of(),
                        "db",
                        props("create", path("true"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:db;create=true;create=false",
                        List.of(),
                        "db",
                        props("create", path("false"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:db; create = true ;user=a",
                        List.of(),
                        "db",
                        props("create", path("true"), "user", path("a"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:db;Create=true",
                        List.of(),
                        "db",
                        props("Create", path("true"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:db;a=b=c", List.of(), "db", props("a", path("b=c"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:db;password=a&instanceName=S3Cx",
                        List.of(),
                        "db",
                        props("password", path("a&instanceName=S3Cx"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:db?create=true", List.of(), "db?create=true", props("MODE", derived("EMBEDDED"))),
                // Derby rejects the value as no boolean; option values are not validated [G7].
                Arguments.of(
                        "jdbc:derby:db;create=true&user=a",
                        List.of(),
                        "db",
                        props("create", path("true&user=a"), "MODE", derived("EMBEDDED"))),
                Arguments.of("jdbc:derby:db;MODE=x", List.of(), "db", props("MODE", path("x"))),
                Arguments.of(
                        "jdbc:derby:db;mode=x", List.of(), "db", props("mode", path("x"), "MODE", derived("EMBEDDED"))),
                Arguments.of(
                        "jdbc:derby:;databaseName=memory:x",
                        List.of(),
                        "x",
                        props("databaseName", path("memory:x"), "MODE", derived("MEMORY"))));
    }

    @ParameterizedTest
    @MethodSource("embeddedDriverReadings")
    void readsEmbeddedUrlAsDriverDoes(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<Arguments> clientDriverReadings() {

        return Stream.of(
                Arguments.of(
                        "jdbc:derby://db:1528/x;create=true",
                        List.of(host("db", 1528)),
                        "x",
                        props("create", path("true"), "MODE", derived("NETWORK"))),
                Arguments.of("jdbc:derby://db:0/x", List.of(host("db", 1527)), "x", props("MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://DB.Example.COM:1527/X",
                        List.of(host("DB.Example.COM", 1527)),
                        "X",
                        props("MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://db:1527//data/x",
                        List.of(host("db", 1527)),
                        "/data/x",
                        props("MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://db/dir/x", List.of(host("db", 1527)), "dir/x", props("MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://db/x;User=a;user=b",
                        List.of(host("db", 1527)),
                        "x",
                        props("User", path("a"), "user", path("b"), "MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://db:1527/shop;create=true;databaseName=S3Cx",
                        List.of(host("db", 1527)),
                        "shop",
                        props("create", path("true"), "databaseName", path("S3Cx"), "MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://db/x?create=true",
                        List.of(host("db", 1527)),
                        "x?create=true",
                        props("MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://db/;databaseName=y",
                        List.of(host("db", 1527)),
                        "y",
                        props("databaseName", path("y"), "MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://db/\t;databaseName=y",
                        List.of(host("db", 1527)),
                        "y",
                        props("databaseName", path("y"), "MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://db/x y;a=b",
                        List.of(host("db", 1527)),
                        "x y",
                        props("a", path("b"), "MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://db/x\ty;a=b",
                        List.of(host("db", 1527)),
                        "x",
                        props("a", path("b"), "MODE", derived("NETWORK"))),
                Arguments.of("jdbc:derby://db/ zz", List.of(host("db", 1527)), "zz", props("MODE", derived("NETWORK"))),
                Arguments.of(
                        "jdbc:derby://db/x;mode=y",
                        List.of(host("db", 1527)),
                        "x",
                        props("mode", path("y"), "MODE", derived("NETWORK"))),
                Arguments.of("jdbc:derby://db/x;MODE=y", List.of(host("db", 1527)), "x", props("MODE", path("y"))));
    }

    @ParameterizedTest
    @MethodSource("clientDriverReadings")
    void readsClientUrlAsDriverDoes(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<String> driverRejections() {

        return Stream.of(
                "jdbc:derby:db;create",
                "jdbc:derby:db; ;a=b",
                "jdbc:DERBY:db",
                "jdbc:derby:Memory:x",
                "jdbc:derby:foo:bar",
                "jdbc:derby:ab:x",
                "jdbc:derby:;databaseName=foo:bar",
                "jdbc:derby:net://db/x",
                "jdbc:derby://db:abc/x",
                "jdbc:derby://db:/x",
                "jdbc:derby://db:1527:5/x",
                "jdbc:derby://db:99999999999/x",
                "jdbc:derby://db",
                "jdbc:derby://db:1527",
                "jdbc:derby://db/",
                "jdbc:derby://db/;create=true",
                "jdbc:derby://db/;databaseName=",
                "jdbc:derby://:1527/x",
                "jdbc:derby:///x",
                "jdbc:derby://ZUSR:S3Cx@db:1527/x",
                "jdbc:derby://[::1/x",
                "jdbc:derby://db/x;create",
                "jdbc:derby://db/x; ;a=b",
                "jdbc:DERBY://db/x");
    }

    @ParameterizedTest
    @MethodSource("driverRejections")
    void rejectsUrlDriverRejects(String url) {

        assertRejected(url);
    }

    // The driver connects to a host named "ZUSR@db"; the hostname rule rejects it [G5].
    @Test
    void rejectsCredentialsInHost() {

        assertRejected("jdbc:derby://ZUSR@db/x");
    }

    // The driver fails on port ":1]:1527"; the library rejects the hostname "[" first [G5].
    @Test
    void rejectsBracketedIpv6Host() {

        assertRejected("jdbc:derby://[::1]:1527/x");
    }

    // The driver accepts ports outside 0..65535 and fails only when it opens the socket [G6].
    @ParameterizedTest
    @MethodSource("portsOutsideRange")
    void rejectsPortOutsideRange(String url) {

        assertRejected(url);
    }

    static Stream<String> portsOutsideRange() {

        return Stream.of("jdbc:derby://db:-1/x", "jdbc:derby://db:99999/x");
    }
}
