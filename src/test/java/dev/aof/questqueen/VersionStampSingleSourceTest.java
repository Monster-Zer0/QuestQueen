package dev.aof.questqueen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guard for the version stamp (1.1.168).
 *
 * <p>Command feedback shipped {@code QQ[1.1.129]} while the mod was on 1.1.167, because the prefix was a
 * string literal in {@code QuestQueenCommands} and nobody bumped it for thirty-odd releases. The version now
 * comes from the loaded mod container ({@link QuestVersion}), which NeoForge fills from
 * {@code neoforge.mods.toml}, which Gradle expands from {@code mod_version} in {@code gradle.properties}.
 *
 * <p>The first test is the one that stops it drifting again: a {@code QQ[<digit>} literal anywhere in
 * {@code src/main/java} fails the build.
 */
class VersionStampSingleSourceTest {
    /**
     * Matches a version baked into a string literal, e.g. {@code "QQ[1.1.129]"}. The quote is part of the
     * pattern on purpose: prose that *describes* the old defect is fine, a literal that ships is not.
     */
    private static final Pattern HARDCODED_STAMP = Pattern.compile("\"QQ\\[\\d");

    @Test
    void noMainSourceBakesInAVersionStamp() throws IOException {
        Path root = Path.of("src", "main", "java");
        assumeTrue(Files.isDirectory(root), "not running from the project root");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = HARDCODED_STAMP.matcher(source);
                if (matcher.find()) {
                    offenders.add(file + ": " + source.substring(matcher.start(),
                            Math.min(source.length(), matcher.start() + 20)).lines().findFirst().orElse(""));
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "feedback text must read the version from QuestVersion, not write one down: " + offenders);
    }

    @Test
    void theStampIsWellFormed() {
        String stamp = QuestVersion.stamp();
        assertTrue(stamp.startsWith("QQ["), "unexpected stamp " + stamp);
        assertTrue(stamp.endsWith("]"), "unexpected stamp " + stamp);
        String inner = stamp.substring(3, stamp.length() - 1);
        assertTrue(inner.equals(QuestVersion.UNKNOWN) || inner.matches("\\d[\\w.+-]*"),
                "stamp body is neither the fallback nor a version: " + inner);
    }

    @Test
    void prefixPutsTheStampInFrontOfTheMessage() {
        assertEquals(QuestVersion.stamp() + " raid-start ok", QuestVersion.prefix("raid-start ok"));
    }
}
