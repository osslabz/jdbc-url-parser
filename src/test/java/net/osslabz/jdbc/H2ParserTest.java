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

        String url = "jdbc:h2:mem:testdb?MODE=PostgreSQL&DATABASE_TO_LOWER=TRUE";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals("testdb", parsed.databaseName());
        assertEquals("PostgreSQL", parsed.getPropertyValue("MODE"));
        assertEquals("TRUE", parsed.getPropertyValue("DATABASE_TO_LOWER"));
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
}
