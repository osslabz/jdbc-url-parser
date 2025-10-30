package net.osslabz.jdbc.parser;

import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.JdbcUrl;


/**
 * Parser for PostgreSQL JDBC URLs.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:postgresql://host:port/database</li>
 *   <li>jdbc:postgresql://host/database</li>
 *   <li>jdbc:postgresql://host:port/database?param=value</li>
 *   <li>jdbc:postgresql://host1:port1,host2:port2/database (multi-host)</li>
 * </ul>
 */
public class PostgreSQLParser extends AbstractUrlParser {

    public PostgreSQLParser() {

        super(DatabaseProduct.POSTGRESQL);
    }


    @Override
    public JdbcUrl parse(String url) {

        return parseStandardNetworkUrl(url, DatabaseProduct.POSTGRESQL);
    }
}
