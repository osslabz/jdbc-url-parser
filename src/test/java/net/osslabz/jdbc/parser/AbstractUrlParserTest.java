package net.osslabz.jdbc.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the property-section splitting shared by the parsers.
 */
class AbstractUrlParserTest {

    static Stream<Arguments> splitCases() {

        return Stream.of(
                Arguments.of("", List.of()),
                Arguments.of("&", List.of()),
                Arguments.of("&&", List.of()),
                Arguments.of("=x", List.of(new RawPair("", "x"))),
                Arguments.of("a=", List.of(new RawPair("a", ""))),
                Arguments.of("flag", List.of(new RawPair("flag", null))),
                Arguments.of("a=1&", List.of(new RawPair("a", "1"))),
                Arguments.of("a=b=c", List.of(new RawPair("a", "b=c"))),
                Arguments.of(" a = b ", List.of(new RawPair(" a ", " b "))),
                Arguments.of("a=%20&b", List.of(new RawPair("a", "%20"), new RawPair("b", null))),
                Arguments.of("&a=1&&b=2&", List.of(new RawPair("a", "1"), new RawPair("b", "2"))));
    }

    @ParameterizedTest
    @MethodSource("splitCases")
    void splitsPairsOnTheSeparatorOnly(String text, List<RawPair> expected) {

        assertEquals(expected, AbstractUrlParser.splitPairs(text, '&'));
    }

    @ParameterizedTest
    @MethodSource("splitCases")
    void splitsOnTheGivenSeparator(String text, List<RawPair> expected) {

        assertEquals(expected, AbstractUrlParser.splitPairs(text.replace('&', ';'), ';'));
    }
}
