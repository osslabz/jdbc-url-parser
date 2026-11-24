package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assertion helpers that compare a parsed URL against its complete expected result.
 */
final class ParsedUrls {

    private ParsedUrls() {}

    static Host host(String hostname, Integer port) {

        return new Host(hostname, port, null);
    }

    static Host host(String hostname, Integer port, String instanceName) {

        return new Host(hostname, port, instanceName);
    }

    static JdbcProperty query(String value) {

        return new JdbcProperty(PropertySource.QUERY, value);
    }

    static JdbcProperty path(String value) {

        return new JdbcProperty(PropertySource.PATH, value);
    }

    static JdbcProperty authority(String value) {

        return new JdbcProperty(PropertySource.AUTHORITY, value);
    }

    static JdbcProperty derived(String value) {

        return new JdbcProperty(PropertySource.DERIVED, value);
    }

    static JdbcProperty descriptor(String value) {

        return new JdbcProperty(PropertySource.DESCRIPTOR, value);
    }

    /**
     * Builds an insertion-ordered map from alternating {@code String} keys and {@link JdbcProperty} values.
     */
    static Map<String, JdbcProperty> props(Object... keysAndProperties) {

        if (keysAndProperties.length % 2 != 0) {
            throw new IllegalArgumentException("props() needs alternating keys and properties");
        }
        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        for (int i = 0; i < keysAndProperties.length; i += 2) {
            properties.put((String) keysAndProperties[i], (JdbcProperty) keysAndProperties[i + 1]);
        }
        return properties;
    }

    static void assertParsed(String url, List<Host> hosts, String databaseName, Map<String, JdbcProperty> properties) {

        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(hosts, parsed.hosts(), url);
        assertEquals(databaseName, parsed.databaseName(), url);
        assertEquals(properties, parsed.properties(), url);
    }

    static void assertRejected(String url) {

        assertThrows(JdbcUrlParseException.class, () -> JdbcUrlParser.parse(url), url);
        assertNull(JdbcUrlParser.tryParse(url), url);
    }
}
