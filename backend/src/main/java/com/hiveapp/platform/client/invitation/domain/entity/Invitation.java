package com.hiveapp.platform.client.invitation.domain.entity;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.invitation.domain.constant.InvitationStatus;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "invitations", indexes = {
        @Index(name = "idx_invitations_token", columnList = "token", unique = true),
        @Index(name = "idx_invitations_account_email", columnList = "account_id, email")
})
@Getter
@Setter
public class Invitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private String email;

    /** Secure random 64-char hex token included in the invite link. */
    @Column(nullable = false, unique = true, length = 128)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Who sent this invitation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_member_id", nullable = false)
    private Member invitedBy;

    /** Optional — pre-assign this role to the accepted member. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    /** Optional — scope the role assignment to a specific company. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;
}
