package com.suntravels.service;
import com.suntravels.dto.*;
import com.suntravels.entity.*;
import com.suntravels.exception.*;
import com.suntravels.repository.*;
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
import org.springframework.dao.*;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {
 @Mock ContractRepository repository; @Mock HotelRepository hotels; @Mock SeasonalRateRepository seasons;
 @Mock OfferingRepository offerings; @Mock ReservationCommitmentRepository commitments;
 ContractService service; ValidatorFactory factory; Hotel hotel; Contract c; ContractRoomOffering offering;
 final LocalDate from=LocalDate.of(2026,1,1),to=LocalDate.of(2026,12,31),today=LocalDate.of(2026,10,14);
 @BeforeEach void setup() {
  factory=Validation.buildDefaultValidatorFactory(); service=new ContractService(repository,hotels,factory.getValidator(),seasons,offerings,commitments,Clock.fixed(Instant.parse("2026-10-13T20:00:00Z"),ZoneId.of("Asia/Colombo")));
  hotel=new Hotel(); ReflectionTestUtils.setField(hotel,"hotelId",7); hotel.setName("Hotel"); hotel.setStatus(HotelStatus.ACTIVE);
  c=new Contract(); ReflectionTestUtils.setField(c,"contractId",1); c.setContractCode("LEGACY"); c.setHotel(hotel); c.setValidFrom(from); c.setValidTo(to); c.setMarkupPercentage(BigDecimal.TEN); c.setCurrencyCode("USD"); c.setStatus(ContractStatus.DRAFT);
  RoomType room=new RoomType(); ReflectionTestUtils.setField(room,"roomTypeId",2); room.setName("Double");
  offering=new ContractRoomOffering(); ReflectionTestUtils.setField(offering,"offeringId",3); offering.setContract(c); offering.setRoomType(room); offering.setStatus(OfferingStatus.ENABLED); offering.setRatePerPersonPerNight(BigDecimal.TEN); offering.setContractedRoomCount(10); offering.setMaxAdults(2);
 }
 @AfterEach void close() { factory.close(); }
 ContractRequest request() { return new ContractRequest(7,from,to,new BigDecimal("15.00"),"USD"); }
 void locked() { when(repository.findHotelId(1)).thenReturn(Optional.of(7)); when(hotels.findForUpdate(7)).thenReturn(Optional.of(hotel)); when(repository.findForUpdate(1)).thenReturn(Optional.of(c)); }
 void saving() { when(repository.saveAndFlush(any())).thenAnswer(i->i.getArgument(0)); }
 void approvalReady() { locked(); when(offerings.findAllForUpdate(1)).thenReturn(List.of(offering)); }
 void error(int status,Runnable work) { assertEquals(status,assertThrows(ContractException.class,work::run).getStatus()); }
 @Test void generatesCodeAfterIdentityWithoutReturningTemporaryValue() {
  when(hotels.findForUpdate(7)).thenReturn(Optional.of(hotel)); List<String> codes=new ArrayList<>();
  when(repository.saveAndFlush(any())).thenAnswer(i->{ Contract row=i.getArgument(0); codes.add(row.getContractCode()); ReflectionTestUtils.setField(row,"contractId",4); return row; });
  var r=service.createContract(request()); assertEquals("C004",r.contractCode()); assertEquals(ContractStatus.DRAFT,r.status()); assertTrue(codes.get(0).startsWith("TMP-")); assertTrue(codes.get(0).length()<=45); assertEquals("C004",codes.get(1)); verify(repository).existsByContractCodeAndContractIdNot("C004",4);
 }
 @ParameterizedTest @CsvSource({"1,C001","42,C042","999,C999","1000,C1000","2147483647,C2147483647"}) void codeFormatUsesMinimumPadding(int id,String expected) { assertEquals(expected,ContractService.generatedCode(id)); }
 @Test void codeCollisionFailsWithoutSecondSaveOrOverwritingExistingRow() {
  when(hotels.findForUpdate(7)).thenReturn(Optional.of(hotel)); when(repository.saveAndFlush(any())).thenAnswer(i->{Contract row=i.getArgument(0); ReflectionTestUtils.setField(row,"contractId",4); return row;}); when(repository.existsByContractCodeAndContractIdNot("C004",4)).thenReturn(true);
  error(409,()->service.createContract(request())); verify(repository,times(1)).saveAndFlush(any()); verify(repository,never()).findById(any());
 }
 @Test void databaseCreationRaceHasSafeError() { when(hotels.findForUpdate(7)).thenReturn(Optional.of(hotel)); when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("secret SQL"));
   var requestForAssertion1 = request();
   var ex=assertThrows(ContractException.class,()->service.createContract(requestForAssertion1)); assertEquals(409,ex.getStatus()); assertFalse(ex.getMessage().contains("secret")); }
 @Test void invalidHeadersBeforeAnyWrite() {
  for(var r:List.of(new ContractRequest(7,to,from,BigDecimal.ZERO,"USD"),new ContractRequest(7,from,to,new BigDecimal("-0.01"),"USD"),new ContractRequest(7,from,to,new BigDecimal("1000"),"USD"),new ContractRequest(7,from,to,new BigDecimal("1.001"),"USD"),new ContractRequest(7,from,to,BigDecimal.ZERO,"usd"))) error(400,()->service.createContract(r)); verifyNoInteractions(repository,hotels);
 }
 @Test void missingHotelAndContract() {
   var requestForAssertion2 = request();
   assertThrows(HotelNotFoundException.class,()->service.createContract(requestForAssertion2)); error(404,()->service.approveContract(1)); error(404,()->service.terminateContract(1)); }
 @Test void draftHeaderUpdatesPreserveLegacyCode() { locked(); saving(); service.updateContract(1,request()); assertEquals("LEGACY",c.getContractCode()); assertEquals(new BigDecimal("15.00"),c.getMarkupPercentage()); }
 @Test void shrinkingDatesCannotExcludeSeasonalPeriod() { locked(); when(seasons.countOutsideContract(1,from,to)).thenReturn(1L); error(409,()->service.updateContract(1,request())); assertEquals(BigDecimal.TEN,c.getMarkupPercentage()); verify(repository,never()).saveAndFlush(any()); }
 @ParameterizedTest @EnumSource(value=ContractStatus.class,names={"APPROVED","SUSPENDED","TERMINATED"}) void nonDraftHeaderImmutable(ContractStatus status) { c.setStatus(status); locked(); error(409,()->service.updateContract(1,request())); assertEquals("LEGACY",c.getContractCode()); }
 @Test void approvedMarkupChangesOnlyMarkup() { c.setStatus(ContractStatus.APPROVED); locked(); saving(); service.updateMarkup(1,new ContractMarkupRequest(new BigDecimal("25"))); assertEquals("LEGACY",c.getContractCode()); assertSame(hotel,c.getHotel()); assertEquals(from,c.getValidFrom()); verifyNoInteractions(commitments,offerings,seasons); }
 @ParameterizedTest @EnumSource(value=ContractStatus.class,names={"DRAFT","SUSPENDED","TERMINATED"}) void markupRequiresApproved(ContractStatus status) { c.setStatus(status); locked(); error(409,()->service.updateMarkup(1,new ContractMarkupRequest(BigDecimal.ZERO))); }
 @Test void suspensionDoesNotCancelOrQueryReservations() { c.setStatus(ContractStatus.APPROVED); locked(); saving(); assertEquals(ContractStatus.SUSPENDED,service.suspendContract(1).status()); verifyNoInteractions(commitments); assertEquals(BigDecimal.TEN,c.getMarkupPercentage()); }
 @ParameterizedTest @EnumSource(value=ContractStatus.class,names={"DRAFT","SUSPENDED","TERMINATED"}) void invalidSuspension(ContractStatus status) { c.setStatus(status); locked(); error(409,()->service.suspendContract(1)); }
 @Test void initialApprovalLocksHotelFirstAndChangesOnlyCandidate() { approvalReady(); saving(); assertEquals(ContractStatus.APPROVED,service.approveContract(1).status()); assertEquals("LEGACY",c.getContractCode()); assertEquals(HotelStatus.ACTIVE,hotel.getStatus()); var order=inOrder(repository,hotels,offerings,seasons,commitments); order.verify(repository).findHotelId(1); order.verify(hotels).findForUpdate(7); order.verify(repository).findForUpdate(1); order.verify(offerings).findAllForUpdate(1); order.verify(seasons).findContractRatesForUpdate(1); order.verify(repository).approvedOverlaps(7,1,from,to); order.verify(commitments).invalidForContract(1); order.verify(commitments).invalidForReplacement(7,1,Set.of(2)); order.verify(commitments).replacementConflicts(7,1,from,to,today,Set.of(2)); order.verify(repository).saveAndFlush(c); }
 @Test void futureValidityCanBeApproved() { c.setValidFrom(today.plusDays(30)); approvalReady(); saving(); assertEquals(ContractStatus.APPROVED,service.approveContract(1).status()); }
 @Test void expiredContractCanRemainApprovedAndIsNotTerminated() { c.setValidTo(today.minusDays(1)); approvalReady(); saving(); assertEquals(ContractStatus.APPROVED,service.approveContract(1).status()); }
 @Test void reapprovesSuspendedThroughSameGuards() { c.setStatus(ContractStatus.SUSPENDED); approvalReady(); saving(); assertEquals(ContractStatus.APPROVED,service.reapproveContract(1).status()); verify(commitments).replacementConflicts(7,1,from,to,today,Set.of(2)); }
 @ParameterizedTest @EnumSource(value=ContractStatus.class,names={"APPROVED","SUSPENDED","TERMINATED"}) void onlyDraftInitialApproval(ContractStatus status) { c.setStatus(status); locked(); error(409,()->service.approveContract(1)); assertEquals(status,c.getStatus()); }
 @ParameterizedTest @EnumSource(value=ContractStatus.class,names={"DRAFT","APPROVED","TERMINATED"}) void onlySuspendedReapproval(ContractStatus status) { c.setStatus(status); locked(); error(409,()->service.reapproveContract(1)); }
 @Test void inactiveHotelIsNeverActivated() { hotel.setStatus(HotelStatus.INACTIVE); locked(); error(409,()->service.approveContract(1)); assertEquals(HotelStatus.INACTIVE,hotel.getStatus()); assertEquals(ContractStatus.DRAFT,c.getStatus()); }
 @Test void approvalRequiresEnabledOffering() { locked(); error(409,()->service.approveContract(1)); when(offerings.findAllForUpdate(1)).thenReturn(List.of(offering)); offering.setStatus(OfferingStatus.SUSPENDED); error(409,()->service.approveContract(1)); }
 @ParameterizedTest @ValueSource(strings={"0","-1","100000000","1.001"}) void persistedInvalidRateBlocksApproval(String value) { approvalReady(); offering.setRatePerPersonPerNight(new BigDecimal(value)); error(409,()->service.approveContract(1)); assertEquals(ContractStatus.DRAFT,c.getStatus()); }
 @Test void invalidOfferingInventoryCapacityAndHeaderFailClosed() { approvalReady(); offering.setContractedRoomCount(0); error(409,()->service.approveContract(1)); offering.setContractedRoomCount(1); offering.setMaxAdults(0); error(409,()->service.approveContract(1)); offering.setMaxAdults(1); c.setValidFrom(to.plusDays(1)); error(409,()->service.approveContract(1)); }
 @Test void overlapsBlockApprovalAndReapprovalWithoutSuspendingOther() { approvalReady(); Contract other=new Contract(); other.setContractCode("C099"); other.setStatus(ContractStatus.APPROVED); ReflectionTestUtils.setField(other,"contractId",99); when(repository.approvedOverlaps(7,1,from,to)).thenReturn(List.of(other)); var ex=assertThrows(ContractException.class,()->service.approveContract(1)); assertTrue(ex.getMessage().contains("C099")); c.setStatus(ContractStatus.SUSPENDED); error(409,()->service.reapproveContract(1)); assertEquals(ContractStatus.APPROVED,other.getStatus()); assertEquals(ContractStatus.SUSPENDED,c.getStatus()); verify(repository,never()).saveAndFlush(any()); }
 @Test void replacementCommitmentRejectsWithoutMutation() { approvalReady(); when(commitments.replacementConflicts(7,1,from,to,today,Set.of(2))).thenReturn(List.of(50)); error(409,()->service.approveContract(1)); assertEquals(ContractStatus.DRAFT,c.getStatus()); verify(repository,never()).saveAndFlush(any()); }
 @Test void invalidConfirmedDataCannotBeIgnored() { approvalReady(); when(commitments.invalidForContract(1)).thenReturn(List.of(50)); error(409,()->service.approveContract(1)); error(409,()->service.terminateContract(1)); verify(repository,never()).saveAndFlush(any()); }
 @ParameterizedTest @EnumSource(value=ContractStatus.class,names={"DRAFT","APPROVED","SUSPENDED"}) void terminationAllowedWithoutOutstandingCommitments(ContractStatus status) { c.setStatus(status); locked(); saving(); assertEquals(ContractStatus.TERMINATED,service.terminateContract(1).status()); verify(commitments).outstanding(1,today); }
 @ParameterizedTest @EnumSource(value=ContractStatus.class,names={"DRAFT","APPROVED","SUSPENDED"}) void ongoingOrFutureConfirmedCommitmentsBlockTermination(ContractStatus status) { c.setStatus(status); locked(); when(commitments.outstanding(1,today)).thenReturn(List.of(50)); error(409,()->service.terminateContract(1)); assertEquals(status,c.getStatus()); verify(repository,never()).saveAndFlush(any()); }
 @Test void terminalContractHasNoOutgoingTransitions() { c.setStatus(ContractStatus.TERMINATED); locked(); error(409,()->service.terminateContract(1)); error(409,()->service.reapproveContract(1)); error(409,()->service.approveContract(1)); verifyNoInteractions(commitments); }
 @Test void databaseGuardFailureDoesNotAssumeZero() { locked(); when(commitments.outstanding(1,today)).thenThrow(new DataAccessResourceFailureException("secret")); assertThrows(DataAccessResourceFailureException.class,()->service.terminateContract(1)); assertEquals(ContractStatus.DRAFT,c.getStatus()); verify(repository,never()).saveAndFlush(any()); }
 @Test void staleHotelLookupFailsRatherThanUsesWrongLock() { locked(); Hotel other=new Hotel(); ReflectionTestUtils.setField(other,"hotelId",8); c.setHotel(other); error(409,()->service.approveContract(1)); verifyNoInteractions(offerings,commitments); }
 @Test void hotelReassignmentLocksBothHotelsInIdOrder() { locked(); Hotel other=new Hotel(); ReflectionTestUtils.setField(other,"hotelId",4); other.setName("Other"); when(hotels.findForUpdate(4)).thenReturn(Optional.of(other)); saving(); service.updateContract(1,new ContractRequest(4,from,to,BigDecimal.TEN,"USD")); var order=inOrder(hotels,repository); order.verify(repository).findHotelId(1); order.verify(hotels).findForUpdate(4); order.verify(hotels).findForUpdate(7); order.verify(repository).findForUpdate(1); assertSame(other,c.getHotel()); assertEquals("LEGACY",c.getContractCode()); }
 @Test void historicalContractsRemainListable() { c.setStatus(ContractStatus.TERMINATED); when(repository.findById(1)).thenReturn(Optional.of(c)); when(repository.findAll(any(Sort.class))).thenReturn(List.of(c)); assertEquals(ContractStatus.TERMINATED,service.getContractById(1).status()); assertEquals(1,service.getAllContracts().size()); }
 @Test void corruptSeasonalOverlapPreventsApproval() { approvalReady(); var rate=new OfferingSeasonalRate(); rate.setOffering(offering); rate.setValidFrom(from); rate.setValidTo(to); rate.setRatePerPersonPerNight(BigDecimal.TEN); when(seasons.findContractRatesForUpdate(1)).thenReturn(List.of(rate,rate)); error(409,()->service.approveContract(1)); assertEquals(ContractStatus.DRAFT,c.getStatus()); }

 @Test void terminationReportsDistinctReservationCountWithoutSaving() {
  c.setStatus(ContractStatus.APPROVED);locked();when(commitments.outstanding(1,today)).thenReturn(List.of(50,50,51));
  var ex=assertThrows(ContractException.class,()->service.terminateContract(1));
  assertTrue(ex.getMessage().contains("2 ongoing or upcoming confirmed reservations"));
  assertEquals(ContractStatus.APPROVED,c.getStatus());verify(repository,never()).saveAndFlush(any());
 }
}
