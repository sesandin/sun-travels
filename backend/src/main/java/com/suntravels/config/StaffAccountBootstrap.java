package com.suntravels.config;

import com.suntravels.entity.*;
import com.suntravels.repository.UserRepository;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StaffAccountBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StaffAccountBootstrap.class);
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final boolean enabled;
    private final char[] adminPassword;
    private final char[] agentPassword;

    public StaffAccountBootstrap(UserRepository users, PasswordEncoder passwords,
            @Value("${suntravels.auth.bootstrap.enabled:false}") boolean enabled,
            @Value("${suntravels.auth.bootstrap.admin-password:}") String adminPassword,
            @Value("${suntravels.auth.bootstrap.agent-password:}") String agentPassword) {
        this.users = users; this.passwords = passwords; this.enabled = enabled;
        this.adminPassword = adminPassword.toCharArray(); this.agentPassword = agentPassword.toCharArray();
    }

    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        try {
            if (!enabled) return;
            createIfMissing("admin01", "Sun Travels Administrator", UserRole.ADMIN, adminPassword);
            createIfMissing("agent01", "Sun Travels Agent", UserRole.AGENT, agentPassword);
            log.info("Explicit staff account bootstrap completed; existing accounts were preserved.");
        } finally {
            Arrays.fill(adminPassword, '\0'); Arrays.fill(agentPassword, '\0');
        }
    }

    private void createIfMissing(String username, String fullName, UserRole role, char[] rawPassword) {
        if (users.existsByUsernameIgnoreCase(username)) {
            log.info("Staff account {} already exists; no changes were made.", username);
            return;
        }
        validatePassword(username, rawPassword);
        String encoded = passwords.encode(new String(rawPassword));
        users.saveAndFlush(new User(username, encoded, fullName, role, UserStatus.ACTIVE));
        log.info("Created ACTIVE {} staff account {}.", role, username);
    }

    private void validatePassword(String username, char[] rawPassword) {
        int encodedLength = new String(rawPassword).getBytes(StandardCharsets.UTF_8).length;
        boolean uppercase=false, lowercase=false, digit=false, special=false;
        for (char value : rawPassword) {
            uppercase |= value >= 'A' && value <= 'Z';
            lowercase |= value >= 'a' && value <= 'z';
            digit |= value >= '0' && value <= '9';
            special |= value >= '!' && value <= '~'
                    && !(value >= 'A' && value <= 'Z')
                    && !(value >= 'a' && value <= 'z')
                    && !(value >= '0' && value <= '9');
        }
        if (rawPassword.length < 8 || encodedLength > 72 || !uppercase || !lowercase || !digit || !special) {
            throw new IllegalStateException("Bootstrap password for " + username
                    + " must contain 8 or more characters, no more than 72 UTF-8 bytes, and at least one uppercase English letter, lowercase English letter, digit and special character.");
        }
    }
}
