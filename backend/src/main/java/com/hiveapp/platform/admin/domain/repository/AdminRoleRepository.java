package com.hiveapp.platform.admin.domain.repository;
import com.hiveapp.platform.admin.domain.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface AdminRoleRepository extends JpaRepository<AdminRole, UUID> {
    long countByIsActiveTrue();
    long countByIsActiveFalse();

    @Query("select role from AdminRole role where (:active is null or role.isActive = :active) "
            + "and (:search is null or lower(role.name) like lower(concat('%', :search, '%')) "
            + "or lower(coalesce(role.description, '')) like lower(concat('%', :search, '%')))")
    Page<AdminRole> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);
}
