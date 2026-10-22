package com.suntravels.integration;

import com.suntravels.dto.HotelRequest;
import com.suntravels.dto.HotelUpdateRequest;
import com.suntravels.entity.HotelStatus;
import com.suntravels.exception.DuplicateHotelException;
import com.suntravels.service.HotelService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="SUNTRAVELS_HOTEL_IDENTITY_MYSQL_TESTS",matches="true")
class HotelIdentityMySqlIntegrationTest
{
    @Autowired HotelService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    String name;
    @BeforeEach void disposableOnly() {
        assertEquals("suntravels_hotel_identity_20261022_test",jdbc.queryForObject("select database()",String.class));
        name="IdentityFixture-"+UUID.randomUUID();
    }
    @AfterEach void cleanup() {
        if(name!=null) jdbc.update("delete from hotel where identity_name like ?",name.toLowerCase(java.util.Locale.ROOT)+"%");
    }
    HotelRequest request(String city,String address) {
        var r=new HotelRequest(); r.setName(name); r.setCountry("LK"); r.setCity(city); r.setAddress(address);
        r.setStatus(HotelStatus.INACTIVE); r.setStarRating((byte)3); return r;
    }
    HotelUpdateRequest update(String city,String address) {
        var r=new HotelUpdateRequest(); r.setName(name); r.setCountry("Sri Lanka"); r.setCity(city);
        r.setAddress(address); r.setStarRating((byte)4); return r;
    }
    @Test void creationEditingAndInactiveIdentity() {
        var first=service.createHotel(request("Galle","10 Main Street"));
        var duplicate=request("\t GALLE \n"," 10 MAIN STREET\t"); duplicate.setName(" "+name.toUpperCase(java.util.Locale.ROOT)+" ");
        assertThrows(DuplicateHotelException.class,()->service.createHotel(duplicate));
        var self=update(" GALLE "," 10 MAIN STREET "); self.setName(name.toUpperCase(java.util.Locale.ROOT));
        assertEquals(first.getHotelId(),service.updateHotel(first.getHotelId(),self).getHotelId());
        assertEquals(HotelStatus.INACTIVE,service.getHotelById(first.getHotelId()).getStatus());
        var other=service.createHotel(request("Galle","11 Main Street"));
        assertThrows(DuplicateHotelException.class,()->service.updateHotel(other.getHotelId(),update("galle","10 main street")));
        assertEquals("11 Main Street",service.getHotelById(other.getHotelId()).getAddress());
        assertNotNull(service.createHotel(request("Colombo","10 Main Street")));
        var country=request("Galle","10 Main Street"); country.setCountry("US");
        assertNotNull(service.createHotel(country));
    }
    @Test void databaseRejectsBypassAndPreservesAccents() {
        service.createHotel(request("Galle","Road"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update(
                "insert into hotel(name,country,city,address,status) values (?,?,?,?, 'ACTIVE')",
                "\t"+name.toUpperCase(java.util.Locale.ROOT)+"\n"," SRI LANKA "," GALLE "," road "));
        assertNotNull(service.createHotel(request("Gallé","Road")));
    }
    @Test void concurrentDirectInsertsCannotRegisterTheSameIdentity() throws Exception {
        var gate=new CyclicBarrier(2);
        var pool=Executors.newFixedThreadPool(2);
        Callable<Integer> insert=()-> {
            try(var connection=dataSource.getConnection(); var statement=connection.prepareStatement(
                    "insert into hotel(name,country,city,address,status) values (?,'Sri Lanka','Galle','Road','INACTIVE')")) {
                statement.setString(1,name); gate.await(10,TimeUnit.SECONDS);
                try { statement.executeUpdate(); return 0; }
                catch(SQLException ex) { return ex.getErrorCode(); }
            }
        };
        try {
            var a=pool.submit(insert); var b=pool.submit(insert);
            var outcomes=new java.util.ArrayList<>(java.util.List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)));
            outcomes.sort(Integer::compareTo); assertEquals(java.util.List.of(0,1062),outcomes);
            assertEquals(1,jdbc.queryForObject("select count(*) from hotel where identity_name=?",Integer.class,name.toLowerCase(java.util.Locale.ROOT)));
        } finally { pool.shutdownNow(); }
    }
}
