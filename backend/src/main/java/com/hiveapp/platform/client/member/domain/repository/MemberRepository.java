package com.hiveapp.platform.client.member.domain.repository;
import com.hiveapp.platform.client.member.domain.entity.Member;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
public interface MemberRepository extends JpaRepository<Member, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Member> {
    List<Member> findAllByAccountId(UUID accountId);

    /**
     * Read model for the member list surface. {@code MemberMapper} projects identity fields
     * from the associated user, so the graph loads it up front instead of initializing one
     * proxy per member. Callers that only need the user id keep using
     * {@link #findAllByAccountId(UUID)}, which reads the id straight off the proxy.
     */
    @EntityGraph(attributePaths = "user")
    List<Member> findWithUserByAccountId(UUID accountId);
    long countByAccountIdAndIsActiveTrue(UUID accountId);
    long countByAccountIdAndIsActiveTrueAndIsOwnerFalse(UUID accountId);
    Optional<Member> findByIdAndAccountId(UUID id, UUID accountId);
    Optional<Member> findByAccountIdAndUserId(UUID accountId, UUID userId);
    Optional<Member> findByUserIdAndIsActiveTrue(UUID userId);
    Optional<Member> findByAccount_SlugAndEmployeeNumber(String accountSlug, String employeeNumber);
    boolean existsByAccountIdAndUserId(UUID accountId, UUID userId);
    boolean existsByUserId(UUID userId);
    boolean existsByUserIdAndIsActiveTrue(UUID userId);
    boolean existsByAccountIdAndEmployeeNumber(UUID accountId, String employeeNumber);
}
