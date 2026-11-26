package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A parse failure carries no cause, so a logged stack trace holds no text from the URL.
 */
class ParseExceptionCauseTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "jdbc:sqlserver://db:S3Cx", // AbstractUrlParser.parsePort
                "jdbc:h2:tcp://db:S3Cx/shop", // H2Parser.decodePort
                "jdbc:postgresql://db/shop?password=%S3Cx" // AbstractUrlParser.percentDecode
            })
    void throwsWithoutCauseAndLeaksNoUrlTextInTheStackTrace(String url) {

        JdbcUrlParseException e = assertThrows(JdbcUrlParseException.class, () -> JdbcUrlParser.parse(url));

        assertNull(e.getCause());
        StringWriter trace = new StringWriter();
        e.printStackTrace(new PrintWriter(trace));
        assertFalse(trace.toString().contains("S3Cx"), trace.toString());
    }
}
