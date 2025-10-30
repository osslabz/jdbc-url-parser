package net.osslabz.jdbc.parser;

import java.util.List;
import java.util.Map;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.PropertySource;


/**
 * Parser for Apache Derby JDBC URLs.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:derby:database (embedded)</li>
 *   <li>jdbc:derby:database;create=true</li>
 *   <li>jdbc:derby:/path/to/database</li>
 *   <li>jdbc:derby:memory:testdb (in-memory)</li>
 *   <li>jdbc:derby://host:port/database (network/client mode)</li>
 * </ul>
 *
 * <p>Note: Derby uses semicolon (;) as the property separator.
 */
public class DerbyParser extends AbstractUrlParser {

    public DerbyParser() {

        super(DatabaseProduct.DERBY);
    }


    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);

        DatabaseProduct dbType = DatabaseProduct.DERBY;
        String protocol = extractProtocol(url, dbType);
        String remainder = removeProtocol(url, protocol);

        // Check for network mode (client/server)
        if (remainder.startsWith("//")) {
            return parseNetworkMode(url, protocol, remainder);
        }

        // Embedded mode - split by semicolon for properties (PATH properties)
        String[] parts = splitFirst(remainder, ';');
        String databasePath = parts[0];
        String propsString = parts.length > 1 ? parts[1] : null;

        Map<String, JdbcProperty> properties = parseProperties(propsString, PropertySource.PATH);

        // Check for in-memory mode
        if (databasePath.startsWith("memory:")) {
            addDerivedProperty(properties, "MODE", "MEMORY");
            databasePath = databasePath.substring(7);
        } else {
            addDerivedProperty(properties, "MODE", "EMBEDDED");
        }

        return new JdbcUrl(url, dbType, protocol, List.of(), databasePath, properties);
    }


    /**
     * Parses Derby in network/client mode.
     */
    private JdbcUrl parseNetworkMode(String url, String protocol, String remainder) {
        // Format: //host:port/database[;properties]
        String withoutSlashes = remainder.substring(2);

        // Split by semicolon for properties (PATH properties)
        String[] mainAndProps = splitFirst(withoutSlashes, ';');
        String mainPart = mainAndProps[0];
        String propsString = mainAndProps.length > 1 ? mainAndProps[1] : null;

        Map<String, JdbcProperty> properties = parseProperties(propsString, PropertySource.PATH);

        // Split host and database
        String[] hostAndDb = splitFirst(mainPart, '/');
        String hostString = hostAndDb[0];
        String databaseName = hostAndDb.length > 1 ? hostAndDb[1] : "";

        Host host = parseHost(hostString);
        addDerivedProperty(properties, "MODE", "NETWORK");

        return new JdbcUrl(url, DatabaseProduct.DERBY, protocol, List.of(host), databaseName, properties);
    }
}
