package org.example;

public final class DecimalTransport {

    // 128-битная мультипликативная инверсия для 10^-9 (из таблиц fast_float)
    private static final long MAGIC_HIGH = 0x89705F4136B4A597L;
    private static final long MAGIC_LOW = 0x31686302B0F33A9EL;
    private static final int EXP_BIAS = 1023;

    /**
     * Конвертирует финансовый decimal (units, nanos) в точный double.
     * Соответствует логике fast_float / Lemire для предотвращения double rounding error.
     */
    public static double toDouble(long units, int nanos) {
        if (units == 0 && nanos == 0) return 0.0;

        // Определяем знак на основе транспортного соглашения (обычно знак в units)
        boolean isNegative = units < 0 || nanos < 0;
        long absUnits = units == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(units);
        long absNanos = Math.abs((long) nanos);

        // 1. Сборка точной 128-битной мантиссы в наносекундах: (units * 10^9) + nanos
        long lowNanos = absUnits * 1_000_000_000L;
        long highNanos = Math.multiplyHigh(absUnits, 1_000_000_000L);

        lowNanos += absNanos;
        if (Long.compareUnsigned(lowNanos, absNanos) < 0) {
            highNanos++; // Перенос бита (carry)
        }

        // 2. Нормализация 128-битного целого числа
        int leadingZeros = Long.numberOfLeadingZeros(highNanos);
        if (highNanos == 0) {
            leadingZeros = Long.numberOfLeadingZeros(lowNanos) + 64;
        }

        long w1, w2;
        if (leadingZeros < 64) {
            w1 = (highNanos << leadingZeros) | (lowNanos >>> (64 - leadingZeros));
            w2 = lowNanos << leadingZeros;
        } else {
            w1 = lowNanos << (leadingZeros - 64);
            w2 = 0;
        }

        // Базовая бинарная экспонента для наносекунд
        int binaryExponent = 92 - leadingZeros;

        // 3. Умножение на 10^-9 в 128-битной сетке (берем старшие 128 бит произведения)
        long p1 = Math.multiplyHigh(w1, MAGIC_LOW);
        long p2 = Math.multiplyHigh(w2, MAGIC_HIGH);
        long p3 = Math.multiplyHigh(w1, MAGIC_HIGH);

        long productHigh = p3 + p1 + p2;
        binaryExponent += -30; // Корректировка экспоненты под масштаб константы 10^-9

        // 4. Округление мантиссы до 53 бит (IEEE 754 Round-to-nearest, ties-to-even)
        int shift = 64 - 53;
        long mantissa = productHigh >>> shift;
        long roundBits = productHigh & ((1L << shift) - 1);
        long half = 1L << (shift - 1);

        if (roundBits > half || (roundBits == half && (mantissa & 1) != 0)) {
            mantissa++;
        }

        // Если округление вызвало переполнение мантиссы
        if ((mantissa & (1L << 53)) != 0) {
            mantissa >>>= 1;
            binaryExponent++;
        }

        // Маскируем скрытый бит
        mantissa &= (1L << 52) - 1;

        // 5. Финальная сборка битов double
        long finalExponent = binaryExponent + EXP_BIAS;

        // Защита от переполнения диапазона double (для финансов маловероятно, но нужно для транспорта)
        if (finalExponent >= 2047) {
            return isNegative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (finalExponent <= 0) {
            return isNegative ? -0.0 : 0.0; // Защита от субнормального андерфлоу
        }

        long bits = (finalExponent << 52) | mantissa;
        if (isNegative) {
            bits |= (1L << 63);
        }

        return Double.longBitsToDouble(bits);
    }
}
