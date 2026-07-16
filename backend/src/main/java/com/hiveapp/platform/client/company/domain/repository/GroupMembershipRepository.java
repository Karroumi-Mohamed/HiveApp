package com.hiveapp.platform.client.company.domain.repository;

import com.hiveapp.platform.client.company.domain.entity.GroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {
    List<GroupMembership> findAllByGroupId(UUID groupId);
    List<GroupMembership> findAllByGroupIdIn(Collection<UUID> groupIds);
    List<GroupMembership> findAllByMemberIdAndGroupCompanyId(UUID memberId, UUID companyId);
    Optional<GroupMembership> findByGroupIdAndMemberId(UUID groupId, UUID memberId);
    long countByGroupId(UUID groupId);
}
