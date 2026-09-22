package com.gucardev.jackson.convert;

import com.gucardev.jackson.dto.Money;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class MoneySerializer extends ValueSerializer<Money> {

    @Override
    public void serialize(Money value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        gen.writeString(value.amount().toPlainString() + " " + value.currency());
    }
}
