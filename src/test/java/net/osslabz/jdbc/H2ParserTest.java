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
 * Tests for H2 Database JDBC URL parsing.
 */
class H2ParserTest {

    @Test
    void testH2InMemory() {

        String url = "jdbc:h2:mem:testdb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.H2, parsed.databaseProduct());
        assertEquals("jdbc:h2:", parsed.protocol());
        assertTrue(parsed.isFileBased());
        assertEquals("testdb", parsed.databaseName());
        assertEquals("MEMORY", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testH2FileRelative() {

        String url = "jdbc:h2:~/test";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.H2, parsed.databaseProduct());
        assertEquals("~/test", parsed.databaseName());
        assertEquals("FILE", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testH2FileAbsolute() {

        String url = "jdbc:h2:file:/data/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("/data/mydb", parsed.databaseName());
        assertEquals("FILE", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testH2FileWithProperties() {

        String url = "jdbc:h2:~/testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("~/testdb", parsed.databaseName());
        assertEquals("MySQL", parsed.getPropertyValue("MODE"));
        assertEquals("TRUE", parsed.getPropertyValue("DATABASE_TO_LOWER"));
    }

    @Test
    void testH2TCP() {

        String url = "jdbc:h2:tcp://localhost:9092/~/testdb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.H2, parsed.databaseProduct());
        assertTrue(parsed.isNetworkBased());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(9092, parsed.hosts().get(0).port());
        assertEquals("~/testdb", parsed.databaseName());
        assertEquals("TCP", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testH2SSL() {

        String url = "jdbc:h2:ssl://dbserver:9092/~/production";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertTrue(parsed.isNetworkBased());
        assertEquals("dbserver", parsed.hosts().get(0).hostname());
        assertEquals(9092, parsed.hosts().get(0).port());
        assertEquals("~/production", parsed.databaseName());
        assertEquals("SSL", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testH2InMemoryWithOptions() {

        String url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("testdb", parsed.databaseName());
        assertEquals("-1", parsed.getPropertyValue("DB_CLOSE_DELAY"));
        assertEquals("MySQL", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testH2ImpliedFile() {

        String url = "jdbc:h2:/opt/databases/mydb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("/opt/databases/mydb", parsed.databaseName());
        assertEquals("FILE", parsed.getPropertyValue("MODE"));
    }

    @Test
    void testH2WithQuestionMarkProperties() {

        assertParsed(
                "jdbc:h2:mem:testdb?MODE=PostgreSQL&DATABASE_TO_LOWER=TRUE",
                List.of(),
                "testdb?MODE=PostgreSQL&DATABASE_TO_LOWER=TRUE",
                props("MODE", derived("MEMORY")));
    }

    static Stream<Arguments> defaultPortCases() {

        return Stream.of(
                Arguments.of("jdbc:h2:tcp://db/x", List.of(host("db", 9092)), "x", props("MODE", derived("TCP"))),
                Arguments.of("jdbc:h2:ssl://db/x", List.of(host("db", 9092)), "x", props("MODE", derived("SSL"))));
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
                        "jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                        List.of(),
                        "testdb",
                        props("MODE", path("PostgreSQL"), "DATABASE_TO_LOWER", path("TRUE"))),
                Arguments.of("jdbc:h2:mem:testdb;mode=mysql", List.of(), "testdb", props("mode", path("mysql"))),
                Arguments.of(
                        "jdbc:h2:mem:testdb;MODE=MySQL;MODE=MySQL", List.of(), "testdb", props("MODE", path("MySQL"))),
                Arguments.of("jdbc:h2:mem:x;", List.of(), "x", props("MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:h2:mem:testdb;;DB_CLOSE_DELAY=-1;",
                        List.of(),
                        "testdb",
                        props("DB_CLOSE_DELAY", path("-1"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:h2:mem:t;MODE=MySQL ;DB_CLOSE_DELAY=-1",
                        List.of(),
                        "t",
                        props("MODE", path("MySQL "), "DB_CLOSE_DELAY", path("-1"))),
                Arguments.of(
                        "jdbc:h2:mem:testdb;USER=ZUSR;PASSWORD=S3Cx",
                        List.of(),
                        "testdb",
                        props("USER", path("ZUSR"), "PASSWORD", path("S3Cx"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:h2:mem:testdb;INIT=RUNSCRIPT FROM 'x.sql'",
                        List.of(),
                        "testdb",
                        props("INIT", path("RUNSCRIPT FROM 'x.sql'"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:h2:mem:testdb;INIT=SET A=1&B=2",
                        List.of(),
                        "testdb",
                        props("INIT", path("SET A=1&B=2"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:h2:mem:t;INIT=a\\;b",
                        List.of(),
                        "t",
                        props("INIT", path("a;b"), "MODE", derived("MEMORY"))),
                Arguments.of(
                        "jdbc:h2:mem:t;INIT=a\\\\b;MODE=MySQL",
                        List.of(),
                        "t",
                        props("INIT", path("a\\b"), "MODE", path("MySQL"))),
                Arguments.of(
                        "jdbc:h2:mem:testdb;FOO=bar",
                        List.of(),
                        "testdb",
                        props("FOO", path("bar"), "MODE", derived("MEMORY"))),
                Arguments.of("jdbc:h2:mem:", List.of(), "", props("MODE", derived("MEMORY"))),
                Arguments.of("jdbc:h2:.", List.of(), "", props("MODE", derived("MEMORY"))),
                Arguments.of("jdbc:h2:file:/tmp/x", List.of(), "/tmp/x", props("MODE", derived("FILE"))),
                Arguments.of("jdbc:h2:split:/tmp/x", List.of(), "split:/tmp/x", props("MODE", derived("FILE"))),
                Arguments.of("jdbc:h2:tcp://db//x", List.of(host("db", 9092)), "/x", props("MODE", derived("TCP"))),
                Arguments.of("jdbc:h2:tcp:db/x", List.of(host("db", 9092)), "x", props("MODE", derived("TCP"))),
                Arguments.of("jdbc:h2:tcp://db/", List.of(host("db", 9092)), "", props("MODE", derived("TCP"))),
                Arguments.of(
                        "jdbc:h2:tcp://db/mem:x", List.of(host("db", 9092)), "mem:x", props("MODE", derived("TCP"))),
                Arguments.of("jdbc:h2:ssl://db:9094/x", List.of(host("db", 9094)), "x", props("MODE", derived("SSL"))),
                Arguments.of(
                        "jdbc:h2:tcp://a:9092,b:9093/x",
                        List.of(host("a", 9092), host("b", 9093)),
                        "x",
                        props("MODE", derived("TCP"))),
                Arguments.of(
                        "jdbc:h2:tcp://a , b/x",
                        List.of(host("a", 9092), host("b", 9092)),
                        "x",
                        props("MODE", derived("TCP"))),
                Arguments.of("jdbc:h2:tcp://a:0x2384/x", List.of(host("a", 9092)), "x", props("MODE", derived("TCP"))),
                Arguments.of("jdbc:h2:tcp://[::1]/x", List.of(host("[::1]", 9092)), "x", props("MODE", derived("TCP"))),
                Arguments.of("jdbc:h2:tcp:// /x", List.of(host("localhost", 9092)), "x", props("MODE", derived("TCP"))),
                Arguments.of(
                        "jdbc:h2:tcp://,/x",
                        List.of(host("localhost", 9092), host("localhost", 9092)),
                        "x",
                        props("MODE", derived("TCP"))),
                Arguments.of(
                        "jdbc:h2:tcp://a,,b/x",
                        List.of(host("a", 9092), host("localhost", 9092), host("b", 9092)),
                        "x",
                        props("MODE", derived("TCP"))),
                Arguments.of(
                        "jdbc:h2:tcp://db/x;MODE=MySQL", List.of(host("db", 9092)), "x", props("MODE", path("MySQL"))),
                Arguments.of(
                        "jdbc:h2:tcp://db:9092/x;USER=ZUSR;PASSWORD=S3Cx",
                        List.of(host("db", 9092)),
                        "x",
                        props("USER", path("ZUSR"), "PASSWORD", path("S3Cx"), "MODE", derived("TCP"))));
    }

    @ParameterizedTest
    @MethodSource("driverReadings")
    void readsUrlAsDriverDoes(String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<String> driverRejections() {

        return Stream.of(
                "jdbc:h2:mem:testdb;MODE=MySQL;MODE=Oracle",
                "jdbc:h2:mem:t;MODE=MySQL;mode=Oracle",
                "jdbc:h2:mem:testdb;DB_CLOSE_DELAY",
                "jdbc:h2:tcp://db",
                "jdbc:h2:tcp:///x",
                "jdbc:h2:ssl:///x",
                "jdbc:h2:tcp://ZUSR:S3Cx@db/x",
                "jdbc:h2:tcp://ZUSR@db/x",
                "jdbc:h2:tcp://ZUSR:S3Cx@localhost/~/test",
                "jdbc:h2:tcp://db:abc/x",
                "jdbc:h2:tcp://db:/x",
                "jdbc:h2:tcp://::1/x",
                "jdbc:h2:tcp://[::1/x",
                "jdbc:h2:tcp://db:99999/x",
                "jdbc:H2:mem:x",
                "jdbc:h2:");
    }

    @ParameterizedTest
    @MethodSource("driverRejections")
    void rejectsUrlDriverRejects(String url) {

        assertRejected(url);
    }
}
