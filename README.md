# JDBC URL Parser
==================

![GitHub](https://img.shields.io/github/license/osslabz/jdbc-url-parser)
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/osslabz/jdbc-url-parser/build-on-push.yml?branch=dev&label=build&logo=git)
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/osslabz/jdbc-url-parser/release.yml?branch=dev&label=perform-release&logo=semanticrelease)
[![Reproducible Builds](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jvm-repo-rebuild/reproducible-central/master/content/net/osslabz/jdbc-url-parser/badge.json)](https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/content/net/osslabz/jdbc-url-parser/README.md)
[![Maven Central](https://img.shields.io/maven-central/v/net.osslabz/jdbc-url-parser?label=Maven%20Central)](https://search.maven.org/artifact/net.osslabz/jdbc-url-parser)



A JDBC URL parser for Java 17+. It reads a URL the way the database's own JDBC driver does.

Releases 0.1.0 and 0.1.1 went out on 2025-10-30, and 0.1.1 changed only CI and the pom description. Release 0.1.2 (2026-09-26) fixed URL prefix detection under a Turkish default locale and made the jar reproducible from its tag. Release 0.1.3 (2026-11-09) stopped shipping git.properties in the jar. Release 0.2.0 (2026-11-24) reads each URL the way its driver does. Nine database products are covered, out of the 21 drivers Spring Boot 3.5 knows, and 1003 tests assert the parsed output for all nine. One other project of mine depends on it.


## Features

- **Comprehensive Database Support**: Parses JDBC URLs for 9 major database types
- **Property Source Tracking**: Identifies where each property originated (query string, path parameters, authority, derived, or descriptor)
 extensible design
- **Type-Safe**: Uses Java 17 records for immutability and null safety
- **Zero Dependencies**: Pure Java implementation (only SLF4J for logging)

## Supported Databases

| Database        | Features                                                                  |
|-----------------|---------------------------------------------------------------------------|
| **MySQL**       | Multi-host, userinfo, host attributes, loadbalance/replication            |
| **MariaDB**     | Multi-host, address=, HA modes                                            |
| **PostgreSQL**  | Multi-host, IPv6, PGHOST/PGPORT/PGDBNAME overrides                        |
| **Oracle**      | SID, service name, easy connect host lists, descriptors                   |
| **SQL Server**  | Instance names, `{}` quoting, synonyms, serverName/portNumber overrides   |
| **H2**          | Memory, file, TCP, SSL modes                                              |
| **HSQLDB**      | Memory, file, resource, server modes                                      |
| **Derby**       | Embedded, memory, network modes                                           |
| **SQLite**      | File and in-memory databases                                              |

## Follows the driver

Each parser reads a URL the way the product's JDBC driver reads it. It reports the hosts, ports, database,
instance and properties the driver would connect with. The reference drivers are:

| Database   | Reference driver           |
|------------|----------------------------|
| SQL Server | mssql-jdbc 13.4.0          |
| Oracle     | ojdbc11 23.26.3            |
| PostgreSQL | PostgreSQL JDBC 42.7.14    |
| MySQL      | Connector/J 9.7.0          |
| MariaDB    | MariaDB Connector/J 3.5.10 |
| H2         | H2 2.4.240                 |
| HSQLDB     | HSQLDB 2.7.3               |
| Derby      | Derby 10.16.1.1            |
| SQLite     | sqlite-jdbc 3.53.2.1       |

A few differences are deliberate. The parser rejects hostnames and ports that can never connect, and it does not
validate option values.

Behaviour changes in 0.2.0:

- A host without a port gets the driver's default port: `jdbc:mysql://db/shop` gives `Host(db, 3306)`.
- A URL the driver rejects throws `JdbcUrlParseException`.
- Userinfo (`user:password@`) and Connector/J host attributes come back as `AUTHORITY` properties.
- SQL Server instance URLs without a port report port `null` (was `0`).
- `;` no longer splits MySQL, PostgreSQL or SQLite queries.
- `protocol` carries the sub-protocol: `jdbc:mysql:replication:`.
- H2 and HSQLDB keep `?` text in the database name, as their drivers do.
- Parse error messages no longer contain the URL. `JdbcUrlParseException.getJdbcUrl()` still returns it.

## Installation

### Maven

```xml
<dependency>
    <groupId>net.osslabz</groupId>
    <artifactId>jdbc-url-parser</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Gradle

```gradle
implementation 'net.osslabz:jdbc-url-parser:0.2.0'
```

### Snapshots

Every push to `dev` publishes the next version as a `-SNAPSHOT` to Central's snapshot repository. Maven doesn't
search that repository by default, so a build that wants a snapshot declares it:

```xml
<repositories>
    <repository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

## Quick Start

### Simple Example

```java
import net.osslabz.jdbc.JdbcUrlParser;
import net.osslabz.jdbc.JdbcUrl;

// Parse a MySQL JDBC URL

JdbcUrl url = JdbcUrlParser.parse("jdbc:mysql://localhost:3306/mydb?useSSL=true");

// Access components
DatabaseProduct type = url.databaseProduct();     // MYSQL
String protocol = url.protocol();                 // "jdbc:mysql:"
List<Host> hosts = url.hosts();                   // [Host(localhost, 3306)]
String database = url.databaseName();             // "mydb"

// Access property values
String sslValue = url.getPropertyValue("useSSL"); // "true"

// Helper methods
boolean isNetwork = url.isNetworkBased();         // true
Host primary = url.getPrimaryHost();              // Host(localhost, 3306)
```

## Database-Specific Examples

### MySQL - Complex Example

```java
import net.osslabz.jdbc.*;
import java.util.List;
import java.util.Map;


// Multi-host MySQL with failover and SSL configuration
String mysqlUrl = "jdbc:mysql://primary:3306,secondary:3307,tertiary:3308/production" +
                  "?useSSL=true&serverTimezone=UTC&maxReconnects=3";

JdbcUrl url = JdbcUrlParser.parse(mysqlUrl);

// Access all hosts (for failover/load balancing)
List<Host> hosts = url.hosts();
// Returns: [Host(primary, 3306), Host(secondary, 3307), Host(tertiary, 3308)]

// Get primary host
Host primary = url.getPrimaryHost();
// Returns: Host(primary, 3306)

// Access property values
String ssl = url.getPropertyValue("useSSL");           // "true"
String timezone = url.getPropertyValue("serverTimezone"); // "UTC"

// All properties with source information
Map<String, JdbcProperty> props = url.properties();
// Each property includes its source: QUERY, PATH, AUTHORITY, DERIVED, or DESCRIPTOR
```

### SQL Server - Path Properties

```java
import net.osslabz.jdbc.*;

// SQL Server with instance name and semicolon-separated properties
String sqlServerUrl = "jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=TestDB;" +
                      "encrypt=true;trustServerCertificate=false;loginTimeout=30";

JdbcUrl url = JdbcUrlParser.parse(sqlServerUrl);

// Host includes instance name
Host host = url.getPrimaryHost();
// Returns: Host(localhost, null, SQLEXPRESS)

String instanceName = host.instanceName();        // "SQLEXPRESS"
String database = url.databaseName();             // "TestDB"

// Properties from path (semicolon-separated)
String encrypt = url.getPropertyValue("encrypt");      // "true"
String timeout = url.getPropertyValue("loginTimeout"); // "30"

// Get property with source information
JdbcProperty encryptProp = url.getProperty("encrypt");
PropertySource source = encryptProp.source();     // PATH
String value = encryptProp.value();               // "true"
```

### Oracle - Multiple Formats

```java
import net.osslabz.jdbc.*;

// Oracle with SID format
JdbcUrl oracleSid = JdbcUrlParser.parse("jdbc:oracle:thin:@localhost:1521:ORCL");

String driverType = oracleSid.getPropertyValue("DRIVER_TYPE");  // "thin" (DERIVED)
String sid = oracleSid.getPropertyValue("SID");                 // "ORCL" (DESCRIPTOR)

// Oracle with Service Name format
JdbcUrl oracleService = JdbcUrlParser.parse("jdbc:oracle:thin:@//dbserver:1521/myservice");

String serviceName = oracleService.getPropertyValue("SERVICE_NAME");  // "myservice"
String database = oracleService.databaseName();                       // "myservice"

// Oracle with TNSNAMES descriptor
String descriptorUrl = "jdbc:oracle:thin:@(DESCRIPTION=" +
                       "(ADDRESS=(PROTOCOL=TCP)(HOST=dbhost)(PORT=1521))" +
                       "(CONNECT_DATA=(SERVICE_NAME=proddb)))";

JdbcUrl oracleDescriptor = JdbcUrlParser.parse(descriptorUrl);

// Properties extracted from descriptor
String service = oracleDescriptor.getPropertyValue("SERVICE_NAME");  // "proddb"
String descriptor = oracleDescriptor.getPropertyValue("DESCRIPTOR"); // Full descriptor string

// Get property with source information
JdbcProperty serviceNameProp = oracleDescriptor.getProperty("SERVICE_NAME");
PropertySource source = serviceNameProp.source();  // DESCRIPTOR
```

## Advanced Features

### Property Source Tracking

Every property includes metadata about its origin based on RFC 3986 URI components:

```java
JdbcUrl url = JdbcUrlParser.parse(
    "jdbc:sqlserver://localhost;databaseName=testdb;encrypt=true"
);

// Get property with source information
JdbcProperty dbNameProp = url.getProperty("databaseName");
dbNameProp.source();  // PropertySource.PATH
dbNameProp.value();   // "testdb"

// Filter properties by source
Map<String, String> pathProps = url.getPropertiesBySource(PropertySource.PATH);
// Returns: {databaseName=testdb, encrypt=true}

Map<String, String> queryProps = url.getPropertiesBySource(PropertySource.QUERY);
// Returns: {} (empty, SQL Server uses PATH properties)

Map<String, String> derivedProps = url.getPropertiesBySource(PropertySource.DERIVED);
// Returns derived properties like MODE for H2/HSQLDB/Derby/SQLite

```

#### Property Source Types

| Source       | Description | Example Databases |
|--------------|-------------|-------------------|
| `QUERY`      | Properties from the query component, after `?` and separated by `&` | MySQL, MariaDB, PostgreSQL, SQLite, Oracle easy connect, HSQLDB `?user=`/`&password=` markers |
| `PATH`       | Properties embedded in the path, separated by `;` | SQL Server, H2, HSQLDB, Derby |
| `AUTHORITY`  | Userinfo (`user[:password]@`) and host attributes; values can carry credentials | MySQL, Oracle |
| `DERIVED`    | Properties computed by parser from URL structure | All (MODE, DRIVER_TYPE, etc.) |
| `DESCRIPTOR` | Properties extracted from connection descriptors | Oracle TNSNAMES |

### Property Access

```java
JdbcUrl url = JdbcUrlParser.parse("jdbc:mysql://localhost/db?useSSL=true&timeout=30");

// Get simple key-value map (values only)
Map<String, String> simpleProps = url.getPropertyValuesAsMap();
// Returns: {useSSL=true, timeout=30}

// Get full property map (with source info)
Map<String, JdbcProperty> detailedProps = url.properties();
// Each entry includes PropertySource metadata

// Get specific property value
String ssl = url.getPropertyValue("useSSL");
// Returns: "true"

// Get property with source information
JdbcProperty sslProp = url.getProperty("useSSL");
// Returns: JdbcProperty(QUERY, "true")

// Access source and value
PropertySource source = sslProp.source();  // QUERY
String value = sslProp.value();            // "true"
```

## Requirements

- Java 17 or higher
- Maven 3.6+ (for building)


## Error Handling

The parser throws `JdbcUrlParseException` for invalid URLs. The message names the problem and never repeats URL
text, which may hold a password:

```java
try {
    JdbcUrl url = JdbcUrlParser.parse("invalid-url");
} catch (JdbcUrlParseException e) {
    System.out.println(e.getMessage());
    System.out.println(e.getJdbcUrl());  // The URL that failed to parse
}
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
