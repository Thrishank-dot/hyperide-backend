package com.hyperide.backend.controller;

import com.hyperide.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // Admin Manually Resets Password
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String newPassword = req.get("newPassword");
        
        return userRepository.findByUsername(username).map(user -> {
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordResetRequested(false);
            userRepository.save(user);
            return ResponseEntity.ok("Password reset successful for " + username);
        }).orElse(ResponseEntity.badRequest().body("User not found"));
    }

    // Admin Grants Access to a File
    @PostMapping("/grant-access")
    public ResponseEntity<?> grantAccess(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String fileName = req.get("fileName");
        
        return userRepository.findByUsername(username).map(user -> {
            user.getAccessibleFiles().add(fileName);
            userRepository.save(user);
            return ResponseEntity.ok("Access Granted to " + fileName);
        }).orElse(ResponseEntity.badRequest().body("User not found"));
    }
}