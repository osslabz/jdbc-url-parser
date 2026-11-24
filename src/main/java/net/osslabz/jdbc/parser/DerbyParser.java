package net.osslabz.jdbc.parser;

import java.util.List;
import java.util.Map;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;

/**
 * Parser for Apache Derby JDBC URLs, read as Derby's embedded driver and network client read them.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:derby:database (embedded)</li>
 *   <li>jdbc:derby:database;create=true</li>
 *   <li>jdbc:derby:/path/to/database</li>
 *   <li>jdbc:derby:memory:testdb (in-memory)</li>
 *   <li>jdbc:derby:;databaseName=database</li>
 *   <li>jdbc:derby://host:port/database (network/client mode)</li>
 * </ul>
 *
 * <p>The prefix is case-sensitive. Attributes follow the first ';', separated by ';', and each needs an '='.
 */
public class DerbyParser extends AbstractUrlParser {

    static final int DEFAULT_NETWORK_PORT = 1527;

    private static final String PREFIX = "jdbc:derby:";

    private static final String CLIENT_PREFIX = "jdbc:derby://";

    private static final String DATABASE_NAME = "databaseName";

    private static final String CLIENT_DATABASE_DELIMITERS = "\t\n\r\f;";

    private enum Subsubprotocol {
        MEMORY("memory:", "MEMORY", true),
        DIRECTORY("directory:", "EMBEDDED", true),
        CLASSPATH("classpath:", "EMBEDDED", false),
        JAR("jar:", "EMBEDDED", false),
        HTTP("http:", "EMBEDDED", false),
        HTTPS("https:", "EMBEDDED", false);

        private final String prefix;

        private final String mode;

        private final boolean prefixStripped;

        Subsubprotocol(String prefix, String mode, boolean prefixStripped) {

            this.prefix = prefix;
            this.mode = mode;
            this.prefixStripped = prefixStripped;
        }
    }

    public DerbyParser() {

        super(DatabaseProduct.DERBY);
    }

    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);
        requireExactPrefix(url, PREFIX);
        String protocol = extractProtocol(url, DatabaseProduct.DERBY);

        int attributesStart = url.indexOf(';');
        Map<String, JdbcProperty> attributes =
                readAttributes(url, attributesStart < 0 ? "" : url.substring(attributesStart + 1));
        if (url.startsWith(CLIENT_PREFIX)) {
            return parseClient(url, protocol, url.substring(CLIENT_PREFIX.length()), attributes);
        }
        String location = url.substring(PREFIX.length(), attributesStart < 0 ? url.length() : attributesStart);
        return parseEmbedded(url, protocol, location, attributes);
    }

    private static Map<String, JdbcProperty> readAttributes(String url, String text) {

        List<RawPair> pairs = splitPairs(text, ';');
        for (RawPair pair : pairs) {
            if (pair.value() == null) {
                throw new JdbcUrlParseException(url, "Attribute without '='");
            }
        }
        return toTrimmedPathProperties(pairs);
    }

    private static JdbcUrl parseEmbedded(
            String url, String protocol, String location, Map<String, JdbcProperty> attributes) {

        // Only an empty location defers to databaseName; a blank one trims to an empty name.
        String name = (location.isEmpty() ? writtenDatabaseName(attributes, location) : location).trim();
        int colon = name.indexOf(':');
        // A colon at index 0 or 1 is no subsubprotocol, so Windows drive letters pass.
        if (colon < 2) {
            addDerivedModeUnlessWritten(attributes, "EMBEDDED");
            return new JdbcUrl(url, DatabaseProduct.DERBY, protocol, List.of(), name, attributes);
        }
        Subsubprotocol subsubprotocol = subsubprotocolOf(url, name);
        addDerivedModeUnlessWritten(attributes, subsubprotocol.mode);
        String database = subsubprotocol.prefixStripped ? name.substring(subsubprotocol.prefix.length()) : name;
        return new JdbcUrl(url, DatabaseProduct.DERBY, protocol, List.of(), database, attributes);
    }

    private static Subsubprotocol subsubprotocolOf(String url, String name) {

        for (Subsubprotocol subsubprotocol : Subsubprotocol.values()) {
            if (name.startsWith(subsubprotocol.prefix)) {
                return subsubprotocol;
            }
        }
        throw new JdbcUrlParseException(url, "Unknown subsubprotocol");
    }

    private static JdbcUrl parseClient(
            String url, String protocol, String hostPortAndDatabase, Map<String, JdbcProperty> attributes) {

        int hostEnd = indexOfAny(hostPortAndDatabase, "/:");
        if (hostEnd < 0) {
            throw new JdbcUrlParseException(url, "Missing database");
        }
        String hostname = requireHostname(url, hostPortAndDatabase.substring(0, hostEnd));
        int port = DEFAULT_NETWORK_PORT;
        int pathStart = hostEnd;
        if (hostPortAndDatabase.charAt(hostEnd) == ':') {
            pathStart = hostPortAndDatabase.indexOf('/', hostEnd);
            if (pathStart < 0) {
                throw new JdbcUrlParseException(url, "Missing database");
            }
            port = clientPort(url, hostPortAndDatabase.substring(hostEnd + 1, pathStart));
        }
        String database = clientDatabase(url, hostPortAndDatabase.substring(pathStart + 1), attributes);
        addDerivedModeUnlessWritten(attributes, "NETWORK");
        return new JdbcUrl(
                url, DatabaseProduct.DERBY, protocol, List.of(Host.of(hostname, port)), database, attributes);
    }

    private static int clientPort(String url, String portText) {

        int port = parsePort(url, portText);
        return port == 0 ? DEFAULT_NETWORK_PORT : port;
    }

    // The server receives the name with the attributes appended, trims it, and only then defers to databaseName.
    private static String clientDatabase(String url, String path, Map<String, JdbcProperty> attributes) {

        int end = indexOfAny(path, CLIENT_DATABASE_DELIMITERS);
        String name = path.substring(0, end < 0 ? path.length() : end).trim();
        String database = name.isEmpty() ? writtenDatabaseName(attributes, name) : name;
        if (database.isEmpty()) {
            throw new JdbcUrlParseException(url, "Missing database");
        }
        return database;
    }

    private static String writtenDatabaseName(Map<String, JdbcProperty> attributes, String otherwise) {

        JdbcProperty databaseName = attributes.get(DATABASE_NAME);
        return databaseName == null ? otherwise : databaseName.value();
    }

    private static int indexOfAny(String text, String characters) {

        for (int i = 0; i < text.length(); i++) {
            if (characters.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }
}
