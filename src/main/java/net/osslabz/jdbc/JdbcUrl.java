package net.osslabz.jdbc;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * Represents a parsed JDBC URL with all its components. This is an immutable value object that provides structured access to JDBC URL parts.
 *
 * @param originalUrl  the original unparsed URL
 * @param databaseProduct the detected database type
 * @param protocol     the full JDBC protocol (e.g., "jdbc:mysql")
 * @param hosts        list of database hosts (empty for file-based databases)
 * @param databaseName the database name, file path, or memory database identifier
 * @param properties   connection properties extracted from the URL with source information
 */
public record JdbcUrl(
    String originalUrl,
    DatabaseProduct databaseProduct,
    String protocol,
    List<Host> hosts,
    String databaseName,
    Map<String, JdbcProperty> properties
) {

    /**
     * Compact constructor with validation and defensive copying.
     */
    public JdbcUrl {

        Objects.requireNonNull(originalUrl, "Original URL cannot be null");
        Objects.requireNonNull(databaseProduct, "Database type cannot be null");
        Objects.requireNonNull(protocol, "Protocol cannot be null");

        // Make defensive copies of mutable collections
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }


    /**
     * Gets the first host from the hosts list.
     *
     * @return the first host, or null if no hosts are present
     */
    public Host getPrimaryHost() {

        return hosts.isEmpty() ? null : hosts.get(0);
    }


    /**
     * Gets a specific property value.
     *
     * @param key the property key
     * @return the property value, or null if not present
     */
    public String getPropertyValue(String key) {

        JdbcProperty prop = properties.get(key);
        return prop != null ? prop.value() : null;
    }


    /**
     * Gets the full property object with value and source information.
     *
     * @param key the property key
     * @return the JdbcProperty, or null if not present
     */
    public JdbcProperty getProperty(String key) {

        return properties.get(key);
    }


    /**
     * Gets all properties that originated from a specific source.
     *
     * @param source the property source to filter by
     * @return map of property keys to values for the given source
     */
    public Map<String, String> getPropertiesBySource(PropertySource source) {

        return properties.entrySet().stream()
            .filter(entry -> entry.getValue().source() == source)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().value()
            ));
    }


    /**
     * Gets all properties as a simple key-value map (without source information).
     *
     * @return map of property keys to values
     */
    public Map<String, String> getPropertyValuesAsMap() {

        return properties.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().value()
            ));
    }


    /**
     * Checks if this is a file-based database connection.
     *
     * @return true if the database is file-based (no network hosts)
     */
    public boolean isFileBased() {

        return hosts.isEmpty();
    }


    /**
     * Checks if this is a network-based database connection.
     *
     * @return true if the database uses network hosts
     */
    public boolean isNetworkBased() {

        return !hosts.isEmpty();
    }


    @Override
    public String toString() {

        return "JdbcUrl{" +
               "type=" + databaseProduct +
               ", protocol='" + protocol + '\'' +
               ", hosts=" + hosts +
               ", database='" + databaseName + '\'' +
               ", properties=" + properties.size() + " entries" +
               '}';
    }
}
