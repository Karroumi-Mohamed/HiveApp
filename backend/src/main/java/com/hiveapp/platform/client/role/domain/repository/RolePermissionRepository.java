package com.hiveapp.platform.client.role.domain.repository;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {}
