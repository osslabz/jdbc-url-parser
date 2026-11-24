package net.osslabz.jdbc.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;
import net.osslabz.jdbc.PropertySource;

/**
 * Parser for Microsoft SQL Server JDBC URLs, read as mssql-jdbc reads them.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:sqlserver://host:port;databaseName=db</li>
 *   <li>jdbc:sqlserver://host\instance;databaseName=db</li>
 *   <li>jdbc:sqlserver://;serverName=host;portNumber=port;databaseName=db</li>
 * </ul>
 *
 * <p>Note: SQL Server uses semicolon (;) as the property separator, not question mark (?). The {@code serverName},
 * {@code portNumber} and {@code instanceName} properties override the host part.
 */
public class SQLServerParser extends AbstractUrlParser {

    static final int DEFAULT_PORT = 1433;

    private static final String DEFAULT_HOST = "localhost";

    private static final List<String> SERVER_KEYS = List.of("serverName", "server");

    private static final List<String> PORT_KEYS = List.of("portNumber", "port");

    private static final List<String> INSTANCE_KEYS = List.of("instanceName");

    private static final List<String> DATABASE_KEYS = List.of("databaseName", "database");

    // SQL Server's instance naming rule: a letter, then letters, digits, '$' or '_'.
    private static final Pattern INSTANCE_NAME =
            Pattern.compile("(\\p{javaLetter}[\\p{javaLetter}\\p{javaDigit}$_]*)?");

    public SQLServerParser() {

        super(DatabaseProduct.SQLSERVER);
    }

    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);
        requireExactPrefix(url, SQLServerUrlTokenizer.PREFIX);

        SQLServerUrlTokenizer.Result tokens = SQLServerUrlTokenizer.tokenize(url);
        Map<String, String> written = tokens.properties();

        Host host = resolveHost(url, tokens);
        String databaseName = lastValue(written, DATABASE_KEYS, "");

        DatabaseProduct dbType = DatabaseProduct.SQLSERVER;
        return new JdbcUrl(
                url, dbType, extractProtocol(url, dbType), List.of(host), databaseName, toPathProperties(written));
    }

    private static Host resolveHost(String url, SQLServerUrlTokenizer.Result tokens) {

        Map<String, String> written = tokens.properties();
        String server = lastValue(written, SERVER_KEYS, tokens.host());
        String instance = lastValue(written, INSTANCE_KEYS, tokens.instance());
        String portText = lastValue(written, PORT_KEYS, tokens.port());

        String hostname = server == null ? "" : server;
        int instanceSeparator = hostname.indexOf('\\');
        if (instanceSeparator >= 0) {
            if (instance == null) {
                instance = hostname.substring(instanceSeparator + 1);
            }
            hostname = hostname.substring(0, instanceSeparator);
        }
        if (hostname.isEmpty()) {
            hostname = DEFAULT_HOST;
        }

        return new Host(
                requireHostname(url, hostname), resolvePort(url, portText, instance), requireInstance(url, instance));
    }

    // Port 0 means "not set" to the driver; with an instance it then asks the SQL Browser.
    private static Integer resolvePort(String url, String portText, String instance) {

        int port = portText == null ? 0 : parsePort(url, portText);
        if (port != 0) {
            return port;
        }
        return instance == null ? DEFAULT_PORT : null;
    }

    private static String requireInstance(String url, String instance) {

        if (instance != null && !INSTANCE_NAME.matcher(instance).matches()) {
            throw new JdbcUrlParseException(url, "Invalid instance name");
        }
        return instance;
    }

    private static String lastValue(Map<String, String> written, List<String> keys, String fallback) {

        String value = fallback;
        for (Map.Entry<String, String> property : written.entrySet()) {
            if (keys.stream().anyMatch(property.getKey()::equalsIgnoreCase)) {
                value = property.getValue();
            }
        }
        return value;
    }

    private static Map<String, JdbcProperty> toPathProperties(Map<String, String> written) {

        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        written.forEach((key, value) -> properties.put(key, new JdbcProperty(PropertySource.PATH, value)));
        return properties;
    }
}
