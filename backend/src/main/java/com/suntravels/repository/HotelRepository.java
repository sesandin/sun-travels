package com.suntravels.repository;

import com.suntravels.entity.Hotel;
import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface HotelRepository extends JpaRepository<Hotel, Integer>,
        JpaSpecificationExecutor<Hotel> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hotel h where h.hotelId = :id")
    Optional<Hotel> findForUpdate(@Param("id") Integer id);
}
