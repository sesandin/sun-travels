package com.suntravels.dto;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class StrictSearchDate extends ValueDeserializer<LocalDate> {
    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            String value = parser.getString();
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException ex) { /* report below */ }
        }
        return context.reportInputMismatch(LocalDate.class, "Use a valid YYYY-MM-DD calendar date");
    }
}
