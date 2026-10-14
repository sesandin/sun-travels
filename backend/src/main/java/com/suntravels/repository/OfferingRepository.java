package com.suntravels.repository;
import com.suntravels.entity.ContractRoomOffering;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface OfferingRepository extends JpaRepository<ContractRoomOffering,Integer> {
 // Fetch only to-one relationships: no collection join inflation or per-row lazy reads.
 @Query("select o from ContractRoomOffering o join fetch o.contract c join fetch c.hotel join fetch o.roomType order by o.offeringId desc")
 List<ContractRoomOffering> findDirectoryOfferings();
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select o from ContractRoomOffering o where o.contract.contractId = :id order by o.offeringId")
 List<ContractRoomOffering> findAllForUpdate(@Param("id") Integer id);
 List<ContractRoomOffering> findByContractContractIdOrderByOfferingId(Integer id);
 boolean existsByContractContractIdAndRoomTypeRoomTypeIdAndOfferingIdNot(Integer contractId,Integer roomTypeId,Integer offeringId);
 @Query("select o.contract.contractId from ContractRoomOffering o where o.offeringId = :id") Optional<Integer> findContractId(@Param("id") Integer id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from ContractRoomOffering o where o.offeringId = :id") Optional<ContractRoomOffering> findForUpdate(@Param("id") Integer id);
}
