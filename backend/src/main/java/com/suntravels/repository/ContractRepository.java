package com.suntravels.repository;

import com.suntravels.entity.Contract;
import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, Integer>,
        JpaSpecificationExecutor<Contract> {
    // Loads matching records with the requested query and ordering.
    @Override
    @EntityGraph(attributePaths = {"hotel"})
    org.springframework.data.domain.Page<Contract> findAll(
            org.springframework.data.jpa.domain.Specification<Contract> specification,
            org.springframework.data.domain.Pageable pageable);

    // Checks whether a contract code is already in use.
    boolean existsByContractCode(String code);

    // Checks whether another contract uses the same code.
    boolean existsByContractCodeAndContractIdNot(String code, Integer id);

    // Retrieves the hotel ID associated with a contract.
    @Query("select c.hotel.hotelId from Contract c where c.contractId = :id")
    Optional<Integer> findHotelId(@Param("id") Integer id);

    // Finds approved contracts whose validity overlaps the candidate contract.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.hotel.hotelId = :hotelId and c.status = com.suntravels.entity.ContractStatus.APPROVED and c.contractId <> :id and c.validFrom <= :to and c.validTo >= :from order by c.contractId")
    java.util.List<Contract> approvedOverlaps(@Param("hotelId") Integer hotelId, @Param("id") Integer id,
            @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    // Serialize header, markup and lifecycle writes without adding a version column.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.contractId = :id")
    Optional<Contract> findForUpdate(@Param("id") Integer id);
}
