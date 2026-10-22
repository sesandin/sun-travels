package com.suntravels.integration;

import com.suntravels.service.HotelService;
import com.suntravels.entity.HotelStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="SUNTRAVELS_CONTRACT_LOOKUP_MYSQL_TESTS", matches="true")
class ContractHotelLookupMySqlIntegrationTest
{
    @Autowired HotelService service;
    @Autowired JdbcTemplate jdbc;

    @Test void boundedStableManagementOptionsAndIndependentResolution()
    {
        assertEquals("suntravels_contract_lookup_20261022_test", jdbc.queryForObject("select database()", String.class));
        for (int i=0; i<25; i++) jdbc.update("insert into hotel(name,city,country,address,status) values ('Lookup Same','Galle','Sri Lanka',?,?)", "Fixture street " + i, i%2==0 ? "ACTIVE" : "INACTIVE");
        var options = service.options("lookup", null);
        assertEquals(20, options.size());
        var json = tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(options);
        assertEquals(5, json.get(0).size());
        assertEquals(options.get(0).getHotelId().intValue(), json.get(0).get("hotelId").asInt());
        for (int i=1; i<options.size(); i++) assertTrue(options.get(i).getHotelId()>options.get(i-1).getHotelId());
        assertTrue(options.stream().anyMatch(h -> h.getStatus().equals("INACTIVE")));
        assertEquals("Galle", options.get(0).getCity());
        assertEquals("Sri Lanka", options.get(0).getCountry());
        var inactive = service.options("Lookup", HotelStatus.INACTIVE);
        assertEquals(12, inactive.size());
        assertTrue(inactive.stream().allMatch(h -> h.getStatus().equals("INACTIVE")));
        assertEquals(13, service.options("Lookup", HotelStatus.ACTIVE).size());
        Integer lastId = jdbc.queryForObject("select max(hotel_id) from hotel where name='Lookup Same'", Integer.class);
        assertTrue(options.stream().noneMatch(h -> h.getHotelId().equals(lastId)));
        assertEquals(lastId, service.option(lastId).getHotelId());
        assertTrue(service.options("", null).isEmpty());
        assertTrue(service.options("missing fixture", null).isEmpty());
        jdbc.update("insert into hotel(name,status) values ('Lookup%Literal','ACTIVE')");
        assertEquals(1, service.options("Lookup%", null).size());
    }
}
