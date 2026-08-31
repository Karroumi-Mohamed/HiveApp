package com.hiveapp.identity.service.impl;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.EmailIdentity;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.service.IdentityService;
import com.hiveapp.identity.service.NewUserCommand;
import com.hiveapp.identity.service.UserView;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class IdentityServiceImpl implements IdentityService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserView> findUserView(UUID id) {
        return userRepository.findById(id).map(IdentityServiceImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserView> findUserViews(java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return userRepository.findAllById(new java.util.LinkedHashSet<>(ids)).stream()
                .map(IdentityServiceImpl::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public User createUser(NewUserCommand command) {
        User user = new User();
        user.setUsername(command.username());
        user.setEmail(command.email());
        user.setFirstName(command.firstName());
        user.setLastName(command.lastName());
        user.setPhone(command.phone());
        user.setPasswordHash(command.passwordHash());
        user.setActive(command.active());
        user.setEmailVerified(command.emailVerified());
        user.setKind(command.kind());
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Identity owns its uniqueness rules, so it also owns translating their violation.
            throw new InvalidStateException("Username or email is already in use");
        }
    }

    @Override
    @Transactional
    public UserView renameUser(UUID userId, String firstName, String lastName) {
        User user = requireManagedUser(userId);
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        return toView(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional
    public boolean changeEmail(UUID userId, String email) {
        User user = requireManagedUser(userId);
        String canonical = EmailIdentity.canonicalize(email);
        if (canonical == null || canonical.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (canonical.equals(user.getEmail())) {
            return false;
        }
        userRepository.findByEmail(canonical)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("User", "email", canonical);
                });
        user.setEmail(canonical);
        user.setEmailVerified(false);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("User", "email", canonical);
        }
        return true;
    }

    @Override
    public User requireManagedUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private static UserView toView(User user) {
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isActive());
    }
}
