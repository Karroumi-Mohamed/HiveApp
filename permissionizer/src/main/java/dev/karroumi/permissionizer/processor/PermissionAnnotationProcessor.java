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
 *   <li>Permission classes with {@code $} constants (nested or flat style)</li>
 *   <li>A {@code descriptions()} method on each root for runtime access</li>
 *   <li>A {@code META-INF/permission-roots.idx} index file listing all roots</li>
 * </ul>
 *
 * <p>Output style is controlled by the compiler argument
 * {@code -Apermissionizer.style=nested} (default) or
 * {@code -Apermissionizer.style=flat}.</p>
 */
@SupportedAnnotationTypes("dev.karroumi.permissionizer.PermissionNode")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedOptions("permissionizer.style")
public class PermissionAnnotationProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Messager messager;
    private Filer filer;

    private final Map<String, ResolvedNode> resolvedNodes = new LinkedHashMap<>();

    private enum OutputStyle { NESTED, FLAT }

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

        // Phase 2b: Auto-discover methods on classes with autoDiscover=true
        for (Element element : annotatedElements) {
            if (element.getKind() == ElementKind.CLASS
                    || element.getKind() == ElementKind.INTERFACE) {
                PermissionNode annotation = element.getAnnotation(PermissionNode.class);
                if (annotation.autoDiscover()) {
                    autoDiscoverMethods((TypeElement) element);
                }
            }
        }

        // Phase 3: Validate no duplicate siblings
        validateNoDuplicateSiblings();

        // Phase 4: Generate permission classes and index file
        OutputStyle style = readOutputStyle();
        generatePermissionTrees(style);

        return true;
    }

    /**
     * Reads the output style from compiler arguments.
     * Defaults to NESTED if not specified or unrecognized.
     */
    private OutputStyle readOutputStyle() {
        String value = processingEnv.getOptions().get("permissionizer.style");
        if ("flat".equalsIgnoreCase(value)) {
            return OutputStyle.FLAT;
        }
        return OutputStyle.NESTED;
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

    private String resolveNode(Element element) {
        String elementKey = getElementKey(element);

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

            resolvedNodes.put(elementKey,
                    new ResolvedNode(key, description, key, null, element));
            return key;
        }

        messager.printMessage(Diagnostic.Kind.ERROR,
                "Cannot resolve parent for @PermissionNode on: " + elementKey,
                element);
        return key;
    }

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

    private String walkUpPackages(PackageElement startPackage) {
        String packageName = startPackage.getQualifiedName().toString();

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
    // Phase 2b: Auto-discovery
    // ──────────────────────────────────────────────

    /**
     * Scans a class for declared public methods that don't have their own
     * @PermissionNode annotation. Creates resolved nodes for each using
     * the method name as the key.
     *
     * <p>Skips:</p>
     * <ul>
     *   <li>Methods already annotated with @PermissionNode</li>
     *   <li>Non-public methods</li>
     *   <li>Static methods</li>
     *   <li>Methods inherited from Object (toString, equals, hashCode, etc.)</li>
     *   <li>Constructors</li>
     * </ul>
     */
    private void autoDiscoverMethods(TypeElement classElement) {
        String classKey = classElement.getQualifiedName().toString();
        ResolvedNode classNode = resolvedNodes.get(classKey);
        if (classNode == null) {
            return;
        }

        String classPath = classNode.dotPath();

        for (Element enclosed : classElement.getEnclosedElements()) {
            // Only methods
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement method = (ExecutableElement) enclosed;

            // Skip if already annotated
            if (method.getAnnotation(PermissionNode.class) != null) {
                continue;
            }

            // Skip non-public
            if (!method.getModifiers().contains(javax.lang.model.element.Modifier.PUBLIC)) {
                continue;
            }

            // Skip static
            if (method.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)) {
                continue;
            }

            // Skip Object methods
            String methodName = method.getSimpleName().toString();
            if (isObjectMethod(methodName, method)) {
                continue;
            }

            // Create a resolved node using method name as key
            String elementKey = classElement.getQualifiedName().toString()
                    + "#" + methodName;

            // Skip if already resolved (shouldn't happen but safety check)
            if (resolvedNodes.containsKey(elementKey)) {
                continue;
            }

            String dotPath = classPath + "." + methodName;
            resolvedNodes.put(elementKey,
                    new ResolvedNode(methodName, "", dotPath, classPath, method));
        }
    }

    /**
     * Checks if a method is one of the standard Object methods that
     * should not be auto-discovered as permissions.
     */
    private boolean isObjectMethod(String name, ExecutableElement method) {
        List<? extends javax.lang.model.element.VariableElement> params = method.getParameters();
        int paramCount = params.size();

        return switch (name) {
            case "toString" -> paramCount == 0;
            case "hashCode" -> paramCount == 0;
            case "equals" -> paramCount == 1;
            case "clone" -> paramCount == 0;
            case "finalize" -> paramCount == 0;
            case "getClass" -> paramCount == 0;
            case "notify" -> paramCount == 0;
            case "notifyAll" -> paramCount == 0;
            case "wait" -> paramCount <= 2;
            default -> false;
        };
    }

    // ──────────────────────────────────────────────
    // Phase 3: Sibling validation
    // ──────────────────────────────────────────────

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

    private void generatePermissionTrees(OutputStyle style) {
        // Build tree structure
        Map<String, TreeNode> treeNodes = new LinkedHashMap<>();

        for (ResolvedNode resolved : resolvedNodes.values()) {
            treeNodes.put(resolved.dotPath(), new TreeNode(resolved));
        }

        for (TreeNode treeNode : treeNodes.values()) {
            String parentPath = treeNode.resolved.parentDotPath();
            if (parentPath != null && treeNodes.containsKey(parentPath)) {
                treeNodes.get(parentPath).children.add(treeNode);
            }
        }

        List<String> rootClassNames = new ArrayList<>();

        for (TreeNode treeNode : treeNodes.values()) {
            if (treeNode.resolved.parentDotPath() == null) {
                if (style == OutputStyle.NESTED) {
                    String className = generateNestedRootClass(treeNode);
                    if (className != null) {
                        rootClassNames.add(className);
                    }
                } else {
                    List<String> classNames = generateFlatClasses(treeNode);
                    rootClassNames.addAll(classNames);
                }
            }
        }

        writeRootIndex(rootClassNames);
    }

    private static final class TreeNode {
        final ResolvedNode resolved;
        final List<TreeNode> children = new ArrayList<>();

        TreeNode(ResolvedNode resolved) {
            this.resolved = resolved;
        }
    }

    // ──────────────────────────────────────────────
    // Nested style generation
    // ──────────────────────────────────────────────

    private String generateNestedRootClass(TreeNode root) {
        String outputPackage = getOutputPackage(root.resolved.element());
        String className = capitalize(root.resolved.key()) + "Permissions";
        String qualifiedName = outputPackage + "." + className;

        // Collect all descriptions for the descriptions() method
        Map<String, String> allDescriptions = new LinkedHashMap<>();
        collectDescriptions(root, allDescriptions);

        try {
            JavaFileObject file = filer.createSourceFile(qualifiedName);
            try (PrintWriter out = new PrintWriter(file.openWriter())) {
                out.println("package " + outputPackage + ";");
                out.println();
                out.println("import javax.annotation.processing.Generated;");
                out.println("import java.util.Map;");
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

                // descriptions() method
                writeDescriptionsMethod(out, allDescriptions, 1);

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

    private void writeNestedClass(PrintWriter out, TreeNode node, int depth) {
        String indent = "    ".repeat(depth);
        String innerIndent = "    ".repeat(depth + 1);
        String className = capitalize(node.resolved.key());

        out.println(indent + "public static final class " + className + " {");

        writeDescription(out, node.resolved, depth + 1);
        out.println(innerIndent + "public static final String $ = \""
                + node.resolved.dotPath() + "\";");
        out.println();

        for (TreeNode child : node.children) {
            writeNestedClass(out, child, depth + 1);
        }

        out.println(innerIndent + "private " + className + "() {}");
        out.println(indent + "}");
        out.println();
    }

    // ──────────────────────────────────────────────
    // Flat style generation
    // ──────────────────────────────────────────────

    private List<String> generateFlatClasses(TreeNode root) {
        List<String> generatedClassNames = new ArrayList<>();

        // Collect all nodes in the tree
        List<TreeNode> allNodes = new ArrayList<>();
        collectAllNodes(root, allNodes);

        // Collect all descriptions for the root's descriptions() method
        Map<String, String> allDescriptions = new LinkedHashMap<>();
        collectDescriptions(root, allDescriptions);

        String outputPackage = getOutputPackage(root.resolved.element());

        for (TreeNode node : allNodes) {
            boolean hasChildren = !node.children.isEmpty();
            String className = capitalize(node.resolved.key())
                    + (hasChildren ? "Permissions" : "Permission");
            String qualifiedName = outputPackage + "." + className;

            try {
                JavaFileObject file = filer.createSourceFile(qualifiedName);
                try (PrintWriter out = new PrintWriter(file.openWriter())) {
                    out.println("package " + outputPackage + ";");
                    out.println();
                    out.println("import javax.annotation.processing.Generated;");

                    // Only root gets descriptions() and Map import
                    boolean isRoot = node.resolved.parentDotPath() == null;
                    if (isRoot) {
                        out.println("import java.util.Map;");
                    }

                    out.println();
                    out.println("@Generated(\""
                            + PermissionAnnotationProcessor.class.getName() + "\")");
                    out.println("public final class " + className + " {");
                    out.println();

                    writeDescription(out, node.resolved, 1);
                    out.println("    public static final String $ = \""
                            + node.resolved.dotPath() + "\";");
                    out.println();

                    // Only root gets descriptions()
                    if (isRoot) {
                        writeDescriptionsMethod(out, allDescriptions, 1);
                    }

                    out.println("    private " + className + "() {}");
                    out.println("}");
                }
                generatedClassNames.add(qualifiedName);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Failed to generate " + className + ": " + e.getMessage());
            }
        }

        return generatedClassNames;
    }

    private void collectAllNodes(TreeNode node, List<TreeNode> result) {
        result.add(node);
        for (TreeNode child : node.children) {
            collectAllNodes(child, result);
        }
    }

    // ──────────────────────────────────────────────
    // Shared generation helpers
    // ──────────────────────────────────────────────

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
     * Generates a static descriptions() method that returns a Map
     * of all permission paths to their descriptions.
     */
    private void writeDescriptionsMethod(PrintWriter out,
                                          Map<String, String> descriptions,
                                          int depth) {
        String indent = "    ".repeat(depth);

        out.println(indent + "/**");
        out.println(indent + " * Returns all permission paths and their descriptions.");
        out.println(indent + " * Used by the collector for database seeding.");
        out.println(indent + " */");
        out.println(indent + "public static Map<String, String> descriptions() {");

        if (descriptions.size() <= 10) {
            // Map.of() supports up to 10 entries
            out.print(indent + "    return Map.of(");
            boolean first = true;
            for (var entry : descriptions.entrySet()) {
                if (!first) {
                    out.print(",");
                }
                out.println();
                out.print(indent + "        \"" + entry.getKey() + "\", \""
                        + escapeJava(entry.getValue()) + "\"");
                first = false;
            }
            out.println();
            out.println(indent + "    );");
        } else {
            // Map.ofEntries() for more than 10
            out.println(indent + "    return Map.ofEntries(");
            boolean first = true;
            for (var entry : descriptions.entrySet()) {
                if (!first) {
                    out.println(",");
                }
                out.print(indent + "        Map.entry(\"" + entry.getKey()
                        + "\", \"" + escapeJava(entry.getValue()) + "\")");
                first = false;
            }
            out.println();
            out.println(indent + "    );");
        }

        out.println(indent + "}");
        out.println();
    }

    /**
     * Recursively collects all path-description pairs from a tree.
     */
    private void collectDescriptions(TreeNode node, Map<String, String> descriptions) {
        descriptions.put(node.resolved.dotPath(), node.resolved.description());
        for (TreeNode child : node.children) {
            collectDescriptions(child, descriptions);
        }
    }

    /**
     * Writes the index file listing all generated root class names.
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

    private String getOutputPackage(Element element) {
        if (element.getKind() == ElementKind.PACKAGE) {
            return ((PackageElement) element).getQualifiedName().toString();
        }
        return elementUtils.getPackageOf(element).getQualifiedName().toString();
    }

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

    private TypeMirror getParentMirror(PermissionNode annotation) {
        try {
            annotation.parent();
            return null;
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
    }

    private boolean isVoidType(TypeMirror mirror) {
        return mirror.toString().equals("java.lang.Void");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String escapeJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
