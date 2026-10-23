package com.suntravels.integration;

import com.suntravels.entity.HotelStatus;
import com.suntravels.repository.HotelRepository;
import com.suntravels.service.HotelService;
import com.suntravels.service.QuotationSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable( named = "SUNTRAVELS_HOTEL_FUNCTIONAL_MYSQL_TESTS", matches = "true" )
class HotelFunctionalIndexMySqlIntegrationTest
{
    @Autowired JdbcTemplate jdbc;
    @Autowired HotelRepository hotels;
    @Autowired HotelService service;
    @Autowired QuotationSearchService search;

    @BeforeEach
    void disposableMigratedDatabaseOnly()
    {
        assertEquals( "suntravels_hotel_identity_20261022_test",
                jdbc.queryForObject( "select database()", String.class ) );
        assertEquals( 0, jdbc.queryForObject( """
                select count(*) from information_schema.columns
                where table_schema=database() and table_name='hotel'
                 and column_name in ('identity_name',
                    'identity_country','identity_city','identity_address')
                """, Integer.class ) );
    }

    @Test
    void schemaRetainsSearchColumnsAndFourFunctionalIdentityParts()
    {
        assertEquals( 11, jdbc.queryForObject( """
                select count(*) from information_schema.columns
                where table_schema=database() and table_name='hotel'
                """, Integer.class ) );
        assertEquals( 4, jdbc.queryForObject( """
                select count(*) from information_schema.statistics
                where table_schema=database() and table_name='hotel' and expression is not null
                """, Integer.class ) );
    }

    @Test
    void nullAndBlankIdentityPartsStillCollide()
    {
        jdbc.update( "insert into hotel(name,status) values ('Missing parts fixture','INACTIVE')" );
        assertEquals( 1, hotels.countIdentity( " MISSING PARTS FIXTURE ", "", "", "", null ) );
        assertThrows( DataIntegrityViolationException.class, () -> jdbc.update( """
                insert into hotel(name,country,city,address,status)
                values (' MISSING PARTS FIXTURE ',' ', '', '\t', 'ACTIVE')
                """ ) );
    }

    @Test
    void inactiveManagementSubstringAndEscapedSearchesStillWork()
    {
        jdbc.update( """
                insert into hotel(name,city,country,address,status)
                values ('Functional Ocean View',' Galle ',' Sri Lanka ','Street 1','INACTIVE'),
                       ('Functional %_! Hotel',' Galle ',' Sri Lanka ','Street 2','ACTIVE')
                """ );
        var matches = service.options( "oCeAn", null );
        assertEquals( 1, matches.size() );
        assertEquals( "INACTIVE", matches.get( 0 ).getStatus() );
        assertTrue( service.options( "oCeAn", HotelStatus.ACTIVE ).isEmpty() );
        assertEquals( 1, service.options( "%_!", null ).size() );
    }

    @Test
    void locationSuggestionsAndStoredRawTextArePreserved()
    {
        jdbc.update( """
                insert into hotel(name,city,country,address,status)
                values ('Functional Location',' Function City ',' Sri Lanka ','Street 1','ACTIVE'),
                       ('Functional Hidden',' Function City ',' United States ','Street 2','INACTIVE')
                """ );
        var cities = search.citySuggestions( "Fu", "LK" );
        assertEquals( 1, cities.size() );
        assertEquals( "Function City", cities.get( 0 ).city() );
        assertEquals( "Sri Lanka", cities.get( 0 ).country() );
        assertEquals( 1, search.hotelSuggestions( "Functional", "Function City", "LK" ).size() );
        assertEquals( " Function City ", jdbc.queryForObject(
                "select city from hotel where name='Functional Location'", String.class ) );
    }
}
