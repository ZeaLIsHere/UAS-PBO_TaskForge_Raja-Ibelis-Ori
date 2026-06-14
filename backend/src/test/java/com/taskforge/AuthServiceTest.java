package com.taskforge;

import com.taskforge.dto.request.LoginRequest;
import com.taskforge.dto.request.RegisterRequest;
import com.taskforge.dto.response.AuthResponse;
import com.taskforge.dto.response.UserResponse;
import com.taskforge.exception.DuplicateResourceException;
import com.taskforge.model.User;
import com.taskforge.repository.UserRepository;
import com.taskforge.security.JwtUtil;
import com.taskforge.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setName("Budi Santoso");
        registerRequest.setEmail("budi@test.com");
        registerRequest.setPassword("password123");
        registerRequest.setRole(User.Role.KETUA);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("budi@test.com");
        loginRequest.setPassword("password123");

        savedUser = User.builder()
                .id(1L)
                .name("Budi Santoso")
                .email("budi@test.com")
                .password("$2a$10$hashedpassword")
                .role(User.Role.KETUA)
                .build();
    }

    @Test
    void register_success() {
        when(userRepository.existsByEmail("budi@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse result = authService.register(registerRequest);

        assertThat(result.getEmail()).isEqualTo("budi@test.com");
        assertThat(result.getRole()).isEqualTo("KETUA");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateEmail_throwsException() {
        when(userRepository.existsByEmail("budi@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("sudah terdaftar");
    }

    @Test
    void login_success() {
        when(userRepository.findByEmail("budi@test.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", savedUser.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString())).thenReturn("mock.jwt.token");

        AuthResponse result = authService.login(loginRequest);

        assertThat(result.getToken()).isEqualTo("mock.jwt.token");
        assertThat(result.getUser().getEmail()).isEqualTo("budi@test.com");
    }

    @Test
    void login_wrongPassword_throwsException() {
        when(userRepository.findByEmail("budi@test.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", savedUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_emailNotFound_throwsException() {
        when(userRepository.findByEmail("budi@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);
    }
}
