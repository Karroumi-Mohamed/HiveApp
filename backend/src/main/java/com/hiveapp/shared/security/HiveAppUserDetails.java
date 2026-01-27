package com.hiveapp.shared.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

@Getter
public class HiveAppUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String password;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    // Member/Account context — set after authentication based on active account
    private UUID memberId;
    private UUID accountId;

    public HiveAppUserDetails(
            UUID userId,
            String email,
            String password,
            boolean active,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.active = active;
        this.authorities = authorities;
    }

    public void setMemberContext(UUID memberId, UUID accountId) {
        this.memberId = memberId;
        this.accountId = accountId;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
