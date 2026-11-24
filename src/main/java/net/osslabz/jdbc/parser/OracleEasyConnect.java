package net.osslabz.jdbc.parser;

import static net.osslabz.jdbc.parser.AbstractUrlParser.parsePort;
import static net.osslabz.jdbc.parser.AbstractUrlParser.requireHostname;
import static net.osslabz.jdbc.parser.AbstractUrlParser.splitPairs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcUrlParseException;

/**
 * An Oracle easy connect string as ojdbc's {@code EZConnectResolver} and {@code AddrResolution.resolveSimple} read it:
 * {@code host:port:sid[:mode]} or {@code [protocol://]hostlist[/service[:mode][/instance]]}, each with an optional
 * {@code ?key=value&...} query. A path outside that grammar names the service as written, as ojdbc's hostname adapter
 * reads it.
 *
 * @param hosts   the hosts in written order, each carrying the instance when one is written
 * @param service the service name; null for the SID form or when none is written
 * @param sid     the SID; null for the service form or when written empty
 * @param query   the query options under their written keys, values as written
 */
record OracleEasyConnect(List<Host> hosts, String service, String sid, Map<String, String> query) {

    private static final String DEFAULT_HOST = "localhost";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern PROTOCOL_PREFIX = Pattern.compile("^(?i)(?:tcps?:)?(?://)?");

    // OracleDriver hands URLs of this shape to the hostname adapter without EZConnectResolver.
    private static final Pattern SIMPLE_URL = Pattern.compile(
            "jdbc:oracle:(thin|oci|oci8|kprb|sharding):\\w*/?\\w*@(//)?[A-z0-9-._]+(:\\d+)[:/][A-z0-9-._:]+");

    // EZConnectResolver's grammar for the text after the host list; A-z also spans [\]^_`.
    private static final Pattern EASY_CONNECT_PATH = Pattern.compile(
            "(?<service>\\w[\\w#$,\\-.]+)(?::(?:dedicated|shared|pooled))?(?:/(?<instance>[A-z][A-z0-9]+))?",
            Pattern.CASE_INSENSITIVE);

    // The driver upper-cases the written mode and looks it up in this list.
    private static final Set<String> SERVER_MODES = Set.of("DEDICATED", "SHARED", "POOLED");

    OracleEasyConnect {
        hosts = List.copyOf(hosts);
        query = Collections.unmodifiableMap(new LinkedHashMap<>(query));
    }

    /**
     * Reads an easy connect string, with all whitespace removed first as the driver does.
     *
     * @param url     the URL being parsed, for error messages
     * @param connect the text after the userinfo '@'
     * @return the hosts, service or SID, and query the driver connects with
     * @throws JdbcUrlParseException if the driver rejects the string or it cannot name a reachable host
     */
    static OracleEasyConnect parse(String url, String connect) {

        String text = WHITESPACE.matcher(connect).replaceAll("");
        if (text.isEmpty()) {
            throw new JdbcUrlParseException(url, "Oracle connect identifier is empty");
        }
        int queryStart = text.indexOf('?');
        String address = queryStart < 0 ? text : text.substring(0, queryStart);
        Map<String, String> query = queryStart < 0 ? Map.of() : parseQuery(url, text.substring(queryStart + 1));
        if (isSidForm(url, address)) {
            return sidForm(url, address, query);
        }
        return serviceForm(url, address, query);
    }

    /**
     * Reads an easy connect query: '&amp;'-separated {@code key=value} pairs, keys and values as written apart from
     * whitespace, which the driver drops; last wins.
     *
     * @param url   the URL being parsed, for error messages
     * @param query the text after '?'
     * @return the options in written order
     * @throws JdbcUrlParseException if a pair is empty (other than after a trailing '&amp;'), lacks '=' or holds a
     *     second '='
     */
    static Map<String, String> parseQuery(String url, String query) {

        String options = WHITESPACE.matcher(query).replaceAll("");
        if (options.startsWith("&") || options.contains("&&")) {
            throw new JdbcUrlParseException(url, "Empty Oracle query option");
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (RawPair pair : splitPairs(options, '&')) {
            if (pair.value() == null || pair.value().indexOf('=') >= 0) {
                throw new JdbcUrlParseException(url, "Oracle query options must be key=value");
            }
            parsed.put(pair.key(), pair.value());
        }
        return parsed;
    }

    private static boolean isSidForm(String url, String address) {

        if (address.indexOf('/') >= 0) {
            return false;
        }
        int portSeparator = portSeparator(url, address);
        return portSeparator >= 0 && address.indexOf(':', portSeparator + 1) >= 0;
    }

    private static OracleEasyConnect sidForm(String url, String address, Map<String, String> query) {

        int portSeparator = portSeparator(url, address);
        String[] segments = address.substring(portSeparator + 1).split(":", -1);
        if (segments.length > 3) {
            throw new JdbcUrlParseException(url, "Oracle SID form must be host:port:sid");
        }
        if (segments.length == 3) {
            requireServerMode(url, segments[2]);
        }
        Host host = host(url, address.substring(0, portSeparator), parsePort(url, segments[0]), null);
        return new OracleEasyConnect(List.of(host), null, sid(url, segments[1]), query);
    }

    private static String sid(String url, String text) {

        if (text.indexOf('(') >= 0 || text.indexOf(')') >= 0) {
            throw new JdbcUrlParseException(url, "Oracle SID must not contain parentheses");
        }
        return text.isEmpty() ? null : text;
    }

    private static OracleEasyConnect serviceForm(String url, String address, Map<String, String> query) {

        String body = PROTOCOL_PREFIX.matcher(address).replaceFirst("");
        int slash = body.indexOf('/');
        if (slash < 0) {
            List<Host> hosts = hosts(url, body, null);
            if (hosts.size() > 1) {
                throw new JdbcUrlParseException(url, "Oracle host list needs a service name");
            }
            return new OracleEasyConnect(hosts, null, null, query);
        }
        Matcher path = EASY_CONNECT_PATH.matcher(body.substring(slash + 1));
        if (SIMPLE_URL.matcher(url).matches() || !path.matches()) {
            return hostnameForm(url, address, query);
        }
        List<Host> hosts = hosts(url, body.substring(0, slash), path.group("instance"));
        return new OracleEasyConnect(hosts, path.group("service"), null, query);
    }

    /**
     * Reads the service form as ojdbc's hostname adapter does: one host with an optional port, then the whole path as
     * the service, less a trailing server mode. The protocol prefix is not stripped, so it reads as the host.
     */
    private static OracleEasyConnect hostnameForm(String url, String address, Map<String, String> query) {

        String text = address.startsWith("//") ? address.substring(2) : address;
        int slash = text.indexOf('/');
        String hostAndPort = text.substring(0, slash);
        String path = text.substring(slash + 1);
        int portSeparator = portSeparator(url, hostAndPort);
        // Without a port the adapter reads the first ':' as the port separator, and a trailing '/' as an empty name.
        if (path.endsWith("/") || (portSeparator < 0 && path.indexOf(':') >= 0)) {
            throw new JdbcUrlParseException(url, "Malformed Oracle service name");
        }
        int port = portSeparator < 0
                ? OracleParser.DEFAULT_PORT
                : parsePort(url, hostAndPort.substring(portSeparator + 1));
        String hostname = portSeparator < 0 ? hostAndPort : hostAndPort.substring(0, portSeparator);
        String service = withoutServerMode(path);
        if (service.isEmpty()) {
            throw new JdbcUrlParseException(url, "Oracle service name is empty");
        }
        return new OracleEasyConnect(List.of(host(url, hostname, port, null)), service, null, query);
    }

    private static String withoutServerMode(String path) {

        int colon = path.lastIndexOf(':');
        boolean endsInMode =
                colon >= 0 && SERVER_MODES.contains(path.substring(colon + 1).toUpperCase(Locale.ROOT));
        return endsInMode ? path.substring(0, colon) : path;
    }

    private static void requireServerMode(String url, String mode) {

        if (!SERVER_MODES.contains(mode.toUpperCase(Locale.ROOT))) {
            throw new JdbcUrlParseException(url, "Unknown Oracle server mode: " + mode);
        }
    }

    /**
     * A portless host takes the port written next after it in the list, else 1521; a trailing ',' is ignored.
     */
    private static List<Host> hosts(String url, String hostList, String instance) {

        String[] entries = hostList.split(",", -1);
        int count = entries.length > 1 && entries[entries.length - 1].isEmpty() ? entries.length - 1 : entries.length;
        List<Host> hosts = new ArrayList<>();
        int nextPort = OracleParser.DEFAULT_PORT;
        for (int i = count - 1; i >= 0; i--) {
            String entry = entries[i];
            if (entry.isEmpty() && entries.length > 1) {
                throw new JdbcUrlParseException(url, "Empty entry in Oracle host list");
            }
            int portSeparator = portSeparator(url, entry);
            if (portSeparator >= 0) {
                nextPort = parsePort(url, entry.substring(portSeparator + 1));
            }
            String hostname = portSeparator < 0 ? entry : entry.substring(0, portSeparator);
            hosts.add(0, host(url, hostname, nextPort, instance));
        }
        return hosts;
    }

    private static Host host(String url, String hostname, int port, String instance) {

        return new Host(requireHostname(url, hostname.isEmpty() ? DEFAULT_HOST : hostname), port, instance);
    }

    /**
     * Finds the ':' after the host, looking past a bracketed IPv6 literal.
     *
     * @return the index of that ':', or -1 when no port is written
     */
    private static int portSeparator(String url, String hostAndRest) {

        if (!hostAndRest.startsWith("[")) {
            return hostAndRest.indexOf(':');
        }
        int afterLiteral = hostAndRest.indexOf(']') + 1;
        if (afterLiteral == 0) {
            throw new JdbcUrlParseException(url, "Unclosed IPv6 literal");
        }
        if (afterLiteral == hostAndRest.length()) {
            return -1;
        }
        if (hostAndRest.charAt(afterLiteral) != ':') {
            throw new JdbcUrlParseException(url, "Unexpected text after IPv6 literal");
        }
        return afterLiteral;
    }
}
