package bc.core;

public enum TxError {
    BAD_FORMAT("netinkamas lauku formatas", true),
    UNKNOWN_SENDER("nezinomas siuntejas", true),
    UNKNOWN_RECEIVER("nezinomas gavejas", true),
    SELF_TRANSFER("siuntejas sutampa su gaveju", true),
    NON_POSITIVE_AMOUNT("suma ne teigiama", true),
    BAD_ID("transaction_id neatitinka turinio", true),
    DUPLICATE_IN_POOL("ID jau yra fonde", true),
    ALREADY_CONFIRMED("ID jau patvirtintas grandineje", true),
    DUPLICATE_IN_BLOCK("ID kartojasi bloke", true),
    INSUFFICIENT_BALANCE("nepakankamas balansas", false),
    OVERFLOW("balanso perpildymas", true);

    private final String description;
    private final boolean permanent;

    TxError(String description, boolean permanent) {
        this.description = description;
        this.permanent = permanent;
    }

    public String description() { return description; }

    public boolean permanent() { return permanent; }
}
