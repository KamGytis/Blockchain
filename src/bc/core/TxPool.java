package bc.core;

import bc.model.Transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TxPool {
    public record Rejection(Transaction tx, TxError reason) {}

    private final Map<String, Transaction> pending = new LinkedHashMap<>();
    private final List<Rejection> rejected = new ArrayList<>();

    public TxError submit(Transaction tx, Blockchain chain) {
        TxError e = Validator.admission(tx, chain, this);
        if (e != null) {
            rejected.add(new Rejection(tx, e));
            return e;
        }
        pending.put(tx.id(), tx);
        return null;
    }

    public List<Transaction> pending() {
        return new ArrayList<>(pending.values());
    }

    public boolean contains(String id) {
        return pending.containsKey(id);
    }

    public int size() {
        return pending.size();
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public void removeConfirmed(Collection<Transaction> txs) {
        for (Transaction tx : txs) pending.remove(tx.id());
    }
    public void reject(Transaction tx, TxError reason) {
        if (pending.remove(tx.id()) != null) rejected.add(new Rejection(tx, reason));
    }

    public List<Rejection> rejected() {
        return List.copyOf(rejected);
    }
}
