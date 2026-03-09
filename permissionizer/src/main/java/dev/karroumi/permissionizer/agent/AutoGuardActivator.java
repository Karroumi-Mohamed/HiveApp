package dev.karroumi.permissionizer.agent;

import dev.karroumi.permissionizer.PermissionNode;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.logging.Logger;

/**
 * Activates Byte Buddy runtime instrumentation for automatic
 * permission checking on guarded methods.
 *
 * <p>
 * This class is loaded reflectively by
 * {@link dev.karroumi.permissionizer.PermissionGuard}
 * when {@code withAutoGuard()} is called. The reflective loading isolates
 * Byte Buddy imports — if Byte Buddy is not on the classpath, the guard
 * reports a clear error instead of a {@code NoClassDefFoundError}.
 * </p>
 *
 * <p>
 * Instrumentation targets all classes annotated with {@code @PermissionNode}
 * and all public methods within them. The {@link GuardAdvice} is injected
 * at the entry of each targeted method.
 * </p>
 *
 * <p>
 * Uses {@link AgentBuilder.RedefinitionStrategy#RETRANSFORMATION} to
 * modify classes that are already loaded. This means {@code enableAutoGuard()}
 * can be called at any point during startup — timing does not matter.
 * </p>
 */
public final class AutoGuardActivator {

    private static final Logger LOG = Logger.getLogger(AutoGuardActivator.class.getName());

    private AutoGuardActivator() {
    }

    /**
     * Installs the Byte Buddy agent and instruments all @PermissionNode
     * annotated classes. Called reflectively by PermissionGuard.
     */
    public static void activate() {
        // Self-attach the agent to the current JVM
        ByteBuddyAgent.install();

        AgentBuilder.Listener listener = new AgentBuilder.Listener.Adapter() {
            @Override
            public void onTransformation(
                    net.bytebuddy.description.type.TypeDescription typeDescription,
                    ClassLoader classLoader,
                    net.bytebuddy.utility.JavaModule module,
                    boolean loaded,
                    net.bytebuddy.dynamic.DynamicType dynamicType) {
                LOG.info("[Permissionizer] Instrumented: " + typeDescription.getName());
            }

            @Override
            public void onError(
                    String typeName,
                    ClassLoader classLoader,
                    net.bytebuddy.utility.JavaModule module,
                    boolean loaded,
                    Throwable throwable) {
                LOG.warning("[Permissionizer] Failed to instrument: "
                        + typeName + " — " + throwable.getMessage());
            }
        };

        new AgentBuilder.Default()
                // Retransformation allows modifying already-loaded classes
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                // Log what we touch
                .with(listener)
                // Target classes annotated with @PermissionNode
                .type(ElementMatchers.isAnnotatedWith(PermissionNode.class))
                .transform((builder, typeDescription, classLoader, module, domain) -> builder
                        .method(ElementMatchers.isPublic()
                                .and(ElementMatchers.not(ElementMatchers.isStatic()))
                                .and(ElementMatchers.not(ElementMatchers.isConstructor())))
                        .intercept(Advice.to(GuardAdvice.class)))
                // Also target methods directly annotated with @PermissionNode
                // in classes that are NOT annotated (method-only annotation)
                .type(ElementMatchers.declaresMethod(
                        ElementMatchers.isAnnotatedWith(PermissionNode.class)))
                .transform((builder, typeDescription, classLoader, module, domain) -> builder
                        .method(ElementMatchers.isAnnotatedWith(PermissionNode.class))
                        .intercept(Advice.to(GuardAdvice.class)))
                .installOnByteBuddyAgent();

        LOG.info("[Permissionizer] Auto-guard activated");
    }
}
