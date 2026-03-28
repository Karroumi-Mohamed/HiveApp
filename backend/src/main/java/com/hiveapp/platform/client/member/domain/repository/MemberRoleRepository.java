package com.hiveapp.platform.client.member.domain.repository;
import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
public interface MemberRoleRepository extends JpaRepository<MemberRole, UUID> {
    List<MemberRole> findAllByMemberId(UUID memberId);
}
