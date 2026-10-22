package com.suntravels.service;

import com.suntravels.dto.EvaluatedHotelPage;
import com.suntravels.dto.HotelSuggestion;
import com.suntravels.dto.CitySuggestion;
import com.suntravels.validation.CountryCatalog;
import com.suntravels.dto.OfferingOccupancy;
import com.suntravels.dto.QuotationSearchFilters;
import com.suntravels.dto.QuotationSearchRequest;
import com.suntravels.dto.QuotationSearchResponse;
import com.suntravels.dto.QuotationSearchResponse.AvailabilityStatus;
import com.suntravels.dto.QuotationSearchResponse.Limits;
import com.suntravels.dto.QuotationSearchResponse.Quotation;
import com.suntravels.dto.QuotationSearchResponse.SelectionOffering;
import com.suntravels.dto.QuotationSearchResponse.UnavailabilityReason;
import com.suntravels.dto.QuotationSearchResponse.UnavailableOption;
import com.suntravels.dto.QuotationSelectionRequest;
import com.suntravels.entity.HotelStatus;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.HotelRepository;
import com.suntravels.repository.QuotationSearchRepository;
import com.suntravels.repository.QuotationSearchRepository.Candidate;
import com.suntravels.repository.RoomTypeRepository;
import jakarta.validation.Validator;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional( readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15 )
public class QuotationSearchService
{
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(QuotationSearchService.class);
    public static final int MAX_ROOMS = 8;
    public static final int MAX_NIGHTS = 31;
    public static final int MAX_ADVANCE_DAYS = 730;
    public static final int MAX_RESULTS = 20;
    private static final String INDIVIDUAL_ROOM_TYPES = "INDIVIDUAL_ROOM_TYPES";
    private final QuotationSearchRepository data;
    private final HotelRepository hotels;
    private final RoomTypeRepository rooms;
    private final Validator validator;
    private final Clock clock;
    private final QuotationEvaluator evaluator;

    // Initializes the quotation search service and its required dependencies.
    public QuotationSearchService(
            QuotationSearchRepository data, HotelRepository hotels,
            RoomTypeRepository rooms, Validator validator, Clock clock, NightlyQuotationPricing pricing,
            StayAvailability inventory )
    {
        this.data = data;
        this.hotels = hotels;
        this.rooms = rooms;
        this.validator = validator;
        this.clock = clock;
        this.evaluator = new QuotationEvaluator( data, validator, pricing, inventory );
    }

    // Returns the current business date and supported search limits.
    public Limits limits()
    {
        return new Limits( LocalDate.now( clock ), MAX_ROOMS, MAX_NIGHTS, MAX_ADVANCE_DAYS, MAX_RESULTS,
                INDIVIDUAL_ROOM_TYPES );
    }

    // Returns active hotel and room-type options for quotation search.
    public QuotationSearchFilters filters()
    {
        var hotelOptions = hotels.findAll(
                Sort.by( Sort.Order.asc( "name" ), Sort.Order.asc( "hotelId" ) ) ).stream().filter(
                h -> h.getStatus() == HotelStatus.ACTIVE ).map(
                h -> new QuotationSearchFilters.HotelOption( h.getHotelId(), h.getName(),
                        h.getCity() ) ).toList();
        return new QuotationSearchFilters( hotelOptions, roomTypeFilters() );
    }

    public List<QuotationSearchFilters.RoomTypeOption> roomTypeFilters()
    {
        return rooms.findAll(
                Sort.by( Sort.Order.asc( "name" ), Sort.Order.asc( "roomTypeId" ) ) ).stream().map(
                r -> new QuotationSearchFilters.RoomTypeOption( r.getRoomTypeId(), r.getName() ) ).toList();
    }

    public List<HotelSuggestion> hotelSuggestions( String term )
    {
        return hotelSuggestions( term, null, null );
    }

    public List<HotelSuggestion> hotelSuggestions( String term, String city, String country )
    {
        if( term == null || term.trim().length() < 2 ) return List.of();
        if( term.length() > 100 || city != null && city.length() > 100 )
            throw QuotationSearchException.invalid( "Hotel name and city must be at most 100 characters" );
        return data.hotelSuggestions( term.trim(), city, canonicalCountry( country ) );
    }

    public List<CitySuggestion> citySuggestions( String term, String country )
    {
        if( term == null || term.trim().length() < 2 ) return List.of();
        if( term.length() > 100 ) throw QuotationSearchException.invalid( "City must be at most 100 characters" );
        return data.citySuggestions( term.trim(), canonicalCountry( country ) );
    }

    private String canonicalCountry( String country )
    {
        if( country == null || country.isBlank() ) return null;
        if( country.length() > 100 ) throw QuotationSearchException.invalid( "Country must be at most 100 characters" );
        return CountryCatalog.filterName( country );
    }

    public HotelSuggestion hotelOption( int id )
    {
        if( id <= 0 ) throw QuotationSearchException.invalid( "Hotel ID must be positive" );
        return data.activeHotelOption( id ).orElseThrow(
                () -> new QuotationSearchException( 404, "Active hotel was not found" ) );
    }

    /** Discovery only. Continue with the same filters and the returned nextAfterHotelId.
     * The existing repeatable-read transaction keeps both queries on one snapshot.
     */
    public QuotationSearchRepository.CandidatePage candidatePage(
            QuotationSearchRequest request, Integer afterHotelId )
    {
        validate( request, LocalDate.now( clock ) );
        if( afterHotelId != null && afterHotelId <= 0 )
        {
            throw QuotationSearchException.invalid( "Hotel continuation ID must be positive" );
        }
        var page = data.candidatePage( request, afterHotelId );
        LOG.debug("Candidate page hotels={} offerings={} afterHotelId={} hasNext={}",
                page.hotelIds().size(), page.offerings().size(), afterHotelId, page.nextAfterHotelId() != null);
        return page;
    }

    /** Evaluates one complete hotel page without the legacy offering-result cap. */
    public EvaluatedHotelPage evaluatedPage( QuotationSearchRequest request, Integer afterHotelId )
    {
        var page = candidatePage( request, afterHotelId );
        LocalDate today = LocalDate.now( clock );
        int nights = ( int ) ChronoUnit.DAYS.between( request.checkIn(), request.checkOut() );
        var evaluated = evaluator.evaluate( request, null, page.offerings(), today, nights );
        LOG.debug("Page evaluated nights={} requestedRooms={} available={} unavailable={}",
                nights, request.rooms().size(), evaluated.quotations().size(), evaluated.unavailable().size());
        var quotes = evaluated.quotations().stream().collect( Collectors.toMap(
                q -> q.rooms().get( 0 ).offeringId(), q -> q ) );
        var unavailable = evaluated.unavailable().stream().collect( Collectors.toMap(
                u -> u.offerings().get( 0 ).offeringId(), u -> u ) );
        var choices = evaluated.choices().stream().collect( Collectors.toMap(
                SelectionOffering::offeringId, o -> o ) );
        var byHotel = page.offerings().stream().collect( Collectors.groupingBy( Candidate::hotelId ) );
        var groups = page.hotelIds().stream().map( id -> {
            var offerings = byHotel.get( id );
            var hotel = offerings.get( 0 );
            var results = offerings.stream().map( o -> new EvaluatedHotelPage.Offering(
                    o.offeringId(), o.roomTypeId(), o.contractedRooms(), choices.get( o.offeringId() ),
                    quotes.containsKey( o.offeringId() ) ? AvailabilityStatus.AVAILABLE : AvailabilityStatus.NOT_AVAILABLE,
                    quotes.get( o.offeringId() ), unavailable.get( o.offeringId() ) ) ).toList();
            return new EvaluatedHotelPage.Hotel( id, hotel.hotelName(), hotel.city(), results );
        } ).toList();
        return new EvaluatedHotelPage( today, request.checkIn(), request.checkOut(), nights,
                groups, page.nextAfterHotelId() );
    }

    // Returns adult limits for eligible offerings without calculating prices.
    public List<OfferingOccupancy> occupancy(
            Integer hotelId, Integer roomTypeId, LocalDate checkIn,
            LocalDate checkOut, String city )
    {
        if( hotelId == null || roomTypeId == null )
        {
            throw QuotationSearchException.invalid( "Select a hotel and room type for occupancy guidance" );
        }
        var request = new QuotationSearchRequest( checkIn, checkOut,
                List.of( new QuotationSearchRequest.RequestedRoom( 1 ) ), city, hotelId, roomTypeId );
        validate( request, LocalDate.now( clock ) );
        var candidates = data.candidates( request );
        if( candidates.size() > QuotationSearchRepository.MAX_OFFERINGS )
        {
            throw QuotationSearchException.tooBroad();
        }
        if( candidates.stream().anyMatch( offering -> offering.maxAdults() < 1 ) )
        {
            throw QuotationSearchException.inconsistent();
        }
        return candidates.stream().map(
                offering -> new OfferingOccupancy( offering.offeringId(), offering.roomTypeId(),
                        offering.roomTypeName(), offering.maxAdults() ) ).toList();
    }

    /**
     * Evaluates each eligible offering against the whole room request independently.
     * Availability is the minimum free inventory across occupied nights. These read-only
     * results do not reserve inventory; confirmation must repeat the checks under locks.
     */
    public QuotationSearchResponse search( QuotationSearchRequest request )
    {
        return evaluate( request, null );
    }

    /**
     * Prices exactly the submitted offerings within one hotel and approved contract.
     * Repeated offering IDs share one inventory pool; adult counts and selection order
     * are preserved rather than replaced by alternative room types.
     *
     * @throws QuotationSearchException when the exact selection cannot be quoted
     */
    public Quotation customize( QuotationSelectionRequest selection )
    {
        if( selection == null || !validator.validate( selection ).isEmpty() )
        {
            throw QuotationSearchException.invalid( "Check selected hotel, contract, dates and rooms" );
        }
        LOG.debug("Custom quotation hotelId={} contractId={} rooms={}",
                selection.hotelId(), selection.contractId(), selection.rooms().size());
        var request = new QuotationSearchRequest( selection.checkIn(), selection.checkOut(),
                selection.rooms().stream().map(
                        r -> new QuotationSearchRequest.RequestedRoom( r.adults() ) ).toList(), null,
                selection.hotelId(), null );
        var result = evaluate( request, selection );
        if( result.quotations().size() != 1 ||
                    !result.quotations().get( 0 ).hotelId().equals( selection.hotelId() ) )
        {
            String reason = result.unavailableOptions().stream().flatMap( o -> o.reasons().stream() ).map(
                    UnavailabilityReason::message ).collect( Collectors.joining( " " ) );
            throw QuotationSearchException.invalid( reason.isBlank() ?
                                                            "Selected rooms are unavailable or do not belong to the same eligible hotel and contract" :
                                                            reason );
        }
        return result.quotations().get( 0 );
    }

    // Evaluates stay eligibility, occupancy, inventory, and prices for search or selection.
    private QuotationSearchResponse evaluate(
            QuotationSearchRequest request,
            QuotationSelectionRequest selection )
    {
        LocalDate today = LocalDate.now( clock );
        validate( request, today );
        List<Candidate> candidates = new ArrayList<>( data.candidates( request ) );
        if( selection != null )
        {
            candidates.removeIf( offering -> offering.contractId() != selection.contractId() );
        }
        if( candidates.size() > QuotationSearchRepository.MAX_OFFERINGS )
        {
            throw QuotationSearchException.tooBroad();
        }
        int nights = ( int ) ChronoUnit.DAYS.between( request.checkIn(), request.checkOut() );
        if( candidates.isEmpty() )
        {
            return response( request, today, nights, List.of(), List.of() );
        }
        var evaluated = evaluator.evaluate( request, selection, candidates, today, nights );
        List<Quotation> results = new ArrayList<>( evaluated.quotations() );
        List<UnavailableOption> unavailable = new ArrayList<>( evaluated.unavailable() );
        List<SelectionOffering> choices = evaluated.choices();
        results.sort( Comparator.comparing( Quotation::currencyCode ).thenComparing(
                Quotation::totalPrice ).thenComparing( Quotation::hotelId ).thenComparing(
                Quotation::contractId ) );
        unavailable.sort( Comparator.comparing( UnavailableOption::hotelName ).thenComparingInt(
                UnavailableOption::hotelId ).thenComparingInt( UnavailableOption::contractId ) );
        var result = response( request, today, nights, results, unavailable );
        return new QuotationSearchResponse( result.businessDate(), result.checkIn(), result.checkOut(),
                result.nights(), result.quotations(), result.matchingOptions(), result.truncated(),
                selection == null ? INDIVIDUAL_ROOM_TYPES : "EXPLICIT_SELECTION", result.notice(),
                result.unavailableOptions(), result.matchingUnavailableOptions(),
                result.unavailableTruncated(), choices );
    }

    // Builds the bounded quotation response with totals and truncation indicators.
    private QuotationSearchResponse response(
            QuotationSearchRequest r, LocalDate today, int nights,
            List<Quotation> quotes, List<UnavailableOption> unavailable )
    {
        return new QuotationSearchResponse( today, r.checkIn(), r.checkOut(), nights,
                List.copyOf( quotes.subList( 0, Math.min( MAX_RESULTS, quotes.size() ) ) ), quotes.size(),
                quotes.size() > MAX_RESULTS, INDIVIDUAL_ROOM_TYPES,
                "Informational quotation only. Prices and availability must be rechecked atomically at reservation confirmation. Available options are shown first, ordered by currency, then total price.",
                List.copyOf( unavailable.subList( 0, Math.min( MAX_RESULTS, unavailable.size() ) ) ),
                unavailable.size(), unavailable.size() > MAX_RESULTS );
    }

    // Validates stay dates, requested rooms, and optional search references.
    private void validate( QuotationSearchRequest r, LocalDate today )
    {
        if( r == null )
        {
            throw QuotationSearchException.invalid( "Search details are required" );
        }
        var violations = validator.validate( r );
        if( !violations.isEmpty() )
        {
            throw QuotationSearchException.invalid( violations.stream().map(
                    v -> v.getPropertyPath() + ": " + v.getMessage() ).sorted().collect(
                    Collectors.joining( "; " ) ) );
        }
        long nights = ChronoUnit.DAYS.between( r.checkIn(), r.checkOut() );
        if( r.checkIn().isBefore( today ) )
        {
            throw QuotationSearchException.invalid( "Check-in cannot be before today's business date" );
        }
        if( nights < 1 || nights > MAX_NIGHTS )
        {
            throw QuotationSearchException.invalid( "Stay must be between 1 and 31 occupied nights" );
        }
        if( r.checkIn().isAfter( today.plusDays( MAX_ADVANCE_DAYS ) ) )
        {
            throw QuotationSearchException.invalid( "Check-in must be within 730 days of the business date" );
        }
        if( r.hotelId() != null && !hotels.existsById( r.hotelId() ) )
        {
            throw new QuotationSearchException( 404, "Selected hotel was not found" );
        }
        if( r.roomTypeId() != null && !rooms.existsById( r.roomTypeId() ) )
        {
            throw new QuotationSearchException( 404, "Selected room type was not found" );
        }
    }

}
