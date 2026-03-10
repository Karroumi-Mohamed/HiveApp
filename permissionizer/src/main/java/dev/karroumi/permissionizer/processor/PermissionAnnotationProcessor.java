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
 * <p>
 * Processes all {@code @PermissionNode} annotations, resolves parent hierarchy,
 * validates the tree, and generates:
 * </p>
 * <ul>
 * <li>Permission tree classes with type-safe {@code permission()}
 * accessors</li>
 * <li>Branch nodes with {@code all()} and {@code except()} methods</li>
 * <li>A {@code descriptions()} method on roots for database seeding</li>
 * <li>A verification class when {@code guard = ON} is detected</li>
 * <li>A {@code META-INF/permission-roots.idx} index file</li>
 * </ul>
 *
 * <p>
 * Supports two output styles via {@code -Apermissionizer.style=nested|flat}.
 * </p>
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
    private boolean guardOnDetected = false;

    private enum OutputStyle {
        NESTED, FLAT
    }

    private record ResolvedNode(
            String key,
            String description,
            String dotPath,
            String parentDotPath,
            Element element) {
    }

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

        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(PermissionNode.class);

        if (annotatedElements.isEmpty()) {
            return false;
        }

        // Phase 1: Validate element kinds and keys
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

        // Phase 2c: Detect guard=ON for verification class
        for (Element element : annotatedElements) {
            PermissionNode annotation = element.getAnnotation(PermissionNode.class);
            if (annotation.guard() == PermissionNode.Guard.ON) {
                guardOnDetected = true;
                break;
            }
        }

        // Phase 3: Validate no duplicate siblings
        validateNoDuplicateSiblings();

        // Phase 4: Generate permission classes, index, and verification
        OutputStyle style = readOutputStyle();
        generatePermissionTrees(style);

        if (guardOnDetected) {
            generateVerificationClass();
        }

        return true;
    }

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
            case PACKAGE, CLASS, INTERFACE, METHOD -> {
            }
            default -> {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "@PermissionNode is not supported on " + element.getKind(),
                        element);
                return;
            }
        }

        PermissionNode annotation = element.getAnnotation(PermissionNode.class);
        String key = annotation.key();

        // Key is required on packages and classes
        if ((element.getKind() == ElementKind.PACKAGE
                || element.getKind() == ElementKind.CLASS
                || element.getKind() == ElementKind.INTERFACE)
                && (key == null || key.isEmpty())) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "@PermissionNode on " + element.getKind()
                            + " requires a non-empty key.",
                    element);
        }

        // autoDiscover only on classes
        if (annotation.autoDiscover()
                && element.getKind() != ElementKind.CLASS
                && element.getKind() != ElementKind.INTERFACE) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "autoDiscover is only supported on classes.",
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

        // Derive key from method name if empty
        if ((key == null || key.isEmpty()) && element.getKind() == ElementKind.METHOD) {
            key = element.getSimpleName().toString();
        }

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
            resolvedNodes.put(elementKey,
                    new ResolvedNode(key, description, key, null, errorElement));
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

    private void autoDiscoverMethods(TypeElement classElement) {
        String classKey = classElement.getQualifiedName().toString();
        ResolvedNode classNode = resolvedNodes.get(classKey);
        if (classNode == null) {
            return;
        }

        String classPath = classNode.dotPath();

        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement method = (ExecutableElement) enclosed;

            // Skip if already annotated
            if (method.getAnnotation(PermissionNode.class) != null) {
                continue;
            }

            // Skip non-public
            if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }

            // Skip static
            if (method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }

            // Skip Object methods
            String methodName = method.getSimpleName().toString();
            if (isObjectMethod(methodName, method)) {
                continue;
            }

            String elementKey = classElement.getQualifiedName().toString()
                    + "#" + methodName;

            if (resolvedNodes.containsKey(elementKey)) {
                continue;
            }

            String dotPath = classPath + "." + methodName;
            resolvedNodes.put(elementKey,
                    new ResolvedNode(methodName, "", dotPath, classPath, method));
        }
    }

    private boolean isObjectMethod(String name, ExecutableElement method) {
        int paramCount = method.getParameters().size();

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
                                ? node.parentDotPath()
                                : "__ROOT__"));

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
        String basePackage = getOutputPackage(root.resolved.element());
        String outputPackage = basePackage + ".generated";
        String className = capitalize(root.resolved.key()) + "Permissions";
        String qualifiedName = outputPackage + "." + className;

        Map<String, String> allDescriptions = new LinkedHashMap<>();
        collectDescriptions(root, allDescriptions);

        try {
            JavaFileObject file = filer.createSourceFile(qualifiedName);
            try (PrintWriter out = new PrintWriter(file.openWriter())) {
                out.println("package " + outputPackage + ";");
                out.println();
                out.println("import javax.annotation.processing.Generated;");
                out.println("import dev.karroumi.permissionizer.Permission;");
                out.println("import java.util.Arrays;");
                out.println("import java.util.Map;");
                out.println("import java.util.Set;");
                out.println("import java.util.stream.Collectors;");
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

                // Root's permission() method
                writePermissionMethod(out, root.resolved, 1);

                // all() and except() if root has children
                if (!root.children.isEmpty()) {
                    writeAllMethod(out, root, 1);
                    writeExceptMethod(out, 1);
                }

                // Nested children
                for (TreeNode child : root.children) {
                    writeNestedClass(out, child, 1);
                }

                // descriptions() method
                writeDescriptionsMethod(out, allDescriptions, 1);

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
        boolean hasChildren = !node.children.isEmpty();

        out.println(indent + "public static final class " + className + " {");
        out.println();

        // permission() method
        writePermissionMethod(out, node.resolved, depth + 1);

        // all() and except() if has children
        if (hasChildren) {
            writeAllMethod(out, node, depth + 1);
            writeExceptMethod(out, depth + 1);
        }

        // Recurse into children
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
        List<TreeNode> allNodes = new ArrayList<>();
        collectAllNodes(root, allNodes);

        Map<String, String> allDescriptions = new LinkedHashMap<>();
        collectDescriptions(root, allDescriptions);

        String rootPrefix = capitalize(root.resolved.key()) + "Permissions";

        for (TreeNode node : allNodes) {
            String nodeBasePackage = getOutputPackage(node.resolved.element());
            String outputPackage = nodeBasePackage + ".generated";
            
            boolean isRoot = node.resolved.parentDotPath() == null;
            String className;
            if (isRoot) {
                className = rootPrefix;
            } else {
                className = rootPrefix + buildFlatClassName(node, root);
            }
            String qualifiedName = outputPackage + "." + className;

            boolean hasChildren = !node.children.isEmpty();

            try {
                JavaFileObject file = filer.createSourceFile(qualifiedName);
                try (PrintWriter out = new PrintWriter(file.openWriter())) {
                    out.println("package " + outputPackage + ";");
                    out.println();
                    out.println("import javax.annotation.processing.Generated;");
                    out.println("import dev.karroumi.permissionizer.Permission;");

                    if (hasChildren || isRoot) {
                        out.println("import java.util.Arrays;");
                        out.println("import java.util.Map;");
                        out.println("import java.util.Set;");
                        out.println("import java.util.stream.Collectors;");
                    }

                    out.println();
                    out.println("@Generated(\""
                            + PermissionAnnotationProcessor.class.getName() + "\")");
                    out.println("public final class " + className + " {");
                    out.println();

                    writePermissionMethod(out, node.resolved, 1);

                    if (hasChildren) {
                        writeFlatAllMethod(out, node, root, rootPrefix, outputPackage, 1);
                        writeExceptMethod(out, 1);
                    }

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

    private String buildFlatClassName(TreeNode node, TreeNode root) {
        List<String> segments = new ArrayList<>();
        String path = node.resolved.dotPath();
        String rootPath = root.resolved.dotPath();

        // Strip root prefix to get remaining segments
        String remaining = path.substring(rootPath.length());
        if (remaining.startsWith(".")) {
            remaining = remaining.substring(1);
        }

        for (String segment : remaining.split("\\.")) {
            segments.add(capitalize(segment));
        }

        return String.join("", segments);
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

    private void writePermissionMethod(PrintWriter out, ResolvedNode node, int depth) {
        String indent = "    ".repeat(depth);

        if (!node.description().isEmpty()) {
            out.println(indent + "/** " + node.description()
                    + " — {@code \"" + node.dotPath() + "\"} */");
        } else {
            out.println(indent + "/** {@code \"" + node.dotPath() + "\"} */");
        }

        out.println(indent + "public static final Permission PERMISSION = new Permission(\""
                + node.dotPath() + "\");");
        out.println();
        out.println(indent + "public static Permission permission() { return PERMISSION; }");
        out.println();
    }

    private void writeAllMethod(PrintWriter out, TreeNode node, int depth) {
        String indent = "    ".repeat(depth);

        // Collect all leaf descendants
        List<TreeNode> leaves = new ArrayList<>();
        collectLeaves(node, leaves);

        out.println(indent + "private static final Permission[] ALL = {");
        for (int i = 0; i < leaves.size(); i++) {
            TreeNode leaf = leaves.get(i);
            String accessor = buildNestedAccessor(leaf, node);
            out.print(indent + "    " + accessor + ".permission()");
            if (i < leaves.size() - 1) {
                out.println(",");
            } else {
                out.println();
            }
        }
        out.println(indent + "};");
        out.println();
        out.println(indent + "/** Returns all leaf permissions under this group. */");
        out.println(indent + "public static Permission[] all() { return ALL.clone(); }");
        out.println();
    }

    private void writeFlatAllMethod(PrintWriter out, TreeNode node, TreeNode root,
            String rootPrefix, String outputPackage, int depth) {
        String indent = "    ".repeat(depth);

        List<TreeNode> leaves = new ArrayList<>();
        collectLeaves(node, leaves);

        out.println(indent + "private static final Permission[] ALL = {");
        for (int i = 0; i < leaves.size(); i++) {
            TreeNode leaf = leaves.get(i);
            String flatClassName;
            if (leaf.resolved.parentDotPath() == null) {
                flatClassName = rootPrefix;
            } else {
                flatClassName = rootPrefix + buildFlatClassName(leaf, root);
            }
            
            String nodeBasePackage = getOutputPackage(leaf.resolved.element());
            String nodeOutputPackage = nodeBasePackage + ".generated";
            String fullAccessor = nodeOutputPackage + "." + flatClassName;
            
            out.print(indent + "    " + fullAccessor + ".permission()");
            if (i < leaves.size() - 1) {
                out.println(",");
            } else {
                out.println();
            }
        }
        out.println(indent + "};");
        out.println();
        out.println(indent + "/** Returns all leaf permissions under this group. */");
        out.println(indent + "public static Permission[] all() { return ALL.clone(); }");
        out.println();
    }

    private void writeExceptMethod(PrintWriter out, int depth) {
        String indent = "    ".repeat(depth);

        out.println(indent + "/**");
        out.println(indent + " * Returns all leaf permissions except the excluded ones.");
        out.println(indent + " *");
        out.println(indent + " * @param excluded permissions to exclude");
        out.println(indent + " * @return filtered permission array");
        out.println(indent + " */");
        out.println(indent + "public static Permission[] except(Permission... excluded) {");
        out.println(indent + "    Set<String> excludePaths = Arrays.stream(excluded)");
        out.println(indent + "        .map(Permission::path)");
        out.println(indent + "        .collect(Collectors.toSet());");
        out.println(indent + "    return Arrays.stream(ALL)");
        out.println(indent + "        .filter(p -> !excludePaths.contains(p.path()))");
        out.println(indent + "        .toArray(Permission[]::new);");
        out.println(indent + "}");
        out.println();
    }

    /**
     * Collects all leaf nodes (nodes with no children) under a given node.
     */
    private void collectLeaves(TreeNode node, List<TreeNode> leaves) {
        if (node.children.isEmpty()) {
            leaves.add(node);
        } else {
            for (TreeNode child : node.children) {
                collectLeaves(child, leaves);
            }
        }
    }

    /**
     * Builds a nested accessor path from a leaf back to a given ancestor.
     * For leaf "create" under "operations" under the ancestor, returns
     * "Operations.Create".
     */
    private String buildNestedAccessor(TreeNode target, TreeNode ancestor) {
        List<String> segments = new ArrayList<>();
        String targetPath = target.resolved.dotPath();
        String ancestorPath = ancestor.resolved.dotPath();

        String remaining = targetPath.substring(ancestorPath.length());
        if (remaining.startsWith(".")) {
            remaining = remaining.substring(1);
        }

        for (String segment : remaining.split("\\.")) {
            segments.add(capitalize(segment));
        }

        return String.join(".", segments);
    }

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

    private void collectDescriptions(TreeNode node, Map<String, String> descriptions) {
        descriptions.put(node.resolved.dotPath(), node.resolved.description());
        for (TreeNode child : node.children) {
            collectDescriptions(child, descriptions);
        }
    }

    // ──────────────────────────────────────────────
    // Verification class generation
    // ──────────────────────────────────────────────

    private void generateVerificationClass() {
        String qualifiedName = "dev.karroumi.permissionizer.generated.PermissionizerVerification";

        try {
            JavaFileObject file = filer.createSourceFile(qualifiedName);
            try (PrintWriter out = new PrintWriter(file.openWriter())) {
                out.println("package dev.karroumi.permissionizer.generated;");
                out.println();
                out.println("import javax.annotation.processing.Generated;");
                out.println();
                out.println("/**");
                out.println(" * Generated verification class indicating that guard=ON");
                out.println(" * exists in the codebase. Read by PermissionGuard at startup");
                out.println(" * to verify enforcement is active.");
                out.println(" *");
                out.println(" * <p>Do not edit. Regenerated on every compilation.</p>");
                out.println(" */");
                out.println("@Generated(\""
                        + PermissionAnnotationProcessor.class.getName() + "\")");
                out.println("public final class PermissionizerVerification {");
                out.println();
                out.println("    public static final boolean GUARD_ENABLED = true;");
                out.println();
                out.println("    private PermissionizerVerification() {}");
                out.println("}");
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate PermissionizerVerification: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // Index file
    // ──────────────────────────────────────────────

    private void writeRootIndex(List<String> rootClassNames) {
        try {
            var indexFile = filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    "META-INF/permission-roots.idx");
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
        if (s == null || s.isEmpty())
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String escapeJava(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
