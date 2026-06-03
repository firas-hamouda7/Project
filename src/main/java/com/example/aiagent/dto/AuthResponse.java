package com.example.aiagent.dto;

public class AuthResponse {
    private String token;
    private String type = "Bearer";
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String role;
    private String photo;

    public AuthResponse(String token, Long id, String nom, String prenom, String email, String role) {
        this.token = token;
        this.id = id;
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.role = role;
    }

    public AuthResponse(String token, Long id, String nom, String prenom, String email, String role, String photo) {
        this(token, id, nom, prenom, email, role);
        this.photo = photo;
    }

    public String getToken() { return token; }
    public String getType() { return type; }
    public Long getId() { return id; }
    public String getNom() { return nom; }
    public String getPrenom() { return prenom; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getPhoto() { return photo != null ? photo : ""; }
}
