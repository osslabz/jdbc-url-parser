package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


/**
 * Integration tests for the main JdbcUrlParser facade.
 */
class JdbcUrlParserTest {

    @Test
    void testDetectDatabaseProduct() {

        assertEquals(DatabaseProduct.MYSQL, JdbcUrlParser.detectDatabaseProduct("jdbc:mysql://localhost/db"));
        assertEquals(DatabaseProduct.POSTGRESQL, JdbcUrlParser.detectDatabaseProduct("jdbc:postgresql://localhost/db"));
        assertEquals(DatabaseProduct.ORACLE, JdbcUrlParser.detectDatabaseProduct("jdbc:oracle:thin:@localhost:1521:ORCL"));
        assertEquals(DatabaseProduct.SQLSERVER, JdbcUrlParser.detectDatabaseProduct("jdbc:sqlserver://localhost;databaseName=db"));
        assertEquals(DatabaseProduct.H2, JdbcUrlParser.detectDatabaseProduct("jdbc:h2:mem:test"));
        assertEquals(DatabaseProduct.HSQLDB, JdbcUrlParser.detectDatabaseProduct("jdbc:hsqldb:file:test"));
        assertEquals(DatabaseProduct.DERBY, JdbcUrlParser.detectDatabaseProduct("jdbc:derby:mydb"));
        assertEquals(DatabaseProduct.SQLITE, JdbcUrlParser.detectDatabaseProduct("jdbc:sqlite:test.db"));
    }


    @Test
    void testIsJdbcUrl() {

        assertTrue(JdbcUrlParser.isJdbcUrl("jdbc:mysql://localhost/db"));
        assertTrue(JdbcUrlParser.isJdbcUrl("JDBC:MYSQL://localhost/db"));
        assertFalse(JdbcUrlParser.isJdbcUrl("mysql://localhost/db"));
        assertFalse(JdbcUrlParser.isJdbcUrl(null));
        assertFalse(JdbcUrlParser.isJdbcUrl(""));
    }


    @Test
    void testTryParseValid() {

        JdbcUrl result = JdbcUrlParser.tryParse("jdbc:mysql://localhost/db");
        assertNotNull(result);
        assertEquals(DatabaseProduct.MYSQL, result.databaseProduct());
    }


    @Test
    void testTryParseInvalid() {

        JdbcUrl result = JdbcUrlParser.tryParse("invalid url");
        assertNull(result);
    }


    @Test
    void testTryParseNull() {

        JdbcUrl result = JdbcUrlParser.tryParse(null);
        assertNull(result);
    }


    @Test
    void testParseNullThrowsException() {

        assertThrows(IllegalArgumentException.class, () -> JdbcUrlParser.parse(null));
    }


    @Test
    void testParseBlankThrowsException() {

        assertThrows(IllegalArgumentException.class, () -> JdbcUrlParser.parse("  "));
    }


    @Test
    void testParseNonJdbcUrlThrowsException() {

        assertThrows(JdbcUrlParseException.class, () -> JdbcUrlParser.parse("mysql://localhost/db"));
    }


    @Test
    void testParseUnknownDatabaseThrowsException() {

        assertThrows(JdbcUrlParseException.class, () -> JdbcUrlParser.parse("jdbc:unknown://localhost/db"));
    }


    @Test
    void testJdbcUrlEquality() {

        String url = "jdbc:mysql://localhost:3306/mydb";
        JdbcUrl parsed1 = JdbcUrlParser.parse(url);
        JdbcUrl parsed2 = JdbcUrlParser.parse(url);

        assertEquals(parsed1, parsed2);
        assertEquals(parsed1.hashCode(), parsed2.hashCode());
    }


    @Test
    void testJdbcUrlToString() {

        String url = "jdbc:postgresql://localhost:5432/testdb";
        JdbcUrl parsed = JdbcUrlParser.parse(url);
        String toString = parsed.toString();

        assertTrue(toString.contains("POSTGRESQL"));
        assertTrue(toString.contains("jdbc:postgresql:"));
    }


    @Test
    void testOriginalUrlPreserved() {

        String url = "jdbc:mysql://localhost:3306/mydb?useSSL=true";
        JdbcUrl parsed = JdbcUrlParser.parse(url);

        assertEquals(url, parsed.originalUrl());
    }



    @Test
    void testIsNetworkBased() {

        JdbcUrl mysql = JdbcUrlParser.parse("jdbc:mysql://localhost/db");
        assertTrue(mysql.isNetworkBased());
        assertFalse(mysql.isFileBased());

        JdbcUrl h2 = JdbcUrlParser.parse("jdbc:h2:mem:test");
        assertFalse(h2.isNetworkBased());
        assertTrue(h2.isFileBased());
    }


    @Test
    void testGetPrimaryHost() {

        JdbcUrl mysql = JdbcUrlParser.parse("jdbc:mysql://host1:3306,host2:3307/db");
        assertEquals("host1", mysql.getPrimaryHost().hostname());
        assertEquals(3306, mysql.getPrimaryHost().port());

        JdbcUrl sqlite = JdbcUrlParser.parse("jdbc:sqlite:test.db");
        assertNull(sqlite.getPrimaryHost());
    }
}
