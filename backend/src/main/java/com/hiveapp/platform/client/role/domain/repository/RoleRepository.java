package com.hiveapp.platform.client.role.domain.repository;
import com.hiveapp.platform.client.role.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findAllByAccountId(UUID accountId);
    List<Role> findAllByCompanyId(UUID companyId);
    Optional<Role> findByIdAndAccountId(UUID id, UUID accountId);
}
