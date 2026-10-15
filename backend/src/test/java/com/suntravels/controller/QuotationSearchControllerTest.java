package com.suntravels.controller;
import com.suntravels.dto.*;
import com.suntravels.exception.*;
import com.suntravels.service.QuotationSearchService;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@ExtendWith(MockitoExtension.class)
class QuotationSearchControllerTest {
 @Mock QuotationSearchService service; MockMvc mvc; LocalValidatorFactoryBean validator;
 final String json="""
 {"checkIn":"2026-10-24","checkOut":"2026-10-27","rooms":[{"adults":2},{"adults":1}],"city":"Colombo","hotelId":null,"roomTypeId":null}
 """;
 @BeforeEach void setup() { validator=new LocalValidatorFactoryBean();validator.afterPropertiesSet();mvc=MockMvcBuilders.standaloneSetup(new QuotationSearchController(service)).setControllerAdvice(new QuotationSearchExceptionHandler()).setValidator(validator).build(); }
 @AfterEach void close() { validator.close(); }
 @Test void acceptsStructuredRoomsAndReturnsSuccessfulEmptyEnvelope() throws Exception {
  when(service.search(any())).thenReturn(new QuotationSearchResponse(LocalDate.of(2026,10,15),LocalDate.of(2026,10,24),LocalDate.of(2026,10,27),3,List.of(),0,false,"LOWEST_TOTAL_PER_CONTRACT","Informational"));
  mvc.perform(post("/api/search/quotations").contentType("application/json").content(json)).andExpect(status().isOk()).andExpect(jsonPath("$.quotations").isArray()).andExpect(jsonPath("$.nights").value(3));
  var capture=ArgumentCaptor.forClass(QuotationSearchRequest.class);verify(service).search(capture.capture());Assertions.assertEquals(List.of(2,1),capture.getValue().rooms().stream().map(r->r.adults()).toList());
 }
 @Test void publishesServerBusinessDateAndLimits() throws Exception {
  when(service.limits()).thenReturn(new QuotationSearchResponse.Limits(LocalDate.of(2026,10,15),8,10,31,730,20,"LOWEST_TOTAL_PER_CONTRACT"));
  mvc.perform(get("/api/search/limits")).andExpect(status().isOk()).andExpect(jsonPath("$.businessDate").value("2026-10-15")).andExpect(jsonPath("$.maxRooms").value(8));
 }
 @Test void validatesRequiredAndNestedRoomFields() throws Exception {
  for(String body:List.of("{}",json.replace("2}","0}"),json.replace("2}","-1}"),json.replace("2}","11}"),json.replace("2}","null}"),json.replace("[{\"adults\":2},{\"adults\":1}]","[]"),json.replace("[{\"adults\":2},{\"adults\":1}]","[null]")))
   mvc.perform(post("/api/search/quotations").contentType("application/json").content(body)).andExpect(status().isBadRequest());
  verifyNoInteractions(service);
 }
 @Test void rejectsFractionalAndStringAdultsAndMalformedCalendarDates() throws Exception {
  for(String body:List.of(json.replace("2}","2.5}"),json.replace("2}","\"2\"}"),json.replace("2026-10-24","2026-02-30"),json.replace("2026-10-24","2026-10-24T00:00:00"),json.replace("\"hotelId\":null","\"hotelId\":1.5"),"{"))
   mvc.perform(post("/api/search/quotations").contentType("application/json").content(body)).andExpect(status().isBadRequest());
  verifyNoInteractions(service);
 }
 @Test void returnsBusinessValidationNotFoundAndSafeConsistencyMessages() throws Exception {
  when(service.search(any())).thenThrow(QuotationSearchException.invalid("Checkout must be later"),new QuotationSearchException(404,"Selected hotel was not found"),QuotationSearchException.inconsistent());
  mvc.perform(post("/api/search/quotations").contentType("application/json").content(json)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Checkout must be later"));
  mvc.perform(post("/api/search/quotations").contentType("application/json").content(json)).andExpect(status().isNotFound());
  mvc.perform(post("/api/search/quotations").contentType("application/json").content(json)).andExpect(status().isConflict());
 }
 @Test void databaseErrorsDoNotLeakSql() throws Exception {
  when(service.search(any())).thenThrow(new DataAccessResourceFailureException("secret SQL"));
  mvc.perform(post("/api/search/quotations").contentType("application/json").content(json)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Accommodation search is temporarily unavailable. Please retry."));
 }
 @Test void doesNotExposeBookingOrConfirmationEndpoint() throws Exception { mvc.perform(post("/api/search/confirm")).andExpect(status().isNotFound()); }
}
