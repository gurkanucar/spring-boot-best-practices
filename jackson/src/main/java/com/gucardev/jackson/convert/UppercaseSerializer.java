package com.gucardev.jackson.convert;

import java.util.Locale;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

// Locale.ROOT, never the JVM default: String.toUpperCase() with the platform's default
// locale gives a DIFFERENT result under a Turkish locale specifically - "istanbul"
// becomes "ISTANBUL" under Locale.ROOT/en, but "İSTANBUL" (dotted capital I) under
// Locale("tr", "TR"), because Turkish has separate dotted/dotless I letters. A server
// running with -Duser.language=tr would silently produce different codes than one
// running in the default locale for the exact same input, which is the kind of bug that
// only shows up in one deployment environment.
public class UppercaseSerializer extends ValueSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        gen.writeString(value.toUpperCase(Locale.ROOT));
    }
}
