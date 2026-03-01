package com.hiveapp.identity.domain.entity;

import com.hiveapp.shared.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
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

    public void deactivate() {
        this.isActive = false;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
