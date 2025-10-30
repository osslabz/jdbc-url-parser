package net.osslabz.jdbc;

import java.util.Objects;


/**
 * Represents a JDBC URL property with its value and source origin.
 *
 * <p>This immutable value object captures both the property value and metadata
 * about where the property originated in the JDBC URL structure.
 *
 * @param source the origin of this property in the URL structure
 * @param value  the property value (never null, may be empty string for flags)
 */
public record JdbcProperty(PropertySource source, String value) {

    /**
     * Compact constructor with validation.
     */
    public JdbcProperty {
        Objects.requireNonNull(source, "Property source cannot be null");
        Objects.requireNonNull(value, "Property value cannot be null");
    }
}
