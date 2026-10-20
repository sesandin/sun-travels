package com.suntravels.config;

import com.suntravels.security.DatabaseUserDetailsService;
import com.suntravels.security.JsonAccessDeniedHandler;
import com.suntravels.security.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig
{
    private static final String ADMIN = "ADMIN";
    private static final String AGENT = "AGENT";

    // Provides the password encoder used to hash and verify staff passwords.
    @Bean
    PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    // Provides the authentication manager for staff sign-in.
    @Bean
    AuthenticationManager authenticationManager( DatabaseUserDetailsService users, PasswordEncoder encoder )
    {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider( users );
        provider.setPasswordEncoder( encoder );
        return new ProviderManager( provider );
    }

    // Stores authenticated security contexts in HTTP sessions.
    @Bean
    SecurityContextRepository securityContextRepository()
    {
        return new HttpSessionSecurityContextRepository();
    }

    // Configures session handling after successful authentication.
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy()
    {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    // Provides storage for tokens that protect state-changing requests.
    @Bean
    CookieCsrfTokenRepository csrfTokenRepository()
    {
        // Angular reads XSRF-TOKEN and echoes it in its X-XSRF-TOKEN request header.
        // Session authentication cookies remain HttpOnly; this is only the CSRF token cookie.
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath( "/" );
        repository.setCookieCustomizer( cookie -> cookie.sameSite( "Lax" ) );
        return repository;
    }

    // Configures API authentication, role access, sessions, and CSRF protection.
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            CookieCsrfTokenRepository csrfRepository )
    {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName( null );
        return http.cors( cors ->
        {
        } ).csrf( csrf -> csrf.csrfTokenRepository( csrfRepository ).csrfTokenRequestHandler(
                csrfHandler ) ).sessionManagement(
                session -> session.sessionCreationPolicy( SessionCreationPolicy.IF_REQUIRED ).sessionFixation(
                        fixation -> fixation.changeSessionId() ) ).securityContext(
                context -> context.requireExplicitSave( true ) ).exceptionHandling(
                errors -> errors.authenticationEntryPoint( authenticationEntryPoint ).accessDeniedHandler(
                        accessDeniedHandler ) ).authorizeHttpRequests(
                auth -> auth.requestMatchers( HttpMethod.GET, "/api/auth/csrf" ).permitAll().requestMatchers(
                        HttpMethod.POST, "/api/auth/login" ).permitAll().requestMatchers(
                        "/api/auth/**" ).authenticated().requestMatchers( "/api/search/**" ).hasAnyRole( ADMIN,
                        AGENT ).requestMatchers( "/api/customers/**" ).hasAnyRole( ADMIN, AGENT ).requestMatchers(
                        "/api/reservations", "/api/reservations/**" ).hasAnyRole( ADMIN, AGENT ).requestMatchers(
                        "/api/hotels/**", "/api/room-types/**", "/api/contracts/**",
                        "/api/offerings/**" ).hasRole( ADMIN ).requestMatchers(
                        "/api/**" ).denyAll().anyRequest().permitAll() ).build();
    }
}
