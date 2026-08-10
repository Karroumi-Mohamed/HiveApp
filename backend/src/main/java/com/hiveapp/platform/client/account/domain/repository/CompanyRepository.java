package com.hiveapp.platform.client.account.domain.repository;
import com.hiveapp.platform.client.account.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findAllByAccountId(UUID accountId);
    long countByAccountIdAndIsActiveTrue(UUID accountId);
    Optional<Company> findByIdAndAccountId(UUID id, UUID accountId);
    Optional<Company> findByB2bShareCodeHashAndB2bShareEnabledTrue(String shareCodeHash);
    boolean existsByAccountIdAndCountryAndTaxId(UUID accountId, String country, String taxId);
    boolean existsByAccountIdAndCountryAndTaxIdAndIdNot(UUID accountId, String country, String taxId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select company from Company company where company.id = :id and company.account.id = :accountId")
    Optional<Company> findByIdAndAccountIdForUpdate(
            @Param("id") UUID id,
            @Param("accountId") UUID accountId);

    @Modifying
    @Query("update Company company "
            + "set company.b2bShareResolutionCount = company.b2bShareResolutionCount + 1, "
            + "company.b2bShareLastResolvedAt = :occurredAt "
            + "where company.id = :companyId "
            + "and company.b2bShareCodeHash = :expectedHash "
            + "and company.b2bShareEnabled = true")
    int recordB2bShareResolution(
            @Param("companyId") UUID companyId,
            @Param("expectedHash") String expectedHash,
            @Param("occurredAt") Instant occurredAt);

    @Modifying
    @Query("update Company company "
            + "set company.b2bShareRequestCount = company.b2bShareRequestCount + 1, "
            + "company.b2bShareLastRequestedAt = :occurredAt "
            + "where company.id = :companyId "
            + "and company.b2bShareCodeHash = :expectedHash "
            + "and company.b2bShareEnabled = true")
    int recordB2bShareRequest(
            @Param("companyId") UUID companyId,
            @Param("expectedHash") String expectedHash,
            @Param("occurredAt") Instant occurredAt);
}
