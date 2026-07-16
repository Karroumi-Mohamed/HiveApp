package com.hiveapp.platform.client.collaboration.domain.repository;

import com.hiveapp.platform.client.collaboration.domain.entity.CollaborationPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;
import java.util.List;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;

public interface CollaborationPermissionRepository extends JpaRepository<CollaborationPermission, UUID> {
    List<CollaborationPermission> findAllByCollaborationId(UUID collaborationId);
    List<CollaborationPermission> findAllByCollaborationCompanyIdAndCollaborationStatus(
            UUID companyId, CollaborationStatus status);

    @Query("SELECT COUNT(cp) > 0 FROM CollaborationPermission cp WHERE cp.collaboration.id = :collaborationId AND cp.permission.code = :permissionCode")
    boolean existsByCollaborationIdAndPermissionCode(UUID collaborationId, String permissionCode);
    
    boolean existsByCollaborationIdAndPermissionId(UUID collaborationId, UUID permissionId);
    java.util.Optional<CollaborationPermission> findByCollaborationIdAndPermissionId(UUID collaborationId, UUID permissionId);
}
