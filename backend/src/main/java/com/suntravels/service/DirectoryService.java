package com.suntravels.service;

import com.suntravels.dto.*;
import com.suntravels.entity.*;
import com.suntravels.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Path;
import java.util.*;
import java.util.function.Function;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in directory pages keep existing array endpoints available to management selectors. */
@Service
@Transactional(readOnly = true)
public class DirectoryService {
    private static final int SIZE = 10;
    private final HotelRepository hotels;
    private final RoomTypeRepository rooms;
    private final ContractRepository contracts;
    private final OfferingRepository offerings;
    private final HotelService hotelService;
    private final RoomTypeService roomService;
    private final ContractService contractService;
    private final EntityManager entityManager;

    public DirectoryService(HotelRepository hotels, RoomTypeRepository rooms, ContractRepository contracts,
            OfferingRepository offerings, HotelService hotelService, RoomTypeService roomService,
            ContractService contractService, EntityManager entityManager) {
        this.hotels = hotels;
        this.rooms = rooms;
        this.contracts = contracts;
        this.offerings = offerings;
        this.hotelService = hotelService;
        this.roomService = roomService;
        this.contractService = contractService;
        this.entityManager = entityManager;
    }

    public DirectoryPage<HotelResponse> hotels(String query, String status, int page) {
        var spec = DirectoryService.<Hotel>search(query, "name", "city", "country")
                .and(equal("status", status));
        return result(hotels, spec, Sort.by("hotelId"), page, hotelService::toResponse,
                Map.of("totalRecords", hotels.count(), "activeRecords", hotels.count(equal("status", "ACTIVE"))));
    }

    public DirectoryPage<RoomTypeResponse> rooms(String query, int page) {
        return result(rooms, search(query, "name", "description"), Sort.by("name", "roomTypeId"),
                page, roomService::toResponse, Map.of("totalRecords", rooms.count()));
    }

    public DirectoryPage<ContractResponse> contracts(String query, String status, int page) {
        return result(contracts, DirectoryService.<Contract>search(query, "contractCode", "hotel.name", "currencyCode")
                        .and(equal("status", status)), Sort.by("contractId").descending(),
                page, contractService::response, Map.of("totalRecords", contracts.count()));
    }

    public DirectoryPage<OfferingDirectoryResponse> offerings(Map<String, String> filters, int page) {
        Specification<ContractRoomOffering> spec = search(filters.get("search"), "contract.hotel.name", "contract.contractCode", "roomType.name");
        spec = spec.and(equal("contract.hotel.hotelId", filters.get("hotelId")))
                .and(equal("roomType.roomTypeId", filters.get("roomTypeId")))
                .and(equal("contract.status", filters.get("contractStatus")))
                .and(equal("status", filters.get("offeringStatus")));
        String field = switch (filters.getOrDefault("sortBy", "hotel")) {
            case "contract" -> "contract.contractCode";
            case "roomType" -> "roomType.name";
            case "rate" -> "ratePerPersonPerNight";
            case "rooms" -> "contractedRoomCount";
            case "validFrom" -> "contract.validFrom";
            default -> "contract.hotel.name";
        };
        Sort.Direction direction = "desc".equals(filters.get("direction")) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, field);
        if ("rate".equals(filters.get("sortBy"))) sort = Sort.by(direction, "contract.currencyCode").and(sort);
        sort = sort.and(Sort.by("offeringId"));
        var data = page(offerings, spec, sort, page);
        Map<Integer, Long> counts = new HashMap<>();
        if (!data.isEmpty()) {
            var ids = data.getContent().stream().map(ContractRoomOffering::getOfferingId).toList();
            entityManager.createQuery("select s.offering.offeringId, count(s) from OfferingSeasonalRate s where s.offering.offeringId in :ids group by s.offering.offeringId", Object[].class)
                    .setParameter("ids", ids).getResultList().forEach(row -> counts.put((Integer) row[0], (Long) row[1]));
        }
        return response(data, offering -> {
            Contract c = offering.getContract();
            Hotel h = c.getHotel();
            return new OfferingDirectoryResponse(offering.getOfferingId(), h.getHotelId(), h.getName(), h.getStatus(),
                    c.getContractId(), c.getContractCode(), c.getStatus(), c.getValidFrom(), c.getValidTo(),
                    offering.getRoomType().getRoomTypeId(), offering.getRoomType().getName(),
                    offering.getRatePerPersonPerNight(), c.getCurrencyCode(), offering.getContractedRoomCount(),
                    offering.getMaxAdults(), offering.getStatus(), counts.getOrDefault(offering.getOfferingId(), 0L));
        }, Map.of("totalRecords", offerings.count(),
                "hotels", lookups("select distinct o.contract.hotel.hotelId, o.contract.hotel.name from ContractRoomOffering o order by o.contract.hotel.name"),
                "roomTypes", lookups("select distinct o.roomType.roomTypeId, o.roomType.name from ContractRoomOffering o order by o.roomType.name")));
    }

    private List<Map<String, Object>> lookups(String query) {
        return entityManager.createQuery(query, Object[].class).getResultList().stream()
                .map(row -> Map.<String, Object>of("id", row[0], "name", row[1])).toList();
    }

    private <T, R> DirectoryPage<R> result(JpaSpecificationExecutor<T> repository, Specification<T> spec,
            Sort sort, int number, Function<T, R> mapper, Map<String, Object> summary) {
        return response(page(repository, spec, sort, number), mapper, summary);
    }

    private <T> Page<T> page(JpaSpecificationExecutor<T> repository, Specification<T> spec, Sort sort, int number) {
        if (number < 0 || number > Integer.MAX_VALUE / SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page number");
        }
        Page<T> result = repository.findAll(spec, PageRequest.of(number, SIZE, sort));
        // A last-page row can disappear after an edit or status change; return the last valid page.
        if (number > 0 && result.isEmpty()) {
            result = repository.findAll(spec, PageRequest.of(Math.max(0, result.getTotalPages() - 1), SIZE, sort));
        }
        return result;
    }

    private <T, R> DirectoryPage<R> response(Page<T> page, Function<T, R> mapper, Map<String, Object> summary) {
        return new DirectoryPage<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), SIZE,
                page.getTotalElements(), page.getTotalPages(), summary);
    }

    private static Path<?> path(Path<?> root, String name) {
        for (String segment : name.split("[.]")) root = root.get(segment);
        return root;
    }

    private static <T> Specification<T> equal(String field, String value) {
        return (root, query, cb) -> {
            if (value == null || value.isBlank()) return cb.conjunction();
            Path<?> attribute = path(root, field);
            if (attribute.getJavaType().isEnum()) {
                for (Object option : attribute.getJavaType().getEnumConstants()) {
                    if (((Enum<?>) option).name().equals(value)) return cb.equal(attribute, option);
                }
                return cb.disjunction();
            }
            if (attribute.getJavaType() == Integer.class) {
                try {
                    return cb.equal(attribute, Integer.valueOf(value));
                } catch (NumberFormatException invalidId) {
                    return cb.disjunction();
                }
            }
            return cb.equal(attribute, value);
        };
    }

    private static <T> Specification<T> search(String raw, String... fields) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        // Escape LIKE metacharacters so directory search remains a literal substring search.
        String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return (root, query, cb) -> cb.or(Arrays.stream(fields)
                .map(field -> cb.like(cb.lower(path(root, field).as(String.class)), pattern, '!'))
                .toArray(jakarta.persistence.criteria.Predicate[]::new));
    }
}
