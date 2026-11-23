package bc.core;

public final class InvalidBlockException extends Exception {
    private final BlockError error;
    private final TxError txError;

    public InvalidBlockException(BlockError error, String detail) {
        this(error, null, detail);
    }

    public InvalidBlockException(BlockError error, TxError txError, String detail) {
        super(error.description() + (txError != null ? " (" + txError.description() + ")" : "")
                + (detail != null && !detail.isEmpty() ? ": " + detail : ""));
        this.error = error;
        this.txError = txError;
    }

    public BlockError error() { return error; }

    /** Jei blokas atmestas dėl transakcijos – jos klaida, kitaip null. */
    public TxError txError() { return txError; }
}
