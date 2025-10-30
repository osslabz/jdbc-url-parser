package net.osslabz.jdbc;

/**
 * Represents a database host with hostname, port, and optional instance name. This is an immutable value object.
 */
public record Host(String hostname, Integer port, String instanceName) {

    /**
     * Compact constructor with validation.
     */
    public Host {

        if (hostname == null || hostname.isBlank()) {
            throw new IllegalArgumentException("Hostname cannot be null or blank");
        }
    }


    /**
     * Creates a host with just hostname (no port or instance).
     *
     * @param hostname the hostname
     * @return a new Host instance
     */
    public static Host of(String hostname) {

        return new Host(hostname, null, null);
    }


    /**
     * Creates a host with hostname and port.
     *
     * @param hostname the hostname
     * @param port     the port number
     * @return a new Host instance
     */
    public static Host of(String hostname, int port) {

        return new Host(hostname, port, null);
    }


    /**
     * Creates a host with hostname, port, and instance name (for SQL Server).
     *
     * @param hostname     the hostname
     * @param port         the port number
     * @param instanceName the instance name
     * @return a new Host instance
     */
    public static Host of(String hostname, int port, String instanceName) {

        return new Host(hostname, port, instanceName);
    }


    /**
     * Returns a string representation suitable for connection strings.
     *
     * @return formatted host string
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder(hostname);
        if (instanceName != null && !instanceName.isBlank()) {
            sb.append('\\').append(instanceName);
        }
        if (port != null) {
            sb.append(':').append(port);
        }
        return sb.toString();
    }
}
