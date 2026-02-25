package com.hyperide.backend.config;

import com.hyperide.backend.model.User;
import com.hyperide.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // Inside src/main/java/com/hyperide/backend/config/AdminSeeder.java
    // Inside AdminSeeder.java
    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            String securePass = System.getenv("ADMIN_SECRET");

            // Verification Log
            System.out.println("SYSTEM CHECK: ADMIN_SECRET is -> " + securePass);

            if (securePass == null || securePass.isEmpty()) {
                securePass = "Temporary_Fallback_99";
            }

            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(securePass));
            admin.setRole("ADMIN");
            userRepository.save(admin);
            System.out.println("✅ Admin created successfully.");
        }
    }
}