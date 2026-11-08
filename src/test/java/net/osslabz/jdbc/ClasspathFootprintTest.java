package net.osslabz.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Checks the main output directory, which the jar packages as it is.
 */
class ClasspathFootprintTest {

    @Test
    void shipsNoGitProperties() throws URISyntaxException {

        Path mainOutput = Path.of(JdbcUrlParser.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());

        // Spring Boot reads the first git.properties on the classpath as the host application's.
        assertFalse(
                Files.exists(mainOutput.resolve("git.properties")),
                "git.properties in " + mainOutput + " would ship in the jar");
    }
}
