package net.osslabz.jdbc.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;
import net.osslabz.jdbc.PropertySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(OracleParser.class);

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
        // ojdbc would take a second '@' as part of the host or service, which is where a password then lands.
        int queryStart = connect.indexOf('?');
        if ((queryStart < 0 ? connect : connect.substring(0, queryStart)).indexOf('@') >= 0) {
            throw new JdbcUrlParseException(url, "Oracle connect string must not contain '@'");
        }

        String protocol = extractProtocol(url, DatabaseProduct.ORACLE);
        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        addDerivedProperty(properties, "DRIVER_TYPE", driverType);
        properties.putAll(userInfo(url, target.substring(0, userinfoEnd)));
        if (connect.startsWith("(")) {
            return parseDescriptorFormat(url, protocol, properties, "@" + connect);
        }
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

        String databaseName = "";
        if (easyConnect.sid() != null) {
            databaseName = easyConnect.sid();
            addDescriptorProperty(properties, "SID", databaseName);
        } else if (easyConnect.service() != null) {
            databaseName = easyConnect.service();
            addDescriptorProperty(properties, "SERVICE_NAME", databaseName);
        }
        easyConnect.query().forEach((key, value) -> putQueryOption(properties, key, value));
        return new JdbcUrl(url, DatabaseProduct.ORACLE, protocol, easyConnect.hosts(), databaseName, properties);
    }

    // The driver routes with the URL's own driver type, SID and service; a query option only replaces userinfo.
    private static void putQueryOption(Map<String, JdbcProperty> properties, String key, String value) {

        JdbcProperty written = properties.get(key);
        if (written == null || written.source() == PropertySource.AUTHORITY) {
            properties.put(key, new JdbcProperty(PropertySource.QUERY, value));
        }
    }

    /**
     * Parses Oracle TNSNAMES descriptor format.
     */
    private JdbcUrl parseDescriptorFormat(
            String url, String protocol, Map<String, JdbcProperty> properties, String connectionInfo) {

        log.debug("Parsing Oracle descriptor format: {}", connectionInfo);

        String hostname = null;
        int port = DEFAULT_PORT;
        String databaseName = null;

        // Extract HOST
        Pattern hostPattern = Pattern.compile("\\(HOST\\s*=\\s*([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher hostMatcher = hostPattern.matcher(connectionInfo);
        if (hostMatcher.find()) {
            hostname = hostMatcher.group(1).trim();
        }

        // Extract PORT
        Pattern portPattern = Pattern.compile("\\(PORT\\s*=\\s*(\\d+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher portMatcher = portPattern.matcher(connectionInfo);
        if (portMatcher.find()) {
            port = parsePort(url, portMatcher.group(1));
        }

        // Extract SERVICE_NAME or SID
        Pattern servicePattern = Pattern.compile("\\(SERVICE_NAME\\s*=\\s*([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher serviceMatcher = servicePattern.matcher(connectionInfo);
        if (serviceMatcher.find()) {
            databaseName = serviceMatcher.group(1).trim();
            addDescriptorProperty(properties, "SERVICE_NAME", databaseName);
        } else {
            Pattern sidPattern = Pattern.compile("\\(SID\\s*=\\s*([^)]+)\\)", Pattern.CASE_INSENSITIVE);
            Matcher sidMatcher = sidPattern.matcher(connectionInfo);
            if (sidMatcher.find()) {
                databaseName = sidMatcher.group(1).trim();
                addDescriptorProperty(properties, "SID", databaseName);
            }
        }

        // Create host if we found hostname
        List<Host> hosts = List.of();
        if (hostname != null) {
            hosts = List.of(Host.of(hostname, port));
        }

        if (databaseName == null) {
            databaseName = "";
        }

        // Store the full descriptor in properties for reference
        addDescriptorProperty(properties, "DESCRIPTOR", connectionInfo);

        return new JdbcUrl(url, DatabaseProduct.ORACLE, protocol, hosts, databaseName, properties);
    }
}
