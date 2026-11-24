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
import org.junit.jupiter.params.provider.ValueSource;

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
    void testOracleServiceNameWithoutSlashes() {

        String url = "jdbc:oracle:thin:@dbserver.example.com:1522/myservice";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("dbserver.example.com", parsed.hosts().get(0).hostname());
        assertEquals(1522, parsed.hosts().get(0).port());
        assertEquals("myservice", parsed.databaseName());
    }

    @Test
    void testOracleDescriptorFormat() {

        String url =
                "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=localhost)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=myservice)))";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(DatabaseProduct.ORACLE, parsed.databaseProduct());
        assertEquals("localhost", parsed.hosts().get(0).hostname());
        assertEquals(1521, parsed.hosts().get(0).port());
        assertEquals("myservice", parsed.databaseName());
        assertTrue(parsed.properties().containsKey("DESCRIPTOR"));
    }

    @Test
    void testOracleDescriptorWithSID() {

        String url =
                "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=dbhost)(PORT=1521))(CONNECT_DATA=(SID=ORCL)))";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("dbhost", parsed.hosts().get(0).hostname());
        assertEquals(1521, parsed.hosts().get(0).port());
        assertEquals("ORCL", parsed.databaseName());
        assertEquals("ORCL", parsed.getPropertyValue("SID"));
    }

    @Test
    void testOracleDescriptorCaseInsensitive() {

        String url =
                "jdbc:oracle:thin:@(description=(address=(protocol=TCP)(host=myhost)(port=1522))(connect_data=(service_name=PROD)))";
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

    static Stream<Arguments> defaultPortCases() {

        return Stream.of(Arguments.of(
                "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SERVICE_NAME=svc)))",
                List.of(host("db", 1521)),
                "svc",
                props(
                        "DRIVER_TYPE",
                        derived("thin"),
                        "SERVICE_NAME",
                        descriptor("svc"),
                        "DESCRIPTOR",
                        descriptor(
                                "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SERVICE_NAME=svc)))"))));
    }

    @ParameterizedTest
    @MethodSource("defaultPortCases")
    void defaultPortIsReported(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    private static final String THIN = "jdbc:oracle:thin:";

    private static final String SERVICE_NAME = "SERVICE_NAME";

    private static final String SID = "SID";

    private static final String DRIVER_TYPE = "DRIVER_TYPE";

    private static Arguments thin(String connect, List<Host> hosts, String databaseName, Object... properties) {

        Object[] withDriverType = new Object[properties.length + 2];
        withDriverType[0] = DRIVER_TYPE;
        withDriverType[1] = derived("thin");
        System.arraycopy(properties, 0, withDriverType, 2, properties.length);
        return Arguments.of(THIN + connect, hosts, databaseName, props(withDriverType));
    }

    static Stream<Arguments> easyConnectReadings() {

        return Stream.of(
                thin("@db:1521:ORCL", List.of(host("db", 1521)), "ORCL", SID, descriptor("ORCL")),
                thin("@//db:1521/svc", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@db:1521/svc", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@db/svc", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@db", List.of(host("db", 1521)), ""),
                thin("@db:1521", List.of(host("db", 1521)), ""),
                thin("@//db", List.of(host("db", 1521)), ""),
                thin("@:1521:ORCL", List.of(host("localhost", 1521)), "ORCL", SID, descriptor("ORCL")),
                thin("@db:1521:ORCL:dedicated", List.of(host("db", 1521)), "ORCL", SID, descriptor("ORCL")),
                thin(
                        "@//db:1521/svc?connect_timeout=5&foo=bar",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "connect_timeout",
                        query("5"),
                        "foo",
                        query("bar")),
                thin("@//db:1521/svc?", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin(
                        "@db:1521/svc?password=S3Cx",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "password",
                        query("S3Cx")),
                thin("@//db:1521/svc#S3C", List.of(host("db", 1521)), "svc#S3C", SERVICE_NAME, descriptor("svc#S3C")),
                thin("@//db:1521/svc S3C", List.of(host("db", 1521)), "svcS3C", SERVICE_NAME, descriptor("svcS3C")),
                thin("@//db:1521/svc/inst", List.of(host("db", 1521, "inst")), "svc", SERVICE_NAME, descriptor("svc")),
                thin(
                        "@//db:1521/svc:dedicated/inst",
                        List.of(host("db", 1521, "inst")),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                thin(
                        "@//h1,h2/svc",
                        List.of(host("h1", 1521), host("h2", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                thin(
                        "@//h1,h2:1522,h3/svc",
                        List.of(host("h1", 1522), host("h2", 1522), host("h3", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                thin(
                        "@h1:1521,h2:1522/svc",
                        List.of(host("h1", 1521), host("h2", 1522)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                thin("@tcps://db:2484/svc", List.of(host("db", 2484)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@tcps://db/svc", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@//[::1]:1521/svc", List.of(host("[::1]", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@[::1]:1521:ORCL", List.of(host("[::1]", 1521)), "ORCL", SID, descriptor("ORCL")),
                thin("@myalias", List.of(host("myalias", 1521)), ""),
                thin(
                        "ZUSR/S3Cx@db:1521:ORCL",
                        List.of(host("db", 1521)),
                        "ORCL",
                        SID,
                        descriptor("ORCL"),
                        "user",
                        authority("ZUSR"),
                        "password",
                        authority("S3Cx")),
                thin(
                        "\"ZUSR\"/\"S3C@x\"@db:1521/svc",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("\"ZUSR\""),
                        "password",
                        authority("\"S3C@x\"")),
                thin(
                        "ZUSR/S3Cx@//db:1521/svc",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("ZUSR"),
                        "password",
                        authority("S3Cx")),
                thin("/@db:1521/svc", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin(
                        "ZUSR/S3Cx@db:1521/svc?user=OTHER",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        query("OTHER"),
                        "password",
                        authority("S3Cx")),
                Arguments.of(
                        "jdbc:oracle:oci:@db:1521:ORCL",
                        List.of(host("db", 1521)),
                        "ORCL",
                        props(DRIVER_TYPE, derived("oci"), SID, descriptor("ORCL"))),
                Arguments.of(
                        "jdbc:oracle:oci8:@db:1521:ORCL",
                        List.of(host("db", 1521)),
                        "ORCL",
                        props(DRIVER_TYPE, derived("oci8"), SID, descriptor("ORCL"))));
    }

    @ParameterizedTest
    @MethodSource("easyConnectReadings")
    void readsEasyConnectAsDriverDoes(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<Arguments> easyConnectEdgeReadings() {

        return Stream.of(
                thin("@db:1521:ORCL:SHARED", List.of(host("db", 1521)), "ORCL", SID, descriptor("ORCL")),
                thin("@db:1521:ORCL:pooled", List.of(host("db", 1521)), "ORCL", SID, descriptor("ORCL")),
                thin("@db:1521/svc:SHARED", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@db:1521:", List.of(host("db", 1521)), ""),
                thin("@db:01521:ORCL", List.of(host("db", 1521)), "ORCL", SID, descriptor("ORCL")),
                thin("@db:0/svc", List.of(host("db", 0)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@tcp:db/svc", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@TCPS://db/svc", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@tcps:db:2484/svc", List.of(host("db", 2484)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@ db : 1521 / svc", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@h1,/svc", List.of(host("h1", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin(
                        "@db,h2:1522/svc",
                        List.of(host("db", 1522), host("h2", 1522)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                thin("@/svc", List.of(host("localhost", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@:1521/svc", List.of(host("localhost", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@:1521", List.of(host("localhost", 1521)), ""),
                thin("@//[::1]", List.of(host("[::1]", 1521)), ""),
                thin("@db:1521?x=y", List.of(host("db", 1521)), "", "x", query("y")),
                thin("@db:1521:ORCL?a=b", List.of(host("db", 1521)), "ORCL", SID, descriptor("ORCL"), "a", query("b")),
                thin(
                        "@db/svc/inst?a=b",
                        List.of(host("db", 1521, "inst")),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "a",
                        query("b")),
                thin(
                        "@db/svc?a=1&",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "a",
                        query("1")),
                thin(
                        "@db/svc?a=1&a=2",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "a",
                        query("2")),
                thin(
                        "@db/svc?a=%41",
                        List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc"), "a", query("%41")),
                thin(
                        "@//db/svc?a=b c",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "a",
                        query("bc")),
                thin("@db/svc?=x", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc"), "", query("x")),
                thin(
                        "@db/svc?connect_timeout=5&CONNECT_TIMEOUT=7",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "connect_timeout",
                        query("5"),
                        "CONNECT_TIMEOUT",
                        query("7")),
                thin(
                        "@db/svc?password=S3C@x",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "password",
                        query("S3C@x")),
                thin("@db/svc?SERVICE_NAME=other", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@db:1521:ORCL?SID=other", List.of(host("db", 1521)), "ORCL", SID, descriptor("ORCL")),
                thin("@db/svc?DRIVER_TYPE=oci", List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                thin("@db?SID=other", List.of(host("db", 1521)), "", SID, query("other")),
                // The driver rejects a bare '?' after a SID; the library reads it as an empty query [G12].
                thin("@db:1521:ORCL?", List.of(host("db", 1521)), "ORCL", SID, descriptor("ORCL")));
    }

    @ParameterizedTest
    @MethodSource("easyConnectEdgeReadings")
    void readsEasyConnectEdgesAsDriverDoes(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<Arguments> userinfoReadings() {

        return Stream.of(
                thin(
                        "ZUSR/S3C:x@db/svc",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("ZUSR"),
                        "password",
                        authority("S3C:x")),
                thin(
                        "ZUSR/S3C!(x)=y;&*%^~<>[]#$@db/svc",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("ZUSR"),
                        "password",
                        authority("S3C!(x)=y;&*%^~<>[]#$")),
                thin(
                        "ZUSR/pässwörd@db/svc",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("ZUSR"),
                        "password",
                        authority("pässwörd")),
                thin(
                        "\"Z/U\"/pw@db/svc",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("\"Z/U\""),
                        "password",
                        authority("pw")),
                thin(
                        "ZUSR/@db/svc",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("ZUSR")),
                thin(
                        "/S3Cx@db/svc",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "password",
                        authority("S3Cx")),
                thin("/@db", List.of(host("db", 1521)), ""),
                thin(
                        "ZUSR/S3Cx@db/svc?PASSWORD=P2",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("ZUSR"),
                        "password",
                        authority("S3Cx"),
                        "PASSWORD",
                        query("P2")),
                thin(
                        "ZUSR/S3Cx@db/svc?password=",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("ZUSR"),
                        "password",
                        query("")),
                thin(
                        "ZUSR/S3Cx@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(CONNECT_DATA=(SERVICE_NAME=svc)))",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "DESCRIPTOR",
                        descriptor("@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(CONNECT_DATA=(SERVICE_NAME=svc)))"),
                        "user",
                        authority("ZUSR"),
                        "password",
                        authority("S3Cx")));
    }

    @ParameterizedTest
    @MethodSource("userinfoReadings")
    void readsUserinfoAsDriverDoes(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<String> driverRejections() {

        return Stream.of(
                        "@db:ORCL",
                        "@//db:x/svc",
                        "@//db:65536/svc",
                        "@//db:-1/svc",
                        "@db:1521/",
                        "@//db:1521/",
                        "@db:1521:ORCL:foo",
                        "@db::ORCL",
                        "@h1:1521,h2:1522:ORCL",
                        "@//,h1/svc",
                        "@//::1/svc",
                        "@//[::1/svc",
                        "@//db:1521/svc?foo",
                        "@db:1521:ORCL?S3C",
                        "@//db:1521/svc?foo=a=b",
                        "@//db:1521/svc?&foo=bar",
                        "@//ZUSR:S3Cx@db:1521/svc",
                        "@ZUSR/S3Cx@db:1521/svc",
                        "ZUSR@db:1521/svc",
                        "ZUSR:S3Cx@db:1521/svc",
                        "ZUSR/S3C@x@db:1521/svc",
                        "ZUSR/S3Cx@@db",
                        "ZUSR/S3Cx@db:1521/svc@S3Cy",
                        "@db:1521:ORCL@S3Cy",
                        "@foo://db/svc",
                        "@ldap://ldap.example.com/svc",
                        "@ldaps://ldap.example.com/svc",
                        "@",
                        "@db/svc?a=1&&b=2",
                        "@db/svc?a=1&&",
                        "@db/svc?a=b?c=d",
                        "@h1,,h2/svc",
                        "@,/svc",
                        "@h1,h2",
                        "@h1:1521,h2:1522",
                        "@//h1,h2",
                        "@db:1521:ORCL:",
                        "@db:1521:ORCL:dedicated:x",
                        "@db:99999999999:ORCL",
                        "@db:/svc",
                        "@:/svc",
                        "@//db:1521:ORCL",
                        "@tcps://db:1521:ORCL",
                        "@db:1521:OR/CL",
                        "@//db:1521/svc/",
                        "@db:1521/svc:foo",
                        "@db:1521/svc:dedicated:x",
                        "@//db:1521/svc/inst/x",
                        "@//db:1521//inst",
                        "@db/:dedicated",
                        "ZUSR/S3C-x@db/svc",
                        "scott.t/pw@db/svc",
                        "ZUSR/p w@db/svc",
                        "ZÜSR/pw@db/svc",
                        "\"ZU\"SR/pw@db/svc",
                        "ZUSR/\"\"@db/svc",
                        "\"ZUSR/pw@db/svc",
                        "ZUSR/S3Cx@",
                        "ZUSR/S3Cx",
                        "db:1521:ORCL")
                .map(connect -> THIN + connect);
    }

    @ParameterizedTest
    @MethodSource("driverRejections")
    void rejectsUrlDriverRejects(String url) {

        assertRejected(url);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jdbc:oracle:thin:",
                "jdbc:oracle:thin",
                "jdbc:oracle:",
                "jdbc:ORACLE:thin:@db:1521:ORCL",
                "jdbc:oracle:THIN:@db:1521:ORCL",
                "jdbc:oracle:kprb:@db:1521:ORCL",
                "jdbc:oracle:sharding:@db:1521:ORCL"
            })
    void rejectsPrefixOrDriverTypeDriverRejects(String url) {

        assertRejected(url);
    }

    @Test
    void foldsServerModeLocaleIndependently() {

        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertEquals(
                    "ORCL",
                    JdbcUrlParser.parse(THIN + "@db:1521:ORCL:dedicated").databaseName());
            assertEquals(
                    "svc",
                    JdbcUrlParser.parse(THIN + "@//db/svc:dedicated/inst").databaseName());
        } finally {
            Locale.setDefault(original);
        }
    }
}
