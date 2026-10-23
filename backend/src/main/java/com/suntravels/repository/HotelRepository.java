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
             cast(lower(regexp_replace(coalesce(name,''),'^[[:space:]]+|[[:space:]]+$','')) as char(150) character set utf8mb3) collate utf8mb3_bin
             =cast(lower(regexp_replace(coalesce(:name,''),'^[[:space:]]+|[[:space:]]+$','')) as char(150) character set utf8mb3) collate utf8mb3_bin
             and cast(lower(regexp_replace(coalesce(country,''),'^[[:space:]]+|[[:space:]]+$','')) as char(100) character set utf8mb3) collate utf8mb3_bin
             =cast(lower(regexp_replace(coalesce(:country,''),'^[[:space:]]+|[[:space:]]+$','')) as char(100) character set utf8mb3) collate utf8mb3_bin
             and cast(lower(regexp_replace(coalesce(city,''),'^[[:space:]]+|[[:space:]]+$','')) as char(100) character set utf8mb3) collate utf8mb3_bin
             =cast(lower(regexp_replace(coalesce(:city,''),'^[[:space:]]+|[[:space:]]+$','')) as char(100) character set utf8mb3) collate utf8mb3_bin
             and cast(lower(regexp_replace(coalesce(address,''),'^[[:space:]]+|[[:space:]]+$','')) as char(255) character set utf8mb3) collate utf8mb3_bin
             =cast(lower(regexp_replace(coalesce(:address,''),'^[[:space:]]+|[[:space:]]+$','')) as char(255) character set utf8mb3) collate utf8mb3_bin
             and (:excluded is null or hotel_id<>:excluded)
            """, nativeQuery = true)
    long countIdentity(@Param("name") String name, @Param("country") String country,
            @Param("city") String city, @Param("address") String address, @Param("excluded") Integer excluded);

    @Query(value = """
            select hotel_id as hotelId,name,city,country,status from hotel
            where lower(name) like lower(:pattern) escape '!' and status=:status
            order by name,hotel_id limit 20
            """, nativeQuery = true)
    List<ManagementHotelOption> options(
            @Param( "pattern" ) String pattern, @Param( "status" ) String status );

    // Each status branch returns at most 20 rows; the final sort sees at most 40.
    // Contains matching may scan the status range instead of using a name-prefix range.
    @Query(value = """
            (select hotel_id as hotelId,name,city,country,status from hotel
             where status='ACTIVE' and lower(name) like lower(:pattern) escape '!' order by name,hotel_id limit 20)
            union all
            (select hotel_id as hotelId,name,city,country,status from hotel
             where status='INACTIVE' and lower(name) like lower(:pattern) escape '!' order by name,hotel_id limit 20)
            order by name,hotelId limit 20
            """, nativeQuery = true)
    List<ManagementHotelOption> allStatusOptions( @Param( "pattern" ) String pattern );

    @Query(value = "select hotel_id as hotelId,name,city,country,status from hotel where hotel_id=:id", nativeQuery = true)
    Optional<ManagementHotelOption> option(@Param("id") Integer id);

    // Retrieves and locks the record before a coordinated update.
    @Lock( LockModeType.PESSIMISTIC_WRITE )
    @Query( "select h from Hotel h where h.hotelId = :id" )
    Optional<Hotel> findForUpdate( @Param( "id" ) Integer id );
}
