package bc.core;

import bc.hash.Hash;
import bc.hash.HashUtil;
import bc.hash.PrefixHasher;
import bc.model.BlockHeader;

import java.nio.charset.StandardCharsets;

public final class Pow {
    private Pow() {}

    public record Result(BlockHeader header, String hash, long attempts, long nanos) {}

    // Ribotos paieškos rezultatas: solution == null, jei per biudžetą sprendimo nerasta.
    public record Search(BlockHeader solution, long attempts, long nanos, long nextNonce) {}

    // Ar antraštė tenkina JOS PAČIOS deklaruotą sudėtingumą (maiša perskaičiuojama).
    public static boolean isValid(BlockHeader h) {
        return meets(h, h.difficulty());
    }

    /** Ar antraštės maiša (perskaičiuota) prasideda bent {@code required} nulių. */
    public static boolean meets(BlockHeader h, int required) {
        return required >= 0 && HashUtil.hasLeadingZeros(h.hash(), required);
    }

    /**
     * Tikrina nonce nuo {@code startNonce} iš eilės, kol randa sprendimą, išnaudoja {@code maxAttempts}
     * arba pasiekia {@code deadlineNanos} (System.nanoTime reikšmė; Long.MAX_VALUE – be laiko ribos).
     * Kiti antraštės laukai lieka fiksuoti. Grąžina nextNonce – pirmą dar netikrintą nonce.
     */
    public static Search search(BlockHeader template, long startNonce, long maxAttempts, long deadlineNanos) {
        int d = template.difficulty();
        if (d < 0 || d > HashUtil.HEX_LENGTH) throw new IllegalArgumentException("bad difficulty " + d);
        long start = System.nanoTime();
        // v0.3: pastovaus prefikso būsena skaičiuojama vieną kartą (midstate), kiekvienam bandymui
        // į baitų masyvą įrašomas tik "nonce|difficulty"; nuliai skaičiuojami žodžiuose, be hex eilutės.
        PrefixHasher hasher = new PrefixHasher(template.serializePrefix().getBytes(StandardCharsets.UTF_8));
        byte[] tail = ("|" + d).getBytes(StandardCharsets.UTF_8);
        byte[] suffix = new byte[20 + tail.length];
        long nonce = startNonce;
        long attempts = 0;
        while (attempts < maxAttempts) {
            int len = writeDecimal(nonce, suffix);
            System.arraycopy(tail, 0, suffix, len, tail.length);
            attempts++;
            if (Hash.leadingHexZeros(hasher.hashWords(suffix, len + tail.length)) >= d) {
                BlockHeader solution = template.withNonce(nonce);
                // saugiklis: sprendimas patikrinamas įprastu (lėtu) keliu
                if (!meets(solution, d)) throw new IllegalStateException("midstate ir pilna maisa nesutampa");
                return new Search(solution, attempts, System.nanoTime() - start, nonce + 1);
            }
            if (nonce == Long.MAX_VALUE) throw new IllegalStateException("nonce space exhausted");
            nonce++;
            if ((attempts & 255) == 0 && System.nanoTime() >= deadlineNanos) break;
        }
        return new Search(null, attempts, System.nanoTime() - start, nonce);
    }

    /** Dešimtainis užrašas, tapatus Long.toString(v), į buf (>= 20 B); grąžina ilgį. */
    static int writeDecimal(long v, byte[] buf) {
        if (v < 0) { // retas atvejis: naudojame standartinį užrašą
            byte[] b = Long.toString(v).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(b, 0, buf, 0, b.length);
            return b.length;
        }
        int len = 1;
        for (long x = v; x >= 10; x /= 10) len++;
        for (int i = len - 1; i >= 0; i--) {
            buf[i] = (byte) ('0' + (v % 10));
            v /= 10;
        }
        return len;
    }

    // Neribota paieška nuo template.nonce() (v0.1 elgsena).
    public static Result mine(BlockHeader template) {
        Search s = search(template, template.nonce(), Long.MAX_VALUE, Long.MAX_VALUE);
        return new Result(s.solution(), s.solution().hash(), s.attempts(), s.nanos());
    }
}
