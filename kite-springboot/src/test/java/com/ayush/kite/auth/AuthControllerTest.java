package com.ayush.kite.auth;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DataJpaTest
class AuthControllerTest {

    @Autowired
    private UserRepository userRepository;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        this.authController = new AuthController(userRepository, mock(KiteSessionService.class));
    }

    @Test
    void signup_thenLogin_withCorrectPassword_succeeds() {
        authController.signup(new AuthRequest("ayush", "correct-password"));

        User stored = userRepository.findById("ayush").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("correct-password");

        HttpSession session = mock(HttpSession.class);
        authController.login(new AuthRequest("ayush", "correct-password"), session);

        verify(session).setAttribute("userId", "ayush");
    }

    @Test
    void login_withWrongPassword_throwsUnauthorized() {
        authController.signup(new AuthRequest("ayush", "correct-password"));

        HttpSession session = mock(HttpSession.class);
        assertThatThrownBy(() -> authController.login(new AuthRequest("ayush", "wrong-password"), session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void signup_duplicateUserId_throwsConflict() {
        authController.signup(new AuthRequest("ayush", "password1"));

        assertThatThrownBy(() -> authController.signup(new AuthRequest("ayush", "password2")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }
}
