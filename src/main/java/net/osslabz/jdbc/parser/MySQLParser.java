package net.osslabz.jdbc.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
 *   <li>jdbc:mysql://address=(host=host)(port=port)(user=user)/database (host attributes)</li>
 *   <li>jdbc:mysql://(host=host,port=port,user=user)/database (host attributes)</li>
 *   <li>jdbc:mysql://user:password@[host1,host2]/database (host list sharing one userinfo)</li>
 *   <li>jdbc:mysql:loadbalance://host1,host2/database and jdbc:mysql:replication://host1,host2/database</li>
 * </ul>
 *
 * <p>Host, path and query are percent-decoded. The query keys {@code host}, {@code port} and {@code dbname}, in any
 * case, stand in for a host, port or database the URL leaves out; {@code dbname} overrides the path. A host's
 * attributes override the query for that host. The first host's attributes and userinfo are reported as
 * {@link PropertySource#AUTHORITY} properties; its userinfo wins over both for {@code user} and {@code password}.
 */
public class MySQLParser extends AbstractUrlParser {

    static final int DEFAULT_PORT = 3306;

    private static final String PREFIX = "jdbc:mysql:";

    private static final List<String> SUB_PROTOCOLS = List.of("loadbalance", "replication");

    private static final String DEFAULT_HOST = "localhost";

    private static final String USER = "user";

    private static final String PASSWORD = "password";

    private static final Set<String> CREDENTIAL_KEYS = credentialKeys();

    // Connector/J's CONNECTION_STRING_PTRN; the fragment is discarded.
    private static final Pattern CONNECTION_STRING = Pattern.compile("(?<scheme>[\\w\\+:%]+)\\s*"
            + "(?://(?<authority>[^/?#]*))?\\s*"
            + "(?:/(?!\\s*/)(?<path>[^?#]*))?"
            + "(?:\\?(?!\\s*\\?)(?<query>[^#]*))?"
            + "(?:\\s*#.*)?");

    private static final Pattern QUERY_KEY = Pattern.compile("[\\w.\\-\\s%]*");

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
        String subProtocol = subProtocol(url, sections.group("scheme"));
        String path = sections.group("path");
        String pathDatabase = path == null ? "" : percentDecode(url, path).trim();

        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        Map<String, String> query = readQuery(url, sections.group("query"), properties);
        rejectDnsSrv(url, properties);
        List<MySQLHostSpec> specs = hostSpecs(url, sections.group("authority"));
        MySQLHostSpec primary = specs.get(0);
        putAuthorityProperties(primary, query, properties);
        List<Host> hosts = new ArrayList<>();
        for (MySQLHostSpec spec : specs) {
            hosts.add(resolve(url, spec, hostOptions(query, spec)));
        }

        DatabaseProduct dbType = DatabaseProduct.MYSQL;
        String database = databaseName(hostOptions(query, primary), pathDatabase);
        String protocol = withSubProtocol(extractProtocol(url, dbType), subProtocol);
        return new JdbcUrl(url, dbType, protocol, hosts, database, properties);
    }

    // The driver's ConnectionUrl.Type matches the scheme case-insensitively; sequential and aurora are not among them.
    private static String subProtocol(String url, String scheme) {

        if (PREFIX.equalsIgnoreCase(scheme)) {
            return "";
        }
        return SUB_PROTOCOLS.stream()
                .filter(subProtocol -> (PREFIX + subProtocol + ":").equalsIgnoreCase(scheme))
                .findFirst()
                .orElseThrow(() -> new JdbcUrlParseException(url, "Unsupported MySQL URL scheme: " + scheme));
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

    private static String databaseName(Map<String, String> options, String pathDatabase) {

        if (options.containsKey("dbname")) {
            String database = options.get("dbname");
            return database == null ? "" : database;
        }
        return pathDatabase;
    }

    private static List<MySQLHostSpec> hostSpecs(String url, String authority) {

        List<MySQLHostSpec> specs = new ArrayList<>();
        for (String segment : authority == null ? List.of("") : MySQLHostSpec.split(authority)) {
            specs.addAll(MySQLHostSpec.parse(url, segment));
        }
        return specs;
    }

    // The driver lays each host's attributes over the query, keys folded as the query's are.
    private static Map<String, String> hostOptions(Map<String, String> query, MySQLHostSpec spec) {

        Map<String, String> options = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        options.putAll(query);
        options.putAll(spec.attributes());
        return options;
    }

    /**
     * Reports the primary host's attributes over the query, then its userinfo over both for user and password.
     * Other hosts' credentials never reach the properties, as the driver keeps them per host.
     */
    private static void putAuthorityProperties(
            MySQLHostSpec primary, Map<String, String> query, Map<String, JdbcProperty> properties) {

        primary.attributes().forEach((key, value) -> {
            if (!fallsBackToQuery(key, value, query)) {
                properties.put(key, new JdbcProperty(PropertySource.AUTHORITY, value));
            }
        });
        if (!isNullOrEmpty(primary.user())) {
            properties.put(USER, new JdbcProperty(PropertySource.AUTHORITY, primary.user()));
        }
        if (primary.password() != null) {
            properties.put(PASSWORD, new JdbcProperty(PropertySource.AUTHORITY, primary.password()));
        }
    }

    // The driver takes an empty user or password as unset and falls back to the query's value.
    private static boolean fallsBackToQuery(String key, String value, Map<String, String> query) {

        return value.isEmpty() && CREDENTIAL_KEYS.contains(key) && !isNullOrEmpty(query.get(key));
    }

    private static Host resolve(String url, MySQLHostSpec spec, Map<String, String> options) {

        String hostname = spec.host();
        if (hostname == null) {
            hostname = isNullOrEmpty(options.get("host")) ? DEFAULT_HOST : options.get("host");
        }
        Integer port = spec.port() != null ? spec.port() : MySQLHostSpec.writtenPort(url, options.get("port"));
        return Host.of(requireHostname(url, hostname), port == null ? DEFAULT_PORT : port);
    }

    private static Set<String> credentialKeys() {

        Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        keys.add(USER);
        keys.add(PASSWORD);
        return Collections.unmodifiableSet(keys);
    }

    private static boolean isNullOrEmpty(String text) {

        return text == null || text.isEmpty();
    }
}
