package bc;

import bc.hash.HashAccess;
import bc.hash.HashUtil;

import java.util.Random;

/** v0.3 pakeitimų lygiavertiškumo testai: optimizuotas kodas turi duoti lygiai tuos pačius rezultatus. */
final class V03Tests {
    private V03Tests() {}

    static void all() {
        T.test("S1: toHex lentele = String.format(\"%08x\") 1 000 000 atsitiktiniu zodziu", V03Tests::toHexEquivalent);
    }

    static void toHexEquivalent() {
        Random r = new Random(2026);
        int[] w = new int[8];
        int[] edge = {0, 1, 0xF, 0x10, 0xFFFF, 0x7FFFFFFF, 0x80000000, 0xFFFFFFFF};
        T.eq(reference(edge), HashAccess.toHex(edge), "krastiniai atvejai");
        for (int i = 0; i < 125_000; i++) {
            for (int j = 0; j < 8; j++) w[j] = r.nextInt();
            String fast = HashAccess.toHex(w);
            if (!fast.equals(reference(w))) throw new AssertionError("skiriasi: " + fast + " vs " + reference(w));
        }
        T.check(HashUtil.isHash(HashAccess.toHex(w)), "64 hex");
    }

    /** Senoji (v0.2) realizacija – etalonas. */
    static String reference(int[] h) {
        StringBuilder sb = new StringBuilder();
        for (int x : h) sb.append(String.format("%08x", x));
        return sb.toString();
    }
}
