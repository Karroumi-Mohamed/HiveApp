package com.hiveapp.member.domain.repository;

import com.hiveapp.member.domain.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByUserIdAndAccountId(UUID userId, UUID accountId);

    List<Member> findByAccountIdAndIsActiveTrue(UUID accountId);

    List<Member> findByAccountId(UUID accountId);

    List<Member> findByUserId(UUID userId);

    boolean existsByUserIdAndAccountId(UUID userId, UUID accountId);

    long countByAccountIdAndIsActiveTrue(UUID accountId);

    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.memberRoles WHERE m.id = :id")
    Optional<Member> findByIdWithRoles(@Param("id") UUID id);

    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.memberRoles WHERE m.userId = :userId AND m.accountId = :accountId")
    Optional<Member> findByUserIdAndAccountIdWithRoles(@Param("userId") UUID userId, @Param("accountId") UUID accountId);

    @Query("SELECT m FROM Member m WHERE m.accountId = :accountId AND m.isOwner = true")
    Optional<Member> findOwnerByAccountId(@Param("accountId") UUID accountId);
}
