package net.osslabz.jdbc.parser;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.osslabz.jdbc.DatabaseProduct;
import net.osslabz.jdbc.JdbcProperty;
import net.osslabz.jdbc.JdbcUrlParseException;
import net.osslabz.jdbc.PropertySource;

/**
 * Abstract base class for URL parsers providing common parsing utilities.
 */
public abstract class AbstractUrlParser implements UrlParser {

    private static final int MAX_PORT = 65535;

    private static final Pattern IPV6_LITERAL = Pattern.compile("\\[[0-9A-Fa-f:.]+(%\\p{javaLetterOrDigit}+)?]");

    private static final Pattern REGISTERED_NAME = Pattern.compile("[\\p{javaLetterOrDigit}._-]+");

    protected final Set<DatabaseProduct> supportedTypes;

    protected AbstractUrlParser(DatabaseProduct... supportedTypes) {

        this.supportedTypes = Set.of(supportedTypes);
    }

    @Override
    public boolean supports(DatabaseProduct databaseProduct) {

        return supportedTypes.contains(databaseProduct);
    }

    /**
     * Validates that the URL starts with "jdbc:".
     *
     * @param url the URL to validate
     * @throws JdbcUrlParseException if validation fails
     */
    protected void validateJdbcPrefix(String url) {

        if (url == null || url.isBlank()) {
            throw new JdbcUrlParseException(url, "JDBC URL cannot be null or blank");
        }
        if (!url.toLowerCase(Locale.ROOT).startsWith("jdbc:")) {
            throw new JdbcUrlParseException(url, "JDBC URL must start with 'jdbc:'");
        }
    }

    /**
     * Extracts the protocol from a JDBC URL (e.g., "jdbc:mysql").
     *
     * @param url          the JDBC URL
     * @param databaseProduct the database type
     * @return the protocol string
     */
    protected String extractProtocol(String url, DatabaseProduct databaseProduct) {

        String prefix = databaseProduct.getUrlPrefix();
        if (url.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
            return prefix;
        }
        throw new JdbcUrlParseException(url, "URL does not match expected prefix: " + prefix);
    }

    /**
     * Appends a sub-protocol to a protocol such as {@code jdbc:mysql:}.
     *
     * @param protocol    the protocol, ending in ':'
     * @param subProtocol the sub-protocol without colons, empty when the URL has none
     * @return the protocol, followed by the sub-protocol and ':' when there is one
     */
    protected static String withSubProtocol(String protocol, String subProtocol) {

        return subProtocol.isEmpty() ? protocol : protocol + subProtocol + ":";
    }

    /**
     * Adds a derived property to the properties map.
     *
     * @param properties the properties map
     * @param key        the property key
     * @param value      the property value
     */
    protected void addDerivedProperty(Map<String, JdbcProperty> properties, String key, String value) {

        properties.put(key, new JdbcProperty(PropertySource.DERIVED, value));
    }

    /**
     * Adds a derived {@code MODE} unless the URL wrote a {@code MODE} key in exactly that case, for drivers that keep
     * keys as written.
     *
     * @param properties the properties map
     * @param mode       the mode the URL selects
     */
    protected static void addDerivedModeUnlessWritten(Map<String, JdbcProperty> properties, String mode) {

        properties.putIfAbsent("MODE", new JdbcProperty(PropertySource.DERIVED, mode));
    }

    /**
     * Adds a descriptor property to the properties map.
     *
     * @param properties the properties map
     * @param key        the property key
     * @param value      the property value
     */
    protected void addDescriptorProperty(Map<String, JdbcProperty> properties, String key, String value) {

        properties.put(key, new JdbcProperty(PropertySource.DESCRIPTOR, value));
    }

    /**
     * Parses a port the way the drivers do: {@code Integer.parseInt} (a leading '+' is accepted) within 0..65535.
     *
     * @param url      the URL being parsed, for the error message
     * @param portText the text after the colon
     * @return the port
     * @throws JdbcUrlParseException if the text is not a number or out of range
     */
    protected static int parsePort(String url, String portText) {

        try {
            return requirePort(url, Integer.parseInt(portText));
        } catch (NumberFormatException e) {
            throw new JdbcUrlParseException(url, "Invalid port", e);
        }
    }

    /**
     * Checks that an already decoded port lies within 0..65535.
     *
     * @param url  the URL being parsed, for the error message
     * @param port the decoded port
     * @return the port
     * @throws JdbcUrlParseException if the port is out of range
     */
    protected static int requirePort(String url, int port) {

        if (port < 0 || port > MAX_PORT) {
            throw new JdbcUrlParseException(url, "Invalid port");
        }
        return port;
    }

    /**
     * Checks that a hostname can resolve: a bracketed IPv6 literal with an optional zone, or a non-empty run of letters,
     * digits, '.', '-' and '_'. Anything else, such as credentials written into the host part, is rejected.
     *
     * @param url      the URL being parsed, for the error message
     * @param hostname the final hostname the driver would connect to
     * @return the hostname unchanged
     * @throws JdbcUrlParseException if the hostname is neither form
     */
    protected static String requireHostname(String url, String hostname) {

        if (hostname == null
                || !(IPV6_LITERAL.matcher(hostname).matches()
                        || REGISTERED_NAME.matcher(hostname).matches())) {
            throw new JdbcUrlParseException(url, "Invalid hostname");
        }
        return hostname;
    }

    /**
     * Checks that the URL starts with the prefix in exactly the case the driver expects.
     *
     * @param url    the URL being parsed
     * @param prefix the driver's case-sensitive prefix
     * @throws JdbcUrlParseException if the URL does not start with the prefix
     */
    protected static void requireExactPrefix(String url, String prefix) {

        if (!url.startsWith(prefix)) {
            throw new JdbcUrlParseException(url, "URL must start with '" + prefix + "'");
        }
    }

    /**
     * Splits a property section into pairs on the separator only. Empty tokens are skipped; each token splits at its
     * first '='. Nothing is trimmed or decoded.
     *
     * @param text      the property section without its leading delimiter
     * @param separator the character between pairs
     * @return the pairs in written order
     */
    static List<RawPair> splitPairs(String text, char separator) {

        List<RawPair> pairs = new ArrayList<>();
        int start = 0;
        while (start <= text.length()) {
            int end = text.indexOf(separator, start);
            if (end < 0) {
                end = text.length();
            }
            if (end > start) {
                pairs.add(toPair(text.substring(start, end)));
            }
            start = end + 1;
        }
        return pairs;
    }

    /**
     * Turns pairs into path properties with key and value trimmed and keys kept as written; a later key replaces an
     * earlier one. Pairs without '=' are left out.
     *
     * @param pairs the pairs in written order
     * @return mutable map of the properties in written order
     */
    static Map<String, JdbcProperty> toTrimmedPathProperties(List<RawPair> pairs) {

        Map<String, JdbcProperty> properties = new LinkedHashMap<>();
        for (RawPair pair : pairs) {
            if (pair.value() != null) {
                properties.put(
                        pair.key().trim(),
                        new JdbcProperty(PropertySource.PATH, pair.value().trim()));
            }
        }
        return properties;
    }

    private static RawPair toPair(String token) {

        int equalsIndex = token.indexOf('=');
        if (equalsIndex < 0) {
            return new RawPair(token, null);
        }
        return new RawPair(token.substring(0, equalsIndex), token.substring(equalsIndex + 1));
    }

    /**
     * Decodes {@code application/x-www-form-urlencoded} text as UTF-8: '%XX' sequences and '+' as a space.
     *
     * @param url  the URL being parsed, for the error message
     * @param text the encoded text
     * @return the decoded text
     * @throws JdbcUrlParseException if the text holds a malformed '%' sequence
     */
    protected static String percentDecode(String url, String text) {

        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new JdbcUrlParseException(url, "Invalid percent-encoding", e);
        }
    }
}
