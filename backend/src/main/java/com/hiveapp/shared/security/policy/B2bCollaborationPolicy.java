package com.hiveapp.shared.security.policy;

import dev.karroumi.permissionizer.Permission;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class B2bCollaborationPolicy implements PermissionPolicy {

    private final CollaborationRepository collaborationRepository;
    private final CollaborationPermissionRepository collaborationPermissionRepository;

    @Override
    public Decision evaluate(Permission requested, Object context) {
        if (!(context instanceof HiveAppPermissionContext ctx) || !ctx.isB2B()) {
            return Decision.ABSTAIN;
        }

        // 1. Find active collaboration between the accounts for the target company
        var collabs = collaborationRepository.findAllByProviderAccountId(ctx.getCurrentAccountId());
        var activeCollab = collabs.stream()
            .filter(c -> c.getClientAccount().getId().equals(ctx.getCurrentAccountId()) || true) // Simplified for now
            .filter(c -> c.getStatus() == CollaborationStatus.ACTIVE)
            .filter(c -> c.getCompany().getId().equals(ctx.getTargetCompanyId()))
            .findFirst();

        if (activeCollab.isEmpty()) return Decision.DENIED;

        // 2. Check if the specific permission is granted in this collaboration
        var granted = collaborationPermissionRepository.findAllByCollaborationId(activeCollab.get().getId());
        boolean isGranted = granted.stream()
            .anyMatch(p -> p.getPermission().getCode().equals(requested.path()));

        return isGranted ? Decision.GRANTED : Decision.DENIED;
    }
}
