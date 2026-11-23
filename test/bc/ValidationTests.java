package bc;

import bc.core.BlockError;
import bc.core.Blockchain;
import bc.core.CandidateBuilder;
import bc.core.Merkle;
import bc.core.Miner;
import bc.core.Pow;
import bc.core.State;
import bc.core.TxError;
import bc.core.TxPool;
import bc.core.Validator;
import bc.hash.HashUtil;
import bc.model.Block;
import bc.model.BlockHeader;
import bc.model.Transaction;

import java.util.List;
import java.util.Map;

import static bc.T.A;
import static bc.T.B;
import static bc.T.C;

final class ValidationTests {
    private ValidationTests() {}

    static void all() {
        T.test("1 etapas: netinkamos tx neprileidziamos i fonda (kiekviena taisykle atskirai)", ValidationTests::admissionRules);
        T.test("1 etapas: patekimas i fonda lesu nerezervuoja", ValidationTests::admissionNoReservation);
        T.test("2 etapas: A=100, T1 70 + T2 60 -> i kandidata tik viena (abi tvarkos)", ValidationTests::candidateExample);
        T.test("2 etapas: du mokejimai po 70 is 100 negali abu patekti", ValidationTests::twoSeventies);
        T.test("2 etapas: kandidato atranka nekeicia tikruju balansu", ValidationTests::candidateNoSideEffects);
        T.test("balanso perpildymas aptinkamas", ValidationTests::overflow);
        T.test("3 etapas: bloko antrastes klaidos (versija, prev_hash, sudetingumas, PoW)", ValidationTests::headerErrors);
        T.test("3 etapas: kandidatas negali pats sumazinti sudetingumo", ValidationTests::loweredDifficulty);
        T.test("3 etapas: turinio klaidos (tuscias, per daug tx, tx_root)", ValidationTests::bodyErrors);
        T.test("3 etapas: blogos tx bloke (balansas, dalyvis, ID, dublikatas, patvirtinta, suma)", ValidationTests::badTxInBlock);
        T.test("3 etapas: tx tikrinamos ju tvarka bloke vienoje laikinoje busenoje", ValidationTests::orderInBlock);
        T.test("atmestas blokas nekeicia grandines, balansu, patvirtintu ID ir fondo", ValidationTests::atomicReject);
        T.test("priimtas blokas vienu veiksmu atnaujina grandine, balansus ir fonda", ValidationTests::atomicAccept);
        T.test("ID atitikimas tikrina vientisuma, bet neatstoja paraso", ValidationTests::idIsNotSignature);
    }

    static Blockchain chain(long a, long b, long c, int difficulty) {
        return new Blockchain(State.fromUsers(List.of(T.user("A", A, a), T.user("B", B, b), T.user("C", C, c))), difficulty);
    }

    static void admissionRules() {
        Blockchain chain = chain(100, 0, 0, 1);
        TxPool pool = new TxPool();
        String ghost = HashUtil.hashUtf8("ghost");
        Transaction ok = Transaction.create(A, B, 10, 0);
        Map<Transaction, TxError> cases = new java.util.LinkedHashMap<>();
        cases.put(new Transaction("a12f", B, 10, 0, Transaction.computeId("a12f", B, 10, 0)), TxError.BAD_FORMAT);
        cases.put(new Transaction(A, B, 10, 0, "XYZ"), TxError.BAD_FORMAT);
        cases.put(Transaction.create(A, B, 10, -1), TxError.BAD_FORMAT);
        cases.put(Transaction.create(A, B, 0, 1), TxError.NON_POSITIVE_AMOUNT);
        cases.put(Transaction.create(A, B, -5, 2), TxError.NON_POSITIVE_AMOUNT);
        cases.put(Transaction.create(ghost, B, 10, 3), TxError.UNKNOWN_SENDER);
        cases.put(Transaction.create(A, ghost, 10, 4), TxError.UNKNOWN_RECEIVER);
        cases.put(Transaction.create(A, A, 10, 5), TxError.SELF_TRANSFER);
        cases.put(new Transaction(A, B, 11, 0, ok.id()), TxError.BAD_ID);
        for (Map.Entry<Transaction, TxError> c : cases.entrySet()) {
            T.eq(c.getValue(), pool.submit(c.getKey(), chain), "priezastis " + c.getKey());
        }
        T.eq(0, pool.size(), "nei viena netinkama nepateko");
        T.eq(cases.size(), pool.rejected().size(), "visos uzfiksuotos su priezastimi");
        T.eq(null, pool.submit(ok, chain), "tinkama priimta");
        T.eq(TxError.DUPLICATE_IN_POOL, pool.submit(ok, chain), "jau fonde");
        T.eq(TxError.DUPLICATE_IN_POOL, Validator.admission(ok, chain, pool), "ta pati procedura");
    }

    static void admissionNoReservation() {
        Blockchain chain = chain(100, 0, 0, 1);
        TxPool pool = new TxPool();
        T.eq(null, pool.submit(Transaction.create(A, B, 70, 0), chain), "T1 priimta");
        T.eq(null, pool.submit(Transaction.create(A, C, 60, 1), chain), "T2 irgi priimta (lesos nerezervuotos)");
        T.eq(null, pool.submit(Transaction.create(A, C, 500, 2), chain), "net didesne uz balansa - sprendziama veliau");
        T.eq(3, pool.size(), "fonde 3");
        T.eq(100L, chain.state().balance(A), "balansas nepakito");
    }

    static void candidateExample() {
        Blockchain chain = chain(100, 0, 0, 1);
        Transaction t1 = Transaction.create(A, B, 70, 0), t2 = Transaction.create(A, C, 60, 1);
        CandidateBuilder.Selection s = CandidateBuilder.select(chain, List.of(t1, t2), 100);
        T.eq(List.of(t1), s.txs(), "T1 itraukta (70 <= 100), laikinas A = 30");
        T.eq(List.of(t2), s.deferred(), "T2 atidedama (60 > 30)");
        CandidateBuilder.Selection r = CandidateBuilder.select(chain, List.of(t2, t1), 100);
        T.eq(List.of(t2), r.txs(), "atvirkscia tvarka: T2 itraukta, A = 40");
        T.eq(List.of(t1), r.deferred(), "T1 nebetinka (70 > 40)");
    }

    static void twoSeventies() throws Exception {
        Blockchain chain = chain(100, 0, 0, 1);
        Transaction t1 = Transaction.create(A, B, 70, 0), t2 = Transaction.create(A, C, 70, 1);
        T.eq(List.of(t1), CandidateBuilder.select(chain, List.of(t1, t2), 100).txs(), "tik viena");
        // net jei kas nors sudarytų tokį bloką ir jį iškastų – grandinė jį atmeta
        T.expectReject(chain, Miner.mine(chain, List.of(t1, t2), 0).block(), BlockError.BAD_TX, TxError.INSUFFICIENT_BALANCE);
    }

    static void candidateNoSideEffects() {
        Blockchain chain = chain(100, 50, 0, 1);
        Map<String, Long> before = chain.state().snapshot();
        CandidateBuilder.select(chain, List.of(Transaction.create(A, B, 90, 0), Transaction.create(B, C, 140, 0)), 100);
        T.eq(before, chain.state().snapshot(), "tikrieji balansai nepakito");
        T.eq(0, chain.height(), "grandine nepakito");
    }

    static void overflow() {
        State s = State.fromUsers(List.of(T.user("A", A, 100), T.user("B", B, Long.MAX_VALUE - 50)));
        T.eq(TxError.OVERFLOW, s.check(Transaction.create(A, B, 60, 0)), "B + 60 perpildytu long");
        T.eq(null, s.check(Transaction.create(A, B, 50, 1)), "lygiai iki ribos - galima");
    }

    static void headerErrors() throws Exception {
        Blockchain chain = chain(100, 0, 0, 2);
        List<Transaction> txs = List.of(Transaction.create(A, B, 10, 0));
        String root = Merkle.root(txs);
        BlockHeader v1 = Pow.mine(new BlockHeader(chain.tipHash(), 0, 1, root, 0, 2)).header();
        T.expectReject(chain, new Block(v1, txs), BlockError.UNSUPPORTED_VERSION, null);
        BlockHeader badPrev = Pow.mine(new BlockHeader(HashUtil.hashUtf8("x"), 0, Blockchain.VERSION, root, 0, 2)).header();
        T.expectReject(chain, new Block(badPrev, txs), BlockError.BAD_PREV_HASH, null);
        BlockHeader higher = Pow.mine(new BlockHeader(chain.tipHash(), 0, Blockchain.VERSION, root, 0, 3)).header();
        T.expectReject(chain, new Block(higher, txs), BlockError.BAD_DIFFICULTY, null);
        BlockHeader good = Miner.mine(chain, txs, 0).block().header();
        long nonce = 0;
        while (Pow.meets(good.withNonce(nonce), 2)) nonce++;
        T.expectReject(chain, new Block(good.withNonce(nonce), txs), BlockError.BAD_POW, null);
        T.eq(0, chain.height(), "nieko nepriimta");
    }

    static void loweredDifficulty() throws Exception {
        Blockchain chain = chain(100, 0, 0, 3);
        List<Transaction> txs = List.of(Transaction.create(A, B, 10, 0));
        // Iškasta su difficulty=1: maiša tenkina SAVO deklaruotą sąlygą, bet ne grandinės (3)
        BlockHeader easy = Pow.mine(new BlockHeader(chain.tipHash(), 0, Blockchain.VERSION, Merkle.root(txs), 0, 1)).header();
        T.check(Pow.isValid(easy), "pagal savo antraste PoW 'galioja'");
        T.expectReject(chain, new Block(easy, txs), BlockError.LOWERED_DIFFICULTY, null);
        // Net jei pakeistume deklaruotą difficulty į 3, ta pati maiša sąlygos netenkina (perskaičiuojama)
        BlockHeader relabeled = new BlockHeader(easy.prevHash(), easy.timestamp(), easy.version(), easy.txRoot(), easy.nonce(), 3);
        if (!Pow.meets(relabeled, 3)) T.expectReject(chain, new Block(relabeled, txs), BlockError.BAD_POW, null);
    }

    static void bodyErrors() throws Exception {
        Blockchain chain = new Blockchain(State.fromUsers(List.of(T.user("A", A, 100), T.user("B", B, 0))), 1, 2);
        T.expectReject(chain, Miner.mine(chain, List.of(), 0).block(), BlockError.EMPTY, null);
        List<Transaction> three = List.of(Transaction.create(A, B, 1, 0), Transaction.create(A, B, 1, 1), Transaction.create(A, B, 1, 2));
        T.expectReject(chain, Miner.mine(chain, three, 0).block(), BlockError.TOO_MANY_TX, null);
        List<Transaction> two = three.subList(0, 2);
        Block good = Miner.mine(chain, two, 0).block();
        T.expectReject(chain, new Block(good.header(), List.of(two.get(1), two.get(0))), BlockError.BAD_TX_ROOT, null);
        T.expectReject(chain, new Block(good.header(), two.subList(0, 1)), BlockError.BAD_TX_ROOT, null);
        chain.addBlock(good);
        T.eq(1, chain.height(), "2 tx (= riba) priimta");
    }

    static void badTxInBlock() throws Exception {
        Blockchain chain = chain(100, 0, 0, 1);
        Transaction ok = Transaction.create(A, B, 10, 0);
        String ghost = HashUtil.hashUtf8("ghost");
        Object[][] cases = {
                {Transaction.create(A, B, 101, 1), TxError.INSUFFICIENT_BALANCE},
                {Transaction.create(A, ghost, 1, 2), TxError.UNKNOWN_RECEIVER},
                {Transaction.create(ghost, A, 1, 3), TxError.UNKNOWN_SENDER},
                {new Transaction(A, B, 99, 4, Transaction.computeId(A, B, 9, 4)), TxError.BAD_ID},
                {Transaction.create(A, B, 0, 5), TxError.NON_POSITIVE_AMOUNT},
                {Transaction.create(A, A, 5, 6), TxError.SELF_TRANSFER},
        };
        for (Object[] c : cases) {
            Transaction bad = (Transaction) c[0];
            T.expectReject(chain, Miner.mine(chain, List.of(ok, bad), 0).block(), BlockError.BAD_TX, (TxError) c[1]);
        }
        T.expectReject(chain, Miner.mine(chain, List.of(ok, ok), 0).block(), BlockError.BAD_TX, TxError.DUPLICATE_IN_BLOCK);
        chain.addBlock(Miner.mine(chain, List.of(ok), 0).block());
        T.expectReject(chain, Miner.mine(chain, List.of(ok), 1).block(), BlockError.BAD_TX, TxError.ALREADY_CONFIRMED);
    }

    static void orderInBlock() throws Exception {
        // B turi 0; A->B 50 ir B->C 30: tvarka [A->B, B->C] tinka, [B->C, A->B] – ne
        Blockchain chain = chain(100, 0, 0, 1);
        Transaction ab = Transaction.create(A, B, 50, 0), bc = Transaction.create(B, C, 30, 0);
        T.expectReject(chain, Miner.mine(chain, List.of(bc, ab), 0).block(), BlockError.BAD_TX, TxError.INSUFFICIENT_BALANCE);
        chain.addBlock(Miner.mine(chain, List.of(ab, bc), 0).block());
        T.eq(List.of(50L, 20L, 30L), List.of(chain.state().balance(A), chain.state().balance(B), chain.state().balance(C)), "balansai");
    }

    static void atomicReject() throws Exception {
        Blockchain chain = chain(100, 0, 0, 1);
        TxPool pool = new TxPool();
        Transaction first = Transaction.create(A, B, 30, 0);
        chain.addBlock(Miner.mine(chain, List.of(first), 0).block(), pool);
        Transaction good = Transaction.create(A, C, 20, 1), bad = Transaction.create(B, C, 999, 0);
        pool.submit(good, chain);
        pool.submit(bad, chain);

        Map<String, Long> balances = chain.state().snapshot();
        String tip = chain.tipHash();
        int height = chain.height(), confirmed = chain.confirmedCount(), poolSize = pool.size();

        T.expectReject(chain, Miner.mine(chain, List.of(good, bad), 1).block(), BlockError.BAD_TX, TxError.INSUFFICIENT_BALANCE);
        try {
            chain.addBlock(Miner.mine(chain, List.of(good, bad), 1).block(), pool);
        } catch (bc.core.InvalidBlockException expected) {
            // tikimasi
        }
        T.eq(balances, chain.state().snapshot(), "balansai");
        T.eq(tip, chain.tipHash(), "paskutinis blokas");
        T.eq(height, chain.height(), "aukstis");
        T.eq(confirmed, chain.confirmedCount(), "patvirtintu ID skaicius");
        T.check(!chain.isConfirmed(good.id()), "gera tx is atmesto bloko nepatvirtinta");
        T.eq(poolSize, pool.size(), "fondas");
    }

    static void atomicAccept() throws Exception {
        Blockchain chain = chain(100, 0, 0, 1);
        TxPool pool = new TxPool();
        Transaction t1 = Transaction.create(A, B, 30, 0), t2 = Transaction.create(A, C, 20, 1), t3 = Transaction.create(A, C, 5, 2);
        for (Transaction t : List.of(t1, t2, t3)) pool.submit(t, chain);
        chain.addBlock(Miner.mine(chain, List.of(t1, t2), 0).block(), pool);
        T.eq(List.of(t3), pool.pending(), "is fondo pasalintos tik itrauktos");
        T.check(chain.isConfirmed(t1.id()) && chain.isConfirmed(t2.id()), "patvirtintos");
        T.eq(50L, chain.state().balance(A), "A balansas");
    }

    /** Bet kas, žinantis A viešąjį identifikatorių, gali sukurti „A -> B“ su teisingu ID – parašo nėra. */
    static void idIsNotSignature() {
        Blockchain chain = chain(100, 0, 0, 1);
        Transaction forged = Transaction.create(A, B, 100, 0); // sukurta ne A, bet ID vis tiek teisingas
        T.check(forged.hasValidId(), "ID teisingas");
        T.eq(null, new TxPool().submit(forged, chain), "v0.2 tokia tx priimama - autorizacijos patikros nera");
    }
}
