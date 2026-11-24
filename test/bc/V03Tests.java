package bc;

import bc.core.Pow;
import bc.hash.Hash;
import bc.hash.HashAccess;
import bc.hash.HashUtil;
import bc.hash.PrefixHasher;
import bc.model.BlockHeader;

import java.util.Random;

/** v0.3 pakeitimų lygiavertiškumo testai: optimizuotas kodas turi duoti lygiai tuos pačius rezultatus. */
final class V03Tests {
    private V03Tests() {}

    static void all() {
        T.test("S1: toHex lentele = String.format(\"%08x\") 1 000 000 atsitiktiniu zodziu", V03Tests::toHexEquivalent);
        T.test("S2: PrefixHasher (midstate) = pilna maisa, 0..200 B prefiksai ir 0..80 B priesagos", V03Tests::midstateEquivalent);
        T.test("S2: leadingHexZeros = nuliu skaicius hex eiluteje", V03Tests::leadingZerosEquivalent);
        T.test("S4: netinkamo formato antraste / null blokas atmetami su priezastimi, ne NPE", V03Tests::malformedBlocks);
        T.test("S4: pool.submit(null) -> BAD_FORMAT, atmestuju sarase null nera", V03Tests::nullTx);
        T.test("S2: Pow.search = v0.2 algoritmas (nonce, bandymai, nextNonce; skaitmenu ribos, neigiami)", V03Tests::searchEquivalent);
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

    static void malformedBlocks() throws Exception {
        bc.core.Blockchain chain = new bc.core.Blockchain(bc.core.State.fromUsers(java.util.List.of(
                T.user("A", T.A, 100), T.user("B", T.B, 0))), 1);
        java.util.List<bc.model.Transaction> txs = java.util.List.of(bc.model.Transaction.create(T.A, T.B, 10, 0));
        BlockHeader g = bc.core.Miner.mine(chain, txs, 0).block().header();
        java.util.Map<String, Long> before = chain.state().snapshot();
        String[] bad = {null, "", "ABC", g.prevHash().toUpperCase().replace('0', 'G'), g.prevHash() + "0"};
        for (String v : bad) {
            T.expectReject(chain, new bc.model.Block(new BlockHeader(v, 0, 2, g.txRoot(), g.nonce(), 1), txs), bc.core.BlockError.BAD_FORMAT, null);
            T.expectReject(chain, new bc.model.Block(new BlockHeader(g.prevHash(), 0, 2, v, g.nonce(), 1), txs), bc.core.BlockError.BAD_FORMAT, null);
        }
        T.expectReject(chain, null, bc.core.BlockError.BAD_FORMAT, null);
        try {
            chain.validate(new bc.model.Block(null, txs));
            throw new AssertionError("turejo buti atmesta");
        } catch (bc.core.InvalidBlockException e) {
            T.eq(bc.core.BlockError.BAD_FORMAT, e.error(), "be antrastes");
        }
        T.eq(before, chain.state().snapshot(), "busena nepakito");
        T.eq(0, chain.height(), "aukstis nepakito");
    }

    static void nullTx() {
        bc.core.Blockchain chain = new bc.core.Blockchain(bc.core.State.fromUsers(java.util.List.of(
                T.user("A", T.A, 100), T.user("B", T.B, 0))), 1);
        bc.core.TxPool pool = new bc.core.TxPool();
        T.eq(bc.core.TxError.BAD_FORMAT, pool.submit(null, chain), "priezastis");
        T.eq(0, pool.rejected().size(), "null neirasytas");
        T.eq(0, pool.size(), "fondas tuscias");
    }

    static void midstateEquivalent() {
        Random r = new Random(7);
        for (int prefixLen = 0; prefixLen <= 200; prefixLen++) {
            byte[] prefix = new byte[prefixLen];
            r.nextBytes(prefix);
            PrefixHasher ph = new PrefixHasher(prefix);
            for (int k = 0; k < 40; k++) {
                byte[] suffix = new byte[r.nextInt(81)];
                r.nextBytes(suffix);
                byte[] all = new byte[prefixLen + suffix.length];
                System.arraycopy(prefix, 0, all, 0, prefixLen);
                System.arraycopy(suffix, 0, all, prefixLen, suffix.length);
                String fast = Hash.toHexString(ph.hashWords(suffix, suffix.length));
                if (!fast.equals(HashUtil.hashBytes(all)))
                    throw new AssertionError("skiriasi: prefix=" + prefixLen + " suffix=" + suffix.length);
            }
        }
    }

    static void leadingZerosEquivalent() {
        Random r = new Random(9);
        int[] w = new int[8];
        for (int i = 0; i < 200_000; i++) {
            for (int j = 0; j < 8; j++) w[j] = r.nextInt() >>> r.nextInt(32); // daug pradinių nulių
            if (r.nextInt(10) == 0) w[0] = 0;
            String hex = HashAccess.toHex(w);
            int expected = 0;
            while (expected < 64 && hex.charAt(expected) == '0') expected++;
            T.eq(expected, Hash.leadingHexZeros(w), hex);
        }
        T.eq(64, Hash.leadingHexZeros(new int[8]), "visi nuliai");
    }

    /** v0.2 Pow.search (etalonas): kiekvienam nonce nauja antraštė, pilna maiša, hex eilutė. */
    static Pow.Search reference(BlockHeader t, long start, long max) {
        long nonce = start, attempts = 0;
        while (attempts < max) {
            BlockHeader c = t.withNonce(nonce);
            attempts++;
            if (HashUtil.hasLeadingZeros(c.hash(), t.difficulty())) return new Pow.Search(c, attempts, 0, nonce + 1);
            nonce++;
        }
        return new Pow.Search(null, attempts, 0, nonce);
    }

    static void searchEquivalent() {
        String root = HashUtil.hashUtf8("root");
        long[] starts = {0, 5, 95, 990, 9_990, 99_990, 999_999_990L, 9_999_999_990L, 999_999_999_999_999_990L, -40, -5};
        int cases = 0;
        for (int ts = 0; ts < 6; ts++) {
            for (int diff = 1; diff <= 3; diff++) {
                // skirtingi timestamp ilgiai -> prefiksas skirtingai pasislenka bloko ribos atžvilgiu
                BlockHeader t = new BlockHeader(HashUtil.hashUtf8("p" + ts), (long) Math.pow(10, ts * 2), 2, root, 0, diff);
                for (long start : starts) {
                    for (long max : new long[]{1, 7, 300, 20_000}) {
                        Pow.Search a = Pow.search(t, start, max, Long.MAX_VALUE), b = reference(t, start, max);
                        T.eq(b.solution(), a.solution(), "sprendimas");
                        T.eq(b.attempts(), a.attempts(), "bandymai");
                        T.eq(b.nextNonce(), a.nextNonce(), "nextNonce");
                        cases++;
                    }
                }
            }
        }
        T.check(cases == 6 * 3 * 11 * 4, "atvejai");
    }

    /** Senoji (v0.2) realizacija – etalonas. */
    static String reference(int[] h) {
        StringBuilder sb = new StringBuilder();
        for (int x : h) sb.append(String.format("%08x", x));
        return sb.toString();
    }
}
