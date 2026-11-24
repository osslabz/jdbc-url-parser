package net.osslabz.jdbc;

import static net.osslabz.jdbc.ParsedUrls.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        assertNull(parsed.hosts().get(0).port());
        assertEquals("mydb", parsed.databaseName());
    }

    @Test
    void testSQLServerDefaultPort() {

        String url = "jdbc:sqlserver://dbserver;databaseName=production";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("dbserver", parsed.hosts().get(0).hostname());
        assertEquals(1433, parsed.hosts().get(0).port());
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

        String url =
                "jdbc:sqlserver://myserver:1433;databaseName=AdventureWorks;user=sa;password=secret;encrypt=true;trustServerCertificate=true;loginTimeout=30";
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

    static Stream<Arguments> defaultPortCases() {

        return Stream.of(Arguments.of(
                "jdbc:sqlserver://dbserver;databaseName=production",
                List.of(host("dbserver", 1433)),
                "production",
                props("databaseName", path("production"))));
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
                        "jdbc:sqlserver://db:1433;databaseName=x",
                        List.of(host("db", 1433)),
                        "x",
                        props("databaseName", path("x"))),
                Arguments.of("jdbc:sqlserver://db", List.of(host("db", 1433)), "", props()),
                Arguments.of("jdbc:sqlserver://db;", List.of(host("db", 1433)), "", props()),
                Arguments.of("jdbc:sqlserver://", List.of(host("localhost", 1433)), "", props()),
                Arguments.of(
                        "jdbc:sqlserver://;serverName=db",
                        List.of(host("db", 1433)),
                        "",
                        props("serverName", path("db"))),
                Arguments.of(
                        "jdbc:sqlserver://;user=ZUSR;password=S3Cx",
                        List.of(host("localhost", 1433)),
                        "",
                        props("user", path("ZUSR"), "password", path("S3Cx"))),
                Arguments.of(
                        "jdbc:sqlserver://;serverName=db2\\Inst",
                        List.of(host("db2", null, "Inst")),
                        "",
                        props("serverName", path("db2\\Inst"))),
                Arguments.of(
                        "jdbc:sqlserver://db\\inst;password=S3Cx",
                        List.of(host("db", null, "inst")),
                        "",
                        props("password", path("S3Cx"))),
                Arguments.of(
                        "jdbc:sqlserver://db\\INST;user=ZUSR;password=S3Cx",
                        List.of(host("db", null, "INST")),
                        "",
                        props("user", path("ZUSR"), "password", path("S3Cx"))),
                Arguments.of(
                        "jdbc:sqlserver://db\\Inst:1433;databaseName=x",
                        List.of(host("db", 1433, "Inst")),
                        "x",
                        props("databaseName", path("x"))),
                Arguments.of(
                        "jdbc:sqlserver://db\\Inst:1433;instanceName=Other",
                        List.of(host("db", 1433, "Other")),
                        "",
                        props("instanceName", path("Other"))),
                Arguments.of(
                        "jdbc:sqlserver://db;instanceName=I;portNumber=1433",
                        List.of(host("db", 1433, "I")),
                        "",
                        props("instanceName", path("I"), "portNumber", path("1433"))),
                Arguments.of(
                        "jdbc:sqlserver://db\\inst;portNumber=0",
                        List.of(host("db", null, "inst")),
                        "",
                        props("portNumber", path("0"))),
                Arguments.of("jdbc:sqlserver://db\\;x=1", List.of(host("db", null, "")), "", props("x", path("1"))),
                Arguments.of(
                        "jdbc:sqlserver://db:1433;portNumber=1444",
                        List.of(host("db", 1444)),
                        "",
                        props("portNumber", path("1444"))),
                Arguments.of(
                        "jdbc:sqlserver://db;port=4242;databaseName=x",
                        List.of(host("db", 4242)),
                        "x",
                        props("port", path("4242"), "databaseName", path("x"))),
                Arguments.of(
                        "jdbc:sqlserver://db:0;databaseName=x",
                        List.of(host("db", 1433)),
                        "x",
                        props("databaseName", path("x"))),
                Arguments.of("jdbc:sqlserver://db:+1433", List.of(host("db", 1433)), "", props()),
                Arguments.of(
                        "jdbc:sqlserver://db:1433;serverName=other",
                        List.of(host("other", 1433)),
                        "",
                        props("serverName", path("other"))),
                Arguments.of(
                        "jdbc:sqlserver://db;server=evil;databaseName=x",
                        List.of(host("evil", 1433)),
                        "x",
                        props("server", path("evil"), "databaseName", path("x"))),
                Arguments.of(
                        "jdbc:sqlserver://;serverName=[::1];portNumber=1433",
                        List.of(host("[::1]", 1433)),
                        "",
                        props("serverName", path("[::1]"), "portNumber", path("1433"))),
                Arguments.of(
                        "jdbc:sqlserver://db ;databaseName=shop",
                        List.of(host("db", 1433)),
                        "shop",
                        props("databaseName", path("shop"))),
                Arguments.of(
                        "jdbc:sqlserver://db;databaseName=shop;password=a&databaseName=S3Cx",
                        List.of(host("db", 1433)),
                        "shop",
                        props("databaseName", path("shop"), "password", path("a&databaseName=S3Cx"))),
                Arguments.of(
                        "jdbc:sqlserver://db;databaseName=x;password={a;databaseName=S3Cy}",
                        List.of(host("db", 1433)),
                        "x",
                        props("databaseName", path("x"), "password", path("a;databaseName=S3Cy"))),
                Arguments.of(
                        "jdbc:sqlserver://db;password={a}}b}",
                        List.of(host("db", 1433)),
                        "",
                        props("password", path("a}b"))),
                Arguments.of(
                        "jdbc:sqlserver://db;password={a}}}",
                        List.of(host("db", 1433)),
                        "",
                        props("password", path("a}"))),
                Arguments.of(
                        "jdbc:sqlserver://db;password={ a }",
                        List.of(host("db", 1433)),
                        "",
                        props("password", path(" a "))),
                Arguments.of(
                        "jdbc:sqlserver://db;password={a}  ;databaseName=x",
                        List.of(host("db", 1433)),
                        "x",
                        props("password", path("a"), "databaseName", path("x"))),
                Arguments.of(
                        "jdbc:sqlserver://db;password={}", List.of(host("db", 1433)), "", props("password", path(""))),
                Arguments.of(
                        "jdbc:sqlserver://db;databaseName=a;DATABASE=b;dAtAbAsEnAmE=c",
                        List.of(host("db", 1433)),
                        "c",
                        props("databaseName", path("a"), "DATABASE", path("b"), "dAtAbAsEnAmE", path("c"))),
                Arguments.of(
                        "jdbc:sqlserver://db;databaseName=a;database=b",
                        List.of(host("db", 1433)),
                        "b",
                        props("databaseName", path("a"), "database", path("b"))),
                Arguments.of(
                        "jdbc:sqlserver://db;databaseName=a;database=b;databaseName=c",
                        List.of(host("db", 1433)),
                        "c",
                        props("database", path("b"), "databaseName", path("c"))),
                Arguments.of(
                        "jdbc:sqlserver://db;databaſeName=S3Cx",
                        List.of(host("db", 1433)),
                        "S3Cx",
                        props("databaſeName", path("S3Cx"))),
                Arguments.of(
                        "jdbc:sqlserver://db;databaseName=x;UID=ZUSR;PWD=S3Cx",
                        List.of(host("db", 1433)),
                        "x",
                        props("databaseName", path("x"), "UID", path("ZUSR"), "PWD", path("S3Cx"))),
                Arguments.of(
                        "jdbc:sqlserver://db;databaseName=a%20b",
                        List.of(host("db", 1433)), "a%20b", props("databaseName", path("a%20b"))),
                Arguments.of(
                        "jdbc:sqlserver://db\\A;serverName=x\\B",
                        List.of(host("x", null, "A")),
                        "",
                        props("serverName", path("x\\B"))),
                Arguments.of("jdbc:sqlserver://\\inst", List.of(host("localhost", null, "inst")), "", props()),
                Arguments.of("jdbc:sqlserver://db\\Inst$_2", List.of(host("db", null, "Inst$_2")), "", props()),
                Arguments.of(
                        "jdbc:sqlserver://;serverName=[fe80::1%eth0]",
                        List.of(host("[fe80::1%eth0]", 1433)), "", props("serverName", path("[fe80::1%eth0]"))),
                Arguments.of(
                        "jdbc:sqlserver://db_1.example-x.com", List.of(host("db_1.example-x.com", 1433)), "", props()));
    }

    @ParameterizedTest
    @MethodSource("driverReadings")
    void readsUrlAsDriverDoes(String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<String> driverRejections() {

        return Stream.of(
                "jdbc:SQLSERVER://db;databaseName=x",
                "jdbc:sqlserver:db",
                "jdbc:sqlserver:/db",
                "jdbc:sqlserver://db:65536;databaseName=x",
                "jdbc:sqlserver://db:-1",
                "jdbc:sqlserver://db:abc",
                "jdbc:sqlserver://db:",
                "jdbc:sqlserver://db;portNumber=abc",
                "jdbc:sqlserver://db;port=99999",
                "jdbc:sqlserver://db:99999999999",
                "jdbc:sqlserver://db:1433\\ZUSR;databaseName=shop",
                "jdbc:sqlserver://db:1433/x;password=S3Cx",
                "jdbc:sqlserver://ZUSR:S3Cx@db;databaseName=shop",
                "jdbc:sqlserver://ZUSR:S3Cx@db\\INST;databaseName=shop",
                "jdbc:sqlserver://[::1]:1433;password=S3Cx;databaseName=x",
                "jdbc:sqlserver://ZUSR\\S3Cx@db;databaseName=shop",
                "jdbc:sqlserver://db?databaseName=x",
                "jdbc:sqlserver://db/x;databaseName=y",
                "jdbc:sqlserver://db;password={abc",
                "jdbc:sqlserver://db;password={a}}",
                "jdbc:sqlserver://db;password=a{b}",
                "jdbc:sqlserver://db;password={a}b;x=y",
                "jdbc:sqlserver://db;databaseName=x;user",
                "jdbc:sqlserver://db;password=S3Cx;databaseName",
                "jdbc:sqlserver://db;=x",
                "jdbc:sqlserver://;serverName=[::1",
                "jdbc:sqlserver://;serverName=[]",
                "jdbc:sqlserver://:1433",
                "jdbc:sqlserver://db\\2nd",
                "jdbc:sqlserver://db\\in st;x=1",
                "jdbc:sqlserver://db\\a-b",
                "jdbc:sqlserver://;serverName=db\\a\\b");
    }

    @ParameterizedTest
    @MethodSource("driverRejections")
    void rejectsUrlDriverRejects(String url) {

        assertRejected(url);
    }

    @Test
    void parsesUppercaseKeyUnderTurkishLocale() {

        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertEquals(
                    "x",
                    JdbcUrlParser.parse("jdbc:sqlserver://db;DATABASENAME=x").databaseName());
            assertEquals(
                    "x",
                    JdbcUrlParser.parse("jdbc:sqlserver://db;INSTANCENAME=x")
                            .hosts()
                            .get(0)
                            .instanceName());
        } finally {
            Locale.setDefault(original);
        }
    }
}
