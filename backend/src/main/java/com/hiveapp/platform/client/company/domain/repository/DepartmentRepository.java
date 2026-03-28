package com.hiveapp.platform.client.company.domain.repository;
import com.hiveapp.platform.client.company.domain.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findAllByCompanyId(UUID companyId);
}
