package com.suntravels.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id") private Integer userId;
    @Column(name = "username", nullable = false, unique = true, length = 100) private String username;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(name = "full_name", nullable = false, length = 150) private String fullName;
    @Enumerated(EnumType.STRING) @Column(name = "role", nullable = false, length = 20) private UserRole role;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private UserStatus status;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private LocalDateTime createdAt;

    protected User() {}
    public User(String username, String passwordHash, String fullName, UserRole role, UserStatus status) {
        this.username = username; this.passwordHash = passwordHash; this.fullName = fullName;
        this.role = role; this.status = status;
    }
    public Integer getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
