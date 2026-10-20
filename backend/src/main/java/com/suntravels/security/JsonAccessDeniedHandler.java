package com.suntravels.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler
{
    // Returns a JSON forbidden response when the staff role lacks access.
    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception ) throws IOException
    {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI().substring( request.getContextPath().length() );
        if( ( path.equals( "/api/reservations" ) || path.startsWith( "/api/reservations/" ) ) &&
                    ( authentication == null || authentication instanceof AnonymousAuthenticationToken ) )
        {
            response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
            response.setContentType( MediaType.APPLICATION_JSON_VALUE );
            response.getWriter().write( "{\"message\":\"Authentication is required.\"}" );
            return;
        }
        response.setStatus( HttpServletResponse.SC_FORBIDDEN );
        response.setContentType( MediaType.APPLICATION_JSON_VALUE );
        response.getWriter().write( "{\"message\":\"You do not have permission to perform this action.\"}" );
    }
}
