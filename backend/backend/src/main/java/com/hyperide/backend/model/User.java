package com.hyperide.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;
    private String password;
    private String role; 

    // --- FIX: Re-added dbAccess for Admin/Seeder compatibility ---
    private boolean dbAccess = false; 

    // New features
    private boolean passwordResetRequested = false;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> accessibleFiles = new HashSet<>();
}