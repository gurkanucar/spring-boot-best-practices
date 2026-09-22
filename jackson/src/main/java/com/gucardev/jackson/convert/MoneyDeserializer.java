package com.gucardev.jackson.convert;

import com.gucardev.jackson.dto.Money;
import java.math.BigDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class MoneyDeserializer extends ValueDeserializer<Money> {

    @Override
    public Money deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        String[] parts = p.getString().trim().split("\\s+", 2);
        return new Money(new BigDecimal(parts[0]), parts[1]);
    }
}
