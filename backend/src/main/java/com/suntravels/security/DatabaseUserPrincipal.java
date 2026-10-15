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

    private DatabaseUserPrincipal(Integer userId, String username, String password, String fullName,
                                  UserRole role, boolean enabled) {
        this.userId=userId; this.username=username; this.password=password; this.fullName=fullName;
        this.role=role; this.enabled=enabled;
    }
    public static DatabaseUserPrincipal from(User user) {
        return new DatabaseUserPrincipal(user.getUserId(),user.getUsername(),user.getPasswordHash(),
                user.getFullName(),user.getRole(),user.getStatus()==UserStatus.ACTIVE);
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_"+role.name()));
    }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void eraseCredentials() { password=null; }
    public AuthenticatedUserResponse safeProfile() {
        return new AuthenticatedUserResponse(userId,username,fullName,role);
    }
}
