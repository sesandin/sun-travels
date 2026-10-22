package com.suntravels.integration;

import com.suntravels.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="SUNTRAVELS_SUMMARY_MYSQL_TESTS", matches="true")
class ReservationSummaryMySqlIntegrationTest
{
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationRepository repository;
    @Autowired Clock clock;

    long selects() { return Long.parseLong((String) jdbc.queryForMap("show session status like 'Com_select'").get("Value")); }

    @Test void pageAggregationMatchesHistoricalDetailsWithFourQueriesAndNoNightlyPayload()
    {
        assertEquals("suntravels_reservation_summary_20261022_test",jdbc.queryForObject("select database()",String.class));
        int hotel=repository.insert("insert into hotel(name,status) values ('Summary Hotel','INACTIVE')");
        int customer=repository.insert("insert into customer(name,email) values ('Summary Customer','summary@example.test')");
        int type=repository.insert("insert into room_type(name) values ('Summary Room')");
        LocalDate in=LocalDate.now(clock).plusDays(5), out=in.plusDays(3);
        int contract=repository.insert("insert into contract(hotel_id,contract_code,valid_from,valid_to,markup_percentage,currency_code,status) values (?,'SUMMARY',?,?,0,'USD','SUSPENDED')",hotel,in,out);
        int offering=repository.insert("insert into contract_room_offering(contract_id,room_type_id,rate_per_person_per_night,contracted_room_count,max_adults,status) values (?,?,999,50,2,'SUSPENDED')",contract,type);
        List<Integer> ids=new ArrayList<>(), items=new ArrayList<>();
        for(int b=0;b<11;b++) {
            int id=repository.insert("insert into reservation(customer_id,check_in_date,check_out_date,status,currency_code) values (?,?,?,'CONFIRMED','USD')",customer,in,out);
            ids.add(id);
            for(int room=1;room<=2;room++) {
                int item=repository.insert("insert into reservation_item(reservation_id,offering_id,number_of_adults) values (?,?,2)",id,offering);
                items.add(item);
                for(int day=0;day<3;day++) repository.night(item,in.plusDays(day),BigDecimal.ONE,BigDecimal.ZERO,new BigDecimal("10.01").multiply(BigDecimal.valueOf(room)));
            }
        }
        String[] aggregateSql={null}; Object[][] aggregateArgs={null};
        JdbcTemplate measured=new JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource())) {
            @Override public void query(String sql, RowCallbackHandler callback, Object... args) {
                aggregateSql[0]=sql; aggregateArgs[0]=args; super.query(sql,callback,args);
            }
        };
        var reader=new ReservationRepository(measured,clock);
        long before=selects();
        var page=reader.list("Summary Customer","",0,10);
        assertEquals(4,selects()-before);
        assertEquals(11,page.totalElements()); assertEquals(10,page.reservations().size());
        for(var summary:page.reservations()) {
            assertEquals(new BigDecimal("90.09"),summary.totalPrice());
            assertTrue(summary.hasOperationalWarnings()); assertEquals(2,summary.rooms().size());
        }
        var mapper=tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        String summaryJson=mapper.writeValueAsString(page);
        assertFalse(summaryJson.contains("nightlyPrices")); assertFalse(summaryJson.contains("bookedRatePerAdult"));
        before=selects();
        var details=page.reservations().stream().map(s -> repository.detail(s.reservationId())).toList();
        long detailSelects=selects()-before;
        assertEquals(50,detailSelects);
        for(int n=0;n<details.size();n++) assertEquals(details.get(n).totalPrice(),page.reservations().get(n).totalPrice());
        System.out.println("SUMMARY_EVIDENCE list_selects=4 detail_expansion_selects="+detailSelects
                +" summary_bytes="+summaryJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                +" full_details_bytes="+mapper.writeValueAsString(details).getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        System.out.println("SUMMARY_PLAN "+jdbc.queryForList("explain analyze "+aggregateSql[0],aggregateArgs[0]));
        var next=reader.list("Summary Customer","",1,10);
        assertEquals(1,next.reservations().size());
        assertTrue(page.reservations().stream().noneMatch(s -> s.reservationId()==next.reservations().get(0).reservationId()));

        int target=ids.get(10), item=items.get(20);
        jdbc.update("delete from reservation_item_nightly_rate where reservation_item_id=? and occupied_date=?",item,in);
        assertNull(reader.list(""+target,"",0,10).reservations().get(0).totalPrice());
        repository.night(item,out,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.TEN);
        assertNull(reader.list(""+target,"",0,10).reservations().get(0).totalPrice());
        jdbc.update("update reservation set status='CANCELLED' where reservation_id=?",target);
        assertFalse(reader.list(""+target,"CANCELLED",0,10).reservations().get(0).hasOperationalWarnings());
        before=selects();
        assertTrue(reader.list("missing customer","",0,10).reservations().isEmpty());
        assertEquals(2,selects()-before);
        jdbc.update("delete from reservation_item_nightly_rate where reservation_item_id=?",item);
        assertNull(reader.list(""+target,"",0,10).reservations().get(0).totalPrice());
    }
}
