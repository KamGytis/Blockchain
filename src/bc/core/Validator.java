package bc.core;

import bc.model.Transaction;

/**
 * 1 etapas – pirminė patikra prieš priimant į fondą: formatas, dalyviai, teigiama suma, perskaičiuotas ID,
 * ID nėra fonde ir nėra patvirtintas grandinėje. Balansas čia NEtikrinamas: patekimas į fondą lėšų nerezervuoja,
 * o balanso pakankamumas priklauso nuo būsenos ir tvarkos bloke (tikrinama 2 ir 3 etapuose).
 */
public final class Validator {
    private Validator() {}

    public static TxError admission(Transaction tx, Blockchain chain, TxPool pool) {
        TxError e = Validation.checkStatic(tx, chain.liveState());
        if (e != null) return e;
        if (pool.contains(tx.id())) return TxError.DUPLICATE_IN_POOL;
        if (chain.isConfirmed(tx.id())) return TxError.ALREADY_CONFIRMED;
        return null;
    }
}
