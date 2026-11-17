package bc.core;

import bc.model.Block;
import bc.model.BlockHeader;
import bc.model.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// Surenka kandidatinį bloką iš fondo ir jį iškasa.
public final class Miner {
    private Miner() {}

    public record Mined(Block block, Pow.Result pow) {}

    public static List<Transaction> select(Blockchain chain, TxPool pool, int blockSize, Random rng) {
        List<Transaction> candidates = pool.pending();
        if (rng != null) Collections.shuffle(candidates, rng);
        State work = chain.state();
        List<Transaction> selected = new ArrayList<>();
        for (Transaction tx : candidates) {
            if (selected.size() == blockSize) break;
            TxError e = Validation.checkStructure(tx, work);
            if (e == null && chain.isConfirmed(tx.id())) e = TxError.ALREADY_CONFIRMED;
            if (e == null) e = work.check(tx);
            if (e == null) {
                work.apply(tx);
                selected.add(tx);
            } else if (e.permanent()) {
                pool.reject(tx, e);
            }
        }
        return selected.isEmpty() ? null : selected;
    }

    public static Mined mine(Blockchain chain, List<Transaction> txs, long timestamp) {
        BlockHeader template = new BlockHeader(chain.tipHash(), timestamp, Blockchain.VERSION,
                TxRoot.compute(txs), 0, chain.difficulty());
        Pow.Result pow = Pow.mine(template);
        return new Mined(new Block(pow.header(), txs), pow);
    }
}
