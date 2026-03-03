package com.hiveapp.permission.processor;

import com.hiveapp.permission.Permission;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("com.hiveapp.permission.Permission")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class PermissionAnnotationProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Messager messager;
    private Filer filer;

    private final Map<String, ResolvedNode> resolvedNodes = new LinkedHashMap<>();

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

        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(Permission.class);

        if (annotatedElements.isEmpty()) {
            return false;
        }

        // Phase 1: Register all annotated elements
        for (Element element : annotatedElements) {
            registerElement(element);
        }

        // Phase 2: Resolve all parents and build dot-paths
        for (Element element : annotatedElements) {
            resolveNode(element);
        }

        // Phase 3: Validate no duplicate siblings
        validateNoDuplicateSiblings();

        // Phase 4: Generate nested permission tree classes
        generatePermissionTrees();

        return true;
    }

    // ──────────────────────────────────────────────
    // Phase 1: Registration
    // ──────────────────────────────────────────────

    private void registerElement(Element element) {
        switch (element.getKind()) {
            case PACKAGE, CLASS, INTERFACE, METHOD -> {}
            default -> messager.printMessage(Diagnostic.Kind.ERROR,
                    "@Permission is not supported on " + element.getKind(), element);
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

        Permission annotation = element.getAnnotation(Permission.class);
        String key = annotation.key();
        String description = annotation.description();

        // 1. Try explicit parent
        TypeMirror parentMirror = getParentMirror(annotation);
        if (parentMirror != null && !isVoidType(parentMirror)) {
            return resolveWithExplicitParent(element, elementKey, key, description, parentMirror);
        }

        // 2. Method — try enclosing class, then package walk
        if (element.getKind() == ElementKind.METHOD) {
            TypeElement enclosingClass = (TypeElement) element.getEnclosingElement();

            if (enclosingClass.getAnnotation(Permission.class) != null) {
                String parentPath = resolveNode(enclosingClass);
                String dotPath = parentPath + "." + key;
                resolvedNodes.put(elementKey, new ResolvedNode(key, description, dotPath, parentPath, element));
                return dotPath;
            }

            return resolveViaPackageWalk(element, enclosingClass, elementKey, key, description);
        }

        // 3. Class — walk up packages
        if (element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE) {
            return resolveViaPackageWalk(element, (TypeElement) element, elementKey, key, description);
        }

        // 4. Package — walk up parent packages
        if (element.getKind() == ElementKind.PACKAGE) {
            PackageElement pkg = (PackageElement) element;
            String parentPath = walkUpPackages(pkg);

            if (parentPath != null) {
                String dotPath = parentPath + "." + key;
                resolvedNodes.put(elementKey, new ResolvedNode(key, description, dotPath, parentPath, element));
                return dotPath;
            }

            resolvedNodes.put(elementKey, new ResolvedNode(key, description, key, null, element));
            return key;
        }

        messager.printMessage(Diagnostic.Kind.ERROR,
                "Cannot resolve parent for @Permission on element: " + elementKey, element);
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

        if (parentElement.getAnnotation(Permission.class) == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Explicit parent class '" + parentElement.getSimpleName() +
                            "' does not carry @Permission annotation", element);
            return key;
        }

        String parentPath = resolveNode(parentElement);
        String dotPath = parentPath + "." + key;
        resolvedNodes.put(elementKey, new ResolvedNode(key, description, dotPath, parentPath, element));
        return dotPath;
    }

    private String resolveViaPackageWalk(Element errorElement, TypeElement classElement,
                                         String elementKey, String key, String description) {
        PackageElement pkg = elementUtils.getPackageOf(classElement);
        String parentPath = walkUpPackages(pkg);

        if (parentPath == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "@Permission on '" + key + "' cannot resolve a parent. " +
                            "Add @Permission to a parent package-info.java or specify parent explicitly.",
                    errorElement);
            return key;
        }

        String dotPath = parentPath + "." + key;
        resolvedNodes.put(elementKey, new ResolvedNode(key, description, dotPath, parentPath, errorElement));
        return dotPath;
    }

    private String walkUpPackages(PackageElement startPackage) {
        String packageName = startPackage.getQualifiedName().toString();

        if (startPackage.getAnnotation(Permission.class) != null) {
            return resolveNode(startPackage);
        }

        while (packageName.contains(".")) {
            int lastDot = packageName.lastIndexOf('.');
            packageName = packageName.substring(0, lastDot);

            PackageElement parentPkg = elementUtils.getPackageElement(packageName);
            if (parentPkg != null && parentPkg.getAnnotation(Permission.class) != null) {
                return resolveNode(parentPkg);
            }
        }

        return null;
    }

    // ──────────────────────────────────────────────
    // Phase 3: Validation
    // ──────────────────────────────────────────────

    private void validateNoDuplicateSiblings() {
        Map<String, List<ResolvedNode>> byParent = resolvedNodes.values().stream()
                .collect(Collectors.groupingBy(
                        node -> node.parentDotPath() != null ? node.parentDotPath() : "__ROOT__"
                ));

        for (var entry : byParent.entrySet()) {
            Map<String, List<ResolvedNode>> byKey = entry.getValue().stream()
                    .collect(Collectors.groupingBy(ResolvedNode::key));

            for (var keyEntry : byKey.entrySet()) {
                if (keyEntry.getValue().size() > 1) {
                    for (ResolvedNode duplicate : keyEntry.getValue()) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                                "Duplicate permission key '" + keyEntry.getKey() +
                                        "' under parent '" + entry.getKey() +
                                        "'. Each sibling must have a unique key.",
                                duplicate.element());
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // Phase 4: Code Generation — Nested Tree
    // ──────────────────────────────────────────────

    private void generatePermissionTrees() {
        // Build a tree structure from the flat resolved nodes
        Map<String, TreeNode> treeNodes = new LinkedHashMap<>();

        // Create tree nodes for every resolved permission
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

        // Find roots (nodes with no parent) and generate one class per root
        for (TreeNode treeNode : treeNodes.values()) {
            if (treeNode.resolved.parentDotPath() == null) {
                generateRootClass(treeNode);
            }
        }
    }

    private static final class TreeNode {
        final ResolvedNode resolved;
        final List<TreeNode> children = new ArrayList<>();

        TreeNode(ResolvedNode resolved) {
            this.resolved = resolved;
        }
    }

    private void generateRootClass(TreeNode root) {
        // Determine output package — use the permission library's generated subpackage
        String outputPackage = "com.hiveapp.permission.generated";
        String className = capitalize(root.resolved.key()) + "Permissions";

        try {
            JavaFileObject file = filer.createSourceFile(outputPackage + "." + className);
            try (PrintWriter out = new PrintWriter(file.openWriter())) {
                out.println("package " + outputPackage + ";");
                out.println();
                out.println("import javax.annotation.processing.Generated;");
                out.println();
                out.println("/**");
                out.println(" * Generated permission tree rooted at {@code \"" + root.resolved.dotPath() + "\"}.");
                out.println(" *");
                out.println(" * <p>Do not edit manually. Regenerated on every compilation.</p>");
                out.println(" */");
                out.println("@Generated(\"" + PermissionAnnotationProcessor.class.getName() + "\")");
                out.println("public final class " + className + " {");
                out.println();

                // Root's own path
                writeDescription(out, root.resolved, 1);
                out.println("    public static final String $ = \"" + root.resolved.dotPath() + "\";");
                out.println();

                // Generate nested children
                for (TreeNode child : root.children) {
                    writeNestedClass(out, child, 1);
                }

                out.println("    private " + className + "() {}");
                out.println("}");
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate " + className + ": " + e.getMessage());
        }
    }

    private void writeNestedClass(PrintWriter out, TreeNode node, int depth) {
        String indent = "    ".repeat(depth);
        String innerIndent = "    ".repeat(depth + 1);
        String className = capitalize(node.resolved.key());

        out.println(indent + "public static final class " + className + " {");

        // The path constant
        writeDescription(out, node.resolved, depth + 1);
        out.println(innerIndent + "public static final String $ = \"" + node.resolved.dotPath() + "\";");
        out.println();

        // Recurse into children
        for (TreeNode child : node.children) {
            writeNestedClass(out, child, depth + 1);
        }

        out.println(innerIndent + "private " + className + "() {}");
        out.println(indent + "}");
        out.println();
    }

    private void writeDescription(PrintWriter out, ResolvedNode node, int depth) {
        String indent = "    ".repeat(depth);
        if (!node.description().isEmpty()) {
            out.println(indent + "/** " + node.description() + " — {@code \"" + node.dotPath() + "\"} */");
        } else {
            out.println(indent + "/** {@code \"" + node.dotPath() + "\"} */");
        }
    }

    // ──────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────

    private String getElementKey(Element element) {
        return switch (element.getKind()) {
            case PACKAGE -> ((PackageElement) element).getQualifiedName().toString();
            case CLASS, INTERFACE -> ((TypeElement) element).getQualifiedName().toString();
            case METHOD -> {
                TypeElement enclosing = (TypeElement) element.getEnclosingElement();
                yield enclosing.getQualifiedName().toString() + "#" + element.getSimpleName().toString();
            }
            default -> element.toString();
        };
    }

    private TypeMirror getParentMirror(Permission annotation) {
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
}