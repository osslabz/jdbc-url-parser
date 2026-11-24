package net.osslabz.jdbc.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;
import net.osslabz.jdbc.PropertySource;

/**
 * Parser for HSQLDB (HyperSQL) JDBC URLs, read as HSQLDB's {@code DatabaseURL.parseURL} reads them.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:hsqldb:mem:testdb (in-memory)</li>
 *   <li>jdbc:hsqldb:file:/path/to/database</li>
 *   <li>jdbc:hsqldb:/path/to/database (implied file)</li>
 *   <li>jdbc:hsqldb:res:/path/to/database (resource)</li>
 *   <li>jdbc:hsqldb:hsql://host:port/database (network)</li>
 *   <li>jdbc:hsqldb:hsqls://host:port/database (network with SSL)</li>
 *   <li>jdbc:hsqldb:http://host:port/database</li>
 *   <li>jdbc:hsqldb:https://host:port/database</li>
 * </ul>
 *
 * <p>Prefixes match in any case. Properties follow the first ';', separated by ';'. Before the ';', a
 * {@code ?user=} and a {@code &password=} marker carry credentials; any other '?' belongs to the database name.
 */
public class HSQLDBParser extends AbstractUrlParser {

    static final int DEFAULT_HSQL_PORT = 9001;
    static final int DEFAULT_HSQLS_PORT = 554;
    static final int DEFAULT_HTTP_PORT = 80;
    static final int DEFAULT_HTTPS_PORT = 443;

    private static final String MODE = "MODE";

    private static final String DEFAULT_HOST = "localhost";

    private static final String USER_MARKER = "?user=";

    private static final String PASSWORD_MARKER = "&password=";

    private enum Server {
        HSQL("hsql://", DEFAULT_HSQL_PORT),
        HSQLS("hsqls://", DEFAULT_HSQLS_PORT),
        HTTP("http://", DEFAULT_HTTP_PORT),
        HTTPS("https://", DEFAULT_HTTPS_PORT);

        private final String prefix;

        private final int defaultPort;

        Server(String prefix, int defaultPort) {

            this.prefix = prefix;
            this.defaultPort = defaultPort;
        }
    }

    public HSQLDBParser() {

        super(DatabaseProduct.HSQLDB);
    }

    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);
        String protocol = extractProtocol(url, DatabaseProduct.HSQLDB);

        int settingsStart = url.indexOf(';');
        String location =
                settingsStart < 0 ? url.substring(protocol.length()) : url.substring(protocol.length(), settingsStart);
        Map<String, JdbcProperty> properties =
                settingsStart < 0 ? new LinkedHashMap<>() : readSettings(url.substring(settingsStart + 1));

        String foldedLocation = location.toLowerCase(Locale.ROOT);
        for (Server server : Server.values()) {
            if (foldedLocation.startsWith(server.prefix)) {
                return parseServer(url, protocol, server, location.substring(server.prefix.length()), properties);
            }
        }
        String database = inProcessDatabase(url, location, properties);
        return new JdbcUrl(url, DatabaseProduct.HSQLDB, protocol, List.of(), database, properties);
    }

    private static Map<String, JdbcProperty> readSettings(String settings) {

        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        for (RawPair pair : splitPairs(settings, ';')) {
            if (pair.value() != null) {
                properties.put(
                        pair.key().trim(),
                        new JdbcProperty(PropertySource.PATH, pair.value().trim()));
            }
        }
        return properties;
    }

    private static String inProcessDatabase(String url, String location, Map<String, JdbcProperty> properties) {

        String foldedLocation = location.toLowerCase(Locale.ROOT);
        if (".".equals(location)) {
            addDerivedMode(properties, "MEMORY");
            return location;
        }
        if (foldedLocation.startsWith("mem:")) {
            addDerivedMode(properties, "MEMORY");
            String name = requireName(url, location.substring("mem:".length()));
            return removeCredentials(name, true, properties).toLowerCase(Locale.ROOT);
        }
        if (foldedLocation.startsWith("res:")) {
            addDerivedMode(properties, "RESOURCE");
            String name = requireName(url, location.substring("res:".length()));
            return removeCredentials(name.startsWith("/") ? name : "/" + name, false, properties);
        }
        if (foldedLocation.startsWith("alias:")) {
            throw new JdbcUrlParseException(url, "The alias: connection type is not implemented");
        }
        addDerivedMode(properties, "FILE");
        String name = foldedLocation.startsWith("file:") ? location.substring("file:".length()) : location;
        return removeCredentials(requireName(url, name), false, properties);
    }

    private static String requireName(String url, String name) {

        if (name.isEmpty()) {
            throw new JdbcUrlParseException(url, "Database name cannot be empty");
        }
        return name;
    }

    private static JdbcUrl parseServer(
            String url, String protocol, Server server, String hostAndPath, Map<String, JdbcProperty> properties) {

        // DatabaseURL reads past the end of a URL that stops right after the scheme and fails.
        if (hostAndPath.isEmpty() && url.indexOf(';') < 0) {
            throw new JdbcUrlParseException(url, "Server URL without host");
        }
        int slash = hostAndPath.indexOf('/');
        String authority = slash < 0 ? hostAndPath : hostAndPath.substring(0, slash);
        Host host = parseAuthority(url, authority.toLowerCase(Locale.ROOT), server.defaultPort);
        String database = slash < 0 ? "" : serverDatabase(hostAndPath.substring(slash), properties);
        addDerivedMode(properties, server.name());
        return new JdbcUrl(url, DatabaseProduct.HSQLDB, protocol, List.of(host), database, properties);
    }

    private static Host parseAuthority(String url, String authority, int defaultPort) {

        if (authority.startsWith("[")) {
            return parseBracketedAuthority(url, authority, defaultPort);
        }
        // DatabaseURL looks for the port separator from the host's second character on.
        int colon = authority.indexOf(':', 1);
        String hostname = colon < 0 ? authority : authority.substring(0, colon);
        int port = colon < 0 ? defaultPort : parsePort(url, authority.substring(colon + 1));
        return Host.of(requireHostname(url, hostname.isEmpty() ? DEFAULT_HOST : hostname), port);
    }

    private static Host parseBracketedAuthority(String url, String authority, int defaultPort) {

        int literalEnd = authority.indexOf(']', 2);
        if (literalEnd < 0) {
            throw new JdbcUrlParseException(url, "Unterminated IPv6 literal");
        }
        String afterLiteral = authority.substring(literalEnd + 1);
        if (!afterLiteral.isEmpty() && afterLiteral.charAt(0) != ':') {
            throw new JdbcUrlParseException(url, "Invalid text after IPv6 literal: " + afterLiteral);
        }
        int port = afterLiteral.isEmpty() ? defaultPort : parsePort(url, afterLiteral.substring(1));
        return Host.of(requireHostname(url, authority.substring(0, literalEnd + 1)), port);
    }

    private static String serverDatabase(String path, Map<String, JdbcProperty> properties) {

        int lastSlash = path.lastIndexOf('/');
        boolean singleSegment = lastSlash == 0;
        String database = removeCredentials(path.substring(lastSlash + 1), singleSegment, properties);
        return singleSegment ? database.toLowerCase(Locale.ROOT) : database;
    }

    /**
     * Moves the credential markers out of a database name: {@code &password=} takes the rest of the name, then
     * {@code ?user=} takes what precedes it. They override a same-named property, as the driver sets them last.
     *
     * @param name                  the database name as written
     * @param driverLowercasesName  whether the driver lowercases the name before it looks for the markers
     * @param properties            receives the credentials, in their written case
     * @return the name without the markers
     */
    private static String removeCredentials(
            String name, boolean driverLowercasesName, Map<String, JdbcProperty> properties) {

        String remaining = name;
        for (String marker : List.of(PASSWORD_MARKER, USER_MARKER)) {
            int markerStart = indexOfMarker(remaining, marker, driverLowercasesName);
            if (markerStart >= 0) {
                String key = remaining.substring(markerStart + 1, markerStart + marker.length() - 1);
                String value = remaining.substring(markerStart + marker.length());
                properties.put(key, new JdbcProperty(PropertySource.QUERY, value));
                remaining = remaining.substring(0, markerStart);
            }
        }
        return remaining;
    }

    private static int indexOfMarker(String name, String marker, boolean anyCase) {

        if (!anyCase) {
            return name.indexOf(marker);
        }
        for (int start = 0; start + marker.length() <= name.length(); start++) {
            if (lowercasesTo(name, start, marker)) {
                return start;
            }
        }
        return -1;
    }

    // Compares char by char so that indexes stay those of the written name.
    private static boolean lowercasesTo(String text, int start, String marker) {

        for (int i = 0; i < marker.length(); i++) {
            if (Character.toLowerCase(text.charAt(start + i)) != marker.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    // HSQLDB keeps property keys as written, so only an exactly written MODE replaces the derived one.
    private static void addDerivedMode(Map<String, JdbcProperty> properties, String mode) {

        properties.putIfAbsent(MODE, new JdbcProperty(PropertySource.DERIVED, mode));
    }
}
