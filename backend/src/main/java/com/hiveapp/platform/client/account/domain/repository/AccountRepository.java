package com.hiveapp.platform.client.account.domain.repository;

import com.hiveapp.platform.client.account.domain.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {
    Optional<Account> findByOwner_Id(UUID ownerId);

    @Query("select count(account) > 0 from Account account where account.id = :id and account.isActive = true")
    boolean existsActiveById(@Param("id") UUID id);

    List<Account> findAllByIdInOrderByNameAscIdAsc(Collection<UUID> ids);

    @EntityGraph(attributePaths = "owner")
    @Query("select account from Account account where account.id in :ids")
    List<Account> findAllWithOwnerByIdIn(@Param("ids") Collection<UUID> ids);

    @Query("select account.owner.email from Account account where account.id = :accountId")
    Optional<String> findOwnerEmailById(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.id = :accountId")
    Optional<Account> findByIdForQuotaUpdate(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.id = :accountId")
    Optional<Account> findByIdForSubscriptionUpdate(@Param("accountId") UUID accountId);
}
