package com.hiveapp.identity.service;

import com.hiveapp.identity.domain.entity.User;
import java.util.UUID;
import java.util.Optional;

public interface IdentityService {
    Optional<User> getUserById(UUID id);
}
