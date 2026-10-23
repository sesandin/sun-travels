package com.suntravels.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="SUNTRAVELS_VALIDATION_MYSQL_TESTS", matches="true")
class ValidationMySqlIntegrationTest
{
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void disposableDatabaseOnly()
    {
        assertEquals("suntravels_validation_20261022_test", jdbc.queryForObject("select database()", String.class));
        assertTrue(jdbc.queryForObject("select @@sql_mode", String.class).contains("STRICT"));
    }

    @ParameterizedTest
    @CsvSource({"name,150", "address,255", "city,100", "country,100", "phone,20", "email,100"})
    void actualHotelVarcharBoundary(String column, int max)
    {
        // Column comes only from the fixed test annotation, never user input.
        jdbc.update("insert into hotel(name,status) values ('Validation fixture','ACTIVE')");
        Integer id = jdbc.queryForObject("select last_insert_id()", Integer.class);
        jdbc.update("update hotel set " + column + "=? where hotel_id=?", "a".repeat(max), id);
        assertEquals(max, jdbc.queryForObject("select char_length(" + column + ") from hotel where hotel_id=?", Integer.class, id));
        String updateSql = "update hotel set " + column + "=? where hotel_id=?";
        String tooLong = "a".repeat(max+1);
        assertThrows(DataAccessException.class, () -> jdbc.update(updateSql, tooLong, id));
    }

    @Test void actualRoomAndCustomerBoundaries()
    {
        jdbc.update("insert into room_type(name,description) values (?,?)", "a".repeat(100), "a".repeat(255));
        String longRoomName = "a".repeat(101);
        assertThrows(DataAccessException.class, () -> jdbc.update("insert into room_type(name) values (?)", longRoomName));
        jdbc.update("insert into customer(name,phone,email) values (?,?,?)", "a".repeat(150), "1".repeat(20), "a".repeat(150));
        String longCustomerName = "a".repeat(151);
        assertThrows(DataAccessException.class, () -> jdbc.update("insert into customer(name) values (?)", longCustomerName));
    }
}
