package com.hiveapp.admin.domain.repository;

import com.hiveapp.admin.domain.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    List<AdminUser> findByIsActiveTrue();

    @Query("SELECT au FROM AdminUser au LEFT JOIN FETCH au.adminUserRoles aur LEFT JOIN FETCH aur.adminRole WHERE au.id = :id")
    Optional<AdminUser> findByIdWithRoles(@Param("id") UUID id);

    @Query("SELECT au FROM AdminUser au LEFT JOIN FETCH au.adminUserRoles aur LEFT JOIN FETCH aur.adminRole WHERE au.userId = :userId")
    Optional<AdminUser> findByUserIdWithRoles(@Param("userId") UUID userId);
}
