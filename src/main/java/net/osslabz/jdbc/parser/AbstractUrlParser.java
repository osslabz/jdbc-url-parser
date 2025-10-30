package net.osslabz.jdbc.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;
import net.osslabz.jdbc.PropertySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Abstract base class for URL parsers providing common parsing utilities.
 */
public abstract class AbstractUrlParser implements UrlParser {

    private static final Logger log = LoggerFactory.getLogger(AbstractUrlParser.class);

    protected final Set<DatabaseProduct> supportedTypes;


    protected AbstractUrlParser(DatabaseProduct... supportedTypes) {

        this.supportedTypes = Set.of(supportedTypes);
    }


    @Override
    public boolean supports(DatabaseProduct databaseProduct) {

        return supportedTypes.contains(databaseProduct);
    }


    /**
     * Validates that the URL starts with "jdbc:".
     *
     * @param url the URL to validate
     * @throws JdbcUrlParseException if validation fails
     */
    protected void validateJdbcPrefix(String url) {

        if (url == null || url.isBlank()) {
            throw new JdbcUrlParseException(url, "JDBC URL cannot be null or blank");
        }
        if (!url.toLowerCase().startsWith("jdbc:")) {
            throw new JdbcUrlParseException(url, "JDBC URL must start with 'jdbc:'");
        }
    }


    /**
     * Extracts the protocol from a JDBC URL (e.g., "jdbc:mysql").
     *
     * @param url          the JDBC URL
     * @param databaseProduct the database type
     * @return the protocol string
     */
    protected String extractProtocol(String url, DatabaseProduct databaseProduct) {

        String prefix = databaseProduct.getUrlPrefix();
        if (url.toLowerCase().startsWith(prefix.toLowerCase())) {
            return prefix;
        }
        throw new JdbcUrlParseException(url, "URL does not match expected prefix: " + prefix);
    }


    /**
     * Parses properties from a query string (after '?') or path parameters (after ';'). Supports both '&' and ';' as separators. Always returns a mutable map so parsers can add additional properties.
     *
     * @param queryString the query string without the leading '?' or ';'
     * @param source      the source of these properties (QUERY or PATH)
     * @return mutable map of properties with source information
     */
    protected Map<String, JdbcProperty> parseProperties(String queryString, PropertySource source) {

        Map<String, JdbcProperty> properties = new LinkedHashMap<>();

        if (queryString == null || queryString.isBlank()) {
            return properties;
        }

        String[] pairs = queryString.split("[&;]");

        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }

            int equalsIndex = pair.indexOf('=');
            if (equalsIndex > 0) {
                String key = pair.substring(0, equalsIndex).trim();
                String value = pair.substring(equalsIndex + 1).trim();
                properties.put(key, new JdbcProperty(source, value));
            } else {
                // Property without value (flag)
                properties.put(pair.trim(), new JdbcProperty(source, ""));
            }
        }

        return properties;
    }


    /**
     * Adds a derived property to the properties map.
     *
     * @param properties the properties map
     * @param key        the property key
     * @param value      the property value
     */
    protected void addDerivedProperty(Map<String, JdbcProperty> properties, String key, String value) {

        properties.put(key, new JdbcProperty(PropertySource.DERIVED, value));
    }


    /**
     * Adds a descriptor property to the properties map.
     *
     * @param properties the properties map
     * @param key        the property key
     * @param value      the property value
     */
    protected void addDescriptorProperty(Map<String, JdbcProperty> properties, String key, String value) {

        properties.put(key, new JdbcProperty(PropertySource.DESCRIPTOR, value));
    }


    /**
     * Parses a host:port string into a Host object.
     *
     * @param hostString the host string (e.g., "localhost:3306")
     * @return the Host object
     */
    protected Host parseHost(String hostString) {

        if (hostString == null || hostString.isBlank()) {
            throw new IllegalArgumentException("Host string cannot be null or blank");
        }

        // Check for SQL Server instance name (hostname\instanceName:port)
        if (hostString.contains("\\")) {
            return parseSqlServerHost(hostString);
        }

        // Standard hostname:port format
        int colonIndex = hostString.lastIndexOf(':');
        if (colonIndex > 0 && colonIndex < hostString.length() - 1) {
            String hostname = hostString.substring(0, colonIndex);
            String portString = hostString.substring(colonIndex + 1);
            try {
                int port = Integer.parseInt(portString);
                return Host.of(hostname, port);
            } catch (NumberFormatException e) {
                log.debug("Invalid port number in host string: {}", hostString);
                return Host.of(hostString);
            }
        }

        return Host.of(hostString);
    }


    /**
     * Parses SQL Server host with instance name (hostname\instanceName:port).
     *
     * @param hostString the host string
     * @return the Host object
     */
    protected Host parseSqlServerHost(String hostString) {

        String hostname;
        String instanceName;
        Integer port = null;

        int backslashIndex = hostString.indexOf('\\');
        hostname = hostString.substring(0, backslashIndex);

        String remaining = hostString.substring(backslashIndex + 1);
        int colonIndex = remaining.indexOf(':');

        if (colonIndex > 0) {
            instanceName = remaining.substring(0, colonIndex);
            String portString = remaining.substring(colonIndex + 1);
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                log.debug("Invalid port number in SQL Server host string: {}", hostString);
            }
        } else {
            instanceName = remaining;
        }

        return Host.of(hostname, port != null ? port : 0, instanceName);
    }


    /**
     * Parses multiple hosts separated by commas (for multi-host URLs).
     *
     * @param hostsString the hosts string (e.g., "host1:3306,host2:3306")
     * @return list of Host objects
     */
    protected List<Host> parseMultipleHosts(String hostsString) {

        if (hostsString == null || hostsString.isBlank()) {
            return List.of();
        }

        String[] hostParts = hostsString.split(",");
        List<Host> hosts = new ArrayList<>();

        for (String hostPart : hostParts) {
            String trimmed = hostPart.trim();
            if (!trimmed.isBlank()) {
                hosts.add(parseHost(trimmed));
            }
        }

        return hosts;
    }


    /**
     * Extracts the portion of the URL after the protocol prefix.
     *
     * @param url      the full JDBC URL
     * @param protocol the protocol prefix to remove
     * @return the URL remainder after the protocol
     */
    protected String removeProtocol(String url, String protocol) {

        if (url.toLowerCase().startsWith(protocol.toLowerCase())) {
            return url.substring(protocol.length());
        }
        throw new JdbcUrlParseException(url, "URL does not start with expected protocol: " + protocol);
    }


    /**
     * Splits a string by a delimiter, limiting to two parts.
     *
     * @param input     the input string
     * @param delimiter the delimiter character
     * @return array with two parts, or the original string if delimiter not found
     */
    protected String[] splitFirst(String input, char delimiter) {

        int index = input.indexOf(delimiter);
        if (index >= 0) {
            return new String[] {
                input.substring(0, index),
                input.substring(index + 1)
            };
        }
        return new String[] {input};
    }


    /**
     * Splits by first occurrence of either ';' or '?'. Useful for databases that support both property delimiters.
     *
     * @param input the input string
     * @return array with two parts (main and properties), or just main part if no delimiter found
     */
    protected String[] splitByFirstPropertyDelimiter(String input) {

        int semicolonIndex = input.indexOf(';');
        int questionIndex = input.indexOf('?');

        int splitIndex;
        if (semicolonIndex >= 0 && questionIndex >= 0) {
            splitIndex = Math.min(semicolonIndex, questionIndex);
        } else if (semicolonIndex >= 0) {
            splitIndex = semicolonIndex;
        } else if (questionIndex >= 0) {
            splitIndex = questionIndex;
        } else {
            return new String[] {input};
        }

        return new String[] {
            input.substring(0, splitIndex),
            input.substring(splitIndex + 1)
        };
    }


    /**
     * Common parsing logic for standard network-based JDBC URLs. Handles format: jdbc:protocol://host:port/database?properties
     *
     * @param url          the original JDBC URL
     * @param databaseProduct the database type
     * @return parsed JdbcUrl
     */
    protected JdbcUrl parseStandardNetworkUrl(String url, DatabaseProduct databaseProduct) {

        validateJdbcPrefix(url);

        String protocol = extractProtocol(url, databaseProduct);
        String remainder = removeProtocol(url, protocol);

        // Remove leading slashes (//host:port/database format)
        if (remainder.startsWith("//")) {
            remainder = remainder.substring(2);
        }

        // Split into host/database and properties parts
        String[] mainAndProps = splitFirst(remainder, '?');
        String mainPart = mainAndProps[0];
        String propsString = mainAndProps.length > 1 ? mainAndProps[1] : null;

        // Parse properties from query component
        Map<String, JdbcProperty> properties = parseProperties(propsString, PropertySource.QUERY);

        // Split host(s) and database
        String[] hostAndDb = splitFirst(mainPart, '/');
        String hostsString = hostAndDb[0];
        String databaseName = hostAndDb.length > 1 ? hostAndDb[1] : "";

        // Parse hosts (supports multiple hosts for clustering/failover)
        List<Host> hosts = parseMultipleHosts(hostsString);

        return new JdbcUrl(url, databaseProduct, protocol, hosts, databaseName, properties);
    }
}
