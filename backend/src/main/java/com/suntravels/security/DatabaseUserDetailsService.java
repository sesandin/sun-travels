package com.suntravels.security;

import com.suntravels.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public DatabaseUserDetailsService(UserRepository users) { this.users = users; }
    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .map(DatabaseUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
    }
}
