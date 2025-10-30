package net.osslabz.jdbc.parser;

import java.util.List;
import java.util.Map;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.PropertySource;


/**
 * Parser for Microsoft SQL Server JDBC URLs.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:sqlserver://host:port;databaseName=db</li>
 *   <li>jdbc:sqlserver://host\instance;databaseName=db</li>
 *   <li>jdbc:sqlserver://host:port;databaseName=db;property=value</li>
 * </ul>
 *
 * <p>Note: SQL Server uses semicolon (;) as the property separator, not question mark (?).
 */
public class SQLServerParser extends AbstractUrlParser {

    public SQLServerParser() {

        super(DatabaseProduct.SQLSERVER);
    }


    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);

        DatabaseProduct dbType = DatabaseProduct.SQLSERVER;
        String protocol = extractProtocol(url, dbType);
        String remainder = removeProtocol(url, protocol);

        // Remove leading slashes
        if (remainder.startsWith("//")) {
            remainder = remainder.substring(2);
        }

        // SQL Server format: host[:port][\instance];property=value;...
        // Split by first semicolon to separate host from properties
        String[] parts = splitFirst(remainder, ';');
        String hostPart = parts[0];
        String propertiesPart = parts.length > 1 ? parts[1] : "";

        // Parse host (may include instance name with backslash)
        Host host = parseHost(hostPart);

        // Parse properties (semicolon-separated path parameters)
        Map<String, JdbcProperty> properties = parseProperties(propertiesPart, PropertySource.PATH);

        // Extract database name from properties (it's typically in 'databaseName' property)
        JdbcProperty dbNameProp = properties.get("databaseName");
        if (dbNameProp == null) {
            dbNameProp = properties.get("database");
        }
        String databaseName = dbNameProp != null ? dbNameProp.value() : "";

        return new JdbcUrl(url, dbType, protocol, List.of(host), databaseName, properties);
    }
}
