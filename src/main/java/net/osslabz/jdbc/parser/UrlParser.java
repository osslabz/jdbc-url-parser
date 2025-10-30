package net.osslabz.jdbc.parser;

import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.JdbcUrl;


/**
 * Interface for database-specific JDBC URL parsers. Each database type has its own parser implementation to handle vendor-specific URL formats.
 */
public interface UrlParser {

    /**
     * Checks if this parser can handle the given database type.
     *
     * @param databaseProduct the database type to check
     * @return true if this parser supports the database type
     */
    boolean supports(DatabaseProduct databaseProduct);

    /**
     * Parses a JDBC URL into its components.
     *
     * @param url the JDBC URL to parse
     * @return the parsed JDBC URL object
     *
     * @throws net.osslabz.jdbc.JdbcUrlParseException if the URL cannot be parsed
     */
    JdbcUrl parse(String url);
}
