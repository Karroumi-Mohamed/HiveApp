package com.hiveapp.admin.domain.repository;

import com.hiveapp.admin.domain.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminRoleRepository extends JpaRepository<AdminRole, UUID> {

    List<AdminRole> findByIsActiveTrue();

    @Query("SELECT ar FROM AdminRole ar LEFT JOIN FETCH ar.adminRolePermissions arp LEFT JOIN FETCH arp.adminPermission WHERE ar.id = :id")
    Optional<AdminRole> findByIdWithPermissions(@Param("id") UUID id);

    @Query("SELECT ar FROM AdminRole ar LEFT JOIN FETCH ar.adminRolePermissions arp LEFT JOIN FETCH arp.adminPermission WHERE ar.isActive = true")
    List<AdminRole> findAllActiveWithPermissions();
}
