package dev.karroumi.permissionizer.spring;

import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Spring AOP interceptor for automatic permission enforcement.
 *
 * <p>
 * Auto-registered by {@link PermissionizerAutoConfiguration} when
 * Spring AOP is on the classpath. No manual configuration needed.
 * </p>
 *
 * <p>
 * Intercepts two categories of methods:
 * </p>
 * <ul>
 * <li>Methods directly annotated with {@code @PermissionNode}</li>
 * <li>All public methods in classes annotated with {@code @PermissionNode}</li>
 * </ul>
 *
 * <p>
 * Delegates to {@link PermissionResolver} for path resolution and
 * guard status. If the resolver says skip, the method proceeds without
 * any permission check. This handles {@code guard = OFF} and unannotated
 * methods in non-autoDiscover classes.
 * </p>
 *
 * <p>
 * This interceptor is an alternative to the Byte Buddy agent for
 * Spring Boot applications. Both achieve the same result — automatic
 * permission checking. The Spring interceptor uses AOP proxies while
 * the agent uses bytecode instrumentation.
 * </p>
 */
@Aspect
public class PermissionInterceptor {

    private static final Logger LOG = Logger.getLogger(PermissionInterceptor.class.getName());

    /**
     * Intercepts methods annotated with @PermissionNode and all methods
     * in classes annotated with @PermissionNode.
     */
    @Around("@annotation(dev.karroumi.permissionizer.PermissionNode) || " +
            "@within(dev.karroumi.permissionizer.PermissionNode)")
    public Object enforce(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        PermissionResolver.Result result = PermissionResolver.resolve(method);

        if (result.shouldCheck()) {
            if (LOG.isLoggable(java.util.logging.Level.FINE)) {
                LOG.fine("[Permissionizer] Checking: " + result.path()
                        + " on " + method.getDeclaringClass().getSimpleName()
                        + "#" + method.getName());
            }
            PermissionGuard.check(result.permission());
        }

        return joinPoint.proceed();
    }
}
