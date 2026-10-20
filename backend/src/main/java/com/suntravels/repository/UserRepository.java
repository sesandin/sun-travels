package com.suntravels.repository;

import com.suntravels.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Integer>
{
    // Finds a staff account by username, ignoring case.
    Optional<User> findByUsernameIgnoreCase( String username );

    // Checks whether a staff username is already registered, ignoring case.
    boolean existsByUsernameIgnoreCase( String username );
}
