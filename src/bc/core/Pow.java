package bc.core;

import bc.hash.HashUtil;
import bc.model.BlockHeader;

public final class Pow {
    private Pow() {}

    public record Result(BlockHeader header, String hash, long attempts, long nanos) {}

    public static boolean isValid(BlockHeader h) {
        return h.difficulty() >= 0 && HashUtil.hasLeadingZeros(h.hash(), h.difficulty());
    }
    public static Result mine(BlockHeader template) {
        if (template.difficulty() < 0 || template.difficulty() > HashUtil.HEX_LENGTH)
            throw new IllegalArgumentException("bad difficulty " + template.difficulty());
        long start = System.nanoTime();
        long nonce = template.nonce();
        long attempts = 0;
        while (true) {
            BlockHeader candidate = template.withNonce(nonce);
            String hash = candidate.hash();
            attempts++;
            if (HashUtil.hasLeadingZeros(hash, candidate.difficulty())) {
                return new Result(candidate, hash, attempts, System.nanoTime() - start);
            }
            if (nonce == Long.MAX_VALUE) throw new IllegalStateException("nonce space exhausted");
            nonce++;
        }
    }
}
