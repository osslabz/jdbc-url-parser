package net.osslabz.jdbc.parser;

import java.util.List;

/**
 * One NV pair of an Oracle connect descriptor: a keyword with either a value or a list of entries.
 *
 * @param value the value with escapes resolved; the keyword itself for a pair written without '='; null for a list
 *     or an empty value
 */
record OracleNvPair(String keyword, String value, List<OracleNvPair> children) {

    boolean is(String expected) {

        return keyword.equalsIgnoreCase(expected);
    }

    boolean isAny(List<String> expected) {

        return expected.stream().anyMatch(this::is);
    }

    OracleNvPair first(String expected) {

        return children.stream().filter(child -> child.is(expected)).findFirst().orElse(null);
    }
}
