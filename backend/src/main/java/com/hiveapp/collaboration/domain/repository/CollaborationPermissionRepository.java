package com.hiveapp.collaboration.domain.repository;

import com.hiveapp.collaboration.domain.entity.CollaborationPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface CollaborationPermissionRepository extends JpaRepository<CollaborationPermission, UUID> {

    @Query("SELECT cp.permissionId FROM CollaborationPermission cp WHERE cp.collaboration.id = :collaborationId")
    Set<UUID> findPermissionIdsByCollaborationId(@Param("collaborationId") UUID collaborationId);
}
