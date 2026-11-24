package net.osslabz.jdbc.parser;

import static net.osslabz.jdbc.parser.AbstractUrlParser.parsePort;
import static net.osslabz.jdbc.parser.AbstractUrlParser.requireHostname;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.osslabz.jdbc.Host;
import net.osslabz.jdbc.JdbcUrlParseException;

/**
 * An Oracle connect descriptor as ojdbc's NV parser ({@code NVTokens}, {@code NVFactory}) and its address resolution
 * ({@code ServiceAlias}, {@code Description}, {@code AddressList}, {@code Address}) read it.
 *
 * <p>The root is a {@code DESCRIPTION}, a {@code DESCRIPTION_LIST} or a single {@code ADDRESS}. Keywords match
 * case-insensitively; values keep their inner whitespace and quotes, with backslash escapes resolved.
 *
 * @param hosts       one host per {@code ADDRESS} in written order, each carrying its {@code DESCRIPTION}'s
 *                    {@code INSTANCE_NAME}
 * @param serviceName the first {@code DESCRIPTION}'s {@code SERVICE_NAME}; null when absent
 * @param sid         the first {@code DESCRIPTION}'s {@code SID}; null when absent
 * @param end         the index in the parsed text just past the root's closing ')'
 */
record OracleDescriptor(List<Host> hosts, String serviceName, String sid, int end) {

    private static final String DEFAULT_HOST = "localhost";

    private static final String DESCRIPTION = "DESCRIPTION";

    private static final String ADDRESS = "ADDRESS";

    private static final String ADDRESS_LIST = "ADDRESS_LIST";

    private static final String SERVICE_NAME = "SERVICE_NAME";

    private static final String SID = "SID";

    private static final String INSTANCE_NAME = "INSTANCE_NAME";

    private static final List<String> CONNECT_DATA_KEYS = List.of(SERVICE_NAME, SID, INSTANCE_NAME);

    // The only keywords besides their entries that ojdbc allows inside ADDRESS_LIST and DESCRIPTION_LIST.
    private static final List<String> LIST_SWITCHES = List.of("SOURCE_ROUTE", "LOAD_BALANCE", "FAILOVER");

    OracleDescriptor {
        hosts = List.copyOf(hosts);
    }

    /**
     * Reads the descriptor that starts at the first non-whitespace character of the text. After the root's ')' only
     * whitespace may follow, or a surplus ')' after which ojdbc stops reading.
     *
     * @param url  the URL being parsed, for error messages
     * @param text the connect string after the userinfo '@', without its query
     * @return the hosts and the first description's service name and SID
     * @throws JdbcUrlParseException if the text is malformed, nests deeper than 64 levels, or the driver rejects it
     */
    static OracleDescriptor parse(String url, String text) {

        OracleNvReader reader = new OracleNvReader(url, text);
        OracleNvPair root = reader.readRoot();
        int end = reader.position();
        reader.requireEndOfDescriptor();
        return resolve(url, root, end);
    }

    private static OracleDescriptor resolve(String url, OracleNvPair root, int end) {

        if (root.is(ADDRESS)) {
            return new OracleDescriptor(List.of(host(url, root, null)), null, null, end);
        }
        List<Description> descriptions = descriptions(url, root);
        List<Host> hosts = new ArrayList<>();
        for (Description description : descriptions) {
            for (OracleNvPair address : description.addresses()) {
                hosts.add(host(url, address, description.connectData().get(INSTANCE_NAME)));
            }
        }
        Map<String, String> connectData = descriptions.get(0).connectData();
        return new OracleDescriptor(hosts, connectData.get(SERVICE_NAME), connectData.get(SID), end);
    }

    private static List<Description> descriptions(String url, OracleNvPair root) {

        if (root.is(DESCRIPTION)) {
            return List.of(description(url, root));
        }
        if (!root.is("DESCRIPTION_LIST")) {
            throw new JdbcUrlParseException(
                    url, "Oracle descriptor must be a DESCRIPTION, DESCRIPTION_LIST or ADDRESS");
        }
        List<Description> descriptions = new ArrayList<>();
        for (OracleNvPair child : root.children()) {
            if (child.is(DESCRIPTION)) {
                descriptions.add(description(url, child));
            } else if (!child.isAny(LIST_SWITCHES)) {
                throw new JdbcUrlParseException(url, "Unexpected entry in Oracle DESCRIPTION_LIST");
            }
        }
        if (descriptions.isEmpty()) {
            throw new JdbcUrlParseException(url, "Oracle DESCRIPTION_LIST holds no DESCRIPTION");
        }
        return descriptions;
    }

    private static Description description(String url, OracleNvPair description) {

        requireEntries(url, description);
        List<OracleNvPair> addresses = new ArrayList<>();
        Map<String, String> connectData = new HashMap<>();
        for (OracleNvPair child : description.children()) {
            if (child.is(ADDRESS)) {
                addresses.add(child);
            } else if (child.is(ADDRESS_LIST)) {
                addresses.addAll(addressList(url, child));
            } else if (child.is("CONNECT_DATA")) {
                readConnectData(url, child, connectData);
            } else if (child.is("SECURITY")) {
                requireEntries(url, child);
            }
        }
        if (addresses.isEmpty()) {
            throw new JdbcUrlParseException(url, "Oracle DESCRIPTION holds no ADDRESS");
        }
        return new Description(addresses, connectData);
    }

    // Every CONNECT_DATA of a description feeds the same settings; the last one written wins, even when empty.
    private static void readConnectData(String url, OracleNvPair connectData, Map<String, String> settings) {

        requireEntries(url, connectData);
        for (OracleNvPair entry : connectData.children()) {
            for (String key : CONNECT_DATA_KEYS) {
                if (entry.is(key)) {
                    settings.put(key, entry.value());
                }
            }
        }
    }

    private static List<OracleNvPair> addressList(String url, OracleNvPair addressList) {

        requireEntries(url, addressList);
        List<OracleNvPair> addresses = new ArrayList<>();
        for (OracleNvPair child : addressList.children()) {
            if (child.is(ADDRESS)) {
                addresses.add(child);
            } else if (child.is(ADDRESS_LIST)) {
                addresses.addAll(addressList(url, child));
            } else if (!child.isAny(LIST_SWITCHES)) {
                throw new JdbcUrlParseException(url, "Unexpected entry in Oracle ADDRESS_LIST");
            }
        }
        if (addresses.isEmpty()) {
            throw new JdbcUrlParseException(url, "Oracle ADDRESS_LIST holds no ADDRESS");
        }
        return addresses;
    }

    private static Host host(String url, OracleNvPair address, String instance) {

        OracleNvPair protocol = address.first("PROTOCOL");
        if (protocol == null || protocol.value() == null) {
            throw new JdbcUrlParseException(url, "Oracle ADDRESS needs a PROTOCOL");
        }
        OracleNvPair host = address.first("HOST");
        String hostname = host == null || host.value() == null ? DEFAULT_HOST : host.value();
        OracleNvPair port = address.first("PORT");
        int portNumber =
                port == null || port.value() == null ? OracleParser.DEFAULT_PORT : parsePort(url, port.value());
        return new Host(requireHostname(url, hostname), portNumber, instance);
    }

    private static void requireEntries(String url, OracleNvPair pair) {

        if (pair.children().isEmpty()) {
            throw new JdbcUrlParseException(url, "Oracle " + pair.keyword() + " needs entries");
        }
    }

    private record Description(List<OracleNvPair> addresses, Map<String, String> connectData) {}
}
