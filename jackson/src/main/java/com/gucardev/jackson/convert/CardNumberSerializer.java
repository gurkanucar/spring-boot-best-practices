package com.gucardev.jackson.convert;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

// Runs on the way OUT: masks every digit except the last four, so a raw number is never
// echoed back in full even if the caller sent it that way.
public class CardNumberSerializer extends ValueSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        String digits = value.replaceAll("\\D", "");
        String lastFour = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        gen.writeString("**** **** **** " + lastFour);
    }
}
