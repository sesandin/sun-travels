package com.suntravels.repository;
import java.util.List;
import com.suntravels.dto.ManagementHotelOption;

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
    @Query(value = """
            select count(*) from hotel where
             identity_name=lower(regexp_replace(coalesce(:name,''),'^[[:space:]]+|[[:space:]]+$',''))
             and identity_country=lower(regexp_replace(coalesce(:country,''),'^[[:space:]]+|[[:space:]]+$',''))
             and identity_city=lower(regexp_replace(coalesce(:city,''),'^[[:space:]]+|[[:space:]]+$',''))
             and identity_address=lower(regexp_replace(coalesce(:address,''),'^[[:space:]]+|[[:space:]]+$',''))
             and (:excluded is null or hotel_id<>:excluded)
            """, nativeQuery = true)
    long countIdentity(@Param("name") String name, @Param("country") String country,
            @Param("city") String city, @Param("address") String address, @Param("excluded") Integer excluded);

    @Query(value = """
            select hotel_id as hotelId,name,city,country,status from hotel
            where name like :prefix escape '!' and status=:status
            order by name,hotel_id limit 20
            """, nativeQuery = true)
    List<ManagementHotelOption> options(
            @Param("prefix") String prefix, @Param("status") String status);

    // Two bounded status ranges reuse V001's status/name index; sort at most 40 rows.
    @Query(value = """
            (select hotel_id as hotelId,name,city,country,status from hotel
             where status='ACTIVE' and name like :prefix escape '!' order by name,hotel_id limit 20)
            union all
            (select hotel_id as hotelId,name,city,country,status from hotel
             where status='INACTIVE' and name like :prefix escape '!' order by name,hotel_id limit 20)
            order by name,hotelId limit 20
            """, nativeQuery = true)
    List<ManagementHotelOption> allStatusOptions(@Param("prefix") String prefix);

    @Query(value = "select hotel_id as hotelId,name,city,country,status from hotel where hotel_id=:id", nativeQuery = true)
    Optional<ManagementHotelOption> option(@Param("id") Integer id);

    // Retrieves and locks the record before a coordinated update.
    @Lock( LockModeType.PESSIMISTIC_WRITE )
    @Query( "select h from Hotel h where h.hotelId = :id" )
    Optional<Hotel> findForUpdate( @Param( "id" ) Integer id );
}
