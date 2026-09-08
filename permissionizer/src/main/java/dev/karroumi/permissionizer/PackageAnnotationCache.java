package dev.karroumi.permissionizer;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Static package metadata only. Neither keys nor annotation values retain an application loader. */
final class PackageAnnotationCache {
    private static final Entry MISSING = new Entry(null);
    private final ReferenceQueue<ClassLoader> retiredLoaders = new ReferenceQueue<>();
    private final ConcurrentMap<LoaderKey, ConcurrentMap<String, Entry>> loaders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Entry> bootstrap = new ConcurrentHashMap<>();

    PermissionNode get(String packageName, ClassLoader loader) {
        LoaderKey retired;
        while ((retired = (LoaderKey) retiredLoaders.poll()) != null) loaders.remove(retired);
        ConcurrentMap<String, Entry> packages = loader == null ? bootstrap
                : loaders.computeIfAbsent(new LoaderKey(loader, retiredLoaders), ignored -> new ConcurrentHashMap<>());
        Entry cached = packages.get(packageName);
        if (cached == MISSING) return null;
        PermissionNode annotation = cached == null ? null : cached.annotation().get();
        if (annotation != null) return annotation;

        // Only cold misses serialize, per loader. Warm lookups require no monitor. Weak values
        // avoid the weak-key/strong-value cycle when an annotation's parent class belongs to loader.
        synchronized (packages) {
            cached = packages.get(packageName);
            if (cached == MISSING) return null;
            annotation = cached == null ? null : cached.annotation().get();
            if (annotation != null) return annotation;
            try {
                Class<?> info = Class.forName(packageName + ".package-info", false, loader);
                annotation = info.getAnnotation(PermissionNode.class);
            } catch (ClassNotFoundException missing) {
                annotation = null;
            }
            // Do not cache linkage, security or other discovery failures as an absent guard.
            packages.put(packageName, annotation == null ? MISSING : new Entry(new WeakReference<>(annotation)));
            return annotation;
        }
    }

    void clear() {
        loaders.clear();
        bootstrap.clear();
        while (retiredLoaders.poll() != null) { /* discard detached weak keys */ }
    }

    private record Entry(WeakReference<PermissionNode> annotation) {}

    private static final class LoaderKey extends WeakReference<ClassLoader> {
        private final int hash;

        private LoaderKey(ClassLoader loader, ReferenceQueue<ClassLoader> queue) {
            super(loader, queue);
            hash = System.identityHashCode(loader);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            ClassLoader loader = get();
            return this == other || loader != null && other instanceof LoaderKey key && loader == key.get();
        }
    }
}
