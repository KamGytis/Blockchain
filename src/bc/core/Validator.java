package bc.core;

import bc.model.Transaction;

/** Transakcijos priėmimo į fondą taisyklės (struktūra + dublikatai). Balansas tikrinamas renkant bloką. */
public final class Validator {
    private Validator() {}

    public static TxError admission(Transaction tx, Blockchain chain, TxPool pool) {
        TxError e = Validation.checkStructure(tx, chain.liveState());
        if (e != null) return e;
        if (chain.isConfirmed(tx.id())) return TxError.ALREADY_CONFIRMED;
        if (pool.contains(tx.id())) return TxError.DUPLICATE_IN_POOL;
        return null;
    }
}
