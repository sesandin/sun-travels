package com.suntravels.security;

import com.suntravels.config.SecurityConfig;
import com.suntravels.controller.AuthController;
import com.suntravels.controller.ContractController;
import com.suntravels.controller.CustomerController;
import com.suntravels.controller.DirectoryController;
import com.suntravels.controller.OfferingController;
import com.suntravels.controller.QuotationSearchController;
import com.suntravels.dto.ContractResponse;
import com.suntravels.dto.CustomerPageResponse;
import com.suntravels.dto.CustomerResponse;
import com.suntravels.dto.OfferingOccupancy;
import com.suntravels.dto.QuotationSearchFilters;
import com.suntravels.dto.QuotationSearchResponse;
import com.suntravels.entity.ContractStatus;
import com.suntravels.entity.User;
import com.suntravels.entity.UserRole;
import com.suntravels.entity.UserStatus;
import com.suntravels.repository.UserRepository;
import com.suntravels.service.ContractService;
import com.suntravels.service.CustomerService;
import com.suntravels.service.DirectoryService;
import com.suntravels.service.OfferingService;
import com.suntravels.service.QuotationSearchService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest( { AuthController.class, ContractController.class, QuotationSearchController.class,
        CustomerController.class, OfferingController.class, DirectoryController.class } )
@Import( { SecurityConfig.class, DatabaseUserDetailsService.class, JsonAuthenticationEntryPoint.class,
        JsonAccessDeniedHandler.class } )
class AuthenticationSecurityTest
{
    @Autowired
    MockMvc mvc;
    @Autowired
    PasswordEncoder encoder;
    @MockitoBean
    UserRepository users;
    @MockitoBean
    OfferingService offerings;
    @MockitoBean
    DirectoryService directories;
    @MockitoBean
    ContractService contracts;
    @MockitoBean
    QuotationSearchService search;
    @MockitoBean
    CustomerService customers;

    // Prepares staff accounts and authentication mocks for the test.
    @BeforeEach
    void users()
    {
        when( users.findByUsernameIgnoreCase( "admin01" ) ).thenReturn(
                Optional.of( user( 1, "admin01", UserRole.ADMIN, UserStatus.ACTIVE ) ) );
        when( users.findByUsernameIgnoreCase( "agent01" ) ).thenReturn(
                Optional.of( user( 2, "agent01", UserRole.AGENT, UserStatus.ACTIVE ) ) );
        when( users.findByUsernameIgnoreCase( "inactive01" ) ).thenReturn(
                Optional.of( user( 3, "inactive01", UserRole.AGENT, UserStatus.INACTIVE ) ) );
        when( users.findByUsernameIgnoreCase( "missing" ) ).thenReturn( Optional.empty() );
    }

    // Verifies admin and agent login restore safe profiles with bcrypt.
    @Test
    void adminAndAgentLoginRestoreSafeProfilesWithBcrypt() throws Exception
    {
        MockHttpSession admin = login( "admin01" );
        SecurityContext stored = ( SecurityContext ) admin.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY );
        Assertions.assertNull(
                ( ( DatabaseUserPrincipal ) stored.getAuthentication().getPrincipal() ).getPassword() );
        mvc.perform( get( "/api/auth/me" ).session( admin ) ).andExpect( status().isOk() ).andExpect(
                jsonPath( "$.userId" ).value( 1 ) ).andExpect( jsonPath( "$.role" ).value( "ADMIN" ) ).andExpect(
                jsonPath( "$.passwordHash" ).doesNotExist() ).andExpect(
                content().string( org.hamcrest.Matchers.not( org.hamcrest.Matchers.containsString( "$2a$" ) ) ) );
        MockHttpSession agent = login( "agent01" );
        mvc.perform( get( "/api/auth/me" ).session( agent ) ).andExpect( status().isOk() ).andExpect(
                jsonPath( "$.role" ).value( "AGENT" ) );
        Assertions.assertTrue( encoder.matches( "Correct-Horse-123!",
                user( 9, "x", UserRole.ADMIN, UserStatus.ACTIVE ).getPasswordHash() ) );
    }

    // Verifies unknown wrong password and inactive use same failure.
    @Test
    void unknownWrongPasswordAndInactiveUseSameFailure() throws Exception
    {
        for( String body : List.of( loginJson( "missing", "Correct-Horse-123!" ),
                loginJson( "admin01", "wrong-password" ), loginJson( "inactive01", "Correct-Horse-123!" ) ) )
        {
            mvc.perform( post( "/api/auth/login" ).with( csrf() ).contentType( "application/json" ).content(
                    body ) ).andExpect( status().isUnauthorized() ).andExpect(
                    jsonPath( "$.message" ).value( "Invalid username or password." ) );
        }
    }

    // Verifies csrf is required and unauthenticated apis return json401.
    @Test
    void csrfIsRequiredAndUnauthenticatedApisReturnJson401() throws Exception
    {
        mvc.perform( get( "/api/auth/csrf" ) ).andExpect( status().isOk() ).andExpect(
                cookie().exists( "XSRF-TOKEN" ) );
        mvc.perform( post( "/api/auth/login" ).contentType( "application/json" ).content(
                loginJson( "admin01", "Correct-Horse-123!" ) ) ).andExpect( status().isForbidden() );
        mvc.perform( get( "/api/contracts" ) ).andExpect( status().isUnauthorized() ).andExpect(
                jsonPath( "$.message" ).value( "Authentication is required." ) );
    }

    // Verifies agent cannot mutate contract but admin reaches business controller.
    @Test
    void agentCannotMutateContractButAdminReachesBusinessController() throws Exception
    {
        mvc.perform( patch( "/api/contracts/1/suspend" ).session( login( "agent01" ) ).with( csrf() ) ).andExpect(
                status().isForbidden() );
        when( contracts.suspendContract( 1 ) ).thenReturn( contract() );
        mvc.perform( patch( "/api/contracts/1/suspend" ).session( login( "admin01" ) ).with( csrf() ) ).andExpect(
                status().isOk() );
        verify( contracts ).suspendContract( 1 );
    }

    // Verifies both roles can search and agent can read filter references.
    @Test
    void bothRolesCanSearchAndAgentCanReadFilterReferences() throws Exception
    {
        var limits = new QuotationSearchResponse.Limits( LocalDate.of( 2026, 10, 15 ), 8, 31, 730, 20,
                "INDIVIDUAL_ROOM_TYPES" );
        when( search.limits() ).thenReturn( limits );
        when( search.filters() ).thenReturn( new QuotationSearchFilters( List.of(), List.of() ) );
        when( search.search( any() ) ).thenReturn(
                new QuotationSearchResponse( LocalDate.of( 2026, 10, 15 ), LocalDate.of( 2026, 10, 24 ),
                        LocalDate.of( 2026, 10, 25 ), 1, List.of(), 0, false, "INDIVIDUAL_ROOM_TYPES",
                        "Informational" ) );
        for( String username : List.of( "admin01", "agent01" ) )
        {
            mvc.perform( get( "/api/search/limits" ).session( login( username ) ) ).andExpect(
                    status().isOk() ).andExpect( jsonPath( "$.maxRooms" ).value( 8 ) );
            mvc.perform( get( "/api/search/filters" ).session( login( username ) ) ).andExpect( status().isOk() );
            mvc.perform( post( "/api/search/quotations" ).session( login( username ) ).with( csrf() ).contentType(
                    "application/json" ).content(
                    "{\"checkIn\":\"2026-10-24\",\"checkOut\":\"2026-10-25\",\"rooms\":[{\"adults\":1}]}" ) ).andExpect(
                    status().isOk() );
        }
        mvc.perform( get( "/api/contracts" ).session( login( "agent01" ) ) ).andExpect( status().isForbidden() );
        mvc.perform( get( "/api/hotels" ).session( login( "agent01" ) ) ).andExpect( status().isForbidden() );
    }

    // Verifies occupancy is read only and available to admin and agent only when authenticated.
    @Test
    void occupancyIsReadOnlyAndAvailableToAdminAndAgentOnlyWhenAuthenticated() throws Exception
    {
        when( search.occupancy( anyInt(), anyInt(), any(), any(), isNull() ) ).thenReturn(
                List.of( new OfferingOccupancy( 8, 3, "Garden View", 2 ) ) );
        String url = "/api/search/occupancy?hotelId=7&roomTypeId=3&checkIn=2026-10-24&checkOut=2026-10-27";
        mvc.perform( get( url ) ).andExpect( status().isUnauthorized() );
        for( String username : List.of( "admin01", "agent01" ) )
        {
            MockHttpSession session = login( username );
            mvc.perform( get( url ).session( session ) ).andExpect( status().isOk() ).andExpect(
                    jsonPath( "$[0].maxAdults" ).value( 2 ) );
            mvc.perform( post( url ).session( session ) ).andExpect( status().isForbidden() );
        }
    }

    // Verifies both roles can manage customers but anonymous is rejected and writes need csrf.
    @Test
    void bothRolesCanManageCustomersButAnonymousIsRejectedAndWritesNeedCsrf() throws Exception
    {
        when( customers.searchCustomers( null, 0, 10 ) ).thenReturn(
                new CustomerPageResponse( List.of(), 0, 10, 0, 0 ) );
        when( customers.createCustomer( any() ) ).thenReturn(
                new CustomerResponse( 1, "Alice", "+94771234567", null ) );
        mvc.perform( get( "/api/customers" ) ).andExpect( status().isUnauthorized() );
        for( String username : List.of( "admin01", "agent01" ) )
        {
            MockHttpSession session = login( username );
            mvc.perform( get( "/api/customers" ).session( session ) ).andExpect( status().isOk() );
            mvc.perform( post( "/api/customers" ).session( session ).contentType( "application/json" ).content(
                    "{\"name\":\"Alice\",\"phone\":\"+94771234567\"}" ) ).andExpect( status().isForbidden() );
            mvc.perform( post( "/api/customers" ).session( session ).with( csrf() ).contentType(
                    "application/json" ).content( "{\"name\":\"Alice\",\"phone\":\"+94771234567\"}" ) ).andExpect(
                    status().isCreated() );
        }
    }

    // Verifies logout invalidates session and requires csrf.
    @Test
    void logoutInvalidatesSessionAndRequiresCsrf() throws Exception
    {
        MockHttpSession session = login( "admin01" );
        mvc.perform( post( "/api/auth/logout" ).session( session ) ).andExpect( status().isForbidden() );
        mvc.perform( post( "/api/auth/logout" ).session( session ).with( csrf() ) ).andExpect( status().isNoContent() );
        mvc.perform( get( "/api/auth/me" ).session( session ) ).andExpect( status().isUnauthorized() );
    }

    // Signs in a test account and returns its authenticated session.
    private MockHttpSession login( String username ) throws Exception
    {
        return ( MockHttpSession ) mvc.perform(
                post( "/api/auth/login" ).with( csrf() ).contentType( "application/json" ).content(
                        loginJson( username, "Correct-Horse-123!" ) ) ).andExpect(
                status().isOk() ).andReturn().getRequest().getSession( false );
    }

    // Builds the JSON staff sign-in request used by security tests.
    private String loginJson( String username, String password )
    {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    // Creates a staff account fixture with the requested role.
    private User user( int id, String username, UserRole role, UserStatus status )
    {
        User user = new User( username, encoder.encode( "Correct-Horse-123!" ), "Test User", role, status );
        ReflectionTestUtils.setField( user, "userId", id );
        return user;
    }

    // Builds the contract fixture used by this test suite.
    private ContractResponse contract()
    {
        return new ContractResponse( 1, "C001", 1, "Hotel", LocalDate.of( 2026, 1, 1 ),
                LocalDate.of( 2026, 12, 31 ), new BigDecimal( "15.00" ), "USD", ContractStatus.APPROVED );
    }

    // Verifies both roles can customize with csrf.
    @Test
    void bothRolesCanCustomizeWithCsrf() throws Exception
    {
        String selection = """
                {"hotelId":7,"contractId":4,"checkIn":"2026-10-24","checkOut":"2026-10-27","rooms":[{"offeringId":8,"adults":2}]}
                """;
        for( String username : List.of( "admin01", "agent01" ) )
        {
            var session = login( username );
            mvc.perform( post( "/api/search/selection" ).session( session ).with( csrf() ).contentType(
                    "application/json" ).content( selection ) ).andExpect( status().isOk() );
            mvc.perform(
                    post( "/api/search/selection" ).session( session ).contentType( "application/json" ).content(
                            selection ) ).andExpect( status().isForbidden() );
        }
        mvc.perform( post( "/api/search/selection" ).with( csrf() ).contentType( "application/json" ).content(
                selection ) ).andExpect( status().isUnauthorized() );
    }

    // Verifies directory pages remain admin only.
    @Test
    void directoryPagesRemainAdminOnly() throws Exception
    {
        for( String resource : List.of( "hotels", "room-types", "contracts", "offerings" ) )
        {
            mvc.perform( get( "/api/" + resource + "/page" ).session( login( "agent01" ) ) )
               .andExpect( status().isForbidden() );
            mvc.perform( get( "/api/" + resource + "/page" ).session( login( "admin01" ) ) )
               .andExpect( status().isOk() );
        }
    }
}
