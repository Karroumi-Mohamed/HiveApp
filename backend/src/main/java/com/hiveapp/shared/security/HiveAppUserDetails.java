package com.hiveapp.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;

@Getter
public class HiveAppUserDetails implements UserDetails {
    private final UUID userId;
    private final String email;
    private final String password;
    private final boolean active;

    public HiveAppUserDetails(UUID userId, String email, String password, boolean active){
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.active = active;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        // FIXME: This is a placeholder. In a real application, authorities should be loaded from the database or another source.
        return List.of(new SimpleGrantedAuthority("platform.other"));
    }

     @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername(){
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
