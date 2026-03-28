package com.hiveapp.platform.client.account.domain.repository;
import com.hiveapp.platform.client.account.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findAllByAccountId(UUID accountId);
}
