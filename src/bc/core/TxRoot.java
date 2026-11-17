package bc.core;

import bc.hash.HashUtil;
import bc.model.Transaction;

import java.util.List;

public final class TxRoot {
    private TxRoot() {}

    public static String compute(List<Transaction> txs) {
        StringBuilder sb = new StringBuilder(txs.size() * HashUtil.HEX_LENGTH);
        for (Transaction tx : txs) sb.append(tx.id());
        return HashUtil.hashUtf8(sb.toString());
    }
}
