package net.osslabz.jdbc;

import static net.osslabz.jdbc.ParsedUrls.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
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
                                "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SERVICE_NAME=svc)))"))));
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

    private static Arguments servicePath(String connect, Host host, String service) {

        return thin(connect, List.of(host), service, SERVICE_NAME, descriptor(service));
    }

    static Stream<Arguments> servicePathReadings() {

        Host db = host("db", 1521);
        return Stream.of(
                servicePath("@//db:1521/svc/S3C:x", db, "svc/S3C:x"),
                servicePath("@//db:1521/svc/a-b", db, "svc/a-b"),
                servicePath("@//db:1521/svc/a.b", db, "svc/a.b"),
                servicePath("@//db:1521/svc/a$b", db, "svc/a$b"),
                servicePath("@//db:1521/svc/inst/x", db, "svc/inst/x"),
                servicePath("@//db:1521//inst", db, "/inst"),
                servicePath("@//db:1521/svc//inst", db, "svc//inst"),
                servicePath("@//db:1521/svc/i", db, "svc/i"),
                servicePath("@//db:1521/svc/1", db, "svc/1"),
                servicePath("@//db:1521/svc:dedicated/a-b", db, "svc:dedicated/a-b"),
                servicePath("@//db:1521/svc:bogus/inst", db, "svc:bogus/inst"),
                servicePath("@//db:1521/svc/inst:shared", db, "svc/inst"),
                servicePath("@//db:1521/svc/S3C:dedicated", db, "svc/S3C"),
                servicePath("@db:1521/svc:foo", db, "svc:foo"),
                servicePath("@db:1521/svc:dedicated:x", db, "svc:dedicated:x"),
                servicePath("@//db:1521/svc(x)", db, "svc(x)"),
                servicePath("@//db:1521/svc(SERVICE_NAME=S3Cx)", db, "svc(SERVICE_NAME=S3Cx)"),
                servicePath("@db:1521/svc/x(y)", db, "svc/x(y)"),
                servicePath("@//db/svc/a-b", db, "svc/a-b"),
                servicePath("@//[::1]/svc/a-b", host("[::1]", 1521), "svc/a-b"),
                servicePath("@//db:1521/svc/inst_1", host("db", 1521, "inst_1"), "svc"),
                servicePath("@//db:1521/svc/iNST9", host("db", 1521, "iNST9"), "svc"),
                servicePath("@//db:1521/_svc/inst", host("db", 1521, "inst"), "_svc"),
                servicePath("@//db/sv,c/inst", host("db", 1521, "inst"), "sv,c"),
                servicePath("@tcp:1521/svc", host("tcp", 1521), "svc"),
                servicePath("@TCP:1521/svc", host("TCP", 1521), "svc"),
                servicePath("@tcps:2484/svc", host("tcps", 2484), "svc"),
                servicePath("@tcp:1521/svc:dedicated", host("tcp", 1521), "svc"),
                servicePath("@tcp:1521/svc/a-b", host("tcp", 1521), "svc/a-b"),
                servicePath("@tcp:1521/svc/inst", host("1521", 1521, "inst"), "svc"),
                thin(
                        "@tcp:1521/svc?a=b",
                        List.of(host("1521", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "a",
                        query("b")),
                thin(
                        "ZUSR/S3Cx@tcp:1521/svc",
                        List.of(host("tcp", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("ZUSR"),
                        "password",
                        authority("S3Cx")),
                thin(
                        "@//db:1521/svc/a-b?password=S3Cx",
                        List.of(db),
                        "svc/a-b",
                        SERVICE_NAME,
                        descriptor("svc/a-b"),
                        "password",
                        query("S3Cx")));
    }

    @ParameterizedTest
    @MethodSource("servicePathReadings")
    void readsServicePathAsDriverDoes(
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
                        descriptor("(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
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
                        "@db:1521:ORCL?S3C",
                        "@//db:1521/svc?foo=a=b",
                        "@//db:1521/svc?&foo=bar",
                        "@//ZUSR:S3Cx@db:1521/svc",
                        "@ZUSR/S3Cx@db:1521/svc",
                        "ZUSR@db:1521/svc",
                        "ZUSR:S3Cx@db:1521/svc",
                        "@db:1521:ORCL@S3Cy",
                        "@foo://db/svc",
                        "@",
                        "@db/svc?a=1&&b=2",
                        "@db/svc?a=1&&",
                        "@db/svc?a=b?c=d",
                        "@h1,,h2/svc",
                        "@,/svc",
                        "@h1,h2",
                        "@h1:1521,h2:1522",
                        "@db:1521:ORCL:",
                        "@db:1521:ORCL:dedicated:x",
                        "@db:99999999999:ORCL",
                        "@db:/svc",
                        "@:/svc",
                        "@//db:1521:ORCL",
                        "@tcps://db:1521:ORCL",
                        "@db:1521:OR/CL",
                        "@//db:1521/svc/",
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
                        "db:1521:ORCL",
                        "@//db:1521/svc/inst/",
                        "@//db:1521//",
                        "@//db:1521///",
                        "@db/svc:bogus",
                        "@//db/svc:",
                        "@//db/svc:/inst",
                        "@//db/svc/a-b:dedicated",
                        "@//db/svc/inst:dedicated",
                        "@//[::1]/svc:x",
                        "@//h1:1521,h2:1522/svc/a-b",
                        "@tcp://db:1521/svc/a-b",
                        "@tcps://db/svc/a-b",
                        "@tcp:db:1521/svc/a-b",
                        "@db:1521:orcl (SERVICE_NAME=S3Cx)",
                        "@db:1521:orcl(x)",
                        "@db:1521:or(cl",
                        "@db:1521:orcl)",
                        "@db:1521:or(cl:dedicated")
                .map(connect -> THIN + connect);
    }

    @ParameterizedTest
    @MethodSource("driverRejections")
    void rejectsUrlDriverRejects(String url) {

        assertRejected(url);
    }

    // The driver accepts these; the library rejects them on purpose.
    static Stream<String> deliberateRejections() {

        return Stream.of(
                        // G12: ojdbc keeps a '?' without '=' in the service name.
                        "@//db:1521/svc?foo",
                        // G5: ojdbc reads host x@db or @db, or a service holding '@', where the password lands.
                        "ZUSR/S3C@x@db:1521/svc",
                        "ZUSR/S3Cx@@db",
                        "ZUSR/S3Cx@db:1521/svc@S3Cy",
                        "@//db:1521/svc/a@b",
                        // G5: ojdbc reads the whole host list as the one host name h1,h2.
                        "@//h1,h2",
                        "@//h1,h2/svc/a-b",
                        "@//h1,h2:1522/svc:bogus",
                        // Non-goal: directory naming needs an LDAP server.
                        "@ldap://ldap.example.com/svc",
                        "@ldaps://ldap.example.com/svc")
                .map(connect -> THIN + connect);
    }

    @ParameterizedTest
    @MethodSource("deliberateRejections")
    void rejectsUrlDriverAcceptsOnPurpose(String url) {

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

    private static final String D1 =
            "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=svc)))";

    private static Arguments descriptorRow(
            String connect, String descriptorText, List<Host> hosts, String databaseName, Object... properties) {

        Object[] withDescriptor = Arrays.copyOf(properties, properties.length + 2);
        withDescriptor[properties.length] = "DESCRIPTOR";
        withDescriptor[properties.length + 1] = descriptor(descriptorText);
        return thin(connect, hosts, databaseName, withDescriptor);
    }

    private static Arguments bareDescriptor(
            String descriptorText, List<Host> hosts, String databaseName, Object... properties) {

        return descriptorRow("@" + descriptorText, descriptorText, hosts, databaseName, properties);
    }

    static Stream<Arguments> descriptorReadings() {

        return Stream.of(
                bareDescriptor(D1, List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                bareDescriptor(
                        "(Description=(Address=(Protocol=tcp)(Host=db)(Port=1521))(Connect_Data=(Service_Name=svc)))",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(PORT=1521))"
                                + "(ADDRESS=(PROTOCOL=TCP)(HOST=b)(PORT=1522)))(CONNECT_DATA=(SERVICE_NAME=svc)))",
                        List.of(host("a", 1521), host("b", 1522)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(PORT=1521))"
                                + "(ADDRESS=(PROTOCOL=TCP)(HOST=b)(PORT=1522))(CONNECT_DATA=(SERVICE_NAME=svc)))",
                        List.of(host("a", 1521), host("b", 1522)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(CONNECT_DATA=(SERVICE_NAME=svc)(INSTANCE_NAME=i1)))",
                        List.of(host("db", 1521, "i1")),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SID=x))(PW=(SERVICE_NAME=S3Cx)))",
                        List.of(host("db", 1521)),
                        "x",
                        SID,
                        descriptor("x")),
                bareDescriptor(
                        "(DESCRIPTION=(SECURITY=(SSL_SERVER_CERT_DN=\"(HOST=S3Cx)\"))"
                                + "(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=svc)))",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(CONNECT_DATA=(SERVICE_NAME=svc)(CID=(PROGRAM=x)(HOST=y)(USER=ZUSR))))",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                // With both present the server decides; the library keeps 0.1.3's SERVICE_NAME preference [G15].
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(CONNECT_DATA=(SID=ORCL)(SERVICE_NAME=svc)))",
                        List.of(host("db", 1521)),
                        "svc",
                        SID,
                        descriptor("ORCL"),
                        SERVICE_NAME,
                        descriptor("svc")),
                descriptorRow(
                        "@" + D1 + "?x=1",
                        D1,
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "x",
                        query("1")),
                descriptorRow("@" + D1 + "))", D1, List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                bareDescriptor(
                        "(DESCRIPTION_LIST=(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(PORT=1521))"
                                + "(CONNECT_DATA=(SERVICE_NAME=svc)))(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=b)"
                                + "(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=svc2))))",
                        List.of(host("a", 1521), host("b", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                descriptorRow(
                        "ZUSR/S3Cx@" + D1,
                        D1,
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "user",
                        authority("ZUSR"),
                        "password",
                        authority("S3Cx")));
    }

    @ParameterizedTest
    @MethodSource("descriptorReadings")
    void readsDescriptorAsDriverDoes(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<Arguments> descriptorEdgeReadings() {

        return Stream.of(
                descriptorRow("@ " + D1, D1, List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                descriptorRow("@ \t" + D1 + " ", D1, List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                descriptorRow(
                        "@ " + D1 + " ?x=1",
                        D1,
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "x",
                        query("1")),
                descriptorRow(
                        "@" + D1 + "? x = 1",
                        D1,
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "x",
                        query("1")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(SECURITY=(SSL_SERVER_CERT_DN=\"CN=x,E=a@b.com\"))(CONNECT_DATA=(SERVICE_NAME=svc)))",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                descriptorRow(
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(SECURITY=(SSL_SERVER_CERT_DN=\"CN=x?y\"))(CONNECT_DATA=(SERVICE_NAME=svc)))?a=1",
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(SECURITY=(SSL_SERVER_CERT_DN=\"CN=x?y\"))(CONNECT_DATA=(SERVICE_NAME=svc)))",
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "a",
                        query("1")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=svc@x)))",
                        List.of(host("db", 1521)),
                        "svc@x",
                        SERVICE_NAME,
                        descriptor("svc@x")),
                descriptorRow(
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(CONNECT_DATA=(SERVICE_NAME=svc?x=1)))?y=2",
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(CONNECT_DATA=(SERVICE_NAME=svc?x=1)))",
                        List.of(host("db", 1521)),
                        "svc?x=1",
                        SERVICE_NAME,
                        descriptor("svc?x=1"),
                        "y",
                        query("2")),
                // ojdbc stops reading at a surplus ')', so it never sees the text or query after it.
                descriptorRow(
                        "@" + D1 + ")junk", D1, List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                descriptorRow(
                        "@" + D1 + ")?x=1", D1, List.of(host("db", 1521)), "svc", SERVICE_NAME, descriptor("svc")),
                descriptorRow(
                        "@" + D1 + "?a=1&b=(",
                        D1,
                        List.of(host("db", 1521)),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc"),
                        "a",
                        query("1"),
                        "b",
                        query("(")),
                bareDescriptor("(ADDRESS=(PROTOCOL=TCP)(HOST=db))", List.of(host("db", 1521)), ""),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SERVICE_NAME=s\\)v)))",
                        List.of(host("db", 1521)),
                        "s)v",
                        SERVICE_NAME,
                        descriptor("s)v")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SID=x\\y)))",
                        List.of(host("db", 1521)),
                        "x\\y",
                        SID,
                        descriptor("x\\y")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SERVICE_NAME=\"svc\")))",
                        List.of(host("db", 1521)),
                        "\"svc\"",
                        SERVICE_NAME,
                        descriptor("\"svc\"")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SID=x=y,z)))",
                        List.of(host("db", 1521)),
                        "x=y,z",
                        SID,
                        descriptor("x=y,z")),
                bareDescriptor(
                        "( DESCRIPTION = ( ADDRESS = (PROTOCOL=TCP)(HOST= db )(PORT= 1521 ))"
                                + "(CONNECT_DATA=(SERVICE_NAME = svc x )))",
                        List.of(host("db", 1521)),
                        "svc x",
                        SERVICE_NAME,
                        descriptor("svc x")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=)(PORT=))(CONNECT_DATA=(SID=x)))",
                        List.of(host("localhost", 1521)),
                        "x",
                        SID,
                        descriptor("x")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(HOST=b)(PORT=1)(PORT=2))(CONNECT_DATA=(SID=x)))",
                        List.of(host("a", 1)),
                        "x",
                        SID,
                        descriptor("x")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(PORT=+1521))(CONNECT_DATA=(SID=x)))",
                        List.of(host("a", 1521)),
                        "x",
                        SID,
                        descriptor("x")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=[::1])(PORT=0))(CONNECT_DATA=(SID=x)))",
                        List.of(host("[::1]", 0)),
                        "x",
                        SID,
                        descriptor("x")),
                bareDescriptor("(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)))", List.of(host("a", 1521)), ""),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a))(CONNECT_DATA=(SERVICE_NAME=s1)(SERVICE_NAME=)))",
                        List.of(host("a", 1521)),
                        ""),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a))(CONNECT_DATA=(SERVICE_NAME=(X=1))))",
                        List.of(host("a", 1521)),
                        ""),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a))(CONNECT_DATA=(SID=x))"
                                + "(connect_data=(service_name=s)))",
                        List.of(host("a", 1521)),
                        "s",
                        SID,
                        descriptor("x"),
                        SERVICE_NAME,
                        descriptor("s")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=b))(CONNECT_DATA=(SID)))",
                        List.of(host("b", 1521)),
                        "SID",
                        SID,
                        descriptor("SID")),
                bareDescriptor(
                        "(DESCRIPTION_LIST=(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(PORT=1521))"
                                + "(CONNECT_DATA=(SERVICE_NAME=svc)(INSTANCE_NAME=i1)))(DESCRIPTION=(ADDRESS="
                                + "(PROTOCOL=TCP)(HOST=b)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=svc2)(INSTANCE_NAME=i2))))",
                        List.of(host("a", 1521, "i1"), host("b", 1521, "i2")),
                        "svc",
                        SERVICE_NAME,
                        descriptor("svc")),
                bareDescriptor(
                        "(DESCRIPTION_LIST=(LOAD_BALANCE=off)(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=b))))",
                        List.of(host("b", 1521)),
                        ""),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS_LIST=(LOAD_BALANCE=on)(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=a)))"
                                + "(ADDRESS=(PROTOCOL=TCP)(HOST=b)))(CONNECT_DATA=(SID=x)))",
                        List.of(host("a", 1521), host("b", 1521)),
                        "x",
                        SID,
                        descriptor("x")),
                bareDescriptor(
                        "(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(ADDRESS=(PROTOCOL=TCP)(HOST=b)))"
                                + "(FOO=(ADDRESS=(PROTOCOL=TCP)(HOST=c)))"
                                + "(CONNECT_DATA=(SID=x)(ADDRESS=(PROTOCOL=TCP)(HOST=d))))",
                        List.of(host("a", 1521)),
                        "x",
                        SID,
                        descriptor("x")));
    }

    @ParameterizedTest
    @MethodSource("descriptorEdgeReadings")
    void readsDescriptorEdgesAsDriverDoes(
            String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        assertParsed(url, hosts, databaseName, properties);
    }

    static Stream<String> descriptorDriverRejections() {

        return Stream.of(
                        "@(DESCRIPTION=(ADDRESS=(HOST=db)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=svc)))",
                        "@(DESCRIPTION=(CONNECT_DATA=(SERVICE_NAME=svc)))",
                        "@(foo=bar)",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=x))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SID=x)))?x=(SERVICE_NAME=S3Cx)",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SID=x))) (PORT=4242)",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SID=x)))(PORT=4242)",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SID=x)))?password=(PORT=4242)",
                        "@" + D1 + "junk",
                        "@" + D1 + "#frag",
                        "@" + D1 + ";x",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST==db))(CONNECT_DATA=(SID=x)))",
                        "@" + "(A=".repeat(200) + "x" + ")".repeat(200),
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)=(HOST=db))(CONNECT_DATA=(SID=x)))",
                        "@" + D1 + "?",
                        "@" + D1 + "?x",
                        "@" + D1 + "?&a=1",
                        "@" + D1 + "?a=1&b=2?c=3",
                        "@" + D1 + D1,
                        "@" + D1 + " junk",
                        // ojdbc finds the '?' in the trimmed text and cuts the untrimmed one there, dropping a ')'.
                        "@ " + D1 + "?x=1",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))"
                                + "(SECURITY=(SSL_SERVER_CERT_DN=\"CN=x)?y\"))(CONNECT_DATA=(SERVICE_NAME=svc)))?y=2",
                        "@(A=b?c@d)",
                        "ZUSR/S3C@x@" + D1,
                        "@(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=db)))",
                        "@(DESCRIPTION=)",
                        "@(DESCRIPTION=x)",
                        "@()",
                        "@(=x)",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a))(CONNECT_DATA=(SID=x))(=y))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a))(CONNECT_DATA=(SID=x)) junk (Z=1))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=)(HOST=a))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=)(PROTOCOL=TCP)(HOST=b))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=(X=1))(HOST=b))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=x)(ADDRESS=(PROTOCOL=TCP)(HOST=b))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(PORT=65536))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(PORT=99999999999))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a)(PORT=-1))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=b)(PORT))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a))(CONNECT_DATA=))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=b))(CONNECT_DATA=x))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))(SECURITY=))",
                        "@(DESCRIPTION=(ADDRESS_LIST=(FOO=1)(ADDRESS=(PROTOCOL=TCP)(HOST=a)))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS_LIST=(LOAD_BALANCE=on))(ADDRESS=(PROTOCOL=TCP)(HOST=b))"
                                + "(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS_LIST=)(ADDRESS=(PROTOCOL=TCP)(HOST=b))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION_LIST=(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a))(CONNECT_DATA=(SID=x)))"
                                + "(ADDRESS=(PROTOCOL=TCP)(HOST=b)))",
                        "@(DESCRIPTION_LIST=(DESCRIPTION=(CONNECT_DATA=(SID=x)))(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)"
                                + "(HOST=b))(CONNECT_DATA=(SID=y))))",
                        "@(DESCRIPTION_LIST=(FOO=1)(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=b))))",
                        "@(DESCRIPTION_LIST=(LOAD_BALANCE=off))",
                        "@(DESCRIPTION_LIST=)",
                        "@(DESCRIPTION=(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=b))))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a))(CONNECT_DATA=(SID=\"x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=b))(CONNECT_DATA=(SID=\"a\"b)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a))(CONNECT_DATA=(SID=x\\",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))(CONNECT_DATA=(SID=,x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=a))(CONNECT_DATA=(SID=x)),(Z=1))")
                .map(connect -> THIN + connect);
    }

    @ParameterizedTest
    @MethodSource("descriptorDriverRejections")
    void rejectsDescriptorDriverRejects(String url) {

        assertRejected(url);
    }

    private static String nestedDescriptor(int levels) {

        return "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))" + "(X=".repeat(levels - 1) + "(X=1)"
                + ")".repeat(levels - 1) + ")";
    }

    @Test
    void readsDescriptorNestedToDepthLimit() {

        String url = THIN + nestedDescriptor(64);

        assertParsed(
                url,
                List.of(host("db", 1521)),
                "",
                props(DRIVER_TYPE, derived("thin"), "DESCRIPTOR", descriptor(url.substring(THIN.length() + 1))));
    }

    // ojdbc connects to these. The library refuses a host that cannot resolve, comma-separated NV lists and nesting
    // beyond 64 levels.
    static Stream<String> descriptorDeviations() {

        return Stream.of(
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=ZUSR@db)(PORT=1521))"
                                + "(CONNECT_DATA=(SERVICE_NAME=svc)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db?x)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=svc)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=\"db\"))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=::1))(CONNECT_DATA=(SID=x)))",
                        "@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db))(CONNECT_DATA=(SID=x))(Z,Y=1))",
                        nestedDescriptor(65),
                        nestedDescriptor(200))
                .map(connect -> THIN + connect);
    }

    @ParameterizedTest
    @MethodSource("descriptorDeviations")
    void rejectsDescriptorDriverReadsDifferently(String url) {

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
