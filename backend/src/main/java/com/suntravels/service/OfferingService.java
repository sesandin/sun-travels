package com.suntravels.service;

import com.suntravels.dto.OfferingDirectoryResponse;
import com.suntravels.dto.OfferingRateRequest;
import com.suntravels.dto.OfferingRequest;
import com.suntravels.dto.OfferingResponse;
import com.suntravels.dto.OfferingStatusRequest;
import com.suntravels.dto.SeasonalRateRequest;
import com.suntravels.dto.SeasonalRateResponse;
import com.suntravels.entity.Contract;
import com.suntravels.entity.ContractRoomOffering;
import com.suntravels.entity.ContractStatus;
import com.suntravels.entity.Hotel;
import com.suntravels.entity.OfferingSeasonalRate;
import com.suntravels.entity.RoomType;
import com.suntravels.exception.OfferingException;
import com.suntravels.repository.ContractRepository;
import com.suntravels.repository.OfferingRepository;
import com.suntravels.repository.RoomTypeRepository;
import com.suntravels.repository.SeasonalRateRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional( readOnly = true )
public class OfferingService
{
    private static final String OFFERING_LABEL = "Offering";
    private static final String CONTRACT_LABEL = "Contract";
    private final ContractRepository contracts;
    private final OfferingRepository offerings;
    private final RoomTypeRepository rooms;
    private final SeasonalRateRepository seasons;
    private final Validator validator;

    // Initializes the offering service and its required dependencies.
    public OfferingService(
            ContractRepository contracts, OfferingRepository offerings,
            RoomTypeRepository rooms, SeasonalRateRepository seasons, Validator validator )
    {
        this.contracts = contracts;
        this.offerings = offerings;
        this.rooms = rooms;
        this.seasons = seasons;
        this.validator = validator;
    }

    /**
     * Initial full administrative listing: two queries independent of row count.
     * Client paging bounds rendering, not database/result-set size.
     */
    public List<OfferingDirectoryResponse> directory()
    {
        var rows = offerings.findDirectoryOfferings();
        if( rows.isEmpty() )
        {
            return List.of();
        }
        java.util.Map<Integer,Long> counts = new java.util.HashMap<>();
        seasons.countPeriodsByOffering().forEach(
                count -> counts.put( count.getOfferingId(), count.getPeriodCount() ) );
        return rows.stream().map( offering ->
        {
            Contract contract = offering.getContract();
            Hotel h = contract.getHotel();
            return new OfferingDirectoryResponse( offering.getOfferingId(), h.getHotelId(), h.getName(),
                    h.getStatus(), contract.getContractId(), contract.getContractCode(), contract.getStatus(),
                    contract.getValidFrom(), contract.getValidTo(), offering.getRoomType().getRoomTypeId(),
                    offering.getRoomType().getName(), offering.getRatePerPersonPerNight(),
                    contract.getCurrencyCode(), offering.getContractedRoomCount(), offering.getMaxAdults(),
                    offering.getStatus(), counts.getOrDefault( offering.getOfferingId(), 0L ) );
        } ).toList();
    }

    // Lists room offering records matching the supplied filters.
    public List<OfferingResponse> list( Integer contractId )
    {
        contracts.findById( contractId ).orElseThrow(
                () -> OfferingException.missing( CONTRACT_LABEL, contractId ) );
        return offerings.findByContractContractIdOrderByOfferingId( contractId ).stream().map(
                this::response ).toList();
    }

    // Retrieves the requested room offering details.
    public OfferingResponse get( Integer id )
    {
        return response( find( id ) );
    }

    // Lists seasonal rate periods for the selected offering.
    public List<SeasonalRateResponse> seasonalRates( Integer id )
    {
        find( id );
        return seasons.findByOfferingOfferingIdOrderByValidFrom( id ).stream().map( this::response ).toList();
    }

    // Read-only hotelier-rate lookup; quotation selling prices use NightlyQuotationPricing.
    public java.math.BigDecimal effectiveRate( Integer id, java.time.LocalDate occupiedDate )
    {
        ContractRoomOffering offering = find( id );
        if( occupiedDate == null || occupiedDate.isBefore( offering.getContract().getValidFrom() ) ||
                    occupiedDate.isAfter( offering.getContract().getValidTo() ) )
        {
            throw OfferingException.invalid( "Occupied date must be within the contract validity period" );
        }
        var matches = seasons.findByOfferingOfferingIdOrderByValidFrom( id ).stream().filter(
                r -> !occupiedDate.isBefore( r.getValidFrom() ) &&
                             !occupiedDate.isAfter( r.getValidTo() ) ).toList();
        if( matches.size() > 1 )
        {
            throw OfferingException.conflict( "Overlapping persisted seasonal rates require correction" );
        }
        return matches.isEmpty() ? offering.getRatePerPersonPerNight() :
                       matches.get( 0 ).getRatePerPersonPerNight();
    }

    // Creates a room offering from the validated request.
    @Transactional
    public OfferingResponse create( Integer contractId, OfferingRequest request )
    {
        Contract contract = contracts.findForUpdate( contractId ).orElseThrow(
                () -> OfferingException.missing( CONTRACT_LABEL, contractId ) );
        require( contract, ContractStatus.DRAFT );
        validate( request );
        duplicate( contractId, request.roomTypeId(), 0 );
        RoomType room = room( request.roomTypeId() );
        ContractRoomOffering offering = new ContractRoomOffering();
        offering.setContract( contract );
        apply( offering, request, room );
        return response( offerings.saveAndFlush( offering ) );
    }

    // Updates the editable fields of the selected room offering.
    @Transactional
    public OfferingResponse update( Integer id, OfferingRequest request )
    {
        ContractRoomOffering offering = locked( id );
        require( offering.getContract(), ContractStatus.DRAFT );
        validate( request );
        duplicate( offering.getContract().getContractId(), request.roomTypeId(), id );
        RoomType room = room( request.roomTypeId() );
        apply( offering, request, room );
        return response( offerings.saveAndFlush( offering ) );
    }

    // Updates the selected offering’s base nightly rate.
    @Transactional
    public OfferingResponse rate( Integer id, OfferingRateRequest request )
    {
        ContractRoomOffering offering = locked( id );
        require( offering.getContract(), ContractStatus.APPROVED );
        validate( request );
        offering.setRatePerPersonPerNight( request.ratePerPersonPerNight() );
        return response( offerings.saveAndFlush( offering ) );
    }

    /**
     * Changes the sales status without modifying existing reservations or their inventory.
     * Re-enabling is administrative: an active hotel and approved contract are still
     * required by quotation and confirmation eligibility checks.
     */
    @Transactional
    public OfferingResponse status( Integer id, OfferingStatusRequest request )
    {
        ContractRoomOffering offering = locked( id );
        if( offering.getContract().getStatus() == ContractStatus.TERMINATED )
        {
            throw OfferingException.conflict( "Offering status cannot change under a TERMINATED contract" );
        }
        validate( request );
        offering.setStatus( request.status() );
        return response( offerings.saveAndFlush( offering ) );
    }

    // Creates a seasonal rate period for the selected offering.
    @Transactional
    public SeasonalRateResponse createSeason( Integer id, SeasonalRateRequest request )
    {
        return saveSeason( id, null, request );
    }

    // Updates an existing seasonal rate period.
    @Transactional
    public SeasonalRateResponse updateSeason( Integer id, Integer rateId, SeasonalRateRequest request )
    {
        return saveSeason( id, rateId, request );
    }

    // Validates and saves a seasonal rate within its owning offering and contract.
    private SeasonalRateResponse saveSeason( Integer id, Integer rateId, SeasonalRateRequest request )
    {
        ContractRoomOffering offering = locked( id );
        maintainable( offering.getContract() );
        validate( request );
        OfferingSeasonalRate season = rateId == null ? new OfferingSeasonalRate() :
                                              seasons.findBySeasonalRateIdAndOfferingOfferingId( rateId, id ).orElseThrow(
                                                      () -> OfferingException.missing( "Seasonal rate for this offering", rateId ) );
        Contract contract = offering.getContract();
        if( request.validFrom().isBefore( contract.getValidFrom() ) ||
                    request.validTo().isAfter( contract.getValidTo() ) )
        {
            throw OfferingException.invalid( "Seasonal dates must remain within the contract validity period" );
        }
        if( !seasons.overlaps( id, rateId == null ? 0 : rateId, request.validFrom(),
                request.validTo() ).isEmpty() )
        {
            throw OfferingException.conflict(
                    "Seasonal period overlaps another period for this offering (dates are inclusive)" );
        }
        if( rateId == null )
        {
            season.setOffering( offering );
        }
        season.setValidFrom( request.validFrom() );
        season.setValidTo( request.validTo() );
        season.setRatePerPersonPerNight( request.ratePerPersonPerNight() );
        return response( seasons.saveAndFlush( season ) );
    }

    // Future hotel coordination must use HOTEL -> CONTRACT -> OFFERING -> SEASONAL RATE.
    // Scalar lookup cannot retain stale lifecycle state before acquiring the contract lock.
    private ContractRoomOffering locked( Integer id )
    {
        Integer contractId = offerings.findContractId( id ).orElseThrow(
                () -> OfferingException.missing( OFFERING_LABEL, id ) );
        contracts.findForUpdate( contractId ).orElseThrow(
                () -> OfferingException.missing( CONTRACT_LABEL, contractId ) );
        return offerings.findForUpdate( id ).orElseThrow( () -> OfferingException.missing( OFFERING_LABEL, id ) );
    }

    // Finds an offering or reports that it does not exist.
    private ContractRoomOffering find( Integer id )
    {
        return offerings.findById( id ).orElseThrow( () -> OfferingException.missing( OFFERING_LABEL, id ) );
    }

    // Finds the room type referenced by an offering request.
    private RoomType room( Integer id )
    {
        return rooms.findById( id ).orElseThrow( () -> OfferingException.missing( "Room type", id ) );
    }

    // Rejects duplicate room-type offerings within the same contract.
    private void duplicate( Integer contractId, Integer roomId, Integer excluded )
    {
        if( offerings.existsByContractContractIdAndRoomTypeRoomTypeIdAndOfferingIdNot( contractId, roomId,
                excluded ) )
        {
            throw OfferingException.conflict( "Room type already appears in this contract" );
        }
    }

    // Rejects an operation unless the contract has the required status.
    private void require( Contract contract, ContractStatus status )
    {
        if( contract.getStatus() != status )
        {
            throw OfferingException.conflict( "This operation requires a " + status + " contract" );
        }
    }

    // Restricts offering and seasonal maintenance to supported contract statuses.
    private void maintainable( Contract contract )
    {
        if( contract.getStatus() != ContractStatus.DRAFT && contract.getStatus() != ContractStatus.APPROVED )
        {
            throw OfferingException.conflict(
                    "Offering and seasonal maintenance requires a DRAFT or APPROVED contract" );
        }
    }

    // Validates offering or seasonal input before maintenance.
    private void validate( Object request )
    {
        if( request == null )
        {
            throw OfferingException.invalid( "Details are required" );
        }
        var violations = validator.validate( request );
        if( !violations.isEmpty() )
        {
            throw OfferingException.invalid(
                    violations.stream().map( v -> v.getPropertyPath() + ": " + v.getMessage() ).sorted().reduce(
                            ( a, b ) -> a + "; " + b ).orElseThrow() );
        }
    }

    // Copies editable room type, rate, allocation, adult capacity, and status fields.
    private void apply( ContractRoomOffering offering, OfferingRequest r, RoomType room )
    {
        offering.setRoomType( room );
        offering.setRatePerPersonPerNight( r.ratePerPersonPerNight() );
        offering.setContractedRoomCount( r.contractedRoomCount() );
        offering.setMaxAdults( r.maxAdults() );
        offering.setStatus( r.status() );
    }

    // Maps an offering or seasonal rate entity to its API response.
    private OfferingResponse response( ContractRoomOffering offering )
    {
        return new OfferingResponse( offering.getOfferingId(), offering.getContract().getContractId(),
                offering.getRoomType().getRoomTypeId(), offering.getRoomType().getName(),
                offering.getRatePerPersonPerNight(), offering.getContractedRoomCount(),
                offering.getMaxAdults(), offering.getStatus(), offering.getContract().getCurrencyCode() );
    }

    // Maps an offering or seasonal rate entity to its API response.
    private SeasonalRateResponse response( OfferingSeasonalRate season )
    {
        return new SeasonalRateResponse( season.getSeasonalRateId(), season.getOffering().getOfferingId(),
                season.getValidFrom(), season.getValidTo(), season.getRatePerPersonPerNight() );
    }
}
