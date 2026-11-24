package net.osslabz.jdbc.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.PropertySource;

/**
 * Parser for SQLite JDBC URLs.
 *
 * <p>Read as sqlite-jdbc reads them: the prefix is case-insensitive, the first '?' starts the query and its pairs are
 * separated by '&amp;' only. Query pairs are properties and never part of the database name, so a driver-specific
 * file name such as {@code t.db?mode=ro} is not reproduced.
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
        String protocol = extractProtocol(url, DatabaseProduct.SQLITE);

        // The driver trims the whole URL, so trailing whitespace never reaches the database name.
        String address = url.trim().substring(protocol.length());
        int queryStart = address.indexOf('?');
        String databaseName = queryStart < 0 ? address : address.substring(0, queryStart);
        Map<String, JdbcProperty> properties =
                queryStart < 0 ? new LinkedHashMap<>() : queryProperties(address.substring(queryStart + 1));

        addDerivedModeUnlessWritten(properties, ":memory:".equals(databaseName) ? "MEMORY" : "FILE");
        return new JdbcUrl(url, DatabaseProduct.SQLITE, protocol, List.of(), databaseName, properties);
    }

    private static Map<String, JdbcProperty> queryProperties(String query) {

        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        for (RawPair pair : splitPairs(query, '&')) {
            String key = pair.key().trim();
            if (!key.isEmpty()) {
                String value = pair.value() == null ? "" : pair.value().trim();
                properties.put(key, new JdbcProperty(PropertySource.QUERY, value));
            }
        }
        return properties;
    }
}
