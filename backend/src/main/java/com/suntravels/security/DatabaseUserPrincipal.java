package com.suntravels.security;

import com.suntravels.dto.AuthenticatedUserResponse;
import com.suntravels.entity.User;
import com.suntravels.entity.UserRole;
import com.suntravels.entity.UserStatus;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class DatabaseUserPrincipal implements UserDetails, CredentialsContainer {
    private final Integer userId;
    private final String username;
    private String password;
    private final String fullName;
    private final UserRole role;
    private final boolean enabled;

    // Initializes the database user principal and its required dependencies.
    private DatabaseUserPrincipal(Integer userId, String username, String password, String fullName,
            UserRole role, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.enabled = enabled;
    }

    // Builds the authenticated principal from the stored staff account.
    public static DatabaseUserPrincipal from(User user) {
        return new DatabaseUserPrincipal(user.getUserId(), user.getUsername(), user.getPasswordHash(),
                user.getFullName(), user.getRole(), user.getStatus() == UserStatus.ACTIVE);
    }

    // Returns the authorities.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // Returns the username.
    @Override
    public String getUsername() {
        return username;
    }

    // Returns the password.
    @Override
    public String getPassword() {
        return password;
    }

    // Reports whether the authenticated account is treated as unexpired.
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // Reports whether the authenticated account is treated as unlocked.
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // Reports whether the authenticated credentials are treated as unexpired.
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // Reports whether the staff account is active and permitted to sign in.
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    // Clears the stored password from the authenticated principal.
    @Override
    public void eraseCredentials() {
        password = null;
    }

    // Returns staff identity and role without exposing credentials.
    public AuthenticatedUserResponse safeProfile() {
        return new AuthenticatedUserResponse(userId, username, fullName, role);
    }
}
