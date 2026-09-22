package com.gucardev.jackson.convert;

import java.util.Locale;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

// Same Locale.ROOT rule as UppercaseSerializer - normalizing on the way IN too, so
// "save10", "SAVE10" and "SaVe10" all become the one canonical "SAVE10" before the
// record is even constructed, regardless of the server's own default locale.
public class UppercaseDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        return p.getString().toUpperCase(Locale.ROOT);
    }
}
