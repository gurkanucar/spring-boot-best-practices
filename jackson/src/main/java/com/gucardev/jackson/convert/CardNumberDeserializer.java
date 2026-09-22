package com.gucardev.jackson.convert;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

// Runs on the way IN: accepts spaces or dashes ("4111 1111 1111 1111", "4111-1111-1111-1111")
// and normalizes to a plain digit string before the record is even constructed.
public class CardNumberDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        return p.getString().replaceAll("[\\s-]", "");
    }
}
