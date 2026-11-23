package bc;

import bc.core.Blockchain;
import bc.core.InvalidBlockException;
import bc.core.Miner;
import bc.core.Pow;
import bc.core.State;
import bc.core.TxError;
import bc.core.TxPool;
import bc.core.Merkle;
import bc.hash.HashUtil;
import bc.model.Block;
import bc.model.BlockHeader;
import bc.model.Transaction;
import bc.model.User;
import bc.sim.Config;
import bc.sim.Generator;
import bc.sim.Simulation;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** v0.1 testai (išlaikomas veikimas). Paleidžiami per {@link Tests}. */
final class V01Tests {

    // Užfiksuoti maišos vektoriai (KGHash v2). Pakeitus maišą, jie turi būti sąmoningai atnaujinti.
    static final String H_EMPTY = "e209e47debe9cdafc922ad38f2bdb0105fa7a00a7130f088c4b797095a2116f2";
    static final String H_ABC = "699b56920847030b53e5f67a423b8add976670faef02625dd72a9f9e8785c111";
    static final String H_EXAMPLE = "7437bf5156dfb2e4b65440ad4c2f6b19e6fdd1670e14adfbe70883337ef5d363";
    static final String H_FIXED_HEADER = "2da8fbc2b786bc949f3ab6f00ea6367dd0a4dfb9d671d2bc07dc40f0703f18c9";

    static final String A = HashUtil.hashUtf8("A"), B = HashUtil.hashUtf8("B"), C = HashUtil.hashUtf8("C");

    static void all() {
        T.test("maisos formatas: 64 mazosios hex, pradiniai nuliai islaikomi", V01Tests::hashFormat);
        T.test("maisos determinizmas ir uzfiksuoti vektoriai", V01Tests::hashVectors);
        T.test("serializacija sender|receiver|amount|sequence", V01Tests::serialization);
        T.test("tas pats seed -> tos pacios tx maisos", V01Tests::seedDeterminism);
        T.test("fiksuota antraste -> tas pats bloko hash", V01Tests::fixedHeader);
        T.test("PoW tikrinimas perskaiciuoja maisa", V01Tests::powRecomputes);
        T.test("mazas rankinis atvejis (3 vartotojai, 6 mokejimai)", V01Tests::handCase);
        T.test("negalima isleisti daugiau nei turi keliais mokejimais viename bloke", V01Tests::doubleSpendSameBlock);
        T.test("dubliuotos tx atmetamos (fondas, blokas, jau patvirtintos)", V01Tests::duplicates);
        T.test("netinkami blokai atmetami (prev_hash, tx_root, difficulty, tuscias)", V01Tests::badBlocks);
        T.test("visa simuliacija: invariantai ir atkuriamumas su --fixed-time", V01Tests::fullSimulation);
    }

    // ---------- testai ----------

    static void hashFormat() {
        Random r = new Random(1);
        boolean sawLeadingZero = false;
        for (int i = 0; i < 5000; i++) {
            byte[] data = new byte[r.nextInt(200)];
            r.nextBytes(data);
            String h = HashUtil.hashBytes(data);
            T.check(HashUtil.isHash(h), "ne 64 hex: " + h);
            if (h.charAt(0) == '0') sawLeadingZero = true;
        }
        T.check(sawLeadingZero, "nerasta maisa su pradiniu nuliu (turi buti ~1/16)");
    }

    static void hashVectors() {
        T.eq(H_EMPTY, HashUtil.hashUtf8(""), "hash(\"\")");
        T.eq(H_ABC, HashUtil.hashUtf8("abc"), "hash(\"abc\")");
        T.eq(H_EXAMPLE, HashUtil.hashUtf8("a12f|b34e|100|5"), "hash(pavyzdys)");
        T.eq(HashUtil.hashUtf8("lietuva ąčęėįšųūž"), HashUtil.hashUtf8("lietuva ąčęėįšųūž"), "pakartojimas");
        T.check(!HashUtil.hashUtf8("abc").equals(HashUtil.hashUtf8("abd")), "skirtingi ivedimai -> skirtingos maisos");
    }

    static void serialization() {
        T.eq("a12f|b34e|100|5", Transaction.serialize("a12f", "b34e", 100, 5), "pavyzdys");
        T.check(!Transaction.serialize("1", "23", 4, 5).equals(Transaction.serialize("12", "3", 4, 5)), "1|23 != 12|3");
        T.check(!Transaction.serialize(A, B, 12, 3).equals(Transaction.serialize(A, B, 1, 23)), "12|3 != 1|23");
        T.eq(HashUtil.hashUtf8("a12f|b34e|100|5"), Transaction.computeId("a12f", "b34e", 100, 5), "ID = maisa(UTF-8 serializacija)");
        Transaction t = Transaction.create(A, B, 7, 0);
        T.check(t.hasValidId(), "sukurtas ID galioja");
        T.check(!new Transaction(A, B, 8, 0, t.id()).hasValidId(), "pakeista suma -> ID negalioja");
    }

    static void seedDeterminism() {
        List<String> a = ids(42), b = ids(42), c = ids(43);
        T.eq(a, b, "tas pats seed");
        T.check(!a.equals(c), "kitas seed -> kitos tx");
    }

    static List<String> ids(long seed) {
        Random r = new Random(seed);
        List<User> users = Generator.users(r, 50);
        List<String> out = new ArrayList<>();
        for (Transaction t : Generator.transactions(r, users, 500, 1000, 0.05)) out.add(t.id());
        return out;
    }

    static void fixedHeader() {
        BlockHeader h = new BlockHeader(HashUtil.ZERO_HASH, 1700000000L, 1, HashUtil.hashUtf8(""), 42, 3);
        T.eq(HashUtil.ZERO_HASH + "|1700000000|1|" + H_EMPTY + "|42|3", h.serialize(), "antrastes serializacija");
        T.eq(h.hash(), new BlockHeader(HashUtil.ZERO_HASH, 1700000000L, 1, HashUtil.hashUtf8(""), 42, 3).hash(), "du objektai");
        T.eq(H_FIXED_HEADER, h.hash(), "uzfiksuotas antrastes hash");
    }

    static void powRecomputes() {
        BlockHeader tpl = new BlockHeader(HashUtil.ZERO_HASH, 1, 1, H_EMPTY, 0, 3);
        Pow.Result r = Pow.mine(tpl);
        T.check(r.hash().startsWith("000"), "rasta maisa prasideda 000");
        T.eq(r.hash(), r.header().hash(), "maisa perskaiciuojama is antrastes");
        T.eq((long) r.attempts(), r.header().nonce() + 1, "bandymu skaicius = nonce + 1");
        T.check(Pow.isValid(r.header()), "galiojantis PoW");
        // Pakeitus timestamp, senas nonce nebegarantuoja PoW: isValid sprendžia tik pagal perskaičiuotą maišą.
        for (long ts = 2; ts < 50; ts++) {
            BlockHeader changed = new BlockHeader(HashUtil.ZERO_HASH, ts, 1, H_EMPTY, r.header().nonce(), 3);
            T.eq(changed.hash().startsWith("000"), Pow.isValid(changed), "isValid = perskaiciuotos maisos patikra");
        }
    }

    /**
     * Pradžia: A=100, B=50, C=0 (suma 150). Bloko dydis 2, FIFO tvarka.
     * tx1 A->B 30, tx2 A->C 50, tx3 A->B 60, tx4 B->C 80, tx5 C->A 10, tx6 C->A 15.
     * Blokas 0: tx1, tx2            -> A=20  B=80  C=50  (tx3 nebeįeina: blokas pilnas)
     * Blokas 1: tx4, tx5 (tx3 praleista: A=20 < 60)  -> A=30  B=0   C=120
     * Blokas 2: tx6 (nepilnas)      -> A=45  B=0   C=105
     * tx3 lieka fonde: A=45 < 60.
     */
    static void handCase() throws Exception {
        Blockchain chain = new Blockchain(State.fromUsers(List.of(
                new User("A", A, 100), new User("B", B, 50), new User("C", C, 0))), 2);
        TxPool pool = new TxPool();
        Transaction tx3 = Transaction.create(A, B, 60, 2);
        for (Transaction t : List.of(Transaction.create(A, B, 30, 0), Transaction.create(A, C, 50, 1), tx3,
                Transaction.create(B, C, 80, 0), Transaction.create(C, A, 10, 0), Transaction.create(C, A, 15, 1)))
            T.eq(null, pool.submit(t, chain), "priimta i fonda");

        int[] sizes = new int[10];
        int n = 0;
        List<Transaction> sel;
        while ((sel = Miner.select(chain, pool, 2, null)) != null) {
            chain.addBlock(Miner.mine(chain, sel, n).block());
            pool.removeConfirmed(sel);
            sizes[n++] = sel.size();
        }
        T.eq(3, n, "bloku skaicius");
        T.eq(List.of(2, 2, 1), List.of(sizes[0], sizes[1], sizes[2]), "tx bloke (paskutinis nepilnas)");
        State s = chain.state();
        T.eq(45L, s.balance(A), "A");
        T.eq(0L, s.balance(B), "B");
        T.eq(105L, s.balance(C), "C");
        T.eq(150L, s.total(), "bendra suma");
        T.eq(List.of(tx3), pool.pending(), "likusi tx3");
        T.eq(TxError.INSUFFICIENT_BALANCE, s.check(tx3), "tx3 priezastis");
        T.eq(100L, chain.initialBalances().get(A), "pradine busena issaugota");
        for (Block b : chain.blocks()) T.check(b.hash().startsWith("00"), "PoW difficulty=2");
    }

    static void doubleSpendSameBlock() throws Exception {
        Blockchain chain = new Blockchain(State.fromUsers(List.of(
                new User("A", A, 100), new User("B", B, 0), new User("C", C, 0))), 1);
        Transaction t1 = Transaction.create(A, B, 60, 0), t2 = Transaction.create(A, C, 60, 1);
        TxPool pool = new TxPool();
        pool.submit(t1, chain);
        pool.submit(t2, chain);
        List<Transaction> sel = Miner.select(chain, pool, 10, null);
        T.eq(List.of(t1), sel, "i bloka parenkamas tik vienas is dvieju 60 mokejimu");

        // Blokas, kuriame A išleidžia 120 turėdamas 100, turi būti atmestas net su galiojančiu PoW.
        Block bad = Miner.mine(chain, List.of(t1, t2), 0).block();
        T.expectReject(chain, bad, "nepakankamas");
        T.eq(0, chain.height(), "grandine nepakito");
        T.eq(100L, chain.state().balance(A), "busena nepakito");
    }

    static void duplicates() throws Exception {
        Blockchain chain = new Blockchain(State.fromUsers(List.of(new User("A", A, 100), new User("B", B, 0))), 1);
        Transaction t = Transaction.create(A, B, 10, 0);
        TxPool pool = new TxPool();
        T.eq(null, pool.submit(t, chain), "pirmas kartas");
        T.eq(TxError.DUPLICATE_IN_POOL, pool.submit(t, chain), "antras kartas fonde");
        T.expectReject(chain, Miner.mine(chain, List.of(t, t), 0).block(), "kartojasi bloke");
        chain.addBlock(Miner.mine(chain, List.of(t), 0).block());
        pool.removeConfirmed(List.of(t));
        T.eq(TxError.ALREADY_CONFIRMED, pool.submit(t, chain), "jau patvirtinta");
        T.expectReject(chain, Miner.mine(chain, List.of(t), 1).block(), "patvirtintas");
        T.eq(1, chain.confirmedCount(), "patvirtinta 1");
    }

    static void badBlocks() throws Exception {
        Blockchain chain = new Blockchain(State.fromUsers(List.of(new User("A", A, 100), new User("B", B, 0))), 2);
        List<Transaction> txs = List.of(Transaction.create(A, B, 10, 0));
        Block good = Miner.mine(chain, txs, 0).block();
        BlockHeader g = good.header();

        T.expectReject(chain, new Block(new BlockHeader(H_ABC, g.timestamp(), Blockchain.VERSION, g.txRoot(), 0, 2), txs), "prev_hash");
        T.expectReject(chain, new Block(g, List.of(Transaction.create(A, B, 11, 0))), "tx_root");
        T.expectReject(chain, new Block(Pow.mine(new BlockHeader(g.prevHash(), 0, Blockchain.VERSION, g.txRoot(), 0, 1)).header(), txs), "sudetinguma");
        T.expectReject(chain, new Block(Pow.mine(new BlockHeader(g.prevHash(), 0, Blockchain.VERSION, Merkle.root(List.of()), 0, 2)).header(),
                List.of()), "tuscias");
        // Ieškome nonce, kurio maiša NEtenkina sąlygos – blokas turi būti atmestas.
        long nonce = 0;
        while (Pow.isValid(g.withNonce(nonce))) nonce++;
        T.expectReject(chain, new Block(g.withNonce(nonce), txs), "PoW");

        chain.addBlock(good);
        T.eq(1, chain.height(), "geras blokas priimtas");
        T.expectReject(chain, good, "prev_hash"); // to paties bloko pakartotinai prijungti negalima
    }

    static void fullSimulation() throws Exception {
        Path dir = Files.createTempDirectory("bc-test");
        String[] args = {"--users", "60", "--txs", "800", "--block-size", "25", "--difficulty", "2",
                "--max-amount", "200000", "--invalid-rate", "0.05", "--fixed-time", "--out", dir.toString()};
        PrintStream orig = System.out;
        String tip1, tip2;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buf, true, "UTF-8"));
            tip1 = new Simulation(Config.parse(args)).run(); // run() pats meta išimtį, jei pažeisti invariantai
            tip2 = new Simulation(Config.parse(args)).run();
        } finally {
            System.setOut(orig);
        }
        T.eq(tip1, tip2, "tas pats seed + --fixed-time -> ta pati grandine");
        String out = buf.toString("UTF-8");
        T.check(out.contains("(sutampa)"), "lesu suma sutampa");
        T.check(out.contains("= sugeneruota 800 (OK)"), "visos tx paaiskintos");
        T.check(Files.readString(dir.resolve("report.txt")).contains("INSUFFICIENT_BALANCE"), "ataskaitoje yra likusiu tx");
    }
}
