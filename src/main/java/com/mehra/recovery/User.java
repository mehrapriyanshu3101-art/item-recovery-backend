package com.mehra.foundit;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;

    @Column(unique = true)
    private String uniqueToken; // Required for the QR logic

    public User() {}

    // Getters and Setters
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUniqueToken() { return uniqueToken; }
    public void setUniqueToken(String uniqueToken) { this.uniqueToken = uniqueToken; }
}
