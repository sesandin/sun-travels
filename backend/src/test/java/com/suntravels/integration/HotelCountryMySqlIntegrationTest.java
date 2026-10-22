package com.suntravels.integration;

import com.suntravels.dto.HotelRequest;
import com.suntravels.dto.HotelUpdateRequest;
import com.suntravels.entity.HotelStatus;
import com.suntravels.service.HotelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable( named = "SUNTRAVELS_HOTEL_MYSQL_TESTS", matches = "true" )
class HotelCountryMySqlIntegrationTest
{
    @Autowired HotelService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void storesMappedCountriesAndPreservesLegacyReads()
    {
        assertTrue( jdbc.queryForObject( "select database()", String.class ).endsWith( "_test" ) );
        var create = new HotelRequest();
        create.setName( "Country fixture" );
        create.setAddress( "Address" );
        create.setCity( "An editable new city" );
        create.setCountry( "LK" );
        create.setStarRating( (byte) 3 );
        create.setStatus( HotelStatus.ACTIVE );
        var saved = service.createHotel( create );
        assertEquals( "Sri Lanka", saved.getCountry() );
        assertEquals( "Sri Lanka", jdbc.queryForObject( "select country from hotel where hotel_id=?", String.class, saved.getHotelId() ) );
        var update = new HotelUpdateRequest();
        update.setName( saved.getName() );
        update.setAddress( saved.getAddress() );
        update.setCity( saved.getCity() );
        update.setCountry( "US" );
        update.setStarRating( saved.getStarRating() );
        assertEquals( "United States", service.updateHotel( saved.getHotelId(), update ).getCountry() );
        entityManager.flush();
        assertEquals( "United States", jdbc.queryForObject( "select country from hotel where hotel_id=?", String.class, saved.getHotelId() ) );
        assertEquals( HotelStatus.ACTIVE, service.getHotelById( saved.getHotelId() ).getStatus() );
        jdbc.update( "update hotel set country='Historical custom value' where hotel_id=?", saved.getHotelId() );
        // Existing data stays readable; no automatic bulk conversion is performed.
        // Clear the JPA persistence context so this read observes the SQL fixture.
        entityManager.clear();
        assertEquals( "Historical custom value", service.getHotelById( saved.getHotelId() ).getCountry() );
    }

    @Autowired jakarta.persistence.EntityManager entityManager;
}
