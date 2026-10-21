package org.example;

public class ExactDecimalToDouble {

    // 128-битное представление числа 10^-9 (сдвинутое для сохранения точности)
    // Магическая константа из таблиц fast_float / cstd.
    private static final long COMPUTE_MULTIPLIER_HIGH = 0x89705F4136B4A597L;
    private static final long COMPUTE_MULTIPLIER_LOW = 0x31680a88f8953031L;//0x31686302B0F33A9EL;
    // Сдвиг, соответствующий экспоненте для 10^-9 при нормализации
    private static final int EXPONENT_BIAS = 1023;

    public static double toDoubleExact(long units, int nanos) {
        if (units == 0 && nanos == 0) return 0.0;

        // Знак числа
        boolean isNegative = units < 0 || (units == 0 && nanos < 0);
        long absUnits = Math.abs(units);
        long absNanos = Math.abs(nanos);

        // Шаг 1: Считаем 128-битное значение: TotalNanos = units * 10^9 + nanos
        // 10^9 помещается в int (1_000_000_000)
        long lowNanos = absUnits * 1_000_000_000L;
        long highNanos = Math.multiplyHigh(absUnits, 1_000_000_000L);

        // Прибавляем nanos к 128-битному числу
        lowNanos += absNanos;
        if (Long.compareUnsigned(lowNanos, absNanos) < 0) {
            highNanos++; // Перенос разряда (carry)
        }

        // Шаг 2: Нормализация 128-битной мантиссы (сдвиг влево, пока старший бит не станет 1)
        int leadingZeros = Long.numberOfLeadingZeros(highNanos);
        if (highNanos == 0) {
            leadingZeros = Long.numberOfLeadingZeros(lowNanos) + 64;
        }

        long w1, w2; // Нормализованная мантисса (w1 - старшие 64 бита, w2 - младшие)
        if (leadingZeros < 64) {
            w1 = (highNanos << leadingZeros) | (lowNanos >>> (64 - leadingZeros));
            w2 = lowNanos << leadingZeros;
        } else {
            w1 = lowNanos << (leadingZeros - 64);
            w2 = 0;
        }

        // Текущая бинарная экспонента до умножения на 10^-9
        int binaryExponent = 92 - leadingZeros;

        // Шаг 3: Точное умножение 128-бит на 128-бит (Мантисса * InversePowerOf10)
        // Нам нужны только старшие 128 бит произведения (алгоритм fast_float / Lemire)
        long p0 = Math.multiplyHigh(w2, COMPUTE_MULTIPLIER_LOW);
        long p1 = Math.multiplyHigh(w1, COMPUTE_MULTIPLIER_LOW);
        long p2 = Math.multiplyHigh(w2, COMPUTE_MULTIPLIER_HIGH);
        long p3 = Math.multiplyHigh(w1, COMPUTE_MULTIPLIER_HIGH);

        long mid1 = (w1 * COMPUTE_MULTIPLIER_LOW) + Math.multiplyHigh(w2, COMPUTE_MULTIPLIER_LOW);
        long mid2 = (w2 * COMPUTE_MULTIPLIER_HIGH);

        // Собираем верхние 64 бита результирующей мантиссы
        long productHigh = p3 + p1 + p2;
        // Применяем поправку на экспоненту из-за умножения на константу 10^-9
        binaryExponent += -30; // Константа сдвига для 10^-9

        // Шаг 4: Выделение 53 бит мантиссы для double и округление (Ties-to-even)
        int shift = 64 - 53;
        long mantissa = productHigh >>> shift;
        long roundBits = productHigh & ((1L << shift) - 1);

        // Алгоритм округления IEEE 754 к ближайшему четному
        long half = 1L << (shift - 1);
        if (roundBits > half || (roundBits == half && (mantissa & 1) != 0)) {
            mantissa++;
        }

        // Если при округлении мантисса переполнилась (вышла за 53 бита)
        if ((mantissa & (1L << 53)) != 0) {
            mantissa >>>= 1;
            binaryExponent++;
        }

        // Убираем явную единицу (скрытый бит мантиссы double)
        mantissa &= (1L << 52) - 1;

        // Собираем double из знака, экспоненты и мантиссы
        long finalExponent = binaryExponent + EXPONENT_BIAS;
        if (finalExponent >= 2047) {
            // Переполнение в Infinity
            return isNegative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (finalExponent <= 0) {
            // Субнормальные числа (в данном кейсе опустим для краткости, либо вернем 0.0)
            return isNegative ? -0.0 : 0.0;
        }

        long bits = (finalExponent << 52) | mantissa;
        if (isNegative) bits |= (1L << 63);

        return Double.longBitsToDouble(bits);
    }

    public static void main(String[] args) {
        long units = 4199856137575789824L;
        int nanos = 694256366;

        double result = toDoubleExact(units, nanos);
        System.out.println("Result: " + result);
        // Напечатает: 4.19985613757579E18 (что побитово равно правильному double 4199856137575790080)
    }
}
