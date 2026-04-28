package com.mehra.recovery;

import jakarta.persistence.*;

@Entity
@Table(name = "users") // This links the class to your MySQL table
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String password;
    private String uniqueToken;

    // Default Constructor (Required by JPA)
    public User() {}

    // Getters and Setters (So other classes can read/write this data)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUniqueToken() { return uniqueToken; }
    public void setUniqueToken(String uniqueToken) { this.uniqueToken = uniqueToken; }
}