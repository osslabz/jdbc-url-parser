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
 * Tests for MariaDB JDBC URL parsing.
 */
class MariaDBParserTest {

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

    static Stream<Arguments> driverReadings() {

        return Stream.of(
                Arguments.of("jdbc:mariadb://db/x", List.of(host("db", 3306)), "x", props()),
                Arguments.of("jdbc:mariadb://db", List.of(host("db", 3306)), "", props()),
                Arguments.of("jdbc:mariadb://db?a=b", List.of(host("db", 3306)), "", props("a", query("b"))),
                Arguments.of("jdbc:mariadb://db/x?", List.of(host("db", 3306)), "x", props()),
                Arguments.of("jdbc:mariadb://db/x/y", List.of(host("db", 3306)), "x/y", props()),
                Arguments.of("jdbc:mariadb://db/sh%6Fp", List.of(host("db", 3306)), "sh%6Fp", props()),
                Arguments.of("jdbc:mariadb://db/shop#frag", List.of(host("db", 3306)), "shop#frag", props()),
                Arguments.of(
                        "jdbc:mariadb://db/shop?password=a%26b",
                        List.of(host("db", 3306)), "shop", props("password", query("a%26b"))),
                Arguments.of(
                        "jdbc:mariadb://db/x?a=b+c%20d", List.of(host("db", 3306)), "x", props("a", query("b+c%20d"))),
                Arguments.of(
                        "jdbc:mariadb://db/shop?password=a;instanceName=S3Cx",
                        List.of(host("db", 3306)),
                        "shop",
                        props("password", query("a;instanceName=S3Cx"))),
                Arguments.of(
                        "jdbc:mariadb://db/x?a&b=",
                        List.of(host("db", 3306)),
                        "x",
                        props("a", query(""), "b", query(""))),
                Arguments.of(
                        "jdbc:mariadb://db/x?user=u&user=v", List.of(host("db", 3306)), "x", props("user", query("v"))),
                Arguments.of(
                        "jdbc:mariadb://db/x?database=y",
                        List.of(host("db", 3306)),
                        "y",
                        props("database", query("y"))),
                Arguments.of("jdbc:mariadb://db:3307/x", List.of(host("db", 3307)), "x", props()),
                Arguments.of("jdbc:mariadb://db:03307/x", List.of(host("db", 3307)), "x", props()),
                Arguments.of("jdbc:mariadb://DB/x", List.of(host("DB", 3306)), "x", props()),
                Arguments.of("jdbc:mariadb://[::1]:3307/x", List.of(host("[::1]", 3307)), "x", props()),
                Arguments.of(
                        "jdbc:mariadb://db1,db2:3308/x", List.of(host("db1", 3306), host("db2", 3308)), "x", props()),
                Arguments.of("jdbc:mariadb://db:3306:S3Cx/shop", List.of(host("db", 3306)), "shop", props()),
                Arguments.of("jdbc:mariadb://address=(host=DB)(port=3307)/x", List.of(host("db", 3307)), "x", props()),
                Arguments.of(
                        "jdbc:mariadb://address=(host=db)(password=S3Cx)/x", List.of(host("db", 3306)), "x", props()),
                Arguments.of(
                        "jdbc:mariadb://address=(host=db)(port=3306),address=(host=db2)(type=replica)/x",
                        List.of(host("db", 3306), host("db2", 3306)),
                        "x",
                        props()),
                Arguments.of(
                        "jdbc:mariadb:///x?localSocket=/tmp/s.sock",
                        List.of(),
                        "x",
                        props("localSocket", query("/tmp/s.sock"))),
                Arguments.of("jdbc:mariadb://:3307/x", List.of(host("localhost", 3307)), "x", props()),
                Arguments.of("jdbc:mariadb://[]:3307/x", List.of(host("localhost", 3307)), "x", props()),
                Arguments.of("jdbc:mariadb://db,/x", List.of(host("db", 3306)), "x", props()),
                Arguments.of("jdbc:mariadb:// db /x", List.of(host("db", 3306)), "x", props()),
                Arguments.of("jdbc:mariadb://[::1]x/y", List.of(host("[::1]", 3306)), "y", props()),
                Arguments.of(
                        "jdbc:mariadb://db/x?DataBase=y",
                        List.of(host("db", 3306)),
                        "y",
                        props("DataBase", query("y"))),
                Arguments.of(
                        "jdbc:mariadb://db/x?database=", List.of(host("db", 3306)), "x", props("database", query(""))),
                Arguments.of(
                        "jdbc:mariadb://db/x?databaſe=y",
                        List.of(host("db", 3306)),
                        "x",
                        props("databaſe", query("y"))),
                Arguments.of("jdbc:mariadb://address=(host=[::1])/x", List.of(host("[::1]", 3306)), "x", props()),
                Arguments.of(
                        "jdbc:mariadb://address=(host=::1)(port=3307)/x", List.of(host("[::1]", 3307)), "x", props()),
                Arguments.of("jdbc:mariadb:///x?LOCALSOCKET=/s", List.of(), "x", props("LOCALSOCKET", query("/s"))),
                Arguments.of("jdbc:mariadb:///x?PiPe=p", List.of(), "x", props("PiPe", query("p"))),
                Arguments.of("jdbc:mariadb://address=(localSocket=/tmp/s.sock)/x", List.of(), "x", props()),
                Arguments.of("jdbc:mariadb://address=(pipe=p)/x", List.of(), "x", props()),
                Arguments.of("jdbc:mariadb://,/x?localSocket=/s", List.of(), "x", props("localSocket", query("/s"))));
    }

    @ParameterizedTest
    @MethodSource("driverReadings")
    void readsUrlAsDriverDoes(String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<String> driverRejections() {

        return Stream.of(
                "jdbc:MARIADB://db/shop",
                "jdbc:mariadb:db/x",
                "jdbc:mariadb:/db/x",
                "jdbc:mariadb://ZUSR:S3Cx@db/shop",
                "jdbc:mariadb://ZUSR:123@db/x",
                "jdbc:mariadb://ZUSR@db/x",
                "jdbc:mariadb://(host=db,port=3306)/shop",
                "jdbc:mariadb://db1, db2/x",
                "jdbc:mariadb://db:abc/x",
                "jdbc:mariadb://db:/x",
                "jdbc:mariadb://[::1/x",
                "jdbc:mariadb://db1,,db2/x",
                "jdbc:mariadb://,db/x",
                "jdbc:mariadb://db:99999/x",
                "jdbc:mariadb://db:-1/x",
                "jdbc:mariadb://address=(host=db)(type=bogus)/x",
                "jdbc:mariadb://address=(host=db)(port=abc)/x",
                "jdbc:mariadb://address=(foo)/x",
                "jdbc:mariadb:///x",
                "jdbc:mariadb://",
                "jdbc:mariadb://address=(host=db/x",
                "jdbc:mariadb://address=(port=3307)/x",
                "jdbc:mariadb:///x?localSocket=",
                "jdbc:mariadb://ADDRESS=(host=db)/x",
                "jdbc:mariadb:x//db/x");
    }

    @ParameterizedTest
    @MethodSource("driverRejections")
    void rejectsUrlDriverRejects(String url) {

        assertRejected(url);
    }
}
