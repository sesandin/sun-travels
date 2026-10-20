package com.suntravels.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table( name = "user" )
public class User
{
    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "user_id" )
    private Integer userId;
    @Column( name = "username", nullable = false, unique = true, length = 100 )
    private String username;
    @Column( name = "password_hash", nullable = false, length = 255 )
    private String passwordHash;
    @Column( name = "full_name", nullable = false, length = 150 )
    private String fullName;
    @Enumerated( EnumType.STRING )
    @Column( name = "role", nullable = false, length = 20 )
    private UserRole role;
    @Enumerated( EnumType.STRING )
    @Column( name = "status", nullable = false, length = 20 )
    private UserStatus status;
    @Column( name = "created_at", nullable = false, insertable = false, updatable = false )
    private LocalDateTime createdAt;

    // Initializes the user and its required dependencies.
    protected User()
    {
    }

    // Initializes the user and its required dependencies.
    public User( String username, String passwordHash, String fullName, UserRole role, UserStatus status )
    {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
    }

    // Returns the user id.
    public Integer getUserId()
    {
        return userId;
    }

    // Returns the username.
    public String getUsername()
    {
        return username;
    }

    // Returns the password hash.
    public String getPasswordHash()
    {
        return passwordHash;
    }

    // Returns the full name.
    public String getFullName()
    {
        return fullName;
    }

    // Returns the role.
    public UserRole getRole()
    {
        return role;
    }

    // Returns the status.
    public UserStatus getStatus()
    {
        return status;
    }

    // Returns the created at.
    public LocalDateTime getCreatedAt()
    {
        return createdAt;
    }
}
