# JDBC URL Parser
==================

![GitHub](https://img.shields.io/github/license/osslabz/jdbc-url-parser)
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/osslabz/jdbc-url-parser/build-on-push.yml?branch=dev&label=build&logo=git)
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/osslabz/jdbc-url-parser/build-release-on-main-push.yml?branch=main&label=perform-release&logo=semanticrelease)
[![Maven Central](https://img.shields.io/maven-central/v/net.osslabz/jdbc-url-parser?label=Maven%20Central)](https://search.maven.org/artifact/net.osslabz/jdbc-url-parser)



A comprehensive JDBC URL parser for Java 17+ that supports all databases autoconfigured by Spring Boot 3.5.


## Features

- **Comprehensive Database Support**: Parses JDBC URLs for 9 major database types
- **Property Source Tracking**: Identifies where each property originated (query string, path parameters, derived, or descriptor)
 extensible design
- **Type-Safe**: Uses Java 17 records for immutability and null safety
- **Zero Dependencies**: Pure Java implementation (only SLF4J for logging)

## Supported Databases

| Database        | Features                                |
|-----------------|-----------------------------------------|
| **MySQL**       | Single/multi-host, properties           |
| **MariaDB**     | Single/multi-host, properties           |
| **PostgreSQL**  | Single/multi-host, IPv6, properties     |
| **Oracle**      | SID, Service Name, TNSNAMES descriptors |
| **SQL Server**  | Instance names, semicolon properties    |
| **H2**          | Memory, file, TCP, SSL modes            |
| **HSQLDB**      | Memory, file, resource, server modes    |
| **Derby**       | Embedded, memory, network modes         |
| **SQLite**      | File and in-memory databases            |

## Installation

### Maven

```xml
<dependency>
    <groupId>net.osslabz</groupId>
    <artifactId>jdbc-url-parser</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Gradle

```gradle
implementation 'net.osslabz:jdbc-url-parser:0.1.0-SNAPSHOT'
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
// Each property includes its source: QUERY, PATH, DERIVED, or DESCRIPTOR
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
// Returns: Host(localhost, 0, SQLEXPRESS)

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
| `QUERY`      | Properties from query component (after `?`) | MySQL, PostgreSQL, SQLite |
| `PATH`       | Properties embedded in path (after `;`) | SQL Server, H2, HSQLDB, Derby |
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

The parser throws descriptive exceptions for invalid URLs:

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
