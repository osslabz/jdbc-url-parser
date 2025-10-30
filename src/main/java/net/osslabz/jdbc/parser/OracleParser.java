package net.osslabz.jdbc.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Parser for Oracle JDBC URLs.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:oracle:thin:@host:port:SID</li>
 *   <li>jdbc:oracle:thin:@//host:port/serviceName</li>
 *   <li>jdbc:oracle:thin:@(DESCRIPTION=...)</li>
 * </ul>
 */
public class OracleParser extends AbstractUrlParser {

    private static final Logger log = LoggerFactory.getLogger(OracleParser.class);

    // Pattern for thin driver with SID: @host:port:SID
    private static final Pattern SID_PATTERN = Pattern.compile("@([^:]+):(\\d+):(.+)");

    // Pattern for thin driver with service name: @//host:port/serviceName or @host:port/serviceName
    private static final Pattern SERVICE_PATTERN = Pattern.compile("@/?/?([^/:]+):(\\d+)/(.+)");


    public OracleParser() {

        super(DatabaseProduct.ORACLE);
    }


    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);

        DatabaseProduct dbType = DatabaseProduct.ORACLE;
        String protocol = extractProtocol(url, dbType);
        String remainder = removeProtocol(url, protocol);

        // Oracle URLs have format: jdbc:oracle:{driver_type}:{connection_info}
        // Common driver types: thin, oci, oci8

        // Extract driver type
        int colonIndex = remainder.indexOf(':');
        if (colonIndex < 0) {
            throw new JdbcUrlParseException(url, "Invalid Oracle URL format: missing driver type");
        }

        String driverType = remainder.substring(0, colonIndex);
        String connectionInfo = remainder.substring(colonIndex + 1);

        // Parse based on connection format
        if (connectionInfo.startsWith("@(DESCRIPTION=") || connectionInfo.startsWith("@(description=")) {
            return parseDescriptorFormat(url, protocol, driverType, connectionInfo);
        } else if (connectionInfo.startsWith("@//")) {
            return parseServiceNameFormat(url, protocol, driverType, connectionInfo);
        } else if (connectionInfo.startsWith("@")) {
            return parseSidFormat(url, protocol, driverType, connectionInfo);
        } else {
            throw new JdbcUrlParseException(url, "Unsupported Oracle connection format");
        }
    }


    /**
     * Parses Oracle SID format: @host:port:SID or service name format @host:port/serviceName
     */
    private JdbcUrl parseSidFormat(String url, String protocol, String driverType, String connectionInfo) {
        // First try service name format (with /)
        Matcher serviceMatcher = SERVICE_PATTERN.matcher(connectionInfo);
        if (serviceMatcher.matches()) {
            String hostname = serviceMatcher.group(1);
            int port = Integer.parseInt(serviceMatcher.group(2));
            String serviceName = serviceMatcher.group(3);

            Host host = Host.of(hostname, port);
            Map<String, JdbcProperty> properties = new LinkedHashMap<>();
            addDerivedProperty(properties, "DRIVER_TYPE", driverType);
            addDescriptorProperty(properties, "SERVICE_NAME", serviceName);

            return new JdbcUrl(url, DatabaseProduct.ORACLE, protocol, List.of(host), serviceName, properties);
        }

        // Try SID format (with :)
        Matcher sidMatcher = SID_PATTERN.matcher(connectionInfo);
        if (sidMatcher.matches()) {
            String hostname = sidMatcher.group(1);
            int port = Integer.parseInt(sidMatcher.group(2));
            String sid = sidMatcher.group(3);

            Host host = Host.of(hostname, port);
            Map<String, JdbcProperty> properties = new LinkedHashMap<>();
            addDerivedProperty(properties, "DRIVER_TYPE", driverType);
            addDescriptorProperty(properties, "SID", sid);

            return new JdbcUrl(url, DatabaseProduct.ORACLE, protocol, List.of(host), sid, properties);
        }

        throw new JdbcUrlParseException(url, "Invalid Oracle SID or service name format");
    }


    /**
     * Parses Oracle service name format: @//host:port/serviceName
     */
    private JdbcUrl parseServiceNameFormat(String url, String protocol, String driverType, String connectionInfo) {

        Matcher matcher = SERVICE_PATTERN.matcher(connectionInfo);
        if (!matcher.matches()) {
            throw new JdbcUrlParseException(url, "Invalid Oracle service name format");
        }

        String hostname = matcher.group(1);
        int port = Integer.parseInt(matcher.group(2));
        String serviceName = matcher.group(3);

        Host host = Host.of(hostname, port);
        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        addDerivedProperty(properties, "DRIVER_TYPE", driverType);
        addDescriptorProperty(properties, "SERVICE_NAME", serviceName);

        return new JdbcUrl(url, DatabaseProduct.ORACLE, protocol, List.of(host), serviceName, properties);
    }


    /**
     * Parses Oracle TNSNAMES descriptor format.
     */
    private JdbcUrl parseDescriptorFormat(String url, String protocol, String driverType, String connectionInfo) {

        log.debug("Parsing Oracle descriptor format: {}", connectionInfo);

        // Extract basic information from the descriptor
        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        addDerivedProperty(properties, "DRIVER_TYPE", driverType);

        String hostname = null;
        Integer port = null;
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
            port = Integer.parseInt(portMatcher.group(1));
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
            Host host = port != null ? Host.of(hostname, port) : Host.of(hostname);
            hosts = List.of(host);
        }

        if (databaseName == null) {
            databaseName = "";
        }

        // Store the full descriptor in properties for reference
        addDescriptorProperty(properties, "DESCRIPTOR", connectionInfo);

        return new JdbcUrl(url, DatabaseProduct.ORACLE, protocol, hosts, databaseName, properties);
    }
}
