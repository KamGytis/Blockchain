package bc.core;

import bc.hash.HashUtil;
import bc.model.Transaction;

public final class Validation {
    private Validation() {}

    public static TxError checkStructure(Transaction tx, State state) {
        if (tx == null || !HashUtil.isHash(tx.sender()) || !HashUtil.isHash(tx.receiver()) || !HashUtil.isHash(tx.id()))
            return TxError.BAD_FORMAT;
        if (tx.amount() <= 0) return TxError.NON_POSITIVE_AMOUNT;
        if (!state.hasAccount(tx.sender())) return TxError.UNKNOWN_SENDER;
        if (!state.hasAccount(tx.receiver())) return TxError.UNKNOWN_RECEIVER;
        if (tx.sender().equals(tx.receiver())) return TxError.SELF_TRANSFER;
        if (!tx.hasValidId()) return TxError.BAD_ID;
        return null;
    }
}
