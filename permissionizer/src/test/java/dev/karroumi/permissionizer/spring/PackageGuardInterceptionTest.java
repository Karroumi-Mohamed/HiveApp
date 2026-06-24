package dev.karroumi.permissionizer.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.permissionizer.guarded.PackageGuardedService;
import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionPolicy;
import dev.karroumi.permissionizer.PermissionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class PackageGuardInterceptionTest {

    @BeforeEach
    void setUp() {
        PermissionGuard.reset();
        PermissionResolver.clearCache();
        PermissionGuard.builder()
                .addPolicy((permission, context) -> PermissionPolicy.Decision.DENIED)
                .skipVerification()
                .initialize();
    }

    @AfterEach
    void tearDown() {
        PermissionGuard.reset();
        PermissionResolver.clearCache();
    }

    @Test
    void confirmsPackageGuardIsResolvedButNotReachedByCurrentSpringPointcut() throws Exception {
        assertTrue(PermissionResolver.resolve(
                PackageGuardedService.class.getMethod("execute")).shouldCheck());

        AspectJProxyFactory factory = new AspectJProxyFactory(new PackageGuardedService());
        factory.addAspect(PermissionInterceptor.class);
        PackageGuardedService service = factory.getProxy();

        assertEquals("executed", service.execute(),
                "Confirmed PERM-001 gap: package resolution exists but the current Spring pointcut is never entered");
    }
}
