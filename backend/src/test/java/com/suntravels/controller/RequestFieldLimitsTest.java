package com.suntravels.controller;

import com.suntravels.dto.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class RequestFieldLimitsTest
{
    static final jakarta.validation.ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    static final Validator validator = factory.getValidator();
    @AfterAll static void close() { factory.close(); }

    @ParameterizedTest
    @CsvSource({"HotelRequest,name,150", "HotelRequest,address,255", "HotelRequest,city,100",
            "HotelRequest,country,100", "HotelRequest,phone,20", "HotelRequest,email,100",
            "HotelUpdateRequest,name,150", "HotelUpdateRequest,address,255", "HotelUpdateRequest,city,100",
            "HotelUpdateRequest,country,100", "HotelUpdateRequest,phone,20", "HotelUpdateRequest,email,100",
            "RoomTypeRequest,name,100", "RoomTypeRequest,description,255",
            "CustomerRequest,name,150", "CustomerRequest,phone,20", "CustomerRequest,email,150",
            "LoginRequest,username,100", "LoginRequest,password,200"})
    void stringSizeBoundary(String type, String field, int limit) throws Exception
    {
        Class<?> dto = Class.forName("com.suntravels.dto." + type);
        // Isolate size from independent email/country/phone format constraints.
        assertTrue(validator.validateValue(dto, field, "a".repeat(limit)).stream()
                .noneMatch(v -> v.getConstraintDescriptor().getAnnotation() instanceof Size));
        assertTrue(validator.validateValue(dto, field, "a".repeat(limit + 1)).stream()
                .anyMatch(v -> v.getConstraintDescriptor().getAnnotation() instanceof Size));
    }

    @ParameterizedTest
    @CsvSource({"0.00,true", "999.99,true", "1000,false", "0.001,false", "-1,false"})
    void markupPrecision(String value, boolean valid)
    {
        assertEquals(valid, validator.validate(new ContractMarkupRequest(new BigDecimal(value))).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0.01,true", "99999999.99,true", "100000000,false", "0.001,false", "0,false", "-1,false"})
    void ratePrecision(String value, boolean valid)
    {
        BigDecimal rate = new BigDecimal(value);
        assertEquals(valid, validator.validate(new OfferingRateRequest(rate)).isEmpty());
        assertEquals(valid, validator.validate(new SeasonalRateRequest(LocalDate.of(2026,1,1),
                LocalDate.of(2026,1,2), rate)).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"HotelRequest,name", "HotelUpdateRequest,name", "HotelRequest,address", "HotelRequest,city",
            "RoomTypeRequest,name", "CustomerRequest,name", "LoginRequest,username", "LoginRequest,password"})
    void requiredTextRejectsEmptyAndWhitespace(String type, String field) throws Exception
    {
        Class<?> dto = Class.forName("com.suntravels.dto." + type);
        assertFalse(validator.validateValue(dto, field, "").isEmpty());
        assertFalse(validator.validateValue(dto, field, "   ").isEmpty());
    }
}
