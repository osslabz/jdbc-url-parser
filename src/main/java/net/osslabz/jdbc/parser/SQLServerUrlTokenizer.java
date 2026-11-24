package net.osslabz.jdbc.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.osslabz.jdbc.JdbcUrlParseException;

/**
 * Splits a SQL Server URL into its host part and properties the way mssql-jdbc's {@code Util.parseUrl} does.
 *
 * <p>The host part is {@code host[:port][\instance[:port]]} up to the first ';'. Properties are ';'-separated
 * {@code name=value} pairs; a value starting with '{' runs to the matching '}', where "}}" is a literal '}'.
 */
final class SQLServerUrlTokenizer {

    static final String PREFIX = "jdbc:sqlserver://";

    private static final char PAIR_SEPARATOR = ';';

    private static final char PORT_SEPARATOR = ':';

    private static final char INSTANCE_SEPARATOR = '\\';

    private static final char ESCAPE_START = '{';

    private static final char ESCAPE_END = '}';

    /**
     * The raw parts of a SQL Server URL.
     *
     * @param host       the trimmed host text, null when the host part names none
     * @param port       the trimmed port text, null when the host part has no ':'
     * @param instance   the trimmed instance text, null when the host part has no '\'
     * @param properties the properties keyed as written, values unescaped; a repeated key holds its last value at
     *                   the position of its last occurrence
     */
    record Result(String host, String port, String instance, Map<String, String> properties) {

        Result {
            properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        }
    }

    private final String url;

    private final Map<String, String> properties = new LinkedHashMap<>();

    private int position;

    private SQLServerUrlTokenizer(String url, int position) {

        this.url = url;
        this.position = position;
    }

    /**
     * Tokenizes a URL that starts with {@link #PREFIX}.
     *
     * @param url the URL
     * @return the raw parts
     * @throws JdbcUrlParseException if the property list is malformed
     */
    static Result tokenize(String url) {

        int hostPartEnd = url.indexOf(PAIR_SEPARATOR, PREFIX.length());
        if (hostPartEnd < 0) {
            hostPartEnd = url.length();
        }
        String hostPart = url.substring(PREFIX.length(), hostPartEnd);
        Map<String, String> properties = new SQLServerUrlTokenizer(url, hostPartEnd + 1).readProperties();

        int hostEnd = hostEnd(hostPart);
        String host = hostPart.substring(0, hostEnd).trim();
        String afterHost = hostEnd < hostPart.length() ? hostPart.substring(hostEnd + 1) : null;
        if (afterHost == null) {
            return new Result(emptyToNull(host), null, null, properties);
        }
        if (hostPart.charAt(hostEnd) == PORT_SEPARATOR) {
            return new Result(emptyToNull(host), afterHost.trim(), null, properties);
        }
        int instanceEnd = afterHost.indexOf(PORT_SEPARATOR);
        if (instanceEnd < 0) {
            return new Result(emptyToNull(host), null, afterHost.trim(), properties);
        }
        return new Result(
                emptyToNull(host),
                afterHost.substring(instanceEnd + 1).trim(),
                afterHost.substring(0, instanceEnd).trim(),
                properties);
    }

    // The driver always takes the first character as part of the host, even ':' or '\'.
    private static int hostEnd(String hostPart) {

        for (int i = 1; i < hostPart.length(); i++) {
            char ch = hostPart.charAt(i);
            if (ch == PORT_SEPARATOR || ch == INSTANCE_SEPARATOR) {
                return i;
            }
        }
        return hostPart.length();
    }

    private static String emptyToNull(String text) {

        return text.isEmpty() ? null : text;
    }

    private Map<String, String> readProperties() {

        while (position < url.length()) {
            readPair();
        }
        return properties;
    }

    private void readPair() {

        int nameEnd = indexOfEither('=', PAIR_SEPARATOR);
        String name = url.substring(position, nameEnd).trim();
        if (nameEnd == url.length() || url.charAt(nameEnd) == PAIR_SEPARATOR) {
            if (!name.isEmpty()) {
                throw new JdbcUrlParseException(url, "Property without '=': " + name);
            }
            position = nameEnd + 1;
            return;
        }
        if (name.isEmpty()) {
            throw new JdbcUrlParseException(url, "Property without a name");
        }
        position = nameEnd + 1;
        putLast(name, readValue());
    }

    private String readValue() {

        int valueEnd = indexOfEither(ESCAPE_START, PAIR_SEPARATOR);
        String value = url.substring(position, valueEnd).trim();
        if (valueEnd < url.length() && url.charAt(valueEnd) == ESCAPE_START) {
            if (!value.isEmpty()) {
                throw new JdbcUrlParseException(url, "'{' inside a property value");
            }
            position = valueEnd + 1;
            return readEscapedValue();
        }
        position = valueEnd + 1;
        return value;
    }

    private String readEscapedValue() {

        StringBuilder value = new StringBuilder();
        while (position < url.length()) {
            char ch = url.charAt(position);
            position++;
            if (ch != ESCAPE_END) {
                value.append(ch);
            } else if (position < url.length() && url.charAt(position) == ESCAPE_END) {
                value.append(ESCAPE_END);
                position++;
            } else {
                skipSpacesToPairEnd();
                return value.toString();
            }
        }
        throw new JdbcUrlParseException(url, "Unterminated '{' in property value");
    }

    private void skipSpacesToPairEnd() {

        while (position < url.length() && url.charAt(position) == ' ') {
            position++;
        }
        if (position < url.length() && url.charAt(position) != PAIR_SEPARATOR) {
            throw new JdbcUrlParseException(url, "Text after '}' in property value");
        }
        position++;
    }

    private int indexOfEither(char first, char second) {

        for (int i = position; i < url.length(); i++) {
            char ch = url.charAt(i);
            if (ch == first || ch == second) {
                return i;
            }
        }
        return url.length();
    }

    // The parser's synonym lookups take the last matching key in order, so a repeated key moves to the end.
    private void putLast(String name, String value) {

        properties.remove(name);
        properties.put(name, value);
    }
}
