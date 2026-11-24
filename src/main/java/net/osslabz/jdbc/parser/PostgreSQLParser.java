package net.osslabz.jdbc.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;
import net.osslabz.jdbc.PropertySource;

/**
 * Parser for PostgreSQL JDBC URLs, read as pgjdbc's {@code Driver.parseURL} reads them.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:postgresql://host:port/database</li>
 *   <li>jdbc:postgresql://host/database</li>
 *   <li>jdbc:postgresql://host:port/database?param=value</li>
 *   <li>jdbc:postgresql://host1:port1,host2:port2/database (multi-host)</li>
 *   <li>jdbc:postgresql:database (localhost)</li>
 * </ul>
 *
 * <p>The query keys {@code PGHOST}, {@code PGPORT} and {@code PGDBNAME}, and {@code host}, {@code port} and
 * {@code dbname} in any case, override the hosts, ports and database written before the '?'.
 */
public class PostgreSQLParser extends AbstractUrlParser {

    static final int DEFAULT_PORT = 5432;

    private static final String PREFIX = "jdbc:postgresql:";

    private static final String DEFAULT_HOST = "localhost";

    private static final String NETWORK_START = "//";

    public PostgreSQLParser() {

        super(DatabaseProduct.POSTGRESQL);
    }

    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);
        requireExactPrefix(url, PREFIX);

        int queryStart = url.indexOf('?');
        String serverPart =
                queryStart < 0 ? url.substring(PREFIX.length()) : url.substring(PREFIX.length(), queryStart);
        List<RawPair> pairs = queryStart < 0 ? List.of() : splitPairs(url.substring(queryStart + 1), '&');

        ConnectionTarget target = parseServerPart(url, serverPart);
        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        for (RawPair pair : pairs) {
            String value = pair.value() == null ? "" : percentDecode(url, pair.value());
            properties.put(pair.key(), new JdbcProperty(PropertySource.QUERY, value));
            target.override(pair, value);
        }

        DatabaseProduct dbType = DatabaseProduct.POSTGRESQL;
        return new JdbcUrl(
                url,
                dbType,
                extractProtocol(url, dbType),
                target.toHosts(url),
                target.databaseName(properties),
                properties);
    }

    private static ConnectionTarget parseServerPart(String url, String serverPart) {

        ConnectionTarget target = new ConnectionTarget();
        if (NETWORK_START.equals(serverPart) || "///".equals(serverPart)) {
            return target;
        }
        if (serverPart.startsWith(NETWORK_START)) {
            parseNetworkPart(url, serverPart.substring(NETWORK_START.length()), target);
        } else if (serverPart.startsWith("/")) {
            throw new JdbcUrlParseException(url, "A local database name must not start with '/'");
        } else {
            target.database = percentDecode(url, serverPart);
        }
        return target;
    }

    private static void parseNetworkPart(String url, String networkPart, ConnectionTarget target) {

        if (networkPart.chars().filter(ch -> ch == '/').count() != 1) {
            throw new JdbcUrlParseException(url, "The host list must be followed by exactly one '/'");
        }
        int slash = networkPart.indexOf('/');
        if (slash < networkPart.length() - 1) {
            target.database = percentDecode(url, networkPart.substring(slash + 1));
        }

        target.hosts = new ArrayList<>();
        target.ports = new ArrayList<>();
        for (String address : splitList(networkPart.substring(0, slash))) {
            int portSeparator = address.lastIndexOf(':');
            if (portSeparator >= 0 && address.lastIndexOf(']') < portSeparator) {
                target.hosts.add(address.substring(0, portSeparator));
                target.ports.add(address.substring(portSeparator + 1));
            } else {
                target.hosts.add(address);
                target.ports.add(String.valueOf(DEFAULT_PORT));
            }
        }
    }

    // pgjdbc splits with String.split(","), which drops trailing empty entries but keeps a lone empty one.
    @SuppressWarnings("StringSplitter")
    private static List<String> splitList(String commaSeparated) {

        return Arrays.asList(commaSeparated.split(","));
    }

    // Mirrors the driver's comma-joined PGHOST and PGPORT values, which the query can replace before they are zipped.
    private static final class ConnectionTarget {

        private List<String> hosts = List.of(DEFAULT_HOST);

        private List<String> ports = List.of(String.valueOf(DEFAULT_PORT));

        // Null while the URL names no database, so the user name can stand in for it.
        private String database;

        // pgjdbc translates host, port and dbname only in tokens that carry a '='.
        void override(RawPair pair, String value) {

            if (overrides(pair, "PGHOST", "host")) {
                hosts = splitList(value);
            } else if (overrides(pair, "PGPORT", "port")) {
                ports = splitList(value);
            } else if (overrides(pair, "PGDBNAME", "dbname")) {
                database = value;
            }
        }

        private static boolean overrides(RawPair pair, String driverKey, String serviceKey) {

            return pair.key().equals(driverKey)
                    || (pair.value() != null && pair.key().equalsIgnoreCase(serviceKey));
        }

        String databaseName(Map<String, JdbcProperty> properties) {

            if (database != null) {
                return database;
            }
            JdbcProperty user = properties.get("user");
            return user == null ? "" : user.value();
        }

        List<Host> toHosts(String url) {

            if (hosts.size() != ports.size()) {
                throw new JdbcUrlParseException(url, "Hosts and ports must list the same number of values");
            }
            if (hosts.isEmpty()) {
                throw new JdbcUrlParseException(url, "No host");
            }
            List<Host> result = new ArrayList<>();
            for (int i = 0; i < hosts.size(); i++) {
                String hostname = hosts.get(i).isEmpty() ? DEFAULT_HOST : hosts.get(i);
                result.add(Host.of(requireHostname(url, hostname), parseServerPort(url, ports.get(i))));
            }
            return result;
        }

        private static int parseServerPort(String url, String portText) {

            int port = parsePort(url, portText);
            if (port == 0) {
                throw new JdbcUrlParseException(url, "Invalid port: 0");
            }
            return port;
        }
    }
}
