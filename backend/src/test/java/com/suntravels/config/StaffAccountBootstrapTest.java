package com.suntravels.config;

import com.suntravels.entity.User;
import com.suntravels.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffAccountBootstrapTest {
    @Test void disabledBootstrapDoesNothing() throws Exception {
        UserRepository users=mock(UserRepository.class);
        new StaffAccountBootstrap(users,new BCryptPasswordEncoder(),false,"","" ).run(new DefaultApplicationArguments());
        verifyNoInteractions(users);
    }
    @Test void createsMissingAccountsWithBcryptAndPreservesExistingAccounts() throws Exception {
        UserRepository users=mock(UserRepository.class); BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
        when(users.existsByUsernameIgnoreCase("admin01")).thenReturn(true);
        when(users.existsByUsernameIgnoreCase("agent01")).thenReturn(false);
        new StaffAccountBootstrap(users,encoder,true,"Admin-Password-123!","Agent-Password-123!").run(new DefaultApplicationArguments());
        ArgumentCaptor<User> saved=ArgumentCaptor.forClass(User.class); verify(users).saveAndFlush(saved.capture());
        assertEquals("agent01",saved.getValue().getUsername()); assertTrue(encoder.matches("Agent-Password-123!",saved.getValue().getPasswordHash()));
        assertNotEquals("Agent-Password-123!",saved.getValue().getPasswordHash());
    }
    @ParameterizedTest
    @ValueSource(strings={"","       ","Aa1!aaa","lower1!x","UPPER1!X","NoDigit!x","NoSpecial1x"})
    void refusesMissingBlankShortAndMissingCharacterCategories(String invalidPassword) {
        UserRepository users=mock(UserRepository.class);
        IllegalStateException error=assertThrows(IllegalStateException.class,()->new StaffAccountBootstrap(
                users,new BCryptPasswordEncoder(),true,invalidPassword,"Valid2@x").run(new DefaultApplicationArguments()));
        assertTrue(error.getMessage().contains("uppercase English letter"));
        verify(users,never()).saveAndFlush(any());
    }
    @Test void acceptsEightCharacterPasswordAndHashesItWithBcrypt() throws Exception {
        UserRepository users=mock(UserRepository.class);BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
        when(users.existsByUsernameIgnoreCase("agent01")).thenReturn(true);
        new StaffAccountBootstrap(users,encoder,true,"Aa1!aaaa","ignored").run(new DefaultApplicationArguments());
        ArgumentCaptor<User> saved=ArgumentCaptor.forClass(User.class);verify(users).saveAndFlush(saved.capture());
        assertTrue(encoder.matches("Aa1!aaaa",saved.getValue().getPasswordHash()));
    }
    @Test void enforcesMaximumUtf8BytesRatherThanCharacterCount() throws Exception {
        String exactly72Bytes="Aa1!"+"é".repeat(34);
        UserRepository accepted=mock(UserRepository.class);when(accepted.existsByUsernameIgnoreCase("agent01")).thenReturn(true);
        new StaffAccountBootstrap(accepted,new BCryptPasswordEncoder(),true,exactly72Bytes,"ignored").run(new DefaultApplicationArguments());
        verify(accepted).saveAndFlush(any(User.class));

        String over72Bytes="Aa1!"+"é".repeat(35);
        UserRepository rejected=mock(UserRepository.class);
        assertThrows(IllegalStateException.class,()->new StaffAccountBootstrap(
                rejected,new BCryptPasswordEncoder(),true,over72Bytes,"Valid2@x").run(new DefaultApplicationArguments()));
        verify(rejected,never()).saveAndFlush(any());
    }
}
