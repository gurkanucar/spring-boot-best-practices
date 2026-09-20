package com.gucardev.restapidesign.common;

/** Formats and parses the strong ETag this project uses: the resource's version number, quoted. */
public final class ETagSupport {

    private ETagSupport() {
    }

    public static String format(long version) {
        return "\"" + version + "\"";
    }

    public static long parse(String ifMatchHeaderValue) {
        String trimmed = ifMatchHeaderValue.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformed If-Match header: " + ifMatchHeaderValue);
        }
    }
}
