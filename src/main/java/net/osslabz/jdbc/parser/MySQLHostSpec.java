package net.osslabz.jdbc.parser;

import static net.osslabz.jdbc.parser.AbstractUrlParser.parsePort;
import static net.osslabz.jdbc.parser.AbstractUrlParser.percentDecode;
import static net.osslabz.jdbc.parser.AbstractUrlParser.requirePort;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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

    private static final String ADDRESS_PREFIX = "ADDRESS=";

    private static final String OPENING_MARKERS = "[(";

    private static final String CLOSING_MARKERS = "])";

    // Integer.parseInt reads these as -1, which the driver takes as "no port written".
    private static final Pattern NO_PORT = Pattern.compile("-0*1");

    private static final Pattern HOST_LIST = Pattern.compile("^\\[(?<hosts>.*)\\]$");

    // A single list element of these characters is an IPv6 literal written without its brackets.
    private static final Pattern BARE_IPV6 = Pattern.compile("(?i)^[\\dabcdef:]+$");

    private static final Pattern KEY_VALUE_ATTRIBUTE =
            Pattern.compile("[,\\s]*(?<key>[\\w\\.\\-\\s%]*)(?:=(?<value>[^,]*))?");

    private static final Pattern ADDRESS_ATTRIBUTE =
            Pattern.compile("\\s*\\(\\s*(?<key>[\\w\\.\\-%]+)?\\s*(?:=(?<value>[^)]*))?\\)\\s*");

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
        Matcher hostList = HOST_LIST.matcher(hostInfo);
        if (hostList.matches()) {
            return listedSpecs(url, split(hostList.group("hosts")), credentials);
        }
        return List.of(attributeOrGenericSpec(url, hostInfo, credentials));
    }

    /**
     * Splits on ',' outside [...] and (...) blocks, trims each part and keeps empty ones, as the driver splits an
     * authority and a host list.
     *
     * @param text the authority or the inside of a host list
     * @return the parts in written order
     */
    static List<String> split(String text) {

        List<String> parts = new ArrayList<>();
        int partStart = 0;
        for (int i = 0; i < text.length(); i = blockEnd(text, i) + 1) {
            if (text.charAt(i) == ',') {
                parts.add(text.substring(partStart, i).trim());
                partStart = i + 1;
            }
        }
        parts.add(text.substring(partStart).trim());
        return parts;
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
            int closing = segment.charAt(i) == '(' ? blockEnd(segment, i) : i;
            i = closing < segment.length() ? closing + 1 : i + 1;
        }
        return -1;
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

    // A list element that fails every reader makes the driver retry the whole list as one generic host, which throws.
    private static List<MySQLHostSpec> listedSpecs(String url, List<String> elements, Credentials credentials) {

        boolean bareIpv6 =
                elements.size() == 1 && BARE_IPV6.matcher(elements.get(0)).matches();
        List<MySQLHostSpec> specs = new ArrayList<>();
        for (String element : elements) {
            MySQLHostSpec spec = element.isEmpty() ? credentials.spec(null, null) : uriSpec(url, element, credentials);
            if (spec == null && bareIpv6) {
                spec = uriSpec(url, "[" + element + "]", credentials);
            }
            specs.add(spec != null ? spec : attributeOrGenericSpec(url, element, credentials));
        }
        return specs;
    }

    // The key-value reader runs before address= because a key named "address" would otherwise mislead it.
    private static MySQLHostSpec attributeOrGenericSpec(String url, String hostInfo, Credentials credentials) {

        if (hostInfo.startsWith("(") && hostInfo.endsWith(")")) {
            String attributes = hostInfo.substring(1, hostInfo.length() - 1);
            return credentials.spec(attributes(url, KEY_VALUE_ATTRIBUTE, attributes));
        }
        if (hostInfo.regionMatches(true, 0, ADDRESS_PREFIX, 0, ADDRESS_PREFIX.length())) {
            String attributes = hostInfo.substring(ADDRESS_PREFIX.length()).trim();
            return credentials.spec(attributes(url, ADDRESS_ATTRIBUTE, attributes));
        }
        return genericSpec(url, hostInfo, credentials);
    }

    // The driver's processKeyValuePattern: successive matches must cover the text without a gap.
    private static Map<String, String> attributes(String url, Pattern pattern, String text) {

        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = pattern.matcher(text);
        int covered = 0;
        while (matcher.find()) {
            if (matcher.start() != covered) {
                throw unreadableAttributes(url);
            }
            putAttribute(url, attributes, matcher.group("key"), matcher.group("value"));
            covered = matcher.end();
        }
        if (covered != text.length()) {
            throw unreadableAttributes(url);
        }
        return attributes;
    }

    private static void putAttribute(String url, Map<String, String> attributes, String rawKey, String rawValue) {

        String key = rawKey == null ? "" : percentDecode(url, rawKey.trim());
        String value = rawValue == null ? "" : percentDecode(url, rawValue.trim());
        if (!key.isEmpty()) {
            attributes.put(key, value);
        } else if (!value.isEmpty()) {
            throw unreadableAttributes(url);
        }
    }

    private static JdbcUrlParseException unreadableAttributes(String url) {

        return new JdbcUrlParseException(url, "Unreadable host attributes");
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

    // An unclosed block runs to the end; only markers of the block's own kind nest.
    private static int blockEnd(String text, int start) {

        int kind = OPENING_MARKERS.indexOf(text.charAt(start));
        if (kind < 0) {
            return start;
        }
        char opening = OPENING_MARKERS.charAt(kind);
        char closing = CLOSING_MARKERS.charAt(kind);
        int depth = 0;
        int i = start + 1;
        while (i < text.length() && (text.charAt(i) != closing || depth > 0)) {
            if (text.charAt(i) == opening) {
                depth++;
            } else if (text.charAt(i) == closing) {
                depth--;
            }
            i++;
        }
        return i;
    }

    private record Credentials(String user, String password) {

        static final Credentials NONE = new Credentials(null, null);

        MySQLHostSpec spec(String host, Integer port) {

            return new MySQLHostSpec(host, port, user, password, Map.of());
        }

        MySQLHostSpec spec(Map<String, String> attributes) {

            return new MySQLHostSpec(null, null, user, password, attributes);
        }
    }
}
