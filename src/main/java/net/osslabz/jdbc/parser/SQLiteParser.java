package net.osslabz.jdbc.parser;

import java.util.List;
import java.util.Map;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.PropertySource;


/**
 * Parser for SQLite JDBC URLs.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:sqlite:/path/to/database.db</li>
 *   <li>jdbc:sqlite:C:\path\to\database.db (Windows)</li>
 *   <li>jdbc:sqlite::memory: (in-memory)</li>
 *   <li>jdbc:sqlite:file.db?param=value</li>
 * </ul>
 */
public class SQLiteParser extends AbstractUrlParser {

    public SQLiteParser() {

        super(DatabaseProduct.SQLITE);
    }


    @Override
    public JdbcUrl parse(String url) {

        validateJdbcPrefix(url);

        DatabaseProduct dbType = DatabaseProduct.SQLITE;
        String protocol = extractProtocol(url, dbType);
        String remainder = removeProtocol(url, protocol);

        // Split properties (QUERY properties after ?)
        String[] mainAndProps = splitFirst(remainder, '?');
        String databasePath = mainAndProps[0];
        String propsString = mainAndProps.length > 1 ? mainAndProps[1] : null;

        Map<String, JdbcProperty> properties = parseProperties(propsString, PropertySource.QUERY);

        // Check for in-memory database
        if (":memory:".equals(databasePath) || "memory:".equals(databasePath)) {
            addDerivedProperty(properties, "MODE", "MEMORY");
            databasePath = ":memory:";
        } else {
            addDerivedProperty(properties, "MODE", "FILE");
        }

        // SQLite is always file-based (no network hosts)
        return new JdbcUrl(url, dbType, protocol, List.of(), databasePath, properties);
    }
}
