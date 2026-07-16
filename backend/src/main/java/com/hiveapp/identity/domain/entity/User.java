package com.hiveapp.identity.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;
import com.hiveapp.identity.domain.EmailIdentity;
import com.hiveapp.identity.domain.constant.CredentialState;
import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(unique = true)
    private String email;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    private String passwordHash;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(length = 50)
    private String phone;


    @Column(length = 500)
    private String avatarUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_state", nullable = false, length = 40)
    @Builder.Default
    private CredentialState credentialState = CredentialState.ACTIVE;

    @Column(name = "password_change_required", nullable = false)
    @Builder.Default
    private boolean passwordChangeRequired = false;

    @Column(name = "credential_token_hash", unique = true, length = 64)
    private String credentialTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_token_purpose", length = 30)
    private CredentialTokenPurpose credentialTokenPurpose;

    @Column(name = "credential_token_expires_at")
    private Instant credentialTokenExpiresAt;

    @Column(name = "initial_access_failed_attempts", nullable = false)
    @Builder.Default
    private int initialAccessFailedAttempts = 0;

    @Column(name = "initial_access_locked", nullable = false)
    @Builder.Default
    private boolean initialAccessLocked = false;

    public void deactivate() {
        this.isActive = false;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    @PrePersist
    @PreUpdate
    void canonicalizeEmail() {
        email = EmailIdentity.canonicalize(email);
        username = username == null ? null : username.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
