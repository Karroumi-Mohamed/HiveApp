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

/**
 * Compile-time annotation processor for {@link Permission}.
 *
 * <p>Performs four phases:</p>
 * <ol>
 *   <li>Register all {@code @Permission}-annotated elements</li>
 *   <li>Resolve parent hierarchy and build dot-paths for each node</li>
 *   <li>Validate no duplicate sibling keys exist</li>
 *   <li>Generate companion classes with {@code public static final String} constants</li>
 * </ol>
 *
 * <p>Parent resolution follows a strict priority chain:</p>
 * <ol>
 *   <li>Explicit {@code parent} attribute on the annotation</li>
 *   <li>Enclosing class's {@code @Permission} (for method-level annotations)</li>
 *   <li>Walk up the package hierarchy until an annotated {@code package-info.java} is found</li>
 *   <li>Compile error if no parent can be resolved (except root nodes)</li>
 * </ol>
 */
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

        // Phase 4: Generate companion classes
        generateCompanionClasses(annotatedElements);

        return true;
    }

    // ──────────────────────────────────────────────
    // Phase 1: Registration
    // ──────────────────────────────────────────────

    private void registerElement(Element element) {
        switch (element.getKind()) {
            case PACKAGE, CLASS, INTERFACE, METHOD -> {
                // Valid targets — nothing to do beyond confirming the kind.
                // Resolution happens in Phase 2.
            }
            default -> messager.printMessage(Diagnostic.Kind.ERROR,
                    "@Permission is not supported on " + element.getKind(), element);
        }
    }

    // ──────────────────────────────────────────────
    // Phase 2: Resolution
    // ──────────────────────────────────────────────

    private String resolveNode(Element element) {
        String elementKey = getElementKey(element);

        // Already resolved — return cached path
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

            // Enclosing class has no @Permission — walk up packages from the class
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

            // No parent package found — this is a root node
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

    /**
     * Walks up the package hierarchy starting from the given package.
     * Returns the resolved dot-path of the first ancestor package that carries @Permission,
     * or null if no annotated ancestor is found.
     */
    private String walkUpPackages(PackageElement startPackage) {
        String packageName = startPackage.getQualifiedName().toString();

        // Also check the start package itself (for classes inside an annotated package)
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
    // Phase 4: Code Generation
    // ──────────────────────────────────────────────

    private void generateCompanionClasses(Set<? extends Element> annotatedElements) {
        // Group method-level permissions by their enclosing class
        Map<TypeElement, List<ResolvedNode>> methodsByClass = new LinkedHashMap<>();

        // Collect class-level and package-level nodes
        Set<TypeElement> classesWithPermission = new LinkedHashSet<>();
        List<ResolvedNode> packageNodes = new ArrayList<>();

        for (Element element : annotatedElements) {
            String elementKey = getElementKey(element);
            ResolvedNode node = resolvedNodes.get(elementKey);
            if (node == null) continue;

            switch (element.getKind()) {
                case METHOD -> {
                    TypeElement enclosing = (TypeElement) element.getEnclosingElement();
                    methodsByClass.computeIfAbsent(enclosing, k -> new ArrayList<>()).add(node);
                }
                case CLASS, INTERFACE -> classesWithPermission.add((TypeElement) element);
                case PACKAGE -> packageNodes.add(node);
                default -> {}
            }
        }

        // Generate companion classes for classes that have method-level permissions
        for (var entry : methodsByClass.entrySet()) {
            TypeElement clazz = entry.getKey();
            List<ResolvedNode> methods = entry.getValue();

            // Check if the class itself also has @Permission
            ResolvedNode classNode = null;
            if (classesWithPermission.contains(clazz)) {
                String classKey = clazz.getQualifiedName().toString();
                classNode = resolvedNodes.get(classKey);
                classesWithPermission.remove(clazz); // handled here, don't generate twice
            }

            generateClassCompanion(clazz, classNode, methods);
        }

        // Generate for class-level permissions that had no method-level children
        for (TypeElement clazz : classesWithPermission) {
            String classKey = clazz.getQualifiedName().toString();
            ResolvedNode classNode = resolvedNodes.get(classKey);
            if (classNode != null) {
                generateClassCompanion(clazz, classNode, List.of());
            }
        }

        // Generate for package-level permissions
        for (ResolvedNode node : packageNodes) {
            generatePackageCompanion((PackageElement) node.element(), node);
        }
    }

    private void generateClassCompanion(TypeElement clazz, ResolvedNode classNode,
                                        List<ResolvedNode> methodNodes) {
        String className = clazz.getSimpleName().toString();
        String packageName = elementUtils.getPackageOf(clazz).getQualifiedName().toString();
        String companionName = className + "Permissions";
        String qualifiedCompanionName = packageName + "." + companionName;

        try {
            JavaFileObject file = filer.createSourceFile(qualifiedCompanionName, clazz);
            try (PrintWriter out = new PrintWriter(file.openWriter())) {
                out.println("package " + packageName + ";");
                out.println();
                out.println("import javax.annotation.processing.Generated;");
                out.println();
                out.println("/**");
                out.println(" * Generated permission constants for {@link " + className + "}.");
                out.println(" *");
                out.println(" * <p>Do not edit manually. Regenerated on every compilation.</p>");
                out.println(" */");
                out.println("@Generated(\"" + PermissionAnnotationProcessor.class.getName() + "\")");
                out.println("public final class " + companionName + " {");
                out.println();

                // Class-level constant
                if (classNode != null) {
                    writeConstant(out, classNode);
                }

                // Method-level constants
                for (ResolvedNode node : methodNodes) {
                    writeConstant(out, node);
                }

                out.println("    private " + companionName + "() {}");
                out.println("}");
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate " + companionName + ": " + e.getMessage(), clazz);
        }
    }

    private void generatePackageCompanion(PackageElement pkg, ResolvedNode node) {
        String packageName = pkg.getQualifiedName().toString();
        String lastSegment = packageName.substring(packageName.lastIndexOf('.') + 1);
        String companionName = capitalize(lastSegment) + "Permissions";
        String qualifiedCompanionName = packageName + "." + companionName;

        try {
            JavaFileObject file = filer.createSourceFile(qualifiedCompanionName, pkg);
            try (PrintWriter out = new PrintWriter(file.openWriter())) {
                out.println("package " + packageName + ";");
                out.println();
                out.println("import javax.annotation.processing.Generated;");
                out.println();
                out.println("/**");
                out.println(" * Generated permission constant for package {@code " + packageName + "}.");
                out.println(" *");
                out.println(" * <p>Do not edit manually. Regenerated on every compilation.</p>");
                out.println(" */");
                out.println("@Generated(\"" + PermissionAnnotationProcessor.class.getName() + "\")");
                out.println("public final class " + companionName + " {");
                out.println();
                writeConstant(out, node);
                out.println("    private " + companionName + "() {}");
                out.println("}");
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate " + companionName + ": " + e.getMessage(), pkg);
        }
    }

    private void writeConstant(PrintWriter out, ResolvedNode node) {
        if (!node.description().isEmpty()) {
            out.println("    /** " + node.description() + " — {@code \"" + node.dotPath() + "\"} */");
        } else {
            out.println("    /** {@code \"" + node.dotPath() + "\"} */");
        }
        out.println("    public static final String " +
                toConstantName(node.key()) + " = \"" + node.dotPath() + "\";");
        out.println();
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

    /**
     * Getting a Class value from an annotation at compile time throws MirroredTypeException.
     * This is the standard approach to obtain the TypeMirror representation.
     */
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

    private static String toConstantName(String key) {
        return key.toUpperCase().replace('-', '_').replace('.', '_');
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}