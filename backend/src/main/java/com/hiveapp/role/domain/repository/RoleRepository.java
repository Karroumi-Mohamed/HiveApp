package com.hiveapp.role.domain.repository;

import com.hiveapp.role.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByAccountIdAndIsActiveTrue(UUID accountId);

    List<Role> findByAccountId(UUID accountId);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.rolePermissions WHERE r.id = :id")
    Optional<Role> findByIdWithPermissions(@Param("id") UUID id);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.rolePermissions WHERE r.id IN :ids")
    List<Role> findByIdsWithPermissions(@Param("ids") Set<UUID> ids);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.rolePermissions WHERE r.accountId = :accountId AND r.isActive = true")
    List<Role> findByAccountIdWithPermissions(@Param("accountId") UUID accountId);
}
