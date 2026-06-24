package dev.karroumi.permissionizer.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PermissionAnnotationProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void overloadedMethodsKeepDistinctProcessorNodes() throws IOException {
        Path source = tempDir.resolve("sample/OverloadedService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package sample;

                import dev.karroumi.permissionizer.PermissionNode;

                @PermissionNode(key = "service")
                public class OverloadedService {
                    @PermissionNode(key = "read_text")
                    public void read(String value) {}

                    @PermissionNode(key = "read_number")
                    public void read(int value) {}
                }
                """);

        Path classes = tempDir.resolve("classes");
        Path generated = tempDir.resolve("generated");
        Files.createDirectories(classes);
        Files.createDirectories(generated);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjects(source.toFile());
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    files,
                    diagnostics,
                    List.of(
                            "-classpath", System.getProperty("java.class.path"),
                            "-d", classes.toString(),
                            "-s", generated.toString()),
                    null,
                    units);
            task.setProcessors(List.of(new PermissionAnnotationProcessor()));
            assertTrue(task.call(), () -> diagnostics.getDiagnostics().toString());
        }

        String generatedSource;
        try (var paths = Files.walk(generated)) {
            generatedSource = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(PermissionAnnotationProcessorTest::readUnchecked)
                    .collect(Collectors.joining("\n"));
        }

        assertTrue(generatedSource.contains("read_text"), generatedSource);
        assertTrue(generatedSource.contains("read_number"), generatedSource);
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
