package com.hyperide.backend.controller;

import com.hyperide.backend.model.User;
import com.hyperide.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // PASSWORD CONSTRAINT: Min 8 chars, 1 Upper, 1 Special (!@#$&*)
    private static final Pattern PASS_PATTERN = Pattern.compile("^(?=.*[A-Z])(?=.*[!@#$&*])(?=.*[0-9]).{8,}$");

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username taken");
        }
        
        // Security Check
        if (!PASS_PATTERN.matcher(user.getPassword()).matches()) {
            return ResponseEntity.badRequest().body("Password too weak! Must be 8+ chars, contain 1 Uppercase, 1 Number, 1 Special Char.");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("USER");
        // Grant access to their own folder (created dynamically later)
        userRepository.save(user);
        return ResponseEntity.ok("Registration Successful");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User req) {
        Optional<User> user = userRepository.findByUsername(req.getUsername());
        if (user.isPresent() && passwordEncoder.matches(req.getPassword(), user.get().getPassword())) {
            return ResponseEntity.ok(Map.of("username", user.get().getUsername(), "role", user.get().getRole()));
        }
        return ResponseEntity.status(401).body("Invalid credentials");
    }
}