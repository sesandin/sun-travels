package com.suntravels.service;

import com.suntravels.dto.ContractResponse;
import com.suntravels.dto.DirectoryPage;
import com.suntravels.dto.HotelResponse;
import com.suntravels.dto.OfferingDirectoryResponse;
import com.suntravels.dto.RoomTypeResponse;
import com.suntravels.entity.Contract;
import com.suntravels.entity.ContractRoomOffering;
import com.suntravels.entity.Hotel;
import com.suntravels.repository.ContractRepository;
import com.suntravels.repository.HotelRepository;
import com.suntravels.repository.OfferingRepository;
import com.suntravels.repository.RoomTypeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Path;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Opt-in directory pages keep existing array endpoints available to management selectors.
 */
@Service
@Transactional( readOnly = true )
public class DirectoryService
{
    private static final int SIZE = 10;
    private static final String TOTAL_RECORDS = "totalRecords";
    private static final String STATUS = "status";
    private final HotelRepository hotels;
    private final RoomTypeRepository rooms;
    private final ContractRepository contracts;
    private final OfferingRepository offerings;
    private final HotelService hotelService;
    private final RoomTypeService roomService;
    private final ContractService contractService;
    private final EntityManager entityManager;

    // Initializes the directory service and its required dependencies.
    public DirectoryService(
            HotelRepository hotels, RoomTypeRepository rooms, ContractRepository contracts,
            OfferingRepository offerings, HotelService hotelService, RoomTypeService roomService,
            ContractService contractService, EntityManager entityManager )
    {
        this.hotels = hotels;
        this.rooms = rooms;
        this.contracts = contracts;
        this.offerings = offerings;
        this.hotelService = hotelService;
        this.roomService = roomService;
        this.contractService = contractService;
        this.entityManager = entityManager;
    }

    // Builds an optional exact-match filter with enum and numeric validation.
    private static <T> Specification<T> matchesValue( String field, String value )
    {
        return ( root, query, cb ) ->
        {
            if( value == null || value.isBlank() ) return cb.conjunction();
            Path<?> attribute = path( root, field );
            if( attribute.getJavaType().isEnum() )
            {
                return enumMatch( attribute, value, cb );
            }
            if( attribute.getJavaType() == Integer.class )
            {
                try
                {
                    return cb.equal( attribute, Integer.valueOf( value ) );
                }
                catch( NumberFormatException invalidId )
                {
                    return cb.disjunction();
                }
            }
            return cb.equal( attribute, value );
        };
    }

    private static jakarta.persistence.criteria.Predicate enumMatch(
            Path<?> attribute, String value,
            jakarta.persistence.criteria.CriteriaBuilder cb )
    {
        for( Object option : attribute.getJavaType().getEnumConstants() )
        {
            if( ( ( Enum<?> ) option ).name().equals( value ) ) return cb.equal( attribute, option );
        }
        return cb.disjunction();
    }

    // Resolves a dotted field name to its nested database query path.
    private static Path<?> path( Path<?> root, String name )
    {
        for( String segment : name.split( "[.]" ) ) root = root.get( segment );
        return root;
    }

    // Builds optional case-insensitive text matching across directory fields.
    private static <T> Specification<T> search( String raw, String... fields )
    {
        String text = raw == null ? "" : raw.trim().toLowerCase( Locale.ROOT );
        // Escape LIKE metacharacters so directory search remains a literal substring search.
        String pattern = "%" + text.replace( "!", "!!" ).replace( "%", "!%" ).replace( "_", "!_" ) + "%";
        return ( root, query, cb ) -> cb.or( Arrays.stream( fields )
                                                   .map( field -> cb.like( cb.lower( path( root, field ).as( String.class ) ), pattern, '!' ) )
                                                   .toArray( jakarta.persistence.criteria.Predicate[]::new ) );
    }

    // Returns filtered hotel rows, totals, and status summary.
    public DirectoryPage<HotelResponse> hotels( String query, String status, int page )
    {
        var spec = DirectoryService.<Hotel>search( query, "name", "city", "country" )
                                   .and( matchesValue( STATUS, status ) );
        return result( hotels, spec, Sort.by( "hotelId" ), page, hotelService::toResponse,
                Map.of( TOTAL_RECORDS, hotels.count(), "activeRecords", hotels.count( matchesValue( STATUS, "ACTIVE" ) ) ) );
    }

    // Returns filtered room types and pagination metadata.
    public DirectoryPage<RoomTypeResponse> rooms( String query, int page )
    {
        return result( rooms, search( query, "name", "description" ), Sort.by( "name", "roomTypeId" ),
                page, roomService::toResponse, Map.of( TOTAL_RECORDS, rooms.count() ) );
    }

    // Maps lookup query results to safe ID-and-name options.
    private List<Map<String,Object>> lookups( String query )
    {
        return entityManager.createQuery( query, Object[].class ).getResultList().stream()
                            .map( row -> Map.<String,Object>of( "id", row[0], "name", row[1] ) ).toList();
    }

    // Loads a filtered directory page and maps its rows and summary.
    private <T, R> DirectoryPage<R> result(
            JpaSpecificationExecutor<T> repository, Specification<T> spec,
            Sort sort, int number, Function<T,R> mapper, Map<String,Object> summary )
    {
        return response( page( repository, spec, sort, number ), mapper, summary );
    }

    // Loads a bounded directory page and recovers the last valid page after row changes.
    private <T> Page<T> page( JpaSpecificationExecutor<T> repository, Specification<T> spec, Sort sort, int number )
    {
        if( number < 0 || number > Integer.MAX_VALUE / SIZE )
        {
            throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Invalid page number" );
        }
        Page<T> result = repository.findAll( spec, PageRequest.of( number, SIZE, sort ) );
        // A last-page row can disappear after an edit or status change; return the last valid page.
        if( number > 0 && result.isEmpty() )
        {
            result = repository.findAll( spec, PageRequest.of( Math.max( 0, result.getTotalPages() - 1 ), SIZE, sort ) );
        }
        return result;
    }

    // Maps a database page to directory rows and pagination metadata.
    private <T, R> DirectoryPage<R> response( Page<T> page, Function<T,R> mapper, Map<String,Object> summary )
    {
        return new DirectoryPage<>( page.getContent().stream().map( mapper ).toList(), page.getNumber(), SIZE,
                page.getTotalElements(), page.getTotalPages(), summary );
    }

    // Returns filtered contracts and directory summary data.
    public DirectoryPage<ContractResponse> contracts( String query, String status, int page )
    {
        return result( contracts, DirectoryService.<Contract>search( query, "contractCode", "hotel.name", "currencyCode" )
                                                  .and( matchesValue( STATUS, status ) ), Sort.by( "contractId" ).descending(),
                page, contractService::response, Map.of( TOTAL_RECORDS, contracts.count() ) );
    }

    // Returns filtered offering rows with directory summary and lookup options.
    public DirectoryPage<OfferingDirectoryResponse> offerings( Map<String,String> filters, int page )
    {
        Specification<ContractRoomOffering> spec = search( filters.get( "search" ), "contract.hotel.name", "contract.contractCode", "roomType.name" );
        spec = spec.and( matchesValue( "contract.hotel.hotelId", filters.get( "hotelId" ) ) )
                   .and( matchesValue( "roomType.roomTypeId", filters.get( "roomTypeId" ) ) )
                   .and( matchesValue( "contract.status", filters.get( "contractStatus" ) ) )
                   .and( matchesValue( STATUS, filters.get( "offeringStatus" ) ) );
        String field = switch( filters.getOrDefault( "sortBy", "hotel" ) )
        {
            case "contract" -> "contract.contractCode";
            case "roomType" -> "roomType.name";
            case "rate" -> "ratePerPersonPerNight";
            case "rooms" -> "contractedRoomCount";
            case "validFrom" -> "contract.validFrom";
            default -> "contract.hotel.name";
        };
        Sort.Direction direction = "desc".equals( filters.get( "direction" ) ) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by( direction, field );
        if( "rate".equals( filters.get( "sortBy" ) ) ) sort = Sort.by( direction, "contract.currencyCode" ).and( sort );
        sort = sort.and( Sort.by( "offeringId" ) );
        var data = page( offerings, spec, sort, page );
        Map<Integer,Long> counts = new HashMap<>();
        if( !data.isEmpty() )
        {
            var ids = data.getContent().stream().map( ContractRoomOffering::getOfferingId ).toList();
            entityManager.createQuery( "select s.offering.offeringId, count(s) from OfferingSeasonalRate s where s.offering.offeringId in :ids group by s.offering.offeringId", Object[].class )
                         .setParameter( "ids", ids ).getResultList().forEach( row -> counts.put( ( Integer ) row[0], ( Long ) row[1] ) );
        }
        return response( data, offering ->
        {
            Contract c = offering.getContract();
            Hotel h = c.getHotel();
            return new OfferingDirectoryResponse( offering.getOfferingId(), h.getHotelId(), h.getName(), h.getStatus(),
                    c.getContractId(), c.getContractCode(), c.getStatus(), c.getValidFrom(), c.getValidTo(),
                    offering.getRoomType().getRoomTypeId(), offering.getRoomType().getName(),
                    offering.getRatePerPersonPerNight(), c.getCurrencyCode(), offering.getContractedRoomCount(),
                    offering.getMaxAdults(), offering.getStatus(), counts.getOrDefault( offering.getOfferingId(), 0L ) );
        }, Map.of( TOTAL_RECORDS, offerings.count(),
                "hotels", lookups( "select distinct o.contract.hotel.hotelId, o.contract.hotel.name from ContractRoomOffering o order by o.contract.hotel.name" ),
                "roomTypes", lookups( "select distinct o.roomType.roomTypeId, o.roomType.name from ContractRoomOffering o order by o.roomType.name" ) ) );
    }
}
