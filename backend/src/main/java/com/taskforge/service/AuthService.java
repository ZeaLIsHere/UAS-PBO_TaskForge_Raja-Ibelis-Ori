package com.taskforge.service;

import com.taskforge.dto.request.LoginRequest;
import com.taskforge.dto.request.RegisterRequest;
import com.taskforge.dto.response.AuthResponse;
import com.taskforge.dto.response.UserResponse;
import com.taskforge.exception.DuplicateResourceException;
import com.taskforge.model.User;
import com.taskforge.repository.UserRepository;
import com.taskforge.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email " + request.getEmail() + " sudah terdaftar");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .nim(request.getNim())
                .nipm(request.getNipm())
                .build();

        User saved = userRepository.save(user);
        log.info("User baru terdaftar: {} ({})", saved.getEmail(), saved.getRole());
        return UserResponse.from(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Email atau password salah"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Email atau password salah");
        }

        String roleAuthority = "ROLE_" + user.getRole().name();
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), roleAuthority);

        log.info("User login berhasil: {}", user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .expiresIn(86400)
                .user(UserResponse.from(user))
                .build();
    }
}
