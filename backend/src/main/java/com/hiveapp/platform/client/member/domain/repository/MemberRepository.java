package com.hiveapp.platform.client.member.domain.repository;
import com.hiveapp.platform.client.member.domain.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
public interface MemberRepository extends JpaRepository<Member, UUID> {
    List<Member> findAllByAccountId(UUID accountId);
    Optional<Member> findByAccountIdAndUserId(UUID accountId, UUID userId);
    Optional<Member> findFirstByUserId(UUID userId);
}
