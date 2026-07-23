package com.ayush.kite.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {

    private final UserRepository userRepository;
    private final KiteSessionService kiteSessionService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepository, KiteSessionService kiteSessionService) {
        this.userRepository = userRepository;
        this.kiteSessionService = kiteSessionService;
    }

    @PostMapping("/auth/signup")
    public void signup(@RequestBody AuthRequest request) {
        String userId = request.userId().toLowerCase();
        if (userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists.");
        }
        userRepository.save(new User(userId, passwordEncoder.encode(request.password())));
    }

    @PostMapping("/auth/login")
    public void login(@RequestBody AuthRequest request, HttpSession session) {
        User user = userRepository.findById(request.userId().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
        }

        session.setAttribute("userId", user.getId());
    }

    @PostMapping("/auth/logout")
    public void logout(HttpSession session) {
        session.invalidate();
    }

    @GetMapping("/auth/session")
    public SessionStatusResponse session(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            return new SessionStatusResponse(false, null, false, null);
        }

        String id = (String) userId;
        return kiteSessionService.getActiveSession(id)
                .map(ks -> new SessionStatusResponse(true, id, true, ks.getExpiresAt()))
                .orElseGet(() -> new SessionStatusResponse(true, id, false, null));
    }
}
