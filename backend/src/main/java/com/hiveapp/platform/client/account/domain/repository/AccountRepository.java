package com.hiveapp.platform.client.account.domain.repository;

import com.hiveapp.platform.client.account.domain.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    boolean existsByOwnerId(UUID ownerId);
    Optional<Account> findByOwnerId(UUID ownerId);
    boolean existsBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.id = :accountId")
    Optional<Account> findByIdForSubscriptionUpdate(@Param("accountId") UUID accountId);
}
