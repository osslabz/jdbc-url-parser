package net.osslabz.jdbc.parser;

import java.util.ArrayList;
import java.util.HashMap;
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
 * Parser for H2 Database JDBC URLs, read as H2's {@code ConnectionInfo} and {@code SessionRemote} read them.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:h2:mem:testdb (in-memory)</li>
 *   <li>jdbc:h2:~/test (file in user home)</li>
 *   <li>jdbc:h2:file:/path/to/database</li>
 *   <li>jdbc:h2:/path/to/database (implied file)</li>
 *   <li>jdbc:h2:tcp://host:port/database (network mode)</li>
 *   <li>jdbc:h2:ssl://host:port/database (network with SSL)</li>
 * </ul>
 *
 * <p>Settings follow the first ';', separated by ';' with '\' escaping the next character. '?' and '&amp;' are
 * ordinary characters of the name.
 */
public class H2Parser extends AbstractUrlParser {

    static final int DEFAULT_TCP_PORT = 9092;

    private static final String PREFIX = "jdbc:h2:";

    private static final String MODE = "MODE";

    public H2Parser() {

        super(DatabaseProduct.H2);
    }

    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);
        requireExactPrefix(url, PREFIX);

        int settingsStart = url.indexOf(';');
        String name =
                settingsStart < 0 ? url.substring(PREFIX.length()) : url.substring(PREFIX.length(), settingsStart);
        if (name.isEmpty()) {
            throw new JdbcUrlParseException(url, "Database name cannot be empty");
        }
        Map<String, JdbcProperty> properties =
                settingsStart < 0 ? new LinkedHashMap<>() : readSettings(url, url.substring(settingsStart + 1));

        DatabaseProduct dbType = DatabaseProduct.H2;
        String protocol = extractProtocol(url, dbType);
        if (name.startsWith("tcp:")) {
            return parseRemote(url, protocol, name.substring("tcp:".length()), "TCP", properties);
        }
        if (name.startsWith("ssl:")) {
            return parseRemote(url, protocol, name.substring("ssl:".length()), "SSL", properties);
        }
        return new JdbcUrl(url, dbType, protocol, List.of(), embeddedDatabaseName(url, name, properties), properties);
    }

    private static String embeddedDatabaseName(String url, String name, Map<String, JdbcProperty> properties) {

        if (".".equals(name)) {
            addDerivedMode(properties, "MEMORY");
            return "";
        }
        if (name.startsWith("mem:")) {
            addDerivedMode(properties, "MEMORY");
            return name.substring("mem:".length());
        }
        addDerivedMode(properties, "FILE");
        String fileName = name.startsWith("file:") ? name.substring("file:".length()) : name;
        if (fileName.isEmpty()) {
            throw new JdbcUrlParseException(url, "Database name cannot be empty");
        }
        return fileName;
    }

    private static Map<String, JdbcProperty> readSettings(String url, String settings) {

        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        Map<String, String> valuesByFoldedKey = new HashMap<>();
        for (String setting : arraySplit(settings, ';')) {
            if (setting.isEmpty()) {
                continue;
            }
            int equalsIndex = setting.indexOf('=');
            if (equalsIndex < 0) {
                throw new JdbcUrlParseException(url, "Setting without '='");
            }
            String key = setting.substring(0, equalsIndex);
            String value = setting.substring(equalsIndex + 1);
            String previous = valuesByFoldedKey.putIfAbsent(foldKey(key), value);
            if (previous != null && !previous.equals(value)) {
                throw new JdbcUrlParseException(url, "Duplicate setting");
            }
            properties.put(key, new JdbcProperty(PropertySource.PATH, value));
        }
        return properties;
    }

    // Mirrors StringUtils.arraySplit: empty text has no segments, '\' takes the next character, a trailing '\' stays.
    private static List<String> arraySplit(String text, char separator) {

        List<String> segments = new ArrayList<>();
        if (text.isEmpty()) {
            return segments;
        }
        StringBuilder segment = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                segment.append(c);
                escaped = false;
            } else if (c == separator) {
                segments.add(segment.toString());
                segment.setLength(0);
            } else if (c == '\\' && i < text.length() - 1) {
                escaped = true;
            } else {
                segment.append(c);
            }
        }
        segments.add(segment.toString());
        return segments;
    }

    // H2 folds setting keys with StringUtils.toUpperEnglish.
    private static String foldKey(String key) {

        return key.toUpperCase(Locale.ROOT);
    }

    private static void addDerivedMode(Map<String, JdbcProperty> properties, String mode) {

        boolean modeWritten = properties.keySet().stream().anyMatch(key -> MODE.equals(foldKey(key)));
        if (!modeWritten) {
            properties.put(MODE, new JdbcProperty(PropertySource.DERIVED, mode));
        }
    }

    private static JdbcUrl parseRemote(
            String url, String protocol, String remoteName, String mode, Map<String, JdbcProperty> properties) {

        String serversAndDatabase = remoteName.startsWith("//") ? remoteName.substring("//".length()) : remoteName;
        int slash = serversAndDatabase.indexOf('/');
        if (slash < 0) {
            throw new JdbcUrlParseException(url, "The server list must be followed by '/'");
        }
        List<Host> hosts = parseServers(url, serversAndDatabase.substring(0, slash));
        addDerivedMode(properties, mode);
        return new JdbcUrl(
                url, DatabaseProduct.H2, protocol, hosts, serversAndDatabase.substring(slash + 1), properties);
    }

    private static List<Host> parseServers(String url, String serverList) {

        List<Host> hosts = new ArrayList<>();
        for (String server : arraySplit(serverList, ',')) {
            hosts.add(parseServer(url, server.trim()));
        }
        // With no server H2 opens no socket and fails, so an empty list is no connection target.
        if (hosts.isEmpty()) {
            throw new JdbcUrlParseException(url, "No server");
        }
        return hosts;
    }

    // Mirrors NetUtils.createSocket: the port follows the first ':' after an optional bracketed literal.
    private static Host parseServer(String url, String server) {

        int literalEnd = server.startsWith("[") ? server.indexOf(']') : 0;
        int colon = server.indexOf(':', literalEnd);
        String hostname = colon < 0 ? server : server.substring(0, colon);
        int port = colon < 0 ? DEFAULT_TCP_PORT : decodePort(url, server.substring(colon + 1));
        return Host.of(requireHostnameOrLocalhost(url, hostname), port);
    }

    private static int decodePort(String url, String portText) {

        try {
            return requirePort(url, Integer.decode(portText));
        } catch (NumberFormatException e) {
            throw new JdbcUrlParseException(url, "Invalid port", e);
        }
    }
}
