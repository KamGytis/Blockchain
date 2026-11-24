package bc.core;

import bc.model.Block;
import bc.model.BlockHeader;
import bc.model.Transaction;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Vieno kasėjo režimas (v0.1): vienas kandidatas, kasamas be biudžeto ribos.
public final class Miner {
    private Miner() {}

    public record Mined(Block block, Pow.Result pow) {}

    /**
     * Sudaro vieną kandidatą per {@link CandidateBuilder}. Nuolatinių klaidų transakcijos atmetamos iš fondo,
     * nepakankamo balanso – lieka. @return null, jei nėra nė vienos įvykdomos transakcijos.
     */
    public static List<Transaction> select(Blockchain chain, TxPool pool, int blockSize, Random rng) {
        List<Transaction> order = pool.pending();
        if (rng != null) Collections.shuffle(order, rng);
        CandidateBuilder.Selection sel = CandidateBuilder.select(chain, order, blockSize);
        for (Map.Entry<Transaction, TxError> e : sel.permanent().entrySet()) pool.reject(e.getKey(), e.getValue());
        return sel.txs().isEmpty() ? null : sel.txs();
    }

    public static BlockHeader template(Blockchain chain, List<Transaction> txs, long timestamp) {
        return new BlockHeader(chain.tipHash(), timestamp, Blockchain.VERSION, Merkle.root(txs), 0, chain.difficulty());
    }

    public static Mined mine(Blockchain chain, List<Transaction> txs, long timestamp) {
        Pow.Result pow = Pow.mine(template(chain, txs, timestamp));
        return new Mined(new Block(pow.header(), txs), pow);
    }
}
