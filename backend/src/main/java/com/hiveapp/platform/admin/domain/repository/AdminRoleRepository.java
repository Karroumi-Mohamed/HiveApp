package com.hiveapp.platform.admin.domain.repository;
import com.hiveapp.platform.admin.domain.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AdminRoleRepository extends JpaRepository<AdminRole, UUID> {}
