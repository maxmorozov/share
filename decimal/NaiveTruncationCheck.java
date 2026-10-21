// Measures how often naive 64-bit truncation variants of Decimal.toDouble() disagree with Double.parseDouble.
// Needs Decimal64.java and Decimal.java in the same package.
import java.util.*;
public class NaiveTruncationCheck {
    // W = hi:lo; returns {w, s, sticky}
    static double naiveTruncate(long hi, long lo)  { int s = 64 - Long.numberOfLeadingZeros(hi); long w = (hi << (64 - s)) | (lo >>> s);
        return Math.scalb(Decimal64.toDoubleEiselLemire(w, -9), s); }
    static double roundToOdd(long hi, long lo)     { int s = 64 - Long.numberOfLeadingZeros(hi); long w = (hi << (64 - s)) | (lo >>> s);
        if ((lo & ((1L << s) - 1)) != 0) w |= 1; return Math.scalb(Decimal64.toDoubleEiselLemire(w, -9), s); }
    static double roundNearest64(long hi, long lo) { int s = 64 - Long.numberOfLeadingZeros(hi); long w = (hi << (64 - s)) | (lo >>> s);
        long rem = lo & ((1L << s) - 1); long half = 1L << (s - 1);
        if (Long.compareUnsigned(rem, half) > 0 || (rem == half && (w & 1) != 0)) w++;   // (w overflow ignored: negligible here)
        return Math.scalb(Decimal64.toDoubleEiselLemire(w, -9), s); }

    public static void main(String[] a) {
        Random r = new Random(5);
        long n = 0, badT = 0, badO = 0, badN = 0, ties = 0, badTt = 0, badOt = 0, badNt = 0;
        String exT = null;
        for (int i = 0; i < 4_000_000; i++) {
            long units = (1L << 34) | (r.nextLong() >>> 1) >>> r.nextInt(29);   // units >= 2^34 -> W > 2^64
            if (units < 18_446_744_074L) continue;
            int nanos = r.nextInt(1_000_000_000);
            Decimal d = new Decimal(false, units, nanos);
            double ref = Double.parseDouble(units + "." + String.format("%09d", nanos));
            long hi = Math.multiplyHigh(units, 1_000_000_000L), lo = units * 1_000_000_000L;
            long lo2 = lo + nanos; if (Long.compareUnsigned(lo2, lo) < 0) hi++; lo = lo2;
            n++;
            if (naiveTruncate(hi, lo) != ref) { badT++; if (exT == null) exT = units + "." + String.format("%09d", nanos) + " ref=" + ref + " naive=" + naiveTruncate(hi, lo); }
            if (roundToOdd(hi, lo) != ref) badO++;
            if (roundNearest64(hi, lo) != ref) badN++;
        }
        // exact double ties on integers (units >= 2^53, nanos = 0)
        for (int i = 0; i < 1_000_000; i++) {
            int k = 1 + r.nextInt(10); long m = (1L << 52) | (r.nextLong() >>> 12);
            long units = (m << k) | (1L << (k - 1));
            double ref = Double.parseDouble(units + ".000000000");
            long hi = Math.multiplyHigh(units, 1_000_000_000L), lo = units * 1_000_000_000L;
            ties++;
            if (naiveTruncate(hi, lo) != ref) badTt++;
            if (roundToOdd(hi, lo) != ref) badOt++;
            if (roundNearest64(hi, lo) != ref) badNt++;
        }
        System.out.printf("random, W>2^64 (n=%d): truncate wrong=%d (%.4f%%)  round-to-odd wrong=%d  round-nearest-64 wrong=%d%n", n, badT, 100.0*badT/n, badO, badN);
        System.out.printf("exact integer ties (n=%d): truncate wrong=%d  round-to-odd wrong=%d  round-nearest-64 wrong=%d%n", ties, badTt, badOt, badNt);
        System.out.println("example: " + exT);
    }
}
