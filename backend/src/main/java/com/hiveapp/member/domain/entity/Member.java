package com.hiveapp.member.domain.entity;

import com.hiveapp.permission.engine.IPermissionActor;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "members")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member extends BaseEntity implements IPermissionActor {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "is_owner", nullable = false)
    @Builder.Default
    private boolean isOwner = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MemberRole> memberRoles = new ArrayList<>();

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void addRole(MemberRole memberRole) {
        memberRoles.add(memberRole);
        memberRole.setMember(this);
    }

    public void removeRole(MemberRole memberRole) {
        memberRoles.remove(memberRole);
        memberRole.setMember(null);
    }

    // --- IPermissionActor ---

    @Override
    public UUID getActorAccountId() {
        return this.accountId;
    }

    @Override
    public boolean isActorActive() {
        return this.isActive;
    }
}
