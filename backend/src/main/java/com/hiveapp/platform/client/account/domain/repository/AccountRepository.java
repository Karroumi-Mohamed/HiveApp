package com.hiveapp.platform.client.account.domain.repository;

import com.hiveapp.platform.client.account.domain.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {
    Optional<Account> findByOwner_Id(UUID ownerId);

    @EntityGraph(attributePaths = "owner")
    @Query(
            value = "select account from Account account join account.owner owner where "
                    + ":query is null or lower(account.name) like lower(concat('%', :query, '%')) "
                    + "or lower(account.slug) like lower(concat('%', :query, '%')) "
                    + "or lower(owner.email) like lower(concat('%', :query, '%'))",
            countQuery = "select count(account) from Account account join account.owner owner where "
                    + ":query is null or lower(account.name) like lower(concat('%', :query, '%')) "
                    + "or lower(account.slug) like lower(concat('%', :query, '%')) "
                    + "or lower(owner.email) like lower(concat('%', :query, '%'))")
    Page<Account> searchDirectory(@Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    List<Account> findAllByIdInOrderByNameAscIdAsc(Collection<UUID> ids);

    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Account> findAll(Specification<Account> specification, Pageable pageable);

    @Query("select account.owner.email from Account account where account.id = :accountId")
    Optional<String> findOwnerEmailById(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.id = :accountId")
    Optional<Account> findByIdForQuotaUpdate(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.id = :accountId")
    Optional<Account> findByIdForSubscriptionUpdate(@Param("accountId") UUID accountId);
}
