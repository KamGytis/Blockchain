package bc;

import bc.core.BlockError;
import bc.core.Blockchain;
import bc.core.Merkle;
import bc.core.Miner;
import bc.core.State;
import bc.core.TxError;
import bc.hash.HashUtil;
import bc.model.Block;
import bc.model.BlockHeader;
import bc.model.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bc.T.A;
import static bc.T.B;
import static bc.T.C;
import static bc.T.D;

final class MerkleTests {
    private MerkleTests() {}

    static void all() {
        T.test("tuscias sarasas -> EMPTY_ROOT (64 nuliai, tik genesis)", MerkleTests::empty);
        T.test("1 tx: saknis = pats ID", MerkleTests::one);
        T.test("2 tx: H(a||b) is baitu, ne is hex teksto", MerkleTests::two);
        T.test("3 tx: neporinis poruojamas su kopija", MerkleTests::three);
        T.test("5 tx: lygiai 5-3-2-1", MerkleTests::five);
        T.test("pakeitus tx turini ir perskaiciavus ID saknis pasikeicia (1,2,3,5)", MerkleTests::contentChange);
        T.test("sukeitus skirtingu tx tvarka saknis pasikeicia (2,3,5)", MerkleTests::orderChange);
        T.test("zinoma struktura: [a,b,c] ir [a,b,c,c] ta pati saknis -> dublikatas blokuojamas", MerkleTests::duplicateLast);
        T.test("200 000 skirtingu ivesciu - maisos kolizijos nerasta", MerkleTests::noCollision);
    }

    // Nepriklausomas skaičiavimas (nenaudoja Merkle.parent): H(bytes(l) || bytes(r))
    static String h(String l, String r) {
        byte[] a = HashUtil.hexToBytes(l), b = HashUtil.hexToBytes(r);
        byte[] ab = new byte[64];
        System.arraycopy(a, 0, ab, 0, 32);
        System.arraycopy(b, 0, ab, 32, 32);
        return HashUtil.hashBytes(ab);
    }

    static List<Transaction> txs(int n) {
        String[] keys = {A, B, C, D};
        List<Transaction> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(Transaction.create(keys[i % 4], keys[(i + 1) % 4], 10 + i, i));
        return out;
    }

    static List<String> ids(List<Transaction> txs) {
        List<String> out = new ArrayList<>();
        for (Transaction t : txs) out.add(t.id());
        return out;
    }

    static void empty() {
        T.eq(HashUtil.ZERO_HASH, Merkle.root(List.of()), "tuscia saknis");
        T.eq(0, Merkle.levels(List.of()).size(), "lygiu nera");
    }

    static void one() {
        List<Transaction> t = txs(1);
        T.eq(t.get(0).id(), Merkle.root(t), "vienas lapas yra saknis");
    }

    static void two() {
        List<String> id = ids(txs(2));
        String expected = h(id.get(0), id.get(1));
        T.eq(expected, Merkle.rootOfIds(id), "2 lapu saknis");
        T.check(!expected.equals(HashUtil.hashUtf8(id.get(0) + id.get(1))), "jungiami baitai, ne hex tekstas");
        T.eq(32, HashUtil.hexToBytes(id.get(0)).length, "ID dekoduojamas i 32 baitus");
    }

    static void three() {
        List<String> id = ids(txs(3));
        String expected = h(h(id.get(0), id.get(1)), h(id.get(2), id.get(2)));
        T.eq(expected, Merkle.rootOfIds(id), "3 lapu saknis");
    }

    static void five() {
        List<String> id = ids(txs(5));
        String ab = h(id.get(0), id.get(1)), cd = h(id.get(2), id.get(3)), ee = h(id.get(4), id.get(4));
        String abcd = h(ab, cd), eeee = h(ee, ee);
        T.eq(h(abcd, eeee), Merkle.rootOfIds(id), "5 lapu saknis");
        List<List<String>> lv = Merkle.levels(id);
        T.eq(List.of(5, 3, 2, 1), List.of(lv.get(0).size(), lv.get(1).size(), lv.get(2).size(), lv.get(3).size()), "lygiu dydziai");
        T.eq(List.of(ab, cd, ee), lv.get(1), "1 lygis");
    }

    static void contentChange() {
        for (int n : new int[]{1, 2, 3, 5}) {
            List<Transaction> base = txs(n);
            String root = Merkle.root(base);
            for (int i = 0; i < n; i++) {
                List<Transaction> changed = new ArrayList<>(base);
                Transaction t = base.get(i);
                // pakeičiama suma ir ID perskaičiuojamas (transakcija pati savaime galiojanti)
                Transaction t2 = Transaction.create(t.sender(), t.receiver(), t.amount() + 1, t.sequence());
                T.check(t2.hasValidId() && !t2.id().equals(t.id()), "naujas ID");
                changed.set(i, t2);
                T.check(!root.equals(Merkle.root(changed)), "n=" + n + " pakeitus " + i + "-a tx saknis nepasikeite");
            }
        }
    }

    static void orderChange() {
        for (int n : new int[]{2, 3, 5}) {
            List<Transaction> base = txs(n);
            String root = Merkle.root(base);
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    List<Transaction> swapped = new ArrayList<>(base);
                    swapped.set(i, base.get(j));
                    swapped.set(j, base.get(i));
                    T.check(!root.equals(Merkle.root(swapped)), "n=" + n + " sukeitus " + i + "<->" + j);
                }
            }
        }
    }

    /**
     * Dokumentuota savybė (ne maišos kolizija, o medžio struktūros): kopijuojant neporinį mazgą, [a,b,c] ir
     * [a,b,c,c] turi tą pačią šaknį (tas pats žinomas Bitcoin atvejis, CVE-2012-2459). Todėl grandinė
     * atmeta bloką su pasikartojančiu ID – antroji versija niekada negali būti priimta.
     */
    static void duplicateLast() throws Exception {
        List<Transaction> three = txs(3);
        List<Transaction> four = new ArrayList<>(three);
        four.add(three.get(2));
        T.eq(Merkle.root(three), Merkle.root(four), "struktura sutampa (dokumentuota)");

        Blockchain chain = new Blockchain(State.fromUsers(List.of(T.user("A", A, 100), T.user("B", B, 100),
                T.user("C", C, 100), T.user("D", D, 100))), 1);
        Block dup = Miner.mine(chain, four, 0).block();
        T.expectReject(chain, dup, BlockError.BAD_TX, TxError.DUPLICATE_IN_BLOCK);
        BlockHeader h = dup.header();
        chain.addBlock(new Block(h, three)); // ta pati antraštė su teisingu (be dublikato) turiniu tinka
        T.eq(1, chain.height(), "priimtas tik be dublikato");
    }

    static void noCollision() {
        Map<String, Integer> seen = new HashMap<>(400_000);
        for (int i = 0; i < 200_000; i++) {
            String hash = HashUtil.hashUtf8("collision-test|" + i);
            Integer prev = seen.put(hash, i);
            if (prev != null) throw new AssertionError("KOLIZIJA: " + prev + " ir " + i + " -> " + hash);
        }
    }
}
