package bc.core;

import bc.hash.HashUtil;
import bc.model.Block;
import bc.model.BlockHeader;
import bc.model.Transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vieno proceso grandinė. 3 etapas – galutinis bloko priėmimas: antraštė, turinys ir visos transakcijos
 * jų tvarka tikrinamos iš naujo pagal tuometinę patvirtintą būseną. Tik visam blokui praėjus patikrą,
 * vienu veiksmu atnaujinami blokai, balansai, patvirtintų ID aibė ir (jei duotas) fondas.
 * Netinkamas blokas nieko nekeičia.
 */
public final class Blockchain {
    /** Bloko formato versija. v1 – tx_root = H(id1..idn), v2 – Merkle šaknis. */
    public static final int VERSION = 2;
    public static final int DEFAULT_MAX_TX = 100;

    private final List<Block> blocks = new ArrayList<>();
    private final Set<String> confirmed = new HashSet<>();
    private final Map<String, Long> initialBalances;
    private final long initialTotal;
    private final int difficulty;
    private final int maxTx;
    private State state;

    public Blockchain(State genesisState, int difficulty) {
        this(genesisState, difficulty, DEFAULT_MAX_TX);
    }

    public Blockchain(State genesisState, int difficulty, int maxTx) {
        if (difficulty < 0 || difficulty > HashUtil.HEX_LENGTH) throw new IllegalArgumentException("bad difficulty");
        if (maxTx < 1) throw new IllegalArgumentException("bad maxTx");
        this.state = genesisState.copy();
        this.initialBalances = genesisState.snapshot(); // pradinė būsena išsaugoma prieš taikant transakcijas
        this.initialTotal = genesisState.total();
        this.difficulty = difficulty;
        this.maxTx = maxTx;
    }

    // ---------- 3 etapas: atskiros patikros procedūros ----------

    /** Antraštė: versija, prev_hash, sudėtingumas (kandidatas negali jo sumažinti), PoW pagal GRANDINĖS sudėtingumą. */
    public void checkHeader(BlockHeader h) throws InvalidBlockException {
        if (h.version() != VERSION) throw new InvalidBlockException(BlockError.UNSUPPORTED_VERSION, "version=" + h.version());
        if (!h.prevHash().equals(tipHash())) throw new InvalidBlockException(BlockError.BAD_PREV_HASH, h.prevHash());
        if (h.difficulty() < difficulty)
            throw new InvalidBlockException(BlockError.LOWERED_DIFFICULTY, h.difficulty() + " < " + difficulty);
        if (h.difficulty() != difficulty)
            throw new InvalidBlockException(BlockError.BAD_DIFFICULTY, h.difficulty() + " != " + difficulty);
        if (!Pow.meets(h, difficulty)) throw new InvalidBlockException(BlockError.BAD_POW, h.hash());
    }

    /** Turinys: leidžiamas transakcijų skaičius ir Merkle šaknis. */
    public void checkBody(Block block) throws InvalidBlockException {
        List<Transaction> txs = block.transactions();
        if (txs.isEmpty()) throw new InvalidBlockException(BlockError.EMPTY, null);
        if (txs.size() > maxTx) throw new InvalidBlockException(BlockError.TOO_MANY_TX, txs.size() + " > " + maxTx);
        for (Transaction tx : txs) {
            TxError e = Validation.checkFormat(tx);
            if (e != null) throw new InvalidBlockException(BlockError.BAD_TX, e, String.valueOf(tx));
        }
        if (!Merkle.root(txs).equals(block.header().txRoot()))
            throw new InvalidBlockException(BlockError.BAD_TX_ROOT, block.header().txRoot());
    }

    /**
     * Transakcijos jų tvarka vienoje laikinoje būsenoje (patvirtintos būsenos kopijoje).
     * Grąžina naują būseną; patvirtintos būsenos nekeičia.
     */
    public State applyTransactions(List<Transaction> txs) throws InvalidBlockException {
        State work = state.copy();
        Set<String> seen = new HashSet<>();
        for (Transaction tx : txs) {
            TxError e = Validation.checkInBlock(tx, work, seen, this);
            if (e != null) throw new InvalidBlockException(BlockError.BAD_TX, e, String.valueOf(tx));
            work.apply(tx);
            seen.add(tx.id());
        }
        if (work.total() != initialTotal) throw new InvalidBlockException(BlockError.SUPPLY_CHANGED, null);
        if (work.minBalance() < 0) throw new InvalidBlockException(BlockError.NEGATIVE_BALANCE, null);
        return work;
    }

    /** Pilna patikra be jokių pakeitimų. Grąžina būseną, kuri būtų po bloko. */
    public State validate(Block block) throws InvalidBlockException {
        checkHeader(block.header());
        checkBody(block);
        return applyTransactions(block.transactions());
    }

    public void addBlock(Block block) throws InvalidBlockException {
        addBlock(block, null);
    }

    /** Patikrina ir, tik jei viskas tinka, vienu veiksmu prijungia bloką ir pašalina jo transakcijas iš fondo. */
    public void addBlock(Block block, TxPool pool) throws InvalidBlockException {
        State next = validate(block);
        Set<String> ids = new LinkedHashSet<>();
        for (Transaction tx : block.transactions()) ids.add(tx.id());
        // commit: nuo čia niekas nebegali mesti išimties
        state = next;
        confirmed.addAll(ids);
        blocks.add(block);
        if (pool != null) pool.removeConfirmed(block.transactions());
    }

    public String tipHash() {
        return blocks.isEmpty() ? HashUtil.ZERO_HASH : blocks.get(blocks.size() - 1).hash();
    }

    public int height() { return blocks.size(); }
    public int difficulty() { return difficulty; }
    public int maxTx() { return maxTx; }
    public List<Block> blocks() { return List.copyOf(blocks); }
    public boolean isConfirmed(String id) { return confirmed.contains(id); }
    public int confirmedCount() { return confirmed.size(); }
    public State state() { return state.copy(); }
    State liveState() { return state; }
    public Map<String, Long> initialBalances() { return initialBalances; }
    public long initialTotal() { return initialTotal; }
}
