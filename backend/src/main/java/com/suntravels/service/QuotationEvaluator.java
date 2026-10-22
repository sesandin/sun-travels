package com.suntravels.service;

import com.suntravels.dto.ContractRequest;
import com.suntravels.dto.OfferingRequest;
import com.suntravels.dto.QuotationSearchRequest;
import com.suntravels.dto.QuotationSearchResponse.AvailabilityStatus;
import com.suntravels.dto.QuotationSearchResponse.Quotation;
import com.suntravels.dto.QuotationSearchResponse.ReasonCode;
import com.suntravels.dto.QuotationSearchResponse.RoomAllocation;
import com.suntravels.dto.QuotationSearchResponse.SelectionOffering;
import com.suntravels.dto.QuotationSearchResponse.SelectionRoom;
import com.suntravels.dto.QuotationSearchResponse.UnavailabilityReason;
import com.suntravels.dto.QuotationSearchResponse.UnavailableOffering;
import com.suntravels.dto.QuotationSearchResponse.UnavailableOption;
import com.suntravels.dto.QuotationSelectionRequest;
import com.suntravels.entity.OfferingStatus;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.QuotationSearchRepository;
import com.suntravels.repository.QuotationSearchRepository.BookedRoom;
import com.suntravels.repository.QuotationSearchRepository.Candidate;
import com.suntravels.repository.QuotationSearchRepository.Season;
import jakarta.validation.Validator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Shared evaluation for legacy search, exact selections and hotel candidate pages. */
final class QuotationEvaluator
{
    private static final int OFFERING_BATCH_SIZE = 200;
    private final QuotationSearchRepository data;
    private final Validator validator;
    private final NightlyQuotationPricing pricing;
    private final StayAvailability inventory;

    QuotationEvaluator( QuotationSearchRepository data, Validator validator,
                        NightlyQuotationPricing pricing, StayAvailability inventory )
    {
        this.data = data;
        this.validator = validator;
        this.pricing = pricing;
        this.inventory = inventory;
    }

    record Evaluation(List<Quotation> quotations, List<UnavailableOption> unavailable,
                      List<SelectionOffering> choices) {}

    Evaluation evaluate( QuotationSearchRequest request, QuotationSelectionRequest selection,
                         List<Candidate> candidates, LocalDate today, int nights )
    {
        if( candidates.isEmpty() ) return new Evaluation( List.of(), List.of(), List.of() );
        Set<Integer> ids = candidates.stream().map( Candidate::offeringId ).collect( Collectors.toSet() );
        Set<Integer> contracts = candidates.stream().map( Candidate::contractId ).collect( Collectors.toSet() );
        if( data.inconsistent( ids, contracts, today ) )
        {
            throw QuotationSearchException.inconsistent();
        }
        List<Season> seasons = new ArrayList<>();
        List<BookedRoom> bookings = new ArrayList<>();
        // Bound IN-list sizes without issuing a query for each hotel or offering.
        // Keep the original aggregate safety limits: never quote from truncated data.
        List<Integer> orderedIds = ids.stream().sorted().toList();
        for( int offset = 0; offset < orderedIds.size(); offset += OFFERING_BATCH_SIZE )
        {
            Set<Integer> batch = Set.copyOf( orderedIds.subList( offset,
                    Math.min( offset + OFFERING_BATCH_SIZE, orderedIds.size() ) ) );
            seasons.addAll( data.seasons( batch ) );
            bookings.addAll( data.bookedRooms( batch, request.checkIn(), request.checkOut() ) );
            if( seasons.size() > QuotationSearchRepository.MAX_SEASONS ||
                        bookings.size() > QuotationSearchRepository.MAX_BOOKED_ITEMS )
            {
                throw QuotationSearchException.tooBroad();
            }
        }
        Map<Integer,List<Season>> bySeason = seasons.stream().collect(
                Collectors.groupingBy( Season::offeringId ) );
        Map<Integer,List<BookedRoom>> byBooking = bookings.stream().collect(
                Collectors.groupingBy( BookedRoom::offeringId ) );
        Map<Integer,List<NightlyQuotationPricing.Tariff>> tariffs = new HashMap<>();
        Map<Integer,Integer> available = new HashMap<>();
        for( Candidate offering : candidates )
        {
            validateCandidate( offering, request );
            tariffs.put( offering.offeringId(),
                    pricing.tariffs( offering, bySeason.getOrDefault( offering.offeringId(), List.of() ),
                            request.checkIn(), request.checkOut() ) );
            available.put( offering.offeringId(),
                    inventory.available( offering.offeringId(), offering.contractedRooms(),
                            byBooking.getOrDefault( offering.offeringId(), List.of() ), request.checkIn(),
                            request.checkOut() ) );
        }
        List<Quotation> results = new ArrayList<>();
        List<UnavailableOption> unavailable = new ArrayList<>();
        List<SelectionOffering> choices = selectionOfferings( request, candidates, available,
                tariffs );
        Map<Integer,List<Candidate>> groups = candidates.stream().collect( Collectors.groupingBy(
                offering -> selection != null ? offering.contractId() : offering.offeringId(), TreeMap::new,
                Collectors.toList() ) );
        var context = new SelectionContext( request, selection, available, tariffs, nights );
        for( List<Candidate> group : groups.values() )
        {
            addSelectionResult( context, group, results, unavailable );
        }
        return new Evaluation( List.copyOf( results ), List.copyOf( unavailable ), choices );
    }

    // Builds offering choices with per-room selling prices and stay-wide inventory.
    private List<SelectionOffering> selectionOfferings(
            QuotationSearchRequest request,
            List<Candidate> candidates, Map<Integer,Integer> available,
            Map<Integer,List<NightlyQuotationPricing.Tariff>> tariffs )
    {
        return candidates.stream().map(
                offering -> new SelectionOffering( offering.hotelId(), offering.hotelName(),
                        offering.contractId(), offering.offeringId(), offering.roomTypeName(),
                        offering.maxAdults(), available.get( offering.offeringId() ), offering.currency(),
                        java.util.stream.IntStream.range( 0, request.rooms().size() ).mapToObj(
                                r -> new SelectionRoom( r + 1, request.rooms().get( r ).adults(),
                                        request.rooms().get( r ).adults() <= offering.maxAdults() ?
                                                pricing.total( tariffs.get( offering.offeringId() ),
                                                        request.rooms().get( r ).adults(), offering.markup() ) :
                                                null ) ).toList() ) ).toList();
    }

    // Rejects stored offering or contract data that cannot be safely quoted.
    private void validateCandidate( Candidate offering, QuotationSearchRequest request )
    {
        if( offering.offeringId() <= 0 || offering.contractId() <= 0 || offering.contractCode() == null ||
                    offering.contractCode().isBlank() || offering.contractCode().length() > 45 ||
                    offering.hotelName() == null || offering.hotelName().isBlank() ||
                    offering.roomTypeName() == null || offering.roomTypeName().isBlank() || !validator.validate(
                new ContractRequest( offering.hotelId(), offering.validFrom(), offering.validTo(),
                        offering.markup(), offering.currency() ) ).isEmpty() || !validator.validate(
                new OfferingRequest( offering.roomTypeId(), offering.baseRate(), offering.contractedRooms(),
                        offering.maxAdults(), OfferingStatus.ENABLED ) ).isEmpty() ||
                    offering.validFrom().isAfter( request.checkIn() ) ||
                    offering.validTo().isBefore( request.checkOut().minusDays( 1 ) ) )
        {
            throw QuotationSearchException.inconsistent();
        }
    }

    // Checks that grouped offerings belong to consistent hotel and commercial terms.
    private void validateGroup( List<Candidate> group, Candidate parent )
    {
        if( group.stream().anyMatch( offering -> offering.hotelId() != parent.hotelId() ||
                                                         !offering.currency().equals( parent.currency() ) ||
                                                         offering.markup().compareTo( parent.markup() ) != 0 ) )
        {
            throw QuotationSearchException.inconsistent();
        }
    }

    // Adds a complete quotation or an explanation of why the selected rooms are unavailable.
    private void addSelectionResult(
            SelectionContext context, List<Candidate> group, List<Quotation> results,
            List<UnavailableOption> unavailable )
    {
        var request = context.request();
        var selection = context.selection();
        var available = context.available();
        var tariffs = context.tariffs();
        int nights = context.nights();
        group.sort( Comparator.comparingInt( Candidate::offeringId ) );
        Candidate parent = group.get( 0 );
        validateGroup( group, parent );
        int count = request.rooms().size();
        Map<Integer,Candidate> byId = group.stream().collect(
                Collectors.toMap( Candidate::offeringId, offering -> offering ) );
        List<Candidate> selected = new ArrayList<>();
        Map<Integer,Integer> demand = new HashMap<>();
        List<String> occupancyErrors = new ArrayList<>();
        for( int r = 0; r < count; r++ )
        {
            // Primary results use one room type; customized results use exactly the submitted offering IDs.
            Candidate offering = selection == null ? parent : byId.get( selection.rooms().get( r ).offeringId() );
            if( offering == null )
            {
                throw QuotationSearchException.invalid(
                        "Selected offering does not belong to the same eligible hotel and approved contract" );
            }
            selected.add( offering );
            demand.merge( offering.offeringId(), 1, Integer::sum );
            int adults = request.rooms().get( r ).adults();
            if( adults > offering.maxAdults() )
            {
                occupancyErrors.add( "Maximum " + offering.maxAdults() + " adults per room. Room " + ( r + 1 ) +
                                             " requests " + adults + " adults; maximum is " + offering.maxAdults() + "." );
            }
        }
        List<UnavailabilityReason> reasons = unavailabilityReasons( group,
                occupancyErrors, demand, available );
        // Pricing always follows the same explicit offerings, including any non-bookable indicative total.
        List<BigDecimal> roomPrices = new ArrayList<>();
        BigDecimal total = new BigDecimal( "0.00" );
        if( occupancyErrors.isEmpty() )
        {
            for( int r = 0; r < count; r++ )
            {
                Candidate offering = selected.get( r );
                BigDecimal price = pricing.total( tariffs.get( offering.offeringId() ),
                        request.rooms().get( r ).adults(), offering.markup() );
                roomPrices.add( price );
                total = total.add( price );
            }
        }
        if( !reasons.isEmpty() )
        {
            unavailable.add( new UnavailableOption( parent.hotelId(), parent.hotelName(), parent.city(),
                    parent.contractId(), parent.contractCode(), parent.currency(),
                    AvailabilityStatus.NOT_AVAILABLE, group.stream().map(
                    offering -> new UnavailableOffering( offering.offeringId(), offering.roomTypeId(),
                            offering.roomTypeName(), offering.maxAdults(),
                            available.get( offering.offeringId() ) ) ).toList(), List.copyOf( reasons ),
                    occupancyErrors.isEmpty() ? total : null ) );
            return;
        }
        List<RoomAllocation> allocated = new ArrayList<>();
        for( int r = 0; r < count; r++ )
        {
            Candidate offering = selected.get( r );
            int adults = request.rooms().get( r ).adults();
            int availableRooms = available.get( offering.offeringId() );
            int used = demand.get( offering.offeringId() );
            allocated.add( new RoomAllocation( r + 1, adults, offering.offeringId(), offering.roomTypeId(),
                    offering.roomTypeName(), offering.contractedRooms(), availableRooms, used,
                    availableRooms - used, roomPrices.get( r ),
                    pricing.breakdown( tariffs.get( offering.offeringId() ), adults, offering.markup() ) ) );
        }
        results.add( new Quotation( parent.hotelId(), parent.hotelName(), parent.city(), parent.contractId(),
                parent.contractCode(), parent.currency(), request.checkIn(), request.checkOut(), nights,
                List.copyOf( allocated ), total, "INFORMATIONAL" ) );
    }

    // Explains occupancy or shared-inventory shortages for the requested rooms.
    private List<UnavailabilityReason> unavailabilityReasons(
            List<Candidate> group,
            List<String> occupancyErrors, Map<Integer,Integer> demand,
            Map<Integer,Integer> available )
    {
        List<UnavailabilityReason> reasons = new ArrayList<>();
        if( !occupancyErrors.isEmpty() )
        {
            reasons.add( new UnavailabilityReason( ReasonCode.MAX_ADULT_OCCUPANCY,
                    String.join( " ", occupancyErrors ) ) );
        }
        for( Candidate offering : group )
        {
            int requested = demand.getOrDefault( offering.offeringId(), 0 );
            int remaining = available.get( offering.offeringId() );
            if( requested > remaining )
            {
                reasons.add( new UnavailabilityReason( ReasonCode.INSUFFICIENT_INVENTORY,
                        "Only " + remaining + ( remaining == 1 ? " room available; " : " rooms available; " ) +
                                requested + " requested." ) );
            }
        }
        return reasons;
    }

    private record SelectionContext(
            QuotationSearchRequest request,
            QuotationSelectionRequest selection,
            Map<Integer,Integer> available,
            Map<Integer,List<NightlyQuotationPricing.Tariff>> tariffs,
            int nights
    )
    {
    }
}
