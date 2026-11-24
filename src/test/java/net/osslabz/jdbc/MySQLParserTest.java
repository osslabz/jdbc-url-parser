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
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for MySQL JDBC URL parsing.
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
        assertEquals(3306, parsed.hosts().get(0).port());
    }

    @Test
    void testMySQLEmptyDatabase() {

        String url = "jdbc:mysql://localhost:3306/";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("", parsed.databaseName());
    }

    @Test
    void testMySQLComplexUrl() {

        String url =
                "jdbc:mysql://primary:3306,replica1:3306,replica2:3306/mydb?useSSL=true&rewriteBatchedStatements=true";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(3, parsed.hosts().size());
        assertEquals("primary", parsed.hosts().get(0).hostname());
        assertEquals("replica1", parsed.hosts().get(1).hostname());
        assertEquals("replica2", parsed.hosts().get(2).hostname());
        assertEquals("true", parsed.getPropertyValue("useSSL"));
        assertEquals("true", parsed.getPropertyValue("rewriteBatchedStatements"));
    }

    static Stream<Arguments> defaultPortCases() {

        return Stream.of(
                Arguments.of("jdbc:mysql://localhost/mydb", List.of(host("localhost", 3306)), "mydb", props()),
                Arguments.of("jdbc:mariadb://db/x", List.of(host("db", 3306)), "x", props()));
    }

    @ParameterizedTest
    @MethodSource("defaultPortCases")
    void defaultPortIsReported(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<Arguments> driverReadings() {

        return Stream.of(
                Arguments.of("jdbc:mysql://db", List.of(host("db", 3306)), "", props()),
                Arguments.of("jdbc:mysql:///shop", List.of(host("localhost", 3306)), "shop", props()),
                Arguments.of("jdbc:mysql://", List.of(host("localhost", 3306)), "", props()),
                Arguments.of("jdbc:mysql:/db/shop", List.of(host("localhost", 3306)), "db/shop", props()),
                Arguments.of("jdbc:mysql://:3307/shop", List.of(host("localhost", 3307)), "shop", props()),
                Arguments.of("jdbc:mysql://db:/shop", List.of(host("db", 3306)), "shop", props()),
                Arguments.of("jdbc:mysql://db:-1/shop", List.of(host("db", 3306)), "shop", props()),
                Arguments.of("jdbc:mysql://db/x?", List.of(host("db", 3306)), "x", props()),
                Arguments.of(
                        "jdbc:mysql://[2001:db8::1]:3307/shop", List.of(host("[2001:db8::1]", 3307)), "shop", props()),
                Arguments.of("jdbc:mysql://[::1]/shop", List.of(host("[::1]", 3306)), "shop", props()),
                Arguments.of("jdbc:mysql://d%62/sh%6Fp", List.of(host("db", 3306)), "shop", props()),
                Arguments.of("jdbc:mysql://db/sh+op", List.of(host("db", 3306)), "sh op", props()),
                Arguments.of(
                        "jdbc:mysql://db/shop?pass%77ord=S3Cx&%75ser=U&a=b+c",
                        List.of(host("db", 3306)),
                        "shop",
                        props("password", query("S3Cx"), "user", query("U"), "a", query("b c"))),
                Arguments.of("jdbc:mysql://db/shop?a=1&a=2", List.of(host("db", 3306)), "shop", props("a", query("2"))),
                Arguments.of(
                        "jdbc:mysql://db/shop?permitMysqlScheme&dbname=S3Cx",
                        List.of(host("db", 3306)),
                        "S3Cx",
                        props("permitMysqlScheme", query(""), "dbname", query("S3Cx"))),
                Arguments.of("jdbc:mysql://db/shop?dbname=", List.of(host("db", 3306)), "", props("dbname", query(""))),
                Arguments.of("jdbc:mysql://db/shop?dbname", List.of(host("db", 3306)), "", props("dbname", query(""))),
                Arguments.of(
                        "jdbc:mysql://db/shop?DbName=x", List.of(host("db", 3306)), "x", props("DbName", query("x"))),
                Arguments.of(
                        "jdbc:mysql://db/shop?a=b;c=d", List.of(host("db", 3306)), "shop", props("a", query("b;c=d"))),
                Arguments.of("jdbc:mysql://db/shop#?password=S3Cx", List.of(host("db", 3306)), "shop", props()),
                Arguments.of(
                        "jdbc:mysql://db/shop?password=S3Cx#frag",
                        List.of(host("db", 3306)),
                        "shop",
                        props("password", query("S3Cx"))),
                Arguments.of(
                        "jdbc:mysql://db/shop?password=a%26instanceName%3DS3Cx",
                        List.of(host("db", 3306)), "shop", props("password", query("a&instanceName=S3Cx"))),
                Arguments.of("jdbc:mysql://db/x;password=S3Cx", List.of(host("db", 3306)), "x;password=S3Cx", props()),
                Arguments.of("jdbc:mysql://db/shop? a = b ", List.of(host("db", 3306)), "shop", props("a", query("b"))),
                Arguments.of("jdbc:mysql://db/shop?=", List.of(host("db", 3306)), "shop", props()),
                Arguments.of(
                        "jdbc:mysql://db?host=other&port=3307",
                        List.of(host("db", 3307)),
                        "",
                        props("host", query("other"), "port", query("3307"))),
                Arguments.of(
                        "jdbc:mysql:///shop?host=h&port=3307",
                        List.of(host("h", 3307)),
                        "shop",
                        props("host", query("h"), "port", query("3307"))),
                Arguments.of(
                        "jdbc:mysql:///shop?HOST=h&PORT=3307",
                        List.of(host("h", 3307)),
                        "shop",
                        props("HOST", query("h"), "PORT", query("3307"))),
                Arguments.of(
                        "jdbc:mysql://db:3307/shop?port=abc",
                        List.of(host("db", 3307)),
                        "shop",
                        props("port", query("abc"))),
                Arguments.of(
                        "jdbc:mysql://db:-1/shop?port=3307",
                        List.of(host("db", 3307)),
                        "shop",
                        props("port", query("3307"))),
                Arguments.of(
                        "jdbc:mysql://db/shop?port=-1", List.of(host("db", 3306)), "shop", props("port", query("-1"))),
                Arguments.of(
                        "jdbc:mysql://h1,,h2/shop",
                        List.of(host("h1", 3306), host("localhost", 3306), host("h2", 3306)),
                        "shop",
                        props()),
                Arguments.of(
                        "jdbc:mysql://h1:3305,h2/shop?port=3307",
                        List.of(host("h1", 3305), host("h2", 3307)),
                        "shop",
                        props("port", query("3307"))),
                Arguments.of(
                        "jdbc:mysql:///ZUSR:S3Cx@db/shop",
                        List.of(host("localhost", 3306)),
                        "ZUSR:S3Cx@db/shop",
                        props()),
                Arguments.of(
                        "jdbc:mysql://db/shop?dnsSrv=false",
                        List.of(host("db", 3306)),
                        "shop",
                        props("dnsSrv", query("false"))),
                Arguments.of(
                        "jdbc:mysql://db/shop?DNSSRV=true",
                        List.of(host("db", 3306)),
                        "shop",
                        props("DNSSRV", query("true"))));
    }

    @ParameterizedTest
    @MethodSource("driverReadings")
    void readsUrlAsDriverDoes(String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {"JDBC:mysql://db/shop", "jdbc:MYSQL://db/shop"})
    void acceptsSchemeInAnyCase(String url) {

        assertParsed(url, List.of(host("db", 3306)), "shop", props());
        assertEquals("jdbc:mysql:", JdbcUrlParser.parse(url).protocol());
    }

    static Stream<String> driverRejections() {

        return Stream.of(
                "jdbc:mysql://db:abc/shop",
                "jdbc:mysql://db:+3307/shop",
                "jdbc:mysql://db/shop?a;b=c",
                "jdbc:mysql://db/shop?k$=v",
                "jdbc:mysql://db/shop?a+b=c",
                "jdbc:mysql://db/shop?=x",
                "jdbc:mysql://db/shop?a=%zz",
                "jdbc:mysql://db//x",
                "jdbc:mysql://db/shop??a=1",
                "jdbc:mysql://db/shop?port=abc",
                "jdbc:mysql://db/shop?port=+3307",
                "jdbc:mysql://db:99999/shop",
                "jdbc:mysql://db:99999999999/shop",
                "jdbc:mysql:db/shop",
                "jdbc:mysql:ZUSR:S3Cx@db/shop",
                "jdbc:mysql:sequential://db",
                "jdbc:mysql:aurora://db",
                "jdbc:mysql://db/shop?dnsSrv=true",
                "jdbc:mysql://db/shop?dnsSrv=Yes",
                "jdbc:mysql://db/shop?dns%53rv=true",
                "jdbc:mysql://[h1,h2]:3307/shop",
                "jdbc:mysql://2001:db8::1/shop",
                "jdbc:mysql://[::1/shop",
                "jdbc:mysql://[::1]x/shop");
    }

    @ParameterizedTest
    @MethodSource("driverRejections")
    void rejectsUrlDriverRejects(String url) {

        assertRejected(url);
    }
}
