package net.osslabz.jdbc.parser;

import java.util.List;
import java.util.Map;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.PropertySource;


/**
 * Parser for H2 Database JDBC URLs.
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
 */
public class H2Parser extends AbstractUrlParser {

    public H2Parser() {

        super(DatabaseProduct.H2);
    }


    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);

        DatabaseProduct dbType = DatabaseProduct.H2;
        String protocol = extractProtocol(url, dbType);
        String remainder = removeProtocol(url, protocol);

        // Split properties (using ; or ?)
        String[] mainAndProps = splitByFirstPropertyDelimiter(remainder);
        String mainPart = mainAndProps[0];
        String propsString = mainAndProps.length > 1 ? mainAndProps[1] : null;

        // H2 uses semicolon in path, so these are PATH properties, but also supports ? for QUERY
        PropertySource propertySource = remainder.contains("?") ? PropertySource.QUERY : PropertySource.PATH;
        Map<String, JdbcProperty> properties = parseProperties(propsString, propertySource);

        // Check for network modes (tcp, ssl)
        if (mainPart.startsWith("tcp://") || mainPart.startsWith("ssl://")) {
            return parseNetworkMode(url, protocol, mainPart, properties);
        }

        // File-based or in-memory mode
        String databasePath = mainPart;

        // Add mode to properties for clarity (only if not already specified in properties)
        if (mainPart.startsWith("mem:")) {
            if (!properties.containsKey("MODE")) {
                addDerivedProperty(properties, "MODE", "MEMORY");
            }
            databasePath = mainPart.substring(4); // Remove "mem:" prefix
        } else if (mainPart.startsWith("file:")) {
            if (!properties.containsKey("MODE")) {
                addDerivedProperty(properties, "MODE", "FILE");
            }
            databasePath = mainPart.substring(5); // Remove "file:" prefix
        } else {
            // Default is file mode
            if (!properties.containsKey("MODE")) {
                addDerivedProperty(properties, "MODE", "FILE");
            }
        }

        return new JdbcUrl(url, dbType, protocol, List.of(), databasePath, properties);
    }


    /**
     * Parses H2 in network mode (tcp:// or ssl://).
     */
    private JdbcUrl parseNetworkMode(String url, String protocol, String mainPart, Map<String, JdbcProperty> properties) {
        // Format: tcp://host:port/database or ssl://host:port/database
        boolean isSsl = mainPart.startsWith("ssl://");
        String withoutScheme = isSsl ? mainPart.substring(6) : mainPart.substring(6);

        String[] hostAndDb = splitFirst(withoutScheme, '/');
        String hostString = hostAndDb[0];
        String databaseName = hostAndDb.length > 1 ? hostAndDb[1] : "";

        Host host = parseHost(hostString);
        addDerivedProperty(properties, "MODE", isSsl ? "SSL" : "TCP");

        return new JdbcUrl(url, DatabaseProduct.H2, protocol, List.of(host), databaseName, properties);
    }

}
