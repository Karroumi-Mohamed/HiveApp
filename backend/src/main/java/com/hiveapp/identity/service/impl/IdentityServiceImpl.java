package com.hiveapp.identity.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.service.IdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdentityServiceImpl implements IdentityService {
    private final UserRepository userRepository;

    @Override
    public Optional<User> getUserById(UUID id) {
        return userRepository.findById(id);
    }
}
