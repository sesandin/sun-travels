package com.suntravels.security;

import com.suntravels.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService
{
    private final UserRepository users;

    // Initializes the database user details service and its required dependencies.
    public DatabaseUserDetailsService( UserRepository users )
    {
        this.users = users;
    }

    // Loads a staff account and its authorities for authentication.
    @Override
    public UserDetails loadUserByUsername( String username ) throws UsernameNotFoundException
    {
        return users.findByUsernameIgnoreCase( username == null ? "" : username.trim() ).map(
                DatabaseUserPrincipal::from ).orElseThrow(
                () -> new UsernameNotFoundException( "Invalid username or password" ) );
    }
}
