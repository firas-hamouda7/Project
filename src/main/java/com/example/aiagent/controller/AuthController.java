package com.example.aiagent.controller;

import com.example.aiagent.dto.AuthResponse;
import com.example.aiagent.dto.ForgotPasswordRequest;
import com.example.aiagent.dto.LoginRequest;
import com.example.aiagent.dto.RegisterRequest;
import com.example.aiagent.dto.ResetPasswordRequest;
import com.example.aiagent.entity.Role;
import com.example.aiagent.entity.User;
import com.example.aiagent.entity.UserStatus;
import com.example.aiagent.repository.UserRepository;
import com.example.aiagent.security.JwtUtil;
import com.example.aiagent.service.MailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final MailService mailService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          MailService mailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.mailService = mailService;
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
            user.getRole().name(),
            user.getPhoto() != null ? user.getPhoto() : ""
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

        if (user.getStatus() == UserStatus.SUSPENDU) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Votre compte est suspendu. Contactez un administrateur."));
        }
        if (user.getStatus() == UserStatus.DESACTIVE) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Votre compte a été désactivé."));
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(new AuthResponse(
            token,
            user.getId(),
            user.getNom(),
            user.getPrenom(),
            user.getEmail(),
            user.getRole().name(),
            user.getPhoto() != null ? user.getPhoto() : ""
        ));
    }

    /** Demande de réinitialisation du mot de passe. */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email est obligatoire"));
        }

        User user = userRepository.findByEmail(req.getEmail()).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(Map.of("message", "Si cet email existe, un lien de réinitialisation a été envoyé."));
        }

        String token = UUID.randomUUID().toString();
        user.setResetPasswordToken(token);
        user.setResetPasswordExpiresAt(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        try {
            mailService.sendResetPasswordEmail(user.getEmail(), token);
            return ResponseEntity.ok(Map.of("message", "Un email de réinitialisation a été envoyé."));
        } catch (Exception ex) {
            String resetUrl = "/reset-password?token=" + token;
            return ResponseEntity.ok(Map.of(
                "message", "Impossible d'envoyer l'email. Utilisez le lien ci-dessous pour tester.",
                "resetUrl", resetUrl
            ));
        }
    }

    /** Réinitialisation du mot de passe avec token. */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest req) {
        if (req.getToken() == null || req.getToken().isBlank()
         || req.getPassword() == null || req.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token et nouveau mot de passe sont obligatoires"));
        }

        User user = userRepository.findByResetPasswordToken(req.getToken()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token invalide ou expiré"));
        }
        if (user.getResetPasswordExpiresAt() == null || user.getResetPasswordExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le token de réinitialisation a expiré"));
        }

        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordExpiresAt(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès"));
    }

    /** Retourne le profil de l'utilisateur connecté. */
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(toUserMap(user));
    }

    /** Modifier son propre profil. */
    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, String> req) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if (req.containsKey("nom") && !req.get("nom").isBlank()) user.setNom(req.get("nom"));
        if (req.containsKey("prenom") && !req.get("prenom").isBlank()) user.setPrenom(req.get("prenom"));
        if (req.containsKey("email") && !req.get("email").isBlank()) {
            String newEmail = req.get("email");
            if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cet email est déjà utilisé"));
            }
            user.setEmail(newEmail);
        }
        if (req.containsKey("password") && !req.get("password").isBlank()) {
            String current = req.getOrDefault("currentPassword", "");
            if (!passwordEncoder.matches(current, user.getPassword())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Mot de passe actuel incorrect"));
            }
            user.setPassword(passwordEncoder.encode(req.get("password")));
        }
        userRepository.save(user);
        return ResponseEntity.ok(toUserMap(user));
    }

    /** Upload photo de profil. */
    @PostMapping("/me/photo")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fichier vide"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Seules les images sont acceptées"));
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image trop grande (max 2 Mo)"));
        }
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        try {
            String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(file.getBytes());
            user.setPhoto(dataUrl);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("photo", dataUrl));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Erreur lors de l'upload"));
        }
    }

    /** Supprimer la photo de profil. */
    @DeleteMapping("/me/photo")
    public ResponseEntity<?> deletePhoto() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        user.setPhoto(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Photo supprimée"));
    }

    /** Liste tous les utilisateurs (ADMIN uniquement) avec pagination + recherche. */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "") String search) {
        if (!isAdmin()) return ResponseEntity.status(403).body(Map.of("error", "Accès réservé aux administrateurs"));

        String q = search.trim().toLowerCase();
        List<User> allUsers = userRepository.findAll().stream()
            .filter(u -> q.isBlank()
                || (u.getNom()    != null && u.getNom().toLowerCase().contains(q))
                || (u.getPrenom() != null && u.getPrenom().toLowerCase().contains(q))
                || (u.getEmail()  != null && u.getEmail().toLowerCase().contains(q))
                || (u.getRole()   != null && u.getRole().toString().toLowerCase().contains(q)))
            .toList();

        int total = allUsers.size();
        int totalPages = (int) Math.ceil((double) total / size);
        int from = page * size;
        int to = Math.min(from + size, total);

        List<Map<String, Object>> pageUsers = (from >= total)
            ? List.of()
            : allUsers.subList(from, to).stream().map(this::toUserMap).toList();

        return ResponseEntity.ok(Map.of(
            "users",         pageUsers,
            "totalElements", total,
            "totalPages",    totalPages,
            "currentPage",   page,
            "pageSize",      size
        ));
    }

    /** Créer un utilisateur (ADMIN uniquement). */
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody RegisterRequest req) {
        if (!isAdmin()) return ResponseEntity.status(403).body(Map.of("error", "Accès réservé aux administrateurs"));
        if (req.getNom() == null || req.getNom().isBlank()
         || req.getPrenom() == null || req.getPrenom().isBlank()
         || req.getEmail() == null || req.getEmail().isBlank()
         || req.getPassword() == null || req.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "nom, prenom, email et password sont obligatoires"));
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Un compte existe déjà avec cet email"));
        }
        Role role;
        try {
            role = (req.getRole() != null && !req.getRole().isBlank()) ? Role.valueOf(req.getRole().toUpperCase()) : Role.CLIENT;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rôle invalide. Valeurs : ADMIN, CLIENT"));
        }
        User user = new User();
        user.setNom(req.getNom());
        user.setPrenom(req.getPrenom());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok(toUserMap(user));
    }

    /** Modifier un utilisateur (ADMIN uniquement). */
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, String> req) {
        if (!isAdmin()) return ResponseEntity.status(403).body(Map.of("error", "Accès réservé aux administrateurs"));
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if (req.containsKey("nom") && !req.get("nom").isBlank()) user.setNom(req.get("nom"));
        if (req.containsKey("prenom") && !req.get("prenom").isBlank()) user.setPrenom(req.get("prenom"));
        if (req.containsKey("email") && !req.get("email").isBlank()) {
            String newEmail = req.get("email");
            if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cet email est déjà utilisé"));
            }
            user.setEmail(newEmail);
        }
        if (req.containsKey("role") && !req.get("role").isBlank()) {
            try {
                user.setRole(Role.valueOf(req.get("role").toUpperCase()));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rôle invalide. Valeurs : ADMIN, CLIENT"));
            }
        }
        if (req.containsKey("password") && !req.get("password").isBlank()) {
            user.setPassword(passwordEncoder.encode(req.get("password")));
        }
        userRepository.save(user);
        return ResponseEntity.ok(toUserMap(user));
    }

    /** Supprimer un utilisateur (ADMIN uniquement). */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!isAdmin()) return ResponseEntity.status(403).body(Map.of("error", "Accès réservé aux administrateurs"));
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getEmail().equals(currentEmail)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Impossible de supprimer votre propre compte"));
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication()
            .getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Map<String, Object> toUserMap(User u) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id",        u.getId());
        map.put("nom",       u.getNom());
        map.put("prenom",    u.getPrenom());
        map.put("email",     u.getEmail());
        map.put("clientId",  u.getEmail());
        map.put("role",      u.getRole().name());
        map.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
        map.put("photo",     u.getPhoto() != null ? u.getPhoto() : "");
        map.put("status",    u.getStatus() != null ? u.getStatus().name() : UserStatus.ACTIF.name());
        return map;
    }

    /** Changer le statut d'un utilisateur (ADMIN uniquement). */
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> req) {
        if (!isAdmin()) return ResponseEntity.status(403).body(Map.of("error", "Accès réservé aux administrateurs"));
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getEmail().equals(currentEmail)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Impossible de modifier votre propre statut"));
        }
        try {
            UserStatus newStatus = UserStatus.valueOf(req.get("status").toUpperCase());
            user.setStatus(newStatus);
            userRepository.save(user);
            
            // Notification par email
            mailService.sendStatusChangeEmail(user, newStatus);
            
            return ResponseEntity.ok(toUserMap(user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Statut invalide. Valeurs : ACTIF, SUSPENDU, DESACTIVE"));
        }
    }
}
