package com.example.aiagent.controller;

import com.example.aiagent.dto.AuthResponse;
import com.example.aiagent.dto.LoginRequest;
import com.example.aiagent.dto.RegisterRequest;
import com.example.aiagent.entity.Role;
import com.example.aiagent.entity.User;
import com.example.aiagent.repository.UserRepository;
import com.example.aiagent.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Gestion de l'authentification.
 *
 * POST /auth/register  → Créer un compte
 * POST /auth/login     → Connexion → retourne JWT
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /** Inscription */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req.getNom() == null || req.getNom().isBlank()
         || req.getPrenom() == null || req.getPrenom().isBlank()
         || req.getEmail() == null || req.getEmail().isBlank()
         || req.getPassword() == null || req.getPassword().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "nom, prenom, email et password sont obligatoires"));
        }

        if (userRepository.existsByEmail(req.getEmail())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Un compte existe déjà avec cet email"));
        }

        Role role;
        try {
            role = (req.getRole() != null) ? Role.valueOf(req.getRole().toUpperCase()) : Role.CLIENT;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Rôle invalide. Valeurs acceptées : ADMIN, CLIENT"));
        }

        User user = new User();
        user.setNom(req.getNom());
        user.setPrenom(req.getPrenom());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(new AuthResponse(
            token,
            user.getId(),
            user.getNom(),
            user.getPrenom(),
            user.getEmail(),
            user.getRole().name()
        ));
    }

    /** Connexion */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()
         || req.getPassword() == null || req.getPassword().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "email et password sont obligatoires"));
        }

        User user = userRepository.findByEmail(req.getEmail()).orElse(null);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Email ou mot de passe incorrect"));
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(new AuthResponse(
            token,
            user.getId(),
            user.getNom(),
            user.getPrenom(),
            user.getEmail(),
            user.getRole().name()
        ));
    }

    /** Retourne le profil de l'utilisateur connecté. */
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
            "id",        user.getId(),
            "nom",       user.getNom(),
            "prenom",    user.getPrenom(),
            "email",     user.getEmail(),
            "role",      user.getRole().name(),
            "clientId",  user.getEmail(),
            "createdAt", user.getCreatedAt().toString()
        ));
    }

    /** Liste tous les utilisateurs (ADMIN uniquement). */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            return ResponseEntity.status(403).body(Map.of("error", "Accès réservé aux administrateurs"));
        }
        List<Map<String, Object>> users = userRepository.findAll().stream()
            .map(u -> Map.<String, Object>of(
                "id",       u.getId(),
                "nom",      u.getNom(),
                "prenom",   u.getPrenom(),
                "email",    u.getEmail(),
                "clientId", u.getEmail(),
                "role",     u.getRole().name(),
                "createdAt", u.getCreatedAt().toString()
            ))
            .toList();
        return ResponseEntity.ok(Map.of("total", users.size(), "users", users));
    }
}
