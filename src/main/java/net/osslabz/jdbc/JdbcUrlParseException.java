package net.osslabz.jdbc;

/**
 * Exception thrown when a JDBC URL cannot be parsed.
 */
public class JdbcUrlParseException extends RuntimeException {

    private final String jdbcUrl;


    /**
     * Creates a new parse exception.
     *
     * @param jdbcUrl the URL that failed to parse
     * @param message the error message
     */
    public JdbcUrlParseException(String jdbcUrl, String message) {

        super(message);
        this.jdbcUrl = jdbcUrl;
    }


    /**
     * Creates a new parse exception with a cause.
     *
     * @param jdbcUrl the URL that failed to parse
     * @param message the error message
     * @param cause   the underlying cause
     */
    public JdbcUrlParseException(String jdbcUrl, String message, Throwable cause) {

        super(message, cause);
        this.jdbcUrl = jdbcUrl;
    }


    /**
     * Gets the JDBC URL that failed to parse.
     *
     * @return the JDBC URL
     */
    public String getJdbcUrl() {

        return jdbcUrl;
    }


    @Override
    public String getMessage() {

        return super.getMessage() + " [URL: " + jdbcUrl + "]";
    }
}
