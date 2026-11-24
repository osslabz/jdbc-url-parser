package net.osslabz.jdbc.parser;

import static net.osslabz.jdbc.parser.AbstractUrlParser.parsePort;
import static net.osslabz.jdbc.parser.AbstractUrlParser.percentDecode;
import static net.osslabz.jdbc.parser.AbstractUrlParser.requirePort;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.osslabz.jdbc.JdbcUrlParseException;

/**
 * One host as Connector/J's {@code ConnectionUrlParser} reads it from an authority segment.
 *
 * @param host       the written hostname, decoded; null when the segment leaves it out
 * @param port       the written port; null when the segment leaves it out
 * @param user       the userinfo user, decoded; null when the segment has no userinfo
 * @param password   the userinfo password, decoded; null when not written, "" when written empty
 * @param attributes the host attributes under their written keys with decoded values, "" for a key without '='
 */
record MySQLHostSpec(String host, Integer port, String user, String password, Map<String, String> attributes) {

    private static final String URI_SCHEME = "cj://";

    // Integer.parseInt reads these as -1, which the driver takes as "no port written".
    private static final Pattern NO_PORT = Pattern.compile("-0*1");

    MySQLHostSpec {
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /**
     * Reads one authority segment: optional userinfo, then the host part.
     *
     * @param url     the URL being parsed, for error messages
     * @param segment one trimmed, comma-separated authority segment
     * @return the hosts the segment names, in written order
     * @throws JdbcUrlParseException if the driver rejects the segment or it hides a second userinfo
     */
    static List<MySQLHostSpec> parse(String url, String segment) {

        int userinfoEnd = userinfoEnd(url, segment);
        Credentials credentials = userinfoEnd < 0
                ? Credentials.NONE
                : credentials(url, segment.substring(0, userinfoEnd).trim());
        String hostInfo = segment.substring(userinfoEnd + 1).trim();
        if (hostInfo.isEmpty()) {
            return List.of(credentials.spec(null, null));
        }
        // The driver hands the URI reader the whole segment and drops its answer when it holds userinfo.
        MySQLHostSpec uriSpec = uriSpec(url, segment, credentials);
        if (uriSpec != null) {
            return List.of(uriSpec);
        }
        return List.of(genericSpec(url, hostInfo, credentials));
    }

    /**
     * Reads a port the way the driver does: empty or -1 means none written, anything else must be a valid port.
     *
     * @param url      the URL being parsed, for error messages
     * @param portText the decoded port text, may be null
     * @return the port, or null when none is written
     */
    static Integer writtenPort(String url, String portText) {

        if (portText == null || portText.isEmpty() || NO_PORT.matcher(portText).matches()) {
            return null;
        }
        return parsePort(url, portText);
    }

    // The driver ends the userinfo at the first '@' anywhere; one inside a closed (...) belongs to an attribute value.
    private static int userinfoEnd(String url, String segment) {

        int i = 0;
        while (i < segment.length()) {
            if (segment.charAt(i) == '@') {
                if (segment.indexOf('@') != i) {
                    throw new JdbcUrlParseException(url, "'@' inside a host attribute ahead of the userinfo end");
                }
                return i;
            }
            int closing = segment.charAt(i) == '(' ? closingParen(segment, i) : i;
            i = closing < segment.length() ? closing + 1 : i + 1;
        }
        return -1;
    }

    // Nested parentheses count; an unclosed one runs to the end.
    private static int closingParen(String text, int start) {

        int depth = 0;
        int i = start + 1;
        while (i < text.length() && (text.charAt(i) != ')' || depth > 0)) {
            if (text.charAt(i) == '(') {
                depth++;
            } else if (text.charAt(i) == ')') {
                depth--;
            }
            i++;
        }
        return i;
    }

    private static Credentials credentials(String url, String userinfo) {

        if (userinfo.isEmpty()) {
            return Credentials.NONE;
        }
        int separator = userinfo.indexOf(':');
        if (separator < 0) {
            return new Credentials(percentDecode(url, userinfo), null);
        }
        return new Credentials(
                percentDecode(url, userinfo.substring(0, separator).trim()),
                percentDecode(url, userinfo.substring(separator + 1).trim()));
    }

    // Any failure, the host's decoding included, sends the driver on to its next reader.
    private static MySQLHostSpec uriSpec(String url, String text, Credentials credentials) {

        URI uri;
        String host;
        try {
            uri = URI.create(URI_SCHEME + text);
            host = uri.getHost() == null ? null : URLDecoder.decode(uri.getHost(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (uri.getUserInfo() != null || (host == null && uri.getPort() < 0)) {
            return null;
        }
        return credentials.spec(host, uri.getPort() < 0 ? null : requirePort(url, uri.getPort()));
    }

    // The driver's GENERIC_HOST_PTRN: the port is whatever follows the last ':'.
    private static MySQLHostSpec genericSpec(String url, String hostInfo, Credentials credentials) {

        if (hostInfo.indexOf('@') >= 0) {
            throw new JdbcUrlParseException(url, "More than one userinfo in an authority segment");
        }
        int portSeparator = hostInfo.lastIndexOf(':');
        String host = portSeparator < 0 ? hostInfo : hostInfo.substring(0, portSeparator);
        Integer port = portSeparator < 0
                ? null
                : writtenPort(
                        url,
                        percentDecode(url, hostInfo.substring(portSeparator + 1).trim()));
        String decodedHost = percentDecode(url, host.trim());
        return credentials.spec(decodedHost.isEmpty() ? null : decodedHost, port);
    }

    private record Credentials(String user, String password) {

        static final Credentials NONE = new Credentials(null, null);

        MySQLHostSpec spec(String host, Integer port) {

            return new MySQLHostSpec(host, port, user, password, Map.of());
        }
    }
}
