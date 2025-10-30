package net.osslabz.jdbc.parser;

import java.util.List;
import java.util.Map;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.PropertySource;


/**
 * Parser for HSQLDB (HyperSQL) JDBC URLs.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:hsqldb:mem:testdb (in-memory)</li>
 *   <li>jdbc:hsqldb:file:/path/to/database</li>
 *   <li>jdbc:hsqldb:/path/to/database (implied file)</li>
 *   <li>jdbc:hsqldb:res:/path/to/database (resource)</li>
 *   <li>jdbc:hsqldb:hsql://host:port/database (network)</li>
 *   <li>jdbc:hsqldb:hsqls://host:port/database (network with SSL)</li>
 *   <li>jdbc:hsqldb:http://host:port/database</li>
 *   <li>jdbc:hsqldb:https://host:port/database</li>
 * </ul>
 */
public class HSQLDBParser extends AbstractUrlParser {

    public HSQLDBParser() {

        super(DatabaseProduct.HSQLDB);
    }


    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);

        DatabaseProduct dbType = DatabaseProduct.HSQLDB;
        String protocol = extractProtocol(url, dbType);
        String remainder = removeProtocol(url, protocol);

        // Split properties
        String[] mainAndProps = splitByFirstPropertyDelimiter(remainder);
        String mainPart = mainAndProps[0];
        String propsString = mainAndProps.length > 1 ? mainAndProps[1] : null;

        // HSQLDB uses semicolon in path, so these are PATH properties, but also supports ? for QUERY
        PropertySource propertySource = remainder.contains("?") ? PropertySource.QUERY : PropertySource.PATH;
        Map<String, JdbcProperty> properties = parseProperties(propsString, propertySource);

        // Check for network modes
        if (mainPart.startsWith("hsql://") || mainPart.startsWith("hsqls://") ||
            mainPart.startsWith("http://") || mainPart.startsWith("https://")) {
            return parseNetworkMode(url, protocol, mainPart, properties);
        }

        // File-based or in-memory mode
        String databasePath = mainPart;

        if (mainPart.startsWith("mem:")) {
            addDerivedProperty(properties, "MODE", "MEMORY");
            databasePath = mainPart.substring(4);
        } else if (mainPart.startsWith("file:")) {
            addDerivedProperty(properties, "MODE", "FILE");
            databasePath = mainPart.substring(5);
        } else if (mainPart.startsWith("res:")) {
            addDerivedProperty(properties, "MODE", "RESOURCE");
            databasePath = mainPart.substring(4);
        } else {
            // Default is file mode
            addDerivedProperty(properties, "MODE", "FILE");
        }

        return new JdbcUrl(url, dbType, protocol, List.of(), databasePath, properties);
    }


    /**
     * Parses HSQLDB in network mode.
     */
    private JdbcUrl parseNetworkMode(String url, String protocol, String mainPart, Map<String, JdbcProperty> properties) {

        String scheme;
        String withoutScheme;

        if (mainPart.startsWith("hsqls://")) {
            scheme = "hsqls";
            withoutScheme = mainPart.substring(8);
        } else if (mainPart.startsWith("hsql://")) {
            scheme = "hsql";
            withoutScheme = mainPart.substring(7);
        } else if (mainPart.startsWith("https://")) {
            scheme = "https";
            withoutScheme = mainPart.substring(8);
        } else { // http://
            scheme = "http";
            withoutScheme = mainPart.substring(7);
        }

        String[] hostAndDb = splitFirst(withoutScheme, '/');
        String hostString = hostAndDb[0];
        String databaseName = hostAndDb.length > 1 ? hostAndDb[1] : "";

        Host host = parseHost(hostString);
        addDerivedProperty(properties, "MODE", scheme.toUpperCase());

        return new JdbcUrl(url, DatabaseProduct.HSQLDB, protocol, List.of(host), databaseName, properties);
    }
}
