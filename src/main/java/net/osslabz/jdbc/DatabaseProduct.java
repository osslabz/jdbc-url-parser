package net.osslabz.jdbc;

/**
 * Enumeration of supported database types. This covers the core subset of databases commonly used with Spring Boot.
 */
public enum DatabaseProduct {
    /**
     * MySQL database
     */
    MYSQL("mysql"),

    /**
     * MariaDB database
     */
    MARIADB("mariadb"),

    /**
     * PostgreSQL database
     */
    POSTGRESQL("postgresql"),

    /**
     * Oracle database
     */
    ORACLE("oracle"),

    /**
     * Microsoft SQL Server
     */
    SQLSERVER("sqlserver"),

    /**
     * H2 embedded database
     */
    H2("h2"),

    /**
     * HSQLDB (HyperSQL) database
     */
    HSQLDB("hsqldb"),

    /**
     * Apache Derby database
     */
    DERBY("derby"),

    /**
     * SQLite database
     */
    SQLITE("sqlite"),

    /**
     * Unknown or unsupported database type
     */
    UNKNOWN("");

    private final String productIndicator;


    DatabaseProduct(String productIndicator) {

        this.productIndicator = productIndicator;
    }


    /**
     * Gets the product indicator for this database type.
     *
     * @return the product indicator (e.g., "mysql", "postgresql")
     */
    public String getProductIndicator() {

        return productIndicator;
    }


    /**
     * Gets the JDBC URL prefix for this database type.
     *
     * @return the URL prefix (e.g., "jdbc:mysql:")
     */
    public String getUrlPrefix() {

        if (this == UNKNOWN) {
            return "";
        }
        return "jdbc:" + productIndicator + ":";
    }


    /**
     * Detects the database type from a JDBC URL.
     *
     * @param url the JDBC URL to analyze
     * @return the detected database type, or UNKNOWN if not recognized
     */
    public static DatabaseProduct fromUrl(String url) {

        if (url == null || url.isBlank()) {
            return UNKNOWN;
        }

        String lowerUrl = url.toLowerCase();

        // Check each database type's prefix
        for (DatabaseProduct type : values()) {
            if (type != UNKNOWN && lowerUrl.startsWith(type.getUrlPrefix().toLowerCase())) {
                return type;
            }
        }

        return UNKNOWN;
    }
}
