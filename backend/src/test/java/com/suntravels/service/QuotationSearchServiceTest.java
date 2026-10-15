package com.suntravels.service;
import com.suntravels.dto.*;
import com.suntravels.dto.QuotationSearchRequest.RequestedRoom;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.*;
import com.suntravels.repository.QuotationSearchRepository.*;
import jakarta.validation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class QuotationSearchServiceTest {
 @Mock QuotationSearchRepository data; @Mock HotelRepository hotels; @Mock RoomTypeRepository rooms;
 ValidatorFactory factory; QuotationSearchService service;
 final LocalDate today=LocalDate.of(2026,10,15),in=LocalDate.of(2026,10,24),out=in.plusDays(3);
 @BeforeEach void setup() { factory=Validation.buildDefaultValidatorFactory();service=new QuotationSearchService(data,hotels,rooms,factory.getValidator(),Clock.fixed(Instant.parse("2026-10-14T20:00:00Z"),ZoneId.of("Asia/Colombo")),new NightlyQuotationPricing(),new StayAvailability(),new RoomAllocationEngine()); }
 @AfterEach void close() { factory.close(); }
 QuotationSearchRequest request(int... adults) { return new QuotationSearchRequest(in,out,Arrays.stream(adults).mapToObj(RequestedRoom::new).toList(),null,null,null); }
 Candidate candidate(int id,int contract,int hotel,int capacity,int rooms,String rate,String currency) { return new Candidate(id,contract,hotel,"Hotel "+hotel,"Colombo","C"+contract,currency,new BigDecimal("15.00"),in,in.plusDays(40),id,"Type "+id,new BigDecimal(rate),rooms,capacity); }
 Candidate candidate() { return candidate(7,4,1,2,3,"100","USD"); }
 void candidates(Candidate... values) { when(data.candidates(any())).thenReturn(Arrays.asList(values)); }
 void error(int status,QuotationSearchRequest r) { assertEquals(status,assertThrows(QuotationSearchException.class,()->service.search(r)).getStatus()); }
 @Test void businessClockAndLimitsComeFromColombo() { assertEquals(today,service.limits().businessDate()); assertEquals(8,service.limits().maxRooms()); }
 @ParameterizedTest @ValueSource(ints={-1,0}) void rejectsPastOrSameDayCheckout(int offset) { error(400,new QuotationSearchRequest(today.plusDays(offset),today.plusDays(offset),List.of(new RequestedRoom(1)),null,null,null)); verifyNoInteractions(data); }
 @Test void pastCheckInIsRejectedEvenWithValidCheckout() { error(400,new QuotationSearchRequest(today.minusDays(1),out,List.of(new RequestedRoom(1)),null,null,null)); }
 @Test void rejectsReversedAndExcessiveDateRanges() { error(400,new QuotationSearchRequest(in,in.minusDays(1),List.of(new RequestedRoom(1)),null,null,null)); error(400,new QuotationSearchRequest(in,in.plusDays(32),List.of(new RequestedRoom(1)),null,null,null)); error(400,new QuotationSearchRequest(today.plusDays(731),today.plusDays(732),List.of(new RequestedRoom(1)),null,null,null)); }
 @ParameterizedTest @ValueSource(ints={0,-1,11}) void rejectsInvalidAdultCounts(int count) { error(400,request(count)); verifyNoInteractions(data); }
 @Test void rejectsNullAndEmptyRoomStructures() {
  error(400,null); error(400,request());error(400,new QuotationSearchRequest(in,out,null,null,null,null));
  error(400,new QuotationSearchRequest(in,out,Arrays.asList((RequestedRoom)null),null,null,null)); error(400,new QuotationSearchRequest(in,out,List.of(new RequestedRoom(null)),null,null,null));
  error(400,request(1,1,1,1,1,1,1,1,1));
 }
 @Test void selectedReferenceMustExistAndBadIdsAreInvalid() {
  error(404,new QuotationSearchRequest(in,out,List.of(new RequestedRoom(1)),null,99,null));
  error(404,new QuotationSearchRequest(in,out,List.of(new RequestedRoom(1)),null,null,99));
  error(400,new QuotationSearchRequest(in,out,List.of(new RequestedRoom(1)),null,0,null));
  error(400,new QuotationSearchRequest(in,out,List.of(new RequestedRoom(1)),"x".repeat(101),null,null)); verifyNoInteractions(data);
 }
 @Test void noMatchesIsSuccessfulEmptySearch() { assertTrue(service.search(request(2)).quotations().isEmpty()); verify(data,never()).seasons(any()); }
 @Test void mixedAdultsShareInventoryAndDisplayedNightsSumExactly() {
  candidates(candidate()); var result=service.search(request(2,1));var q=result.quotations().get(0);
  assertEquals(new BigDecimal("1035.00"),q.totalPrice()); assertEquals(2,q.rooms().get(0).adults()); assertEquals(1,q.rooms().get(1).adults());
  assertEquals(3,q.rooms().get(0).availableRoomsForStay()); assertEquals(2,q.rooms().get(0).allocatedRoomsForOffering()); assertEquals(1,q.rooms().get(0).remainingRoomsAfterAllocation());
  assertEquals(q.totalPrice(),q.rooms().stream().map(r->r.totalPrice()).reduce(new BigDecimal("0.00"),BigDecimal::add));
  q.rooms().forEach(r->assertEquals(r.totalPrice(),r.nightlyPrices().stream().map(n->n.sellingPrice()).reduce(new BigDecimal("0.00"),BigDecimal::add)));
 }
 @Test void mixedTypesSatisfyDifferentRoomSpecificOccupancy() {
  candidates(candidate(7,4,1,2,1,"100","USD"),candidate(8,4,1,1,1,"50","USD"));var q=service.search(request(2,1)).quotations().get(0);
  assertEquals(7,q.rooms().get(0).offeringId());assertEquals(8,q.rooms().get(1).offeringId());assertEquals(new BigDecimal("862.50"),q.totalPrice());
 }
 @Test void fullCombinationIsRequiredNotPartialPerOfferingMatches() { candidates(candidate(7,4,1,2,1,"100","USD"));assertTrue(service.search(request(2,1)).quotations().isEmpty());assertTrue(service.search(request(3)).quotations().isEmpty()); }
 @Test void cannotCombineContractsOrHotels() { candidates(candidate(7,4,1,2,1,"100","USD"),candidate(8,5,2,1,1,"50","USD"));assertTrue(service.search(request(2,1)).quotations().isEmpty()); }
 @Test void peakNightAvailabilityAllowsTurnoverNotFalseSoldOut() {
  candidates(candidate(7,4,1,2,2,"100","USD"));when(data.bookedRooms(Set.of(7),in,out)).thenReturn(List.of(new BookedRoom(1,7,in,in.plusDays(1),2),new BookedRoom(2,7,in.plusDays(1),out,2)));
  assertEquals(1,service.search(request(2)).quotations().get(0).rooms().get(0).availableRoomsForStay());
 }
 @Test void fullyBookedOfferingDoesNotProduceAQuote() { candidates(candidate(7,4,1,2,1,"100","USD"));when(data.bookedRooms(Set.of(7),in,out)).thenReturn(List.of(new BookedRoom(1,7,in,out,1)));assertTrue(service.search(request(1)).quotations().isEmpty()); }
 @Test void seasonalRatesAreBulkLoadedAndOverrideOnlyApplicableNight() {
  candidates(candidate());when(data.seasons(Set.of(7))).thenReturn(List.of(new Season(1,7,in.plusDays(1),in.plusDays(1),new BigDecimal("150"))));var q=service.search(request(2)).quotations().get(0);
  assertEquals(new BigDecimal("805.00"),q.totalPrice()); assertEquals(List.of("BASE","SEASONAL","BASE"),q.rooms().get(0).nightlyPrices().stream().map(n->n.source()).toList());verify(data,times(1)).seasons(Set.of(7));
 }
 @Test void unsafeReplacementOrApprovalStateFailsClosed() { candidates(candidate());when(data.inconsistent(Set.of(7),Set.of(4),Set.of(1),today)).thenReturn(true);error(409,request(1));verify(data,never()).bookedRooms(any(),any(),any()); }
 @Test void excessiveProcessingLimitsNeverReturnAnIncompleteSearch() { when(data.candidates(any())).thenReturn(Collections.nCopies(501,candidate()));error(409,request(1)); }
 @Test void invalidStoredRateAndCurrencyAreNotQuoted() { candidates(candidate(7,4,1,2,2,"0","USD"));error(409,request(1));candidates(candidate(7,4,1,2,2,"100","usd"));error(409,request(1)); }
 @Test void currencyGroupsAndTotalsAreDeterministicAndDoNotConvert() {
  candidates(candidate(7,4,1,2,3,"100","USD"),candidate(8,5,2,2,3,"200","EUR"),candidate(9,6,3,2,3,"50","USD"));var q=service.search(request(1)).quotations();
  assertEquals(List.of("EUR","USD","USD"),q.stream().map(v->v.currencyCode()).toList());assertEquals(new BigDecimal("172.50"),q.get(1).totalPrice());
 }
 @Test void resultsAreCappedExplicitlyWithoutLosingAllocationCorrectness() {
  List<Candidate> all=new ArrayList<>();for(int i=1;i<=25;i++)all.add(candidate(i,i,i,2,1,"100","USD"));when(data.candidates(any())).thenReturn(all);
  var result=service.search(request(1));assertTrue(result.truncated());assertEquals(25,result.matchingOptions());assertEquals(20,result.quotations().size());assertEquals("LOWEST_TOTAL_PER_CONTRACT",result.allocationPolicy());
 }
}
