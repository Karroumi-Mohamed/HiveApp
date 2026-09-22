package dev.karroumi.permissionizer.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void guardOffMethodIsNotEmittedAsAGrantablePermission() throws IOException {
        Path source = tempDir.resolve("sample/InternalMethodService.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package sample;

                import dev.karroumi.permissionizer.PermissionNode;

                @PermissionNode(key = "service", guard = PermissionNode.Guard.ON)
                public class InternalMethodService {
                    @PermissionNode(key = "read")
                    public void read() {}

                    @PermissionNode(key = "internal_bridge", guard = PermissionNode.Guard.OFF)
                    public void internalBridge() {}
                }
                """);

        Path classes = tempDir.resolve("classes-off");
        Path generated = tempDir.resolve("generated-off");
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

        assertTrue(generatedSource.contains("service.read"), generatedSource);
        assertFalse(generatedSource.contains("internal_bridge"), generatedSource);
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(30)
    void largeCatalogueCanBeReferencedInTheSameCompilation() throws IOException {
        Path source = tempDir.resolve("sample/LargeService.java");
        Files.createDirectories(source.getParent());
        StringBuilder body = new StringBuilder("package sample; import dev.karroumi.permissionizer.*; @PermissionNode(key=\"service\") public class LargeService {\n");
        for (int i = 0; i < 150; i++) {
            body.append("@PermissionNode(key=\"read_").append(i).append("\", description=\"Read\") public void read_").append(i).append("() {}\n");
        }
        body.append("public Permission reference() { return sample.generated.ServicePermissions.Read_0.permission(); } }");
        Files.writeString(source, body);
        Path classes = Files.createDirectories(tempDir.resolve("large-classes"));
        Path generated = Files.createDirectories(tempDir.resolve("large-generated"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var task = compiler.getTask(null, files, diagnostics,
                    List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString(), "-s", generated.toString()),
                    null, files.getJavaFileObjects(source.toFile()));
            task.setProcessors(List.of(new PermissionAnnotationProcessor()));
            assertTrue(task.call(), () -> diagnostics.getDiagnostics().toString());
        }
        assertTrue(readUnchecked(generated.resolve("sample/generated/ServicePermissions.java"))
                .contains("Map.<String, String>ofEntries"));
    }
}
