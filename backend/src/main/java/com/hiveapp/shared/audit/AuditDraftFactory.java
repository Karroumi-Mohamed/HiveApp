package com.hiveapp.shared.audit;

import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.security.HiveAppUserDetails;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import dev.karroumi.permissionizer.PermissionNode;
import dev.karroumi.permissionizer.PermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class AuditDraftFactory {

    private final AuditPayloadSanitizer payloadSanitizer;

    AuditDraft create(ProceedingJoinPoint joinPoint, PermissionNode permissionNode) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        return create(joinPoint, method, action(method, permissionNode), resourceType(joinPoint.getTarget().getClass()));
    }

    AuditDraft create(ProceedingJoinPoint joinPoint, AuditedMutation auditedMutation) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        return create(joinPoint, method, auditedMutation.action(), auditedMutation.resourceType());
    }

    private AuditDraft create(
            ProceedingJoinPoint joinPoint,
            Method method,
            String action,
            String resourceType
    ) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] arguments = joinPoint.getArgs();
        String[] parameterNames = signature.getParameterNames();
        HiveAppPermissionContext context = HiveAppContextHolder.getContext();
        HttpServletRequest request = currentRequest();
        UUID actorUserId = actorUserId(context);

        return new AuditDraft(
                actorSurface(request, context, actorUserId),
                actorUserId,
                context == null ? null : context.clientAccountId(),
                targetAccountId(method, arguments, context),
                targetCompanyId(method, arguments, context),
                collaborationId(method, arguments, context),
                action,
                resourceType,
                fallbackResourceId(method, arguments),
                request == null ? null : request.getMethod(),
                request == null ? null : request.getRequestURI(),
                payloadSanitizer.arguments(method, arguments, parameterNames));
    }

    private UUID actorUserId(HiveAppPermissionContext context) {
        if (context != null && context.actorUserId() != null) return context.actorUserId();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof HiveAppUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    private AuditActorSurface actorSurface(
            HttpServletRequest request,
            HiveAppPermissionContext context,
            UUID actorUserId
    ) {
        if (actorUserId == null) return AuditActorSurface.SYSTEM;
        if (request != null && request.getRequestURI().startsWith("/api/admin")) {
            return AuditActorSurface.PLATFORM_ADMIN;
        }
        if (context != null && (context.currentAccountId() != null || context.clientAccountId() != null)) {
            return AuditActorSurface.CLIENT_WORKSPACE;
        }
        return AuditActorSurface.PLATFORM_ADMIN;
    }

    private String action(Method method, PermissionNode annotation) {
        String resolved = PermissionResolver.resolve(method).path();
        if (resolved != null && method.getAnnotation(PermissionNode.class) == null) {
            return resolved + "." + method.getName();
        }
        if (resolved != null) return resolved;
        String key = annotation.key().isBlank() ? method.getName() : annotation.key();
        return method.getDeclaringClass().getSimpleName() + "." + key;
    }

    private String resourceType(Class<?> targetClass) {
        String name = targetClass.getSimpleName()
                .replace("ServiceImpl", "")
                .replace("Service", "");
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private UUID targetAccountId(Method method, Object[] arguments, HiveAppPermissionContext context) {
        UUID explicit = uuidArgument(method, arguments, "accountId");
        if (explicit != null) return explicit;
        if (context == null) return null;
        return context.currentAccountId() != null ? context.currentAccountId() : context.clientAccountId();
    }

    private UUID targetCompanyId(Method method, Object[] arguments, HiveAppPermissionContext context) {
        UUID explicit = uuidArgument(method, arguments, "companyId");
        return explicit != null ? explicit : context == null ? null : context.targetCompanyId();
    }

    private UUID collaborationId(Method method, Object[] arguments, HiveAppPermissionContext context) {
        UUID explicit = uuidArgument(method, arguments, "collaborationId");
        return explicit != null ? explicit : context == null ? null : context.collaborationId();
    }

    private String fallbackResourceId(Method method, Object[] arguments) {
        var parameters = method.getParameters();
        for (int index = 0; index < parameters.length && index < arguments.length; index++) {
            String name = parameters[index].getName().toLowerCase(Locale.ROOT);
            if (arguments[index] instanceof UUID uuid
                    && name.endsWith("id")
                    && !name.equals("actoruserid")
                    && !name.equals("accountid")
                    && !name.equals("clientaccountid")
                    && !name.equals("provideraccountid")
                    && !name.equals("companyid")) {
                return uuid.toString();
            }
        }
        for (Object argument : arguments) {
            if (argument instanceof UUID uuid) return uuid.toString();
        }
        return null;
    }

    private UUID uuidArgument(Method method, Object[] arguments, String requestedName) {
        var parameters = method.getParameters();
        for (int index = 0; index < parameters.length && index < arguments.length; index++) {
            if (requestedName.equals(parameters[index].getName()) && arguments[index] instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
