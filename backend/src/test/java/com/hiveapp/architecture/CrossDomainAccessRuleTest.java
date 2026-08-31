package com.hiveapp.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the cross-domain access rule so it fails loudly instead of relying on code review.
 *
 * <p>The rule: a domain reaches another domain only through its service. Facts come from a read
 * view; a managed entity comes from an explicitly named service method. Another domain's
 * repositories are never imported.</p>
 *
 * <p>This is a source scan rather than a bytecode rule so it needs no extra dependency and
 * reports the offending file directly.</p>
 */
class CrossDomainAccessRuleTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "hiveapp");

    @Test
    void platformCodeNeverImportsIdentityRepositories() throws IOException {
        List<String> offenders = importOffenders(
                MAIN.resolve("platform"), "import com.hiveapp.identity.domain.repository");

        assertThat(offenders)
                .as("""
                    Platform classes must reach identity through IdentityService.
                    Use findUserView(..) for facts, or createUser(..)/requireManagedUser(..) when a
                    JPA relationship genuinely needs the managed row.""")
                .isEmpty();
    }

    /**
     * The reverse direction is NOT asserted yet. Three identity classes
     * ({@code AuthServiceImpl}, {@code ClientCredentialAuthenticationService},
     * {@code CredentialLifecycleService}) import the member repository because authentication
     * needs membership facts. Closing that requires an unguarded membership lookup owned by the
     * member domain — authentication runs before any permission context exists, so the guarded
     * MemberService cannot serve it. Tracked as MODULES-003.
     */

    private static List<String> importOffenders(Path root, String forbiddenPrefix) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Expected source root not found: " + root.toAbsolutePath());
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> readImports(p).stream()
                            .filter(line -> line.startsWith(forbiddenPrefix))
                            .map(line -> root.relativize(p) + " -> " + line.trim()))
                    .toList();
        }
    }

    private static List<String> readImports(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("import "))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
