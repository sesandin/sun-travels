package com.suntravels.service;
import com.suntravels.dto.*;
import com.suntravels.dto.QuotationSearchRequest.RequestedRoom;
import com.suntravels.dto.QuotationSearchResponse.*;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.entity.*;
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
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class QuotationSearchServiceTest {
 @Mock QuotationSearchRepository data; @Mock HotelRepository hotels; @Mock RoomTypeRepository rooms;
 ValidatorFactory factory; QuotationSearchService service;
 final LocalDate today=LocalDate.of(2026,10,15),in=LocalDate.of(2026,10,24),out=in.plusDays(3);
 @BeforeEach void setup() { factory=Validation.buildDefaultValidatorFactory();service=new QuotationSearchService(data,hotels,rooms,factory.getValidator(),Clock.fixed(Instant.parse("2026-10-14T20:00:00Z"),ZoneId.of("Asia/Colombo")),new NightlyQuotationPricing(),new StayAvailability()); }
 @AfterEach void close() { factory.close(); }
 QuotationSearchRequest request(int... adults) { return new QuotationSearchRequest(in,out,Arrays.stream(adults).mapToObj(RequestedRoom::new).toList(),null,null,null); }
 Candidate candidate(int id,int contract,int hotel,int capacity,int rooms,String rate,String currency) { return new Candidate(id,contract,hotel,"Hotel "+hotel,"Colombo","C"+contract,currency,new BigDecimal("15.00"),in,in.plusDays(40),id,"Type "+id,new BigDecimal(rate),rooms,capacity); }
 Candidate candidate() { return candidate(7,4,1,2,3,"100","USD"); }
 void candidates(Candidate... values) { when(data.candidates(any())).thenReturn(Arrays.asList(values)); }
 void error(int status,QuotationSearchRequest r) { assertEquals(status,assertThrows(QuotationSearchException.class,()->service.search(r)).getStatus()); }
 @Test void businessClockAndLimitsComeFromColombo() { assertEquals(today,service.limits().businessDate()); assertEquals(8,service.limits().maxRooms()); }
 @Test void occupancyReturnsEveryEligibleLimitWithoutLoadingPricesOrInventory() {
  when(hotels.existsById(1)).thenReturn(true);when(rooms.existsById(7)).thenReturn(true);
  candidates(candidate(),candidate(8,4,1,4,3,"200","USD"));
  var result=service.occupancy(1,7,in,out,"Colombo");
  assertEquals(List.of(2,4),result.stream().map(OfferingOccupancy::maxAdults).toList());
  var captured=ArgumentCaptor.forClass(QuotationSearchRequest.class);verify(data).candidates(captured.capture());
  assertEquals(1,captured.getValue().hotelId());assertEquals(7,captured.getValue().roomTypeId());
  assertEquals(in,captured.getValue().checkIn());assertEquals(out,captured.getValue().checkOut());assertEquals("Colombo",captured.getValue().city());
  verify(data,never()).seasons(any());verify(data,never()).bookedRooms(any(),any(),any());
 }
 @Test void occupancyValidatesDatesAndReferencesBeforeQueryingAndAllowsNoMatches() {
  assertEquals(400,assertThrows(QuotationSearchException.class,()->service.occupancy(1,7,in,in,null)).getStatus());
  assertEquals(400,assertThrows(QuotationSearchException.class,()->service.occupancy(null,7,in,out,null)).getStatus());
  assertEquals(400,assertThrows(QuotationSearchException.class,()->service.occupancy(1,7,null,out,null)).getStatus());
  assertEquals(404,assertThrows(QuotationSearchException.class,()->service.occupancy(99,7,in,out,null)).getStatus());
  verifyNoInteractions(data);
  when(hotels.existsById(1)).thenReturn(true);when(rooms.existsById(7)).thenReturn(true);
  assertTrue(service.occupancy(1,7,in,out,null).isEmpty());
 }
 @Test void searchFiltersExposeOnlyActiveHotelIdentityAndGenericRoomNames() {
  Hotel active=new Hotel();active.setName("Ocean");active.setCity("Colombo");active.setStatus(HotelStatus.ACTIVE);ReflectionTestUtils.setField(active,"hotelId",7);
  Hotel inactive=new Hotel();inactive.setName("Closed");inactive.setStatus(HotelStatus.INACTIVE);ReflectionTestUtils.setField(inactive,"hotelId",8);
  RoomType room=new RoomType();room.setName("Double");ReflectionTestUtils.setField(room,"roomTypeId",3);
  when(hotels.findAll(any(Sort.class))).thenReturn(List.of(active,inactive));when(rooms.findAll(any(Sort.class))).thenReturn(List.of(room));
  var filters=service.filters();assertEquals(List.of("Ocean"),filters.hotels().stream().map(QuotationSearchFilters.HotelOption::name).toList());assertEquals("Double",filters.roomTypes().get(0).name());
 }
 @ParameterizedTest @ValueSource(ints={-1,0}) void rejectsPastOrSameDayCheckout(int offset) { error(400,new QuotationSearchRequest(today.plusDays(offset),today.plusDays(offset),List.of(new RequestedRoom(1)),null,null,null)); verifyNoInteractions(data); }
 @Test void pastCheckInIsRejectedEvenWithValidCheckout() { error(400,new QuotationSearchRequest(today.minusDays(1),out,List.of(new RequestedRoom(1)),null,null,null)); }
 @Test void rejectsReversedAndExcessiveDateRanges() { error(400,new QuotationSearchRequest(in,in.minusDays(1),List.of(new RequestedRoom(1)),null,null,null)); error(400,new QuotationSearchRequest(in,in.plusDays(32),List.of(new RequestedRoom(1)),null,null,null)); error(400,new QuotationSearchRequest(today.plusDays(731),today.plusDays(732),List.of(new RequestedRoom(1)),null,null,null)); }
 @ParameterizedTest @ValueSource(ints={0,-1}) void rejectsInvalidAdultCounts(int count) { error(400,request(count)); verifyNoInteractions(data); }
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
  assertEquals(AvailabilityStatus.AVAILABLE,q.availabilityStatus());assertTrue(result.unavailableOptions().isEmpty());
 }
 @Test void unavailableInventoryCountsAllRequestedRoomsAgainstTheSamePool() {
  candidates(candidate(7,4,1,2,2,"100","USD"));
  when(data.bookedRooms(Set.of(7),in,out)).thenReturn(List.of(new BookedRoom(1,7,in.plusDays(1),in.plusDays(2),1)));
  var result=service.search(request(2,1));assertTrue(result.quotations().isEmpty());
  var option=result.unavailableOptions().get(0);
  assertEquals(AvailabilityStatus.NOT_AVAILABLE,option.availabilityStatus());
  assertEquals(1,option.offerings().get(0).availableRoomsForStay());
  assertEquals(ReasonCode.INSUFFICIENT_INVENTORY,option.reasons().get(0).code());
  assertEquals("Only 1 room available; 2 requested.",option.reasons().get(0).message());
  assertEquals(new BigDecimal("1035.00"),option.indicativeTotalPrice());
 }
 @Test void occupancyFailureHasNoMisleadingSellingPrice() {
  candidates(candidate());var result=service.search(request(4));
  assertTrue(result.quotations().isEmpty());var option=result.unavailableOptions().get(0);
  assertEquals(ReasonCode.MAX_ADULT_OCCUPANCY,option.reasons().get(0).code());
  assertTrue(option.reasons().get(0).message().contains("Maximum 2 adults per room"));
  assertNull(option.indicativeTotalPrice());
 }
 @Test void closedEligibleHotelIsDiagnosticOnlyAndCanHaveSeasonalIndicativePrice() {
  when(data.closedCandidates(any())).thenReturn(List.of(candidate()));
  when(data.seasons(Set.of(7))).thenReturn(List.of(new Season(1,7,in.plusDays(1),in.plusDays(1),new BigDecimal("150"))));
  var result=service.search(request(2));assertTrue(result.quotations().isEmpty());
  assertEquals(ReasonCode.HOTEL_CLOSED,result.unavailableOptions().get(0).reasons().get(0).code());
  assertEquals(new BigDecimal("805.00"),result.unavailableOptions().get(0).indicativeTotalPrice());
 }
 @Test void availableOptionsRemainSeparateAndSortedAndCannotBeDisplacedByUnavailableResults() {
  List<Candidate> all=new ArrayList<>();for(int i=1;i<=25;i++)all.add(candidate(i,i,i,1,1,"50","USD"));
  all.add(candidate(30,30,30,2,2,"200","USD"));all.add(candidate(31,31,31,2,2,"100","USD"));
  when(data.candidates(any())).thenReturn(all);var result=service.search(request(2));
  assertEquals(List.of(31,30),result.quotations().stream().map(Quotation::hotelId).toList());
  assertEquals(20,result.unavailableOptions().size());assertEquals(25,result.matchingUnavailableOptions());assertTrue(result.unavailableTruncated());
  assertFalse(result.truncated());
 }
 @Test void mixedTypesSatisfyDifferentRoomSpecificOccupancy() {
  when(hotels.existsById(1)).thenReturn(true);
  candidates(candidate(7,4,1,2,1,"100","USD"),candidate(8,4,1,1,1,"50","USD"));
  var q=service.customize(new QuotationSelectionRequest(1,4,in,out,List.of(new ReservationRequest.Room(7,2),new ReservationRequest.Room(8,1))));
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
  var result=service.search(request(1));assertTrue(result.truncated());assertEquals(25,result.matchingOptions());assertEquals(20,result.quotations().size());assertEquals("INDIVIDUAL_ROOM_TYPES",result.allocationPolicy());
 }

 @Test void individualTypesUseCompleteRequestWithAssignmentMarkup() {
  candidates(candidate(7,4,1,2,15,"35","USD"),candidate(8,4,1,3,8,"55","USD"));
  var result=service.search(request(2,1));
  assertEquals(List.of(new BigDecimal("362.25"),new BigDecimal("569.25")),result.quotations().stream().map(Quotation::totalPrice).toList());
  assertEquals(List.of(7,8),result.quotations().stream().map(q->q.rooms().get(0).offeringId()).toList());
  assertEquals(2,result.selectionOfferings().size());
 }
 @Test void individualFailuresStillAllowExplicitMixedSelection() {
  candidates(candidate(7,4,1,2,1,"35","USD"),candidate(8,4,1,3,1,"55","USD"));
  var result=service.search(request(2,1));assertTrue(result.quotations().isEmpty());assertEquals(2,result.unavailableOptions().size());
  when(hotels.existsById(1)).thenReturn(true);
  var q=service.customize(new QuotationSelectionRequest(1,4,in,out,List.of(new ReservationRequest.Room(7,2),new ReservationRequest.Room(8,1))));
  assertEquals(List.of(7,8),q.rooms().stream().map(RoomAllocation::offeringId).toList());
  assertEquals(new BigDecimal("431.25"),q.totalPrice());
 }
 QuotationSelectionRequest selection(int contract,int... ids) {
  return new QuotationSelectionRequest(1,contract,in,out,Arrays.stream(ids).mapToObj(id->new ReservationRequest.Room(id,1)).toList());
 }
 @Test void customizedSelectionPreservesMoreExpensiveChoiceAndAdultOrder() {
  when(hotels.existsById(1)).thenReturn(true);
  candidates(candidate(7,4,1,2,15,"35","USD"),candidate(8,4,1,3,8,"55","USD"));
  var q=service.customize(new QuotationSelectionRequest(1,4,in,out,List.of(new ReservationRequest.Room(8,1),new ReservationRequest.Room(7,2))));
  assertEquals(List.of(8,7),q.rooms().stream().map(RoomAllocation::offeringId).toList());
  assertEquals(List.of(1,2),q.rooms().stream().map(RoomAllocation::adults).toList());
  assertEquals(new BigDecimal("431.25"),q.totalPrice());
 }
 @Test void customizedRepeatedOfferingsShareInventoryAndRejectForeignReferences() {
  when(hotels.existsById(1)).thenReturn(true);
  candidates(candidate(7,4,1,2,1,"35","USD"),candidate(8,5,2,3,8,"55","USD"));
  assertThrows(QuotationSearchException.class,()->service.customize(selection(4,7,7)));
  assertThrows(QuotationSearchException.class,()->service.customize(selection(4,7,8)));
  assertThrows(QuotationSearchException.class,()->service.customize(selection(99,7)));
 }

 @Test void customizedCrossContractAndCrossHotelReferencesAreRejectedIndependently() {
  when(hotels.existsById(1)).thenReturn(true);
  candidates(candidate(7,4,1,2,3,"35","USD"),candidate(8,5,1,3,8,"55","USD"));
  assertThrows(QuotationSearchException.class,()->service.customize(selection(4,7,8)));
  candidates(candidate(7,4,1,2,3,"35","USD"),candidate(8,4,2,3,8,"55","USD"));
  assertThrows(QuotationSearchException.class,()->service.customize(selection(4,7,8)));
 }

 @ParameterizedTest @CsvSource({"1,2,true","2,2,true","3,2,false","3,3,true","5,6,true","7,6,false","12,12,true","13,12,false"})
 void contractualCapacityDeterminesAvailabilityWithoutRoomTypeFilter(int adults,int maximum,boolean available) {
  candidates(candidate(7,4,1,maximum,8,"100","USD"));
  var result=service.search(request(adults));assertEquals(available,!result.quotations().isEmpty());
  if(available) assertEquals(new BigDecimal("345.00").multiply(BigDecimal.valueOf(adults)),result.quotations().get(0).totalPrice());
  else assertEquals(ReasonCode.MAX_ADULT_OCCUPANCY,result.unavailableOptions().get(0).reasons().get(0).code());
 }
 @Test void differentCapacitiesAreEvaluatedIndependentlyForTheCompleteRequest() {
  candidates(candidate(7,4,1,2,8,"35","USD"),candidate(8,4,1,3,8,"55","USD"));
  var result=service.search(request(3,1));assertEquals(8,result.quotations().get(0).rooms().get(0).offeringId());
  assertEquals(7,result.unavailableOptions().get(0).offerings().get(0).offeringId());
 }
 @Test void largeRepresentableAdultsFailByOfferingCapacityWithoutOverflow() {
  candidates(candidate());var result=service.search(request(Integer.MAX_VALUE));
  assertTrue(result.quotations().isEmpty());assertNull(result.unavailableOptions().get(0).indicativeTotalPrice());
  assertEquals(ReasonCode.MAX_ADULT_OCCUPANCY,result.unavailableOptions().get(0).reasons().get(0).code());
 }
 @Test void customizedRoomsUseTheirOwnLimitsIncludingAboveTen() {
  when(hotels.existsById(1)).thenReturn(true);
  candidates(candidate(7,4,1,12,8,"100","USD"),candidate(8,4,1,6,8,"100","USD"));
  var q=service.customize(new QuotationSelectionRequest(1,4,in,out,List.of(new ReservationRequest.Room(7,12),new ReservationRequest.Room(8,5))));
  assertEquals(List.of(12,5),q.rooms().stream().map(RoomAllocation::adults).toList());
  assertEquals(new BigDecimal("5865.00"),q.totalPrice());
  assertThrows(QuotationSearchException.class,()->service.customize(new QuotationSelectionRequest(1,4,in,out,List.of(new ReservationRequest.Room(8,12)))));
 }
 @Test void eightRoomsRemainAllowedWithoutATotalAdultCeiling() {
  candidates(candidate(7,4,1,12,8,"100","USD"));
  assertEquals(8,service.search(request(12,12,12,12,12,12,12,12)).quotations().get(0).rooms().size());
  error(400,request(12,12,12,12,12,12,12,12,12));
 }
}
