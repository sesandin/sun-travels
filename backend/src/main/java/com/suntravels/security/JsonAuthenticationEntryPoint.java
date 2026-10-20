package com.suntravels.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint
{
    // Returns a JSON unauthorized response when authentication is required.
    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception ) throws IOException
    {
        response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
        response.setContentType( MediaType.APPLICATION_JSON_VALUE );
        response.getWriter().write( "{\"message\":\"Authentication is required.\"}" );
    }
}
