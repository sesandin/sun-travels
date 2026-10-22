package com.suntravels.config;

import com.suntravels.security.DatabaseUserDetailsService;
import com.suntravels.security.JsonAccessDeniedHandler;
import com.suntravels.security.JsonAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(classes=LoggingControlsTest.App.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"management.server.port=0", "suntravels.logging.runtime-enabled=true", "logging.file.name=", "debug=false"})
class LoggingControlsTest
{
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude=DataSourceAutoConfiguration.class)
    @Import({SecurityConfig.class,RuntimeLoggingSecurity.class,RequestCorrelationFilter.class,
            JsonAuthenticationEntryPoint.class,JsonAccessDeniedHandler.class})
    static class App {}

    @Autowired ConfigurableEnvironment environment;
    @Autowired PasswordEncoder encoder;
    @MockitoBean DatabaseUserDetailsService users;

    @Test void runtimeLoggerChangesRequireLocalAdminAndCsrfAndResetCleanly() throws Exception
    {
        String secret="test-operator-only";
        String hash=encoder.encode(secret);
        // Authentication erases credentials; real database lookups also return a fresh principal each time.
        when(users.loadUserByUsername("operator")).thenAnswer(call -> User.withUsername("operator").password(hash).roles("ADMIN").build());
        when(users.loadUserByUsername("agent")).thenAnswer(call -> User.withUsername("agent").password(hash).roles("AGENT").build());
        var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        var client=HttpClient.newBuilder().cookieHandler(cookies).build();
        String host="http://127.0.0.1:"+environment.getRequiredProperty("local.management.port");
        String target="com.suntravels.service.LoggingControlProbe";
        var logger=org.slf4j.LoggerFactory.getLogger(target);
        String endpoint=host+"/actuator/loggers/"+target;
        String admin="Basic "+Base64.getEncoder().encodeToString(("operator:"+secret).getBytes(StandardCharsets.UTF_8));
        String agent="Basic "+Base64.getEncoder().encodeToString(("agent:"+secret).getBytes(StandardCharsets.UTF_8));
        assertEquals(401,client.send(HttpRequest.newBuilder(URI.create(endpoint)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(403,client.send(HttpRequest.newBuilder(URI.create(endpoint)).header("Authorization",agent).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(200,client.send(HttpRequest.newBuilder(URI.create(endpoint)).header("Authorization",admin).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        assertFalse(logger.isDebugEnabled());
        assertEquals(403,client.send(HttpRequest.newBuilder(URI.create(endpoint)).header("Authorization",admin).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"configuredLevel\":\"DEBUG\"}")).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        String token=cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
        try {
            assertEquals(204,change(client,endpoint,admin,token,"\"DEBUG\""),"enable DEBUG");
            assertTrue(logger.isDebugEnabled());
            token=cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
            assertEquals(204,change(client,endpoint,admin,token,"null"),"reset logger to inherited level");
            assertFalse(logger.isDebugEnabled());
            for(String path:new String[]{"/actuator/loggers/ROOT","/actuator/loggers/org.hibernate.SQL","/actuator/env"})
                assertEquals(403,client.send(HttpRequest.newBuilder(URI.create(host+path)).header("Authorization",admin).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            String main="http://127.0.0.1:"+environment.getRequiredProperty("local.server.port")+"/actuator/loggers/"+target;
            assertEquals(403,client.send(HttpRequest.newBuilder(URI.create(main)).header("Authorization",admin).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        } finally { org.springframework.boot.logging.LoggingSystem.get(getClass().getClassLoader()).setLogLevel(target,null); }
        assertFalse(logger.isDebugEnabled());
        TestPropertyValues.of("suntravels.logging.runtime-enabled=false").applyTo(environment);
        assertEquals(403,client.send(HttpRequest.newBuilder(URI.create(endpoint)).header("Authorization",admin).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
    }
    int change(HttpClient client,String endpoint,String auth,String token,String level) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(endpoint)).header("Authorization",auth).header("X-XSRF-TOKEN",token)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"configuredLevel\":"+level+"}")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
