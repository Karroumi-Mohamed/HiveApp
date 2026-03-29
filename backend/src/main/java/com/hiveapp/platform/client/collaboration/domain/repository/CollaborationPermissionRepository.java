package com.hiveapp.platform.client.collaboration.domain.repository;
import com.hiveapp.platform.client.collaboration.domain.entity.CollaborationPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface CollaborationPermissionRepository extends JpaRepository<CollaborationPermission, UUID> {
    List<CollaborationPermission> findAllByCollaborationId(UUID collaborationId);
}
