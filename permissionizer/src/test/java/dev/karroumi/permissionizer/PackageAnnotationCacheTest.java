package dev.karroumi.permissionizer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PackageAnnotationCacheTest {
    private static final String PACKAGE = "com.example.permissionizer.guarded";

    @Test
    void cachesBothPresentAnnotationsAndMissingPackageInfo() {
        var cache = new PackageAnnotationCache();
        var loader = new CountingLoader(false);
        for (int i = 0; i < 400; i++) {
            assertEquals("guarded", cache.get(PACKAGE, loader).key());
            assertNull(cache.get("nonexistent.permissionizer.fixture", loader));
        }
        assertEquals(2, loader.lookups.get());
    }

    @Test
    void doesNotSharePositiveOrNegativeEntriesAcrossContextLoaders() {
        var cache = new PackageAnnotationCache();
        var hidden = new CountingLoader(true);
        var visible = new CountingLoader(false);
        assertNull(cache.get(PACKAGE, hidden));
        assertEquals("guarded", cache.get(PACKAGE, visible).key());
        assertNull(cache.get(PACKAGE, hidden));
        assertEquals(1, hidden.lookups.get());
        assertEquals(1, visible.lookups.get());
    }

    @Test
    void concurrentColdLookupsPublishOneCompleteResult() throws Exception {
        var cache = new PackageAnnotationCache();
        var loader = new CountingLoader(false);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var results = new ArrayList<Future<PermissionNode>>();
            for (int i = 0; i < 32; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return cache.get(PACKAGE, loader);
                }));
            }
            start.countDown();
            for (var result : results) assertEquals("guarded", result.get(5, TimeUnit.SECONDS).key());
            assertEquals(1, loader.lookups.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cacheClearAllowsRediscoveryAndBootstrapMissIsSafe() {
        var cache = new PackageAnnotationCache();
        var loader = new CountingLoader(true);
        assertNull(cache.get(PACKAGE, loader));
        cache.clear();
        assertNull(cache.get(PACKAGE, loader));
        assertEquals(2, loader.lookups.get());
        assertNull(cache.get(PACKAGE, null));
    }

    @Test
    void discoveryErrorsPropagateAndAreNotCachedAsMissingGuards() {
        var cache = new PackageAnnotationCache();
        var attempts = new AtomicInteger();
        var loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(PACKAGE + ".package-info") && attempts.getAndIncrement() == 0) {
                    throw new SecurityException("denied metadata discovery");
                }
                return super.loadClass(name, resolve);
            }
        };
        assertThrows(SecurityException.class, () -> cache.get(PACKAGE, loader));
        assertEquals("guarded", cache.get(PACKAGE, loader).key());
    }

    private static final class CountingLoader extends ClassLoader {
        private final AtomicInteger lookups = new AtomicInteger();
        private final boolean hideMetadata;

        private CountingLoader(boolean hideMetadata) {
            super(PackageAnnotationCacheTest.class.getClassLoader());
            this.hideMetadata = hideMetadata;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.endsWith(".package-info")) {
                lookups.incrementAndGet();
                if (hideMetadata) throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
