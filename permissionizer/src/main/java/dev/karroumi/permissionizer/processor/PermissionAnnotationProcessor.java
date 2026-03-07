package dev.karroumi.permissionizer.processor;

import dev.karroumi.permissionizer.PermissionNode;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compile-time annotation processor for {@link PermissionNode}.
 *
 * <p>Processes all {@code @PermissionNode} annotations, resolves the hierarchical
 * parent chain for each, validates the tree structure, and generates:</p>
 * <ul>
 *   <li>One nested class tree per root permission with {@code $} constants</li>
 *   <li>A {@code META-INF/permission-roots.idx} index file listing all root classes</li>
 * </ul>
 *
 * <p>Parent resolution follows a strict priority chain:</p>
 * <ol>
 *   <li>Explicit {@code parent} attribute on the annotation</li>
 *   <li>Enclosing class's {@code @PermissionNode} (for method-level annotations)</li>
 *   <li>Walk up the package hierarchy until an annotated {@code package-info.java} is found</li>
 *   <li>Compile error if no parent can be resolved (except root nodes)</li>
 * </ol>
 */
@SupportedAnnotationTypes("dev.karroumi.permissionizer.PermissionNode")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class PermissionAnnotationProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Messager messager;
    private Filer filer;

    private final Map<String, ResolvedNode> resolvedNodes = new LinkedHashMap<>();

    /**
     * Internal representation of a fully resolved permission node.
     */
    private record ResolvedNode(
            String key,
            String description,
            String dotPath,
            String parentDotPath,
            Element element
    ) {}

    // ──────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        Set<? extends Element> annotatedElements =
                roundEnv.getElementsAnnotatedWith(PermissionNode.class);

        if (annotatedElements.isEmpty()) {
            return false;
        }

        // Phase 1: Validate element kinds
        for (Element element : annotatedElements) {
            validateElement(element);
        }

        // Phase 2: Resolve all parents and build dot-paths
        for (Element element : annotatedElements) {
            resolveNode(element);
        }

        // Phase 3: Validate no duplicate siblings
        validateNoDuplicateSiblings();

        // Phase 4: Generate nested permission tree classes and index file
        generatePermissionTrees();

        return true;
    }

    // ──────────────────────────────────────────────
    // Phase 1: Validation
    // ──────────────────────────────────────────────

    private void validateElement(Element element) {
        switch (element.getKind()) {
            case PACKAGE, CLASS, INTERFACE, METHOD -> {}
            default -> messager.printMessage(Diagnostic.Kind.ERROR,
                    "@PermissionNode is not supported on " + element.getKind(),
                    element);
        }
    }

    // ──────────────────────────────────────────────
    // Phase 2: Resolution
    // ──────────────────────────────────────────────

    /**
     * Resolves the full dot-path for an annotated element by walking
     * the parent chain. Results are cached in {@code resolvedNodes}.
     *
     * @return the fully resolved dot-path for this element
     */
    private String resolveNode(Element element) {
        String elementKey = getElementKey(element);

        // Already resolved — return cached path
        if (resolvedNodes.containsKey(elementKey)) {
            return resolvedNodes.get(elementKey).dotPath();
        }

        PermissionNode annotation = element.getAnnotation(PermissionNode.class);
        String key = annotation.key();
        String description = annotation.description();

        // Priority 1: Explicit parent
        TypeMirror parentMirror = getParentMirror(annotation);
        if (parentMirror != null && !isVoidType(parentMirror)) {
            return resolveWithExplicitParent(
                    element, elementKey, key, description, parentMirror);
        }

        // Priority 2: Method — try enclosing class, then package walk
        if (element.getKind() == ElementKind.METHOD) {
            TypeElement enclosingClass = (TypeElement) element.getEnclosingElement();

            if (enclosingClass.getAnnotation(PermissionNode.class) != null) {
                String parentPath = resolveNode(enclosingClass);
                String dotPath = parentPath + "." + key;
                resolvedNodes.put(elementKey,
                        new ResolvedNode(key, description, dotPath, parentPath, element));
                return dotPath;
            }

            return resolveViaPackageWalk(
                    element, enclosingClass, elementKey, key, description);
        }

        // Priority 3: Class — walk up packages
        if (element.getKind() == ElementKind.CLASS
                || element.getKind() == ElementKind.INTERFACE) {
            return resolveViaPackageWalk(
                    element, (TypeElement) element, elementKey, key, description);
        }

        // Priority 4: Package — walk up parent packages
        if (element.getKind() == ElementKind.PACKAGE) {
            PackageElement pkg = (PackageElement) element;
            String parentPath = walkUpPackages(pkg);

            if (parentPath != null) {
                String dotPath = parentPath + "." + key;
                resolvedNodes.put(elementKey,
                        new ResolvedNode(key, description, dotPath, parentPath, element));
                return dotPath;
            }

            // No parent found — this is a root node
            resolvedNodes.put(elementKey,
                    new ResolvedNode(key, description, key, null, element));
            return key;
        }

        messager.printMessage(Diagnostic.Kind.ERROR,
                "Cannot resolve parent for @PermissionNode on: " + elementKey,
                element);
        return key;
    }

    /**
     * Resolves a node whose annotation specifies an explicit parent class.
     * The parent class must itself carry @PermissionNode.
     */
    private String resolveWithExplicitParent(Element element, String elementKey,
                                              String key, String description,
                                              TypeMirror parentMirror) {
        Element parentElement = typeUtils.asElement(parentMirror);

        if (parentElement == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Explicit parent class could not be resolved", element);
            return key;
        }

        if (parentElement.getAnnotation(PermissionNode.class) == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Explicit parent '" + parentElement.getSimpleName()
                            + "' does not carry @PermissionNode",
                    element);
            return key;
        }

        String parentPath = resolveNode(parentElement);
        String dotPath = parentPath + "." + key;
        resolvedNodes.put(elementKey,
                new ResolvedNode(key, description, dotPath, parentPath, element));
        return dotPath;
    }

    /**
     * Resolves a node by walking up the package hierarchy from the given class
     * to find the nearest annotated package-info.java.
     */
    private String resolveViaPackageWalk(Element errorElement, TypeElement classElement,
                                          String elementKey, String key, String description) {
        PackageElement pkg = elementUtils.getPackageOf(classElement);
        String parentPath = walkUpPackages(pkg);

        if (parentPath == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "@PermissionNode on '" + key + "' cannot resolve a parent. "
                            + "Add @PermissionNode to a parent package-info.java "
                            + "or specify parent explicitly.",
                    errorElement);
            return key;
        }

        String dotPath = parentPath + "." + key;
        resolvedNodes.put(elementKey,
                new ResolvedNode(key, description, dotPath, parentPath, errorElement));
        return dotPath;
    }

    /**
     * Walks up the package hierarchy starting from the given package.
     * Checks the start package first, then each ancestor.
     *
     * @return the resolved dot-path of the nearest annotated ancestor, or null
     */
    private String walkUpPackages(PackageElement startPackage) {
        String packageName = startPackage.getQualifiedName().toString();

        // Only check ancestors, not the start package itself
        while (packageName.contains(".")) {
            int lastDot = packageName.lastIndexOf('.');
            packageName = packageName.substring(0, lastDot);

            PackageElement parentPkg = elementUtils.getPackageElement(packageName);
            if (parentPkg != null
                    && parentPkg.getAnnotation(PermissionNode.class) != null) {
                return resolveNode(parentPkg);
            }
        }

        return null;
    }

    // ──────────────────────────────────────────────
    // Phase 3: Sibling validation
    // ──────────────────────────────────────────────

    /**
     * Validates that no two nodes share the same key under the same parent.
     * Duplicate siblings would produce identical dot-paths.
     */
    private void validateNoDuplicateSiblings() {
        Map<String, List<ResolvedNode>> byParent = resolvedNodes.values().stream()
                .collect(Collectors.groupingBy(
                        node -> node.parentDotPath() != null
                                ? node.parentDotPath() : "__ROOT__"
                ));

        for (var entry : byParent.entrySet()) {
            Map<String, List<ResolvedNode>> byKey = entry.getValue().stream()
                    .collect(Collectors.groupingBy(ResolvedNode::key));

            for (var keyEntry : byKey.entrySet()) {
                if (keyEntry.getValue().size() > 1) {
                    for (ResolvedNode duplicate : keyEntry.getValue()) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "Duplicate permission key '" + keyEntry.getKey()
                                        + "' under parent '" + entry.getKey()
                                        + "'. Each sibling must have a unique key.",
                                duplicate.element());
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // Phase 4: Code generation
    // ──────────────────────────────────────────────

    /**
     * Builds a tree from the flat resolved nodes, finds all roots,
     * generates a nested class file per root, and writes the index file.
     */
    private void generatePermissionTrees() {
        // Build tree structure
        Map<String, TreeNode> treeNodes = new LinkedHashMap<>();

        for (ResolvedNode resolved : resolvedNodes.values()) {
            treeNodes.put(resolved.dotPath(), new TreeNode(resolved));
        }

        // Link children to parents
        for (TreeNode treeNode : treeNodes.values()) {
            String parentPath = treeNode.resolved.parentDotPath();
            if (parentPath != null && treeNodes.containsKey(parentPath)) {
                treeNodes.get(parentPath).children.add(treeNode);
            }
        }

        // Find roots and generate
        List<String> rootClassNames = new ArrayList<>();

        for (TreeNode treeNode : treeNodes.values()) {
            if (treeNode.resolved.parentDotPath() == null) {
                String className = generateRootClass(treeNode);
                if (className != null) {
                    rootClassNames.add(className);
                }
            }
        }

        // Write index file
        writeRootIndex(rootClassNames);
    }

    /**
     * Internal tree node linking a resolved permission to its children.
     */
    private static final class TreeNode {
        final ResolvedNode resolved;
        final List<TreeNode> children = new ArrayList<>();

        TreeNode(ResolvedNode resolved) {
            this.resolved = resolved;
        }
    }

    /**
     * Generates a single root permission class with nested static classes
     * mirroring the permission tree. The class is generated in the same
     * package as the root element.
     *
     * @return the fully qualified class name, or null on failure
     */
    private String generateRootClass(TreeNode root) {
        String outputPackage = getOutputPackage(root.resolved.element());
        String className = capitalize(root.resolved.key()) + "Permissions";
        String qualifiedName = outputPackage + "." + className;

        try {
            JavaFileObject file = filer.createSourceFile(qualifiedName);
            try (PrintWriter out = new PrintWriter(file.openWriter())) {
                out.println("package " + outputPackage + ";");
                out.println();
                out.println("import javax.annotation.processing.Generated;");
                out.println();
                out.println("/**");
                out.println(" * Generated permission tree rooted at"
                        + " {@code \"" + root.resolved.dotPath() + "\"}.");
                out.println(" *");
                out.println(" * <p>Do not edit. Regenerated on every compilation.</p>");
                out.println(" */");
                out.println("@Generated(\""
                        + PermissionAnnotationProcessor.class.getName() + "\")");
                out.println("public final class " + className + " {");
                out.println();

                // Root's own path constant
                writeDescription(out, root.resolved, 1);
                out.println("    public static final String $ = \""
                        + root.resolved.dotPath() + "\";");
                out.println();

                // Nested children
                for (TreeNode child : root.children) {
                    writeNestedClass(out, child, 1);
                }

                // Private constructor
                out.println("    private " + className + "() {}");
                out.println("}");
            }
            return qualifiedName;
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate " + className + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Recursively writes a nested static class for a tree node and its children.
     */
    private void writeNestedClass(PrintWriter out, TreeNode node, int depth) {
        String indent = "    ".repeat(depth);
        String innerIndent = "    ".repeat(depth + 1);
        String className = capitalize(node.resolved.key());

        out.println(indent + "public static final class " + className + " {");

        // Path constant
        writeDescription(out, node.resolved, depth + 1);
        out.println(innerIndent + "public static final String $ = \""
                + node.resolved.dotPath() + "\";");
        out.println();

        // Recurse into children
        for (TreeNode child : node.children) {
            writeNestedClass(out, child, depth + 1);
        }

        // Private constructor
        out.println(innerIndent + "private " + className + "() {}");
        out.println(indent + "}");
        out.println();
    }

    /**
     * Writes a javadoc comment with the node's description and path.
     */
    private void writeDescription(PrintWriter out, ResolvedNode node, int depth) {
        String indent = "    ".repeat(depth);
        if (!node.description().isEmpty()) {
            out.println(indent + "/** " + node.description()
                    + " — {@code \"" + node.dotPath() + "\"} */");
        } else {
            out.println(indent + "/** {@code \"" + node.dotPath() + "\"} */");
        }
    }

    /**
     * Writes the index file listing all generated root class names.
     * The PermissionCollector reads this at runtime to discover roots.
     */
    private void writeRootIndex(List<String> rootClassNames) {
        try {
            var indexFile = filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    "META-INF/permission-roots.idx"
            );
            try (PrintWriter out = new PrintWriter(indexFile.openWriter())) {
                for (String className : rootClassNames) {
                    out.println(className);
                }
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write permission-roots.idx: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────

    /**
     * Determines the output package for a generated class.
     * Uses the element's own package so the generated class lives
     * alongside the annotated code. Works for any consumer project.
     */
    private String getOutputPackage(Element element) {
        if (element.getKind() == ElementKind.PACKAGE) {
            return ((PackageElement) element).getQualifiedName().toString();
        }
        return elementUtils.getPackageOf(element).getQualifiedName().toString();
    }

    /**
     * Returns a unique key for any annotated element.
     * Packages use qualified name. Classes use qualified name.
     * Methods use enclosing class qualified name + "#" + method name.
     */
    private String getElementKey(Element element) {
        return switch (element.getKind()) {
            case PACKAGE -> ((PackageElement) element)
                    .getQualifiedName().toString();
            case CLASS, INTERFACE -> ((TypeElement) element)
                    .getQualifiedName().toString();
            case METHOD -> {
                TypeElement enclosing = (TypeElement) element.getEnclosingElement();
                yield enclosing.getQualifiedName().toString()
                        + "#" + element.getSimpleName().toString();
            }
            default -> element.toString();
        };
    }

    /**
     * Extracts the parent TypeMirror from a @PermissionNode annotation.
     *
     * <p>At compile time, reading a Class value from an annotation throws
     * MirroredTypeException. This is the standard Java annotation processing
     * pattern — not a hack. The exception carries the TypeMirror we need.</p>
     */
    private TypeMirror getParentMirror(PermissionNode annotation) {
        try {
            annotation.parent();
            return null;
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
    }

    /**
     * Checks if a TypeMirror represents java.lang.Void (the "not specified" default).
     */
    private boolean isVoidType(TypeMirror mirror) {
        return mirror.toString().equals("java.lang.Void");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}