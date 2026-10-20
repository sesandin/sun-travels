package com.suntravels.repository;

import com.suntravels.entity.Hotel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HotelRepository extends JpaRepository<Hotel,Integer>,
                                                 JpaSpecificationExecutor<Hotel>
{
    // Retrieves and locks the record before a coordinated update.
    @Lock( LockModeType.PESSIMISTIC_WRITE )
    @Query( "select h from Hotel h where h.hotelId = :id" )
    Optional<Hotel> findForUpdate( @Param( "id" ) Integer id );
}
