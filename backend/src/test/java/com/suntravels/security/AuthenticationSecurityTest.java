package com.suntravels.security;

import com.suntravels.config.SecurityConfig;
import com.suntravels.controller.*;
import com.suntravels.dto.*;
import com.suntravels.entity.*;
import com.suntravels.repository.UserRepository;
import com.suntravels.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, ContractController.class, QuotationSearchController.class})
@Import({SecurityConfig.class, DatabaseUserDetailsService.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class AuthenticationSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean UserRepository users;
    @MockitoBean ContractService contracts;
    @MockitoBean QuotationSearchService search;

    @BeforeEach void users() {
        when(users.findByUsernameIgnoreCase("admin01")).thenReturn(Optional.of(user(1,"admin01",UserRole.ADMIN,UserStatus.ACTIVE)));
        when(users.findByUsernameIgnoreCase("agent01")).thenReturn(Optional.of(user(2,"agent01",UserRole.AGENT,UserStatus.ACTIVE)));
        when(users.findByUsernameIgnoreCase("inactive01")).thenReturn(Optional.of(user(3,"inactive01",UserRole.AGENT,UserStatus.INACTIVE)));
        when(users.findByUsernameIgnoreCase("missing")).thenReturn(Optional.empty());
    }

    @Test void adminAndAgentLoginRestoreSafeProfilesWithBcrypt() throws Exception {
        MockHttpSession admin = login("admin01");
        SecurityContext stored=(SecurityContext)admin.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Assertions.assertNull(((DatabaseUserPrincipal)stored.getAuthentication().getPrincipal()).getPassword());
        mvc.perform(get("/api/auth/me").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1)).andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("$2a$"))));
        MockHttpSession agent = login("agent01");
        mvc.perform(get("/api/auth/me").session(agent)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("AGENT"));
        Assertions.assertTrue(encoder.matches("Correct-Horse-123!", user(9,"x",UserRole.ADMIN,UserStatus.ACTIVE).getPasswordHash()));
    }

    @Test void unknownWrongPasswordAndInactiveUseSameFailure() throws Exception {
        for (String body : List.of(loginJson("missing","Correct-Horse-123!"), loginJson("admin01","wrong-password"), loginJson("inactive01","Correct-Horse-123!"))) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid username or password."));
        }
    }

    @Test void csrfIsRequiredAndUnauthenticatedApisReturnJson401() throws Exception {
        mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(cookie().exists("XSRF-TOKEN"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(loginJson("admin01","Correct-Horse-123!")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/contracts")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test void agentCannotMutateContractButAdminReachesBusinessController() throws Exception {
        mvc.perform(patch("/api/contracts/1/suspend").session(login("agent01")).with(csrf())).andExpect(status().isForbidden());
        when(contracts.suspendContract(1)).thenReturn(contract());
        mvc.perform(patch("/api/contracts/1/suspend").session(login("admin01")).with(csrf())).andExpect(status().isOk());
        verify(contracts).suspendContract(1);
    }

    @Test void bothRolesCanSearchAndAgentCanReadFilterReferences() throws Exception {
        var limits = new QuotationSearchResponse.Limits(LocalDate.of(2026,10,15),8,10,31,730,20,"LOWEST_TOTAL_PER_CONTRACT");
        when(search.limits()).thenReturn(limits);
        when(search.filters()).thenReturn(new QuotationSearchFilters(List.of(),List.of()));
        when(search.search(any())).thenReturn(new QuotationSearchResponse(LocalDate.of(2026,10,15),LocalDate.of(2026,10,24),LocalDate.of(2026,10,25),1,List.of(),0,false,"LOWEST_TOTAL_PER_CONTRACT","Informational"));
        for (String username : List.of("admin01","agent01")) {
            mvc.perform(get("/api/search/limits").session(login(username))).andExpect(status().isOk()).andExpect(jsonPath("$.maxRooms").value(8));
            mvc.perform(get("/api/search/filters").session(login(username))).andExpect(status().isOk());
            mvc.perform(post("/api/search/quotations").session(login(username)).with(csrf()).contentType("application/json")
                    .content("{\"checkIn\":\"2026-10-24\",\"checkOut\":\"2026-10-25\",\"rooms\":[{\"adults\":1}]}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/contracts").session(login("agent01"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/hotels").session(login("agent01"))).andExpect(status().isForbidden());
    }

    @Test void logoutInvalidatesSessionAndRequiresCsrf() throws Exception {
        MockHttpSession session = login("admin01");
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(loginJson(username,"Correct-Horse-123!"))).andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
    private String loginJson(String username,String password) { return "{\"username\":\""+username+"\",\"password\":\""+password+"\"}"; }
    private User user(int id,String username,UserRole role,UserStatus status) {
        User user = new User(username,encoder.encode("Correct-Horse-123!"),"Test User",role,status);
        ReflectionTestUtils.setField(user,"userId",id); return user;
    }
    private ContractResponse contract() { return new ContractResponse(1,"C001",1,"Hotel",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),new BigDecimal("15.00"),"USD",ContractStatus.APPROVED); }
}
