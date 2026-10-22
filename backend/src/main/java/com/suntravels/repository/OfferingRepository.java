package com.suntravels.repository;

import com.suntravels.entity.ContractRoomOffering;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OfferingRepository extends JpaRepository<ContractRoomOffering,Integer>,
                                                    JpaSpecificationExecutor<ContractRoomOffering>
{
    // Loads matching records with the requested query and ordering.
    @Override
    @EntityGraph( attributePaths = { "contract", "contract.hotel", "roomType" } )
    org.springframework.data.domain.Page<ContractRoomOffering> findAll(
            org.springframework.data.jpa.domain.Specification<ContractRoomOffering> specification,
            org.springframework.data.domain.Pageable pageable );

    // Fetch only to-one relationships: no collection join inflation or per-row lazy reads.
    @Query( "select o from ContractRoomOffering o join fetch o.contract c join fetch c.hotel join fetch o.roomType order by o.offeringId desc" )
    List<ContractRoomOffering> findDirectoryOfferings();

    // Retrieves and locks the contract’s offerings in a consistent order.
    @Lock( LockModeType.PESSIMISTIC_WRITE )
    @Query( "select o from ContractRoomOffering o where o.contract.contractId = :id order by o.offeringId" )
    List<ContractRoomOffering> findAllForUpdate( @Param( "id" ) Integer id );

    // Lists a contract’s offerings in offering-ID order.
    @EntityGraph(attributePaths = { "roomType" })
    List<ContractRoomOffering> findByContractContractIdOrderByOfferingId( Integer id );

    // Checks whether another offering already uses this room type in the contract.
    boolean existsByContractContractIdAndRoomTypeRoomTypeIdAndOfferingIdNot(
            Integer contractId,
            Integer roomTypeId, Integer offeringId );

    // Retrieves the contract ID associated with an offering.
    @Query( "select o.contract.contractId from ContractRoomOffering o where o.offeringId = :id" )
    Optional<Integer> findContractId( @Param( "id" ) Integer id );

    // Retrieves and locks the record before a coordinated update.
    @Lock( LockModeType.PESSIMISTIC_WRITE )
    @Query( "select o from ContractRoomOffering o where o.offeringId = :id" )
    Optional<ContractRoomOffering> findForUpdate( @Param( "id" ) Integer id );
}
