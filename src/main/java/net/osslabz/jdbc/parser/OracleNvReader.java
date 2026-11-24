package net.osslabz.jdbc.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.osslabz.jdbc.JdbcUrlParseException;

/**
 * Reads an Oracle connect descriptor into {@link OracleNvPair}s the way ojdbc's {@code NVTokens} and {@code NVFactory}
 * do: {@code pair := '(' keyword ( ')' | '=' literal ')' | '=' pair* ')' )}. It uses an explicit stack instead of
 * recursion and refuses comma-separated lists.
 */
final class OracleNvReader {

    private static final int MAX_DEPTH = 64;

    private static final String ESCAPABLE = "()=\\\"'#";

    private enum Kind {
        OPEN,
        CLOSE,
        EQUALS,
        COMMA,
        LITERAL,
        END
    }

    private final String url;

    private final String text;

    private final Deque<OpenList> openLists = new ArrayDeque<>();

    private int position;

    private boolean afterEquals;

    private String literal;

    OracleNvReader(String url, String text) {

        this.url = url;
        this.text = text;
    }

    int position() {

        return position;
    }

    OracleNvPair readRoot() {

        expect(next(), Kind.OPEN);
        OracleNvPair root = null;
        while (root == null) {
            OracleNvPair pair = readPairAfterOpen();
            root = pair == null ? null : attach(pair);
        }
        return root;
    }

    // ojdbc reads the text as the entries of one more pair, so a surplus ')' ends its reading there.
    void requireEndOfDescriptor() {

        Kind kind = next();
        if (kind != Kind.END && kind != Kind.CLOSE) {
            throw new JdbcUrlParseException(url, "Unexpected text after the Oracle descriptor");
        }
    }

    /**
     * Reads a pair after its '('.
     *
     * @return the pair, or null when it opens a list, in which case the first entry's '(' has been read
     */
    private OracleNvPair readPairAfterOpen() {

        expect(next(), Kind.LITERAL);
        String keyword = literal;
        Kind kind = next();
        if (kind == Kind.CLOSE) {
            return new OracleNvPair(keyword, keyword, List.of());
        }
        expect(kind, Kind.EQUALS);
        kind = next();
        if (kind == Kind.LITERAL) {
            String value = unescape(literal);
            expect(next(), Kind.CLOSE);
            return new OracleNvPair(keyword, value, List.of());
        }
        if (kind == Kind.CLOSE) {
            return new OracleNvPair(keyword, null, List.of());
        }
        expect(kind, Kind.OPEN);
        openList(keyword);
        return null;
    }

    /**
     * Adds a finished pair to the innermost open list, closing lists until another entry starts.
     *
     * @return the root once it is closed, else null with the next entry's '(' read
     */
    private OracleNvPair attach(OracleNvPair pair) {

        OracleNvPair finished = pair;
        while (!openLists.isEmpty()) {
            OpenList list = openLists.peek();
            list.entries().add(finished);
            Kind kind = next();
            if (kind == Kind.OPEN) {
                return null;
            }
            expect(kind, Kind.CLOSE);
            openLists.pop();
            finished = new OracleNvPair(list.keyword(), null, List.copyOf(list.entries()));
        }
        return finished;
    }

    private void openList(String keyword) {

        if (openLists.size() == MAX_DEPTH) {
            throw new JdbcUrlParseException(url, "Oracle descriptor nests deeper than " + MAX_DEPTH + " levels");
        }
        openLists.push(new OpenList(keyword, new ArrayList<>()));
    }

    private void expect(Kind actual, Kind expected) {

        if (actual != expected) {
            throw new JdbcUrlParseException(url, "Malformed Oracle descriptor");
        }
    }

    private Kind next() {

        while (position < text.length() && isWhitespace(text.charAt(position))) {
            position++;
        }
        if (position == text.length()) {
            return Kind.END;
        }
        Kind kind = switch (text.charAt(position)) {
            case '(' -> Kind.OPEN;
            case ')' -> Kind.CLOSE;
            case '=' -> Kind.EQUALS;
            case ',' -> Kind.COMMA;
            default -> Kind.LITERAL;
        };
        if (kind == Kind.LITERAL) {
            literal = readLiteral();
        } else {
            position++;
            afterEquals = kind == Kind.EQUALS;
        }
        return kind;
    }

    /**
     * A quoted literal runs through its closing quote, quotes kept. An unquoted one runs to '(' or ')', and in
     * keyword position also to '=' or ','. A backslash shields the next character either way.
     */
    private String readLiteral() {

        int start = position;
        char quote = text.charAt(start);
        boolean quoted = quote == '"' || quote == '\'';
        position += quoted ? 1 : 0;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == '\\') {
                position += 2;
            } else if (quoted ? c == quote : endsUnquotedLiteral(c)) {
                position += quoted ? 1 : 0;
                break;
            } else {
                position++;
            }
        }
        position = Math.min(position, text.length());
        return stripTrailingWhitespace(text.substring(start, position));
    }

    private boolean endsUnquotedLiteral(char c) {

        return c == '(' || c == ')' || (!afterEquals && (c == '=' || c == ','));
    }

    private static String stripTrailingWhitespace(String value) {

        int end = value.length();
        while (end > 0 && isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static boolean isWhitespace(char c) {

        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static String unescape(String value) {

        StringBuilder unescaped = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            boolean escape =
                    value.charAt(i) == '\\' && i + 1 < value.length() && ESCAPABLE.indexOf(value.charAt(i + 1)) >= 0;
            i += escape ? 1 : 0;
            unescaped.append(value.charAt(i));
            i++;
        }
        return unescaped.toString();
    }

    private record OpenList(String keyword, List<OracleNvPair> entries) {}
}
