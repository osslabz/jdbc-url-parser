package net.osslabz.jdbc;

import java.util.ArrayList;
import java.util.List;
import net.osslabz.jdbc.parser.DerbyParser;
import net.osslabz.jdbc.parser.H2Parser;
import net.osslabz.jdbc.parser.HSQLDBParser;
import net.osslabz.jdbc.parser.MySQLParser;
import net.osslabz.jdbc.parser.OracleParser;
import net.osslabz.jdbc.parser.PostgreSQLParser;
import net.osslabz.jdbc.parser.SQLServerParser;
import net.osslabz.jdbc.parser.SQLiteParser;
import net.osslabz.jdbc.parser.UrlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Main entry point for parsing JDBC URLs. This class provides a simple facade for parsing JDBC URLs of various database types.
 *
 * <p>Example usage:
 * <pre>{@code
 * JdbcUrl url = JdbcUrlParser.parse("jdbc:mysql://localhost:3306/mydb");
 * DatabaseProduct type = url.databaseProduct();
 * List<Host> hosts = url.hosts();
 * String database = url.databaseName();
 * }</pre>
 *
 * <p>Supported databases:
 * <ul>
 *   <li>MySQL and MariaDB</li>
 *   <li>PostgreSQL</li>
 *   <li>Oracle</li>
 *   <li>Microsoft SQL Server</li>
 *   <li>H2</li>
 *   <li>HSQLDB</li>
 *   <li>Apache Derby</li>
 *   <li>SQLite</li>
 * </ul>
 */
public class JdbcUrlParser {

    private static final Logger log = LoggerFactory.getLogger(JdbcUrlParser.class);

    private static final List<UrlParser> PARSERS = new ArrayList<>();

    static {
        // Register all database-specific parsers
        PARSERS.add(new MySQLParser());
        PARSERS.add(new PostgreSQLParser());
        PARSERS.add(new OracleParser());
        PARSERS.add(new SQLServerParser());
        PARSERS.add(new H2Parser());
        PARSERS.add(new HSQLDBParser());
        PARSERS.add(new DerbyParser());
        PARSERS.add(new SQLiteParser());
    }

    /**
     * Private constructor to prevent instantiation. This is a utility class with static methods only.
     */
    private JdbcUrlParser() {

        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }


    /**
     * Parses a JDBC URL into its components.
     *
     * @param url the JDBC URL to parse (must not be null or blank)
     * @return the parsed JDBC URL object
     *
     * @throws JdbcUrlParseException    if the URL cannot be parsed or is invalid
     * @throws IllegalArgumentException if the URL is null or blank
     */
    public static JdbcUrl parse(String url) {

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("JDBC URL cannot be null or blank");
        }

        log.debug("Parsing JDBC URL: {}", url);

        // Detect database type from URL
        DatabaseProduct databaseProduct = DatabaseProduct.fromUrl(url);

        if (databaseProduct == DatabaseProduct.UNKNOWN) {
            throw new JdbcUrlParseException(url, "Unknown or unsupported database type");
        }

        log.debug("Detected database type: {}", databaseProduct);

        // Find appropriate parser for the database type
        for (UrlParser parser : PARSERS) {
            if (parser.supports(databaseProduct)) {
                log.debug("Using parser: {}", parser.getClass().getSimpleName());
                return parser.parse(url);
            }
        }

        // This should not happen if parsers are registered correctly
        throw new JdbcUrlParseException(url, "No parser available for database type: " + databaseProduct);
    }


    /**
     * Attempts to parse a JDBC URL, returning null if parsing fails instead of throwing an exception.
     *
     * @param url the JDBC URL to parse
     * @return the parsed JDBC URL object, or null if parsing fails
     */
    public static JdbcUrl tryParse(String url) {

        try {
            return parse(url);
        } catch (Exception e) {
            log.debug("Failed to parse JDBC URL: {}", url, e);
            return null;
        }
    }


    /**
     * Checks if a string appears to be a valid JDBC URL (starts with "jdbc:").
     *
     * @param url the string to check
     * @return true if the string starts with "jdbc:" (case-insensitive)
     */
    public static boolean isJdbcUrl(String url) {

        return url != null && url.toLowerCase().startsWith("jdbc:");
    }


    /**
     * Detects the database type from a JDBC URL without fully parsing it.
     *
     * @param url the JDBC URL
     * @return the detected database type, or UNKNOWN if not recognized
     */
    public static DatabaseProduct detectDatabaseProduct(String url) {

        return DatabaseProduct.fromUrl(url);
    }
}
