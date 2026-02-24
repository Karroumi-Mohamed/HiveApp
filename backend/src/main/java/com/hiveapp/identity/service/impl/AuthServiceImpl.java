package com.hiveapp.identity.service.impl;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiveapp.identity.domain.entity.User;
import com.hiveapp.identity.domain.repository.UserRepository;
import com.hiveapp.identity.dto.AuthResponse;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RefreshTokenRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.identity.event.UserRegisteredEvent;
import com.hiveapp.identity.service.AuthService;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    final private UserRepository userRepository;
    final private PasswordEncoder passwordEncoder;
    final private JwtTokenProvider jwtTokenProvider;
    final private AuthenticationManager authenticationManager;
    final private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", "Email", request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .build();

        userRepository.save(user);
        log.info("Registered new user: {}", user.getEmail());

        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail()));
        return issueTokens(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Invalid credentials"));

        log.info("User logged in: {}", user.getEmail());
        return issueTokens(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw new BusinessException("Invalid or expired refresh token");
        }

        var userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken());
                var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        log.info("Token refreshed for user: {}", user.getEmail());
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        var claims = Map.<String, Object>of("tokenType", "CLIENT");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), claims);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        long expiresIn = jwtTokenProvider.getAccessTokenExpiration();
        return AuthResponse.of(accessToken, refreshToken, expiresIn);
    }

}
