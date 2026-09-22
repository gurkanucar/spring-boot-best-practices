package com.gucardev.jackson.convert;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

// Unlike CardNumberSerializer (which keeps the LAST four digits, because that's the
// part people use to recognize their own card), this keeps only the FIRST few
// characters visible - the shape you want for something like an API key, where the
// prefix is what a human uses to recognize which key it is, and the rest must never be
// shown again after the one time it was issued.
public class PartialMaskSerializer extends ValueSerializer<String> {

    private static final int VISIBLE_PREFIX_LENGTH = 3;

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        if (value.length() <= VISIBLE_PREFIX_LENGTH) {
            gen.writeString("*".repeat(value.length()));
            return;
        }
        String visible = value.substring(0, VISIBLE_PREFIX_LENGTH);
        String masked = "*".repeat(value.length() - VISIBLE_PREFIX_LENGTH);
        gen.writeString(visible + masked);
    }
}
