package org.example;

import java.math.BigInteger;

public class NumberFormatter {

    // Эквивалент функции format(number) в Python
    public static void format(BigInteger number) {
        BigInteger shift64 = BigInteger.ONE.shiftLeft(64);

        BigInteger upper = number.divide(shift64);
        BigInteger lower = number.mod(shift64);

        // Превращаем в hex-строки (без префикса 0x)
        String upperHex = "0x" + upper.toString(16);
        String lowerHex = "0x" + lower.toString(16);

        System.out.println(upperHex + "," + lowerHex + ",");
    }

    public static void main(String[] args) {
        BigInteger shift127 = BigInteger.ONE.shiftLeft(127);
        BigInteger shift128 = BigInteger.ONE.shiftLeft(128);

        // Первый цикл: range(-342, 0)
        for (int q = -342; q < 0; q++) {
            BigInteger power5 = BigInteger.valueOf(5).pow(-q);
            int z = 0;

            while (BigInteger.ONE.shiftLeft(z).compareTo(power5) < 0) {
                z++;
            }

            BigInteger c;
            if (q >= -27) {
                int b = z + 127;
                c = BigInteger.ONE.shiftLeft(b).divide(power5).add(BigInteger.ONE);
                format(c);
            } else {
                int b = 2 * z + 128; // 2 * 64 = 128
                c = BigInteger.ONE.shiftLeft(b).divide(power5).add(BigInteger.ONE);

                // truncate
                while (c.compareTo(shift128) >= 0) {
                    c = c.divide(BigInteger.TWO);
                }
                format(c);
            }
        }

        // Второй цикл: range(0, 308 + 1)
        for (int q = 0; q <= 308; q++) {
            BigInteger power5 = BigInteger.valueOf(5).pow(q);

            // move the most significant bit in position
            while (power5.compareTo(shift127) < 0) {
                power5 = power5.multiply(BigInteger.TWO);
            }

            // *truncate*
            while (power5.compareTo(shift128) >= 0) {
                power5 = power5.divide(BigInteger.TWO);
            }
            format(power5);
        }
    }
}
