package com.hiveapp.platform.client.account.domain.repository;
import com.hiveapp.platform.client.account.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findAllByAccountId(UUID accountId);
    long countByAccountIdAndIsActiveTrue(UUID accountId);
    Optional<Company> findByIdAndAccountId(UUID id, UUID accountId);
    boolean existsByAccountIdAndCountryAndTaxId(UUID accountId, String country, String taxId);
    boolean existsByAccountIdAndCountryAndTaxIdAndIdNot(UUID accountId, String country, String taxId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select company from Company company where company.id = :id and company.account.id = :accountId")
    Optional<Company> findByIdAndAccountIdForUpdate(
            @Param("id") UUID id,
            @Param("accountId") UUID accountId);
}
