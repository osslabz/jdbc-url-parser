package net.osslabz.jdbc.parser;

import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.JdbcUrl;
import net.osslabz.jdbc.JdbcUrlParseException;


/**
 * Parser for MySQL and MariaDB JDBC URLs.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>jdbc:mysql://host:port/database</li>
 *   <li>jdbc:mysql://host1:port1,host2:port2/database</li>
 *   <li>jdbc:mysql://host/database?param=value</li>
 *   <li>jdbc:mariadb://host:port/database</li>
 * </ul>
 */
public class MySQLParser extends AbstractUrlParser {

    public MySQLParser() {

        super(DatabaseProduct.MYSQL, DatabaseProduct.MARIADB);
    }


    @Override
    public JdbcUrl parse(String url) {

        DatabaseProduct dbType = DatabaseProduct.fromUrl(url);
        if (!supports(dbType)) {
            throw new JdbcUrlParseException(url, "Unsupported database type for MySQL parser: " + dbType);
        }

        return parseStandardNetworkUrl(url, dbType);
    }
}
