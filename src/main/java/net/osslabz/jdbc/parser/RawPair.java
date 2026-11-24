package net.osslabz.jdbc.parser;

/**
 * One separator-delimited token of a URL's property section, split at its first '=' and neither trimmed nor decoded.
 *
 * @param key   the text before the first '=', or the whole token when it has none
 * @param value the text after the first '=', null when the token has no '='
 */
record RawPair(String key, String value) {}
