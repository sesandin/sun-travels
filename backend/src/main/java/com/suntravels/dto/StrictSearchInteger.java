package com.suntravels.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Search-only strictness: never truncate fractional adults/IDs or coerce strings.
 */
public class StrictSearchInteger extends ValueDeserializer<Integer> {
    // Reads a JSON integer without accepting strings, fractions, or overflow.
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
            return context.reportInputMismatch(Integer.class, "Use a JSON whole number");
        return parser.getIntValue();
    }
}
