package net.osslabz.jdbc;

import static net.osslabz.jdbc.ParsedUrls.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        assertEquals(5432, parsed.hosts().get(0).port());
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

        String url =
                "jdbc:postgresql://localhost:5432/testdb?user=postgres&password=secret&ssl=true&sslmode=verify-full&sslcert=/path/to/cert";
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

    static Stream<Arguments> defaultPortCases() {

        return Stream.of(
                Arguments.of(
                        "jdbc:postgresql://dbserver/production",
                        List.of(host("dbserver", 5432)),
                        "production",
                        props()),
                Arguments.of("jdbc:postgresql://[::1]/db", List.of(host("[::1]", 5432)), "db", props()),
                Arguments.of(
                        "jdbc:postgresql://db1,db2:5433/x",
                        List.of(host("db1", 5432), host("db2", 5433)),
                        "x",
                        props()));
    }

    @ParameterizedTest
    @MethodSource("defaultPortCases")
    void defaultPortIsReported(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    @Test
    void portBeyondRangeIsRejected() {

        assertRejected("jdbc:postgresql://db:99999999999/x");
    }

    static Stream<Arguments> driverReadings() {

        return Stream.of(
                Arguments.of(
                        "jdbc:postgresql://db/shop?user=ZUSR&password=S3Cx",
                        List.of(host("db", 5432)),
                        "shop",
                        props("user", query("ZUSR"), "password", query("S3Cx"))),
                Arguments.of(
                        "jdbc:postgresql://db1:5433,db2/x",
                        List.of(host("db1", 5433), host("db2", 5432)),
                        "x",
                        props()),
                Arguments.of("jdbc:postgresql://db/x?", List.of(host("db", 5432)), "x", props()),
                Arguments.of("jdbc:postgresql://db/x?a=b=c", List.of(host("db", 5432)), "x", props("a", query("b=c"))),
                Arguments.of("jdbc:postgresql://db/x?flag", List.of(host("db", 5432)), "x", props("flag", query(""))),
                Arguments.of("jdbc:postgresql://db/x?&&a=1&&", List.of(host("db", 5432)), "x", props("a", query("1"))),
                Arguments.of(
                        "jdbc:postgresql://db/x? a = b &c=d",
                        List.of(host("db", 5432)),
                        "x",
                        props(" a ", query(" b "), "c", query("d"))),
                Arguments.of(
                        "jdbc:postgresql://db/shop?password=a;instanceName=S3Cx",
                        List.of(host("db", 5432)),
                        "shop",
                        props("password", query("a;instanceName=S3Cx"))),
                Arguments.of("jdbc:postgresql://db/x?a=p+q", List.of(host("db", 5432)), "x", props("a", query("p q"))),
                Arguments.of(
                        "jdbc:postgresql://db/x?a=p%2Bq", List.of(host("db", 5432)), "x", props("a", query("p+q"))),
                Arguments.of(
                        "jdbc:postgresql://db/shop?user=ZUSR&password=a%26instanceName%3DS3Cx",
                        List.of(host("db", 5432)),
                        "shop",
                        props("user", query("ZUSR"), "password", query("a&instanceName=S3Cx"))),
                Arguments.of(
                        "jdbc:postgresql://db/shop?pass%77ord=S3Cx",
                        List.of(host("db", 5432)), "shop", props("pass%77ord", query("S3Cx"))),
                Arguments.of(
                        "jdbc:postgresql://db/x?password=S3Cx#frag",
                        List.of(host("db", 5432)),
                        "x",
                        props("password", query("S3Cx#frag"))),
                Arguments.of("jdbc:postgresql://db/x#frag", List.of(host("db", 5432)), "x#frag", props()),
                Arguments.of("jdbc:postgresql://db/x%20y", List.of(host("db", 5432)), "x y", props()),
                Arguments.of("jdbc:postgresql://db/sh%2Fop", List.of(host("db", 5432)), "sh/op", props()),
                Arguments.of("jdbc:postgresql://db/shop+x", List.of(host("db", 5432)), "shop x", props()),
                Arguments.of("jdbc:postgresql://db/shop;x", List.of(host("db", 5432)), "shop;x", props()),
                Arguments.of(
                        "jdbc:postgresql://db/shop:password=S3Cx",
                        List.of(host("db", 5432)),
                        "shop:password=S3Cx",
                        props()),
                Arguments.of(
                        "jdbc:postgresql://db/shop?password=a&password=b",
                        List.of(host("db", 5432)),
                        "shop",
                        props("password", query("b"))),
                Arguments.of(
                        "jdbc:postgresql://db/x?PASSWORD=a&password=b",
                        List.of(host("db", 5432)),
                        "x",
                        props("PASSWORD", query("a"), "password", query("b"))),
                Arguments.of("jdbc:postgresql:shop", List.of(host("localhost", 5432)), "shop", props()),
                Arguments.of("jdbc:postgresql:", List.of(host("localhost", 5432)), "", props()),
                Arguments.of("jdbc:postgresql://", List.of(host("localhost", 5432)), "", props()),
                Arguments.of(
                        "jdbc:postgresql:///x?user=ZUSR&password=S3Cx",
                        List.of(host("localhost", 5432)),
                        "x",
                        props("user", query("ZUSR"), "password", query("S3Cx"))),
                Arguments.of("jdbc:postgresql://:5433/x", List.of(host("localhost", 5433)), "x", props()),
                Arguments.of(
                        "jdbc:postgresql://db/?user=ZUSR",
                        List.of(host("db", 5432)),
                        "ZUSR",
                        props("user", query("ZUSR"))),
                Arguments.of(
                        "jdbc:postgresql:?user=ZUSR",
                        List.of(host("localhost", 5432)),
                        "",
                        props("user", query("ZUSR"))),
                Arguments.of(
                        "jdbc:postgresql://db/shop?PGHOST=evil&PGPORT=4242",
                        List.of(host("evil", 4242)),
                        "shop",
                        props("PGHOST", query("evil"), "PGPORT", query("4242"))),
                Arguments.of(
                        "jdbc:postgresql://db/shop?PGDBNAME=S3Cx",
                        List.of(host("db", 5432)),
                        "S3Cx",
                        props("PGDBNAME", query("S3Cx"))),
                Arguments.of(
                        "jdbc:postgresql://db/x?host=evil&port=4242&dbname=S3Cx",
                        List.of(host("evil", 4242)),
                        "S3Cx",
                        props("host", query("evil"), "port", query("4242"), "dbname", query("S3Cx"))),
                Arguments.of(
                        "jdbc:postgresql://db/x?pghost=evil",
                        List.of(host("db", 5432)),
                        "x",
                        props("pghost", query("evil"))),
                Arguments.of(
                        "jdbc:postgresql://db/x?PGHOST=a,b&PGPORT=1,2",
                        List.of(host("a", 1), host("b", 2)),
                        "x",
                        props("PGHOST", query("a,b"), "PGPORT", query("1,2"))),
                Arguments.of(
                        "jdbc:postgresql://db/x?service=nonexistent",
                        List.of(host("db", 5432)),
                        "x",
                        props("service", query("nonexistent"))),
                Arguments.of("jdbc:postgresql://db:+5432/x", List.of(host("db", 5432)), "x", props()),
                Arguments.of("jdbc:postgresql://[::1]/x", List.of(host("[::1]", 5432)), "x", props()),
                Arguments.of(
                        "jdbc:postgresql://[fe80::1%eth0]:5432/x", List.of(host("[fe80::1%eth0]", 5432)), "x", props()),
                Arguments.of(
                        "jdbc:postgresql:ZUSR:S3Cx@db/shop",
                        List.of(host("localhost", 5432)),
                        "ZUSR:S3Cx@db/shop",
                        props()),
                Arguments.of(
                        "jdbc:postgresql://db1,,db2/x",
                        List.of(host("db1", 5432), host("localhost", 5432), host("db2", 5432)),
                        "x",
                        props()),
                Arguments.of(
                        "jdbc:postgresql://,db/x", List.of(host("localhost", 5432), host("db", 5432)), "x", props()),
                Arguments.of(
                        "jdbc:postgresql://db/x?PGHOST=,a&PGPORT=1,2",
                        List.of(host("localhost", 1), host("a", 2)),
                        "x",
                        props("PGHOST", query(",a"), "PGPORT", query("1,2"))),
                Arguments.of(
                        "jdbc:postgresql://db/x?PGHOST=a&host=b",
                        List.of(host("b", 5432)),
                        "x",
                        props("PGHOST", query("a"), "host", query("b"))),
                Arguments.of(
                        "jdbc:postgresql://db/x?host=b&PGHOST=a",
                        List.of(host("a", 5432)),
                        "x",
                        props("host", query("b"), "PGHOST", query("a"))),
                Arguments.of(
                        "jdbc:postgresql://db/x?PGDBNAME=a&dbname=b",
                        List.of(host("db", 5432)),
                        "b",
                        props("PGDBNAME", query("a"), "dbname", query("b"))),
                Arguments.of("jdbc:postgresql://db/x?host", List.of(host("db", 5432)), "x", props("host", query(""))),
                Arguments.of(
                        "jdbc:postgresql://db/x?PGHOST",
                        List.of(host("localhost", 5432)),
                        "x",
                        props("PGHOST", query(""))));
    }

    @ParameterizedTest
    @MethodSource("driverReadings")
    void readsUrlAsDriverDoes(String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<String> driverRejections() {

        return Stream.of(
                "jdbc:postgresql://db/x?a=%zz",
                "jdbc:postgresql://db/x?a=%",
                "jdbc:postgresql://db/x%zz",
                "jdbc:postgresql:x%zz",
                "jdbc:postgresql://db",
                "jdbc:postgresql://db:5432?password=S3Cx",
                "jdbc:postgresql://db/sh/op",
                "jdbc:postgresql://db/x/",
                "jdbc:postgresql:////x",
                "jdbc:postgresql:/x",
                "jdbc:postgresql://db/x?PGPORT=abc",
                "jdbc:postgresql://db/x?PGPORT=0",
                "jdbc:postgresql://db/x?PGPORT=1,2",
                "jdbc:postgresql://db/x?PGPORT",
                "jdbc:postgresql://db1,db2/x?PGPORT=7",
                "jdbc:postgresql://db/x?PGHOST=a,b",
                "jdbc:postgresql://db:abc/x",
                "jdbc:postgresql://db:/x",
                "jdbc:postgresql://db:0/x",
                "jdbc:postgresql://db:65536/x",
                "jdbc:postgresql://db:-1/x",
                "jdbc:postgresql://[::1]:abc/x",
                "jdbc:postgresql://[::1/x?password=S3Cx",
                "jdbc:postgresql://,/x",
                "jdbc:postgresql://ZUSR:S3Cx@db/shop",
                "jdbc:postgresql://db1,ZUSR:S3Cx@db2/x",
                "jdbc:postgresql://ZUSR@db/x",
                "jdbc:postgresql://ZUSR:S3Cx@db:5432/x",
                "jdbc:postgresql://ZUSR:S3Cx@[::1]/x",
                "jdbc:POSTGRESQL://db/x",
                "jdbc:Postgresql://db/x");
    }

    @ParameterizedTest
    @MethodSource("driverRejections")
    void rejectsUrlDriverRejects(String url) {

        assertRejected(url);
    }
}
