package dev.karroumi.permissionizer;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PermissionCollectorTest {

    @TempDir
    Path tempDir;

    @Test
    void failsWhenIndexedRootClassCannotBeLoaded() throws Exception {
        Path index = tempDir.resolve("META-INF/permission-roots.idx");
        Files.createDirectories(index.getParent());
        Files.writeString(index, "missing.permission.Root\n");

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[] { tempDir.toUri().toURL() }, previous)) {
            Thread.currentThread().setContextClassLoader(loader);
            assertThrows(IllegalStateException.class, PermissionCollector::collect);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void failsWhenGeneratedPermissionMethodThrows() {
        assertThrows(IllegalStateException.class,
                () -> PermissionCollector.collect(BrokenPermissionRoot.class));
    }

    public static final class BrokenPermissionRoot {
        public static Permission permission() {
            throw new IllegalStateException("broken generated permission");
        }

        public static Map<String, String> descriptions() throws IOException {
            return Map.of();
        }
    }
}
