package net.osslabz.jdbc;


/**
 * Indicates the source of a JDBC URL property based on RFC 3986 URI component terminology.
 *
 * <p>JDBC URLs can have properties originating from different parts of the URL:
 * <ul>
 *   <li>{@link #QUERY} - Properties from the query component (after '?')</li>
 *   <li>{@link #PATH} - Properties embedded in the path component (typically with ';')</li>
 *   <li>{@link #DERIVED} - Properties computed by the parser from URL structure</li>
 *   <li>{@link #DESCRIPTOR} - Properties extracted from connection descriptors</li>
 * </ul>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc3986">RFC 3986: Uniform Resource Identifier (URI): Generic Syntax</a>
 */
public enum PropertySource {

    /**
     * Property originates from the query component of the URI (after '?').
     *
     * <p>Example: In {@code jdbc:mysql://host/db?useSSL=true&user=admin},
     * {@code useSSL} and {@code user} are QUERY properties.
     *
     * <p>Per RFC 3986 Section 3.4, the query component contains non-hierarchical data,
     * often represented as key=value pairs separated by '&' or ';'.
     */
    QUERY,

    /**
     * Property is embedded in the path component using scheme-specific delimiters.
     *
     * <p>Example: In {@code jdbc:sqlserver://host;databaseName=mydb;encrypt=true},
     * {@code databaseName} and {@code encrypt} are PATH properties.
     *
     * <p>Per RFC 3986 Section 3.3, path segments may use reserved characters like ';'
     * to delimit scheme-specific or handler-specific subcomponents.
     */
    PATH,

    /**
     * Property is computed/derived by the parser from the URL structure.
     *
     * <p>Examples:
     * <ul>
     *   <li>MODE property derived from H2 prefix: {@code jdbc:h2:mem:testdb} → MODE=MEMORY</li>
     *   <li>DRIVER_TYPE from Oracle URL: {@code jdbc:oracle:thin:@host} → DRIVER_TYPE=thin</li>
     * </ul>
     *
     * <p>These properties are not explicitly present in the URL but are inferred
     * from the URL structure or format by the parser.
     */
    DERIVED,

    /**
     * Property is extracted from a connection descriptor or structured format.
     *
     * <p>Example: In Oracle TNSNAMES descriptor format
     * {@code jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=myhost)(PORT=1521))...)},
     * properties like HOST, PORT, SERVICE_NAME are extracted from the descriptor structure
     * and marked as DESCRIPTOR properties.
     *
     * <p>This distinguishes properties parsed from structured configuration formats
     * versus simple key=value pairs.
     */
    DESCRIPTOR
}
