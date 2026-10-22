package com.suntravels.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class RequestCorrelationFilterTest
{
    @Test void generatesSafeIdsAndRestoresMdcEvenWhenRequestFails() throws Exception {
        var request=new MockHttpServletRequest();
        request.addHeader("X-Request-ID","untrusted-header");
        var response=new MockHttpServletResponse();
        MDC.put("requestId","outer");
        try {
            assertThrows(jakarta.servlet.ServletException.class,()->new RequestCorrelationFilter().doFilter(request,response,(req,res)->{
                String id=MDC.get("requestId");
                assertNotEquals("untrusted-header",id);
                assertEquals(id,response.getHeader("X-Request-ID"));
                assertDoesNotThrow(()->java.util.UUID.fromString(id));
                throw new jakarta.servlet.ServletException("test failure");
            }));
            assertEquals("outer",MDC.get("requestId"));
        } finally { MDC.clear(); }
        new RequestCorrelationFilter().doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),(req,res)->assertNotNull(MDC.get("requestId")));
        assertNull(MDC.get("requestId"));
    }
}
