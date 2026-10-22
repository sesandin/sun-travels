package com.suntravels.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class RuntimeLoggingSecurity
{
    @Bean
    @Order(1)
    SecurityFilterChain runtimeLoggingChain(HttpSecurity http, AuthenticationManager manager,
            CookieCsrfTokenRepository csrfRepository,
            org.springframework.core.env.Environment environment,
            @Value("${management.server.port:9091}") int managementPort,
            @Value("${server.port:8080}") int applicationPort) throws Exception
    {
        var handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null); // GET returns the XSRF-TOKEN cookie for subsequent POSTs.
        return http.securityMatcher("/actuator/**").authenticationManager(manager)
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(handler))
                .authorizeHttpRequests(auth -> auth.anyRequest().access((authentication, context) -> {
                    var request = context.getRequest();
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    String base = "/actuator/loggers/com.suntravels.service";
                    String remote = request.getRemoteAddr();
                    boolean enabled = environment.getProperty("suntravels.logging.runtime-enabled", Boolean.class, false);
                    int boundManagementPort = environment.getProperty("local.management.port", Integer.class, managementPort);
                    int boundApplicationPort = environment.getProperty("local.server.port", Integer.class, applicationPort);
                    boolean local = "127.0.0.1".equals(remote) || "::1".equals(remote)
                            || "0:0:0:0:0:0:0:1".equals(remote);
                    return new AuthorizationDecision(enabled && local && boundManagementPort != boundApplicationPort
                            && request.getLocalPort() == boundManagementPort
                            && (path.equals(base) || path.startsWith(base + "."))
                            && (request.getMethod().equals("GET") || request.getMethod().equals("POST"))
                            && authentication.get().getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
                })).build();
    }
}
