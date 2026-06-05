package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class B2bCollaborationPolicy implements PermissionPolicy {

    private final CollaborationPermissionRepository collaborationPermissionRepository;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx) || !ctx.isB2B()) {
            return Decision.ABSTAIN;
        }

        if (ctx.collaborationId() == null) return Decision.DENIED;

        boolean isGranted = collaborationPermissionRepository.existsByCollaborationIdAndPermissionCode(
            ctx.collaborationId(), requested.path());

        return isGranted ? Decision.GRANTED : Decision.DENIED;
    }
}
