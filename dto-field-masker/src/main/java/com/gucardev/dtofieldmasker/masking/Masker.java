package com.gucardev.dtofieldmasker.masking;

import com.gucardev.dtofieldmasker.masking.MaskData.MaskingOption;

/**
 * The masking algorithm itself, with no dependency on Jackson so it can be unit-tested (and
 * reused, for example for log statements) on its own.
 *
 * <p>Works on Unicode code points, not {@code char}s, so an emoji or other supplementary
 * character counts as one character instead of being split in half.
 *
 * <p>Fail-closed rule: if a {@code *_CLEAR} option would leave the whole value readable (the
 * value is not longer than the number of clear characters), the whole value is masked instead.
 * A masker that reveals a short value in full when misconfigured is worse than one that hides it.
 */
public final class Masker {

    private Masker() {
    }

    public static String mask(CharSequence input, int count, String replacement, MaskingOption option) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        int[] codePoints = input.codePoints().toArray();
        int length = codePoints.length;
        if (length == 0) {
            return "";
        }

        boolean maskEverything = count >= length;
        // Masked range is [from, to) in code point positions.
        int from;
        int to;
        if (maskEverything) {
            from = 0;
            to = length;
        } else {
            switch (option) {
                case FIRST_X_CHARS_MASKED -> {
                    from = 0;
                    to = count;
                }
                case LAST_X_CHARS_MASKED -> {
                    from = length - count;
                    to = length;
                }
                case FIRST_X_CHARS_CLEAR -> {
                    from = count;
                    to = length;
                }
                case LAST_X_CHARS_CLEAR -> {
                    from = 0;
                    to = length - count;
                }
                default -> throw new IllegalStateException("Unhandled option: " + option);
            }
        }

        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            if (i >= from && i < to) {
                result.append(replacement);
            } else {
                result.appendCodePoint(codePoints[i]);
            }
        }
        return result.toString();
    }
}
