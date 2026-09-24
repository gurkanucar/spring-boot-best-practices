package com.gucardev.dtofieldmasker.masking;

import com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Writes a String property masked according to its {@link MaskData} annotation.
 *
 * <p>Jackson creates one shared instance through the no-arg constructor (from
 * {@code @JsonSerialize(using = ...)}), then calls {@link #createContextual} once per annotated
 * property. That is where the annotation's settings are read and a configured copy is returned;
 * Jackson caches that copy, so the annotation is not re-read per request.
 */
public class MaskDataSerializer extends ValueSerializer<Object> {

    private final int count;
    private final String replaceChar;
    private final MaskingOption option;

    /** Required by Jackson; the unconfigured instance is only a template for createContextual. */
    public MaskDataSerializer() {
        this(MaskData.DEFAULT_VALUE, MaskData.DEFAULT_REPLACE_CHAR, MaskingOption.LAST_X_CHARS_CLEAR);
    }

    private MaskDataSerializer(int count, String replaceChar, MaskingOption option) {
        this.count = count;
        this.replaceChar = replaceChar;
        this.option = option;
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        if (property == null) {
            return this;
        }
        MaskData annotation = property.getAnnotation(MaskData.class);
        if (annotation == null) {
            return this;
        }
        // Misconfiguration fails on the first serialization with a message naming the property,
        // instead of masking wrongly or throwing a ClassCastException later.
        if (!property.getType().isTypeOrSubTypeOf(CharSequence.class)) {
            return ctxt.reportBadDefinition(property.getType(),
                    "@MaskData supports String properties only, but '%s' is of type %s"
                            .formatted(property.getName(), property.getType()));
        }
        if (annotation.value() < 0) {
            return ctxt.reportBadDefinition(property.getType(),
                    "@MaskData.value must not be negative on '%s': %d"
                            .formatted(property.getName(), annotation.value()));
        }
        return new MaskDataSerializer(annotation.value(), annotation.replaceChar(), annotation.maskingOption());
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        gen.writeString(Masker.mask((CharSequence) value, count, replaceChar, option));
    }
}
