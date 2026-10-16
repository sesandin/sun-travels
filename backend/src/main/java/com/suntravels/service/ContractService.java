package com.suntravels.service;

import com.suntravels.dto.*;
import com.suntravels.entity.*;
import com.suntravels.exception.*;
import com.suntravels.repository.*;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContractService {
    private final ContractRepository repository;
    private final HotelRepository hotels;
    private final Validator validator;
    private final SeasonalRateRepository seasons;
    private final OfferingRepository offerings;
    private final ReservationCommitmentRepository commitments;
    private final Clock clock;

    public ContractService(ContractRepository repository, HotelRepository hotels, Validator validator,
            SeasonalRateRepository seasons, OfferingRepository offerings,
            ReservationCommitmentRepository commitments, Clock clock) {
        this.repository = repository;
        this.hotels = hotels;
        this.validator = validator;
        this.seasons = seasons;
        this.offerings = offerings;
        this.commitments = commitments;
        this.clock = clock;
    }

    public List<ContractResponse> getAllContracts() {
        return repository.findAll(Sort.by("contractId").descending()).stream().map(this::response).toList();
    }

    public ContractResponse getContractById(Integer id) {
        return response(repository.findById(id).orElseThrow(() -> ContractException.missing(id)));
    }

    @Transactional
    public ContractResponse createContract(ContractRequest request) {
        validate(request);
        Hotel hotel = lockedHotel(request.hotelId());
        Contract contract = new Contract();
        apply(contract, request, hotel);
        contract.setStatus(ContractStatus.DRAFT);
        // Satisfy NOT NULL/UNIQUE before MySQL IDENTITY assigns the business ID.
        // Both flushes share this transaction. No temporary reference is returned.
        contract.setContractCode("TMP-" + UUID.randomUUID());
        try {
            contract = repository.saveAndFlush(contract);
            String code = generatedCode(contract.getContractId());
            if (repository.existsByContractCodeAndContractIdNot(code, contract.getContractId())) {
                throw ContractException.conflict("Generated contract code " + code + " already exists. No contract was created.");
            }
            contract.setContractCode(code);
            return response(repository.saveAndFlush(contract));
        } catch (DataIntegrityViolationException ex) {
            throw ContractException.conflict("Contract creation conflicts with an existing code or referenced record. No contract was created.");
        }
    }

    static String generatedCode(Integer id) {
        if (id == null || id <= 0) {
            throw ContractException.conflict("A valid database contract ID could not be allocated");
        }
        return "C" + String.format(Locale.ROOT, "%03d", id);
    }

    @Transactional
    public ContractResponse updateContract(Integer id, ContractRequest request) {
        validate(request);
        Contract contract = coordinated(id, request.hotelId());
        requireStatus(contract, ContractStatus.DRAFT, "Only DRAFT contracts allow header editing");
        if (seasons.countOutsideContract(id, request.validFrom(), request.validTo()) > 0) {
            throw ContractException.conflict("Contract dates would exclude existing seasonal rates");
        }
        apply(contract, request, lockedHotel(request.hotelId()));
        return persist(contract);
    }

    @Transactional
    public ContractResponse updateMarkup(Integer id, ContractMarkupRequest request) {
        Contract contract = coordinated(id, null);
        requireStatus(contract, ContractStatus.APPROVED, "Only APPROVED contracts allow markup updates");
        validate(request);
        contract.setMarkupPercentage(request.markupPercentage());
        return persist(contract);
    }

    /** Stops new sales under the contract without changing confirmed commitments or saved prices. */
    @Transactional
    public ContractResponse suspendContract(Integer id) {
        Contract contract = coordinated(id, null);
        requireStatus(contract, ContractStatus.APPROVED, "Only APPROVED contracts may be suspended");
        contract.setStatus(ContractStatus.SUSPENDED);
        return persist(contract);
    }

    /**
     * Makes termination irreversible only once confirmed commitments have checked out.
     * Checkout today is completed under the injected business clock. The shared hotel
     * lock prevents confirmation from creating a new commitment between this check and
     * the status change; suspension remains available to stop sales during existing stays.
     *
     * @throws ContractException if already terminated, commitments are invalid, or a stay remains outstanding
     */
    @Transactional
    public ContractResponse terminateContract(Integer id) {
        Contract contract = coordinated(id, null);
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw ContractException.conflict("TERMINATED is a terminal status");
        }
        // Preserve tested DRAFT termination, with the same defensive commitment guard.
        offerings.findAllForUpdate(id);
        invalidCommitments(commitments.invalidForContract(id));
        List<Integer> outstanding = commitments.outstanding(id, LocalDate.now(clock));
        if (!outstanding.isEmpty()) {
            long count = outstanding.stream().distinct().count();
            throw ContractException.conflict("Cannot terminate this contract because it has " + count
                    + (count == 1 ? " ongoing or upcoming confirmed reservation. " : " ongoing or upcoming confirmed reservations. ")
                    + "Wait until these stays are completed or resolve them through the existing cancellation process. Suspend the contract to stop new sales.");
        }
        contract.setStatus(ContractStatus.TERMINATED);
        return persist(contract);
    }

    @Transactional
    public ContractResponse approveContract(Integer id) {
        return approve(id, ContractStatus.DRAFT);
    }

    @Transactional
    public ContractResponse reapproveContract(Integer id) {
        return approve(id, ContractStatus.SUSPENDED);
    }

    /**
     * Approval and reapproval share every commercial and replacement-inventory guard.
     * Suspended predecessors can still hold confirmed inventory, so status alone cannot
     * establish that an overlapping replacement contract is safe to approve.
     */
    private ContractResponse approve(Integer id, ContractStatus expected) {
        Contract contract = coordinated(id, null);
        requireStatus(contract, expected, expected == ContractStatus.DRAFT
                ? "Only DRAFT contracts may be approved" : "Only SUSPENDED contracts may be reapproved");
        Hotel hotel = contract.getHotel();
        if (hotel.getStatus() != HotelStatus.ACTIVE) {
            throw ContractException.conflict("Hotel must be ACTIVE for contract approval. Approval never activates a hotel.");
        }
        if (contract.getContractCode() == null || contract.getContractCode().isBlank()
                || contract.getContractCode().length() > 45) {
            throw ContractException.conflict("Persisted contract code is invalid");
        }
        validatePersisted(new ContractRequest(hotel.getHotelId(), contract.getValidFrom(), contract.getValidTo(),
                contract.getMarkupPercentage(), contract.getCurrencyCode()));
        List<ContractRoomOffering> rows = offerings.findAllForUpdate(id);
        if (rows.isEmpty()) {
            throw ContractException.conflict("At least one valid ENABLED offering is required");
        }
        boolean enabled = false;
        Set<Integer> roomIds = new HashSet<>();
        for (ContractRoomOffering row : rows) {
            if (row.getRoomType() == null) {
                throw ContractException.conflict("Offering has no room type");
            }
            validatePersisted(new OfferingRequest(row.getRoomType().getRoomTypeId(), row.getRatePerPersonPerNight(),
                    row.getContractedRoomCount(), row.getMaxAdults(), row.getStatus()));
            if (!roomIds.add(row.getRoomType().getRoomTypeId())) {
                throw ContractException.conflict("Duplicate room type in persisted contract offerings");
            }
            enabled |= row.getStatus() == OfferingStatus.ENABLED;
        }
        if (!enabled) {
            throw ContractException.conflict("At least one valid ENABLED offering is required");
        }
        validateSeasons(contract, seasons.findContractRatesForUpdate(id));
        List<Contract> overlaps = repository.approvedOverlaps(hotel.getHotelId(), id,
                contract.getValidFrom(), contract.getValidTo());
        if (!overlaps.isEmpty()) {
            Contract other = overlaps.get(0);
            throw ContractException.conflict("Validity overlaps APPROVED contract " + other.getContractCode()
                    + " (ID " + other.getContractId() + ") for this hotel");
        }
        invalidCommitments(commitments.invalidForContract(id));
        invalidCommitments(commitments.invalidForReplacement(hotel.getHotelId(), id, roomIds));
        List<Integer> conflicts = commitments.replacementConflicts(hotel.getHotelId(), id,
                contract.getValidFrom(), contract.getValidTo(), LocalDate.now(clock), roomIds);
        if (!conflicts.isEmpty()) {
            throw ContractException.conflict("Approval blocked by CONFIRMED reservation " + conflicts.get(0)
                    + " under another contract for the same hotel and room type");
        }
        contract.setStatus(ContractStatus.APPROVED);
        return persist(contract);
    }

    private void validateSeasons(Contract contract, List<OfferingSeasonalRate> rates) {
        Map<Integer, LocalDate> latestEnd = new HashMap<>();
        for (OfferingSeasonalRate rate : rates) {
            validatePersisted(new SeasonalRateRequest(rate.getValidFrom(), rate.getValidTo(), rate.getRatePerPersonPerNight()));
            if (rate.getValidFrom().isBefore(contract.getValidFrom()) || rate.getValidTo().isAfter(contract.getValidTo())) {
                throw ContractException.conflict("Persisted seasonal dates are outside contract validity");
            }
            Integer offeringId = rate.getOffering().getOfferingId();
            LocalDate prior = latestEnd.put(offeringId, rate.getValidTo());
            if (prior != null && !rate.getValidFrom().isAfter(prior)) {
                throw ContractException.conflict("Persisted seasonal periods overlap");
            }
        }
    }

    private void invalidCommitments(List<Integer> invalid) {
        if (!invalid.isEmpty()) {
            throw ContractException.conflict("CONFIRMED reservation " + invalid.get(0)
                    + " has invalid stay or occupancy data. Correct it before changing lifecycle status.");
        }
    }

    // HOTEL(s ascending ID) -> CONTRACT -> OFFERING -> seasonal/reservation reads.
    // Never retain a managed contract from before the hotel lock. Hotel reassignment
    // is rechecked after locking so a stale scalar lookup cannot use the wrong lock.
    private Contract coordinated(Integer id, Integer targetHotel) {
        Integer currentHotel = repository.findHotelId(id).orElseThrow(() -> ContractException.missing(id));
        TreeSet<Integer> hotelIds = new TreeSet<>();
        hotelIds.add(currentHotel);
        if (targetHotel != null) {
            hotelIds.add(targetHotel);
        }
        hotelIds.forEach(this::lockedHotel);
        Contract contract = repository.findForUpdate(id).orElseThrow(() -> ContractException.missing(id));
        if (!currentHotel.equals(contract.getHotel().getHotelId())) {
            throw ContractException.conflict("Contract hotel changed concurrently. Reload and try again.");
        }
        return contract;
    }

    private Hotel lockedHotel(Integer id) {
        return hotels.findForUpdate(id).orElseThrow(() -> new HotelNotFoundException(id));
    }

    private void requireStatus(Contract contract, ContractStatus status, String message) {
        if (contract.getStatus() != status) {
            throw ContractException.conflict(message);
        }
    }

    private void validate(Object request) {
        if (request == null) {
            throw ContractException.invalid("Contract details are required");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw ContractException.invalid(violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted()
                    .reduce((a, b) -> a + "; " + b).orElseThrow());
        }
    }

    private void validatePersisted(Object request) {
        try {
            validate(request);
        } catch (ContractException ex) {
            throw ContractException.conflict("Persisted commercial terms are invalid: " + ex.getMessage());
        }
    }

    private void apply(Contract contract, ContractRequest request, Hotel hotel) {
        // Code is assigned only at creation; never changed by header edits.
        contract.setHotel(hotel);
        contract.setValidFrom(request.validFrom());
        contract.setValidTo(request.validTo());
        contract.setMarkupPercentage(request.markupPercentage());
        contract.setCurrencyCode(request.currencyCode());
    }

    private ContractResponse persist(Contract contract) {
        try {
            return response(repository.saveAndFlush(contract));
        } catch (DataIntegrityViolationException ex) {
            throw ContractException.conflict("Contract could not be saved because referenced records conflict. Refresh and try again.");
        }
    }

    private ContractResponse response(Contract contract) {
        return new ContractResponse(contract.getContractId(), contract.getContractCode(),
                contract.getHotel().getHotelId(), contract.getHotel().getName(), contract.getValidFrom(),
                contract.getValidTo(), contract.getMarkupPercentage(), contract.getCurrencyCode(), contract.getStatus());
    }
}
