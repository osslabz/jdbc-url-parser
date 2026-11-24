package net.osslabz.jdbc.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;
import net.osslabz.jdbc.PropertySource;

/**
 * Parser for MariaDB JDBC URLs, read as mariadb-java-client's {@code Configuration.parse} reads them.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:mariadb://host:port/database</li>
 *   <li>jdbc:mariadb://host1:port1,host2:port2/database?param=value (multi-host)</li>
 *   <li>jdbc:mariadb://address=(host=host)(port=port),address=(host=host)/database</li>
 *   <li>jdbc:mariadb:///database?localSocket=path (no network host)</li>
 *   <li>jdbc:mariadb:replication://host1,host2/database (also sequential, loadbalance, load-balance-read, failover)</li>
 * </ul>
 *
 * <p>Nothing is percent-decoded. A non-empty query {@code database} value, in any case, overrides the database
 * written before the '?'.
 */
public class MariaDBParser extends AbstractUrlParser {

    static final int DEFAULT_PORT = 3306;

    private static final String PREFIX = "jdbc:mariadb:";

    private static final String NETWORK_START = "//";

    private static final String ADDRESS_START = "address=";

    private static final String ADDRESS_BLOCK_START = ADDRESS_START + "(";

    private static final Pattern ADDRESS_GROUP_BOUNDARY = Pattern.compile("(?=\\()|(?<=\\))");

    // The driver's HaMode names and values; "failover" is its alias for load balancing.
    private static final List<String> HA_MODES = List.of(
            "replication",
            "sequential",
            "load-balance-read",
            "load_balance_read",
            "load-balance",
            "loadbalance",
            "none",
            "failover",
            "");

    private static final List<String> ADDRESS_TYPES = List.of("master", "primary", "slave", "replica");

    public MariaDBParser() {

        super(DatabaseProduct.MARIADB);
    }

    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);
        requireExactPrefix(url, PREFIX);
        int networkStart = url.indexOf(NETWORK_START);
        if (networkStart < 0) {
            throw new JdbcUrlParseException(url, "'//' must follow the prefix");
        }
        String haMode = haMode(url, url.substring(PREFIX.length(), networkStart));

        String remainder = url.substring(networkStart + NETWORK_START.length());
        int hostSpecEnd = hostSpecEnd(url, remainder);
        String afterHosts = remainder.substring(hostSpecEnd);
        int queryStart = afterHosts.indexOf('?');
        Map<String, JdbcProperty> properties =
                queryStart < 0 ? new LinkedHashMap<>() : queryProperties(afterHosts.substring(queryStart + 1));
        List<Host> hosts = parseHosts(url, remainder.substring(0, hostSpecEnd), namesSocket(properties));

        DatabaseProduct dbType = DatabaseProduct.MARIADB;
        return new JdbcUrl(
                url,
                dbType,
                withSubProtocol(extractProtocol(url, dbType), haMode),
                hosts,
                databaseName(properties, pathDatabase(afterHosts, queryStart)),
                properties);
    }

    /**
     * Reads the sub-protocol written between the prefix and '//'. The driver takes the text up to the next ':' and
     * matches it case-insensitively against its HA mode names, the empty text meaning none.
     *
     * @param url     the URL being parsed, for the error message
     * @param between the text between {@code jdbc:mariadb:} and '//'
     * @return the matching HA mode spelling as listed here, empty when none is written
     * @throws JdbcUrlParseException if the text names no HA mode
     */
    String haMode(String url, String between) {

        int modeEnd = between.indexOf(':');
        String written = modeEnd < 0 ? between : between.substring(0, modeEnd);
        return HA_MODES.stream()
                .filter(mode -> mode.equalsIgnoreCase(written))
                .findFirst()
                .orElseThrow(() -> new JdbcUrlParseException(url, "Unsupported sub-protocol"));
    }

    // The host spec ends at the first '/' after any address=(...) blocks, or at the first '?' if that comes earlier.
    private static int hostSpecEnd(String url, String remainder) {

        int databaseStart = remainder.indexOf('/', skipAddressBlocks(url, remainder));
        int queryStart = remainder.indexOf('?');
        if (queryStart >= 0 && (databaseStart < 0 || queryStart < databaseStart)) {
            return queryStart;
        }
        return databaseStart < 0 ? remainder.length() : databaseStart;
    }

    private static int skipAddressBlocks(String url, String remainder) {

        int skipTo = 0;
        int blockStart = remainder.indexOf(ADDRESS_BLOCK_START, skipTo);
        while (blockStart >= 0) {
            int blockEnd = remainder.indexOf(')', blockStart);
            // The driver loops forever on an unclosed block.
            if (blockEnd < 0) {
                throw new JdbcUrlParseException(url, "Unclosed address block");
            }
            skipTo = skipFollowingGroups(remainder, blockEnd + 1);
            blockStart = remainder.indexOf(ADDRESS_BLOCK_START, skipTo);
        }
        return skipTo;
    }

    private static int skipFollowingGroups(String remainder, int groupStart) {

        int skipTo = groupStart;
        while (remainder.startsWith("(", skipTo)) {
            int groupEnd = remainder.indexOf(')', skipTo);
            if (groupEnd < 0) {
                return skipTo;
            }
            skipTo = groupEnd + 1;
        }
        return skipTo;
    }

    private static String pathDatabase(String afterHosts, int queryStart) {

        int end = queryStart < 0 ? afterHosts.length() : queryStart;
        return end <= 1 ? "" : afterHosts.substring(1, end);
    }

    private static Map<String, JdbcProperty> queryProperties(String query) {

        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        for (RawPair pair : splitPairs(query, '&')) {
            String value = pair.value() == null ? "" : pair.value();
            properties.put(pair.key(), new JdbcProperty(PropertySource.QUERY, value));
        }
        return properties;
    }

    private static String databaseName(Map<String, JdbcProperty> properties, String pathDatabase) {

        String database = pathDatabase;
        for (Map.Entry<String, JdbcProperty> entry : properties.entrySet()) {
            if (isOption(entry, "database")) {
                database = entry.getValue().value();
            }
        }
        return database;
    }

    private static boolean namesSocket(Map<String, JdbcProperty> properties) {

        return properties.entrySet().stream()
                .anyMatch(entry -> isOption(entry, "localsocket") || isOption(entry, "pipe"));
    }

    // The driver lowercases a key before matching it to an option and ignores an empty value.
    private static boolean isOption(Map.Entry<String, JdbcProperty> entry, String lowercaseOption) {

        return entry.getKey().toLowerCase(Locale.ROOT).equals(lowercaseOption)
                && !entry.getValue().value().isEmpty();
    }

    private static List<Host> parseHosts(String url, String hostSpec, boolean socketNamed) {

        List<Host> hosts = new ArrayList<>();
        boolean localConnection = socketNamed;
        for (String token : hostTokens(url, hostSpec)) {
            if (token.startsWith(ADDRESS_START)) {
                Address address = Address.parse(url, token);
                localConnection |= address.namesSocket;
                if (address.host != null) {
                    hosts.add(Host.of(reportedHostname(url, address.host), address.port));
                }
            } else {
                hosts.add(simpleHost(url, token));
            }
        }
        if (hosts.isEmpty() && !localConnection) {
            throw new JdbcUrlParseException(url, "Host, pipe or local socket must be set");
        }
        return hosts;
    }

    // The driver splits with String.split(","), which drops trailing empty entries but keeps inner ones.
    @SuppressWarnings("StringSplitter")
    private static List<String> hostTokens(String url, String hostSpec) {

        if (hostSpec.isEmpty()) {
            return List.of();
        }
        List<String> tokens = Arrays.asList(hostSpec.trim().split(","));
        if (tokens.contains("")) {
            throw new JdbcUrlParseException(url, "Empty host in host list");
        }
        return tokens;
    }

    private static Host simpleHost(String url, String token) {

        if (token.charAt(0) == '[') {
            return bracketedHost(url, token);
        }
        int portStart = token.indexOf(':') + 1;
        if (portStart == 0) {
            return Host.of(reportedHostname(url, token), DEFAULT_PORT);
        }
        // The driver splits on every ':' and reads only the first two segments.
        int portEnd = token.indexOf(':', portStart);
        String portText = token.substring(portStart, portEnd < 0 ? token.length() : portEnd);
        return Host.of(reportedHostname(url, token.substring(0, portStart - 1)), parsePort(url, portText));
    }

    private static Host bracketedHost(String url, String token) {

        int literalEnd = token.indexOf(']');
        if (literalEnd < 0) {
            throw new JdbcUrlParseException(url, "Unclosed IPv6 literal");
        }
        int port = DEFAULT_PORT;
        if (token.startsWith(":", literalEnd + 1)) {
            port = parsePort(url, token.substring(literalEnd + 2));
        }
        return Host.of(reportedHostname(url, token.substring(1, literalEnd)), port);
    }

    // The driver holds hosts without brackets and hands an empty one to InetSocketAddress, which resolves loopback.
    private static String reportedHostname(String url, String driverHost) {

        return requireHostnameOrLocalhost(url, driverHost.indexOf(':') < 0 ? driverHost : "[" + driverHost + "]");
    }

    // Only host, port, type, localsocket and pipe change where the driver connects.
    private static final class Address {

        private String host;

        private int port = DEFAULT_PORT;

        private boolean namesSocket;

        // The driver splits with String.split on this pattern; groups[0] is the "address=" text it skips.
        @SuppressWarnings("StringSplitter")
        static Address parse(String url, String token) {

            Address address = new Address();
            String[] groups = ADDRESS_GROUP_BOUNDARY.split(token.replace(" ", ""));
            for (int i = 1; i < groups.length; i++) {
                address.apply(url, groups[i]);
            }
            return address;
        }

        // The driver splits with String.split("="), so a trailing '=' leaves a two-part pair.
        @SuppressWarnings("StringSplitter")
        private void apply(String url, String group) {

            String[] keyAndValue =
                    group.replace("(", "").replace(")", "").trim().split("=");
            if (keyAndValue.length != 2) {
                throw new JdbcUrlParseException(url, "Expected key=value in address");
            }
            String value = keyAndValue[1].toLowerCase(Locale.ROOT);
            switch (keyAndValue[0].toLowerCase(Locale.ROOT)) {
                case "host" -> host = value.replace("[", "").replace("]", "");
                case "port" -> port = parsePort(url, value);
                case "type" -> requireType(url, value);
                case "localsocket", "pipe" -> namesSocket = true;
                default -> {
                    // sslmode and credentials do not change where the driver connects.
                }
            }
        }

        private static void requireType(String url, String type) {

            if (ADDRESS_TYPES.stream().noneMatch(type::equalsIgnoreCase)) {
                throw new JdbcUrlParseException(url, "Invalid address type");
            }
        }
    }
}
