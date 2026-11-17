package bc.core;

import bc.hash.HashUtil;
import bc.model.Block;
import bc.model.BlockHeader;
import bc.model.Transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vieno proceso grandinė. Pirmojo bloko prev_hash = 64 nuliai. Blokas priimamas tik jei praeina visus
 * patikrinimus; tada būsena pakeičiama atomiškai (dirbama su kopija).
 */
public final class Blockchain {
    public static final int VERSION = 1;

    private final List<Block> blocks = new ArrayList<>();
    private final Set<String> confirmed = new HashSet<>();
    private final Map<String, Long> initialBalances;
    private final long initialTotal;
    private final int difficulty;
    private State state;

    public Blockchain(State genesisState, int difficulty) {
        if (difficulty < 0 || difficulty > HashUtil.HEX_LENGTH) throw new IllegalArgumentException("bad difficulty");
        this.state = genesisState.copy();
        this.initialBalances = genesisState.snapshot(); // pradinė būsena išsaugoma prieš taikant transakcijas
        this.initialTotal = genesisState.total();
        this.difficulty = difficulty;
    }

    public void addBlock(Block block) throws InvalidBlockException {
        BlockHeader h = block.header();
        List<Transaction> txs = block.transactions();
        if (h.version() != VERSION) throw new InvalidBlockException("bloga versija " + h.version());
        if (!h.prevHash().equals(tipHash())) throw new InvalidBlockException("prev_hash nesutampa su paskutiniu bloku");
        if (h.difficulty() != difficulty) throw new InvalidBlockException("difficulty " + h.difficulty() + " != " + difficulty);
        if (txs.isEmpty()) throw new InvalidBlockException("tuscias blokas");
        if (!TxRoot.compute(txs).equals(h.txRoot())) throw new InvalidBlockException("tx_root neatitinka turinio");
        if (!Pow.isValid(h)) throw new InvalidBlockException("PoW netenkina salygos (maisa perskaiciuota)");

        State work = state.copy();
        Set<String> seen = new HashSet<>();
        for (Transaction tx : txs) {
            TxError e = Validation.checkStructure(tx, work);
            if (e == null && !seen.add(tx.id())) e = TxError.DUPLICATE_IN_BLOCK;
            if (e == null && confirmed.contains(tx.id())) e = TxError.ALREADY_CONFIRMED;
            if (e == null) e = work.check(tx); // įskaito ankstesnius to paties bloko mokėjimus
            if (e != null) throw new InvalidBlockException(e.description() + ": " + tx);
            work.apply(tx);
        }
        if (work.total() != initialTotal) throw new InvalidBlockException("pasikeite bendra lesu suma");
        if (work.minBalance() < 0) throw new InvalidBlockException("neigiamas balansas");

        state = work;
        confirmed.addAll(seen);
        blocks.add(block);
    }

    public String tipHash() {
        return blocks.isEmpty() ? HashUtil.ZERO_HASH : blocks.get(blocks.size() - 1).hash();
    }

    public int height() { return blocks.size(); }
    public int difficulty() { return difficulty; }
    public List<Block> blocks() { return List.copyOf(blocks); }
    public boolean isConfirmed(String id) { return confirmed.contains(id); }
    public int confirmedCount() { return confirmed.size(); }
    /** Dabartinė būsena (tik skaitymui; keičiama tik per addBlock). */
    public State state() { return state.copy(); }
    State liveState() { return state; }
    public Map<String, Long> initialBalances() { return initialBalances; }
    public long initialTotal() { return initialTotal; }
}
