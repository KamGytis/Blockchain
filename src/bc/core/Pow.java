package bc.core;

import bc.hash.HashUtil;
import bc.model.BlockHeader;

public final class Pow {
    private Pow() {}

    public record Result(BlockHeader header, String hash, long attempts, long nanos) {}

    /** Ribotos paieškos rezultatas: solution == null, jei per biudžetą sprendimo nerasta. */
    public record Search(BlockHeader solution, long attempts, long nanos, long nextNonce) {}

    /** Ar antraštė tenkina JOS PAČIOS deklaruotą sudėtingumą (maiša perskaičiuojama). */
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
        long nonce = startNonce;
        long attempts = 0;
        while (attempts < maxAttempts) {
            BlockHeader candidate = template.withNonce(nonce);
            attempts++;
            if (HashUtil.hasLeadingZeros(candidate.hash(), d)) {
                return new Search(candidate, attempts, System.nanoTime() - start, nonce + 1);
            }
            if (nonce == Long.MAX_VALUE) throw new IllegalStateException("nonce space exhausted");
            nonce++;
            if ((attempts & 255) == 0 && System.nanoTime() >= deadlineNanos) break;
        }
        return new Search(null, attempts, System.nanoTime() - start, nonce);
    }

    /** Neribota paieška nuo template.nonce() (v0.1 elgsena). */
    public static Result mine(BlockHeader template) {
        Search s = search(template, template.nonce(), Long.MAX_VALUE, Long.MAX_VALUE);
        return new Result(s.solution(), s.solution().hash(), s.attempts(), s.nanos());
    }
}
