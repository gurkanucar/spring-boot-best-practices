package com.gucardev.validation.constraint;

import java.lang.reflect.Method;

/**
 * Reads a named property from a bean, trying the record accessor first and
 * falling back to a JavaBean getter, so both records and POJOs work.
 */
final class PropertyReader {

    private PropertyReader() {
    }

    static Object read(Object bean, String property) {
        Method accessor = findAccessor(bean.getClass(), property);
        if (accessor == null) {
            throw new IllegalStateException(
                    "Property '%s' not found on %s".formatted(property, bean.getClass().getName()));
        }
        try {
            return accessor.invoke(bean);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Property '%s' could not be read: %s".formatted(property, bean.getClass().getName()), e);
        }
    }

    private static Method findAccessor(Class<?> type, String property) {
        String capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (String candidate : new String[] {property, "get" + capitalized, "is" + capitalized}) {
            try {
                return type.getMethod(candidate);
            } catch (NoSuchMethodException ignored) {
                // try the next candidate accessor name
            }
        }
        return null;
    }
}
