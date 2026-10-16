package com.suntravels.service;

import com.suntravels.dto.*;
import com.suntravels.dto.QuotationSearchResponse.*;
import com.suntravels.entity.OfferingStatus;
import com.suntravels.entity.HotelStatus;
import com.suntravels.exception.QuotationSearchException;
import com.suntravels.repository.*;
import com.suntravels.repository.QuotationSearchRepository.*;
import jakarta.validation.Validator;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
public class QuotationSearchService {
    private static final String INDIVIDUAL_ROOM_TYPES = "INDIVIDUAL_ROOM_TYPES";
    public static final int MAX_ROOMS = 8;
    public static final int MAX_NIGHTS = 31;
    public static final int MAX_ADVANCE_DAYS = 730;
    public static final int MAX_RESULTS = 20;
    private final QuotationSearchRepository data;
    private final HotelRepository hotels;
    private final RoomTypeRepository rooms;
    private final Validator validator;
    private final Clock clock;
    private final NightlyQuotationPricing pricing;
    private final StayAvailability inventory;

    private record SelectionContext(
            QuotationSearchRequest request,
            QuotationSelectionRequest selection,
            Set<Integer> closedOfferingIds,
            Map<Integer, Integer> available,
            Map<Integer, List<NightlyQuotationPricing.Tariff>> tariffs,
            int nights
    ) {
    }

    public QuotationSearchService(QuotationSearchRepository data, HotelRepository hotels,
            RoomTypeRepository rooms, Validator validator, Clock clock, NightlyQuotationPricing pricing,
            StayAvailability inventory) {
        this.data = data;
        this.hotels = hotels;
        this.rooms = rooms;
        this.validator = validator;
        this.clock = clock;
        this.pricing = pricing;
        this.inventory = inventory;
    }

    public Limits limits() {
        return new Limits(LocalDate.now(clock), MAX_ROOMS, MAX_NIGHTS, MAX_ADVANCE_DAYS, MAX_RESULTS,
                INDIVIDUAL_ROOM_TYPES);
    }

    public QuotationSearchFilters filters() {
        var hotelOptions = hotels.findAll(
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("hotelId"))).stream().filter(
                h -> h.getStatus() == HotelStatus.ACTIVE).map(
                h -> new QuotationSearchFilters.HotelOption(h.getHotelId(), h.getName(),
                        h.getCity())).toList();
        var roomOptions = rooms.findAll(
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("roomTypeId"))).stream().map(
                r -> new QuotationSearchFilters.RoomTypeOption(r.getRoomTypeId(), r.getName())).toList();
        return new QuotationSearchFilters(hotelOptions, roomOptions);
    }

    public List<OfferingOccupancy> occupancy(Integer hotelId, Integer roomTypeId, LocalDate checkIn,
            LocalDate checkOut, String city) {
        if (hotelId == null || roomTypeId == null) {
            throw QuotationSearchException.invalid("Select a hotel and room type for occupancy guidance");
        }
        var request = new QuotationSearchRequest(checkIn, checkOut,
                List.of(new QuotationSearchRequest.RequestedRoom(1)), city, hotelId, roomTypeId);
        validate(request, LocalDate.now(clock));
        var candidates = data.candidates(request);
        if (candidates.size() > QuotationSearchRepository.MAX_OFFERINGS) {
            throw QuotationSearchException.tooBroad();
        }
        if (candidates.stream().anyMatch(offering -> offering.maxAdults() < 1)) {
            throw QuotationSearchException.inconsistent();
        }
        return candidates.stream().map(
                offering -> new OfferingOccupancy(offering.offeringId(), offering.roomTypeId(),
                        offering.roomTypeName(), offering.maxAdults())).toList();
    }

    /**
     * Evaluates each eligible offering against the whole room request independently.
     * Availability is the minimum free inventory across occupied nights. These read-only
     * results do not reserve inventory; confirmation must repeat the checks under locks.
     */
    public QuotationSearchResponse search(QuotationSearchRequest request) {
        return evaluate(request, null);
    }

    /**
     * Prices exactly the submitted offerings within one hotel and approved contract.
     * Repeated offering IDs share one inventory pool; adult counts and selection order
     * are preserved rather than replaced by alternative room types.
     *
     * @throws QuotationSearchException when the exact selection cannot be quoted
     */
    public Quotation customize(QuotationSelectionRequest selection) {
        if (selection == null || !validator.validate(selection).isEmpty()) {
            throw QuotationSearchException.invalid("Check selected hotel, contract, dates and rooms");
        }
        var request = new QuotationSearchRequest(selection.checkIn(), selection.checkOut(),
                selection.rooms().stream().map(
                        r -> new QuotationSearchRequest.RequestedRoom(r.adults())).toList(), null,
                selection.hotelId(), null);
        var result = evaluate(request, selection);
        if (result.quotations().size() != 1 ||
                !result.quotations().get(0).hotelId().equals(selection.hotelId())) {
            String reason = result.unavailableOptions().stream().flatMap(o -> o.reasons().stream()).map(
                    UnavailabilityReason::message).collect(Collectors.joining(" "));
            throw QuotationSearchException.invalid(reason.isBlank() ?
                    "Selected rooms are unavailable or do not belong to the same eligible hotel and contract" :
                    reason);
        }
        return result.quotations().get(0);
    }

    private QuotationSearchResponse evaluate(QuotationSearchRequest request,
            QuotationSelectionRequest selection) {
        LocalDate today = LocalDate.now(clock);
        validate(request, today);
        List<Candidate> closed = data.closedCandidates(request);
        Set<Integer> closedOfferingIds = closed.stream().map(Candidate::offeringId).collect(
                Collectors.toSet());
        List<Candidate> candidates = new ArrayList<>(data.candidates(request));
        candidates.addAll(closed);
        if (selection != null) {
            candidates.removeIf(offering -> offering.contractId() != selection.contractId());
        }
        if (candidates.size() > QuotationSearchRepository.MAX_OFFERINGS) {
            throw QuotationSearchException.tooBroad();
        }
        int nights = (int) ChronoUnit.DAYS.between(request.checkIn(), request.checkOut());
        if (candidates.isEmpty()) {
            return response(request, today, nights, List.of(), List.of());
        }
        Set<Integer> ids = candidates.stream().map(Candidate::offeringId).collect(Collectors.toSet());
        Set<Integer> contracts = candidates.stream().map(Candidate::contractId).collect(Collectors.toSet());
        Set<Integer> hotelIds = candidates.stream().map(Candidate::hotelId).collect(Collectors.toSet());
        if (data.inconsistent(ids, contracts, hotelIds, today)) {
            throw QuotationSearchException.inconsistent();
        }
        List<Season> seasons = data.seasons(ids);
        List<BookedRoom> bookings = data.bookedRooms(ids, request.checkIn(), request.checkOut());
        if (seasons.size() > QuotationSearchRepository.MAX_SEASONS ||
                bookings.size() > QuotationSearchRepository.MAX_BOOKED_ITEMS) {
            throw QuotationSearchException.tooBroad();
        }
        Map<Integer, List<Season>> bySeason = seasons.stream().collect(
                Collectors.groupingBy(Season::offeringId));
        Map<Integer, List<BookedRoom>> byBooking = bookings.stream().collect(
                Collectors.groupingBy(BookedRoom::offeringId));
        Map<Integer, List<NightlyQuotationPricing.Tariff>> tariffs = new HashMap<>();
        Map<Integer, Integer> available = new HashMap<>();
        for (Candidate offering : candidates) {
            validateCandidate(offering, request);
            tariffs.put(offering.offeringId(),
                    pricing.tariffs(offering, bySeason.getOrDefault(offering.offeringId(), List.of()),
                            request.checkIn(), request.checkOut()));
            available.put(offering.offeringId(),
                    inventory.available(offering.offeringId(), offering.contractedRooms(),
                            byBooking.getOrDefault(offering.offeringId(), List.of()), request.checkIn(),
                            request.checkOut()));
        }
        List<Quotation> results = new ArrayList<>();
        List<UnavailableOption> unavailable = new ArrayList<>();
        List<SelectionOffering> choices = selectionOfferings(request, candidates, available,
                closedOfferingIds, tariffs);
        Map<Integer, List<Candidate>> groups = candidates.stream().collect(Collectors.groupingBy(
                offering -> selection != null ? offering.contractId() : offering.offeringId(), TreeMap::new,
                Collectors.toList()));
        var context = new SelectionContext(request, selection, closedOfferingIds, available, tariffs, nights);
        for (List<Candidate> group : groups.values()) {
            addSelectionResult(context, group, results, unavailable);
        }
        results.sort(Comparator.comparing(Quotation::currencyCode).thenComparing(
                Quotation::totalPrice).thenComparing(Quotation::hotelId).thenComparing(
                Quotation::contractId));
        unavailable.sort(Comparator.comparing(UnavailableOption::hotelName).thenComparingInt(
                UnavailableOption::hotelId).thenComparingInt(UnavailableOption::contractId));
        var result = response(request, today, nights, results, unavailable);
        return new QuotationSearchResponse(result.businessDate(), result.checkIn(), result.checkOut(),
                result.nights(), result.quotations(), result.matchingOptions(), result.truncated(),
                selection == null ? INDIVIDUAL_ROOM_TYPES : "EXPLICIT_SELECTION", result.notice(),
                result.unavailableOptions(), result.matchingUnavailableOptions(),
                result.unavailableTruncated(), choices);
    }

    private List<SelectionOffering> selectionOfferings(QuotationSearchRequest request,
            List<Candidate> candidates, Map<Integer, Integer> available, Set<Integer> closedOfferingIds,
            Map<Integer, List<NightlyQuotationPricing.Tariff>> tariffs) {
        return candidates.stream().map(
                offering -> new SelectionOffering(offering.hotelId(), offering.hotelName(),
                        offering.contractId(), offering.offeringId(), offering.roomTypeName(),
                        offering.maxAdults(), available.get(offering.offeringId()), offering.currency(),
                        closedOfferingIds.contains(offering.offeringId()),
                        java.util.stream.IntStream.range(0, request.rooms().size()).mapToObj(
                                r -> new SelectionRoom(r + 1, request.rooms().get(r).adults(),
                                        request.rooms().get(r).adults() <= offering.maxAdults() ?
                                                pricing.total(tariffs.get(offering.offeringId()),
                                                        request.rooms().get(r).adults(), offering.markup()) :
                                                null)).toList())).toList();
    }

    private void validateCandidate(Candidate offering, QuotationSearchRequest request) {
        if (offering.offeringId() <= 0 || offering.contractId() <= 0 || offering.contractCode() == null ||
                offering.contractCode().isBlank() || offering.contractCode().length() > 45 ||
                offering.hotelName() == null || offering.hotelName().isBlank() ||
                offering.roomTypeName() == null || offering.roomTypeName().isBlank() || !validator.validate(
                new ContractRequest(offering.hotelId(), offering.validFrom(), offering.validTo(),
                        offering.markup(), offering.currency())).isEmpty() || !validator.validate(
                new OfferingRequest(offering.roomTypeId(), offering.baseRate(), offering.contractedRooms(),
                        offering.maxAdults(), OfferingStatus.ENABLED)).isEmpty() ||
                offering.validFrom().isAfter(request.checkIn()) ||
                offering.validTo().isBefore(request.checkOut().minusDays(1))) {
            throw QuotationSearchException.inconsistent();
        }
    }

    private void validateGroup(List<Candidate> group, Candidate parent) {
        if (group.stream().anyMatch(offering -> offering.hotelId() != parent.hotelId() ||
                !offering.currency().equals(parent.currency()) ||
                offering.markup().compareTo(parent.markup()) != 0)) {
            throw QuotationSearchException.inconsistent();
        }
    }

    private void addSelectionResult(SelectionContext context, List<Candidate> group, List<Quotation> results,
            List<UnavailableOption> unavailable) {
        var request = context.request();
        var selection = context.selection();
        var closedOfferingIds = context.closedOfferingIds();
        var available = context.available();
        var tariffs = context.tariffs();
        int nights = context.nights();
        group.sort(Comparator.comparingInt(Candidate::offeringId));
        Candidate parent = group.get(0);
        validateGroup(group, parent);
        int count = request.rooms().size();
        Map<Integer, Candidate> byId = group.stream().collect(
                Collectors.toMap(Candidate::offeringId, offering -> offering));
        List<Candidate> selected = new ArrayList<>();
        Map<Integer, Integer> demand = new HashMap<>();
        List<String> occupancyErrors = new ArrayList<>();
        for (int r = 0; r < count; r++) {
            // Primary results use one room type; customized results use exactly the submitted offering IDs.
            Candidate offering = selection == null ? parent : byId.get(selection.rooms().get(r).offeringId());
            if (offering == null) {
                throw QuotationSearchException.invalid(
                        "Selected offering does not belong to the same eligible hotel and approved contract");
            }
            selected.add(offering);
            demand.merge(offering.offeringId(), 1, Integer::sum);
            int adults = request.rooms().get(r).adults();
            if (adults > offering.maxAdults()) {
                occupancyErrors.add("Maximum " + offering.maxAdults() + " adults per room. Room " + (r + 1) +
                        " requests " + adults + " adults; maximum is " + offering.maxAdults() + ".");
            }
        }
        List<UnavailabilityReason> reasons = unavailabilityReasons(selected, group, closedOfferingIds,
                occupancyErrors, demand, available);
        // Pricing always follows the same explicit offerings, including any non-bookable indicative total.
        List<BigDecimal> roomPrices = new ArrayList<>();
        BigDecimal total = new BigDecimal("0.00");
        if (occupancyErrors.isEmpty()) {
            for (int r = 0; r < count; r++) {
                Candidate offering = selected.get(r);
                BigDecimal price = pricing.total(tariffs.get(offering.offeringId()),
                        request.rooms().get(r).adults(), offering.markup());
                roomPrices.add(price);
                total = total.add(price);
            }
        }
        if (!reasons.isEmpty()) {
            unavailable.add(new UnavailableOption(parent.hotelId(), parent.hotelName(), parent.city(),
                    parent.contractId(), parent.contractCode(), parent.currency(),
                    AvailabilityStatus.NOT_AVAILABLE, group.stream().map(
                    offering -> new UnavailableOffering(offering.offeringId(), offering.roomTypeId(),
                            offering.roomTypeName(), offering.maxAdults(),
                            available.get(offering.offeringId()))).toList(), List.copyOf(reasons),
                    occupancyErrors.isEmpty() ? total : null));
            return;
        }
        List<RoomAllocation> allocated = new ArrayList<>();
        for (int r = 0; r < count; r++) {
            Candidate offering = selected.get(r);
            int adults = request.rooms().get(r).adults();
            int availableRooms = available.get(offering.offeringId());
            int used = demand.get(offering.offeringId());
            allocated.add(new RoomAllocation(r + 1, adults, offering.offeringId(), offering.roomTypeId(),
                    offering.roomTypeName(), offering.contractedRooms(), availableRooms, used,
                    availableRooms - used, roomPrices.get(r),
                    pricing.breakdown(tariffs.get(offering.offeringId()), adults, offering.markup())));
        }
        results.add(new Quotation(parent.hotelId(), parent.hotelName(), parent.city(), parent.contractId(),
                parent.contractCode(), parent.currency(), request.checkIn(), request.checkOut(), nights,
                List.copyOf(allocated), total, "INFORMATIONAL"));
    }

    private List<UnavailabilityReason> unavailabilityReasons(List<Candidate> selected, List<Candidate> group,
            Set<Integer> closedOfferingIds, List<String> occupancyErrors, Map<Integer, Integer> demand,
            Map<Integer, Integer> available) {
        List<UnavailabilityReason> reasons = new ArrayList<>();
        if (selected.stream().anyMatch(offering -> closedOfferingIds.contains(offering.offeringId()))) {
            reasons.add(new UnavailabilityReason(ReasonCode.HOTEL_CLOSED,
                    "Hotel is closed during the requested stay."));
        }
        if (!occupancyErrors.isEmpty()) {
            reasons.add(new UnavailabilityReason(ReasonCode.MAX_ADULT_OCCUPANCY,
                    String.join(" ", occupancyErrors)));
        }
        for (Candidate offering : group) {
            int requested = demand.getOrDefault(offering.offeringId(), 0);
            int remaining = available.get(offering.offeringId());
            if (requested > remaining) {
                reasons.add(new UnavailabilityReason(ReasonCode.INSUFFICIENT_INVENTORY,
                        "Only " + remaining + (remaining == 1 ? " room available; " : " rooms available; ") +
                                requested + " requested."));
            }
        }
        return reasons;
    }

    private QuotationSearchResponse response(QuotationSearchRequest r, LocalDate today, int nights,
            List<Quotation> quotes, List<UnavailableOption> unavailable) {
        return new QuotationSearchResponse(today, r.checkIn(), r.checkOut(), nights,
                List.copyOf(quotes.subList(0, Math.min(MAX_RESULTS, quotes.size()))), quotes.size(),
                quotes.size() > MAX_RESULTS, INDIVIDUAL_ROOM_TYPES,
                "Informational quotation only. Prices and availability must be rechecked atomically at reservation confirmation. Available options are shown first, ordered by currency, then total price.",
                List.copyOf(unavailable.subList(0, Math.min(MAX_RESULTS, unavailable.size()))),
                unavailable.size(), unavailable.size() > MAX_RESULTS);
    }

    private void validate(QuotationSearchRequest r, LocalDate today) {
        if (r == null) {
            throw QuotationSearchException.invalid("Search details are required");
        }
        var violations = validator.validate(r);
        if (!violations.isEmpty()) {
            throw QuotationSearchException.invalid(violations.stream().map(
                    v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().collect(
                    Collectors.joining("; ")));
        }
        long nights = ChronoUnit.DAYS.between(r.checkIn(), r.checkOut());
        if (r.checkIn().isBefore(today)) {
            throw QuotationSearchException.invalid("Check-in cannot be before today's business date");
        }
        if (nights < 1 || nights > MAX_NIGHTS) {
            throw QuotationSearchException.invalid("Stay must be between 1 and 31 occupied nights");
        }
        if (r.checkIn().isAfter(today.plusDays(MAX_ADVANCE_DAYS))) {
            throw QuotationSearchException.invalid("Check-in must be within 730 days of the business date");
        }
        if (r.hotelId() != null && !hotels.existsById(r.hotelId())) {
            throw new QuotationSearchException(404, "Selected hotel was not found");
        }
        if (r.roomTypeId() != null && !rooms.existsById(r.roomTypeId())) {
            throw new QuotationSearchException(404, "Selected room type was not found");
        }
    }
}
