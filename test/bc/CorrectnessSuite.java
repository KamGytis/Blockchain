package bc;

import bc.core.BlockError;
import bc.core.Blockchain;
import bc.core.Candidate;
import bc.core.CandidateBuilder;
import bc.core.Competition;
import bc.core.InvalidBlockException;
import bc.core.Merkle;
import bc.core.Miner;
import bc.core.Pow;
import bc.core.State;
import bc.core.TxError;
import bc.core.TxPool;
import bc.hash.HashUtil;
import bc.model.Block;
import bc.model.BlockHeader;
import bc.model.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static bc.T.A;
import static bc.T.B;
import static bc.T.C;

/**
 * Bendras teisingumo testų rinkinys (5 skyriaus lentelė). Naudoja TIK v0.2 viešąją API, todėl tas pats failas
 * be pakeitimų paleidžiamas ir užfiksuotoje v0.2 (žyma 0.2), ir v0.3:
 * java -cp out/classes:out/test bc.CorrectnessSuite
 */
public final class CorrectnessSuite {
    private CorrectnessSuite() {}

    public static void main(String[] args) {
        all();
        System.out.printf("%nBendras rinkinys: %d praejo, %d nepavyko%n", T.passed, T.failed);
        if (T.failed > 0) System.exit(1);
    }

    static void all() {
        T.test("[1] galiojantys mokejimai; paskutinis blokas <100: balansai, suma, itraukta 1 karta", CorrectnessSuite::validPayments);
        T.test("[2] nezinomas gavejas, suma 0 / neigiama, pakeistas ID: atmesta, priezastis matoma", CorrectnessSuite::invalidTx);
        T.test("[3] du to paties siuntejo mokejimai virsija balansa: nepriimta, laikomasi tvarkos", CorrectnessSuite::overspend);
        T.test("[4] dubliuotas ID ir patvirtinto pakartojimas: nera pakartotinio iskaitymo", CorrectnessSuite::noDoubleCredit);
        T.test("[5] pakeistas turinys, prev_hash, blogas PoW: atmesta, busena nepakito", CorrectnessSuite::tamperedBlocks);
        T.test("[6a] Merkle 1/2/3/5: teisinga medzio konstrukcija", CorrectnessSuite::merkleCases);
        T.test("[6b] nelaimeje kandidatai: busenos nesusimaiso", CorrectnessSuite::losingCandidates);
    }

    // ---------- pagalbinės ----------

    record Snapshot(Map<String, Long> balances, String tip, int height, int confirmed) {
        static Snapshot of(Blockchain c) {
            return new Snapshot(c.state().snapshot(), c.tipHash(), c.height(), c.confirmedCount());
        }
    }

    static Blockchain chain(long a, long b, long c, int difficulty) {
        return new Blockchain(State.fromUsers(List.of(T.user("A", A, a), T.user("B", B, b), T.user("C", C, c))), difficulty, 100);
    }

    static Block mined(Blockchain chain, List<Transaction> txs) {
        return Miner.mine(chain, txs, chain.height()).block();
    }

    // ---------- scenarijai ----------

    static void validPayments() throws Exception {
        Blockchain chain = chain(100_000, 0, 0, 2);
        TxPool pool = new TxPool();
        List<Transaction> all = new ArrayList<>();
        Map<String, Long> expected = new HashMap<>(Map.of(A, 100_000L, B, 0L, C, 0L));
        for (int i = 0; i < 150; i++) {
            String to = i % 3 == 0 ? C : B;
            Transaction t = Transaction.create(A, to, 1 + i, i);
            all.add(t);
            T.eq(null, pool.submit(t, chain), "priimta");
            expected.merge(A, -t.amount(), Long::sum);
            expected.merge(to, t.amount(), Long::sum);
        }
        Random rng = new Random(1);
        List<Integer> sizes = new ArrayList<>();
        while (true) {
            Competition.Result r = Competition.buildAndRun(chain, pool, 5, 100, rng, chain.height(),
                    new Competition.Budget(2000, Long.MAX_VALUE, 10));
            if (r.winner() == null) break;
            chain.addBlock(r.winner().block(), pool);
            sizes.add(r.winner().transactions().size());
        }
        T.eq(List.of(100, 50), sizes, "blokai: pilnas ir paskutinis nepilnas");
        T.eq(expected, chain.state().snapshot(), "balansai sutampa su nepriklausomai suskaiciuotais");
        T.eq(100_000L, chain.state().total(), "lesu suma nekinta");
        Map<String, Integer> times = new HashMap<>();
        for (Block b : chain.blocks()) for (Transaction t : b.transactions()) times.merge(t.id(), 1, Integer::sum);
        for (Transaction t : all) T.eq(1, times.get(t.id()), "kiekviena tx itraukta lygiai 1 karta");
        T.eq(0, pool.size(), "fondas tuscias");
    }

    static void invalidTx() throws Exception {
        Blockchain chain = chain(100, 0, 0, 1);
        TxPool pool = new TxPool();
        String ghost = HashUtil.hashUtf8("nezinomas");
        Transaction valid = Transaction.create(A, B, 10, 0);
        Map<Transaction, TxError> bad = new LinkedHashMap<>();
        bad.put(Transaction.create(A, ghost, 10, 1), TxError.UNKNOWN_RECEIVER);
        bad.put(Transaction.create(A, B, 0, 2), TxError.NON_POSITIVE_AMOUNT);
        bad.put(Transaction.create(A, B, -10, 3), TxError.NON_POSITIVE_AMOUNT);
        bad.put(new Transaction(A, B, 99, 0, valid.id()), TxError.BAD_ID);
        for (Map.Entry<Transaction, TxError> e : bad.entrySet()) {
            T.eq(e.getValue(), pool.submit(e.getKey(), chain), "priezastis grazinama");
        }
        List<TxPool.Rejection> rej = pool.rejected();
        T.eq(bad.size(), rej.size(), "visos atmestos");
        for (int i = 0; i < rej.size(); i++) {
            T.eq(new ArrayList<>(bad.values()).get(i), rej.get(i).reason(), "priezastis matoma atmestuju sarase");
            T.check(!rej.get(i).reason().description().isEmpty(), "priezasties aprasymas");
        }
        T.eq(0, pool.size(), "i fonda nepateko");
        Snapshot before = Snapshot.of(chain);
        for (Map.Entry<Transaction, TxError> e : bad.entrySet()) {
            InvalidBlockException ex = T.expectReject(chain, mined(chain, List.of(valid, e.getKey())), BlockError.BAD_TX, e.getValue());
            T.check(ex.getMessage().contains(e.getValue().description()), "priezastis matoma bloko klaidoje");
        }
        T.eq(before, Snapshot.of(chain), "busena nepakito");
    }

    static void overspend() throws Exception {
        Blockchain chain = chain(100, 0, 0, 1);
        Transaction t1 = Transaction.create(A, B, 70, 0), t2 = Transaction.create(A, C, 60, 1);
        TxPool pool = new TxPool();
        T.eq(null, pool.submit(t1, chain), "T1 i fonda");
        T.eq(null, pool.submit(t2, chain), "T2 i fonda (lesos nerezervuojamos)");
        T.eq(List.of(t1), CandidateBuilder.select(chain, List.of(t1, t2), 100).txs(), "tvarka T1,T2 -> tik T1");
        T.eq(List.of(t2), CandidateBuilder.select(chain, List.of(t2, t1), 100).txs(), "tvarka T2,T1 -> tik T2");
        Snapshot before = Snapshot.of(chain);
        T.expectReject(chain, mined(chain, List.of(t1, t2)), BlockError.BAD_TX, TxError.INSUFFICIENT_BALANCE);
        T.eq(before, Snapshot.of(chain), "bendrai neleistinas rinkinys nieko nepakeite");
        chain.addBlock(mined(chain, List.of(t2)), pool);
        T.eq(List.of(40L, 0L, 60L), List.of(chain.state().balance(A), chain.state().balance(B), chain.state().balance(C)), "po T2");
        T.eq(TxError.INSUFFICIENT_BALANCE, chain.state().check(t1), "T1 dabar netinka (40 < 70)");
        T.eq(List.of(t1), pool.pending(), "T1 atideta fonde");
    }

    static void noDoubleCredit() throws Exception {
        Blockchain chain = chain(100, 0, 0, 1);
        TxPool pool = new TxPool();
        Transaction t = Transaction.create(A, B, 30, 0);
        T.eq(null, pool.submit(t, chain), "pirmas");
        T.eq(TxError.DUPLICATE_IN_POOL, pool.submit(t, chain), "dublikatas fonde");
        T.eq(1, pool.size(), "fonde viena");
        T.expectReject(chain, mined(chain, List.of(t, t)), BlockError.BAD_TX, TxError.DUPLICATE_IN_BLOCK);
        chain.addBlock(mined(chain, List.of(t)), pool);
        T.eq(TxError.ALREADY_CONFIRMED, pool.submit(t, chain), "patvirtintos pakartojimas i fonda");
        Snapshot after = Snapshot.of(chain);
        T.expectReject(chain, mined(chain, List.of(t)), BlockError.BAD_TX, TxError.ALREADY_CONFIRMED);
        T.eq(after, Snapshot.of(chain), "pakartojimas bloke nieko nepakeite");
        T.eq(30L, chain.state().balance(B), "B iskaityta tik 1 karta");
        T.eq(70L, chain.state().balance(A), "A nuskaiciuota tik 1 karta");
    }

    static void tamperedBlocks() throws Exception {
        Blockchain chain = chain(100, 0, 0, 2);
        chain.addBlock(mined(chain, List.of(Transaction.create(A, B, 10, 0))));
        Snapshot before = Snapshot.of(chain);
        List<Transaction> txs = List.of(Transaction.create(A, C, 20, 1));
        Block good = mined(chain, txs);

        // pakeistas turinys: kita suma, ID perskaičiuotas -> tx galioja, bet tx_root nebe
        Block content = new Block(good.header(), List.of(Transaction.create(A, C, 90, 1)));
        T.expectReject(chain, content, BlockError.BAD_TX_ROOT, null);
        // pakeistas prev_hash (su teisingu PoW naujai antraštei)
        BlockHeader h = good.header();
        BlockHeader otherPrev = Pow.mine(new BlockHeader(HashUtil.ZERO_HASH, h.timestamp(), h.version(), h.txRoot(), 0, h.difficulty())).header();
        T.expectReject(chain, new Block(otherPrev, txs), BlockError.BAD_PREV_HASH, null);
        // netinkamas PoW: nonce, kurio perskaičiuota maiša netenkina sąlygos
        long n = 0;
        while (Pow.meets(h.withNonce(n), 2)) n++;
        T.expectReject(chain, new Block(h.withNonce(n), txs), BlockError.BAD_POW, null);

        T.eq(before, Snapshot.of(chain), "patvirtinta busena nepasikeite");
        chain.addBlock(good);
        T.eq(2, chain.height(), "originalus blokas tinka");
    }

    static void merkleCases() {
        List<Transaction> txs = new ArrayList<>();
        for (int i = 0; i < 5; i++) txs.add(Transaction.create(A, B, 1 + i, i));
        List<String> id = new ArrayList<>();
        for (Transaction t : txs) id.add(t.id());
        String ab = MerkleTests.h(id.get(0), id.get(1)), cc = MerkleTests.h(id.get(2), id.get(2));
        String cd = MerkleTests.h(id.get(2), id.get(3)), ee = MerkleTests.h(id.get(4), id.get(4));
        T.eq(id.get(0), Merkle.root(txs.subList(0, 1)), "1 lapas");
        T.eq(ab, Merkle.root(txs.subList(0, 2)), "2 lapai");
        T.eq(MerkleTests.h(ab, cc), Merkle.root(txs.subList(0, 3)), "3 lapai");
        T.eq(MerkleTests.h(MerkleTests.h(ab, cd), MerkleTests.h(ee, ee)), Merkle.root(txs), "5 lapai");
        T.eq(HashUtil.ZERO_HASH, Merkle.root(List.of()), "tuscias (genesis)");
    }

    static void losingCandidates() throws Exception {
        Blockchain chain = chain(100, 50, 0, 2);
        TxPool pool = new TxPool();
        // konfliktuojantys mokėjimai: tik vienas iš T1/T2 gali patekti į tą patį kandidatą
        Transaction t1 = Transaction.create(A, B, 70, 0), t2 = Transaction.create(A, C, 60, 1);
        Transaction t3 = Transaction.create(B, C, 40, 0), t4 = Transaction.create(B, C, 5, 1); // A lėšų negauna
        for (Transaction t : List.of(t1, t2, t3, t4)) pool.submit(t, chain);
        Snapshot base = Snapshot.of(chain);

        List<Candidate> cands = Competition.build(chain, pool, 5, 100, new Random(3), 0, new LinkedHashMap<>());
        Set<Set<String>> contents = new HashSet<>();
        Map<Integer, State> expectedAfter = new HashMap<>();
        for (Candidate c : cands) {
            Set<String> ids = new HashSet<>();
            for (Transaction t : c.transactions()) ids.add(t.id());
            contents.add(ids);
            T.check(!(ids.contains(t1.id()) && ids.contains(t2.id())), "K" + c.index() + ": T1 ir T2 kartu negalimos");
            expectedAfter.put(c.index(), chain.applyTransactions(c.transactions())); // kiekvienas nuo tos pačios būsenos
        }
        T.check(contents.size() > 1, "kandidatai skiriasi");
        T.eq(base, Snapshot.of(chain), "kandidatu sudarymas nepakeite busenos");

        Competition.Result r = Competition.run(cands, new Competition.Budget(2000, Long.MAX_VALUE, 10));
        Candidate w = r.winner();
        T.eq(base, Snapshot.of(chain), "kasimas nepakeite busenos");
        chain.addBlock(w.block(), pool);
        T.eq(expectedAfter.get(w.index()).snapshot(), chain.state().snapshot(), "busena = tik laimetojo poveikis");
        for (Candidate c : cands) {
            if (c == w) continue;
            for (Transaction t : c.transactions()) {
                boolean inWinner = w.transactions().contains(t);
                T.eq(inWinner, chain.isConfirmed(t.id()), "nelaimejusio tx patvirtinta tik jei yra laimetojo bloke");
            }
            if (c.solved()) T.expectReject(chain, c.block(), BlockError.BAD_PREV_HASH, null);
        }
        T.eq(150L, chain.state().total(), "suma nekinta");
    }
}
