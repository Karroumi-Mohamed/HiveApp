package dev.karroumi.permissionizer.agent;

import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionResolver;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;

/**
 * Byte Buddy advice injected at the entry of instrumented methods.
 *
 * <p>
 * This class is never called directly. Byte Buddy inlines its code
 * into the bytecode of targeted methods at instrumentation time.
 * The {@code @Advice.OnMethodEnter} method runs before the original
 * method body executes.
 * </p>
 *
 * <p>
 * The advice delegates to {@link PermissionResolver} to determine
 * if a check is needed and what permission to check. If the resolver
 * says check, it calls {@link PermissionGuard#check}. The guard
 * handles denial (throw, dry-run log, listener callback).
 * </p>
 *
 * <p>
 * Performance: the resolver caches results per method. After the
 * first invocation, the advice is a map lookup + prefix match.
 * The advice itself is inlined — no method call overhead, no proxy,
 * no reflection on subsequent calls.
 * </p>
 */
public final class GuardAdvice {

    private GuardAdvice() {
    }

    /**
     * Runs before the instrumented method body.
     * Resolves the permission and checks if enforcement is needed.
     *
     * @param method the method being entered, provided by Byte Buddy
     */
    @Advice.OnMethodEnter
    public static void enter(@Advice.Origin Method method) {
        PermissionResolver.Result result = PermissionResolver.resolve(method);
        if (result.shouldCheck()) {
            PermissionGuard.check(result.permission());
        }
    }
}
