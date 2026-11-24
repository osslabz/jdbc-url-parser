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
import java.util.regex.Pattern;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcUrlParseException;

/**
 * An Oracle easy connect string as ojdbc's {@code EZConnectResolver} and {@code AddrResolution.resolveSimple} read it:
 * {@code host:port:sid[:mode]} or {@code [protocol://]hostlist[/service[:mode][/instance]]}, each with an optional
 * {@code ?key=value&...} query.
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
        String sid = segments[1].isEmpty() ? null : segments[1];
        return new OracleEasyConnect(List.of(host), null, sid, query);
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
        String[] path = body.substring(slash + 1).split("/", -1);
        if (path.length > 2 || (path.length == 2 && path[1].isEmpty())) {
            throw new JdbcUrlParseException(url, "Oracle service form must be hosts/service[/instance]");
        }
        String instance = path.length == 2 ? path[1] : null;
        String service = serviceName(url, path[0]);
        return new OracleEasyConnect(hosts(url, body.substring(0, slash), instance), service, null, query);
    }

    private static String serviceName(String url, String text) {

        int colon = text.indexOf(':');
        if (colon >= 0) {
            requireServerMode(url, text.substring(colon + 1));
        }
        String service = colon < 0 ? text : text.substring(0, colon);
        if (service.isEmpty()) {
            throw new JdbcUrlParseException(url, "Oracle service name is empty");
        }
        return service;
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
