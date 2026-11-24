package net.osslabz.jdbc.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;
import net.osslabz.jdbc.PropertySource;

/**
 * Parser for MySQL JDBC URLs, read as Connector/J's {@code ConnectionUrlParser} and {@code ConnectionUrl} read them.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:mysql://host:port/database</li>
 *   <li>jdbc:mysql://host1:port1,host2:port2/database (multi-host)</li>
 *   <li>jdbc:mysql://host/database?param=value</li>
 *   <li>jdbc:mysql:///database (localhost)</li>
 *   <li>jdbc:mysql://user:password@host/database (userinfo)</li>
 * </ul>
 *
 * <p>Host, path and query are percent-decoded. The query keys {@code host}, {@code port} and {@code dbname}, in any
 * case, stand in for a host, port or database the URL leaves out; {@code dbname} overrides the path. The first host's
 * userinfo is reported as {@code user} and {@code password} {@link PropertySource#AUTHORITY} properties and wins
 * over the query.
 */
public class MySQLParser extends AbstractUrlParser {

    static final int DEFAULT_PORT = 3306;

    private static final String PREFIX = "jdbc:mysql:";

    private static final String DEFAULT_HOST = "localhost";

    private static final String USER = "user";

    private static final String PASSWORD = "password";

    // Connector/J's CONNECTION_STRING_PTRN; the fragment is discarded.
    private static final Pattern CONNECTION_STRING = Pattern.compile("(?<scheme>[\\w\\+:%]+)\\s*"
            + "(?://(?<authority>[^/?#]*))?\\s*"
            + "(?:/(?!\\s*/)(?<path>[^?#]*))?"
            + "(?:\\?(?!\\s*\\?)(?<query>[^#]*))?"
            + "(?:\\s*#.*)?");

    private static final Pattern QUERY_KEY = Pattern.compile("[\\w.\\-\\s%]*");

    private static final String OPENING_MARKERS = "[(";

    private static final String CLOSING_MARKERS = "])";

    private static final List<String> TRUE_VALUES = List.of("TRUE", "YES");

    public MySQLParser() {

        super(DatabaseProduct.MYSQL);
    }

    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);
        Matcher sections = CONNECTION_STRING.matcher(url);
        if (!sections.matches()) {
            throw new JdbcUrlParseException(url, "Malformed MySQL URL");
        }
        String scheme = sections.group("scheme");
        if (!PREFIX.equalsIgnoreCase(scheme)) {
            throw new JdbcUrlParseException(url, "Unsupported MySQL URL scheme: " + scheme);
        }
        String path = sections.group("path");
        String pathDatabase = path == null ? "" : percentDecode(url, path).trim();

        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        Map<String, String> query = readQuery(url, sections.group("query"), properties);
        rejectDnsSrv(url, properties);
        List<MySQLHostSpec> specs = hostSpecs(url, sections.group("authority"));
        putAuthorityProperties(specs.get(0), properties);
        List<Host> hosts = new ArrayList<>();
        for (MySQLHostSpec spec : specs) {
            hosts.add(resolve(url, spec, query));
        }

        DatabaseProduct dbType = DatabaseProduct.MYSQL;
        return new JdbcUrl(
                url, dbType, extractProtocol(url, dbType), hosts, databaseName(query, pathDatabase), properties);
    }

    /**
     * Reads the query into {@code properties} under the written keys, and returns the same options keyed
     * case-insensitively, as the driver's {@code PropertyKey.normalizeCase} keys {@code host}, {@code port} and
     * {@code dbname}. A flag maps to null there and to "" in {@code properties}.
     */
    private static Map<String, String> readQuery(String url, String queryText, Map<String, JdbcProperty> properties) {

        Map<String, String> query = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (queryText == null) {
            return query;
        }
        for (RawPair pair : splitPairs(queryText, '&')) {
            if (!QUERY_KEY.matcher(pair.key()).matches()) {
                throw new JdbcUrlParseException(url, "Invalid query key: " + pair.key());
            }
            String key = percentDecode(url, pair.key().trim());
            String value = pair.value() == null
                    ? null
                    : percentDecode(url, pair.value().trim());
            if (!key.isEmpty()) {
                query.put(key, value);
                properties.put(key, new JdbcProperty(PropertySource.QUERY, value == null ? "" : value));
            } else if (!isNullOrEmpty(value)) {
                throw new JdbcUrlParseException(url, "Query value without a key: " + value);
            }
        }
        return query;
    }

    // With dnsSrv on, the driver takes its hosts from DNS SRV records; it matches the key in exactly this case.
    private static void rejectDnsSrv(String url, Map<String, JdbcProperty> properties) {

        JdbcProperty dnsSrv = properties.get("dnsSrv");
        if (dnsSrv != null && TRUE_VALUES.contains(dnsSrv.value().toUpperCase(Locale.ROOT))) {
            throw new JdbcUrlParseException(url, "dnsSrv takes the hosts from DNS, not from the URL");
        }
    }

    private static String databaseName(Map<String, String> query, String pathDatabase) {

        if (query.containsKey("dbname")) {
            String database = query.get("dbname");
            return database == null ? "" : database;
        }
        return pathDatabase;
    }

    private static List<MySQLHostSpec> hostSpecs(String url, String authority) {

        List<MySQLHostSpec> specs = new ArrayList<>();
        for (String segment : authoritySegments(authority)) {
            specs.addAll(MySQLHostSpec.parse(url, segment));
        }
        return specs;
    }

    // The driver splits on ',' outside [...] and (...) blocks, trims each segment and keeps empty ones.
    private static List<String> authoritySegments(String authority) {

        List<String> segments = new ArrayList<>();
        if (authority == null) {
            segments.add("");
            return segments;
        }
        int segmentStart = 0;
        for (int i = 0; i < authority.length(); i = blockEnd(authority, i) + 1) {
            if (authority.charAt(i) == ',') {
                segments.add(authority.substring(segmentStart, i).trim());
                segmentStart = i + 1;
            }
        }
        segments.add(authority.substring(segmentStart).trim());
        return segments;
    }

    // An unclosed block runs to the end; only markers of the block's own kind nest.
    private static int blockEnd(String text, int start) {

        int kind = OPENING_MARKERS.indexOf(text.charAt(start));
        if (kind < 0) {
            return start;
        }
        char opening = OPENING_MARKERS.charAt(kind);
        char closing = CLOSING_MARKERS.charAt(kind);
        int depth = 0;
        int i = start + 1;
        while (i < text.length() && (text.charAt(i) != closing || depth > 0)) {
            if (text.charAt(i) == opening) {
                depth++;
            } else if (text.charAt(i) == closing) {
                depth--;
            }
            i++;
        }
        return i;
    }

    // The userinfo beats the query for user and password.
    private static void putAuthorityProperties(MySQLHostSpec primary, Map<String, JdbcProperty> properties) {

        if (!isNullOrEmpty(primary.user())) {
            properties.put(USER, new JdbcProperty(PropertySource.AUTHORITY, primary.user()));
        }
        if (primary.password() != null) {
            properties.put(PASSWORD, new JdbcProperty(PropertySource.AUTHORITY, primary.password()));
        }
    }

    private static Host resolve(String url, MySQLHostSpec spec, Map<String, String> options) {

        String hostname = spec.host();
        if (hostname == null) {
            hostname = isNullOrEmpty(options.get("host")) ? DEFAULT_HOST : options.get("host");
        }
        Integer port = spec.port() != null ? spec.port() : MySQLHostSpec.writtenPort(url, options.get("port"));
        return Host.of(requireHostname(url, hostname), port == null ? DEFAULT_PORT : port);
    }

    private static boolean isNullOrEmpty(String text) {

        return text == null || text.isEmpty();
    }
}
