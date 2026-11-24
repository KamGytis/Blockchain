package bc.core;

import bc.model.Block;
import bc.model.BlockHeader;
import bc.model.Transaction;

import java.util.List;

// Vienas kandidatas kitam blokui: fiksuotas turinys ir antraštė, kasimo būsena tarp biudžeto raundų.
public final class Candidate {
    private final int index;
    private final List<Transaction> txs;
    private final BlockHeader template;
    private long nextNonce;      // pirmas dar netikrintas nonce
    private long attempts;
    private long nanos;
    private int rounds;
    private BlockHeader solution;

    public Candidate(int index, List<Transaction> txs, BlockHeader template) {
        this.index = index;
        this.txs = List.copyOf(txs);
        this.template = template;
        this.nextNonce = template.nonce();
    }

    // Tęsia paiešką nuo dar netikrinto nonce, kol rasta / išnaudotas biudžetas (bandymai arba laikas).
    public void search(long budgetAttempts, long budgetNanos) {
        if (solution != null) return;
        rounds++;
        long deadline = budgetNanos == Long.MAX_VALUE ? Long.MAX_VALUE : System.nanoTime() + budgetNanos;
        Pow.Search s = Pow.search(template, nextNonce, budgetAttempts, deadline);
        attempts += s.attempts();
        nanos += s.nanos();
        nextNonce = s.nextNonce();
        solution = s.solution();
    }

    public boolean solved() { return solution != null; }
    public int index() { return index; }
    public List<Transaction> transactions() { return txs; }
    public BlockHeader template() { return template; }
    public BlockHeader solution() { return solution; }
    public long attempts() { return attempts; }
    public long nanos() { return nanos; }
    public long nextNonce() { return nextNonce; }
    public int rounds() { return rounds; }

    public Block block() {
        if (solution == null) throw new IllegalStateException("kandidatas neiskastas");
        return new Block(solution, txs);
    }
}
