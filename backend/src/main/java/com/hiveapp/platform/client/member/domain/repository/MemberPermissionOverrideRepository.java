package com.hiveapp.platform.client.member.domain.repository;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface MemberPermissionOverrideRepository extends JpaRepository<MemberPermissionOverride, UUID> {
    List<MemberPermissionOverride> findAllByMemberIdAndCompanyId(UUID memberId, UUID companyId);
    Optional<MemberPermissionOverride> findByMemberIdAndCompanyIdAndPermissionId(UUID memberId, UUID companyId, UUID permissionId);
}
