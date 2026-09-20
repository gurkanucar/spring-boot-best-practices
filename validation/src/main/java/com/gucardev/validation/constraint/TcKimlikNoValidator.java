package com.gucardev.validation.constraint;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TcKimlikNoValidator implements ConstraintValidator<TcKimlikNo, String> {

    private static final int LENGTH = 11;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value.length() != LENGTH) {
            return false;
        }
        int[] digits = new int[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            digits[i] = c - '0';
        }
        if (digits[0] == 0) {
            return false;
        }
        // 10th digit: (sum of odd-position digits * 7 - sum of even-position digits) mod 10
        int oddSum = digits[0] + digits[2] + digits[4] + digits[6] + digits[8];
        int evenSum = digits[1] + digits[3] + digits[5] + digits[7];
        int tenthDigit = Math.floorMod(oddSum * 7 - evenSum, 10);
        if (tenthDigit != digits[9]) {
            return false;
        }
        // 11th digit: sum of the first 10 digits mod 10
        int total = 0;
        for (int i = 0; i < 10; i++) {
            total += digits[i];
        }
        return total % 10 == digits[10];
    }
}
