package net.osslabz.jdbc.parser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;
import net.osslabz.jdbc.PropertySource;

/**
 * Parser for Oracle JDBC URLs, read as ojdbc's {@code OracleDriver} and {@code PhysicalConnection} read them.
 *
 * <p>Supported formats, with {@code thin}, {@code oci} or {@code oci8} as the driver type:
 * <ul>
 *   <li>jdbc:oracle:thin:@host:port:SID</li>
 *   <li>jdbc:oracle:thin:@[protocol://]host[:port][,host[:port]...][/serviceName[:mode][/instance]][?key=value&amp;...]</li>
 *   <li>jdbc:oracle:thin:user/password@... (userinfo)</li>
 *   <li>jdbc:oracle:thin:@(DESCRIPTION=...)</li>
 * </ul>
 *
 * <p>Userinfo is reported as {@link PropertySource#AUTHORITY} properties; a query {@code user} or {@code password}
 * replaces it, as connection properties do in the driver.
 */
public class OracleParser extends AbstractUrlParser {

    static final int DEFAULT_PORT = 1521;

    private static final String PREFIX = "jdbc:oracle:";

    private static final Set<String> DRIVER_TYPES = Set.of("thin", "oci", "oci8");

    // The userinfo part of PhysicalConnection.URL_PATTERN.
    private static final Pattern USERINFO = Pattern.compile("(?<user>[\\w\\[\\]$#]*|\"[^\\x00\"]+\")"
            + "/(?<password>[\\w$#+%^&*~=;:<>\\[\\]()!\\p{L}]*|\"[^\\x00\"]+\")");

    private static final Pattern DIRECTORY_NAMING = Pattern.compile("ldaps?://");

    public OracleParser() {

        super(DatabaseProduct.ORACLE);
    }

    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);
        requireExactPrefix(url, PREFIX);
        String remainder = url.substring(PREFIX.length());
        int typeEnd = remainder.indexOf(':');
        if (typeEnd < 0) {
            throw new JdbcUrlParseException(url, "Oracle URL needs a driver type followed by ':'");
        }
        String driverType = remainder.substring(0, typeEnd);
        if (!DRIVER_TYPES.contains(driverType)) {
            throw new JdbcUrlParseException(url, "Unsupported Oracle driver type: " + driverType);
        }
        String target = remainder.substring(typeEnd + 1);
        int userinfoEnd = userinfoEnd(url, target);
        String connect = target.substring(userinfoEnd + 1);

        String protocol = extractProtocol(url, DatabaseProduct.ORACLE);
        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        addDerivedProperty(properties, "DRIVER_TYPE", driverType);
        properties.putAll(userInfo(url, target.substring(0, userinfoEnd)));
        if (connect.trim().startsWith("(")) {
            return descriptor(url, protocol, properties, connect);
        }
        requireSingleAt(url, connect);
        if (DIRECTORY_NAMING.matcher(connect).lookingAt()) {
            throw new JdbcUrlParseException(url, "Oracle LDAP URLs are not supported");
        }
        return easyConnect(url, protocol, properties, OracleEasyConnect.parse(url, connect));
    }

    /**
     * Reads Oracle userinfo {@code user/password}, either part double-quoted or empty, quotes kept.
     *
     * @param url      the URL being parsed, for the error message
     * @param userInfo the text before the '@', empty when the URL has none
     * @return {@code user} and {@code password} as {@link PropertySource#AUTHORITY} properties, each only when
     *     non-empty
     * @throws JdbcUrlParseException if the driver rejects the userinfo
     */
    static Map<String, JdbcProperty> userInfo(String url, String userInfo) {

        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        if (userInfo.isEmpty()) {
            return properties;
        }
        Matcher credentials = USERINFO.matcher(userInfo);
        if (!credentials.matches()) {
            throw new JdbcUrlParseException(url, "Oracle userinfo must be user/password");
        }
        putAuthority(properties, "user", credentials.group("user"));
        putAuthority(properties, "password", credentials.group("password"));
        return properties;
    }

    private static void putAuthority(Map<String, JdbcProperty> properties, String key, String value) {

        if (!value.isEmpty()) {
            properties.put(key, new JdbcProperty(PropertySource.AUTHORITY, value));
        }
    }

    private static int userinfoEnd(String url, String target) {

        boolean quoted = false;
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == '@' && !quoted) {
                return i;
            }
        }
        throw new JdbcUrlParseException(url, "Oracle URL needs '@' before the connect string");
    }

    private JdbcUrl easyConnect(
            String url, String protocol, Map<String, JdbcProperty> properties, OracleEasyConnect easyConnect) {

        String databaseName = route(properties, easyConnect.service(), easyConnect.sid());
        easyConnect.query().forEach((key, value) -> putQueryOption(properties, key, value));
        return new JdbcUrl(url, DatabaseProduct.ORACLE, protocol, easyConnect.hosts(), databaseName, properties);
    }

    // ojdbc would take a second '@' as part of the host or service, which is where a password then lands.
    private static void requireSingleAt(String url, String connect) {

        int queryStart = connect.indexOf('?');
        if ((queryStart < 0 ? connect : connect.substring(0, queryStart)).indexOf('@') >= 0) {
            throw new JdbcUrlParseException(url, "Oracle connect string must not contain '@'");
        }
    }

    private JdbcUrl descriptor(String url, String protocol, Map<String, JdbcProperty> properties, String connect) {

        String trimmed = connect.trim();
        int queryStart = descriptorQueryStart(trimmed);
        Map<String, String> query =
                queryStart < 0 ? Map.of() : OracleEasyConnect.parseQuery(url, trimmed.substring(queryStart + 1));
        // ojdbc finds the '?' in the trimmed text but cuts the untrimmed one there; without options it keeps the '?'.
        String text = query.isEmpty() ? connect : connect.substring(0, queryStart);
        OracleDescriptor descriptor = OracleDescriptor.parse(url, text);
        String databaseName = route(properties, descriptor.serviceName(), descriptor.sid());
        addDescriptorProperty(properties, "DESCRIPTOR", text.substring(text.indexOf('('), descriptor.end()));
        query.forEach((key, value) -> putQueryOption(properties, key, value));
        return new JdbcUrl(url, DatabaseProduct.ORACLE, protocol, descriptor.hosts(), databaseName, properties);
    }

    // ojdbc's EZConnectResolver takes the first '?' outside parentheses, counting quoted parentheses too.
    private static int descriptorQueryStart(String text) {

        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '?' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Records the SID and service name the driver connects with.
     *
     * @return the service name, else the SID, else empty; with both present the server decides, and the library
     *     keeps 0.1.3's service name preference
     */
    private String route(Map<String, JdbcProperty> properties, String serviceName, String sid) {

        if (sid != null) {
            addDescriptorProperty(properties, "SID", sid);
        }
        if (serviceName == null) {
            return sid == null ? "" : sid;
        }
        addDescriptorProperty(properties, "SERVICE_NAME", serviceName);
        return serviceName;
    }

    // The driver routes with the URL's own driver type, SID and service; a query option only replaces userinfo.
    private static void putQueryOption(Map<String, JdbcProperty> properties, String key, String value) {

        JdbcProperty written = properties.get(key);
        if (written == null || written.source() == PropertySource.AUTHORITY) {
            properties.put(key, new JdbcProperty(PropertySource.QUERY, value));
        }
    }
}
