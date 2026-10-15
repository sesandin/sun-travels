package com.suntravels.integration;
import com.suntravels.dto.*;
import com.suntravels.dto.QuotationSearchRequest.RequestedRoom;
import com.suntravels.entity.*;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.*;
import com.suntravels.service.QuotationSearchService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
/** Explicit opt-in AND _test database suffix. Fixtures rollback; never use operational data. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="SUNTRAVELS_SEARCH_MYSQL_TESTS",matches="true")
class QuotationSearchMySqlIntegrationTest {
 @Autowired QuotationSearchService search; @Autowired HotelRepository hotels; @Autowired RoomTypeRepository types;
 @Autowired ContractRepository contracts; @Autowired OfferingRepository offerings; @Autowired SeasonalRateRepository seasons;
 @Autowired JdbcTemplate jdbc; @Autowired Clock clock;
 Hotel hotel; Contract contract; ContractRoomOffering offer; RoomType type; LocalDate in,out; String city;
 @BeforeEach void seed() {
  assertTrue(jdbc.queryForObject("select database()",String.class).endsWith("_test"),"Use a disposable database whose name ends _test");
  assertTrue(jdbc.queryForObject("select version()",String.class).startsWith("8."));
  in=LocalDate.now(clock).plusDays(10);out=in.plusDays(3);city="SearchCity-"+UUID.randomUUID();
  hotel=new Hotel();hotel.setName("Search fixture "+UUID.randomUUID());hotel.setCity(city);hotel.setStatus(HotelStatus.ACTIVE);hotel=hotels.saveAndFlush(hotel);
  type=new RoomType();type.setName("Search Double "+UUID.randomUUID());type=types.saveAndFlush(type);
  contract=newContract(ContractStatus.APPROVED,in.minusDays(3),out.plusDays(30));
  offer=newOffering(contract,type,3,2,"100");
 }
 Contract newContract(ContractStatus status,LocalDate from,LocalDate to) {
  Contract c=new Contract();c.setHotel(hotel);c.setContractCode("S-"+UUID.randomUUID());c.setStatus(status);c.setValidFrom(from);c.setValidTo(to);c.setCurrencyCode("USD");c.setMarkupPercentage(new BigDecimal("15.00"));return contracts.saveAndFlush(c);
 }
 ContractRoomOffering newOffering(Contract c,RoomType room,int count,int adults,String rate) {
  ContractRoomOffering o=new ContractRoomOffering();o.setContract(c);o.setRoomType(room);o.setStatus(OfferingStatus.ENABLED);o.setRatePerPersonPerNight(new BigDecimal(rate));o.setContractedRoomCount(count);o.setMaxAdults(adults);return offerings.saveAndFlush(o);
 }
 QuotationSearchRequest request(int... adults) { return new QuotationSearchRequest(in,out,Arrays.stream(adults).mapToObj(RequestedRoom::new).toList(),null,hotel.getHotelId(),null); }
 Integer booking(ContractRoomOffering o,String status,int start,int end,int items) {
  jdbc.update("insert into customer(name) values (?)","Search customer "+UUID.randomUUID());Integer customer=jdbc.queryForObject("select last_insert_id()",Integer.class);
  jdbc.update("insert into reservation(check_in_date,check_out_date,status,customer_id,currency_code) values (?,?,?,?,?)",in.plusDays(start),in.plusDays(end),status,customer,"USD");Integer reservation=jdbc.queryForObject("select last_insert_id()",Integer.class);
  for(int i=0;i<items;i++) jdbc.update("insert into reservation_item(number_of_adults,offering_id,reservation_id,booked_rate_per_person_per_night,booked_markup_percentage,booked_selling_price) values (?,?,?,?,?,?)",2,o.getOfferingId(),reservation,new BigDecimal("100"),new BigDecimal("15"),new BigDecimal("230"));
  return reservation;
 }
 void season(LocalDate from,LocalDate to,String rate) { OfferingSeasonalRate s=new OfferingSeasonalRate();s.setOffering(offer);s.setValidFrom(from);s.setValidTo(to);s.setRatePerPersonPerNight(new BigDecimal(rate));seasons.saveAndFlush(s); }
 @ParameterizedTest @CsvSource({"ACTIVE,APPROVED,ENABLED,true","INACTIVE,APPROVED,ENABLED,false","ACTIVE,DRAFT,ENABLED,false","ACTIVE,SUSPENDED,ENABLED,false","ACTIVE,TERMINATED,ENABLED,false","ACTIVE,APPROVED,SUSPENDED,false"})
 void actualSqlAppliesHotelContractAndOfferingEligibility(String h,String c,String o,boolean match) {
  hotel.setStatus(HotelStatus.valueOf(h));hotels.saveAndFlush(hotel);contract.setStatus(ContractStatus.valueOf(c));contracts.saveAndFlush(contract);offer.setStatus(OfferingStatus.valueOf(o));offerings.saveAndFlush(offer);
  assertEquals(match,!search.search(request(2)).quotations().isEmpty());
 }
 @Test void fullStayContractValidityIsInclusiveForNightsAndExclusiveForCheckout() {
  contract.setValidTo(out.minusDays(1));contracts.saveAndFlush(contract);assertEquals(1,search.search(request(2)).quotations().size());
  contract.setValidTo(out.minusDays(2));contracts.saveAndFlush(contract);assertTrue(search.search(request(2)).quotations().isEmpty());
 }
 static Stream<Arguments> closures() { return Stream.of(Arguments.of(1,1,false),Arguments.of(3,4,true),Arguments.of(-2,-1,true),Arguments.of(-2,0,false),Arguments.of(-2,null,false),Arguments.of(3,null,true)); }
 @ParameterizedTest @MethodSource("closures") void closureSqlRespectsInclusiveBoundsAndOpenEnd(int start,Integer end,boolean match) {
  jdbc.update("insert into hotel_closure(hotel_id,start_date,end_date,reason) values (?,?,?,?)",hotel.getHotelId(),in.plusDays(start),end==null?null:in.plusDays(end),"Search fixture");assertEquals(match,!search.search(request(1)).quotations().isEmpty());
 }
 @ParameterizedTest @ValueSource(strings={"PENDING","CANCELLED"}) void nonConfirmedReservationsDoNotConsumeInventory(String status) { booking(offer,status,0,3,3);assertEquals(3,search.search(request(1)).quotations().get(0).rooms().get(0).availableRoomsForStay()); }
 @Test void oneItemIsOneRoomAndPeakNightIsNotSumAcrossNights() {
  booking(offer,"CONFIRMED",0,1,1);booking(offer,"CONFIRMED",1,3,1);
  assertEquals(2,search.search(request(2,1)).quotations().get(0).rooms().get(0).availableRoomsForStay());
 }
 @Test void multipleItemsAndExclusiveCheckoutsAreCountedPerNight() {
  booking(offer,"CONFIRMED",-3,0,3);booking(offer,"CONFIRMED",3,5,3);booking(offer,"CONFIRMED",0,3,2);
  assertEquals(1,search.search(request(2)).quotations().get(0).rooms().get(0).availableRoomsForStay());assertTrue(search.search(request(2,1)).quotations().isEmpty());
 }
 @Test void fullyBookedAndOverbookedHaveDifferentOutcomes() {
  booking(offer,"CONFIRMED",0,3,3);assertTrue(search.search(request(1)).quotations().isEmpty());booking(offer,"CONFIRMED",1,2,1);assertEquals(409,assertThrows(QuotationSearchException.class,()->search.search(request(1))).getStatus());
 }
 @Test void unrelatedOfferingConsumptionDoesNotReduceRequestedRoomInventory() {
  RoomType other=new RoomType();other.setName("Other "+UUID.randomUUID());other=types.saveAndFlush(other);ContractRoomOffering o=newOffering(contract,other,3,2,"150");booking(o,"CONFIRMED",0,3,3);
  var result=search.search(new QuotationSearchRequest(in,out,List.of(new RequestedRoom(2)),null,hotel.getHotelId(),type.getRoomTypeId()));assertEquals(3,result.quotations().get(0).rooms().get(0).availableRoomsForStay());
 }
 @Test void mixedRoomAllocationUsesActualCapacityAndSeasonalPricing() {
  offer.setContractedRoomCount(1);offerings.saveAndFlush(offer);RoomType other=new RoomType();other.setName("Single "+UUID.randomUUID());other=types.saveAndFlush(other);newOffering(contract,other,1,1,"50");season(in.plusDays(1),in.plusDays(1),"150");
  var q=search.search(request(2,1)).quotations().get(0);assertEquals(new BigDecimal("977.50"),q.totalPrice());assertEquals(offer.getOfferingId(),q.rooms().get(0).offeringId());assertEquals(other.getRoomTypeId(),q.rooms().get(1).roomTypeId());
 }
 @Test void cityAndReferenceFiltersUseRealExistingRecords() {
  assertEquals(1,search.search(new QuotationSearchRequest(in,out,List.of(new RequestedRoom(1))," "+city.toLowerCase(Locale.ROOT)+" ",hotel.getHotelId(),type.getRoomTypeId())).quotations().size());
  assertTrue(search.search(new QuotationSearchRequest(in,out,List.of(new RequestedRoom(1)),"No matching city",hotel.getHotelId(),null)).quotations().isEmpty());
 }
 @ParameterizedTest @EnumSource(value=ContractStatus.class,names={"SUSPENDED","TERMINATED"}) void oldContractCommitmentsOutsideOldValidityCannotBecomeExtraInventory(ContractStatus status) {
  Contract old=newContract(status,in.minusDays(100),in.minusDays(50));ContractRoomOffering prior=newOffering(old,type,3,2,"100");booking(prior,"CONFIRMED",0,3,1);
  assertEquals(409,assertThrows(QuotationSearchException.class,()->search.search(request(1))).getStatus());
 }
 @Test void malformedOrDuplicateApprovalDataFailsClosed() {
  newContract(ContractStatus.APPROVED,in.minusDays(1),out);assertEquals(409,assertThrows(QuotationSearchException.class,()->search.search(request(1))).getStatus());
 }
 @Test void overlappingSeasonalDataIsNeverArbitrarilySelected() {
  season(in,in.plusDays(1),"120");season(in.plusDays(1),out,"130");assertEquals(409,assertThrows(QuotationSearchException.class,()->search.search(request(1))).getStatus());
 }
 @Test void searchDoesNotMutateConfirmedReservationOrSnapshotRows() {
  Integer reservation=booking(offer,"CONFIRMED",0,3,1);Integer item=jdbc.queryForObject("select reservation_item_id from reservation_item where reservation_id=?",Integer.class,reservation);
  jdbc.update("insert into reservation_item_nightly_rate(reservation_item_id,occupied_date,hotelier_rate,markup_percentage,selling_price) values (?,?,?,?,?)",item,in,new BigDecimal("100"),new BigDecimal("15"),new BigDecimal("230"));
  var r=jdbc.queryForMap("select * from reservation where reservation_id=?",reservation);var i=jdbc.queryForMap("select * from reservation_item where reservation_item_id=?",item);var n=jdbc.queryForMap("select * from reservation_item_nightly_rate where reservation_item_id=?",item);
  search.search(request(2));assertEquals(r,jdbc.queryForMap("select * from reservation where reservation_id=?",reservation));assertEquals(i,jdbc.queryForMap("select * from reservation_item where reservation_item_id=?",item));assertEquals(n,jdbc.queryForMap("select * from reservation_item_nightly_rate where reservation_item_id=?",item));
 }
}
